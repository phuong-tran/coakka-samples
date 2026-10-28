#include "coakka/logger/core.h"
#include "coakka/logger/utils.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define COAKKA_SAMPLE_EXPORT __attribute__((visibility("default")))

static int require_ok(coakka_logger_status_t status, const char* step) {
  if (status == COAKKA_LOGGER_STATUS_OK) {
    return 0;
  }
  fprintf(stderr, "%s failed: %s\n", step, coakka_logger_status_name(status));
  return 1;
}

COAKKA_SAMPLE_EXPORT int coakka_mojo_logger_basic(int ignored) {
  (void)ignored;

  coakka_logger_core_info_t info;
  memset(&info, 0, sizeof(info));
  info.struct_size = sizeof(info);
  if (require_ok(coakka_logger_core_get_info(&info), "get_info")) {
    return 1;
  }
  printf("coakka_logger_info abi=%u version=%s git=%s language=mojo\n",
         info.abi_version,
         info.runtime_version,
         info.git_commit);

  coakka_logger_core_config_t config = coakka_logger_core_default_config();
  config.system_name = "mojo-basic-logger";
  config.queue_capacity = 8;
  config.category_capacity = 96;
  config.message_capacity = 160;

  coakka_logger_core_handle_t* handle = NULL;
  if (require_ok(coakka_logger_core_create(&config, &handle), "create")) {
    return 1;
  }
  if (require_ok(coakka_logger_core_start(handle), "start")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }

  uint64_t sequence = 0;
  if (require_ok(coakka_logger_core_log_info(handle,
                                             "samples.logger.mojo.basic",
                                             "{\"event\":\"hello\",\"language\":\"mojo\"}",
                                             &sequence),
                 "log_info")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }

  char category[96];
  char message[160];
  memset(category, 0, sizeof(category));
  memset(message, 0, sizeof(message));

  coakka_logger_core_record_buffer_t record;
  memset(&record, 0, sizeof(record));
  record.struct_size = sizeof(record);
  record.category = category;
  record.category_capacity = sizeof(category);
  record.message = message;
  record.message_capacity = sizeof(message);

  if (require_ok(coakka_logger_core_read_next(handle, 100, &record), "read_next")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }

  printf("coakka_logger_record sequence=%llu level=%s category=%.*s message=%.*s\n",
         (unsigned long long)record.sequence,
         coakka_logger_level_name(record.level),
         (int)record.category_length,
         record.category,
         (int)record.message_length,
         record.message);

  coakka_logger_core_stats_t stats;
  memset(&stats, 0, sizeof(stats));
  stats.struct_size = sizeof(stats);
  if (require_ok(coakka_logger_core_get_stats(handle, &stats), "get_stats")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }
  printf("coakka_logger_stats emitted=%llu delivered=%llu dropped=%llu language=mojo\n",
         (unsigned long long)stats.emitted_count,
         (unsigned long long)stats.delivered_count,
         (unsigned long long)stats.dropped_count);

  if (require_ok(coakka_logger_core_stop(handle), "stop")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }
  coakka_logger_core_destroy(handle);
  return sequence == 1 ? 0 : 1;
}
