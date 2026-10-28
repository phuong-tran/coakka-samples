#define _POSIX_C_SOURCE 200809L

#include "v4l2_mjpeg_camera_source.h"

#include <errno.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <poll.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

enum { COAKKA_V2_V4L2_MAX_BUFFERS = 4u };

typedef struct coakka_v2_v4l2_buffer_t {
  void *data;
  size_t size;
} coakka_v2_v4l2_buffer_t;

struct coakka_v2_v4l2_mjpeg_source_t {
  int fd;
  enum v4l2_buf_type buffer_type;
  coakka_v2_v4l2_buffer_t buffers[COAKKA_V2_V4L2_MAX_BUFFERS];
  uint32_t buffer_count;
  uint32_t width;
  uint32_t height;
  uint32_t fps;
  uint32_t max_frame_bytes;
  uint32_t poll_timeout_ms;
  uint32_t last_device_sequence;
  bool have_device_sequence;
  bool streaming;
  uint64_t delivered_frames;
  uint64_t delivered_bytes;
  uint64_t dropped_frames;
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

static int retry_ioctl(int fd, unsigned long request, void *argument) {
  int result;
  do {
    result = ioctl(fd, request, argument);
  } while (result < 0 && errno == EINTR);
  return result;
}

static uint64_t monotonic_now_ns(void) {
  struct timespec now;
  if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
    return 0u;
  }
  return (uint64_t)now.tv_sec * UINT64_C(1000000000) + (uint64_t)now.tv_nsec;
}

static uint64_t capture_time_ns(const struct v4l2_buffer *buffer) {
  if ((buffer->flags & V4L2_BUF_FLAG_TIMESTAMP_MASK) ==
      V4L2_BUF_FLAG_TIMESTAMP_MONOTONIC) {
    return (uint64_t)buffer->timestamp.tv_sec * UINT64_C(1000000000) +
           (uint64_t)buffer->timestamp.tv_usec * UINT64_C(1000);
  }
  return monotonic_now_ns();
}

static size_t complete_jpeg_size(const uint8_t *data, size_t size) {
  size_t index;
  if (data == NULL || size < 4u || data[0] != 0xffu || data[1] != 0xd8u) {
    return 0u;
  }
  for (index = size; index >= 2u; --index) {
    if (data[index - 2u] == 0xffu && data[index - 1u] == 0xd9u) {
      return index;
    }
  }
  return 0u;
}

static void close_source(coakka_v2_v4l2_mjpeg_source_t *source) {
  uint32_t index;
  if (source == NULL) {
    return;
  }
  if (source->streaming && source->fd >= 0) {
    (void)retry_ioctl(source->fd, VIDIOC_STREAMOFF, &source->buffer_type);
  }
  for (index = 0u; index < source->buffer_count; ++index) {
    if (source->buffers[index].data != NULL &&
        source->buffers[index].data != MAP_FAILED) {
      (void)munmap(source->buffers[index].data, source->buffers[index].size);
    }
  }
  if (source->fd >= 0) {
    (void)close(source->fd);
  }
  free(source);
}

coakka_v2_v4l2_mjpeg_source_t *
coakka_v2_v4l2_mjpeg_source_open(const coakka_v2_v4l2_mjpeg_config_t *config,
                                 char *detail, size_t detail_capacity) {
  coakka_v2_v4l2_mjpeg_source_t *source;
  struct v4l2_capability capabilities;
  struct v4l2_format format;
  struct v4l2_streamparm parameters;
  struct v4l2_requestbuffers requested;
  uint32_t capability_bits;
  uint32_t index;

  if (config == NULL || config->device == NULL || config->device[0] == '\0' ||
      config->width == 0u || config->height == 0u || config->fps == 0u ||
      config->buffer_count < 2u ||
      config->buffer_count > COAKKA_V2_V4L2_MAX_BUFFERS ||
      config->poll_timeout_ms == 0u) {
    set_detail(detail, detail_capacity, "invalid V4L2 camera configuration");
    return NULL;
  }

  source = (coakka_v2_v4l2_mjpeg_source_t *)calloc(1u, sizeof(*source));
  if (source == NULL) {
    set_detail(detail, detail_capacity, "V4L2 source allocation failed");
    return NULL;
  }
  source->fd = -1;
  source->buffer_type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
  source->poll_timeout_ms = config->poll_timeout_ms;
  source->fd = open(config->device, O_RDWR | O_NONBLOCK | O_CLOEXEC);
  if (source->fd < 0) {
    set_detail(detail, detail_capacity, "cannot open %s: %s", config->device,
               strerror(errno));
    close_source(source);
    return NULL;
  }

  memset(&capabilities, 0, sizeof(capabilities));
  if (retry_ioctl(source->fd, VIDIOC_QUERYCAP, &capabilities) != 0) {
    set_detail(detail, detail_capacity, "cannot query %s: %s", config->device,
               strerror(errno));
    close_source(source);
    return NULL;
  }
  capability_bits = (capabilities.capabilities & V4L2_CAP_DEVICE_CAPS) != 0u
                        ? capabilities.device_caps
                        : capabilities.capabilities;
  if ((capability_bits & V4L2_CAP_VIDEO_CAPTURE) == 0u ||
      (capability_bits & V4L2_CAP_STREAMING) == 0u) {
    set_detail(detail, detail_capacity,
               "%s is not a streaming V4L2 capture device", config->device);
    close_source(source);
    return NULL;
  }

  memset(&format, 0, sizeof(format));
  format.type = source->buffer_type;
  format.fmt.pix.width = config->width;
  format.fmt.pix.height = config->height;
  format.fmt.pix.pixelformat = V4L2_PIX_FMT_MJPEG;
  format.fmt.pix.field = V4L2_FIELD_NONE;
  if (retry_ioctl(source->fd, VIDIOC_S_FMT, &format) != 0 ||
      format.fmt.pix.pixelformat != V4L2_PIX_FMT_MJPEG ||
      format.fmt.pix.sizeimage == 0u) {
    set_detail(detail, detail_capacity, "%s cannot provide MJPEG: %s",
               config->device, strerror(errno));
    close_source(source);
    return NULL;
  }
  source->width = format.fmt.pix.width;
  source->height = format.fmt.pix.height;
  source->max_frame_bytes = format.fmt.pix.sizeimage;
  if (source->max_frame_bytes > COAKKA_V2_STREAM_LANE_MAX_FRAME_BYTES) {
    set_detail(detail, detail_capacity,
               "camera sizeimage %u exceeds Stream Lane frame limit %u",
               source->max_frame_bytes,
               (unsigned)COAKKA_V2_STREAM_LANE_MAX_FRAME_BYTES);
    close_source(source);
    return NULL;
  }

  memset(&parameters, 0, sizeof(parameters));
  parameters.type = source->buffer_type;
  parameters.parm.capture.timeperframe.numerator = 1u;
  parameters.parm.capture.timeperframe.denominator = config->fps;
  if (retry_ioctl(source->fd, VIDIOC_S_PARM, &parameters) != 0 ||
      parameters.parm.capture.timeperframe.numerator == 0u) {
    set_detail(detail, detail_capacity, "cannot configure camera FPS: %s",
               strerror(errno));
    close_source(source);
    return NULL;
  }
  source->fps = parameters.parm.capture.timeperframe.denominator /
                parameters.parm.capture.timeperframe.numerator;
  if (source->fps == 0u) {
    source->fps = config->fps;
  }

  memset(&requested, 0, sizeof(requested));
  requested.count = config->buffer_count;
  requested.type = source->buffer_type;
  requested.memory = V4L2_MEMORY_MMAP;
  if (retry_ioctl(source->fd, VIDIOC_REQBUFS, &requested) != 0 ||
      requested.count < 2u) {
    set_detail(detail, detail_capacity, "cannot allocate V4L2 buffers: %s",
               strerror(errno));
    close_source(source);
    return NULL;
  }
  source->buffer_count = requested.count < COAKKA_V2_V4L2_MAX_BUFFERS
                             ? requested.count
                             : COAKKA_V2_V4L2_MAX_BUFFERS;

  for (index = 0u; index < source->buffer_count; ++index) {
    struct v4l2_buffer buffer;
    memset(&buffer, 0, sizeof(buffer));
    buffer.type = source->buffer_type;
    buffer.memory = V4L2_MEMORY_MMAP;
    buffer.index = index;
    if (retry_ioctl(source->fd, VIDIOC_QUERYBUF, &buffer) != 0) {
      set_detail(detail, detail_capacity, "cannot query V4L2 buffer: %s",
                 strerror(errno));
      close_source(source);
      return NULL;
    }
    source->buffers[index].size = buffer.length;
    source->buffers[index].data =
        mmap(NULL, buffer.length, PROT_READ | PROT_WRITE, MAP_SHARED,
             source->fd, (off_t)buffer.m.offset);
    if (source->buffers[index].data == MAP_FAILED) {
      set_detail(detail, detail_capacity, "cannot map V4L2 buffer: %s",
                 strerror(errno));
      close_source(source);
      return NULL;
    }
    if (retry_ioctl(source->fd, VIDIOC_QBUF, &buffer) != 0) {
      set_detail(detail, detail_capacity, "cannot queue V4L2 buffer: %s",
                 strerror(errno));
      close_source(source);
      return NULL;
    }
  }

  if (retry_ioctl(source->fd, VIDIOC_STREAMON, &source->buffer_type) != 0) {
    set_detail(detail, detail_capacity, "cannot start V4L2 capture: %s",
               strerror(errno));
    close_source(source);
    return NULL;
  }
  source->streaming = true;
  set_detail(detail, detail_capacity, "%ux%u@%u MJPEG sizeimage=%u buffers=%u",
             source->width, source->height, source->fps,
             source->max_frame_bytes, source->buffer_count);
  return source;
}

void coakka_v2_v4l2_mjpeg_source_close(coakka_v2_v4l2_mjpeg_source_t *source) {
  close_source(source);
}

int coakka_v2_v4l2_mjpeg_source_snapshot(
    const coakka_v2_v4l2_mjpeg_source_t *source,
    coakka_v2_v4l2_mjpeg_snapshot_t *snapshot) {
  if (source == NULL || snapshot == NULL) {
    return -1;
  }
  snapshot->width = source->width;
  snapshot->height = source->height;
  snapshot->fps = source->fps;
  snapshot->max_frame_bytes = source->max_frame_bytes;
  snapshot->delivered_frames = source->delivered_frames;
  snapshot->delivered_bytes = source->delivered_bytes;
  snapshot->dropped_frames = source->dropped_frames;
  return 0;
}

coakka_v2_status_t
coakka_v2_v4l2_mjpeg_source_next(void *context, uint8_t *destination,
                                 size_t capacity,
                                 coakka_v2_stream_frame_t *out_frame) {
  coakka_v2_v4l2_mjpeg_source_t *source =
      (coakka_v2_v4l2_mjpeg_source_t *)context;
  struct pollfd descriptor;
  uint64_t dropped_before = 0u;
  uint64_t captured_mono_ns = 0u;
  size_t selected_size = 0u;
  uint32_t dequeued = 0u;

  if (source == NULL || destination == NULL || out_frame == NULL ||
      capacity < source->max_frame_bytes) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }

  memset(&descriptor, 0, sizeof(descriptor));
  descriptor.fd = source->fd;
  descriptor.events = POLLIN;
  for (;;) {
    const int result = poll(&descriptor, 1u, (int)source->poll_timeout_ms);
    if (result > 0) {
      break;
    }
    if (result == 0) {
      return COAKKA_V2_ERR_WOULD_BLOCK;
    }
    if (errno != EINTR) {
      return COAKKA_V2_ERR_IO;
    }
  }
  if ((descriptor.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
    return COAKKA_V2_ERR_IO;
  }

  for (;;) {
    struct v4l2_buffer buffer;
    const uint8_t *bytes;
    size_t jpeg_size;
    memset(&buffer, 0, sizeof(buffer));
    buffer.type = source->buffer_type;
    buffer.memory = V4L2_MEMORY_MMAP;
    if (retry_ioctl(source->fd, VIDIOC_DQBUF, &buffer) != 0) {
      if (errno == EAGAIN) {
        break;
      }
      return COAKKA_V2_ERR_IO;
    }
    if (buffer.index >= source->buffer_count) {
      return COAKKA_V2_ERR_IO;
    }
    if (buffer.bytesused > source->buffers[buffer.index].size) {
      (void)retry_ioctl(source->fd, VIDIOC_QBUF, &buffer);
      return COAKKA_V2_ERR_IO;
    }

    if (source->have_device_sequence &&
        buffer.sequence > source->last_device_sequence + 1u) {
      dropped_before +=
          (uint64_t)(buffer.sequence - source->last_device_sequence - 1u);
    }
    source->last_device_sequence = buffer.sequence;
    source->have_device_sequence = true;
    bytes = (const uint8_t *)source->buffers[buffer.index].data;
    jpeg_size = complete_jpeg_size(bytes, buffer.bytesused);
    if (jpeg_size == 0u || jpeg_size > capacity) {
      if (retry_ioctl(source->fd, VIDIOC_QBUF, &buffer) != 0) {
        return COAKKA_V2_ERR_IO;
      }
      return jpeg_size > capacity ? COAKKA_V2_ERR_INVALID_ARG
                                  : COAKKA_V2_ERR_IO;
    }
    memcpy(destination, bytes, jpeg_size);
    selected_size = jpeg_size;
    captured_mono_ns = capture_time_ns(&buffer);
    ++dequeued;
    if (retry_ioctl(source->fd, VIDIOC_QBUF, &buffer) != 0) {
      return COAKKA_V2_ERR_IO;
    }
  }

  if (dequeued == 0u || selected_size == 0u) {
    return COAKKA_V2_ERR_WOULD_BLOCK;
  }
  dropped_before += dequeued - 1u;
  source->dropped_frames += dropped_before;
  ++source->delivered_frames;
  source->delivered_bytes += selected_size;
  out_frame->captured_mono_ns = captured_mono_ns;
  out_frame->dropped_before = dropped_before;
  out_frame->flags = COAKKA_V2_STREAM_LANE_FRAME_FLAG_KEYFRAME;
  out_frame->size = selected_size;
  return COAKKA_V2_OK;
}
