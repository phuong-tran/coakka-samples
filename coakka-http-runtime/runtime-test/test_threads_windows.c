#include "test_threads.h"

#if !defined(WIN32_LEAN_AND_MEAN)
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <limits.h>
#include <stdlib.h>

typedef struct test_thread_start {
  coakka_http_test_thread_fn worker;
  void *context;
  HANDLE ready_event;
  HANDLE start_event;
  volatile LONG *ready_count;
  LONG target;
  int result;
} test_thread_start_t;

static DWORD WINAPI thread_entry(LPVOID opaque) {
  test_thread_start_t *start = (test_thread_start_t *)opaque;
  if (InterlockedIncrement(start->ready_count) == start->target &&
      SetEvent(start->ready_event) == 0) {
    start->result = -1;
    return 0U;
  }
  if (WaitForSingleObject(start->start_event, INFINITE) != WAIT_OBJECT_0) {
    start->result = -1;
    return 0U;
  }
  start->result = start->worker(start->context);
  return 0U;
}

int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count) {
  HANDLE *threads;
  HANDLE ready_event;
  HANDLE start_event;
  test_thread_start_t *starts;
  volatile LONG ready_count = 0;
  size_t started = 0U;
  int result = 0;

  if (worker == NULL || contexts == NULL || count == 0U ||
      count > (size_t)LONG_MAX) {
    return -1;
  }
  ready_event = CreateEventW(NULL, TRUE, FALSE, NULL);
  start_event = CreateEventW(NULL, TRUE, FALSE, NULL);
  if (ready_event == NULL || start_event == NULL) {
    if (start_event != NULL) {
      (void)CloseHandle(start_event);
    }
    if (ready_event != NULL) {
      (void)CloseHandle(ready_event);
    }
    return -1;
  }
  threads = (HANDLE *)calloc(count, sizeof(*threads));
  starts = (test_thread_start_t *)calloc(count, sizeof(*starts));
  if (threads == NULL || starts == NULL) {
    result = -1;
    goto cleanup;
  }
  for (started = 0U; started < count; ++started) {
    starts[started].worker = worker;
    starts[started].context = contexts[started];
    starts[started].ready_event = ready_event;
    starts[started].start_event = start_event;
    starts[started].ready_count = &ready_count;
    starts[started].target = (LONG)count;
    threads[started] =
        CreateThread(NULL, 0U, thread_entry, &starts[started], 0U, NULL);
    if (threads[started] == NULL) {
      result = -1;
      break;
    }
  }
  if (started == count &&
      WaitForSingleObject(ready_event, INFINITE) != WAIT_OBJECT_0) {
    result = -1;
  }
  if (SetEvent(start_event) == 0) {
    result = -1;
  }
  while (started > 0U) {
    --started;
    if (WaitForSingleObject(threads[started], INFINITE) != WAIT_OBJECT_0 ||
        starts[started].result != 0) {
      result = -1;
    }
    (void)CloseHandle(threads[started]);
  }

cleanup:
  free(starts);
  free(threads);
  if (CloseHandle(start_event) == 0 || CloseHandle(ready_event) == 0) {
    result = -1;
  }
  return result;
}
