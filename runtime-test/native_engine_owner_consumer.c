/* Public-SDK black box for the preparatory fixture, not a throughput test. */
#include "native_engine_owner.h"
#include "coakka/v2/transport.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

#ifdef NDEBUG
#error "Native engine consumer controls require active assertions"
#endif

int main(void) {
  coakka_v2_runtime_info_t info = {0};
  info.struct_size = sizeof(info);
  assert(coakka_v2_runtime_get_info(&info) == COAKKA_V2_OK);
  assert(info.abi_version == 1 && strcmp(info.git_commit,
      "f6b61768f60a08d81b1dc06625a211ffca8e89e2") == 0);
  for (unsigned cycle = 0; cycle < 16; ++cycle) {
    const coakka_v2_runtime_config_t base = {"native-engine-evidence", "main", 1, 0};
    const coakka_v2_endpoint_t endpoint = {"127.0.0.1", 0, 1, COAKKA_V2_ENDPOINT_FLAG_LOCAL};
    const coakka_v2_route_t route = {"evidence.echo", COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER, NULL, 0, &endpoint, 1};
    const coakka_v2_control_snapshot_t routes = {1, &route, 1};
    native_engine_owner_t owner;
    native_engine_failure_t failure;
    native_engine_owner_init(&owner);
    assert(native_engine_owner_open(&owner, &base, &routes, &failure) == COAKKA_V2_OK);
    assert(owner.facts.startup_checked && owner.facts.delivery_checked);
    assert(owner.facts.startup.execution.requested_policy == COAKKA_V2_STARTUP_CPU_AUTO);
    assert(owner.facts.startup.connection.mode == COAKKA_V2_TCP_CONNECTION_MULTIPLEXED);
    assert(owner.facts.delivery.compiled == 1);
    assert(owner.facts.delivery.request_mode == COAKKA_V2_STARTUP_DELIVERY_OWNED_QUEUE);
    assert(owner.facts.delivery.response_mode == COAKKA_V2_STARTUP_DELIVERY_OWNED_QUEUE);
    {
      uint8_t payload[4096];
      uint8_t *wire = NULL, *reply = NULL, *result = NULL, *copy = NULL;
      size_t wire_size = 0, reply_size = 0, result_size = 0, copy_size = 0;
      coakka_v2_ask_ticket_t* ticket = NULL;
      coakka_v2_owned_frame_t* delivered = NULL;
      coakka_v2_client_raw_request_spec_t request = {0};
      coakka_v2_client_raw_reply_spec_t response = {0};
      uint32_t kind = 0;
      const size_t size = cycle % 2 == 0 ? 0 : sizeof(payload);
      memset(payload, 0xa5, sizeof(payload));
      request.struct_size = sizeof(request);
      request.message_id = "native-engine-roundtrip";
      request.source = "evidence.client";
      request.target = "evidence.echo";
      request.reply_to = "evidence.client/replies";
      request.payload = payload;
      request.payload_len = size;
      request.timeout_ms = 1000;
      assert(coakka_v2_client_build_raw_request(&request, &wire, &wire_size) == COAKKA_V2_OK);
      assert(coakka_v2_ask_client_begin(owner.client, wire, wire_size, &ticket) == COAKKA_V2_OK);
      coakka_v2_client_bytes_release(wire);
      assert(coakka_v2_request_reader_read(owner.reader, 1000, &delivered) == COAKKA_V2_OK);
      response.struct_size = sizeof(response);
      response.request_buf = coakka_v2_owned_frame_data(delivered);
      response.request_len = coakka_v2_owned_frame_size(delivered);
      response.source = "evidence.echo";
      response.payload = payload;
      response.payload_len = size;
      assert(coakka_v2_client_build_raw_reply(&response, &reply, &reply_size) == COAKKA_V2_OK);
      coakka_v2_owned_frame_release(delivered);
      assert(coakka_v2_runtime_submit_envelope(owner.runtime, reply, reply_size) == COAKKA_V2_OK);
      coakka_v2_client_bytes_release(reply);
      assert(coakka_v2_ask_ticket_await(ticket, 1000, &kind, &result, &result_size) == COAKKA_V2_OK);
      assert(kind == COAKKA_V2_CLIENT_RESULT_RESPONSE);
      assert(coakka_v2_client_copy_envelope_payload(result, result_size,
          COAKKA_V2_CLIENT_ENVELOPE_RESPONSE, &copy, &copy_size) == COAKKA_V2_OK);
      assert(copy_size == size && (size == 0 || memcmp(copy, payload, size) == 0));
      coakka_v2_client_bytes_release(copy);
      coakka_v2_client_bytes_release(result);
      coakka_v2_ask_ticket_destroy(ticket);
    }
    assert(native_engine_owner_close(&owner, &failure) == COAKKA_V2_OK);
    assert(owner.runtime == NULL && owner.client == NULL && owner.reader == NULL);
    assert(native_engine_owner_close(&owner, &failure) == COAKKA_V2_OK);
  }
  puts("{\"gate\":\"native-engine-fixture-owner\",\"status\":\"pass\",\"cycles\":16,\"responses\":16,\"requestMode\":2,\"responseMode\":2,\"core\":\"f6b61768f60a08d81b1dc06625a211ffca8e89e2\",\"benchmark\":false}");
  return 0;
}
