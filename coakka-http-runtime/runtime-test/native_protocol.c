#if !defined(_WIN32)
#define _POSIX_C_SOURCE 200809L
#endif

#include <coakka/http/http.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(_WIN32)
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#else
#include <errno.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#define CHECK(condition)                                                       \
  do {                                                                         \
    if (!(condition)) {                                                        \
      fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #condition);  \
      exit(EXIT_FAILURE);                                                      \
    }                                                                          \
  } while (0)

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = strlen(value);
  return result;
}

static void handle_protocol(void *context, coakka_http_request_t *request) {
  coakka_http_response_t response;
  (void)context;
  coakka_http_response_init(&response);
  response.status_code = 200U;
  response.body = bytes("protocol-ready");
  CHECK(coakka_http_request_respond(request, &response).code ==
        COAKKA_HTTP_RESULT_OK);
}

static void handle_request_stream(void *context,
                                  coakka_http_request_t *request) {
  static const char expected[] = "request-stream-over-h2-h3";
  char received[sizeof(expected)];
  size_t received_size = 0U;
  uint64_t previous_sequence = 0U;
  int ended = 0;
  coakka_http_response_t response;
  (void)context;

  CHECK(coakka_http_request_body_delivery(request) == COAKKA_HTTP_BODY_STREAM);
  while (!ended) {
    coakka_http_request_body_event_t event;
    coakka_http_result_t read;
    coakka_http_request_body_event_init(&event);
    read = coakka_http_request_body_read(request, COAKKA_HTTP_TIMEOUT_FOREVER,
                                         &event);
    CHECK(read.code == COAKKA_HTTP_RESULT_OK);
    CHECK(event.sequence > previous_sequence);
    previous_sequence = event.sequence;
    if (event.kind == COAKKA_HTTP_REQUEST_BODY_DATA) {
      CHECK(event.data.size <=
            (uint64_t)(sizeof(received) - 1U - received_size));
      if (event.data.size != 0U) {
        memcpy(received + received_size, event.data.data,
               (size_t)event.data.size);
        received_size += (size_t)event.data.size;
      }
    } else {
      CHECK(event.kind == COAKKA_HTTP_REQUEST_BODY_END);
      ended = 1;
    }
  }
  received[received_size] = '\0';
  CHECK(strcmp(received, expected) == 0);
  coakka_http_response_init(&response);
  response.body = bytes("request-stream-ready");
  CHECK(coakka_http_request_respond(request, &response).code ==
        COAKKA_HTTP_RESULT_OK);
}

static int run_process(const char *program, char *const arguments[]) {
#if defined(_WIN32)
  STARTUPINFOA startup;
  PROCESS_INFORMATION process;
  char command_line[4096];
  size_t used = 0U;
  size_t index;
  DWORD wait_status;
  DWORD exit_code = 1U;
  memset(&startup, 0, sizeof(startup));
  memset(&process, 0, sizeof(process));
  startup.cb = sizeof(startup);
  command_line[0] = '\0';
  for (index = 0U; arguments[index] != NULL; ++index) {
    const char *argument = arguments[index];
    const size_t argument_size = strlen(argument);
    const size_t required = argument_size + (index == 0U ? 2U : 3U);
    if (required > sizeof(command_line) - used - 1U ||
        strchr(argument, '"') != NULL) {
      return 0;
    }
    if (index != 0U) {
      command_line[used++] = ' ';
    }
    command_line[used++] = '"';
    memcpy(command_line + used, argument, argument_size);
    used += argument_size;
    command_line[used++] = '"';
    command_line[used] = '\0';
  }
  if (!CreateProcessA(program, command_line, NULL, NULL, FALSE,
                      CREATE_NO_WINDOW, NULL, NULL, &startup, &process)) {
    return 0;
  }
  wait_status = WaitForSingleObject(process.hProcess, 10000U);
  if (wait_status == WAIT_TIMEOUT) {
    (void)TerminateProcess(process.hProcess, 1U);
    (void)WaitForSingleObject(process.hProcess, 5000U);
  }
  if (wait_status == WAIT_OBJECT_0) {
    (void)GetExitCodeProcess(process.hProcess, &exit_code);
  }
  (void)CloseHandle(process.hThread);
  (void)CloseHandle(process.hProcess);
  return wait_status == WAIT_OBJECT_0 && exit_code == 0U;
#else
  const pid_t child = fork();
  struct timespec started;
  const struct timespec pause = {0, 10000000L};
  int status = 0;
  if (child < 0) {
    return 0;
  }
  if (child == 0) {
    execv(program, arguments);
    _exit(127);
  }
  if (clock_gettime(CLOCK_MONOTONIC, &started) != 0) {
    (void)kill(child, SIGKILL);
    (void)waitpid(child, &status, 0);
    return 0;
  }
  for (;;) {
    struct timespec now;
    const pid_t waited = waitpid(child, &status, WNOHANG);
    if (waited == child) {
      return WIFEXITED(status) && WEXITSTATUS(status) == 0;
    }
    if (waited < 0 && errno != EINTR) {
      (void)kill(child, SIGKILL);
      (void)waitpid(child, &status, 0);
      return 0;
    }
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0 ||
        now.tv_sec - started.tv_sec >= 10) {
      (void)kill(child, SIGKILL);
      while (waitpid(child, &status, 0) < 0 && errno == EINTR) {
      }
      return 0;
    }
    (void)nanosleep(&pause, NULL);
  }
#endif
}

static void run_protocol(uint32_t protocol, const char *fixtures,
                         const char *http2_client, const char *http3_client) {
  coakka_http_server_options_t options;
  coakka_http_route_t routes[2];
  coakka_http_server_t *server = NULL;
  char certificate[1024];
  char private_key[1024];
  char port_text[16];
  uint16_t port = 0U;

  const int certificate_size =
      snprintf(certificate, sizeof(certificate), "%s/server.pem", fixtures);
  const int private_key_size =
      snprintf(private_key, sizeof(private_key), "%s/server.key", fixtures);
  CHECK(certificate_size > 0 && (size_t)certificate_size < sizeof(certificate));
  CHECK(private_key_size > 0 && (size_t)private_key_size < sizeof(private_key));
  coakka_http_server_options_init(&options);
  options.protocol = protocol;
  options.security = COAKKA_HTTP_SECURITY_TLS;
  options.credential_generation = UINT64_C(1);
  options.credential_id = bytes("native-protocol-server");
  options.certificate_chain_file = bytes(certificate);
  options.private_key_file = bytes(private_key);
  coakka_http_route_init(&routes[0]);
  routes[0].route_id = UINT64_C(1);
  routes[0].method = bytes("GET");
  routes[0].path = bytes("/protocol");
  routes[0].handler = handle_protocol;
  coakka_http_route_init(&routes[1]);
  routes[1].route_id = UINT64_C(2);
  routes[1].method = bytes("POST");
  routes[1].path = bytes("/request-stream");
  routes[1].body_delivery = COAKKA_HTTP_BODY_STREAM;
  routes[1].handler = handle_request_stream;

  CHECK(coakka_http_server_create(&options, routes, 2U, &server).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_start(server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_port(server, &port).code == COAKKA_HTTP_RESULT_OK);
  CHECK(port != 0U);
  CHECK(snprintf(port_text, sizeof(port_text), "%u", (unsigned)port) > 0);

  if (protocol == COAKKA_HTTP_PROTOCOL_HTTP_2) {
    char *const arguments[] = {
        (char *)http2_client,     port_text, (char *)"/protocol",
        (char *)"protocol-ready", NULL,
    };
    CHECK(run_process(http2_client, arguments));
    {
      char *const stream_arguments[] = {
          (char *)http2_client,
          port_text,
          (char *)"/request-stream",
          (char *)"request-stream-ready",
          (char *)"request-stream-over-h2-h3",
          NULL,
      };
      CHECK(run_process(http2_client, stream_arguments));
    }
  } else {
    char *const arguments[] = {
        (char *)http3_client,     port_text, (char *)"/protocol",
        (char *)"protocol-ready", NULL,
    };
    CHECK(run_process(http3_client, arguments));
    {
      char *const stream_arguments[] = {
          (char *)http3_client,
          port_text,
          (char *)"/request-stream",
          (char *)"request-stream-ready",
          (char *)"request-stream-over-h2-h3",
          NULL,
      };
      CHECK(run_process(http3_client, stream_arguments));
    }
  }

  {
    const coakka_http_result_t stopped = coakka_http_server_stop(server);
    if (stopped.code != COAKKA_HTTP_RESULT_OK) {
      fprintf(stderr,
              "protocol stop failed: protocol=%u code=%s detail=%s "
              "actual=%llu limit=%llu\n",
              (unsigned int)protocol,
              coakka_http_result_code_name(stopped.code), stopped.detail,
              (unsigned long long)stopped.actual,
              (unsigned long long)stopped.limit);
      exit(EXIT_FAILURE);
    }
  }
  CHECK(coakka_http_server_destroy(&server).code == COAKKA_HTTP_RESULT_OK);
}

int main(void) {
  const char *fixtures = getenv("COAKKA_HTTP_TEST_TLS_FIXTURES");
  const char *http2_client = getenv("COAKKA_HTTP_TEST_HTTP2_CLIENT");
  const char *http3_client = getenv("COAKKA_HTTP_TEST_HTTP3_CLIENT");
  const uint64_t features = coakka_http_features();
  CHECK(fixtures != NULL && http2_client != NULL && http3_client != NULL);
  CHECK((features & COAKKA_HTTP_CAPABILITY_HTTP2) != 0U);
  CHECK((features & COAKKA_HTTP_CAPABILITY_HTTP3) != 0U);
  run_protocol(COAKKA_HTTP_PROTOCOL_HTTP_2, fixtures, http2_client,
               http3_client);
  run_protocol(COAKKA_HTTP_PROTOCOL_HTTP_3, fixtures, http2_client,
               http3_client);
  printf("coakka_http_runtime_protocol_test=pass\n");
  return EXIT_SUCCESS;
}
