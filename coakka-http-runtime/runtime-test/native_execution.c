/* Installed consumer of Core-owned construction and lifecycle facts.
 * No CPU probe, private ABI, timing guess or connector default table is used.
 * Invalid updates must preserve the complete previous construction intent. */
#include <coakka/http/http.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

_Static_assert(COAKKA_HTTP_ABI_VERSION == 12U, "Suite requires Core ABI12");
_Static_assert(COAKKA_HTTP_RUNTIME_INFO_VERSION == 5U, "Suite requires runtime-info5");

#define REQUIRE(condition) do { \
  if (!(condition)) { \
    fprintf(stderr, "execution contract line %d: %s\n", __LINE__, #condition); \
    exit(EXIT_FAILURE); \
  } \
} while (0)
#define OK(call) REQUIRE((call).code == COAKKA_HTTP_RESULT_OK)

static void observe(coakka_http_core_t *core, uint32_t mode, uint32_t count,
                    uint32_t reason, uint32_t active) {
  coakka_http_runtime_info_t info;
  coakka_http_runtime_info_init(&info);
  OK(coakka_http_core_get_runtime_info(core, &info));
  REQUIRE(info.info_version == COAKKA_HTTP_RUNTIME_INFO_VERSION);
  REQUIRE(info.abi_version == COAKKA_HTTP_ABI_VERSION);
  REQUIRE(info.execution.observed != 0U);
  REQUIRE(info.selection.requested_mode == mode);
  REQUIRE(info.selection.requested_count == count);
  REQUIRE(info.selection.selected_count == 1U);
  REQUIRE(info.selection.reason == reason);
  REQUIRE(info.execution.configured_event_loops == 1U);
  REQUIRE(info.execution.active_event_loops == active);
  REQUIRE(info.execution.failed_event_loops == 0U);
}

static void lifecycle(coakka_http_configuration_t *config, uint32_t mode,
                      uint32_t count, uint32_t reason) {
  coakka_http_core_t *core = NULL;
  OK(coakka_http_core_create(config, &core));
  REQUIRE(core != NULL);
  observe(core, mode, count, reason, 0U);
  OK(coakka_http_core_start(core));
  observe(core, mode, count, reason, 1U);
  OK(coakka_http_core_stop(core));
  observe(core, mode, count, reason, 0U);
  OK(coakka_http_core_destroy(&core));
  REQUIRE(core == NULL);
}

int main(void) {
  coakka_http_configuration_t *config = NULL;
  coakka_http_execution_options_t options = {
      sizeof(options), COAKKA_HTTP_EXECUTION_PREFER_SUPPORTED, 2U, 0U};
  coakka_http_runtime_info_t global;
  coakka_http_runtime_info_init(&global);
  OK(coakka_http_runtime_get_info(&global));
  REQUIRE(global.execution.observed == 0U);
  OK(coakka_http_configuration_create(&config));
  REQUIRE(coakka_http_configuration_set_execution(NULL, &options).code ==
          COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  lifecycle(config, COAKKA_HTTP_EXECUTION_DEFAULT, 0U,
            COAKKA_HTTP_EXECUTION_DEFAULTED);
  OK(coakka_http_configuration_set_execution(config, &options));
  /* Each malformed replacement must leave the entire prior intent intact. */
  for (unsigned variant = 0U; variant < 6U; ++variant) {
    coakka_http_execution_options_t invalid = options;
    if (variant == 0U) invalid.struct_size -= 1U;
    if (variant == 1U) invalid.mode = COAKKA_HTTP_EXECUTION_DEFAULT;
    if (variant == 2U) invalid.mode = UINT32_MAX;
    if (variant == 3U) invalid.event_loop_count = 0U;
    if (variant == 4U) invalid.event_loop_count = 3U;
    if (variant == 5U) invalid.reserved = 1U;
    REQUIRE(coakka_http_configuration_set_execution(config, &invalid).code ==
            COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
    lifecycle(config, COAKKA_HTTP_EXECUTION_PREFER_SUPPORTED, 2U,
              COAKKA_HTTP_EXECUTION_CPU_UNOBSERVED);
  }
  /* Configuration owns a synchronous copy, not this caller's poisoned bytes. */
  memset(&options, 0xa5, sizeof(options));
  lifecycle(config, COAKKA_HTTP_EXECUTION_PREFER_SUPPORTED, 2U,
            COAKKA_HTTP_EXECUTION_CPU_UNOBSERVED);
  options.struct_size = sizeof(options);
  options.mode = COAKKA_HTTP_EXECUTION_PREFER_SUPPORTED;
  options.event_loop_count = 1U;
  options.reserved = 0U;
  OK(coakka_http_configuration_set_execution(config, &options));
  lifecycle(config, options.mode, 1U, COAKKA_HTTP_EXECUTION_PREFERRED_SELECTED);
  options.mode = COAKKA_HTTP_EXECUTION_EXACT;
  OK(coakka_http_configuration_set_execution(config, &options));
  lifecycle(config, options.mode, 1U, COAKKA_HTTP_EXECUTION_EXACT_SELECTED);
  OK(coakka_http_configuration_set_execution(config, NULL));
  lifecycle(config, COAKKA_HTTP_EXECUTION_DEFAULT, 0U,
            COAKKA_HTTP_EXECUTION_DEFAULTED);
  OK(coakka_http_configuration_destroy(&config));
  REQUIRE(config == NULL);
  puts("coakka_http_runtime_execution_test=pass");
  return EXIT_SUCCESS;
}
