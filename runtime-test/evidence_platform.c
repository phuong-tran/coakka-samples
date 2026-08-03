#include "evidence_platform.h"

#include <stdlib.h>

#if defined(_WIN32)

#ifndef NOMINMAX
#define NOMINMAX 1
#endif
#include <windows.h>

#include <io.h>
#include <process.h>

typedef struct evidence_platform_thread_impl_t {
  HANDLE handle;
  evidence_platform_thread_fn function;
  void* context;
  int result;
} evidence_platform_thread_impl_t;

static DWORD WINAPI evidence_platform_thread_entry(LPVOID raw) {
  evidence_platform_thread_impl_t* implementation =
      (evidence_platform_thread_impl_t*)raw;
  implementation->result = implementation->function(implementation->context);
  return 0u;
}

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
  if (channel != NULL && *channel >= 0) {
    (void)_close(*channel);
    *channel = -1;
  }
}

int evidence_platform_wait_readable(const int* channels,
                                    size_t channel_count,
                                    unsigned int timeout_ms) {
  HANDLE handles[3];
  size_t index;
  const ULONGLONG deadline = GetTickCount64() + (ULONGLONG)timeout_ms;

  if (channels == NULL ||
      channel_count > sizeof(handles) / sizeof(handles[0])) {
    return 1;
  }
  if (channel_count == 0u) {
    return 0;
  }
  for (index = 0u; index < channel_count; ++index) {
    const intptr_t os_handle = _get_osfhandle(channels[index]);
    if (os_handle == -1) {
      return 1;
    }
    handles[index] = (HANDLE)os_handle;
  }
  for (;;) {
    for (index = 0u; index < channel_count; ++index) {
      DWORD available = 0u;
      if (PeekNamedPipe(handles[index], NULL, 0u, NULL, &available, NULL) != 0) {
        if (available > 0u) {
          return 0;
        }
        continue;
      }
      const DWORD error = GetLastError();
      if (error == ERROR_BROKEN_PIPE || error == ERROR_PIPE_NOT_CONNECTED) {
        return 0;
      }
      return 1;
    }
    if (GetTickCount64() >= deadline) {
      return 0;
    }
    /* CRT anonymous pipes have no pollable readiness handle on Windows. */
    Sleep(1u);
  }
}

const char* evidence_platform_wait_backend(void) {
  return "peek-named-pipe-bounded-poll";
}

long evidence_platform_logical_cpu_count(void) {
  const DWORD count = GetActiveProcessorCount(ALL_PROCESSOR_GROUPS);
  return count == 0 ? 0 : (long)count;
}

int evidence_platform_thread_start(evidence_platform_thread_t* thread,
                                   evidence_platform_thread_fn function,
                                   void* context) {
  evidence_platform_thread_impl_t* implementation;

  if (thread == NULL || function == NULL || thread->implementation != NULL) {
    return 1;
  }
  implementation = (evidence_platform_thread_impl_t*)calloc(
      1u, sizeof(*implementation));
  if (implementation == NULL) {
    return 1;
  }
  implementation->function = function;
  implementation->context = context;
  implementation->handle = CreateThread(NULL,
                                        0u,
                                        evidence_platform_thread_entry,
                                        implementation,
                                        0u,
                                        NULL);
  if (implementation->handle == NULL) {
    free(implementation);
    return 1;
  }
  thread->implementation = implementation;
  return 0;
}

int evidence_platform_thread_join(evidence_platform_thread_t* thread,
                                  int* out_result) {
  evidence_platform_thread_impl_t* implementation;
  int failed;

  if (thread == NULL || thread->implementation == NULL) {
    return 1;
  }
  implementation =
      (evidence_platform_thread_impl_t*)thread->implementation;
  if (WaitForSingleObject(implementation->handle, INFINITE) != WAIT_OBJECT_0) {
    return 1;
  }
  failed = !CloseHandle(implementation->handle);
  if (out_result != NULL) {
    *out_result = implementation->result;
  }
  free(implementation);
  thread->implementation = NULL;
  return failed;
}

void evidence_platform_thread_yield(void) {
  if (!SwitchToThread()) {
    Sleep(0u);
  }
}

#else

#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <time.h>
#include <unistd.h>

#if defined(__APPLE__)
#include <sys/sysctl.h>
#endif

typedef struct evidence_platform_thread_impl_t {
  pthread_t handle;
  evidence_platform_thread_fn function;
  void* context;
  int result;
} evidence_platform_thread_impl_t;

static void* evidence_platform_thread_entry(void* raw) {
  evidence_platform_thread_impl_t* implementation =
      (evidence_platform_thread_impl_t*)raw;
  implementation->result = implementation->function(implementation->context);
  return NULL;
}

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
  const int poll_timeout_ms =
      timeout_ms > (unsigned int)INT_MAX ? INT_MAX : (int)timeout_ms;

  if (channels == NULL ||
      channel_count > sizeof(poll_channels) / sizeof(poll_channels[0])) {
    return 1;
  }
  for (index = 0; index < channel_count; ++index) {
    poll_channels[index].fd = channels[index];
    poll_channels[index].events = POLLIN;
    poll_channels[index].revents = 0;
  }
  do {
    result = poll(poll_channels, channel_count, poll_timeout_ms);
  } while (result < 0 && errno == EINTR);
  return result < 0 ? 1 : 0;
}

const char* evidence_platform_wait_backend(void) { return "poll"; }

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

int evidence_platform_thread_start(evidence_platform_thread_t* thread,
                                   evidence_platform_thread_fn function,
                                   void* context) {
  evidence_platform_thread_impl_t* implementation;

  if (thread == NULL || function == NULL || thread->implementation != NULL) {
    return 1;
  }
  implementation = (evidence_platform_thread_impl_t*)calloc(
      1u, sizeof(*implementation));
  if (implementation == NULL) {
    return 1;
  }
  implementation->function = function;
  implementation->context = context;
  if (pthread_create(&implementation->handle,
                     NULL,
                     evidence_platform_thread_entry,
                     implementation) != 0) {
    free(implementation);
    return 1;
  }
  thread->implementation = implementation;
  return 0;
}

int evidence_platform_thread_join(evidence_platform_thread_t* thread,
                                  int* out_result) {
  evidence_platform_thread_impl_t* implementation;
  int failed;

  if (thread == NULL || thread->implementation == NULL) {
    return 1;
  }
  implementation =
      (evidence_platform_thread_impl_t*)thread->implementation;
  if (pthread_join(implementation->handle, NULL) != 0) {
    return 1;
  }
  failed = 0;
  if (out_result != NULL) {
    *out_result = implementation->result;
  }
  free(implementation);
  thread->implementation = NULL;
  return failed;
}

void evidence_platform_thread_yield(void) { (void)sched_yield(); }

#endif

void evidence_platform_close_channels(int* const channels[],
                                      size_t channel_count) {
  size_t index;

  if (channels == NULL) {
    return;
  }
  for (index = 0u; index < channel_count; ++index) {
    size_t duplicate_index;
    int channel;

    if (channels[index] == NULL || *channels[index] < 0) {
      continue;
    }
    channel = *channels[index];
    for (duplicate_index = index + 1u; duplicate_index < channel_count;
         ++duplicate_index) {
      if (channels[duplicate_index] != NULL &&
          *channels[duplicate_index] == channel) {
        *channels[duplicate_index] = -1;
      }
    }
    evidence_platform_close_channel(channels[index]);
  }
}
