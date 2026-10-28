#ifndef COAKKA_NATIVE_EVIDENCE_ENGINE_OWNER_H
#define COAKKA_NATIVE_EVIDENCE_ENGINE_OWNER_H

#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime_startup.h"

/* Private consumer fixture, not installed Core ABI. Never copy a live owner.
 * All calls are serialized. Borrowed frames/tickets and concurrent callers
 * must retire before close; this owner never infers their state. */
typedef enum native_engine_operation_t {
  NATIVE_ENGINE_NONE,
  NATIVE_ENGINE_CREATE,
  NATIVE_ENGINE_STARTUP_FACTS,
  NATIVE_ENGINE_DELIVERY_FACTS,
  NATIVE_ENGINE_HANDLES,
  NATIVE_ENGINE_ROUTES,
  NATIVE_ENGINE_START,
  NATIVE_ENGINE_CLIENT,
  NATIVE_ENGINE_READER,
  NATIVE_ENGINE_STOP
} native_engine_operation_t;

typedef struct native_engine_failure_t {
  native_engine_operation_t operation;
  coakka_v2_status_t status;
} native_engine_failure_t;

typedef struct native_engine_facts_t {
  /* Captured before start, not live applied/lifecycle observations. */
  coakka_v2_runtime_startup_config_t startup;
  coakka_v2_runtime_delivery_config_t delivery;
  int startup_checked;
  int delivery_checked;
} native_engine_facts_t;

typedef struct native_engine_owner_t {
  coakka_v2_runtime_t* runtime;
  coakka_v2_host_handles_t handles;
  coakka_v2_request_reader_t* reader;
  coakka_v2_ask_client_t* client;
  native_engine_facts_t facts;
  /* Accepted start requires a successful stop before destruction. Not an
   * observable runtime phase and never projected as one. */
  int stop_required;
} native_engine_owner_t;

/* Initialize only fresh/fully closed storage. No allocation or Core call. */
void native_engine_owner_init(native_engine_owner_t* owner);

/* Core copies config/routes according to its public contracts. On refusal,
 * failure names the attempted operation/status (NONE for a local ownership
 * guard) and partial ownership stays in
 * owner for explicit close. Only checked snapshots contain observed facts. */
coakka_v2_status_t native_engine_owner_open(
    native_engine_owner_t* owner, const coakka_v2_runtime_config_t* config,
    const coakka_v2_control_snapshot_t* routes, native_engine_failure_t* failure);

/* Reader must have no active read or frame lease. Stop failure preserves the
 * client/handles/runtime and cleanup obligation for retry; no unsafe destroy.
 * OK means this fixture retired its owners, not a fabricated Core state read. */
coakka_v2_status_t native_engine_owner_close(
    native_engine_owner_t* owner, native_engine_failure_t* failure);

#endif
