/* White-box fixture owner tests: only public Core entrypoints are faked. */
#include "native_engine_owner.h"
#include "coakka/v2/transport.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

#ifdef NDEBUG
#error "Native engine fixture controls require active assertions"
#endif

static unsigned char runtime_token, client_token, reader_token;
static native_engine_operation_t refusal;
static int started, calls;
static char retired[32];
static size_t retired_count;
#define RUNTIME ((coakka_v2_runtime_t*)&runtime_token)
#define CLIENT ((coakka_v2_ask_client_t*)&client_token)
#define READER ((coakka_v2_request_reader_t*)&reader_token)

static coakka_v2_status_t result(native_engine_operation_t operation) {
  ++calls;
  if (operation != refusal) return COAKKA_V2_OK;
  return operation == NATIVE_ENGINE_CREATE || operation == NATIVE_ENGINE_CLIENT ||
      operation == NATIVE_ENGINE_READER ? COAKKA_V2_ERR_NOMEM : COAKKA_V2_ERR_IO;
}
static void retired_one(char value) {
  assert(retired_count + 1 < sizeof(retired));
  retired[retired_count++] = value;
  retired[retired_count] = '\0';
}
coakka_v2_status_t coakka_v2_runtime_create_native(
    const coakka_v2_runtime_config_t* base,
    const coakka_v2_runtime_startup_options_t* options, coakka_v2_runtime_t** out) {
  assert(base != NULL && options == NULL);
  const coakka_v2_status_t status = result(NATIVE_ENGINE_CREATE);
  *out = status == COAKKA_V2_OK ? RUNTIME : NULL;
  return status;
}
coakka_v2_status_t coakka_v2_runtime_get_startup_config(
    coakka_v2_runtime_t* runtime, coakka_v2_runtime_startup_config_t* out) {
  assert(runtime == RUNTIME && out->struct_size == sizeof(*out));
  const coakka_v2_status_t status = result(NATIVE_ENGINE_STARTUP_FACTS);
  if (status == COAKKA_V2_OK) out->queue_capacity = 37; /* Issued fact, not a default. */
  return status;
}
coakka_v2_status_t coakka_v2_runtime_get_delivery_config(
    coakka_v2_runtime_t* runtime, coakka_v2_runtime_delivery_config_t* out) {
  assert(runtime == RUNTIME && out->struct_size == sizeof(*out));
  const coakka_v2_status_t status = result(NATIVE_ENGINE_DELIVERY_FACTS);
  if (status == COAKKA_V2_OK) out->slot_capacity = 19;
  return status;
}
coakka_v2_status_t coakka_v2_runtime_get_host_handles(
    coakka_v2_runtime_t* runtime, coakka_v2_host_handles_t* out) {
  assert(runtime == RUNTIME && out->struct_size == sizeof(*out));
  assert(out->flags == COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE);
  out->request_write_fd = 11; /* Close must safely retire even a partial span. */
  return result(NATIVE_ENGINE_HANDLES);
}
coakka_v2_status_t coakka_v2_runtime_apply_control_snapshot(
    coakka_v2_runtime_t* runtime, const coakka_v2_control_snapshot_t* routes) {
  assert(runtime == RUNTIME && routes != NULL);
  return result(NATIVE_ENGINE_ROUTES);
}
coakka_v2_status_t coakka_v2_runtime_start(coakka_v2_runtime_t* runtime) {
  assert(runtime == RUNTIME && !started);
  const coakka_v2_status_t status = result(NATIVE_ENGINE_START);
  if (status == COAKKA_V2_OK) started = 1;
  return status;
}
coakka_v2_status_t coakka_v2_ask_client_create_ex(
    coakka_v2_runtime_t* runtime, const coakka_v2_host_handles_t* handles,
    coakka_v2_ask_client_t** out) {
  assert(runtime == RUNTIME && handles != NULL && started);
  const coakka_v2_status_t status = result(NATIVE_ENGINE_CLIENT);
  *out = status == COAKKA_V2_OK ? CLIENT : NULL;
  return status;
}
coakka_v2_status_t coakka_v2_runtime_request_reader_create(
    coakka_v2_runtime_t* runtime, const coakka_v2_host_handles_t* handles,
    coakka_v2_request_reader_t** out) {
  assert(runtime == RUNTIME && handles != NULL && started);
  const coakka_v2_status_t status = result(NATIVE_ENGINE_READER);
  *out = status == COAKKA_V2_OK ? READER : NULL;
  return status;
}
void coakka_v2_request_reader_destroy(coakka_v2_request_reader_t* reader) {
  if (reader != NULL) { assert(reader == READER); retired_one('R'); }
}
coakka_v2_status_t coakka_v2_runtime_stop(coakka_v2_runtime_t* runtime) {
  assert(runtime == RUNTIME && started);
  retired_one('S');
  const coakka_v2_status_t status = result(NATIVE_ENGINE_STOP);
  if (status == COAKKA_V2_OK) started = 0;
  return status;
}
void coakka_v2_ask_client_destroy(coakka_v2_ask_client_t* client) {
  if (client != NULL) { assert(client == CLIENT && !started); retired_one('C'); }
}
void coakka_v2_host_handles_close(coakka_v2_host_handles_t* handles) {
  assert(!started);
  if (handles->request_write_fd >= 0) { retired_one('H'); handles->request_write_fd = -1; }
}
void coakka_v2_runtime_destroy(coakka_v2_runtime_t* runtime) {
  if (runtime != NULL) { assert(runtime == RUNTIME && !started); retired_one('D'); }
}

int main(void) {
  const coakka_v2_runtime_config_t base = {"owner-test", "main", 1, 0};
  const coakka_v2_control_snapshot_t routes = {1, NULL, 0};
  const char* expected[] = {"RSCHD", "", "D", "D", "HD", "HD", "HD", "SHD", "SCHD"};
  {
    native_engine_failure_t error = {NATIVE_ENGINE_STOP, COAKKA_V2_OK};
    assert(native_engine_owner_open(NULL, &base, &routes, &error) == COAKKA_V2_ERR_INVALID_ARG);
    assert(error.operation == NATIVE_ENGINE_NONE && error.status == COAKKA_V2_ERR_INVALID_ARG && calls == 0);
    assert(native_engine_owner_close(NULL, &error) == COAKKA_V2_ERR_INVALID_ARG);
    assert(error.operation == NATIVE_ENGINE_NONE && error.status == COAKKA_V2_ERR_INVALID_ARG && calls == 0);
  }
  for (unsigned failure = NATIVE_ENGINE_NONE; failure <= NATIVE_ENGINE_READER; ++failure) {
    native_engine_owner_t owner;
    native_engine_failure_t error;
    native_engine_owner_init(&owner);
    refusal = (native_engine_operation_t)failure;
    started = calls = 0; retired_count = 0; retired[0] = '\0';
    const coakka_v2_status_t status = native_engine_owner_open(&owner, &base, &routes, &error);
    if (failure == NATIVE_ENGINE_NONE) {
      assert(status == COAKKA_V2_OK && error.operation == NATIVE_ENGINE_NONE);
      assert(owner.facts.startup_checked && owner.facts.delivery_checked);
      assert(owner.facts.startup.queue_capacity == 37 && owner.facts.delivery.slot_capacity == 19);
      const int before = calls;
      assert(native_engine_owner_open(&owner, &base, &routes, &error) == COAKKA_V2_ERR_BAD_STATE);
      assert(error.operation == NATIVE_ENGINE_NONE && error.status == COAKKA_V2_ERR_BAD_STATE && calls == before);
    } else {
      assert(error.operation == refusal && status == error.status);
      assert(status == (failure == NATIVE_ENGINE_CREATE || failure == NATIVE_ENGINE_CLIENT ||
          failure == NATIVE_ENGINE_READER ? COAKKA_V2_ERR_NOMEM : COAKKA_V2_ERR_IO));
      if (failure <= NATIVE_ENGINE_STARTUP_FACTS) assert(!owner.facts.startup_checked);
      if (failure <= NATIVE_ENGINE_DELIVERY_FACTS) assert(!owner.facts.delivery_checked);
    }
    refusal = NATIVE_ENGINE_NONE;
    assert(native_engine_owner_close(&owner, &error) == COAKKA_V2_OK);
    assert(strcmp(retired, expected[failure]) == 0);
    assert(owner.runtime == NULL && owner.reader == NULL && owner.client == NULL && !owner.stop_required);
    const size_t before = retired_count;
    assert(native_engine_owner_close(&owner, &error) == COAKKA_V2_OK && retired_count == before);
  }
  {
    native_engine_owner_t owner;
    native_engine_failure_t error;
    native_engine_owner_init(&owner);
    refusal = NATIVE_ENGINE_NONE; retired_count = 0; retired[0] = '\0';
    assert(native_engine_owner_open(&owner, &base, &routes, &error) == COAKKA_V2_OK);
    refusal = NATIVE_ENGINE_STOP;
    assert(native_engine_owner_close(&owner, &error) == COAKKA_V2_ERR_IO);
    assert(error.operation == NATIVE_ENGINE_STOP && error.status == COAKKA_V2_ERR_IO);
    assert(owner.runtime == RUNTIME && owner.client == CLIENT && owner.reader == NULL && owner.stop_required);
    assert(owner.handles.request_write_fd == 11 && strcmp(retired, "RS") == 0);
    refusal = NATIVE_ENGINE_NONE;
    assert(native_engine_owner_close(&owner, &error) == COAKKA_V2_OK && strcmp(retired, "RSSCHD") == 0);
  }
  puts("PASS:11 fixture-owner boundary/success/refusal/stop-retry groups; exact facts, status and retirement order");
  return 0;
}
