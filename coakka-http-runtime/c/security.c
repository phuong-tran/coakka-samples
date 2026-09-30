/* Host-inlined C11 TLS and mutual-TLS sample. */
#include <coakka/http/host.h>

#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
typedef HANDLE sample_thread_t;
#else
#include <pthread.h>
#include <time.h>
typedef pthread_t sample_thread_t;
#endif

typedef struct security_state {
  coakka_http_host_service_t *service;
  const char *response_body;
  atomic_int stopping;
  atomic_int failed;
} security_state_t;

static volatile sig_atomic_t signal_stopping = 0;

static coakka_http_host_bytes_t bytes(const char *value) {
  coakka_http_host_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : strlen(value);
  return result;
}

static void run_events(security_state_t *state) {
  while (atomic_load_explicit(&state->stopping, memory_order_acquire) == 0) {
    coakka_http_host_request_event_t event;
    coakka_http_host_status_t status;
    coakka_http_host_request_event_init(&event);
    status = coakka_http_host_take_request(state->service, 100U, &event);
    if (status == COAKKA_HTTP_HOST_TIMEOUT) {
      continue;
    }
    if (status == COAKKA_HTTP_HOST_CLOSED) {
      break;
    }
    if (status != COAKKA_HTTP_HOST_OK) {
      atomic_store_explicit(&state->failed, 1, memory_order_release);
      break;
    }
    if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
      coakka_http_host_response_t response;
      coakka_http_host_response_init(&response);
      response.body = bytes(state->response_body);
      if (coakka_http_host_respond(state->service, event.exchange, &response) !=
          COAKKA_HTTP_HOST_OK) {
        atomic_store_explicit(&state->failed, 1, memory_order_release);
      }
    }
    if (coakka_http_host_release_request(state->service, &event) !=
        COAKKA_HTTP_HOST_OK) {
      atomic_store_explicit(&state->failed, 1, memory_order_release);
    }
  }
}

#if defined(_WIN32)
static DWORD WINAPI event_entry(LPVOID opaque) {
  run_events((security_state_t *)opaque);
  return 0U;
}
static int start_events(sample_thread_t *thread, security_state_t *state) {
  *thread = CreateThread(NULL, 0U, event_entry, state, 0U, NULL);
  return *thread != NULL;
}
static void join_events(sample_thread_t thread) {
  (void)WaitForSingleObject(thread, INFINITE);
  (void)CloseHandle(thread);
}
static void wait_briefly(void) { Sleep(50U); }
#else
static void *event_entry(void *opaque) {
  run_events((security_state_t *)opaque);
  return NULL;
}
static int start_events(sample_thread_t *thread, security_state_t *state) {
  return pthread_create(thread, NULL, event_entry, state) == 0;
}
static void join_events(sample_thread_t thread) {
  (void)pthread_join(thread, NULL);
}
static void wait_briefly(void) {
  const struct timespec delay = {0, 50000000L};
  (void)nanosleep(&delay, NULL);
}
#endif

static void stop_signal(int signal_number) {
  (void)signal_number;
  signal_stopping = 1;
}

static const char *value_after(int argc, char **argv, const char *option) {
  int index;
  for (index = 1; index + 1 < argc; ++index) {
    if (strcmp(argv[index], option) == 0) {
      return argv[index + 1];
    }
  }
  return NULL;
}

int main(int argc, char **argv) {
  const char *mode = value_after(argc, argv, "--security");
  const char *protocol = value_after(argc, argv, "--protocol");
  const char *fixtures = value_after(argc, argv, "--fixtures");
  const char *port_text = value_after(argc, argv, "--port");
  char certificate[4096];
  char private_key[4096];
  char trust_roots[4096];
  coakka_http_host_configuration_t configuration;
  coakka_http_host_route_t route;
  coakka_http_host_service_t *service = NULL;
  security_state_t state;
  sample_thread_t thread;
  uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  unsigned long port;
  coakka_http_host_status_t status;
  int certificate_length;
  int private_key_length;
  int trust_roots_length;
  int failed = 0;

  if (mode == NULL || protocol == NULL || fixtures == NULL ||
      port_text == NULL) {
    fputs("required: --security tls|mtls --protocol http1|http2|http3 "
          "--fixtures DIR --port PORT\n",
          stderr);
    return EXIT_FAILURE;
  }
  port = strtoul(port_text, NULL, 10);
  certificate_length =
      snprintf(certificate, sizeof(certificate), "%s/server.pem", fixtures);
  private_key_length =
      snprintf(private_key, sizeof(private_key), "%s/server.key", fixtures);
  trust_roots_length =
      snprintf(trust_roots, sizeof(trust_roots), "%s/ca.pem", fixtures);
  if (port == 0UL || port > 65535UL || certificate_length < 0 ||
      (size_t)certificate_length >= sizeof(certificate) ||
      private_key_length < 0 ||
      (size_t)private_key_length >= sizeof(private_key) ||
      trust_roots_length < 0 ||
      (size_t)trust_roots_length >= sizeof(trust_roots)) {
    fputs("invalid security sample arguments\n", stderr);
    return EXIT_FAILURE;
  }

  coakka_http_host_configuration_init(&configuration);
  configuration.bind_address = bytes("127.0.0.1");
  configuration.port = (uint16_t)port;
  if (strcmp(protocol, "http1") == 0) {
    configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
  } else if (strcmp(protocol, "http2") == 0) {
    configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_2;
  } else if (strcmp(protocol, "http3") == 0) {
    configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_3;
  } else {
    fputs("unsupported protocol\n", stderr);
    return EXIT_FAILURE;
  }
  if (strcmp(mode, "tls") == 0) {
    configuration.security = COAKKA_HTTP_HOST_SECURITY_TLS;
  } else if (strcmp(mode, "mtls") == 0) {
    configuration.security = COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS;
  } else {
    fputs("security must be tls or mtls\n", stderr);
    return EXIT_FAILURE;
  }
  configuration.credential_generation = UINT64_C(1);
  configuration.credential_id = bytes("c-sample-server");
  configuration.certificate_chain_file = bytes(certificate);
  configuration.private_key_file = bytes(private_key);
  if (configuration.security == COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS) {
    configuration.trust_roots_file = bytes(trust_roots);
  }

  coakka_http_host_route_init(&route);
  route.route_id = UINT64_C(1);
  route.handler_binding_id = UINT64_C(1);
  route.method = bytes("GET");
  route.encoded_path_pattern = bytes("/secure");
  status = coakka_http_host_service_create(&configuration, &route, 1U, &service,
                                           &failed_route);
  if (status != COAKKA_HTTP_HOST_OK) {
    fprintf(stderr, "secure service creation failed: %s (%d), route=%u\n",
            coakka_http_host_status_name(status), (int)status,
            (unsigned)failed_route);
    coakka_http_host_service_destroy(&service);
    return EXIT_FAILURE;
  }
  {
    coakka_http_host_issue_t issue;
    coakka_http_host_issue_init(&issue);
    status = coakka_http_host_service_start(service, &issue);
    if (status != COAKKA_HTTP_HOST_OK) {
      fprintf(stderr,
              "secure listener startup failed: %s (status=%d reason=%u "
              "system=%d)\n",
              issue.detail, (int)issue.status, (unsigned)issue.reason,
              (int)issue.system_code);
    }
  }
  if (status != COAKKA_HTTP_HOST_OK) {
    coakka_http_host_service_destroy(&service);
    return EXIT_FAILURE;
  }

  state.service = service;
  state.response_body =
      configuration.security == COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS
          ? "mtls-ready"
          : "tls-ready";
  atomic_init(&state.stopping, 0);
  atomic_init(&state.failed, 0);
  if (!start_events(&thread, &state)) {
    fputs("event thread creation failed\n", stderr);
    (void)coakka_http_host_service_begin_drain(service);
    (void)coakka_http_host_service_stop(service);
    coakka_http_host_service_destroy(&service);
    return EXIT_FAILURE;
  }
  (void)signal(SIGINT, stop_signal);
#if defined(SIGTERM)
  (void)signal(SIGTERM, stop_signal);
#endif
  while (signal_stopping == 0) {
    wait_briefly();
  }
  atomic_store_explicit(&state.stopping, 1, memory_order_release);
  (void)coakka_http_host_interrupt_requests(service);
  join_events(thread);
  if (atomic_load_explicit(&state.failed, memory_order_acquire) != 0) {
    failed = 1;
  }
  (void)coakka_http_host_service_begin_drain(service);
  if (coakka_http_host_service_stop(service) != COAKKA_HTTP_HOST_OK) {
    failed = 1;
  }
  coakka_http_host_service_destroy(&service);
  return failed ? EXIT_FAILURE : EXIT_SUCCESS;
}
