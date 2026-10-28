#include "coakka/logger/core.h"
#include "coakka/logger/utils.h"

#include <stdio.h>
#include <string.h>

static int fail(const char* message) {
  fprintf(stderr, "%s\n", message);
  return 1;
}

static int require_ok(coakka_logger_status_t status, const char* step) {
  if (status == COAKKA_LOGGER_STATUS_OK) {
    return 0;
  }
  fprintf(stderr, "%s failed: %s\n", step, coakka_logger_status_name(status));
  return 1;
}

int main(void) {
  const int attempts = 8;
  int accepted = 0;
  int rejected = 0;
  int drained = 0;

  coakka_logger_core_config_t config = coakka_logger_core_default_config();
  config.system_name = "native-c-pressure-logger";
  config.queue_capacity = 2;
  config.min_level = COAKKA_LOGGER_LEVEL_INFO;
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

  for (int index = 0; index < attempts; ++index) {
    char message[96];
    snprintf(message, sizeof(message), "{\"event\":\"pressure\",\"index\":%d}", index);
    uint64_t sequence = 0;
    const coakka_logger_status_t status =
        coakka_logger_core_log_info(handle, "samples.logger.native.c.pressure", message, &sequence);
    if (status == COAKKA_LOGGER_STATUS_OK) {
      accepted += 1;
    } else if (status == COAKKA_LOGGER_STATUS_QUEUE_FULL) {
      rejected += 1;
    } else {
      fprintf(stderr, "log_info failed: %s\n", coakka_logger_status_name(status));
      coakka_logger_core_destroy(handle);
      return 1;
    }
  }

  coakka_logger_core_stats_t before_drain;
  memset(&before_drain, 0, sizeof(before_drain));
  before_drain.struct_size = sizeof(before_drain);
  if (require_ok(coakka_logger_core_get_stats(handle, &before_drain), "get_stats_before_drain")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }

  for (;;) {
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

    const coakka_logger_status_t status = coakka_logger_core_read_next(handle, 0, &record);
    if (status == COAKKA_LOGGER_STATUS_TIMED_OUT) {
      break;
    }
    if (status != COAKKA_LOGGER_STATUS_OK) {
      fprintf(stderr, "read_next failed: %s\n", coakka_logger_status_name(status));
      coakka_logger_core_destroy(handle);
      return 1;
    }
    drained += 1;
  }

  coakka_logger_core_stats_t after_drain;
  memset(&after_drain, 0, sizeof(after_drain));
  after_drain.struct_size = sizeof(after_drain);
  if (require_ok(coakka_logger_core_get_stats(handle, &after_drain), "get_stats_after_drain")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }

  if (accepted != 2) {
    coakka_logger_core_destroy(handle);
    return fail("expected accepted=2");
  }
  if (rejected != attempts - accepted) {
    coakka_logger_core_destroy(handle);
    return fail("expected rejected=attempts-accepted");
  }
  if (drained != accepted) {
    coakka_logger_core_destroy(handle);
    return fail("expected drained=accepted");
  }
  if (before_drain.queue_high_watermark != 2) {
    coakka_logger_core_destroy(handle);
    return fail("expected queueHighWatermark=2");
  }
  if (after_drain.dropped_count != (uint64_t)rejected) {
    coakka_logger_core_destroy(handle);
    return fail("expected dropped=rejected");
  }

  printf("coakka_logger_pressure attempts=%d accepted=%d rejected=%d capacity=%u highWatermark=%u language=c\n",
         attempts,
         accepted,
         rejected,
         after_drain.queue_capacity,
         after_drain.queue_high_watermark);
  printf("coakka_logger_stats emitted=%llu delivered=%llu dropped=%llu language=c\n",
         (unsigned long long)after_drain.emitted_count,
         (unsigned long long)after_drain.delivered_count,
         (unsigned long long)after_drain.dropped_count);

  if (require_ok(coakka_logger_core_stop(handle), "stop")) {
    coakka_logger_core_destroy(handle);
    return 1;
  }
  coakka_logger_core_destroy(handle);
  return 0;
}
