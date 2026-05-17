const std = @import("std");

const c = @cImport({
    @cInclude("coakka/v2/runtime.h");
    @cInclude("coakka/v2/control.h");
    @cInclude("coakka/v2/utils.h");
    @cInclude("unistd.h");
});

const SampleError = error{CoakkaCallFailed};

fn cstr(ptr: [*c]const u8) []const u8 {
    if (ptr == null) {
        return "";
    }
    return std.mem.span(ptr);
}

fn requireOk(status: c.coakka_v2_status_t, step: []const u8) !void {
    if (status == c.COAKKA_V2_OK) {
        return;
    }
    std.debug.print("{s} failed: {s}\n", .{ step, cstr(c.coakka_v2_status_name(status)) });
    return SampleError.CoakkaCallFailed;
}

fn closeIfOpen(fd: *c_int) void {
    if (fd.* >= 0) {
        _ = c.close(fd.*);
        fd.* = -1;
    }
}

fn closeHostHandles(handles: *c.coakka_v2_host_handles_t) void {
    closeIfOpen(&handles.request_write_fd);
    closeIfOpen(&handles.response_read_fd);
    closeIfOpen(&handles.deadletter_read_fd);
    closeIfOpen(&handles.control_write_fd);
    closeIfOpen(&handles.monitor_read_fd);
    closeIfOpen(&handles.delivered_request_read_fd);
}

pub fn main() !void {
    var info: c.coakka_v2_runtime_info_t = std.mem.zeroes(c.coakka_v2_runtime_info_t);
    info.struct_size = @sizeOf(c.coakka_v2_runtime_info_t);
    try requireOk(c.coakka_v2_runtime_get_info(&info), "runtime_get_info");
    std.debug.print(
        "coakka_runtime_info abi={d} version={s} git={s} language=zig\n",
        .{ info.abi_version, cstr(info.runtime_version), cstr(info.git_commit) },
    );

    const config = c.coakka_v2_runtime_config_t{
        .system_name = "runtime-v2-zig-basic",
        .node_id = "runtime-v2-zig-node",
        .strict_no_drop = 1,
        .queue_capacity = 16,
    };
    const runtime = c.coakka_v2_runtime_create(&config);
    if (runtime == null) {
        std.debug.print("runtime_create failed\n", .{});
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_runtime_destroy(runtime);

    var handles: c.coakka_v2_host_handles_t = std.mem.zeroes(c.coakka_v2_host_handles_t);
    handles.struct_size = @sizeOf(c.coakka_v2_host_handles_t);
    handles.flags = c.COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR;
    handles.request_write_fd = -1;
    handles.response_read_fd = -1;
    handles.deadletter_read_fd = -1;
    handles.control_write_fd = -1;
    handles.monitor_read_fd = -1;
    handles.delivered_request_read_fd = -1;
    try requireOk(c.coakka_v2_runtime_get_host_handles(runtime, &handles), "get_host_handles");
    defer closeHostHandles(&handles);

    const endpoint = c.coakka_v2_endpoint_t{
        .host = "127.0.0.1",
        .port = 9041,
        .weight = 1,
        .flags = c.COAKKA_V2_ENDPOINT_FLAG_LOCAL,
    };
    const route = c.coakka_v2_route_t{
        .target = "samples.runtime.zig.local",
        .strategy = c.COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER,
        .route_key_hint = null,
        .flags = c.COAKKA_V2_ROUTE_FLAG_NONE,
        .endpoints = &endpoint,
        .endpoint_count = 1,
    };
    const snapshot = c.coakka_v2_control_snapshot_t{
        .generation = 1,
        .routes = &route,
        .route_count = 1,
    };

    try requireOk(c.coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot), "apply_snapshot");
    try requireOk(c.coakka_v2_runtime_start(runtime), "runtime_start");
    defer _ = c.coakka_v2_runtime_stop(runtime);

    var stats: c.coakka_v2_runtime_stats_t = std.mem.zeroes(c.coakka_v2_runtime_stats_t);
    stats.struct_size = @sizeOf(c.coakka_v2_runtime_stats_t);
    try requireOk(c.coakka_v2_runtime_get_stats(runtime, &stats), "runtime_get_stats");
    if (stats.applied_generation != 1 or stats.route_count != 1 or stats.runtime_state != c.COAKKA_V2_STATE_STARTED) {
        std.debug.print(
            "unexpected runtime stats generation={d} routes={d} state={d}\n",
            .{ stats.applied_generation, stats.route_count, stats.runtime_state },
        );
        return SampleError.CoakkaCallFailed;
    }

    std.debug.print(
        "coakka_runtime_stats generation={d} routes={d} state={s} language=zig\n",
        .{
            stats.applied_generation,
            stats.route_count,
            cstr(c.coakka_v2_runtime_state_name(stats.runtime_state)),
        },
    );
}
