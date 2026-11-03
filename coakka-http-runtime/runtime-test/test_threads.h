#ifndef COAKKA_HTTP_RUNTIME_TEST_THREADS_H
#define COAKKA_HTTP_RUNTIME_TEST_THREADS_H

#include <stddef.h>

typedef int (*coakka_http_test_thread_fn)(void *context);

/* Fixture budget, unrelated to Core thread/CPU/resource defaults. */
#define COAKKA_HTTP_TEST_MAX_THREADS 32U

/* Release all created workers through one sticky gate and join every started
 * child. Workload/creation failure returns -1 after proven joins. Unknown OS
 * coordination/completion aborts the fixture; callers must never free contexts
 * that might still be live. The external test runner provides a process limit. */
int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count);

#endif
