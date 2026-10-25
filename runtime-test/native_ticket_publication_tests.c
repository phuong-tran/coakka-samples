#include "native_ticket_publication.h"
#include "evidence_platform.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef NDEBUG
#error "Native ticket-publication controls require active assertions"
#endif

enum { PRODUCERS = 4, TRANSFERS = 4096 };
typedef struct payload_t { uint64_t producer; uint64_t sequence; uint64_t check; } payload_t;
typedef struct producer_t {
  native_ticket_publication_t* publication;
  payload_t* payloads;
  const atomic_int* go;
  uint64_t index;
  uint64_t deadline;
} producer_t;

static int expired(uint64_t deadline) {
  const uint64_t now = evidence_platform_monotonic_ns();
  return now == 0 || now >= deadline;
}

static int publish(void* raw) {
  producer_t* producer = raw;
  /* Deliberately relaxed test-only start gate: it must not publish payloads. */
  while (!atomic_load_explicit(producer->go, memory_order_relaxed)) {
    if (expired(producer->deadline)) return 1;
    evidence_platform_thread_yield();
  }
  for (size_t index = 0; index < TRANSFERS; ++index) {
    payload_t* payload = &producer->payloads[index];
    payload->producer = producer->index;
    payload->sequence = index;
    payload->check = ~(producer->index ^ (uint64_t)index);
    if (!native_ticket_publication_append(producer->publication,
        (coakka_v2_ask_ticket_t*)(void*)payload)) return 1;
  }
  return 0;
}

int main(void) {
  char tokens[2] = {0};
  coakka_v2_ask_ticket_t* one[1] = {0};
  coakka_v2_ask_ticket_t* first = (coakka_v2_ask_ticket_t*)(void*)&tokens[0];
  coakka_v2_ask_ticket_t* second = (coakka_v2_ask_ticket_t*)(void*)&tokens[1];
  native_ticket_publication_t local = {0};
  native_ticket_cursor_t cursor = {0};
  assert(!native_ticket_publication_init(NULL, one, 1));
  assert(!native_ticket_publication_init(&local, NULL, 1));
  assert(!native_ticket_publication_init(&local, one, 0));
  assert(!native_ticket_publication_init(&local, one, SIZE_MAX));
  assert(!native_ticket_publication_append(NULL, first));
  assert(!native_ticket_publication_append(&local, first));
  assert(!native_ticket_cursor_init(NULL, &local));
  assert(!native_ticket_cursor_init(&cursor, NULL));
  assert(native_ticket_cursor_front(NULL) == NULL && native_ticket_cursor_take(NULL) == NULL);
  assert(native_ticket_cursor_front(&cursor) == NULL);
  assert(native_ticket_publication_init(&local, one, 1));
  assert(native_ticket_cursor_init(&cursor, &local));
  assert(!native_ticket_publication_append(&local, NULL));
  assert(native_ticket_cursor_take(&cursor) == NULL);
  assert(native_ticket_publication_append(&local, first));
  assert(!native_ticket_publication_append(&local, second));
  assert(native_ticket_cursor_front(&cursor) == first);
  assert(native_ticket_cursor_take(&cursor) == first);
  assert(native_ticket_cursor_take(&cursor) == NULL);
  /* Taking does NOT recycle producer storage or permit another publication. */
  assert(one[0] == first && !native_ticket_publication_append(&local, second));
  assert(atomic_load_explicit(&local.published, memory_order_relaxed) == 1);

  native_ticket_publication_t publications[PRODUCERS];
  native_ticket_cursor_t cursors[PRODUCERS];
  evidence_platform_thread_t threads[PRODUCERS] = {0};
  producer_t producers[PRODUCERS];
  atomic_int go;
  atomic_init(&go, 0);
  const uint64_t start = evidence_platform_monotonic_ns();
  assert(start != 0);
  const uint64_t deadline = start + UINT64_C(15000000000);
  for (unsigned index = 0; index < PRODUCERS; ++index) {
    coakka_v2_ask_ticket_t** slots = calloc(TRANSFERS, sizeof(*slots));
    payload_t* payloads = calloc(TRANSFERS, sizeof(*payloads));
    assert(slots != NULL && payloads != NULL);
    assert(native_ticket_publication_init(&publications[index], slots, TRANSFERS));
    assert(native_ticket_cursor_init(&cursors[index], &publications[index]));
    producers[index] = (producer_t){&publications[index], payloads, &go, index, deadline};
    assert(evidence_platform_thread_start(&threads[index], publish, &producers[index]) == 0);
  }
  atomic_store_explicit(&go, 1, memory_order_relaxed);
  size_t consumed = 0;
  while (consumed < (size_t)PRODUCERS * TRANSFERS) {
    assert(!expired(deadline));
    for (unsigned index = 0; index < PRODUCERS; ++index) {
      coakka_v2_ask_ticket_t* ticket = native_ticket_cursor_front(&cursors[index]);
      if (ticket == NULL) continue;
      const payload_t* payload = (const payload_t*)(const void*)ticket;
      const size_t sequence = cursors[index].consumed;
      assert(payload == &producers[index].payloads[sequence]);
      assert(payload->producer == index && payload->sequence == sequence);
      assert(payload->check == ~((uint64_t)index ^ (uint64_t)sequence));
      assert(native_ticket_cursor_take(&cursors[index]) == ticket);
      ++consumed;
    }
    evidence_platform_thread_yield();
  }
  /* Join is AFTER ordinary data verification, so it cannot mask a missing
   * publication edge. Fake opaque tickets exist only in this pure test. */
  for (unsigned index = 0; index < PRODUCERS; ++index) {
    int result = 1;
    assert(evidence_platform_thread_join(&threads[index], &result) == 0 && result == 0);
    assert(cursors[index].consumed == TRANSFERS && native_ticket_cursor_front(&cursors[index]) == NULL);
    free(publications[index].slots);
    free(producers[index].payloads);
  }
  puts("PASS: finite ownership/full/empty/no-recycle/refusal;4x4096 release-acquire handoffs, no Core load");
  return 0;
}
