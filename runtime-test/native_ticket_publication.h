#ifndef COAKKA_EVIDENCE_NATIVE_TICKET_PUBLICATION_H
#define COAKKA_EVIDENCE_NATIVE_TICKET_PUBLICATION_H

#include "coakka/v2/client.h"
#include <stdatomic.h>
#include <stdint.h>

/* Private finite handoff, not Core matching or a reusable concurrent queue.
 * Exactly one producer appends once per slot; exactly one controller owns a
 * separate cursor. Storage is caller-owned, zeroed, and cannot move/reset while
 * live. Join the producer and retire all remaining tickets before freeing it.
 * Consumed slot addresses remain stale bookkeeping, never dereferenced again. */
typedef struct native_ticket_publication_t {
  coakka_v2_ask_ticket_t** slots;
  size_t capacity;
  atomic_size_t published;
} native_ticket_publication_t;

typedef struct native_ticket_cursor_t {
  const native_ticket_publication_t* source;
  size_t consumed;
} native_ticket_cursor_t;

/* Only fresh storage. Initialization and both immutable fields precede thread
 * creation; this does not supply visibility for later producer slot writes. */
static inline int native_ticket_publication_init(native_ticket_publication_t* publication,
    coakka_v2_ask_ticket_t** slots, size_t capacity) {
  if (publication == NULL || slots == NULL || capacity == 0 || capacity > SIZE_MAX / sizeof(*slots)) return 0;
  publication->slots = slots;
  publication->capacity = capacity;
  atomic_init(&publication->published, 0);
  return 1;
}

/* Success relinquishes the ticket before return; the receiver may destroy it
 * immediately. Refusal changes no slot/count and retains caller ownership. */
static inline int native_ticket_publication_append(native_ticket_publication_t* publication,
    coakka_v2_ask_ticket_t* ticket) {
  if (publication == NULL || publication->slots == NULL || ticket == NULL) return 0;
  /* Only this producer writes published, so its own cursor load needs no
   * acquire. The release, not timing or a later join, publishes slot/object. */
  const size_t index = atomic_load_explicit(&publication->published, memory_order_relaxed);
  if (index >= publication->capacity) return 0;
  publication->slots[index] = ticket;
  atomic_store_explicit(&publication->published, index + 1u, memory_order_release);
  return 1;
}

static inline int native_ticket_cursor_init(native_ticket_cursor_t* cursor,
    const native_ticket_publication_t* publication) {
  if (cursor == NULL || publication == NULL || publication->slots == NULL) return 0;
  cursor->source = publication;
  cursor->consumed = 0;
  return 1;
}

/* A borrowed front remains owned by the handoff until take. Core poll/await is
 * the only authority for terminal state; this API reports only local ownership. */
static inline coakka_v2_ask_ticket_t* native_ticket_cursor_front(const native_ticket_cursor_t* cursor) {
  if (cursor == NULL || cursor->source == NULL) return NULL;
  const size_t visible = atomic_load_explicit(&cursor->source->published, memory_order_acquire);
  return cursor->consumed < visible ? cursor->source->slots[cursor->consumed] : NULL;
}

static inline coakka_v2_ask_ticket_t* native_ticket_cursor_take(native_ticket_cursor_t* cursor) {
  coakka_v2_ask_ticket_t* ticket = native_ticket_cursor_front(cursor);
  if (ticket != NULL) ++cursor->consumed;
  return ticket;
}

#endif
