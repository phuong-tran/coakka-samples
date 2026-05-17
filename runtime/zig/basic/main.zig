const std = @import("std");

const c = @cImport({
    @cInclude("coakka/v2/runtime.h");
    @cInclude("coakka/v2/control.h");
    @cInclude("coakka/v2/client.h");
    @cInclude("coakka/v2/transport.h");
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

fn readDeliveredFrame(reader: *c.coakka_v2_frame_reader_t) !struct { ptr: [*c]u8, len: usize } {
    var attempts: usize = 0;
    while (attempts < 1000) : (attempts += 1) {
        var buf: [*c]u8 = null;
        var len: usize = 0;
        const rc = c.coakka_v2_frame_read_try(reader, &buf, &len);
        if (rc == c.COAKKA_V2_OK) {
            if (buf == null or len == 0) {
                return SampleError.CoakkaCallFailed;
            }
            return .{ .ptr = buf, .len = len };
        }
        if (rc != c.COAKKA_V2_ERR_WOULD_BLOCK) {
            try requireOk(rc, "frame_read_try");
        }
        _ = c.usleep(1000);
    }
    return SampleError.CoakkaCallFailed;
}

fn runRawRoundTrip(runtime: ?*c.coakka_v2_runtime_t, handles: *const c.coakka_v2_host_handles_t) !void {
    if (runtime == null or handles.delivered_request_read_fd < 0) {
        return SampleError.CoakkaCallFailed;
    }

    const client = c.coakka_v2_ask_client_create(runtime, handles);
    if (client == null) {
        std.debug.print("ask_client_create failed\n", .{});
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_ask_client_destroy(client);

    const request_payload = "hello";
    var request_spec: c.coakka_v2_client_raw_request_spec_t = std.mem.zeroes(c.coakka_v2_client_raw_request_spec_t);
    request_spec.struct_size = @sizeOf(c.coakka_v2_client_raw_request_spec_t);
    request_spec.message_id = "req-zig-basic-raw-1";
    request_spec.source = "zig-basic-client";
    request_spec.target = "svc.echo";
    request_spec.reply_to = "zig-basic-client/replies";
    request_spec.payload = request_payload.ptr;
    request_spec.payload_len = request_payload.len;
    request_spec.timeout_ms = 1000;
    request_spec.delivery_hint = c.COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
    request_spec.one_way = 0;

    var request_buf: [*c]u8 = null;
    var request_len: usize = 0;
    try requireOk(c.coakka_v2_client_build_raw_request(&request_spec, &request_buf, &request_len), "build_raw_request");
    if (request_buf == null or request_len == 0) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_client_bytes_release(request_buf);

    var ticket: ?*c.coakka_v2_ask_ticket_t = null;
    try requireOk(c.coakka_v2_ask_client_begin(client, request_buf, request_len, &ticket), "ask_client_begin");
    if (ticket == null) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_ask_ticket_destroy(ticket);

    const reader = c.coakka_v2_frame_reader_create(handles.delivered_request_read_fd, 64 * 1024);
    if (reader == null) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_frame_reader_destroy(reader);

    const delivered = try readDeliveredFrame(reader.?);
    defer c.coakka_v2_frame_release(delivered.ptr);

    const reply_payload = "reply";
    var reply_spec: c.coakka_v2_client_raw_reply_spec_t = std.mem.zeroes(c.coakka_v2_client_raw_reply_spec_t);
    reply_spec.struct_size = @sizeOf(c.coakka_v2_client_raw_reply_spec_t);
    reply_spec.request_buf = delivered.ptr;
    reply_spec.request_len = delivered.len;
    reply_spec.source = "svc.echo";
    reply_spec.payload = reply_payload.ptr;
    reply_spec.payload_len = reply_payload.len;

    var reply_buf: [*c]u8 = null;
    var reply_len: usize = 0;
    try requireOk(c.coakka_v2_client_build_raw_reply(&reply_spec, &reply_buf, &reply_len), "build_raw_reply");
    if (reply_buf == null or reply_len == 0) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_client_bytes_release(reply_buf);

    try requireOk(c.coakka_v2_runtime_submit_envelope(runtime, reply_buf, reply_len), "submit_reply");

    var result_kind: u32 = c.COAKKA_V2_CLIENT_RESULT_NONE;
    var result_buf: [*c]u8 = null;
    var result_len: usize = 0;
    try requireOk(c.coakka_v2_ask_ticket_await(ticket, 1000, &result_kind, &result_buf, &result_len), "ask_ticket_await");
    defer c.coakka_v2_client_bytes_release(result_buf);
    if (result_kind != c.COAKKA_V2_CLIENT_RESULT_RESPONSE or result_buf == null or result_len == 0) {
        return SampleError.CoakkaCallFailed;
    }
}

fn runRouteMissDeadletter(runtime: ?*c.coakka_v2_runtime_t, handles: *const c.coakka_v2_host_handles_t) !void {
    if (runtime == null) {
        return SampleError.CoakkaCallFailed;
    }

    const client = c.coakka_v2_ask_client_create(runtime, handles);
    if (client == null) {
        std.debug.print("ask_client_create failed\n", .{});
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_ask_client_destroy(client);

    const request_payload = "missing";
    var request_spec: c.coakka_v2_client_raw_request_spec_t = std.mem.zeroes(c.coakka_v2_client_raw_request_spec_t);
    request_spec.struct_size = @sizeOf(c.coakka_v2_client_raw_request_spec_t);
    request_spec.message_id = "req-zig-basic-missing-1";
    request_spec.source = "zig-basic-client";
    request_spec.target = "svc.missing";
    request_spec.reply_to = "zig-basic-client/replies";
    request_spec.payload = request_payload.ptr;
    request_spec.payload_len = request_payload.len;
    request_spec.timeout_ms = 1000;
    request_spec.delivery_hint = c.COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
    request_spec.one_way = 0;

    var request_buf: [*c]u8 = null;
    var request_len: usize = 0;
    try requireOk(c.coakka_v2_client_build_raw_request(&request_spec, &request_buf, &request_len), "build_missing_raw_request");
    if (request_buf == null or request_len == 0) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_client_bytes_release(request_buf);

    var ticket: ?*c.coakka_v2_ask_ticket_t = null;
    try requireOk(c.coakka_v2_ask_client_begin(client, request_buf, request_len, &ticket), "ask_missing_client_begin");
    if (ticket == null) {
        return SampleError.CoakkaCallFailed;
    }
    defer c.coakka_v2_ask_ticket_destroy(ticket);

    var result_kind: u32 = c.COAKKA_V2_CLIENT_RESULT_NONE;
    var result_buf: [*c]u8 = null;
    var result_len: usize = 0;
    try requireOk(c.coakka_v2_ask_ticket_await(ticket, 1000, &result_kind, &result_buf, &result_len), "ask_missing_ticket_await");
    defer c.coakka_v2_client_bytes_release(result_buf);
    if (result_kind != c.COAKKA_V2_CLIENT_RESULT_DEADLETTER or result_buf == null or result_len == 0) {
        return SampleError.CoakkaCallFailed;
    }
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
    handles.flags = c.COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR |
        c.COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
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
        .target = "svc.echo",
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

    try runRawRoundTrip(runtime, &handles);
    try runRouteMissDeadletter(runtime, &handles);

    std.debug.print(
        "coakka_runtime_stats generation={d} routes={d} state={s} rawRoundTrip=ok routeMissDeadletter=ok language=zig\n",
        .{
            stats.applied_generation,
            stats.route_count,
            cstr(c.coakka_v2_runtime_state_name(stats.runtime_state)),
        },
    );
}
