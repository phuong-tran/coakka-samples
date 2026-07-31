#include "evidence_platform.h"

#if defined(_WIN32)

#ifndef NOMINMAX
#define NOMINMAX 1
#endif
#include <windows.h>

#include <process.h>

uint64_t evidence_platform_monotonic_ns(void) {
  LARGE_INTEGER counter;
  LARGE_INTEGER frequency;
  uint64_t seconds;
  uint64_t remainder;

  if (!QueryPerformanceCounter(&counter) ||
      !QueryPerformanceFrequency(&frequency) || frequency.QuadPart <= 0) {
    return 0;
  }
  seconds = (uint64_t)(counter.QuadPart / frequency.QuadPart);
  remainder = (uint64_t)(counter.QuadPart % frequency.QuadPart);
  return seconds * 1000000000u +
         (remainder * 1000000000u) / (uint64_t)frequency.QuadPart;
}

unsigned int evidence_platform_process_id(void) {
  return (unsigned int)_getpid();
}

void evidence_platform_close_channel(int* channel) {
  if (channel != NULL) {
    /*
     * The descriptor was created by the runtime DLL's C runtime. Passing it
     * into a different host CRT can fast-fail; this process-scoped harness
     * leaves final descriptor reclamation to process teardown on Windows.
     */
    *channel = -1;
  }
}

int evidence_platform_wait_readable(const int* channels,
                                    size_t channel_count,
                                    unsigned int timeout_ms) {
  (void)channels;
  (void)channel_count;
  if (timeout_ms > 0) {
    /* frame_read_try remains the readiness authority across the DLL boundary. */
    Sleep(timeout_ms > 1u ? 1u : timeout_ms);
  }
  return 0;
}

long evidence_platform_logical_cpu_count(void) {
  const DWORD count = GetActiveProcessorCount(ALL_PROCESSOR_GROUPS);
  return count == 0 ? 0 : (long)count;
}

#else

#include <errno.h>
#include <poll.h>
#include <time.h>
#include <unistd.h>

#if defined(__APPLE__)
#include <sys/sysctl.h>
#endif

uint64_t evidence_platform_monotonic_ns(void) {
  struct timespec timestamp;
  if (clock_gettime(CLOCK_MONOTONIC, &timestamp) != 0) {
    return 0;
  }
  return (uint64_t)timestamp.tv_sec * 1000000000u +
         (uint64_t)timestamp.tv_nsec;
}

unsigned int evidence_platform_process_id(void) {
  return (unsigned int)getpid();
}

void evidence_platform_close_channel(int* channel) {
  if (channel != NULL && *channel >= 0) {
    close(*channel);
    *channel = -1;
  }
}

int evidence_platform_wait_readable(const int* channels,
                                    size_t channel_count,
                                    unsigned int timeout_ms) {
  struct pollfd poll_channels[3];
  size_t index;
  int result;

  if (channel_count > sizeof(poll_channels) / sizeof(poll_channels[0])) {
    return 1;
  }
  for (index = 0; index < channel_count; ++index) {
    poll_channels[index].fd = channels[index];
    poll_channels[index].events = POLLIN;
    poll_channels[index].revents = 0;
  }
  do {
    result = poll(poll_channels, channel_count, (int)timeout_ms);
  } while (result < 0 && errno == EINTR);
  return result < 0 ? 1 : 0;
}

long evidence_platform_logical_cpu_count(void) {
#if defined(__APPLE__)
  int count = 0;
  size_t count_size = sizeof(count);
  if (sysctlbyname("hw.logicalcpu", &count, &count_size, NULL, 0) == 0 &&
      count > 0) {
    return (long)count;
  }
  return 0;
#elif defined(_SC_NPROCESSORS_ONLN)
  const long count = sysconf(_SC_NPROCESSORS_ONLN);
  return count > 0 ? count : 0;
#else
  return 0;
#endif
}

#endif
