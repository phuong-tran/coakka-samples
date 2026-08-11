#ifndef COAKKA_V2_EXAMPLES_ALSA_PCM_AUDIO_SOURCE_H
#define COAKKA_V2_EXAMPLES_ALSA_PCM_AUDIO_SOURCE_H

#include "coakka/v2/stream_lane.h"

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum {
  COAKKA_V2_CAMERA_AUDIO_SAMPLE_RATE = 48000u,
  COAKKA_V2_CAMERA_AUDIO_CHANNELS = 1u,
  COAKKA_V2_CAMERA_AUDIO_FRAME_MS = 20u,
  COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES = 1920u
};

typedef struct coakka_v2_alsa_pcm_source_t coakka_v2_alsa_pcm_source_t;

typedef struct coakka_v2_alsa_pcm_snapshot_t {
  uint64_t delivered_frames;
  uint64_t delivered_bytes;
  uint64_t recovered_overruns;
} coakka_v2_alsa_pcm_snapshot_t;

coakka_v2_alsa_pcm_source_t *
coakka_v2_alsa_pcm_source_open(const char *device, char *detail,
                               size_t detail_capacity);

void coakka_v2_alsa_pcm_source_close(coakka_v2_alsa_pcm_source_t *source);

int coakka_v2_alsa_pcm_source_snapshot(
    const coakka_v2_alsa_pcm_source_t *source,
    coakka_v2_alsa_pcm_snapshot_t *snapshot);

coakka_v2_status_t
coakka_v2_alsa_pcm_source_next(void *context, uint8_t *destination,
                               size_t capacity,
                               coakka_v2_stream_frame_t *out_frame);

#ifdef __cplusplus
}
#endif

#endif
