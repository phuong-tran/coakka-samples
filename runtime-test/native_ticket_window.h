#ifndef COAKKA_EVIDENCE_NATIVE_TICKET_WINDOW_H
#define COAKKA_EVIDENCE_NATIVE_TICKET_WINDOW_H

#include "coakka/v2/client.h"

/* Private serialized workload/test seam. Storage is caller-owned and zeroed.
 * A successful push transfers one ticket reference into the window; refusal
 * leaves it with the caller. Taking the front transfers it back. No Core call,
 * matching, allocation, cancellation or concurrent access is hidden here. */
enum { NATIVE_WORKLOAD_MAX_TICKETS = 500000 };
typedef struct native_ticket_window_t {
  coakka_v2_ask_ticket_t** slots;
  size_t capacity;
  size_t head;
  size_t count;
} native_ticket_window_t;

static inline int native_ticket_window_init(native_ticket_window_t* window,
    coakka_v2_ask_ticket_t** storage, size_t capacity) {
  if (window == NULL || storage == NULL || capacity == 0 || capacity > NATIVE_WORKLOAD_MAX_TICKETS) return 0;
  window->slots = storage;
  window->capacity = capacity;
  window->head = 0;
  window->count = 0;
  return 1;
}

static inline int native_ticket_window_push(native_ticket_window_t* window,
    coakka_v2_ask_ticket_t* ticket) {
  if (window == NULL || window->slots == NULL || ticket == NULL || window->count >= window->capacity) return 0;
  /* Both addends are below the validated500K ceiling; no size_t overflow. */
  window->slots[(window->head + window->count) % window->capacity] = ticket;
  ++window->count;
  return 1;
}

static inline coakka_v2_ask_ticket_t* native_ticket_window_front(const native_ticket_window_t* window) {
  return window == NULL || window->count == 0 ? NULL : window->slots[window->head];
}

static inline coakka_v2_ask_ticket_t* native_ticket_window_take(native_ticket_window_t* window) {
  coakka_v2_ask_ticket_t* ticket = native_ticket_window_front(window);
  if (ticket != NULL) {
    window->slots[window->head] = NULL;
    window->head = (window->head + 1u) % window->capacity;
    --window->count;
  }
  return ticket;
}

#endif
