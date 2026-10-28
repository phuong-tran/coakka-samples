#include "native_ticket_window.h"
#include <assert.h>
#include <stdio.h>

#ifdef NDEBUG
#error "Native ticket-window controls require active assertions"
#endif

int main(void) {
  char tokens[4] = {0};
  coakka_v2_ask_ticket_t* storage[3] = {0};
  coakka_v2_ask_ticket_t* tickets[4];
  native_ticket_window_t window = {0};
  for (unsigned index = 0; index < 4; ++index) tickets[index] = (coakka_v2_ask_ticket_t*)(void*)&tokens[index];
  assert(!native_ticket_window_init(NULL, storage, 3));
  assert(!native_ticket_window_init(&window, NULL, 3));
  assert(!native_ticket_window_init(&window, storage, 0));
  assert(!native_ticket_window_init(&window, storage, NATIVE_WORKLOAD_MAX_TICKETS + 1u));
  assert(native_ticket_window_front(NULL) == NULL && native_ticket_window_take(NULL) == NULL);
  assert(!native_ticket_window_push(NULL, tickets[0]));
  assert(!native_ticket_window_push(&window, tickets[0]));
  assert(native_ticket_window_init(&window, storage, 3));
  assert(!native_ticket_window_push(&window, NULL));
  assert(native_ticket_window_front(&window) == NULL && native_ticket_window_take(&window) == NULL);
  for (unsigned round = 0; round < 4096; ++round) {
    assert(native_ticket_window_push(&window, tickets[0]));
    assert(native_ticket_window_push(&window, tickets[1]));
    assert(native_ticket_window_push(&window, tickets[2]));
    assert(!native_ticket_window_push(&window, tickets[3]));
    assert(window.count == 3 && native_ticket_window_front(&window) == tickets[0]);
    assert(native_ticket_window_take(&window) == tickets[0]);
    assert(native_ticket_window_push(&window, tickets[3]));
    assert(native_ticket_window_take(&window) == tickets[1]);
    assert(native_ticket_window_take(&window) == tickets[2]);
    assert(native_ticket_window_take(&window) == tickets[3]);
    assert(window.count == 0 && native_ticket_window_take(&window) == NULL);
    assert(storage[0] == NULL && storage[1] == NULL && storage[2] == NULL);
  }
  assert(native_ticket_window_init(&window, storage, 1));
  assert(native_ticket_window_push(&window, tickets[0]));
  assert(!native_ticket_window_push(&window, tickets[1]));
  assert(native_ticket_window_take(&window) == tickets[0] && window.count == 0);
  puts("PASS: bounded ticket ownership/refusal/FIFO/wrap/empty/single-slot;4096 rounds, no Core load");
  return 0;
}
