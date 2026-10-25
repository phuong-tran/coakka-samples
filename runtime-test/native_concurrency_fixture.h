#ifndef COAKKA_EVIDENCE_NATIVE_CONCURRENCY_FIXTURE_H
#define COAKKA_EVIDENCE_NATIVE_CONCURRENCY_FIXTURE_H

#include "native_engine_owner.h"

/* App-owned test substrate shared by workload/snapshot and stop/lifecycle
 * cases. Core still owns matching, modes, effective state and all outcomes. */
typedef struct native_concurrency_fixture_t {
  native_engine_owner_t core;
  struct { uint8_t* bytes; size_t size; } pending_reply;
} native_concurrency_fixture_t;

/* Fresh/closed storage only. No allocation; never copy a live fixture. */
void native_concurrency_fixture_init(native_concurrency_fixture_t* fixture);

/* NULL target means an explicitly empty application route snapshot, not a
 * default/fallback. Other strings reach Core unchanged for its validation.
 * On failure the partially acquired owner remains for explicit close. */
coakka_v2_status_t native_concurrency_fixture_open(
    native_concurrency_fixture_t* fixture, size_t queue_capacity,
    const char* node_id, const char* target, native_engine_failure_t* failure);

coakka_v2_status_t native_concurrency_apply_snapshot(
    coakka_v2_runtime_t* runtime, uint64_t generation, const char* target);

/* The test request intent is the original5000ms, not a Core default table.
 * Builder borrows arguments only until return; caller releases output bytes. */
int native_concurrency_build_request(size_t producer, uint64_t sequence,
    const char* target, uint8_t** bytes, size_t* size);

/* Caller must release frames/tickets and join all users first. Pending reply
 * is caller-owned wire storage, not an execution lease. Stop refusal preserves
 * remaining Core owners through native_engine_owner_close's exact law. */
coakka_v2_status_t native_concurrency_fixture_close(
    native_concurrency_fixture_t* fixture, native_engine_failure_t* failure);

#endif
