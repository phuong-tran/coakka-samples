#include "native_engine_owner.h"
#include "coakka/v2/transport.h"

#include <string.h>

void native_engine_owner_init(native_engine_owner_t* owner) {
  memset(owner, 0, sizeof(*owner));
  owner->handles.struct_size = sizeof(owner->handles);
  owner->handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
  owner->handles.request_write_fd = -1;
  owner->handles.response_read_fd = -1;
  owner->handles.deadletter_read_fd = -1;
  owner->handles.control_write_fd = -1;
  owner->handles.monitor_read_fd = -1;
  owner->handles.delivered_request_read_fd = -1;
}

coakka_v2_status_t native_engine_owner_open(
    native_engine_owner_t* owner, const coakka_v2_runtime_config_t* config,
    const coakka_v2_control_snapshot_t* routes, native_engine_failure_t* failure) {
  if (failure == NULL) return COAKKA_V2_ERR_INVALID_ARG;
  failure->operation = NATIVE_ENGINE_NONE;
  failure->status = COAKKA_V2_OK;
  if (owner == NULL || owner->runtime != NULL) {
    failure->status = owner == NULL ? COAKKA_V2_ERR_INVALID_ARG : COAKKA_V2_ERR_BAD_STATE;
    return failure->status;
  }
#define ACQUIRE(operation_, call_) do { \
  failure->operation = (operation_); \
  failure->status = (call_); \
  if (failure->status != COAKKA_V2_OK) return failure->status; \
} while (0)
  ACQUIRE(NATIVE_ENGINE_CREATE,
      coakka_v2_runtime_create_native(config, NULL, &owner->runtime));
  owner->facts.startup.struct_size = sizeof(owner->facts.startup);
  ACQUIRE(NATIVE_ENGINE_STARTUP_FACTS,
      coakka_v2_runtime_get_startup_config(owner->runtime, &owner->facts.startup));
  owner->facts.startup_checked = 1;
  owner->facts.delivery.struct_size = sizeof(owner->facts.delivery);
  ACQUIRE(NATIVE_ENGINE_DELIVERY_FACTS,
      coakka_v2_runtime_get_delivery_config(owner->runtime, &owner->facts.delivery));
  owner->facts.delivery_checked = 1;
  ACQUIRE(NATIVE_ENGINE_HANDLES,
      coakka_v2_runtime_get_host_handles(owner->runtime, &owner->handles));
  ACQUIRE(NATIVE_ENGINE_ROUTES,
      coakka_v2_runtime_apply_control_snapshot(owner->runtime, routes));
  ACQUIRE(NATIVE_ENGINE_START, coakka_v2_runtime_start(owner->runtime));
  owner->stop_required = 1;
  ACQUIRE(NATIVE_ENGINE_CLIENT,
      coakka_v2_ask_client_create_ex(owner->runtime, &owner->handles, &owner->client));
  ACQUIRE(NATIVE_ENGINE_READER,
      coakka_v2_runtime_request_reader_create(owner->runtime, &owner->handles, &owner->reader));
#undef ACQUIRE
  failure->operation = NATIVE_ENGINE_NONE;
  return COAKKA_V2_OK;
}

coakka_v2_status_t native_engine_owner_close(
    native_engine_owner_t* owner, native_engine_failure_t* failure) {
  if (failure == NULL) return COAKKA_V2_ERR_INVALID_ARG;
  failure->operation = NATIVE_ENGINE_NONE;
  failure->status = COAKKA_V2_OK;
  if (owner == NULL) {
    failure->status = COAKKA_V2_ERR_INVALID_ARG;
    return failure->status;
  }
  coakka_v2_request_reader_destroy(owner->reader);
  owner->reader = NULL;
  if (owner->stop_required) {
    failure->operation = NATIVE_ENGINE_STOP;
    failure->status = coakka_v2_runtime_stop(owner->runtime);
    if (failure->status != COAKKA_V2_OK) return failure->status;
    owner->stop_required = 0;
  }
  coakka_v2_ask_client_destroy(owner->client);
  owner->client = NULL;
  coakka_v2_host_handles_close(&owner->handles);
  coakka_v2_runtime_destroy(owner->runtime);
  native_engine_owner_init(owner);
  failure->operation = NATIVE_ENGINE_NONE;
  return COAKKA_V2_OK;
}
