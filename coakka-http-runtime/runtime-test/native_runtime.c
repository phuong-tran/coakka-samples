#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif
#include <coakka/http/http.h>
#if defined(__linux__)
#include <sched.h>
#endif

#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
typedef SOCKET test_socket_t;
#define TEST_INVALID_SOCKET INVALID_SOCKET
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
typedef int test_socket_t;
#define TEST_INVALID_SOCKET (-1)
#endif

typedef struct test_state {
  /* Callback writers and the client driver have independent threads. Receiving
   * HTTP bytes is not C-language synchronization for these test observations.
   * Atomic counters also keep failure diagnostics safe while the server lives. */
  _Atomic uint32_t calls;
  _Atomic uint32_t rebound_calls;
  _Atomic uint32_t failures;
  _Atomic uint32_t limit_rejections;
  _Atomic uint32_t stream_calls;
} test_state_t;

/* Client-driver-only byte count, including binary response payloads. */
static size_t last_response_bytes;

#define CHECK(expression)                                                      \
  do {                                                                         \
    if (!(expression)) {                                                       \
      fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #expression); \
      return EXIT_FAILURE;                                                     \
    }                                                                          \
  } while (0)

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : (uint64_t)strlen(value);
  return result;
}

static int bytes_equal(coakka_http_bytes_t value, const char *expected) {
  const size_t expected_size = strlen(expected);
  return value.size == (uint64_t)expected_size &&
         (expected_size == 0U ||
          memcmp(value.data, expected, expected_size) == 0);
}

static void handle_request(void *opaque, coakka_http_request_t *request) {
  static const uint8_t oversized_body[1025] = {0U};
  test_state_t *state = (test_state_t *)opaque;
  coakka_http_response_t response;
  coakka_http_header_t header;
  coakka_http_header_t trailer;
  coakka_http_result_t submitted;
  const uint64_t route_id = coakka_http_request_route_id(request);

  state->calls += 1U;
#if defined(__linux__)
  {
    /* The handler must inherit native startup placement, not the restored
     * caller mask. This is a conformance check, not application policy. */
    cpu_set_t actual;
    CPU_ZERO(&actual);
    if (sched_getaffinity(0, sizeof(actual), &actual) != 0 ||
        CPU_COUNT(&actual) < 1 || CPU_COUNT(&actual) > 2) ++state->failures;
  }
#endif
  if (!bytes_equal(coakka_http_request_method(request), "POST") ||
      coakka_http_request_cancelled(request) != 0U) {
    state->failures += 1U;
  }

  if (route_id == UINT64_C(42)) {
    if (!bytes_equal(coakka_http_request_target(request), "/limit")) {
      state->failures += 1U;
    }
    coakka_http_response_init(&response);
    response.body.data = oversized_body;
    response.body.size = sizeof(oversized_body);
    submitted = coakka_http_request_respond(request, &response);
    if (submitted.code != COAKKA_HTTP_RESULT_LIMIT_EXCEEDED) {
      state->failures += 1U;
    } else {
      state->limit_rejections += 1U;
    }
    response.body = bytes("limit-recovered");
    submitted = coakka_http_request_respond(request, &response);
    if (submitted.code != COAKKA_HTTP_RESULT_OK) {
      state->failures += 1U;
    }
    return;
  }

  if (route_id == UINT64_C(44)) {
    static const char compressible[] =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    header.name = bytes("content-type");
    header.value = bytes("text/plain");
    coakka_http_response_init(&response);
    response.headers = &header;
    response.header_count = 1U;
    response.body = bytes(compressible);
    if (coakka_http_request_respond(request, &response).code !=
        COAKKA_HTTP_RESULT_OK) ++state->failures;
    return;
  }

  if (route_id == UINT64_C(43)) {
    char streamed[64];
    size_t streamed_size = 0U;
    uint32_t trailer_seen = 0U;
    uint32_t ended = 0U;
    uint32_t turn;
    state->stream_calls += 1U;
    if (coakka_http_request_body_delivery(request) !=
            COAKKA_HTTP_BODY_STREAM ||
        coakka_http_request_body(request).size != 0U) {
      state->failures += 1U;
    }
    for (turn = 0U; turn < 16U && ended == 0U; ++turn) {
      coakka_http_request_body_event_t event;
      coakka_http_request_body_event_init(&event);
      submitted = coakka_http_request_body_read(request, 2000U, &event);
      if (submitted.code != COAKKA_HTTP_RESULT_OK) {
        state->failures += 1U;
        break;
      }
      if (event.kind == COAKKA_HTTP_REQUEST_BODY_DATA) {
        if (event.data.size > sizeof(streamed) - streamed_size) {
          state->failures += 1U;
          break;
        }
        memcpy(streamed + streamed_size, event.data.data,
               (size_t)event.data.size);
        streamed_size += (size_t)event.data.size;
      } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_TRAILERS) {
        coakka_http_header_t stream_trailer;
        if (coakka_http_request_body_event_trailer_count(&event) != 1U ||
            coakka_http_request_body_event_trailer(&event, 0U,
                                                   &stream_trailer) == 0U ||
            !bytes_equal(stream_trailer.name, "x-checksum") ||
            !bytes_equal(stream_trailer.value, "stream-ok")) {
          state->failures += 1U;
        }
        trailer_seen = 1U;
      } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_END) {
        ended = 1U;
      } else {
        state->failures += 1U;
        break;
      }
    }
    if (streamed_size != strlen("stream-request-body") ||
        memcmp(streamed, "stream-request-body", streamed_size) != 0 ||
        trailer_seen == 0U || ended == 0U) {
      state->failures += 1U;
    }
    coakka_http_response_init(&response);
    response.body = bytes("coakka-native-stream-ready");
    if (coakka_http_request_respond(request, &response).code !=
        COAKKA_HTTP_RESULT_OK) {
      state->failures += 1U;
    }
    return;
  }

  if (route_id != UINT64_C(41) ||
      !bytes_equal(coakka_http_request_target(request),
                   "/echo/a%2Fb?source=abi&flag&flag=&source=x%20y") ||
      !bytes_equal(coakka_http_request_body(request), "request-body") ||
      coakka_http_request_trailer_count(request) != 1U ||
      coakka_http_request_trailer(request, 0U, &trailer) == 0U ||
      !bytes_equal(trailer.name, "x-checksum") ||
      !bytes_equal(trailer.value, "ok")) {
    state->failures += 1U;
  }

  {
    static const char *keys[] = {"source", "flag", "flag", "source"};
    static const char *values[] = {"abi", "", "", "x%20y"};
    coakka_http_path_parameter_t path, path_before;
    coakka_http_query_parameter_t query, query_before;
    uint32_t index;
    if (coakka_http_request_path_parameter_count(request) != 1U ||
        coakka_http_request_query_parameter_count(request) != 4U ||
        !coakka_http_request_path_parameter(request, 0U, &path) ||
        !bytes_equal(path.name, "id") ||
        !bytes_equal(path.encoded_value, "a%2Fb")) ++state->failures;
    for (index = 0U; index < 4U; ++index) {
      if (!coakka_http_request_query_parameter(request, index, &query) ||
          !bytes_equal(query.encoded_key, keys[index]) ||
          !bytes_equal(query.encoded_value, values[index]) ||
          query.has_value != (index == 1U ? 0U : 1U)) ++state->failures;
    }
    memset(&path, 0xa5, sizeof(path));
    memset(&query, 0xa5, sizeof(query));
    path_before = path;
    query_before = query;
    if (coakka_http_request_path_parameter(request, 1U, &path) ||
        coakka_http_request_query_parameter(request, UINT32_MAX, &query) ||
        coakka_http_request_path_parameter(request, 0U, NULL) ||
        coakka_http_request_query_parameter(request, 0U, NULL) ||
        memcmp(&path, &path_before, sizeof(path)) != 0 ||
        memcmp(&query, &query_before, sizeof(query)) != 0) ++state->failures;
  }

  header.name = bytes("content-type");
  header.value = bytes("text/plain");
  coakka_http_response_init(&response);
  response.status_code = 201U;
  response.headers = &header;
  response.header_count = 1U;
  response.body = bytes("coakka-native-ready");
  submitted = coakka_http_request_respond(request, &response);
  if (submitted.code != COAKKA_HTTP_RESULT_OK) {
    state->failures += 1U;
  }
  submitted = coakka_http_request_respond(request, &response);
  if (submitted.code != COAKKA_HTTP_RESULT_INVALID_STATE) {
    state->failures += 1U;
  }
}

static void handle_rebound(void *opaque, coakka_http_request_t *request) {
  test_state_t *state = (test_state_t *)opaque;
  coakka_http_response_t response;
  state->calls += 1U;
  state->rebound_calls += 1U;
  coakka_http_response_init(&response);
  response.status_code = 202U;
  response.body = bytes("coakka-native-rebound");
  if (coakka_http_request_respond(request, &response).code !=
      COAKKA_HTTP_RESULT_OK) {
    state->failures += 1U;
  }
}

static void close_socket(test_socket_t socket_value) {
#if defined(_WIN32)
  (void)closesocket(socket_value);
#else
  (void)close(socket_value);
#endif
}

static int socket_runtime_start(void) {
#if defined(_WIN32)
  WSADATA data;
  return WSAStartup(MAKEWORD(2, 2), &data) == 0;
#else
  return 1;
#endif
}

static void socket_runtime_stop(void) {
#if defined(_WIN32)
  (void)WSACleanup();
#endif
}

static test_socket_t connect_loopback(uint16_t port) {
  test_socket_t socket_value;
  struct sockaddr_in address;
#if defined(_WIN32)
  DWORD timeout_ms = 5000U;
#else
  struct timeval timeout;
  timeout.tv_sec = 5;
  timeout.tv_usec = 0;
#endif

  socket_value = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (socket_value == TEST_INVALID_SOCKET) {
    return TEST_INVALID_SOCKET;
  }
#if defined(_WIN32)
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO,
                   (const char *)&timeout_ms, (int)sizeof(timeout_ms));
#else
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   (socklen_t)sizeof(timeout));
#endif
  memset(&address, 0, sizeof(address));
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (connect(socket_value, (const struct sockaddr *)&address,
              (socklen_t)sizeof(address)) != 0) {
    close_socket(socket_value);
    return TEST_INVALID_SOCKET;
  }
  return socket_value;
}

static int send_all(test_socket_t socket_value, const char *data, size_t size) {
  size_t offset = 0U;
  while (offset < size) {
#if defined(_WIN32)
    const int sent = send(socket_value, data + offset, (int)(size - offset), 0);
#else
    const ssize_t sent = send(socket_value, data + offset, size - offset, 0);
#endif
    if (sent <= 0) {
      return 0;
    }
    offset += (size_t)sent;
  }
  return 1;
}

static int read_response(test_socket_t socket_value, char *output,
                         size_t capacity, const char *expected_body) {
  size_t used = 0U;
  last_response_bytes = 0U;
  while (used + 1U < capacity) {
#if defined(_WIN32)
    const int received =
        recv(socket_value, output + used, (int)(capacity - used - 1U), 0);
#else
    const ssize_t received =
        recv(socket_value, output + used, capacity - used - 1U, 0);
#endif
    if (received < 0) return 0;
    if (received == 0) {
      break;
    }
    used += (size_t)received;
    last_response_bytes = used;
    output[used] = '\0';
    if (expected_body != NULL && strstr(output, expected_body) != NULL) {
      return 1;
    }
  }
  output[used] = '\0';
  return expected_body == NULL && used != 0U;
}

int main(void) {
  static const char request_data[] =
      "POST /echo/a%2Fb?source=abi&flag&flag=&source=x%20y HTTP/1.1\r\n"
                                     "Host: 127.0.0.1\r\n"
                                     "Content-Type: text/plain\r\n"
                                     "Transfer-Encoding: chunked\r\n"
                                     "Trailer: X-Checksum\r\n"
                                     "Connection: close\r\n\r\n"
                                     "c\r\nrequest-body\r\n"
                                     "0\r\nX-Checksum: ok\r\n\r\n";
  static const char limit_request[] = "POST /limit HTTP/1.1\r\n"
                                      "Host: 127.0.0.1\r\n"
                                      "Content-Length: 0\r\n"
                                      "Connection: close\r\n\r\n";
  static const char stream_request[] =
      "POST /stream HTTP/1.1\r\n"
      "Host: 127.0.0.1\r\n"
      "Content-Type: text/plain\r\n"
      "Transfer-Encoding: chunked\r\n"
      "Trailer: X-Checksum\r\n"
      "Connection: close\r\n\r\n"
      "7\r\nstream-\r\n"
      "c\r\nrequest-body\r\n"
      "0\r\nX-Checksum: stream-ok\r\n\r\n";
  coakka_http_server_options_t options;
  coakka_http_server_tuning_t tuning;
  coakka_http_compression_t compression;
  coakka_http_route_t routes[4];
  coakka_http_server_t *server = NULL;
  coakka_http_result_t operation;
  coakka_http_route_rebind_t rebind;
  coakka_http_route_rebind_outcome_t rebind_outcome;
  test_state_t state = {0U, 0U, 0U, 0U, 0U};
  uint16_t port = 0U;
  test_socket_t client;
  char response_data[4096];
#if defined(__linux__)
  cpu_set_t caller_before, caller_after;
  CHECK(sched_getaffinity(0, sizeof(caller_before), &caller_before) == 0);
#endif

  CHECK(coakka_http_abi_version() == COAKKA_HTTP_ABI_VERSION);
  CHECK(strcmp(coakka_http_result_code_name(COAKKA_HTTP_RESULT_OK), "ok") == 0);
  CHECK(socket_runtime_start());

  coakka_http_server_options_init(&options);
  options.port = 0U;
  options.max_connections = 8U;
  options.max_active_requests = 4U;
  options.request_queue_capacity = 4U;
  options.response_queue_capacity = 4U;
  options.max_request_body_bytes = 1024U;
  options.max_response_body_bytes = 1024U;
  options.request_timeout_ms = 2000U;
  options.shutdown_timeout_ms = 2000U;
  coakka_http_server_tuning_init(&tuning);
  coakka_http_compression_init(&compression);
  if ((coakka_http_features() & COAKKA_HTTP_CAPABILITY_GZIP) != 0U) {
    compression.mode = COAKKA_HTTP_COMPRESSION_GZIP;
    compression.minimum_body_bytes = 1U;
    compression.gzip_level = 6;
    tuning.compression = &compression;
    options.tuning = &tuning;
  }

  coakka_http_route_init(&routes[0]);
  routes[0].route_id = UINT64_C(41);
  routes[0].method = bytes("POST");
  routes[0].path = bytes("/echo/{id}");
  routes[0].context = &state;
  routes[0].handler = handle_request;
  coakka_http_route_init(&routes[1]);
  routes[1].route_id = UINT64_C(42);
  routes[1].method = bytes("POST");
  routes[1].path = bytes("/limit");
  routes[1].context = &state;
  routes[1].handler = handle_request;
  coakka_http_route_init(&routes[2]);
  routes[2].body_delivery = COAKKA_HTTP_BODY_STREAM;
  routes[2].route_id = UINT64_C(43);
  routes[2].method = bytes("POST");
  routes[2].path = bytes("/stream");
  routes[2].context = &state;
  routes[2].handler = handle_request;
  coakka_http_route_init(&routes[3]);
  routes[3].route_id = UINT64_C(44);
  routes[3].method = bytes("POST");
  routes[3].path = bytes("/compressed");
  routes[3].context = &state;
  routes[3].handler = handle_request;

  operation = coakka_http_server_create(&options, routes, 4U, &server);
  if (operation.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "create failed: code=%s actual=%llu limit=%llu detail=%s\n",
            coakka_http_result_code_name(operation.code),
            (unsigned long long)operation.actual,
            (unsigned long long)operation.limit, operation.detail);
  }
  CHECK(operation.code == COAKKA_HTTP_RESULT_OK);
  CHECK(server != NULL);
#if defined(__linux__)
  CHECK(sched_getaffinity(0, sizeof(caller_after), &caller_after) == 0);
  CHECK(CPU_EQUAL(&caller_before, &caller_after));
#endif
  CHECK(coakka_http_server_start(server).code == COAKKA_HTTP_RESULT_OK);
#if defined(__linux__)
  CHECK(sched_getaffinity(0, sizeof(caller_after), &caller_after) == 0);
  CHECK(CPU_EQUAL(&caller_before, &caller_after));
#endif
  CHECK(coakka_http_server_port(server, &port).code == COAKKA_HTTP_RESULT_OK);
  CHECK(port != 0U);

  client = connect_loopback(port);
  CHECK(client != TEST_INVALID_SOCKET);
  CHECK(send_all(client, request_data, sizeof(request_data) - 1U));
  CHECK(read_response(client, response_data, sizeof(response_data),
                      "coakka-native-ready"));
  close_socket(client);
  CHECK(strstr(response_data, "HTTP/1.1 201") != NULL);
  CHECK(strstr(response_data, "content-type: text/plain") != NULL);
  client = connect_loopback(port);
  CHECK(client != TEST_INVALID_SOCKET);
  CHECK(send_all(client, limit_request, sizeof(limit_request) - 1U));
  CHECK(read_response(client, response_data, sizeof(response_data),
                      "limit-recovered"));
  close_socket(client);
  CHECK(strstr(response_data, "HTTP/1.1 200") != NULL);
  client = connect_loopback(port);
  CHECK(client != TEST_INVALID_SOCKET);
  CHECK(send_all(client, stream_request, sizeof(stream_request) - 1U));
  if (!read_response(client, response_data, sizeof(response_data),
                     "coakka-native-stream-ready")) {
    fprintf(stderr,
            "stream response missing: response=%s calls=%u stream_calls=%u "
            "failures=%u\n",
            response_data, state.calls, state.stream_calls, state.failures);
    return EXIT_FAILURE;
  }
  close_socket(client);
  CHECK(strstr(response_data, "HTTP/1.1 200") != NULL);
  CHECK(state.calls == 3U);
  CHECK(state.limit_rejections == 1U);
  CHECK(state.stream_calls == 1U);
  CHECK(state.failures == 0U);

  if (options.tuning != NULL) {
    static const char gzip_request[] =
        "POST /compressed HTTP/1.1\r\nHost: 127.0.0.1\r\n"
        "Accept-Encoding: gzip\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    const unsigned char *encoded;
    const char *length_header;
    unsigned long encoded_size;
    client = connect_loopback(port);
    CHECK(client != TEST_INVALID_SOCKET);
    CHECK(send_all(client, gzip_request, sizeof(gzip_request) - 1U));
    CHECK(read_response(client, response_data, sizeof(response_data), NULL));
    close_socket(client);
    CHECK(strstr(response_data, "content-encoding: gzip\r\n") != NULL);
    length_header = strstr(response_data, "content-length: ");
    if (length_header == NULL) length_header = strstr(response_data, "Content-Length: ");
    CHECK(length_header != NULL);
    encoded_size = strtoul(length_header + strlen("content-length: "), NULL, 10);
    CHECK(encoded_size >= 18U && encoded_size < 128U);
    encoded = (const unsigned char *)strstr(response_data, "\r\n\r\n");
    CHECK(encoded != NULL);
    encoded += 4;
    CHECK((size_t)(encoded - (const unsigned char *)response_data) + encoded_size == last_response_bytes);
    CHECK((size_t)(encoded - (const unsigned char *)response_data) + encoded_size < sizeof(response_data));
    CHECK(encoded[0] == 0x1fU && encoded[1] == 0x8bU && encoded[2] == 8U);
    CHECK(encoded[encoded_size - 4U] == 128U && encoded[encoded_size - 3U] == 0U &&
          encoded[encoded_size - 2U] == 0U && encoded[encoded_size - 1U] == 0U);
    CHECK(state.calls == 4U && state.failures == 0U);
  }

  CHECK(coakka_http_server_prepare_handler(server, UINT64_C(9002), &state,
                                           handle_rebound)
            .code == COAKKA_HTTP_RESULT_OK);
  coakka_http_route_rebind_init(&rebind);
  rebind.activation_id = 1U;
  rebind.expected_route_generation = 1U;
  rebind.route_id = UINT64_C(41);
  rebind.expected_binding_revision = 1U;
  rebind.new_handler_binding_id = UINT64_C(9002);
  coakka_http_route_rebind_outcome_init(&rebind_outcome);
  CHECK(
      coakka_http_server_rebind_handler(server, &rebind, 2000U, &rebind_outcome)
          .code == COAKKA_HTTP_RESULT_OK);
  CHECK(rebind_outcome.code == COAKKA_HTTP_ROUTE_REBIND_APPLIED &&
        rebind_outcome.changed != 0U);
  client = connect_loopback(port);
  CHECK(client != TEST_INVALID_SOCKET);
  CHECK(send_all(client, request_data, sizeof(request_data) - 1U));
  CHECK(read_response(client, response_data, sizeof(response_data),
                      "coakka-native-rebound"));
  close_socket(client);
  CHECK(strstr(response_data, "HTTP/1.1 202") != NULL);
  CHECK(state.rebound_calls == 1U && state.failures == 0U);

  operation = coakka_http_server_stop(server);
  if (operation.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "stop failed: code=%s actual=%llu limit=%llu detail=%s\n",
            coakka_http_result_code_name(operation.code),
            (unsigned long long)operation.actual,
            (unsigned long long)operation.limit, operation.detail);
  }
  CHECK(operation.code == COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_stop(server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_destroy(&server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(server == NULL);
  socket_runtime_stop();

  printf("coakka_http_runtime_c_test=pass requests=%u limit_rejections=%u "
         "stream_calls=%u abi=%u\n",
         state.calls, state.limit_rejections, state.stream_calls,
         coakka_http_abi_version());
  return EXIT_SUCCESS;
}
