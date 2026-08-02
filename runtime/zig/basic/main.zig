const std = @import("std");
const runtime = @import("runtime.zig");

fn cstr(value: ?[*:0]const u8) []const u8 {
    if (value) |ptr| return std.mem.span(ptr);
    return "";
}

fn takeStarted(result: runtime.StartHostResult) !runtime.RuntimeHost {
    return switch (result) {
        .started => |host| host,
        .connection_rejected => error.ConnectionConfigurationRejected,
        .security_rejected => error.SecurityConfigurationRejected,
    };
}

pub fn main(init: std.process.Init) !void {
    const lib_path = init.environ_map.get("COAKKA_RUNTIME_LIB") orelse {
        std.debug.print("set COAKKA_RUNTIME_LIB=/path/to/libcoakka_runtime_v2\n", .{});
        return error.MissingRuntimeLibraryPath;
    };

    var native = try runtime.NativeRuntime.open(lib_path);
    defer native.close();

    const info = try native.readInfo();
    const spec = sampleSpec();
    var host = try takeStarted(try native.startHost(spec));
    defer host.deinit();

    const stats = try host.stats();
    if (!runtime.isStarted(stats, spec.generation, 1)) {
        std.debug.print(
            "unexpected runtime stats generation={d} routes={d} state={d}\n",
            .{ stats.applied_generation, stats.route_count, stats.runtime_state },
        );
        return error.UnexpectedRuntimeStats;
    }

    try host.smokeRawRoundTrip();
    try host.smokeRouteMissDeadletter();

    std.debug.print(
        "coakka_runtime_zig_basic abi={d} runtime={s} git={s} system={s} node={s} generation={d} routes={d} state={d} rawRoundTrip=ok routeMissDeadletter=ok\n",
        .{
            info.abi_version,
            cstr(info.runtime_version),
            cstr(info.git_commit),
            std.mem.span(spec.system_name),
            std.mem.span(spec.node_id),
            stats.applied_generation,
            stats.route_count,
            stats.runtime_state,
        },
    );
}

fn sampleSpec() runtime.StartSpec {
    return .{
        .system_name = "zig-runtime-basic",
        .node_id = "zig-runtime-basic-node",
        .target = "svc.echo",
        .host = "127.0.0.1",
        .port = 9041,
        .generation = 1,
        .strict_no_drop = true,
        .queue_capacity = 32,
    };
}
