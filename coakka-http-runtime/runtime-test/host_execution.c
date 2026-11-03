#include "test_support.h"

/* Inspect Core facts at the lifecycle boundary; affinity, loop selection and
 * loop activity are separate observations. No OS probe or copied default table. */
static int lifecycle(const coakka_http_execution_options_t *execution) {
  test_service_t owner;
  coakka_http_host_configuration_t config;
  coakka_http_host_service_info_t before, running, stopped;
  coakka_http_host_issue_t issue;
  uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  int ok = 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  config.execution = execution;
  TEST_CHECK(coakka_http_host_service_create(&config, NULL, 0U, &owner.service,
                                             &failed) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_service_info_init(&before);
  TEST_CHECK(coakka_http_host_service_info(owner.service, &before) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(before.execution.observed && before.execution.active_event_loops == 0U);
  TEST_CHECK(before.execution.configured_event_loops > 0U);
  TEST_CHECK(before.selection.selected_count == before.execution.configured_event_loops);
  TEST_CHECK(before.selection.requested_mode ==
             (execution == NULL ? COAKKA_HTTP_EXECUTION_DEFAULT : execution->mode));
  TEST_CHECK(before.selection.requested_count ==
             (execution == NULL ? 0U : execution->event_loop_count));
  coakka_http_host_issue_init(&issue);
  TEST_CHECK(coakka_http_host_service_start(owner.service, &issue) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_service_info_init(&running);
  TEST_CHECK(coakka_http_host_service_info(owner.service, &running) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(running.execution.active_event_loops == before.execution.configured_event_loops);
  TEST_CHECK(running.execution.failed_event_loops == 0U);
  TEST_CHECK(coakka_http_host_service_stop(owner.service) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_service_info_init(&stopped);
  TEST_CHECK(coakka_http_host_service_info(owner.service, &stopped) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(stopped.execution.active_event_loops == 0U);
  TEST_CHECK(stopped.execution.configured_event_loops == before.execution.configured_event_loops);
  TEST_CHECK(stopped.selection.selected_count == before.selection.selected_count);
  TEST_CHECK(stopped.selection.reason == before.selection.reason);
cleanup:
  return test_service_close(&owner) && ok;
}

int main(void) {
  coakka_http_execution_options_t options = {
      sizeof(options), COAKKA_HTTP_EXECUTION_PREFER_SUPPORTED, 2U, 0U};
  coakka_http_host_configuration_t config;
  coakka_http_host_service_t *rejected = NULL;
  coakka_http_host_cpu_info_t cpu = {0};
  uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  unsigned variant;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  cpu.struct_size = sizeof(cpu);
  TEST_CHECK(test_cpu_info(&cpu) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(cpu.requested_policy == COAKKA_HTTP_HOST_CPU_AUTO);
  if (cpu.placement == COAKKA_HTTP_HOST_CPU_PLACEMENT_UNSUPPORTED) {
    TEST_CHECK(cpu.available_cpu_count == 0U && cpu.selected_cpu_count == 0U);
    TEST_CHECK(cpu.selected_cpu_ids[0] == UINT32_MAX && cpu.selected_cpu_ids[1] == UINT32_MAX);
  } else {
    TEST_CHECK(cpu.selected_cpu_count > 0U && cpu.selected_cpu_count <= 2U);
    TEST_CHECK(cpu.selected_cpu_count <= cpu.available_cpu_count);
    TEST_CHECK(cpu.selected_cpu_ids[0] != UINT32_MAX);
  }
  TEST_CHECK(lifecycle(NULL));
  TEST_CHECK(lifecycle(&options));
  coakka_http_host_configuration_init(&config);
  for (variant = 0U; variant < 6U; ++variant) {
    coakka_http_execution_options_t invalid = options;
    if (variant == 0U) --invalid.struct_size;
    if (variant == 1U) invalid.mode = COAKKA_HTTP_EXECUTION_DEFAULT;
    if (variant == 2U) invalid.mode = UINT32_MAX;
    if (variant == 3U) invalid.event_loop_count = 0U;
    if (variant == 4U) invalid.event_loop_count = 3U;
    if (variant == 5U) invalid.reserved = 1U;
    config.execution = &invalid;
    TEST_CHECK(coakka_http_host_service_create(&config, NULL, 0U, &rejected,
                                               &failed) == COAKKA_HTTP_HOST_INVALID_ARGUMENT);
    TEST_CHECK(rejected == NULL);
  }
  options.mode = COAKKA_HTTP_EXECUTION_EXACT;
  options.event_loop_count = 1U;
  TEST_CHECK(lifecycle(&options));
cleanup:
  if (coakka_http_host_service_destroy(&rejected) != COAKKA_HTTP_HOST_OK || rejected != NULL)
    cleanup_ok = 0;
  return test_finish("execution_truth", ok, cleanup_ok);
}
