#include "sample_common.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>

const char *sample_required_env(const char *name) {
  const char *value = getenv(name);
  if (value == NULL || value[0] == '\0') {
    fprintf(stderr, "required environment variable is missing: %s\n", name);
    return NULL;
  }
  return value;
}

const char *sample_optional_env(const char *name, const char *fallback) {
  const char *value = getenv(name);
  return value == NULL ? fallback : value;
}

static int parse_unsigned(const char *text, unsigned long long maximum,
                          unsigned long long *out_value) {
  char *end = NULL;
  const char *cursor;
  unsigned long long value;

  if (text == NULL || text[0] == '\0' || out_value == NULL) {
    return 1;
  }
  for (cursor = text; *cursor != '\0'; ++cursor) {
    if (*cursor < '0' || *cursor > '9') {
      return 1;
    }
  }
  errno = 0;
  value = strtoull(text, &end, 10);
  if (errno != 0 || end == text || *end != '\0' || value > maximum) {
    return 1;
  }
  *out_value = value;
  return 0;
}

int sample_parse_u16(const char *text, uint16_t *out_value) {
  unsigned long long value = 0;
  if (out_value == NULL || parse_unsigned(text, UINT16_MAX, &value) != 0) {
    return 1;
  }
  *out_value = (uint16_t)value;
  return 0;
}

int sample_parse_u64(const char *text, uint64_t *out_value) {
  unsigned long long value = 0;
  if (out_value == NULL || parse_unsigned(text, UINT64_MAX, &value) != 0) {
    return 1;
  }
  *out_value = (uint64_t)value;
  return 0;
}

static int hex_nibble(char value, uint8_t *out_value) {
  if (value >= '0' && value <= '9') {
    *out_value = (uint8_t)(value - '0');
    return 0;
  }
  if (value >= 'a' && value <= 'f') {
    *out_value = (uint8_t)(value - 'a' + 10);
    return 0;
  }
  if (value >= 'A' && value <= 'F') {
    *out_value = (uint8_t)(value - 'A' + 10);
    return 0;
  }
  return 1;
}

int sample_parse_sha256(const char *text, uint8_t out_digest[32]) {
  size_t index;
  if (text == NULL || out_digest == NULL) {
    return 1;
  }
  for (index = 0; index < 32; ++index) {
    uint8_t high = 0;
    uint8_t low = 0;
    if (text[index * 2] == '\0' || text[index * 2 + 1] == '\0' ||
        hex_nibble(text[index * 2], &high) != 0 ||
        hex_nibble(text[index * 2 + 1], &low) != 0) {
      return 1;
    }
    out_digest[index] = (uint8_t)((high << 4) | low);
  }
  return text[64] == '\0' ? 0 : 1;
}

int sample_load_publish_inputs(sample_publish_inputs_t *out_inputs) {
  const char *receiver_port_text;
  const char *size_text;
  const char *digest_text;

  if (out_inputs == NULL) {
    return 1;
  }
  out_inputs->job_id = sample_required_env("COAKKA_SAMPLE_JOB_ID");
  out_inputs->staging_root = sample_required_env("COAKKA_SAMPLE_STAGING_ROOT");
  out_inputs->destination_name =
      sample_required_env("COAKKA_SAMPLE_STAGING_NAME");
  out_inputs->transfer_id = sample_required_env("COAKKA_SAMPLE_TRANSFER_ID");
  out_inputs->authorization_token =
      sample_required_env("COAKKA_SAMPLE_TRANSFER_TOKEN");
  out_inputs->receiver_host =
      sample_required_env("COAKKA_SAMPLE_RECEIVER_HOST");
  receiver_port_text = sample_required_env("COAKKA_SAMPLE_RECEIVER_PORT");
  size_text = sample_required_env("COAKKA_SAMPLE_ARTIFACT_SIZE");
  digest_text = sample_required_env("COAKKA_SAMPLE_ARTIFACT_SHA256");

  if (out_inputs->job_id == NULL || out_inputs->staging_root == NULL ||
      out_inputs->destination_name == NULL || out_inputs->transfer_id == NULL ||
      out_inputs->authorization_token == NULL ||
      out_inputs->receiver_host == NULL || receiver_port_text == NULL ||
      size_text == NULL || digest_text == NULL ||
      sample_parse_u16(receiver_port_text, &out_inputs->receiver_port) != 0 ||
      sample_parse_u64(size_text, &out_inputs->expected_size) != 0 ||
      sample_parse_sha256(digest_text, out_inputs->expected_sha256) != 0 ||
      out_inputs->receiver_port == 0 || out_inputs->expected_size == 0 ||
      out_inputs->expected_size > UINT64_MAX - 4096u) {
    fprintf(stderr, "publisher configuration is invalid\n");
    return 1;
  }
  return 0;
}

int sample_file_state_is_terminal(uint32_t state) {
  return state == COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_REJECTED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_FAILED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_CANCELED;
}

int sample_write_ready_file(const char *path, uint16_t port) {
  FILE *output;
  int failed = 0;
  if (path == NULL) {
    return 1;
  }
  output = fopen(path, "w");
  if (output == NULL) {
    fprintf(stderr, "could not create readiness file: %s\n", path);
    return 1;
  }
  if (fprintf(output, "%u\n", (unsigned int)port) < 0 || fflush(output) != 0) {
    failed = 1;
  }
  if (fclose(output) != 0) {
    failed = 1;
  }
  if (failed) {
    (void)remove(path);
    fprintf(stderr, "could not publish receiver readiness: %s\n", path);
    return 1;
  }
  return 0;
}
