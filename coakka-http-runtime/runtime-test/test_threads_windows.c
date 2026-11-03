#include "test_threads.h"

#if !defined(WIN32_LEAN_AND_MEAN)
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct test_thread_start {
  coakka_http_test_thread_fn worker;
  void *context;
  HANDLE start_event;
  int result;
} test_thread_start_t;

static void fixture_failure(const char *operation) {
  (void)fprintf(stderr, "thread fixture %s failed: %lu\n", operation,
                 (unsigned long)GetLastError());
  /* Never close shared coordination or free contexts before child completion
   * is proven. A failed fixture is diagnosable, not a successful cleanup. */
  abort();
}

static DWORD WINAPI thread_entry(LPVOID opaque) {
  test_thread_start_t *start = (test_thread_start_t *)opaque;
  if (WaitForSingleObject(start->start_event, 10000U) != WAIT_OBJECT_0) {
    start->result = -1;
    return 0U;
  }
  start->result = start->worker(start->context);
  return 0U;
}

int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count) {
  HANDLE *threads = NULL;
  HANDLE start_event;
  test_thread_start_t *starts = NULL;
  size_t started = 0U;
  int result = 0;
  if (worker == NULL || contexts == NULL || count == 0U ||
      count > COAKKA_HTTP_TEST_MAX_THREADS) return -1;
  start_event = CreateEventW(NULL, TRUE, FALSE, NULL);
  if (start_event == NULL) return -1;
  threads = (HANDLE *)calloc(count, sizeof(*threads));
  starts = (test_thread_start_t *)calloc(count, sizeof(*starts));
  if (threads == NULL || starts == NULL) { result = -1; goto cleanup; }
  for (; started < count; ++started) {
    starts[started].worker = worker;
    starts[started].context = contexts[started];
    starts[started].start_event = start_event;
    threads[started] = CreateThread(NULL, 0U, thread_entry, &starts[started], 0U, NULL);
    if (threads[started] == NULL) { result = -1; break; }
  }
  /* The manual-reset gate is sticky for late starters, including a partial
   * creation failure. No unbounded "ready count" can deadlock the parent. */
  if (!SetEvent(start_event)) fixture_failure("release gate");
  while (started > 0U) {
    --started;
    if (WaitForSingleObject(threads[started], 60000U) != WAIT_OBJECT_0)
      fixture_failure("join");
    if (starts[started].result != 0) result = -1;
    if (!CloseHandle(threads[started])) fixture_failure("close thread");
  }
cleanup:
  free(starts);
  free(threads);
  if (!CloseHandle(start_event)) fixture_failure("close gate");
  return result;
}
