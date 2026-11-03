#ifndef COAKKA_HTTP_TEST_SUPPORT_H
#define COAKKA_HTTP_TEST_SUPPORT_H

#include <coakka/http/host.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#if defined(_WIN32)
#include <winsock2.h>
typedef SOCKET test_socket_t;
#define TEST_INVALID_SOCKET INVALID_SOCKET
#else
typedef int test_socket_t;
#define TEST_INVALID_SOCKET (-1)
#endif

/* Fixture-owned caller budget and output ceiling, not runtime defaults. Every
 * socket operation uses one monotonic deadline across partial I/O and EINTR. */
#define TEST_IO_TIMEOUT_MS UINT64_C(10000)
#define TEST_WIRE_CAPACITY 16384U

/* Single-reader test owner. lease_live records an outstanding release duty,
 * never an inferred exchange/runtime phase. Do not copy a live owner. Other
 * callers must join before close, as required by the public destruction law. */
typedef struct test_service {
  coakka_http_host_service_t *service;
  coakka_http_host_request_event_t event;
  int lease_live;
} test_service_t;

/* Assertions retain control flow through the test's cleanup label. A failed
 * observation must not accidentally report successful resource retirement. */
#define TEST_CHECK(expression)                                                 \
  do {                                                                         \
    if (!(expression)) {                                                       \
      (void)fprintf(stderr, "%s:%d: %s\n", __func__, __LINE__, #expression);    \
      ok = 0;                                                                  \
      goto cleanup;                                                            \
    }                                                                          \
  } while (0)

int test_platform_open(void);
int test_platform_close(void);
coakka_http_host_status_t test_cpu_info(coakka_http_host_cpu_info_t *info);
int test_now_ms(uint64_t *out_ms);
coakka_http_host_bytes_t test_text(const char *text);
int test_bytes_equal(coakka_http_host_bytes_t bytes, const char *text);
void test_service_init(test_service_t *owner);
int test_service_open(test_service_t *owner,
                      const coakka_http_host_configuration_t *configuration,
                      const coakka_http_host_route_t *routes, uint32_t count);
int test_service_close(test_service_t *owner);
coakka_http_host_status_t test_take(test_service_t *owner, uint64_t timeout_ms);
int test_release(test_service_t *owner);
/* Buffered tests may discard only their known completion notifications. Stream
 * and cancellation tests use test_take and inspect every event explicitly. */
int test_next_request(test_service_t *owner);
int test_respond(test_service_t *owner, uint32_t status, const char *body);
test_socket_t test_connect(uint16_t port);
int test_socket_close(test_socket_t *socket);
int test_send(test_socket_t socket, const void *data, size_t size);
int test_receive(test_socket_t socket, char *wire, size_t capacity,
                  size_t *out_size);
int test_wire_response(const char *wire, size_t size, unsigned status,
                        const char *body);
int test_wire_chunked(const char *wire, size_t size, const char *body,
                       const char *trailer_name, const char *trailer_value);
int test_finish(const char *mode, int ok, int cleanup_ok);

#endif
