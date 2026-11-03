#include "test_support.h"

#include <string.h>

int main(void) {
  test_service_t owner;
  coakka_http_host_configuration_t config;
  coakka_http_host_limits_t effective, prior, sentinel;
  coakka_http_host_runtime_info_t runtime;
  coakka_http_host_service_info_t info;
  uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  TEST_CHECK(coakka_http_host_abi_version() == COAKKA_HTTP_HOST_ABI_VERSION);
  coakka_http_host_runtime_info_init(&runtime);
  TEST_CHECK(coakka_http_host_runtime_inspect(&runtime) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(runtime.abi_version == COAKKA_HTTP_HOST_ABI_VERSION);
  TEST_CHECK(runtime.features == coakka_http_host_features());
  coakka_http_host_configuration_init(&config);
  TEST_CHECK(config.limits.max_connections == 0U);
  TEST_CHECK(coakka_http_host_service_create(NULL, NULL, 0U, &owner.service,
                                             &failed) == COAKKA_HTTP_HOST_INVALID_ARGUMENT);
  TEST_CHECK(owner.service == NULL);
  TEST_CHECK(test_service_open(&owner, &config, NULL, 0U));
  coakka_http_host_limits_init(&effective);
  TEST_CHECK(coakka_http_host_service_limits(owner.service, &effective) == COAKKA_HTTP_HOST_OK);
  /* Relationships, not a copied table of Core defaults. Initial zeros express
   * absence; only this accepted snapshot is evidence of effective settings. */
  TEST_CHECK(effective.max_connections > 0U);
  TEST_CHECK(effective.max_active_requests > 0U);
  TEST_CHECK(effective.max_active_requests <= effective.max_connections);
  TEST_CHECK(effective.request_queue_capacity <= effective.max_active_requests);
  TEST_CHECK(effective.request_notification_profile != COAKKA_HTTP_HOST_NOTIFICATION_AUTO);
  TEST_CHECK(effective.terminal_notification_profile != COAKKA_HTTP_HOST_NOTIFICATION_AUTO);
  prior = effective;
  config.limits.max_connections = 1U;
  coakka_http_host_limits_init(&effective);
  TEST_CHECK(coakka_http_host_service_limits(owner.service, &effective) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective.max_connections == prior.max_connections);
  TEST_CHECK(effective.max_active_requests == prior.max_active_requests);
  TEST_CHECK(effective.request_notification_profile == prior.request_notification_profile);
  TEST_CHECK(effective.terminal_notification_profile == prior.terminal_notification_profile);
  memset(&effective, 0xa5, sizeof(effective));
  sentinel = effective;
  TEST_CHECK(coakka_http_host_service_limits(owner.service, &effective) ==
             COAKKA_HTTP_HOST_INVALID_ARGUMENT);
  TEST_CHECK(memcmp(&effective, &sentinel, sizeof(sentinel)) == 0);
  coakka_http_host_service_info_init(&info);
  TEST_CHECK(coakka_http_host_service_info(owner.service, &info) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(info.service_started && info.execution.observed);
  TEST_CHECK(info.execution.active_event_loops > 0U);
  TEST_CHECK(info.execution.active_event_loops <= info.execution.configured_event_loops);
  /* A legal empty table has no application work. A zero wait is a caller
   * observation timeout, not evidence that Core stopped or a request failed. */
  TEST_CHECK(test_take(&owner, 0U) == COAKKA_HTTP_HOST_TIMEOUT);
  TEST_CHECK(!owner.lease_live);
  TEST_CHECK(coakka_http_host_service_stop(owner.service) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_limits_init(&effective);
  TEST_CHECK(coakka_http_host_service_limits(owner.service, &effective) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective.max_connections == prior.max_connections);
  TEST_CHECK(effective.max_active_requests == prior.max_active_requests);
  TEST_CHECK(effective.request_notification_profile == prior.request_notification_profile);
  TEST_CHECK(effective.terminal_notification_profile == prior.terminal_notification_profile);
  TEST_CHECK(test_service_close(&owner));
  TEST_CHECK(test_service_close(&owner));
cleanup:
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("configuration_lifecycle", ok, cleanup_ok);
}
