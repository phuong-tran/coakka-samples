#include "test_support.h"
#include "test_tls_material.h"

#include <string.h>

typedef struct fixture_identity {
  const char *certificate_path;
  const char *key_path;
  const char *trust_path;
  const char *certificate;
  const char *key;
  const char *trust;
} fixture_identity_t;

static const fixture_identity_t identity_a = {
    "fixture-a-certificate.pem", "fixture-a-key.pem", "fixture-a-trust.pem",
    test_tls_certificate_a, test_tls_key_a, test_tls_root_a};
static const fixture_identity_t identity_b = {
    "fixture-b-certificate.pem", "fixture-b-key.pem", "fixture-b-trust.pem",
    test_tls_certificate_b, test_tls_key_b, test_tls_root_b};

static int write_material(const char *path, const char *material, int *created) {
  FILE *file = NULL;
  size_t size = strlen(material);
  int written;
#if defined(_WIN32)
  if (fopen_s(&file, path, "wbx") != 0) return 0;
#else
  file = fopen(path, "wbx");
#endif
  if (file == NULL) return 0;
  *created = 1;
  written = fwrite(material, 1U, size, file) == size;
  return fclose(file) == 0 && written;
}

static int monitor_off(coakka_http_host_service_t *service) {
  coakka_http_host_monitor_config_t monitor;
  coakka_http_host_monitor_config_init(&monitor);
  return coakka_http_host_monitor_config(service, &monitor) ==
             COAKKA_HTTP_HOST_OK &&
         monitor.policy.collection == COAKKA_HTTP_HOST_MONITOR_DISABLED;
}

static int effective_is(const coakka_http_host_tls_credentials_t *state,
                        coakka_http_host_security_t security,
                        uint64_t generation, const char *id, uint64_t floor,
                        int closed) {
  const size_t id_size = strlen(id);
  return state->struct_size == sizeof(*state) && state->security == security &&
         state->generation == generation &&
         state->drain_before_generation == floor &&
         state->closed == (closed != 0 ? 1U : 0U) && state->preparing == 0U &&
         id_size < sizeof(state->credential_id) &&
         memcmp(state->credential_id, id, id_size + 1U) == 0;
}

static void set_material(coakka_http_host_tls_reload_request_t *request,
                         const fixture_identity_t *identity) {
  request->certificate_chain_file = test_text(identity->certificate_path);
  request->private_key_file = test_text(identity->key_path);
  request->trust_roots_file = request->security == COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS
                                  ? test_text(identity->trust_path)
                                  : test_text(NULL);
}

/* Each call owns a fresh outbound service. Pool reuse cannot make an old
 * authenticated connection appear to have performed a new handshake. */
static int exchange(test_service_t *server, uint16_t port,
                    coakka_http_host_security_t security,
                    const fixture_identity_t *trusted_server,
                    const fixture_identity_t *client_identity,
                    const char *tls_peer,
                    coakka_http_host_outbound_reason_t expected_reason,
                    const coakka_http_host_tls_reload_request_t *drain) {
  test_service_t client;
  coakka_http_host_configuration_t config;
  coakka_http_host_outbound_trust_t trust;
  coakka_http_host_outbound_identity_t identity;
  coakka_http_host_outbound_endpoint_t endpoint;
  coakka_http_host_outbound_target_t target;
  coakka_http_host_outbound_request_t request;
  coakka_http_host_outbound_call_t call;
  coakka_http_host_outbound_event_t event;
  int event_live = 0;
  int ok = 1, cleanup_ok = 1;

  test_service_init(&client);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_outbound_trust_init(&trust);
  trust.generation = 1U;
  trust.ca_pem = test_text(trusted_server->trust);
  config.outbound_trusts = &trust;
  config.outbound_trust_count = 1U;
  coakka_http_host_outbound_endpoint_init(&endpoint);
  endpoint.node_id = test_text("tls-fixture");
  endpoint.connect_host = test_text("127.0.0.1");
  endpoint.connect_port = port;
  endpoint.http_authority = test_text("http.fixture.test");
  endpoint.security = security;
  endpoint.available = 1U;
  endpoint.tls_peer_identity = test_text(tls_peer);
  endpoint.tls_trust_generation = trust.generation;
  if (client_identity != NULL) {
    coakka_http_host_outbound_identity_init(&identity);
    identity.generation = 1U;
    identity.certificate_chain_pem = test_text(client_identity->certificate);
    identity.private_key_pem = test_text(client_identity->key);
    config.outbound_identities = &identity;
    config.outbound_identity_count = 1U;
    endpoint.tls_client_identity_generation = identity.generation;
  }
  coakka_http_host_outbound_target_init(&target);
  target.name = test_text("fixture.tls");
  target.generation = 1U;
  target.endpoints = &endpoint;
  target.endpoint_count = 1U;
  config.outbound_targets = &target;
  config.outbound_target_count = 1U;
  TEST_CHECK(test_service_open(&client, &config, NULL, 0U));

  coakka_http_host_outbound_request_init(&request);
  request.logical_target = target.name;
  request.method = test_text("GET");
  request.target = test_text("/tls");
  request.timeout_ms = 3000U;
  TEST_CHECK(coakka_http_host_submit_outbound(client.service, &request, &call) ==
             COAKKA_HTTP_HOST_OK);
  if (expected_reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE) {
    TEST_CHECK(test_next_request(server));
    TEST_CHECK(test_bytes_equal(coakka_http_host_request_target(&server->event), "/tls"));
    TEST_CHECK(
        test_bytes_equal(coakka_http_host_request_authority(&server->event),
                         "http.fixture.test"));
    if (drain != NULL) {
      coakka_http_host_tls_reload_outcome_t outcome;
      coakka_http_host_tls_reload_outcome_init(&outcome);
      /* The borrowed request proves this exchange was admitted before reload.
       * Its response must still complete on the retired TLS connection. */
      TEST_CHECK(coakka_http_host_tls_reload(server->service, drain, &outcome) ==
                 COAKKA_HTTP_HOST_OK);
      TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_APPLIED);
      TEST_CHECK(outcome.changed == 1U);
      TEST_CHECK(outcome.effective.generation == drain->credential_generation);
      TEST_CHECK(outcome.effective.drain_before_generation ==
                 drain->credential_generation);
    }
    TEST_CHECK(test_respond(server, 200U, "authenticated fixture"));
    TEST_CHECK(test_release(server));
  }

  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, TEST_IO_TIMEOUT_MS,
                                            &event) == COAKKA_HTTP_HOST_OK);
  event_live = 1;
  TEST_CHECK(event.call.slot == call.slot && event.call.generation == call.generation);
  TEST_CHECK(event.reason == expected_reason);
  if (expected_reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE) {
    TEST_CHECK(event.response_status == 200U);
    TEST_CHECK(test_bytes_equal(event.response_body, "authenticated fixture"));
  } else if (expected_reason == COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_REJECTED) {
    TEST_CHECK(event.phase == COAKKA_HTTP_HOST_OUTBOUND_PHASE_PROVIDER_ADOPTION);
    TEST_CHECK(event.certainty == COAKKA_HTTP_HOST_OUTBOUND_NO_REQUEST_BYTES_SENT);
  } else {
    TEST_CHECK(event.phase == COAKKA_HTTP_HOST_OUTBOUND_PHASE_TRANSPORT);
    if (strcmp(tls_peer, "127.0.0.2") == 0) {
      TEST_CHECK(event.certainty ==
                 COAKKA_HTTP_HOST_OUTBOUND_NO_REQUEST_BYTES_SENT);
    }
  }

cleanup:
  if (event_live && coakka_http_host_release_outbound(client.service, &event) !=
                        COAKKA_HTTP_HOST_OK) cleanup_ok = 0;
  if (!test_service_close(&client)) cleanup_ok = 0;
  return ok && cleanup_ok;
}

static int secure_reload(coakka_http_host_security_t security) {
  test_service_t owner;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_tls_credentials_t effective;
  coakka_http_host_tls_reload_request_t request;
  coakka_http_host_tls_reload_outcome_t outcome;
  const int mutual = security == COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS;
  uint16_t port;
  int ok = 1, cleanup_ok = 1;

  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  config.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
  config.security = security;
  config.credential_generation = 1U;
  config.credential_id = test_text("identity-a");
  config.certificate_chain_file = test_text(identity_a.certificate_path);
  config.private_key_file = test_text(identity_a.key_path);
  if (mutual) config.trust_roots_file = test_text(identity_a.trust_path);
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/tls");
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(monitor_off(owner.service));

  coakka_http_host_tls_credentials_init(&effective);
  TEST_CHECK(coakka_http_host_tls_credentials(owner.service, &effective) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective_is(&effective, security, 1U, "identity-a", 0U, 0));
  TEST_CHECK(exchange(&owner, port, security, &identity_a,
                      mutual ? &identity_a : NULL, "127.0.0.1",
                      COAKKA_HTTP_HOST_OUTBOUND_RESPONSE, NULL));
  TEST_CHECK(exchange(&owner, port, security, &identity_a,
                      mutual ? &identity_a : NULL, "127.0.0.2",
                      COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_FAILURE, NULL));
  if (!mutual) {
    // These identities fail syntax validation before reaching the TLS peer.
    TEST_CHECK(exchange(&owner, port, security, &identity_a, NULL,
                        "0x7f000001",
                        COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_REJECTED, NULL));
    TEST_CHECK(exchange(&owner, port, security, &identity_a, NULL,
                        "service..fixture.test",
                        COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_REJECTED, NULL));
    TEST_CHECK(exchange(&owner, port, security, &identity_a, NULL,
                        "service-.fixture.test",
                        COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_REJECTED, NULL));
  }

  coakka_http_host_tls_reload_request_init(&request);
  coakka_http_host_tls_reload_outcome_init(&outcome);
  request.security = security;
  request.mode = COAKKA_HTTP_HOST_TLS_RELOAD_GRACEFUL;
  request.expected_generation = 7U;
  request.credential_generation = 2U;
  request.credential_id = test_text("identity-b");
  set_material(&request, &identity_b);
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_GENERATION_CONFLICT);
  TEST_CHECK(outcome.changed == 0U &&
             effective_is(&outcome.effective, security, 1U, "identity-a", 0U, 0));

  request.expected_generation = 1U;
  request.security = mutual ? COAKKA_HTTP_HOST_SECURITY_TLS
                            : COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS;
  request.trust_roots_file = mutual ? test_text(NULL)
                                    : test_text(identity_b.trust_path);
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_SECURITY_CHANGE);
  TEST_CHECK(outcome.changed == 0U &&
             effective_is(&outcome.effective, security, 1U, "identity-a", 0U, 0));

  request.security = security;
  set_material(&request, &identity_b);
  request.credential_generation = 1U;
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_STALE_GENERATION);
  TEST_CHECK(outcome.changed == 0U &&
             effective_is(&outcome.effective, security, 1U, "identity-a", 0U, 0));

  request.credential_generation = 2U;
  request.mode = COAKKA_HTTP_HOST_TLS_RELOAD_DRAIN_EXISTING;
  request.private_key_file = test_text(identity_a.key_path);
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_PREPARATION_REJECTED);
  TEST_CHECK(outcome.changed == 0U &&
             effective_is(&outcome.effective, security, 1U, "identity-a", 0U, 0));
  TEST_CHECK(monitor_off(owner.service));
  TEST_CHECK(exchange(&owner, port, security, &identity_a,
                      mutual ? &identity_a : NULL, "localhost", 1, NULL));

  request.mode = COAKKA_HTTP_HOST_TLS_RELOAD_GRACEFUL;
  set_material(&request, &identity_b);
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_APPLIED);
  TEST_CHECK(outcome.changed == 1U &&
             effective_is(&outcome.effective, security, 2U, "identity-b", 0U, 0));
  TEST_CHECK(exchange(&owner, port, security, &identity_a,
                      mutual ? &identity_b : NULL, "localhost",
                      COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_FAILURE, NULL));
  if (mutual) {
    TEST_CHECK(exchange(&owner, port, security, &identity_b, &identity_a,
                        "localhost", COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_FAILURE,
                        NULL));
  }
  TEST_CHECK(exchange(&owner, port, security, &identity_b,
                      mutual ? &identity_b : NULL, "localhost",
                      COAKKA_HTTP_HOST_OUTBOUND_RESPONSE, NULL));

  request.expected_generation = 2U;
  request.credential_generation = 3U;
  request.credential_id = test_text("identity-b-drain");
  request.mode = COAKKA_HTTP_HOST_TLS_RELOAD_DRAIN_EXISTING;
  TEST_CHECK(exchange(&owner, port, security, &identity_b,
                      mutual ? &identity_b : NULL, "localhost",
                      COAKKA_HTTP_HOST_OUTBOUND_RESPONSE, &request));
  TEST_CHECK(coakka_http_host_tls_credentials(owner.service, &effective) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective_is(&effective, security, 3U, "identity-b-drain", 3U, 0));
  TEST_CHECK(effective.reload_mode == COAKKA_HTTP_HOST_TLS_RELOAD_DRAIN_EXISTING);
  TEST_CHECK(monitor_off(owner.service));

  TEST_CHECK(coakka_http_host_service_stop(owner.service) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(coakka_http_host_tls_credentials(owner.service, &effective) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective_is(&effective, security, 3U, "identity-b-drain", 3U, 1));
  request.expected_generation = 3U;
  request.credential_generation = 4U;
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.reason == COAKKA_HTTP_HOST_TLS_RELOAD_CLOSED);
  TEST_CHECK(outcome.changed == 0U &&
             effective_is(&outcome.effective, security, 3U, "identity-b-drain", 3U, 1));
  TEST_CHECK(monitor_off(owner.service));

cleanup:
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return ok && cleanup_ok;
}

static int plaintext_has_no_credential_owner(void) {
  test_service_t owner;
  coakka_http_host_configuration_t config;
  coakka_http_host_tls_credentials_t credentials;
  coakka_http_host_tls_reload_request_t request;
  coakka_http_host_tls_reload_outcome_t outcome;
  int ok = 1, cleanup_ok = 1;

  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  TEST_CHECK(test_service_open(&owner, &config, NULL, 0U));
  coakka_http_host_tls_credentials_init(&credentials);
  TEST_CHECK(coakka_http_host_tls_credentials(owner.service, &credentials) ==
             COAKKA_HTTP_HOST_UNSUPPORTED);
  coakka_http_host_tls_reload_request_init(&request);
  coakka_http_host_tls_reload_outcome_init(&outcome);
  request.security = COAKKA_HTTP_HOST_SECURITY_TLS;
  request.credential_id = test_text("identity-a");
  request.expected_generation = 1U;
  request.credential_generation = 2U;
  set_material(&request, &identity_a);
  TEST_CHECK(coakka_http_host_tls_reload(owner.service, &request, &outcome) ==
             COAKKA_HTTP_HOST_UNSUPPORTED);
  TEST_CHECK(monitor_off(owner.service));

cleanup:
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return ok && cleanup_ok;
}

int main(void) {
  const char *const paths[] = {
      identity_a.certificate_path, identity_a.key_path, identity_a.trust_path,
      identity_b.certificate_path, identity_b.key_path, identity_b.trust_path};
  const char *const materials[] = {
      identity_a.certificate, identity_a.key, identity_a.trust,
      identity_b.certificate, identity_b.key, identity_b.trust};
  int created[6] = {0};
  size_t index;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  for (index = 0U; index < sizeof(created) / sizeof(created[0]); ++index) {
    TEST_CHECK(write_material(paths[index], materials[index], &created[index]));
  }
  TEST_CHECK(plaintext_has_no_credential_owner());
  TEST_CHECK(secure_reload(COAKKA_HTTP_HOST_SECURITY_TLS));
  TEST_CHECK(secure_reload(COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS));

cleanup:
  for (index = 0U; index < sizeof(created) / sizeof(created[0]); ++index) {
    if (created[index] && remove(paths[index]) != 0) cleanup_ok = 0;
  }
  return test_finish("tls_reload_and_trust", ok, cleanup_ok);
}
