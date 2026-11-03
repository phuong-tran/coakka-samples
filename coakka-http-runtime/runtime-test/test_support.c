#include "test_support.h"

#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#endif

/* One same-thread fixture controller owns this placement lease. Core chooses
 * AUTO; the harness never probes CPUs or duplicates its default table. */
static coakka_http_host_cpu_scope_t *dispatcher_scope;

int test_platform_open(void) {
  coakka_http_host_issue_t issue;
#if defined(_WIN32)
  WSADATA data;
  if (WSAStartup(MAKEWORD(2, 2), &data) != 0) return 0;
#endif
  coakka_http_host_issue_init(&issue);
  if (coakka_http_host_cpu_scope_enter(COAKKA_HTTP_HOST_CPU_AUTO,
                                        &dispatcher_scope, &issue) != COAKKA_HTTP_HOST_OK) {
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return 0;
  }
  return 1;
}

int test_platform_close(void) {
  coakka_http_host_issue_t issue;
  int ok;
  coakka_http_host_issue_init(&issue);
  /* Every Core owner and fixture child is retired before restoring the
   * controller's inherited placement. The Core result is the restore proof. */
  ok = coakka_http_host_cpu_scope_leave(&dispatcher_scope, &issue) == COAKKA_HTTP_HOST_OK;
  if (dispatcher_scope != NULL) ok = 0;
#if defined(_WIN32)
  if (WSACleanup() != 0) ok = 0;
#endif
  return ok;
}

coakka_http_host_status_t test_cpu_info(coakka_http_host_cpu_info_t *info) {
  return coakka_http_host_cpu_scope_info(dispatcher_scope, info);
}

coakka_http_host_bytes_t test_text(const char *text) {
  coakka_http_host_bytes_t result;
  result.data = (const uint8_t *)text;
  result.size = text == NULL ? 0U : strlen(text);
  return result;
}

int test_bytes_equal(coakka_http_host_bytes_t bytes, const char *text) {
  const size_t size = strlen(text);
  return bytes.size == size &&
         (size == 0U || (bytes.data != NULL && memcmp(bytes.data, text, size) == 0));
}

void test_service_init(test_service_t *owner) {
  memset(owner, 0, sizeof(*owner));
  coakka_http_host_request_event_init(&owner->event);
}

int test_service_open(test_service_t *owner,
                      const coakka_http_host_configuration_t *configuration,
                      const coakka_http_host_route_t *routes, uint32_t count) {
  coakka_http_host_issue_t issue;
  coakka_http_host_status_t status;
  uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  if (owner->service != NULL || owner->lease_live) return 0;
  status = coakka_http_host_service_create(configuration, routes, count,
                                           &owner->service, &failed);
  if (status == COAKKA_HTTP_HOST_OK) {
    coakka_http_host_issue_init(&issue);
    status = coakka_http_host_service_start(owner->service, &issue);
  }
  if (status != COAKKA_HTTP_HOST_OK) {
    (void)fprintf(stderr, "service open: %s\n", coakka_http_host_status_name(status));
    /* A failed start can still leave a live owner. Caller cleanup retains it. */
    return 0;
  }
  return 1;
}

int test_release(test_service_t *owner) {
  if (!owner->lease_live) return 1;
  if (coakka_http_host_release_request(owner->service, &owner->event) !=
      COAKKA_HTTP_HOST_OK) return 0;
  owner->lease_live = 0;
  return 1;
}

int test_service_close(test_service_t *owner) {
  if (!test_release(owner)) return 0;
  /* Destroy performs Core-owned stop/join. Failure preserves the pointer;
   * never discard a retained owner and call that successful cleanup. */
  return coakka_http_host_service_destroy(&owner->service) == COAKKA_HTTP_HOST_OK &&
         owner->service == NULL;
}

coakka_http_host_status_t test_take(test_service_t *owner, uint64_t timeout_ms) {
  coakka_http_host_status_t status;
  if (owner->lease_live) return COAKKA_HTTP_HOST_INVALID_ARGUMENT;
  coakka_http_host_request_event_init(&owner->event);
  status = coakka_http_host_take_request(owner->service, timeout_ms, &owner->event);
  if (status == COAKKA_HTTP_HOST_OK) owner->lease_live = 1;
  return status;
}

int test_next_request(test_service_t *owner) {
  uint64_t until, now;
  if (!test_now_ms(&until) || until > UINT64_MAX - TEST_IO_TIMEOUT_MS) return 0;
  until += TEST_IO_TIMEOUT_MS;
  for (;;) {
    if (!test_now_ms(&now) || now >= until ||
        test_take(owner, until - now) != COAKKA_HTTP_HOST_OK) return 0;
    if (owner->event.kind == COAKKA_HTTP_HOST_REQUEST) return 1;
    if (owner->event.kind != COAKKA_HTTP_HOST_REQUEST_TERMINAL ||
        !test_release(owner)) return 0;
  }
}

int test_respond(test_service_t *owner, uint32_t status, const char *body) {
  coakka_http_host_response_t response;
  if (!owner->lease_live || owner->event.kind != COAKKA_HTTP_HOST_REQUEST) return 0;
  coakka_http_host_response_init(&response);
  response.status_code = status;
  response.body = test_text(body);
  /* Core copies the response bytes during this synchronous call. The fixture
   * retains the event lease separately until its explicit release. */
  return coakka_http_host_respond(owner->service, owner->event.exchange,
                                   &response) == COAKKA_HTTP_HOST_OK;
}

int test_finish(const char *mode, int ok, int cleanup_ok) {
  /* Do not retire process networking while an earlier cleanup is unresolved. */
  const int platform_ok = cleanup_ok ? test_platform_close() : 0;
  cleanup_ok = cleanup_ok && platform_ok;
  (void)printf("{\"case\":\"%s\",\"ok\":%s,\"cleanup\":%s,\"abi\":%u}\n",
               mode, ok && cleanup_ok ? "true" : "false",
               cleanup_ok ? "true" : "false", coakka_http_host_abi_version());
  return ok && cleanup_ok ? 0 : 1;
}
