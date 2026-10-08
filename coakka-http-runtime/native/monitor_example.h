/* Shared C-first control-plane recipe, not a monitor implementation.
 * Core supplies generations, resource reservations and accepted policy. The
 * application submits intent and verifies typed acceptance/refusal on startup.
 */
#ifndef COAKKA_SAMPLE_MONITOR_EXAMPLE_H
#define COAKKA_SAMPLE_MONITOR_EXAMPLE_H
#include <coakka/http/http.h>
#include <stdio.h>

static inline int sample_same_monitor_policy(const coakka_http_monitor_policy_view_t *a,
                                              const coakka_http_monitor_policy_view_t *b) {
  return a->collection == b->collection && a->notification == b->notification &&
         a->latency == b->latency && a->detail == b->detail &&
         a->active_event_capacity == b->active_event_capacity &&
         a->active_detail_bytes == b->active_detail_bytes &&
         a->aggregate_categories == b->aggregate_categories && a->event_categories == b->event_categories;
}

static inline int sample_monitor_reload(coakka_http_server_t *server) {
  coakka_http_monitor_config_view_t initial, current;
  coakka_http_monitor_policy_view_t desired, over_budget;
  coakka_http_monitor_apply_outcome_t outcome;
  uint64_t accepted_generation;
  coakka_http_monitor_config_view_init(&initial);
  if (coakka_http_server_monitor_config(server, &initial).code != COAKKA_HTTP_RESULT_OK) return 0;
  coakka_http_monitor_policy_view_init(&desired);
  desired.collection = COAKKA_HTTP_MONITOR_AGGREGATES;
  desired.aggregate_categories = initial.policy.aggregate_categories;
  coakka_http_monitor_apply_outcome_init(&outcome);
  if (coakka_http_server_monitor_apply(server, initial.generation, &desired, &outcome).code != COAKKA_HTTP_RESULT_OK ||
      outcome.reason != COAKKA_HTTP_MONITOR_APPLY_REASON_APPLIED || !outcome.changed ||
      !sample_same_monitor_policy(&outcome.effective.policy, &desired)) return 0;
  accepted_generation = outcome.effective.generation;

  /* Reusing a stale generation must not overwrite the accepted policy. */
  coakka_http_monitor_apply_outcome_init(&outcome);
  if (coakka_http_server_monitor_apply(server, initial.generation, &initial.policy, &outcome).code != COAKKA_HTTP_RESULT_OK ||
      outcome.reason != COAKKA_HTTP_MONITOR_APPLY_REASON_GENERATION_CONFLICT || outcome.changed ||
      outcome.effective.generation != accepted_generation ||
      !sample_same_monitor_policy(&outcome.effective.policy, &desired)) return 0;

  /* Reservations are immutable after startup. Ask for one event beyond Core's
   * reported bound and require a typed refusal, never a silent clamp/fallback.
   */
  if (initial.reserved_event_capacity == UINT32_MAX) return 0;
  over_budget = initial.policy;
  over_budget.active_event_capacity = initial.reserved_event_capacity + 1;
  coakka_http_monitor_apply_outcome_init(&outcome);
  if (coakka_http_server_monitor_apply(server, accepted_generation, &over_budget, &outcome).code != COAKKA_HTTP_RESULT_OK ||
      outcome.reason != COAKKA_HTTP_MONITOR_APPLY_REASON_RESOURCE_RESERVATION_EXCEEDED || outcome.changed ||
      outcome.effective.generation != accepted_generation ||
      !sample_same_monitor_policy(&outcome.effective.policy, &desired)) return 0;

  coakka_http_monitor_apply_outcome_init(&outcome);
  if (coakka_http_server_monitor_apply(server, accepted_generation,
                                     &initial.policy, &outcome).code != COAKKA_HTTP_RESULT_OK ||
      outcome.reason != COAKKA_HTTP_MONITOR_APPLY_REASON_APPLIED || !outcome.changed) return 0;
  coakka_http_monitor_config_view_init(&current);
  if (coakka_http_server_monitor_config(server, &current).code != COAKKA_HTTP_RESULT_OK ||
      current.generation != outcome.effective.generation ||
      !sample_same_monitor_policy(&current.policy, &initial.policy)) return 0;
  puts("native-monitor-reload=pass");
  return 1;
}
#endif
