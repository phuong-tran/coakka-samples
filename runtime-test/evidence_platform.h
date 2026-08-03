#ifndef COAKKA_RUNTIME_NATIVE_EVIDENCE_PLATFORM_H
#define COAKKA_RUNTIME_NATIVE_EVIDENCE_PLATFORM_H

#include <stddef.h>
#include <stdint.h>

typedef int (*evidence_platform_thread_fn)(void* context);

typedef struct evidence_platform_thread_t {
  void* implementation;
} evidence_platform_thread_t;

uint64_t evidence_platform_monotonic_ns(void);
unsigned int evidence_platform_process_id(void);
void evidence_platform_close_channel(int* channel);
void evidence_platform_close_channels(int* const channels[],
                                      size_t channel_count);
int evidence_platform_wait_readable(const int* channels,
                                    size_t channel_count,
                                    unsigned int timeout_ms);
const char* evidence_platform_wait_backend(void);
long evidence_platform_logical_cpu_count(void);
int evidence_platform_thread_start(evidence_platform_thread_t* thread,
                                   evidence_platform_thread_fn function,
                                   void* context);
int evidence_platform_thread_join(evidence_platform_thread_t* thread,
                                  int* out_result);
void evidence_platform_thread_yield(void);

#endif
