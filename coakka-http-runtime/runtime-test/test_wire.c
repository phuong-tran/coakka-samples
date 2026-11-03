#include "test_support.h"

#include <string.h>

static size_t line_end(const char *wire, size_t size, size_t from) {
  size_t i;
  for (i = from; i < size && size - i >= 2U; ++i)
    if (wire[i] == '\r' && wire[i + 1U] == '\n') return i;
  return size;
}

static int ascii_equal(const char *bytes, size_t size, const char *expected) {
  size_t i;
  if (size != strlen(expected)) return 0;
  for (i = 0U; i < size; ++i) {
    unsigned char c = (unsigned char)bytes[i];
    if (c >= (unsigned char)'A' && c <= (unsigned char)'Z')
      c = (unsigned char)(c + ((unsigned char)'a' - (unsigned char)'A'));
    if (c != (unsigned char)expected[i]) return 0;
  }
  return 1;
}

static int status_line(const char *wire, size_t size, unsigned status) {
  char prefix[32];
  const int length = snprintf(prefix, sizeof(prefix), "HTTP/1.1 %u ", status);
  return length > 0 && (size_t)length < sizeof(prefix) && size >= (size_t)length &&
         memcmp(wire, prefix, (size_t)length) == 0;
}

static size_t body_start(const char *wire, size_t size) {
  size_t i;
  for (i = 0U; i < size && size - i >= 4U; ++i)
    if (memcmp(wire + i, "\r\n\r\n", 4U) == 0) return i + 4U;
  return size + 1U; /* size is fixture-bounded, so this cannot overflow. */
}

int test_wire_response(const char *wire, size_t size, unsigned status,
                        const char *body) {
  size_t start, body_size;
  if (wire == NULL || body == NULL || size > TEST_WIRE_CAPACITY) return 0;
  start = body_start(wire, size);
  body_size = strlen(body);
  return status_line(wire, size, status) && start <= size &&
         size - start == body_size && memcmp(wire + start, body, body_size) == 0;
}

/* This bounded assertion understands only the chunked fixture response below.
 * It is test code, not a reusable HTTP client/parser or an alternate data plane.
 * Write boundaries may be coalesced: compare decoded bytes, not chunk sizes. */
int test_wire_chunked(const char *wire, size_t size, const char *body,
                       const char *trailer_name, const char *trailer_value) {
  size_t position, wanted, decoded = 0U, head = 0U;
  int chunked = 0, found_trailer = 0;
  if (wire == NULL || body == NULL || trailer_name == NULL ||
      trailer_value == NULL || size > TEST_WIRE_CAPACITY) return 0;
  position = body_start(wire, size);
  wanted = strlen(body);
  if (!status_line(wire, size, 200U) || position > size) return 0;
  head = line_end(wire, size, 0U);
  if (head == size) return 0;
  head += 2U;
  while (head < position - 2U) {
    const size_t end = line_end(wire, size, head);
    const char *colon;
    size_t value;
    if (end == size || end < head) return 0;
    colon = (const char *)memchr(wire + head, ':', end - head);
    if (colon == NULL) return 0;
    value = (size_t)(colon - wire) + 1U;
    while (value < end && (wire[value] == ' ' || wire[value] == '\t')) ++value;
    if (ascii_equal(wire + head, (size_t)(colon - wire) - head, "content-length"))
      return 0; /* Conflicting framing must not pass an echo-only assertion. */
    if (ascii_equal(wire + head, (size_t)(colon - wire) - head, "transfer-encoding")) {
      if (!ascii_equal(wire + value, end - value, "chunked")) return 0;
      chunked = 1;
    }
    head = end + 2U;
  }
  if (!chunked) return 0;
  for (;;) {
    size_t extent = 0U, i;
    const size_t end = line_end(wire, size, position);
    if (end == size || end == position || end - position > sizeof(size_t) * 2U)
      return 0;
    for (i = position; i < end; ++i) {
      const unsigned char c = (unsigned char)wire[i];
      unsigned digit;
      if (c >= '0' && c <= '9') digit = (unsigned)(c - '0');
      else if (c >= 'a' && c <= 'f') digit = (unsigned)(c - 'a') + 10U;
      else if (c >= 'A' && c <= 'F') digit = (unsigned)(c - 'A') + 10U;
      else return 0;
      if (extent > (SIZE_MAX - digit) / 16U) return 0;
      extent = extent * 16U + digit;
    }
    position = end + 2U;
    if (extent == 0U) break;
    if (extent > size - position || extent > wanted - decoded ||
        size - position - extent < 2U ||
        memcmp(wire + position, body + decoded, extent) != 0 ||
        memcmp(wire + position + extent, "\r\n", 2U) != 0) return 0;
    position += extent + 2U;
    decoded += extent;
  }
  for (;;) {
    const size_t end = line_end(wire, size, position);
    const char *colon;
    size_t value, value_end;
    if (end == size) return 0;
    if (end == position)
      return end + 2U == size && decoded == wanted && found_trailer;
    colon = (const char *)memchr(wire + position, ':', end - position);
    if (colon == NULL || found_trailer ||
        !ascii_equal(wire + position, (size_t)(colon - wire) - position, trailer_name)) return 0;
    value = (size_t)(colon - wire) + 1U;
    value_end = end;
    while (value < value_end && (wire[value] == ' ' || wire[value] == '\t')) ++value;
    while (value_end > value && (wire[value_end - 1U] == ' ' || wire[value_end - 1U] == '\t')) --value_end;
    if (value_end - value != strlen(trailer_value) ||
        memcmp(wire + value, trailer_value, value_end - value) != 0) return 0;
    found_trailer = 1;
    position = end + 2U;
  }
}
