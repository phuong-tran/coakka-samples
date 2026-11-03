#if !defined(_WIN32) && !defined(_POSIX_C_SOURCE)
#define _POSIX_C_SOURCE 200809L
#endif
#include "test_support.h"

#include <errno.h>
#include <limits.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>
#endif

int test_now_ms(uint64_t *out_ms) {
#if defined(_WIN32)
  *out_ms = (uint64_t)GetTickCount64();
#else
  struct timespec value;
  if (clock_gettime(CLOCK_MONOTONIC, &value) != 0 || value.tv_sec < 0 ||
      value.tv_nsec < 0 || value.tv_nsec >= 1000000000L ||
      (uint64_t)value.tv_sec > (UINT64_MAX - UINT64_C(999)) / UINT64_C(1000)) return 0;
  *out_ms = (uint64_t)value.tv_sec * UINT64_C(1000) +
            (uint64_t)value.tv_nsec / UINT64_C(1000000);
#endif
  return 1;
}

static int deadline(uint64_t *out_ms) {
  uint64_t now;
  if (!test_now_ms(&now) || now > UINT64_MAX - TEST_IO_TIMEOUT_MS) return 0;
  *out_ms = now + TEST_IO_TIMEOUT_MS;
  return 1;
}

static int interrupted(void) {
#if defined(_WIN32)
  return WSAGetLastError() == WSAEINTR;
#else
  return errno == EINTR;
#endif
}

static int would_block(void) {
#if defined(_WIN32)
  const int error = WSAGetLastError();
  return error == WSAEWOULDBLOCK || error == WSAEINPROGRESS;
#else
  return errno == EAGAIN || errno == EWOULDBLOCK || errno == EINPROGRESS;
#endif
}

static int ready(test_socket_t socket, int writing, uint64_t until) {
  for (;;) {
    fd_set descriptors;
    struct timeval wait;
    uint64_t now, remaining;
    int result;
    if (!test_now_ms(&now) || now >= until) return 0;
    remaining = until - now;
    wait.tv_sec = (long)(remaining / UINT64_C(1000));
#if defined(_WIN32)
    wait.tv_usec = (long)((remaining % UINT64_C(1000)) * UINT64_C(1000));
#else
    wait.tv_usec = (suseconds_t)((remaining % UINT64_C(1000)) * UINT64_C(1000));
#endif
    FD_ZERO(&descriptors);
#if !defined(_WIN32)
    if (socket < 0 || socket >= FD_SETSIZE) return 0;
#endif
    FD_SET(socket, &descriptors);
#if defined(_WIN32)
    result = select(0, writing ? NULL : &descriptors,
                    writing ? &descriptors : NULL, NULL, &wait);
#else
    result = select(socket + 1, writing ? NULL : &descriptors,
                    writing ? &descriptors : NULL, NULL, &wait);
#endif
    if (result > 0) return 1;
    if (result == 0 || !interrupted()) return 0;
    /* Signals consume the same caller budget; they never reset it. */
  }
}

int test_socket_close(test_socket_t *socket) {
  const test_socket_t owned = *socket;
  if (owned == TEST_INVALID_SOCKET) return 1;
  *socket = TEST_INVALID_SOCKET;
#if defined(_WIN32)
  return closesocket(owned) == 0;
#else
  /* Do not retry close after EINTR: the descriptor may already be reusable. */
  return close(owned) == 0;
#endif
}

test_socket_t test_connect(uint16_t port) {
  struct sockaddr_in address;
  test_socket_t peer = TEST_INVALID_SOCKET;
  uint64_t until;
  int result, error = 0;
#if defined(_WIN32)
  int error_size = (int)sizeof(error);
  u_long nonblocking = 1UL;
#else
  socklen_t error_size = (socklen_t)sizeof(error);
  int flags;
#endif
  if (!deadline(&until)) return TEST_INVALID_SOCKET;
  peer = socket(AF_INET, SOCK_STREAM, 0);
  if (peer == TEST_INVALID_SOCKET) return peer;
#if defined(_WIN32)
  if (ioctlsocket(peer, FIONBIO, &nonblocking) != 0) goto failed;
#else
  flags = fcntl(peer, F_GETFL, 0);
  if (flags < 0 || fcntl(peer, F_SETFL, flags | O_NONBLOCK) != 0) goto failed;
#if defined(SO_NOSIGPIPE)
  {
    const int enabled = 1;
    if (setsockopt(peer, SOL_SOCKET, SO_NOSIGPIPE, &enabled, sizeof(enabled)) != 0)
      goto failed;
  }
#endif
#endif
  memset(&address, 0, sizeof(address));
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  if (inet_pton(AF_INET, "127.0.0.1", &address.sin_addr) != 1) goto failed;
  result = connect(peer, (const struct sockaddr *)&address, sizeof(address));
  if (result != 0) {
    if ((!would_block() && !interrupted()) || !ready(peer, 1, until)) goto failed;
    if (getsockopt(peer, SOL_SOCKET, SO_ERROR, (void *)&error, &error_size) != 0 ||
        error != 0) goto failed;
  }
  return peer;
failed:
  (void)test_socket_close(&peer);
  return TEST_INVALID_SOCKET;
}

int test_send(test_socket_t socket, const void *data, size_t size) {
  const char *bytes = (const char *)data;
  size_t sent = 0U;
  uint64_t until;
  if ((size != 0U && data == NULL) || !deadline(&until)) return 0;
  while (sent < size) {
    const size_t remaining = size - sent;
    const int amount = remaining > (size_t)INT_MAX ? INT_MAX : (int)remaining;
    int flags = 0;
#if defined(MSG_NOSIGNAL)
    flags = MSG_NOSIGNAL;
#endif
    if (!ready(socket, 1, until)) return 0;
#if defined(_WIN32)
    const int count = send(socket, bytes + sent, amount, flags);
#else
    const ssize_t count = send(socket, bytes + sent, (size_t)amount, flags);
#endif
    if (count < 0 && (interrupted() || would_block())) continue;
    if (count <= 0) return 0;
    sent += (size_t)count;
  }
  return 1;
}

int test_receive(test_socket_t socket, char *wire, size_t capacity,
                  size_t *out_size) {
  uint64_t until;
  size_t used = 0U;
  if (wire == NULL || out_size == NULL || capacity == 0U ||
      capacity > TEST_WIRE_CAPACITY || !deadline(&until)) return 0;
  *out_size = 0U;
  for (;;) {
    if (used == capacity - 1U || !ready(socket, 0, until)) return 0;
#if defined(_WIN32)
    const int count = recv(socket, wire + used, (int)(capacity - 1U - used), 0);
#else
    const ssize_t count = recv(socket, wire + used, capacity - 1U - used, 0);
#endif
    if (count < 0 && (interrupted() || would_block())) continue;
    if (count < 0) return 0;
    if (count == 0) {
      wire[used] = '\0';
      *out_size = used;
      return 1;
    }
    used += (size_t)count;
  }
}

