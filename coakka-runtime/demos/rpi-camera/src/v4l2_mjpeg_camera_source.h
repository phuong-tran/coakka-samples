#ifndef COAKKA_V2_EXAMPLES_V4L2_MJPEG_CAMERA_SOURCE_H
#define COAKKA_V2_EXAMPLES_V4L2_MJPEG_CAMERA_SOURCE_H

#include "coakka/v2/stream_lane.h"

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct coakka_v2_v4l2_mjpeg_source_t coakka_v2_v4l2_mjpeg_source_t;

typedef struct coakka_v2_v4l2_mjpeg_config_t {
  const char *device;
  uint32_t width;
  uint32_t height;
  uint32_t fps;
  uint32_t buffer_count;
  uint32_t poll_timeout_ms;
} coakka_v2_v4l2_mjpeg_config_t;

typedef struct coakka_v2_v4l2_mjpeg_snapshot_t {
  uint32_t width;
  uint32_t height;
  uint32_t fps;
  uint32_t max_frame_bytes;
  uint64_t delivered_frames;
  uint64_t delivered_bytes;
  uint64_t dropped_frames;
} coakka_v2_v4l2_mjpeg_snapshot_t;

coakka_v2_v4l2_mjpeg_source_t *
coakka_v2_v4l2_mjpeg_source_open(const coakka_v2_v4l2_mjpeg_config_t *config,
                                 char *detail, size_t detail_capacity);

void coakka_v2_v4l2_mjpeg_source_close(coakka_v2_v4l2_mjpeg_source_t *source);

int coakka_v2_v4l2_mjpeg_source_snapshot(
    const coakka_v2_v4l2_mjpeg_source_t *source,
    coakka_v2_v4l2_mjpeg_snapshot_t *snapshot);

coakka_v2_status_t
coakka_v2_v4l2_mjpeg_source_next(void *context, uint8_t *destination,
                                 size_t capacity,
                                 coakka_v2_stream_frame_t *out_frame);

#ifdef __cplusplus
}
#endif

#endif
