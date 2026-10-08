/* Response streaming and SSE through the installed C callback API.
 *
 * A stream becomes writable only AFTER its request callback returns. The
 * callback therefore hands one writer to main, which produces a finite body.
 * One application-owned slot bounds retained writers; overlap returns 503.
 * This deliberately small producer is an example policy, not a Core limit.
 */
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE 1
#elif !defined(_WIN32)
#define _POSIX_C_SOURCE 200809L
#endif
#include "../native/sample_options.h"
#include <signal.h>
#include <stdatomic.h>
#if defined(_WIN32)
#include <windows.h>
#else
#include <errno.h>
#include <pthread.h>
#include <time.h>
#endif

typedef struct writer_job {
  coakka_http_response_stream_t *stream;
  int sse;
} writer_job_t;

typedef struct writer_slot {
#if defined(_WIN32)
  SRWLOCK mutex;
  CONDITION_VARIABLE ready_signal;
#else
  pthread_mutex_t mutex;
  pthread_cond_t ready_signal;
#endif
  int busy, ready, closing;
  writer_job_t job;
} writer_slot_t;

static volatile sig_atomic_t stopping = 0;
static atomic_int failed = 0;
static void stop_signal(int number) { (void)number; stopping = 1; }

static void sync_check(int code) {
  if (code != 0) {
    fputs("sample synchronization failed\n", stderr);
    abort(); /* Never continue with ambiguous shared-writer ownership. */
  }
}
static void lock(writer_slot_t *slot) {
#if defined(_WIN32)
  AcquireSRWLockExclusive(&slot->mutex);
#else
  sync_check(pthread_mutex_lock(&slot->mutex));
#endif
}
static void unlock(writer_slot_t *slot) {
#if defined(_WIN32)
  ReleaseSRWLockExclusive(&slot->mutex);
#else
  sync_check(pthread_mutex_unlock(&slot->mutex));
#endif
}
static void signal_ready(writer_slot_t *slot) {
#if defined(_WIN32)
  WakeConditionVariable(&slot->ready_signal);
#else
  sync_check(pthread_cond_signal(&slot->ready_signal));
#endif
}

/* Relative/monotonic waits only. The finite wake also observes process signals;
 * the predicate and publication use the same mutex, so a wake cannot be lost.
 */
static void wait_ready(writer_slot_t *slot) {
#if defined(_WIN32)
  if (!SleepConditionVariableSRW(&slot->ready_signal, &slot->mutex, 50, 0) &&
      GetLastError() != ERROR_TIMEOUT) sync_check(1);
#elif defined(__APPLE__)
  const struct timespec delay = {0, 50000000L};
  const int result = pthread_cond_timedwait_relative_np(&slot->ready_signal, &slot->mutex, &delay);
  if (result != ETIMEDOUT) sync_check(result);
#else
  struct timespec deadline;
  int result;
  sync_check(clock_gettime(CLOCK_MONOTONIC, &deadline));
  deadline.tv_nsec += 50000000L;
  if (deadline.tv_nsec >= 1000000000L) { ++deadline.tv_sec; deadline.tv_nsec -= 1000000000L; }
  result = pthread_cond_timedwait(&slot->ready_signal, &slot->mutex, &deadline);
  if (result != ETIMEDOUT) sync_check(result);
#endif
}
static void slot_init(writer_slot_t *slot) {
  memset(slot, 0, sizeof(*slot));
#if defined(_WIN32)
  InitializeSRWLock(&slot->mutex); InitializeConditionVariable(&slot->ready_signal);
#else
  pthread_condattr_t attributes;
  sync_check(pthread_mutex_init(&slot->mutex, NULL));
  sync_check(pthread_condattr_init(&attributes));
#if !defined(__APPLE__)
  sync_check(pthread_condattr_setclock(&attributes, CLOCK_MONOTONIC));
#endif
  sync_check(pthread_cond_init(&slot->ready_signal, &attributes));
  sync_check(pthread_condattr_destroy(&attributes));
#endif
}
static void slot_destroy(writer_slot_t *slot) {
#if defined(_WIN32)
  (void)slot;
#else
  sync_check(pthread_cond_destroy(&slot->ready_signal));
  sync_check(pthread_mutex_destroy(&slot->mutex));
#endif
}
static int checked(coakka_http_result_t value, const char *operation) {
  if (value.code == COAKKA_HTTP_RESULT_OK) return 1;
  fprintf(stderr, "%s: %s (%s)\n", operation, coakka_http_result_code_name(value.code), value.detail);
  atomic_store(&failed, 1); return 0;
}

static void select_writer(void *context, coakka_http_request_t *request) {
  writer_slot_t *slot = (writer_slot_t *)context;
  writer_job_t job = {NULL, coakka_http_request_route_id(request) == 2};
  coakka_http_response_t head;
  coakka_http_header_t header;
  coakka_http_result_t result;
  int admitted;
  lock(slot);
  admitted = !slot->busy && !slot->closing;
  if (admitted) slot->busy = 1; /* Reserve BEFORE selecting a response head. */
  unlock(slot);
  coakka_http_response_init(&head);
  if (!admitted) {
    head.status_code = 503; head.body = sample_bytes("producer busy");
    result = coakka_http_request_respond(request, &head);
    if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED)
      (void)checked(result, "producer refusal");
    return;
  }
  if (job.sse) {
    result = coakka_http_request_start_sse(request, NULL, 0, &job.stream);
  } else {
    header.name = sample_bytes("content-type"); header.value = sample_bytes("text/plain");
    /* Core owns transfer framing; final trailers are supplied to finish. */
    head.headers = &header; head.header_count = 1;
    result = coakka_http_request_start_response_stream(request, &head, &job.stream);
  }
  /* No native operation executes under the publication mutex. On shutdown,
   * the still-executing callback releases its own writer instead of publishing
   * work to a consumer that has stopped. Core stop joins these callbacks.
   */
  lock(slot);
  if (result.code == COAKKA_HTTP_RESULT_OK && !slot->closing) {
    slot->job = job; slot->ready = 1; signal_ready(slot); unlock(slot); return;
  }
  slot->busy = 0;
  unlock(slot);
  coakka_http_response_stream_release(&job.stream);
  if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED)
    (void)checked(result, "stream head");
}

/* Only main calls writer operations; it never retains the originating request.
 * The demo emits one bounded chunk/event. A refused write is not silently
 * retried or called successful: release it and let Core own terminal cleanup.
 */
static void produce(writer_job_t *job) {
  coakka_http_result_t result = coakka_http_response_stream_wait_writable(job->stream, 5000);
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    if (job->sse) {
      coakka_http_sse_event_t event;
      coakka_http_sse_event_init(&event);
      event.data = sample_bytes("ready\nsecond line");
      event.event_type = sample_bytes("state"); event.has_event_type = 1;
      event.id = sample_bytes("1"); event.has_id = 1;
      event.retry_ms = 1000; event.has_retry = 1;
      result = coakka_http_response_stream_write_sse(job->stream, &event);
    } else result = coakka_http_response_stream_write(job->stream, sample_bytes("stream-ready"));
  }
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    coakka_http_header_t trailer;
    trailer.name = sample_bytes("x-stream-end"); trailer.value = sample_bytes("done");
    result = coakka_http_response_stream_finish(&job->stream, job->sse ? NULL : &trailer, job->sse ? 0 : 1);
  }
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "stream stopped: %s\n", coakka_http_result_code_name(result.code));
    if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED &&
        result.code != COAKKA_HTTP_RESULT_TIMEOUT && result.code != COAKKA_HTTP_RESULT_QUEUE_FULL)
      atomic_store(&failed, 1);
  }
  coakka_http_response_stream_release(&job->stream); /* Finish success already nulls it. */
}

int main(void) {
  writer_slot_t slot;
  coakka_http_server_t *server = NULL;
  coakka_http_server_options_t options;
  coakka_http_route_t routes[2];
  uint16_t port = 0;
  unsigned index;
  slot_init(&slot);
  coakka_http_server_options_init(&options);
  options.bind_address = sample_bytes("127.0.0.1");
  for (index = 0; index < 2; ++index) {
    coakka_http_route_init(&routes[index]); routes[index].route_id = index + 1;
    routes[index].method = sample_bytes("GET");
    routes[index].path = sample_bytes(index == 0 ? "/stream" : "/events");
    routes[index].handler = select_writer; routes[index].context = &slot;
  }
  if (!checked(coakka_http_server_create(&options, routes, 2, &server), "create") ||
      !checked(coakka_http_server_start(server), "start") ||
      !checked(coakka_http_server_port(server, &port), "port")) goto cleanup;
  signal(SIGINT, stop_signal); signal(SIGTERM, stop_signal);
  printf("native-stream-port=%u\n", (unsigned)port); fflush(stdout);
  while (!stopping) {
    writer_job_t job = {NULL, 0};
    lock(&slot);
    if (!slot.ready && !stopping) wait_ready(&slot);
    if (slot.ready) { job = slot.job; slot.job.stream = NULL; slot.ready = 0; }
    unlock(&slot);
    if (job.stream != NULL) {
      produce(&job);
      lock(&slot); slot.busy = 0; unlock(&slot);
    }
  }
cleanup:
  /* Close application admission first. No writer is executing here. Dispose
   * the pending slot, then stop/join callbacks before destroying their context.
   */
  lock(&slot); slot.closing = 1; unlock(&slot);
  coakka_http_response_stream_release(&slot.job.stream);
  if (server != NULL) {
    if (!checked(coakka_http_server_stop(server), "stop") ||
        !checked(coakka_http_server_destroy(&server), "destroy"))
      abort(); /* A retained owner may still reference slot; never unwind it. */
  }
  slot_destroy(&slot);
  return atomic_load(&failed) ? EXIT_FAILURE : EXIT_SUCCESS;
}
