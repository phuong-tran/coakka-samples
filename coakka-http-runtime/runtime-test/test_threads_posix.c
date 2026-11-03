#include "test_threads.h"

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct test_thread_gate {
  pthread_mutex_t mutex;
  pthread_cond_t condition;
  int released;
} test_thread_gate_t;

typedef struct test_thread_start {
  coakka_http_test_thread_fn worker;
  void *context;
  test_thread_gate_t *gate;
  int result;
} test_thread_start_t;

/* Once a child exists, an unproven coordination/join cannot return to callers
 * that own its stack context. Abort this test process instead of freeing a
 * possibly live owner's storage and accidentally reporting successful cleanup. */
static void fixture_failure(const char *operation, int code) {
  (void)fprintf(stderr, "thread fixture %s failed: %d\n", operation, code);
  abort();
}

static void checked(int code, const char *operation) {
  if (code != 0) fixture_failure(operation, code);
}

static void *thread_entry(void *opaque) {
  test_thread_start_t *start = (test_thread_start_t *)opaque;
  checked(pthread_mutex_lock(&start->gate->mutex), "gate lock");
  while (!start->gate->released)
    checked(pthread_cond_wait(&start->gate->condition, &start->gate->mutex), "gate wait");
  checked(pthread_mutex_unlock(&start->gate->mutex), "gate unlock");
  start->result = start->worker(start->context);
  return NULL;
}

int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count) {
  pthread_t *threads = NULL;
  test_thread_start_t *starts = NULL;
  test_thread_gate_t gate;
  size_t started = 0U;
  int result = 0;
  if (worker == NULL || contexts == NULL || count == 0U ||
      count > COAKKA_HTTP_TEST_MAX_THREADS) return -1;
  if (pthread_mutex_init(&gate.mutex, NULL) != 0) return -1;
  if (pthread_cond_init(&gate.condition, NULL) != 0) {
    checked(pthread_mutex_destroy(&gate.mutex), "mutex cleanup");
    return -1;
  }
  gate.released = 0;
  threads = (pthread_t *)calloc(count, sizeof(*threads));
  starts = (test_thread_start_t *)calloc(count, sizeof(*starts));
  if (threads == NULL || starts == NULL) { result = -1; goto cleanup; }
  for (; started < count; ++started) {
    starts[started].worker = worker;
    starts[started].context = contexts[started];
    starts[started].gate = &gate;
    if (pthread_create(&threads[started], NULL, thread_entry, &starts[started]) != 0) {
      result = -1;
      break;
    }
  }
  /* Publishing one sticky gate after creation requires no "all ready" wait.
   * Late starters see released under the same mutex; partial creation releases
   * every child that actually exists rather than waiting for missing children. */
  checked(pthread_mutex_lock(&gate.mutex), "release lock");
  gate.released = 1;
  checked(pthread_cond_broadcast(&gate.condition), "release broadcast");
  checked(pthread_mutex_unlock(&gate.mutex), "release unlock");
  while (started > 0U) {
    --started;
    /* Workers have finite Core/socket budgets. CTest is the process watchdog;
     * portable pthread_join itself is not a hard realtime deadline guarantee. */
    checked(pthread_join(threads[started], NULL), "join");
    if (starts[started].result != 0) result = -1;
  }
cleanup:
  free(starts);
  free(threads);
  checked(pthread_cond_destroy(&gate.condition), "condition cleanup");
  checked(pthread_mutex_destroy(&gate.mutex), "mutex cleanup");
  return result;
}
