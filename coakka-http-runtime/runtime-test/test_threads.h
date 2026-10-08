#ifndef COAKKA_HTTP_RUNTIME_TEST_THREADS_H
#define COAKKA_HTTP_RUNTIME_TEST_THREADS_H

#include <stddef.h>

typedef int (*coakka_http_test_thread_fn)(void *context);

/* Starts all workers at one shared gate and joins every started thread. */
int coakka_http_test_run_threads(coakka_http_test_thread_fn worker,
                                 void **contexts, size_t count);

#endif
