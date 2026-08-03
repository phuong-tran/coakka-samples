#include "concurrency_evidence.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

enum {
  CONCURRENCY_DEFAULT_THREADS = 4,
  CONCURRENCY_DEFAULT_REQUESTS_PER_THREAD = 128,
  CONCURRENCY_DEFAULT_GENERATIONS = 16,
  CONCURRENCY_DEFAULT_LIFECYCLE_ITERATIONS = 8,
  CONCURRENCY_DEFAULT_QUEUE_CAPACITY = 1024,
  CONCURRENCY_DEFAULT_TIMEOUT_MS = 30000,
  CONCURRENCY_MAX_THREADS = 64,
  CONCURRENCY_MAX_QUEUE_CAPACITY = 65536,
};

static const uint64_t CONCURRENCY_MAX_REQUESTS_PER_THREAD = UINT64_C(100000);
static const uint64_t CONCURRENCY_MAX_GENERATIONS = UINT64_C(100000);
static const uint64_t CONCURRENCY_MAX_LIFECYCLE_ITERATIONS = UINT64_C(10000);
static const uint64_t CONCURRENCY_MAX_TIMEOUT_MS = UINT64_C(600000);

const char* concurrency_evidence_mode_name(concurrency_evidence_mode_t mode) {
  return mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD ? "hot-reload"
                                                       : "race";
}

static int parse_u64(const char* text, uint64_t maximum, uint64_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;

  if (text == NULL || text[0] == '\0' || out_value == NULL || text[0] == '-') {
    return 1;
  }
  errno = 0;
  parsed = strtoull(text, &end, 10);
  if (errno != 0 || end == text || *end != '\0' || parsed == 0u ||
      (uint64_t)parsed > maximum) {
    return 1;
  }
  *out_value = (uint64_t)parsed;
  return 0;
}

static int parse_size(const char* text, size_t maximum, size_t* out_value) {
  uint64_t parsed;
  if (parse_u64(text, (uint64_t)maximum, &parsed)) {
    return 1;
  }
  *out_value = (size_t)parsed;
  return 0;
}

static int parse_timeout(const char* text, uint64_t* out_value) {
  char digits[32];
  size_t length;
  size_t digit_count;
  uint64_t value;
  uint64_t multiplier;

  if (text == NULL || out_value == NULL) {
    return 1;
  }
  length = strlen(text);
  multiplier = 1u;
  digit_count = length;
  if (length > 2u && strcmp(text + length - 2u, "ms") == 0) {
    digit_count = length - 2u;
  } else if (length > 1u && text[length - 1u] == 's') {
    digit_count = length - 1u;
    multiplier = 1000u;
  }
  if (digit_count == 0u || digit_count >= sizeof(digits)) {
    return 1;
  }
  memcpy(digits, text, digit_count);
  digits[digit_count] = '\0';
  if (parse_u64(digits, CONCURRENCY_MAX_TIMEOUT_MS, &value) ||
      value > CONCURRENCY_MAX_TIMEOUT_MS / multiplier) {
    return 1;
  }
  *out_value = value * multiplier;
  return 0;
}

static int take_value(int argc,
                      char** argv,
                      int* index,
                      const char** out_value) {
  if (*index + 1 >= argc) {
    return 1;
  }
  ++(*index);
  *out_value = argv[*index];
  return 0;
}

concurrency_evidence_parse_status_t concurrency_evidence_parse_args(
    int argc,
    char** argv,
    concurrency_evidence_config_t* config,
    const char** out_error) {
  int index;

  if (config == NULL || out_error == NULL) {
    return CONCURRENCY_EVIDENCE_PARSE_ERROR;
  }
  memset(config, 0, sizeof(*config));
  *out_error = NULL;
  config->mode = CONCURRENCY_EVIDENCE_MODE_RACE;
  config->thread_count = CONCURRENCY_DEFAULT_THREADS;
  config->requests_per_thread = CONCURRENCY_DEFAULT_REQUESTS_PER_THREAD;
  config->generation_count = CONCURRENCY_DEFAULT_GENERATIONS;
  config->lifecycle_iterations_per_thread =
      CONCURRENCY_DEFAULT_LIFECYCLE_ITERATIONS;
  config->queue_capacity = CONCURRENCY_DEFAULT_QUEUE_CAPACITY;
  config->timeout_ms = CONCURRENCY_DEFAULT_TIMEOUT_MS;

  if (argc > 1 &&
      (strcmp(argv[1], "-h") == 0 || strcmp(argv[1], "--help") == 0)) {
    return CONCURRENCY_EVIDENCE_PARSE_HELP;
  }
  if (argc > 1) {
    if (strcmp(argv[1], "race") == 0) {
      config->mode = CONCURRENCY_EVIDENCE_MODE_RACE;
    } else if (strcmp(argv[1], "hot-reload") == 0) {
      config->mode = CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD;
    } else {
      *out_error = "mode must be race or hot-reload";
      return CONCURRENCY_EVIDENCE_PARSE_ERROR;
    }
  }

  for (index = 2; index < argc; ++index) {
    const char* value = NULL;
    if (strcmp(argv[index], "--threads") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_size(value, CONCURRENCY_MAX_THREADS, &config->thread_count)) {
        *out_error = "--threads must be between 1 and 64";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else if (strcmp(argv[index], "--requests") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_u64(value,
                    CONCURRENCY_MAX_REQUESTS_PER_THREAD,
                    &config->requests_per_thread)) {
        *out_error = "--requests must be between 1 and 100000 per thread";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else if (strcmp(argv[index], "--generations") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_u64(value,
                    CONCURRENCY_MAX_GENERATIONS,
                    &config->generation_count)) {
        *out_error = "--generations must be between 1 and 100000";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else if (strcmp(argv[index], "--lifecycle-iterations") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_u64(value,
                    CONCURRENCY_MAX_LIFECYCLE_ITERATIONS,
                    &config->lifecycle_iterations_per_thread)) {
        *out_error =
            "--lifecycle-iterations must be between 1 and 10000 per thread";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else if (strcmp(argv[index], "--queue-capacity") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_size(value,
                     CONCURRENCY_MAX_QUEUE_CAPACITY,
                     &config->queue_capacity)) {
        *out_error = "--queue-capacity must be between 1 and 65536";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else if (strcmp(argv[index], "--timeout") == 0) {
      if (take_value(argc, argv, &index, &value) ||
          parse_timeout(value, &config->timeout_ms)) {
        *out_error = "--timeout must be a positive value up to 600s";
        return CONCURRENCY_EVIDENCE_PARSE_ERROR;
      }
    } else {
      *out_error = "unknown concurrency evidence option";
      return CONCURRENCY_EVIDENCE_PARSE_ERROR;
    }
  }
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD &&
      (config->generation_count < 2u ||
       config->generation_count >
           config->requests_per_thread)) {
    *out_error =
        "hot-reload generations must be between 2 and requests per thread";
    return CONCURRENCY_EVIDENCE_PARSE_ERROR;
  }
  return CONCURRENCY_EVIDENCE_PARSE_OK;
}
