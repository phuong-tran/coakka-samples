/* Fixed response through GNU libmicrohttpd; no socket-only baseline exists. */
#include <microhttpd.h>

#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static volatile sig_atomic_t stopping = 0;
/* MHD uses a non-NULL per-request context to distinguish later callbacks. */
static int request_started;
static const char body[] = "0123456789abcdef0123456789abcdef";

static void stop_signal(int signal_number) {
  (void)signal_number;
  stopping = 1;
}

static enum MHD_Result fixed(void *context, struct MHD_Connection *connection,
                             const char *url, const char *method,
                             const char *version, const char *upload_data,
                             size_t *upload_size, void **request_context) {
  struct MHD_Response *response;
  enum MHD_Result result;
  (void)context;
  (void)version;
  (void)upload_data;
  (void)upload_size;
  if (strcmp(method, "GET") != 0 || strcmp(url, "/fixed") != 0) {
    return MHD_NO;
  }
  /* Queuing a response in the first callback makes MHD close the connection.
   * Defer it until the request is ready so this lane measures persistent
   * HTTP/1.1 connections like the other framework and CoAkka lanes.
   */
  if (*request_context == NULL) {
    *request_context = &request_started;
    return MHD_YES;
  }
  response = MHD_create_response_from_buffer(sizeof(body) - 1U, (void *)body,
                                             MHD_RESPMEM_PERSISTENT);
  if (response == NULL) {
    return MHD_NO;
  }
  (void)MHD_add_response_header(response, "Content-Type",
                                "application/octet-stream");
  result = MHD_queue_response(connection, MHD_HTTP_OK, response);
  MHD_destroy_response(response);
  return result;
}

int main(int argc, char **argv) {
  struct MHD_Daemon *daemon;
  unsigned long port;
  if (argc != 2) {
    return EXIT_FAILURE;
  }
  port = strtoul(argv[1], NULL, 10);
  if (port == 0UL || port > 65535UL) {
    return EXIT_FAILURE;
  }
  daemon = MHD_start_daemon(
      MHD_USE_INTERNAL_POLLING_THREAD | MHD_USE_EPOLL_INTERNAL_THREAD,
      (uint16_t)port, NULL, NULL, &fixed, NULL, MHD_OPTION_THREAD_POOL_SIZE, 3U,
      MHD_OPTION_CONNECTION_LIMIT, 512U, MHD_OPTION_END);
  if (daemon == NULL) {
    return EXIT_FAILURE;
  }
  (void)signal(SIGINT, stop_signal);
  (void)signal(SIGTERM, stop_signal);
  while (!stopping) {
    (void)usleep(50000U);
  }
  MHD_stop_daemon(daemon);
  return EXIT_SUCCESS;
}
