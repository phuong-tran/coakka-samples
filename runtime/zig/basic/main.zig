const std = @import("std");
const runtime = @import("runtime.zig");

fn cstr(value: ?[*:0]const u8) []const u8 {
    if (value) |ptr| return std.mem.span(ptr);
    return "";
}

pub fn main(init: std.process.Init) !void {
    const lib_path = init.environ_map.get("COAKKA_RUNTIME_LIB") orelse {
        std.debug.print("set COAKKA_RUNTIME_LIB=/path/to/libcoakka_runtime_v2\n", .{});
        return error.MissingRuntimeLibraryPath;
    };

    var native = try runtime.NativeRuntime.open(lib_path);
    defer native.close();

    const info = try native.readInfo();
    var host = try native.startHost(runtime.smokeSpec());
    defer host.deinit();

    const stats = try host.stats();
    if (!runtime.isStarted(stats, 1, 1)) {
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
            std.mem.span(runtime.smokeSpec().system_name),
            std.mem.span(runtime.smokeSpec().node_id),
            stats.applied_generation,
            stats.route_count,
            stats.runtime_state,
        },
    );
}
