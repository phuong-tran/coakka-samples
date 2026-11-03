/* White-box fixture failure seam, POSIX only. Runtime code is not intercepted.
 * Partial creation must release/join every actual child before context storage
 * becomes reclaimable; a failed workload is not a failed join. */
#include "test_threads.h"
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdio.h>

static size_t create_calls, fail_at;
static int create_with_refusal(pthread_t *thread, const pthread_attr_t *attributes,
                                void *(*entry)(void *), void *context) {
  if (create_calls++ == fail_at) return EAGAIN;
  return pthread_create(thread, attributes, entry, context);
}
#define pthread_create create_with_refusal
#include "test_threads_posix.c"
#undef pthread_create

typedef struct context { unsigned visits; int refuse; } context_t;
static int worker(void *opaque) {
  context_t *context = (context_t *)opaque;
  ++context->visits;
  return context->refuse ? -1 : 0;
}
int main(void) {
  context_t values[4];
  void *contexts[4];
  size_t failure, i;
  for (failure = 0U; failure <= 4U; ++failure) {
    for (i = 0U; i < 4U; ++i) {
      values[i].visits = 0U; values[i].refuse = 0; contexts[i] = &values[i];
    }
    create_calls = 0U; fail_at = failure;
    if (coakka_http_test_run_threads(worker, contexts, 4U) != (failure < 4U ? -1 : 0)) return 1;
    /* Reading child results is legal here only because the helper has joined
     * every thread it created, including the partially created group. */
    for (i = 0U; i < 4U; ++i)
      if (values[i].visits != (i < failure ? 1U : 0U)) return 1;
  }
  fail_at = 4U; create_calls = 0U; values[2].refuse = 1;
  if (coakka_http_test_run_threads(worker, contexts, 4U) != -1) return 1;
  if (coakka_http_test_run_threads(NULL, contexts, 4U) != -1 ||
      coakka_http_test_run_threads(worker, contexts, COAKKA_HTTP_TEST_MAX_THREADS + 1U) != -1)
    return 1;
  (void)puts("{\"case\":\"fixture_partial_threads\",\"ok\":true}");
  return 0;
}
