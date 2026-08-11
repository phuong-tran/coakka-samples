#define _POSIX_C_SOURCE 200809L

#include "alsa_pcm_audio_source.h"

#include <alsa/asoundlib.h>

#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

enum {
  COAKKA_V2_CAMERA_AUDIO_SAMPLES = COAKKA_V2_CAMERA_AUDIO_SAMPLE_RATE *
                                   COAKKA_V2_CAMERA_AUDIO_FRAME_MS / 1000u,
  COAKKA_V2_CAMERA_AUDIO_WAIT_MS = 100u
};

struct coakka_v2_alsa_pcm_source_t {
  snd_pcm_t *pcm;
  uint8_t pending[COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES];
  size_t pending_bytes;
  uint64_t delivered_frames;
  uint64_t delivered_bytes;
  uint64_t recovered_overruns;
  uint64_t dropped_before;
};

static void set_detail(char *detail, size_t capacity, const char *format, ...) {
  va_list arguments;
  if (detail == NULL || capacity == 0u) {
    return;
  }
  va_start(arguments, format);
  (void)vsnprintf(detail, capacity, format, arguments);
  va_end(arguments);
}

static uint64_t monotonic_now_ns(void) {
  struct timespec now;
  if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
    return 0u;
  }
  return (uint64_t)now.tv_sec * UINT64_C(1000000000) + (uint64_t)now.tv_nsec;
}

static int recover_capture(coakka_v2_alsa_pcm_source_t *source, int error) {
  int result = snd_pcm_recover(source->pcm, error, 1);
  if (result >= 0) {
    result = snd_pcm_start(source->pcm);
  }
  return result;
}

coakka_v2_alsa_pcm_source_t *
coakka_v2_alsa_pcm_source_open(const char *device, char *detail,
                               size_t detail_capacity) {
  coakka_v2_alsa_pcm_source_t *source;
  int result;
  if (device == NULL || device[0] == '\0') {
    set_detail(detail, detail_capacity, "invalid ALSA capture device");
    return NULL;
  }
  source = (coakka_v2_alsa_pcm_source_t *)calloc(1u, sizeof(*source));
  if (source == NULL) {
    set_detail(detail, detail_capacity, "ALSA source allocation failed");
    return NULL;
  }
  result = snd_pcm_open(&source->pcm, device, SND_PCM_STREAM_CAPTURE,
                        SND_PCM_NONBLOCK);
  if (result < 0) {
    set_detail(detail, detail_capacity, "cannot open %s: %s", device,
               snd_strerror(result));
    free(source);
    return NULL;
  }
  result = snd_pcm_set_params(source->pcm, SND_PCM_FORMAT_S16_LE,
                              SND_PCM_ACCESS_RW_INTERLEAVED,
                              COAKKA_V2_CAMERA_AUDIO_CHANNELS,
                              COAKKA_V2_CAMERA_AUDIO_SAMPLE_RATE, 1, 100000u);
  if (result < 0) {
    set_detail(detail, detail_capacity, "cannot configure %s: %s", device,
               snd_strerror(result));
    snd_pcm_close(source->pcm);
    free(source);
    return NULL;
  }
  result = snd_pcm_start(source->pcm);
  if (result < 0) {
    set_detail(detail, detail_capacity, "cannot start %s: %s", device,
               snd_strerror(result));
    snd_pcm_close(source->pcm);
    free(source);
    return NULL;
  }
  set_detail(detail, detail_capacity, "%s S16_LE mono 48000 Hz 20 ms", device);
  return source;
}

void coakka_v2_alsa_pcm_source_close(coakka_v2_alsa_pcm_source_t *source) {
  if (source == NULL) {
    return;
  }
  if (source->pcm != NULL) {
    (void)snd_pcm_drop(source->pcm);
    (void)snd_pcm_close(source->pcm);
  }
  free(source);
}

int coakka_v2_alsa_pcm_source_snapshot(
    const coakka_v2_alsa_pcm_source_t *source,
    coakka_v2_alsa_pcm_snapshot_t *snapshot) {
  if (source == NULL || snapshot == NULL) {
    return -1;
  }
  snapshot->delivered_frames = source->delivered_frames;
  snapshot->delivered_bytes = source->delivered_bytes;
  snapshot->recovered_overruns = source->recovered_overruns;
  return 0;
}

coakka_v2_status_t
coakka_v2_alsa_pcm_source_next(void *context, uint8_t *destination,
                               size_t capacity,
                               coakka_v2_stream_frame_t *out_frame) {
  coakka_v2_alsa_pcm_source_t *source = (coakka_v2_alsa_pcm_source_t *)context;
  if (source == NULL || destination == NULL || out_frame == NULL ||
      capacity < COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }

  while (source->pending_bytes < COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES) {
    const size_t remaining =
        COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES - source->pending_bytes;
    const snd_pcm_sframes_t requested =
        (snd_pcm_sframes_t)(remaining / (2u * COAKKA_V2_CAMERA_AUDIO_CHANNELS));
    const int ready = snd_pcm_wait(source->pcm, COAKKA_V2_CAMERA_AUDIO_WAIT_MS);
    if (ready == 0) {
      return COAKKA_V2_ERR_WOULD_BLOCK;
    }
    if (ready < 0) {
      const int recovered = recover_capture(source, ready);
      if (recovered < 0) {
        return COAKKA_V2_ERR_IO;
      }
      ++source->recovered_overruns;
      ++source->dropped_before;
      source->pending_bytes = 0u;
      continue;
    }
    const snd_pcm_sframes_t frames =
        snd_pcm_readi(source->pcm, source->pending + source->pending_bytes,
                      (snd_pcm_uframes_t)requested);
    if (frames == -EAGAIN) {
      return COAKKA_V2_ERR_WOULD_BLOCK;
    }
    if (frames == 0) {
      return COAKKA_V2_ERR_WOULD_BLOCK;
    }
    if (frames < 0) {
      const int recovered = recover_capture(source, (int)frames);
      if (recovered < 0) {
        return COAKKA_V2_ERR_IO;
      }
      ++source->recovered_overruns;
      ++source->dropped_before;
      source->pending_bytes = 0u;
      continue;
    }
    source->pending_bytes +=
        (size_t)frames * 2u * COAKKA_V2_CAMERA_AUDIO_CHANNELS;
  }

  memcpy(destination, source->pending, COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES);
  source->pending_bytes = 0u;
  ++source->delivered_frames;
  source->delivered_bytes += COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES;
  out_frame->captured_mono_ns = monotonic_now_ns();
  out_frame->dropped_before = source->dropped_before;
  source->dropped_before = 0u;
  out_frame->flags = 0u;
  out_frame->size = COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES;
  return COAKKA_V2_OK;
}
