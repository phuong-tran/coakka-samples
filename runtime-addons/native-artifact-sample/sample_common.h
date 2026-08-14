#ifndef COAKKA_NATIVE_ARTIFACT_SAMPLE_COMMON_H
#define COAKKA_NATIVE_ARTIFACT_SAMPLE_COMMON_H

#include <stddef.h>
#include <stdint.h>

#include "coakka/v2/file_lane.h"

typedef struct sample_publish_inputs_t {
  const char *job_id;
  const char *staging_root;
  const char *destination_name;
  const char *transfer_id;
  const char *authorization_token;
  const char *receiver_host;
  uint16_t receiver_port;
  uint64_t expected_size;
  uint8_t expected_sha256[COAKKA_V2_FILE_LANE_SHA256_BYTES];
} sample_publish_inputs_t;

const char *sample_required_env(const char *name);
const char *sample_optional_env(const char *name, const char *fallback);
int sample_parse_u16(const char *text, uint16_t *out_value);
int sample_parse_u64(const char *text, uint64_t *out_value);
int sample_parse_sha256(const char *text, uint8_t out_digest[32]);
int sample_load_publish_inputs(sample_publish_inputs_t *out_inputs);
int sample_file_state_is_terminal(uint32_t state);
int sample_write_ready_file(const char *path, uint16_t port);

#endif
