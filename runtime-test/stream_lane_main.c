#include "coakka/v2/stream_lane.h"

#include <assert.h>
#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#ifdef NDEBUG
#error "Stream Lane evidence requires assertions to remain enabled"
#endif

enum {
    kFrameBytes = 1024u,
    kWindowBytes = 4u * kFrameBytes,
    kFrameCount = 97u
};

static const uint64_t kFormatId = UINT64_C(0x53545245414d3031);
static const char kSessionId[] = "public-c-stream-roundtrip";
static const char kToken[] = "public-c-local-test-token";

typedef struct source_context_t {
    uint64_t produced;
    uint64_t bytes;
    uint64_t drops;
} source_context_t;

typedef struct consumer_context_t {
    uint64_t consumed;
    uint64_t bytes;
    uint64_t drops;
} consumer_context_t;

static size_t payload_size(uint64_t sequence) {
    return 257u + (size_t)(sequence % 7u) * 83u;
}

static uint8_t payload_byte(uint64_t sequence, size_t index) {
    return (uint8_t)((sequence * UINT64_C(31) + index * 17u) & UINT64_C(0xff));
}

static coakka_v2_status_t source_next(void *opaque, uint8_t *destination,
                                      size_t capacity,
                                      coakka_v2_stream_frame_t *frame) {
    source_context_t *source = (source_context_t *)opaque;
    uint64_t sequence;
    size_t size;
    size_t index;

    if (source == NULL || destination == NULL || frame == NULL) {
        return COAKKA_V2_ERR_INVALID_ARG;
    }
    if (source->produced == kFrameCount) {
        return COAKKA_V2_ERR_CLOSED;
    }

    sequence = source->produced + 1u;
    size = payload_size(sequence);
    if (size > capacity) {
        return COAKKA_V2_ERR_IO;
    }
    for (index = 0u; index < size; ++index) {
        destination[index] = payload_byte(sequence, index);
    }

    frame->captured_mono_ns = UINT64_C(1000000) + sequence;
    frame->dropped_before = sequence % 13u == 0u ? 1u : 0u;
    frame->flags = sequence == 1u
                       ? (uint32_t)COAKKA_V2_STREAM_LANE_FRAME_FLAG_KEYFRAME
                       : 0u;
    frame->size = size;
    source->produced = sequence;
    source->bytes += size;
    source->drops += frame->dropped_before;
    return COAKKA_V2_OK;
}

static coakka_v2_status_t consume_frame(
    void *opaque, const uint8_t *data,
    const coakka_v2_stream_frame_t *frame) {
    consumer_context_t *consumer = (consumer_context_t *)opaque;
    uint64_t expected_sequence;
    size_t expected_size;
    size_t index;

    if (consumer == NULL || data == NULL || frame == NULL ||
        frame->struct_size < sizeof(*frame)) {
        return COAKKA_V2_ERR_IO;
    }
    expected_sequence = consumer->consumed + 1u;
    expected_size = payload_size(expected_sequence);
    if (frame->sequence != expected_sequence || frame->size != expected_size ||
        frame->captured_mono_ns != UINT64_C(1000000) + expected_sequence) {
        return COAKKA_V2_ERR_IO;
    }
    if ((expected_sequence == 1u &&
         frame->flags != COAKKA_V2_STREAM_LANE_FRAME_FLAG_KEYFRAME) ||
        (expected_sequence != 1u && frame->flags != 0u)) {
        return COAKKA_V2_ERR_IO;
    }
    for (index = 0u; index < frame->size; ++index) {
        if (data[index] != payload_byte(expected_sequence, index)) {
            return COAKKA_V2_ERR_IO;
        }
    }

    consumer->consumed = expected_sequence;
    consumer->bytes += frame->size;
    consumer->drops += frame->dropped_before;
    return COAKKA_V2_OK;
}

static int terminal_state(uint32_t state) {
    return state == COAKKA_V2_STREAM_STATE_ENDED ||
           state == COAKKA_V2_STREAM_STATE_REJECTED ||
           state == COAKKA_V2_STREAM_STATE_FAILED ||
           state == COAKKA_V2_STREAM_STATE_CANCELED;
}

static coakka_v2_stream_session_snapshot_t wait_terminal(
    coakka_v2_stream_lane_t *lane, uint32_t direction,
    uint64_t *observed_updates) {
    coakka_v2_stream_session_snapshot_t snapshot;
    unsigned int attempts;

    memset(&snapshot, 0, sizeof(snapshot));
    *observed_updates = 0u;
    snapshot.struct_size = sizeof(snapshot);
    assert(coakka_v2_stream_lane_get_session(lane, kSessionId, direction,
                                             &snapshot) == COAKKA_V2_OK);
    for (attempts = 0u; !terminal_state(snapshot.state) && attempts < 100u;
         ++attempts) {
        const uint64_t after = snapshot.update_sequence;
        coakka_v2_status_t status;
        snapshot.struct_size = sizeof(snapshot);
        status = coakka_v2_stream_lane_wait_session(
            lane, kSessionId, direction, after, 100u, &snapshot);
        assert(status == COAKKA_V2_OK ||
               status == COAKKA_V2_ERR_WOULD_BLOCK);
        if (status == COAKKA_V2_OK) {
            assert(snapshot.update_sequence > after);
            *observed_updates += 1u;
        }
    }
    assert(terminal_state(snapshot.state));
    return snapshot;
}

int main(void) {
    coakka_v2_runtime_info_t runtime_info;
    coakka_v2_stream_lane_config_t publisher_config;
    coakka_v2_stream_lane_config_t subscriber_config;
    coakka_v2_stream_publish_spec_t publish;
    coakka_v2_stream_subscribe_spec_t subscribe;
    coakka_v2_stream_session_snapshot_t publisher_result;
    coakka_v2_stream_session_snapshot_t subscriber_result;
    coakka_v2_stream_pressure_snapshot_t publisher_pressure;
    coakka_v2_stream_pressure_snapshot_t subscriber_pressure;
    coakka_v2_stream_lane_stats_t publisher_stats;
    coakka_v2_stream_lane_stats_t subscriber_stats;
    coakka_v2_stream_lane_t *publisher = NULL;
    coakka_v2_stream_lane_t *subscriber = NULL;
    source_context_t source = {0};
    consumer_context_t consumer = {0};
    uint16_t port = 0u;
    uint64_t publisher_observed_updates = 0u;
    uint64_t subscriber_observed_updates = 0u;

    _Static_assert(COAKKA_V2_STREAM_LANE_WIRE_VERSION == 1u,
                   "stream wire version drifted");
    _Static_assert(COAKKA_V2_STREAM_LANE_MAX_FRAME_BYTES ==
                       4u * 1024u * 1024u,
                   "stream frame hard bound drifted");
    _Static_assert(COAKKA_V2_STREAM_LANE_MAX_WINDOW_BYTES ==
                       16u * 1024u * 1024u,
                   "stream window hard bound drifted");

    memset(&runtime_info, 0, sizeof(runtime_info));
    runtime_info.struct_size = sizeof(runtime_info);
    assert(coakka_v2_runtime_get_info(&runtime_info) == COAKKA_V2_OK);
    assert(runtime_info.runtime_version != NULL);
    assert(runtime_info.runtime_version[0] != '\0');
    assert((runtime_info.feature_flags &
            COAKKA_V2_RUNTIME_FEATURE_STREAM_LANE) != 0u);

    memset(&publisher_config, 0, sizeof(publisher_config));
    publisher_config.struct_size = sizeof(publisher_config);
    publisher_config.flags = COAKKA_V2_STREAM_LANE_ENABLE_PUBLISHER;
    publisher_config.bind_host = "127.0.0.1";
    publisher_config.max_frame_bytes = kFrameBytes;
    publisher_config.max_window_bytes = kWindowBytes;
    publisher_config.io_timeout_ms = 3000u;

    memset(&subscriber_config, 0, sizeof(subscriber_config));
    subscriber_config.struct_size = sizeof(subscriber_config);
    subscriber_config.flags = COAKKA_V2_STREAM_LANE_ENABLE_SUBSCRIBER;
    subscriber_config.max_frame_bytes = kFrameBytes;
    subscriber_config.max_window_bytes = kWindowBytes;
    subscriber_config.io_timeout_ms = 3000u;

    assert(coakka_v2_stream_lane_create_ex(&publisher_config, &publisher) ==
           COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_create_ex(&subscriber_config, &subscriber) ==
           COAKKA_V2_OK);
    assert(publisher != NULL);
    assert(subscriber != NULL);
    assert(coakka_v2_stream_lane_start(publisher) == COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_start(subscriber) == COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_get_bound_port(publisher, &port) ==
           COAKKA_V2_OK);
    assert(port != 0u);

    memset(&publish, 0, sizeof(publish));
    publish.struct_size = sizeof(publish);
    publish.session_id = kSessionId;
    publish.authorization_token = kToken;
    publish.format_id = kFormatId;
    publish.max_frame_bytes = kFrameBytes;
    publish.source_next = source_next;
    publish.source_context = &source;
    assert(coakka_v2_stream_lane_prepare_publish(publisher, &publish) ==
           COAKKA_V2_OK);

    memset(&subscribe, 0, sizeof(subscribe));
    subscribe.struct_size = sizeof(subscribe);
    subscribe.session_id = kSessionId;
    subscribe.authorization_token = kToken;
    subscribe.remote_host = "127.0.0.1";
    subscribe.remote_port = port;
    subscribe.format_id = kFormatId;
    subscribe.max_frame_bytes = kFrameBytes;
    subscribe.initial_window_bytes = kWindowBytes;
    subscribe.timeout_ms = 3000u;
    subscribe.consume = consume_frame;
    subscribe.consumer_context = &consumer;
    assert(coakka_v2_stream_lane_subscribe(subscriber, &subscribe) ==
           COAKKA_V2_OK);

    subscriber_result =
        wait_terminal(subscriber, COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE,
                      &subscriber_observed_updates);
    publisher_result =
        wait_terminal(publisher, COAKKA_V2_STREAM_DIRECTION_PUBLISH,
                      &publisher_observed_updates);
    assert(subscriber_result.state == COAKKA_V2_STREAM_STATE_ENDED);
    assert(subscriber_result.result == COAKKA_V2_STREAM_RESULT_OK);
    assert(publisher_result.state == COAKKA_V2_STREAM_STATE_ENDED);
    assert(publisher_result.result == COAKKA_V2_STREAM_RESULT_OK);
    assert(source.produced == kFrameCount);
    assert(consumer.consumed == kFrameCount);
    assert(source.bytes == consumer.bytes);
    assert(source.drops == consumer.drops);
    assert(publisher_result.frames == kFrameCount);
    assert(publisher_result.bytes == source.bytes);
    assert(subscriber_result.frames == kFrameCount);
    assert(subscriber_result.bytes == consumer.bytes);
    assert(subscriber_result.last_sequence == kFrameCount);

    memset(&publisher_pressure, 0, sizeof(publisher_pressure));
    publisher_pressure.struct_size = sizeof(publisher_pressure);
    assert(coakka_v2_stream_lane_get_pressure(
               publisher, kSessionId, COAKKA_V2_STREAM_DIRECTION_PUBLISH,
               &publisher_pressure) == COAKKA_V2_OK);
    assert(publisher_pressure.state == COAKKA_V2_STREAM_PRESSURE_INACTIVE);
    assert(publisher_pressure.window_capacity_bytes == kWindowBytes);
    assert(publisher_pressure.update_sequence > 0u);

    memset(&subscriber_pressure, 0, sizeof(subscriber_pressure));
    subscriber_pressure.struct_size = sizeof(subscriber_pressure);
    assert(coakka_v2_stream_lane_get_pressure(
               subscriber, kSessionId, COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE,
               &subscriber_pressure) == COAKKA_V2_OK);
    assert(subscriber_pressure.state == COAKKA_V2_STREAM_PRESSURE_INACTIVE);
    assert(subscriber_pressure.window_capacity_bytes == kWindowBytes);
    assert(subscriber_pressure.observed_delivery_bps > 0u);
    subscriber_pressure.struct_size = sizeof(subscriber_pressure);
    assert(coakka_v2_stream_lane_wait_pressure(
               subscriber, kSessionId, COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE,
               subscriber_pressure.update_sequence, 0u,
               &subscriber_pressure) == COAKKA_V2_ERR_WOULD_BLOCK);

    memset(&publisher_stats, 0, sizeof(publisher_stats));
    publisher_stats.struct_size = sizeof(publisher_stats);
    assert(coakka_v2_stream_lane_get_stats(publisher, &publisher_stats) ==
           COAKKA_V2_OK);
    assert(publisher_stats.ended_publishers == 1u);
    assert(publisher_stats.published_frames == kFrameCount);
    assert(publisher_stats.published_bytes == source.bytes);
    assert(publisher_stats.source_reported_drops == source.drops);

    memset(&subscriber_stats, 0, sizeof(subscriber_stats));
    subscriber_stats.struct_size = sizeof(subscriber_stats);
    assert(coakka_v2_stream_lane_get_stats(subscriber, &subscriber_stats) ==
           COAKKA_V2_OK);
    assert(subscriber_stats.ended_subscribers == 1u);
    assert(subscriber_stats.consumed_frames == kFrameCount);
    assert(subscriber_stats.consumed_bytes == consumer.bytes);

    assert(coakka_v2_stream_lane_forget_session(
               subscriber, kSessionId,
               COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE) == COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_forget_session(
               publisher, kSessionId,
               COAKKA_V2_STREAM_DIRECTION_PUBLISH) == COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_stop(subscriber) == COAKKA_V2_OK);
    assert(coakka_v2_stream_lane_stop(publisher) == COAKKA_V2_OK);
    coakka_v2_stream_lane_destroy(subscriber);
    coakka_v2_stream_lane_destroy(publisher);
    printf("{\"schema\":\"coakka.runtime.stream-lane.evidence.v1\"," 
           "\"passed\":true,\"frames\":%u,\"publishedBytes\":%" PRIu64 ","
           "\"consumedBytes\":%" PRIu64 ",\"sourceReportedDrops\":%" PRIu64 ","
           "\"publisherUpdateSequence\":%" PRIu64 ","
           "\"subscriberUpdateSequence\":%" PRIu64 ","
           "\"publisherObservedUpdates\":%" PRIu64 ","
           "\"subscriberObservedUpdates\":%" PRIu64 ","
           "\"publisherPressureSequence\":%" PRIu64 ","
           "\"subscriberPressureSequence\":%" PRIu64 ","
           "\"observedDeliveryBps\":%" PRIu64 "}\n",
           kFrameCount, source.bytes, consumer.bytes, source.drops,
           publisher_result.update_sequence, subscriber_result.update_sequence,
           publisher_observed_updates, subscriber_observed_updates,
           publisher_pressure.update_sequence, subscriber_pressure.update_sequence,
           subscriber_pressure.observed_delivery_bps);
    return 0;
}
