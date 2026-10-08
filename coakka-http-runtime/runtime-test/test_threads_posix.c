#include "test_threads.h"

#include <pthread.h>
#include <stdlib.h>

typedef struct test_thread_gate {
  pthread_mutex_t mutex;
  pthread_cond_t condition;
  size_t ready;
  size_t target;
  int released;
} test_thread_gate_t;

typedef struct test_thread_start {
  coakka_http_test_thread_fn worker;
  void *context;
  test_thread_gate_t *gate;
  int result;
} test_thread_start_t;

static void *thread_entry(void *opaque) {
  test_thread_start_t *start = (test_thread_start_t *)opaque;
  test_thread_gate_t *gate = start->gate;

  if (pthread_mutex_lock(&gate->mutex) != 0) {
    start->result = -1;
    return NULL;
  }
  gate->ready += 1U;
  if (gate->ready == gate->target) {
    (void)pthread_cond_broadcast(&gate->condition);
  }
  while (gate->released == 0 && start->result == 0) {
    if (pthread_cond_wait(&gate->condition, &gate->mutex) != 0) {
      start->result = -1;
    }
  }
  if (pthread_mutex_unlock(&gate->mutex) != 0) {
    start->result = -1;
  }
  if (start->result == 0) {
    start->result = start->worker(start->context);
  }
  return NULL;
}

int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count) {
  pthread_t *threads;
  test_thread_start_t *starts;
  test_thread_gate_t gate;
  size_t started = 0U;
  int result = 0;

  if (worker == NULL || contexts == NULL || count == 0U ||
      pthread_mutex_init(&gate.mutex, NULL) != 0) {
    return -1;
  }
  if (pthread_cond_init(&gate.condition, NULL) != 0) {
    (void)pthread_mutex_destroy(&gate.mutex);
    return -1;
  }
  gate.ready = 0U;
  gate.target = count;
  gate.released = 0;
  threads = (pthread_t *)calloc(count, sizeof(*threads));
  starts = (test_thread_start_t *)calloc(count, sizeof(*starts));
  if (threads == NULL || starts == NULL) {
    result = -1;
    goto cleanup;
  }

  for (started = 0U; started < count; ++started) {
    starts[started].worker = worker;
    starts[started].context = contexts[started];
    starts[started].gate = &gate;
    if (pthread_create(&threads[started], NULL, thread_entry,
                       &starts[started]) != 0) {
      result = -1;
      break;
    }
  }
  if (pthread_mutex_lock(&gate.mutex) != 0) {
    result = -1;
  } else {
    if (started == count) {
      while (gate.ready != gate.target) {
        if (pthread_cond_wait(&gate.condition, &gate.mutex) != 0) {
          result = -1;
          break;
        }
      }
    }
    gate.released = 1;
    if (pthread_cond_broadcast(&gate.condition) != 0) {
      result = -1;
    }
    if (pthread_mutex_unlock(&gate.mutex) != 0) {
      result = -1;
    }
  }
  while (started > 0U) {
    --started;
    if (pthread_join(threads[started], NULL) != 0 ||
        starts[started].result != 0) {
      result = -1;
    }
  }

cleanup:
  free(starts);
  free(threads);
  if (pthread_cond_destroy(&gate.condition) != 0 ||
      pthread_mutex_destroy(&gate.mutex) != 0) {
    result = -1;
  }
  return result;
}
