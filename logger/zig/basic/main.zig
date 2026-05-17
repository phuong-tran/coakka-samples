const std = @import("std");

const c = @cImport({
    @cInclude("coakka/logger/core.h");
    @cInclude("coakka/logger/utils.h");
});

fn cstr(value: [*c]const u8) []const u8 {
    if (value == null) return "";
    return std.mem.span(value);
}

fn requireOk(status: c.coakka_logger_status_t, step: []const u8) !void {
    if (status == c.COAKKA_LOGGER_STATUS_OK) return;
    std.debug.print("{s} failed: {s}\n", .{ step, cstr(c.coakka_logger_status_name(status)) });
    return error.LoggerCallFailed;
}

pub fn main() !void {
    var info = std.mem.zeroes(c.coakka_logger_core_info_t);
    info.struct_size = @sizeOf(c.coakka_logger_core_info_t);
    try requireOk(c.coakka_logger_core_get_info(&info), "get_info");

    std.debug.print(
        "coakka_logger_info abi={d} version={s} git={s} language=zig\n",
        .{ info.abi_version, cstr(info.runtime_version), cstr(info.git_commit) },
    );

    var config = c.coakka_logger_core_default_config();
    config.system_name = "zig-basic-logger";
    config.queue_capacity = 8;
    config.category_capacity = 96;
    config.message_capacity = 160;

    var handle: ?*c.coakka_logger_core_handle_t = null;
    try requireOk(c.coakka_logger_core_create(&config, &handle), "create");
    defer c.coakka_logger_core_destroy(handle);

    try requireOk(c.coakka_logger_core_start(handle), "start");
    defer _ = c.coakka_logger_core_stop(handle);

    var sequence: u64 = 0;
    try requireOk(
        c.coakka_logger_core_log_info(
            handle,
            "samples.logger.zig.basic",
            "{\"event\":\"hello\",\"language\":\"zig\"}",
            &sequence,
        ),
        "log_info",
    );

    var category: [96]u8 = std.mem.zeroes([96]u8);
    var message: [160]u8 = std.mem.zeroes([160]u8);
    var record = std.mem.zeroes(c.coakka_logger_core_record_buffer_t);
    record.struct_size = @sizeOf(c.coakka_logger_core_record_buffer_t);
    record.category = category[0..].ptr;
    record.category_capacity = category.len;
    record.message = message[0..].ptr;
    record.message_capacity = message.len;

    try requireOk(c.coakka_logger_core_read_next(handle, 100, &record), "read_next");

    std.debug.print(
        "coakka_logger_record sequence={d} level={s} category={s} message={s}\n",
        .{
            record.sequence,
            cstr(c.coakka_logger_level_name(record.level)),
            category[0..record.category_length],
            message[0..record.message_length],
        },
    );

    var stats = std.mem.zeroes(c.coakka_logger_core_stats_t);
    stats.struct_size = @sizeOf(c.coakka_logger_core_stats_t);
    try requireOk(c.coakka_logger_core_get_stats(handle, &stats), "get_stats");

    std.debug.print(
        "coakka_logger_stats emitted={d} delivered={d} dropped={d} language=zig\n",
        .{ stats.emitted_count, stats.delivered_count, stats.dropped_count },
    );

    if (sequence != 1) return error.UnexpectedSequence;
}
