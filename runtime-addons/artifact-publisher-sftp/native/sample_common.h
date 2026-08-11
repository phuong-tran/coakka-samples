#ifndef COAKKA_SFTP_SAMPLE_COMMON_H
#define COAKKA_SFTP_SAMPLE_COMMON_H

#include <stddef.h>
#include <stdint.h>

#include "coakka/v2/file_lane.h"

const char *sample_required_env(const char *name);
const char *sample_optional_env(const char *name, const char *fallback);
int sample_parse_u16(const char *text, uint16_t *out_value);
int sample_parse_u64(const char *text, uint64_t *out_value);
int sample_parse_sha256(const char *text, uint8_t out_digest[32]);
int sample_file_state_is_terminal(uint32_t state);
int sample_write_ready_file(const char *path, uint16_t port);

#endif
