#include "evidence.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

enum {
  EVIDENCE_DEFAULT_SMOKE_REQUESTS = 128,
  EVIDENCE_DEFAULT_PRESSURE_REQUESTS = 512,
  EVIDENCE_DEFAULT_STRESS_REQUESTS = 2000,
  EVIDENCE_DEFAULT_SOAK_SECONDS = 10,
  EVIDENCE_DEFAULT_PAYLOAD_BYTES = 64 * 1024,
  EVIDENCE_DEFAULT_PRESSURE_PAYLOAD_BYTES = 4 * 1024,
  EVIDENCE_MAX_PRESSURE_PAYLOAD_BYTES = 16 * 1024,
  EVIDENCE_DEFAULT_QUEUE_CAPACITY = 1024,
  EVIDENCE_PRESSURE_QUEUE_CAPACITY = 2,
  EVIDENCE_MAX_PAYLOAD_BYTES = 3 * 1024 * 1024,
  EVIDENCE_MAX_REQUEST_LIMIT = 500 * 1000,
};

const char* evidence_mode_name(evidence_mode_t mode) {
  switch (mode) {
    case EVIDENCE_MODE_SMOKE:
      return "smoke";
    case EVIDENCE_MODE_PRESSURE:
      return "pressure";
    case EVIDENCE_MODE_STRESS:
      return "stress";
    case EVIDENCE_MODE_SOAK:
      return "soak";
  }
  return "unknown";
}

const char* evidence_submission_path_name(evidence_mode_t mode) {
  return mode == EVIDENCE_MODE_PRESSURE ? "request-channel" : "native-submit";
}

static int parse_mode(const char* text, evidence_mode_t* out_mode) {
  if (strcmp(text, "smoke") == 0) {
    *out_mode = EVIDENCE_MODE_SMOKE;
    return 0;
  }
  if (strcmp(text, "pressure") == 0) {
    *out_mode = EVIDENCE_MODE_PRESSURE;
    return 0;
  }
  if (strcmp(text, "stress") == 0) {
    *out_mode = EVIDENCE_MODE_STRESS;
    return 0;
  }
  if (strcmp(text, "soak") == 0) {
    *out_mode = EVIDENCE_MODE_SOAK;
    return 0;
  }
  return 1;
}

static int parse_unsigned(const char* text,
                          unsigned long long* out_value,
                          char** out_end) {
  char* end = NULL;
  unsigned long long parsed;

  if (text == NULL || !isdigit((unsigned char)*text)) {
    return 1;
  }

  errno = 0;
  parsed = strtoull(text, &end, 10);
  if (errno == ERANGE || end == text) {
    return 1;
  }

  *out_value = parsed;
  *out_end = end;
  return 0;
}

static int parse_u64(const char* text, uint64_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  if (parse_unsigned(text, &parsed, &end) || *end != '\0') {
    return 1;
  }
  if (parsed > (unsigned long long)UINT64_MAX) {
    return 1;
  }
  *out_value = (uint64_t)parsed;
  return 0;
}

static int parse_size(const char* text, size_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  unsigned long long multiplier = 1;

  if (parse_unsigned(text, &parsed, &end)) {
    return 1;
  }
  if (*end != '\0') {
    const char suffix = (char)tolower((unsigned char)*end);
    if (end[1] != '\0') {
      return 1;
    }
    if (suffix == 'k') {
      multiplier = 1024ull;
    } else if (suffix == 'm') {
      multiplier = 1024ull * 1024ull;
    } else {
      return 1;
    }
  }
  if (parsed > (unsigned long long)SIZE_MAX / multiplier) {
    return 1;
  }
  *out_value = (size_t)(parsed * multiplier);
  return 0;
}

static int parse_duration_ms(const char* text, uint64_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  uint64_t multiplier = 1000u;

  if (parse_unsigned(text, &parsed, &end)) {
    return 1;
  }
  if (*end != '\0') {
    if (strcmp(end, "ms") == 0) {
      multiplier = 1u;
    } else if (strcmp(end, "s") == 0) {
      multiplier = 1000u;
    } else if (strcmp(end, "m") == 0) {
      multiplier = 60u * 1000u;
    } else {
      return 1;
    }
  }
  if (parsed > (unsigned long long)UINT64_MAX / multiplier) {
    return 1;
  }
  *out_value = (uint64_t)parsed * multiplier;
  return 0;
}

static int require_value(int index,
                         int argc,
                         char** argv,
                         const char** out_value) {
  if (index + 1 >= argc) {
    return 1;
  }
  *out_value = argv[index + 1];
  return 0;
}

static void init_config(evidence_config_t* config) {
  memset(config, 0, sizeof(*config));
  config->mode = EVIDENCE_MODE_SMOKE;
  config->payload_bytes = EVIDENCE_DEFAULT_PAYLOAD_BYTES;
  config->request_limit = EVIDENCE_DEFAULT_SMOKE_REQUESTS;
  config->queue_capacity = EVIDENCE_DEFAULT_QUEUE_CAPACITY;
  config->max_in_flight = 64;
}

static void apply_mode_defaults(evidence_config_t* config) {
  switch (config->mode) {
    case EVIDENCE_MODE_SMOKE:
      config->request_limit = EVIDENCE_DEFAULT_SMOKE_REQUESTS;
      config->queue_capacity = EVIDENCE_DEFAULT_QUEUE_CAPACITY;
      break;
    case EVIDENCE_MODE_PRESSURE:
      /*
       * Pressure deliberately removes the harness in-flight guard and narrows
       * ingress so a finite burst must produce explicit admission evidence.
       */
      config->request_limit = EVIDENCE_DEFAULT_PRESSURE_REQUESTS;
      config->payload_bytes = EVIDENCE_DEFAULT_PRESSURE_PAYLOAD_BYTES;
      config->queue_capacity = EVIDENCE_PRESSURE_QUEUE_CAPACITY;
      config->max_in_flight = 0;
      break;
    case EVIDENCE_MODE_STRESS:
      config->request_limit = EVIDENCE_DEFAULT_STRESS_REQUESTS;
      config->queue_capacity = EVIDENCE_DEFAULT_QUEUE_CAPACITY;
      break;
    case EVIDENCE_MODE_SOAK:
      config->request_limit = 0;
      config->duration_ms = (uint64_t)EVIDENCE_DEFAULT_SOAK_SECONDS * 1000u;
      config->queue_capacity = EVIDENCE_DEFAULT_QUEUE_CAPACITY;
      break;
  }
}

static evidence_parse_status_t validate_config(const evidence_config_t* config,
                                               int payload_explicit,
                                               const char** out_error) {
  if (config->payload_bytes == 0 ||
      config->payload_bytes > (size_t)EVIDENCE_MAX_PAYLOAD_BYTES) {
    *out_error = "payload must be between 1 byte and 3M";
    return EVIDENCE_PARSE_ERROR;
  }
  if (config->mode == EVIDENCE_MODE_PRESSURE &&
      payload_explicit &&
      config->payload_bytes > (size_t)EVIDENCE_MAX_PRESSURE_PAYLOAD_BYTES) {
    *out_error =
        "pressure mode payload must be 16K or smaller; use smoke, stress, or soak for large-payload evidence";
    return EVIDENCE_PARSE_ERROR;
  }
  if (config->queue_capacity == 0 || config->queue_capacity > (size_t)INT_MAX) {
    *out_error = "queue capacity must be between 1 and INT_MAX";
    return EVIDENCE_PARSE_ERROR;
  }
  if (config->mode == EVIDENCE_MODE_SOAK &&
      config->duration_ms == 0 &&
      config->request_limit == 0) {
    *out_error = "soak requires --duration or --requests";
    return EVIDENCE_PARSE_ERROR;
  }
  if (config->mode != EVIDENCE_MODE_SOAK && config->request_limit == 0) {
    *out_error = "mode requires at least one request";
    return EVIDENCE_PARSE_ERROR;
  }
  if (config->request_limit > (uint64_t)EVIDENCE_MAX_REQUEST_LIMIT) {
    *out_error = "request limit must be 500K or smaller";
    return EVIDENCE_PARSE_ERROR;
  }
  return EVIDENCE_PARSE_OK;
}

evidence_parse_status_t evidence_parse_args(int argc,
                                            char** argv,
                                            evidence_config_t* config,
                                            const char** out_error) {
  int index = 1;
  int payload_explicit = 0;

  init_config(config);
  if (index < argc && argv[index][0] != '-') {
    if (parse_mode(argv[index], &config->mode)) {
      *out_error = "unknown mode";
      return EVIDENCE_PARSE_ERROR;
    }
    ++index;
  }
  apply_mode_defaults(config);

  while (index < argc) {
    const char* value = NULL;
    if (strcmp(argv[index], "--payload") == 0) {
      if (require_value(index, argc, argv, &value) ||
          parse_size(value, &config->payload_bytes)) {
        *out_error = "invalid --payload";
        return EVIDENCE_PARSE_ERROR;
      }
      payload_explicit = 1;
      index += 2;
    } else if (strcmp(argv[index], "--requests") == 0) {
      if (require_value(index, argc, argv, &value) ||
          parse_u64(value, &config->request_limit)) {
        *out_error = "invalid --requests";
        return EVIDENCE_PARSE_ERROR;
      }
      index += 2;
    } else if (strcmp(argv[index], "--duration") == 0) {
      if (require_value(index, argc, argv, &value) ||
          parse_duration_ms(value, &config->duration_ms)) {
        *out_error = "invalid --duration";
        return EVIDENCE_PARSE_ERROR;
      }
      index += 2;
    } else if (strcmp(argv[index], "--queue-capacity") == 0) {
      if (require_value(index, argc, argv, &value) ||
          parse_size(value, &config->queue_capacity)) {
        *out_error = "invalid --queue-capacity";
        return EVIDENCE_PARSE_ERROR;
      }
      index += 2;
    } else if (strcmp(argv[index], "--max-in-flight") == 0) {
      if (require_value(index, argc, argv, &value) ||
          parse_u64(value, &config->max_in_flight)) {
        *out_error = "invalid --max-in-flight";
        return EVIDENCE_PARSE_ERROR;
      }
      index += 2;
    } else if (strcmp(argv[index], "--help") == 0 ||
               strcmp(argv[index], "-h") == 0) {
      *out_error = "help requested";
      return EVIDENCE_PARSE_HELP;
    } else {
      *out_error = "unknown option";
      return EVIDENCE_PARSE_ERROR;
    }
  }

  return validate_config(config, payload_explicit, out_error);
}
