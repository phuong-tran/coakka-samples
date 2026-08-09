#include <coakka/v2/file_lane.h>

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <direct.h>
#include <io.h>
#include <wchar.h>
#include <windows.h>
#else
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#endif

enum {
  FILE_LANE_EVIDENCE_PATH_BYTES = 1024,
  FILE_LANE_EVIDENCE_BLOCK_BYTES = 64 * 1024
};

static const uint64_t k_file_bytes = UINT64_C(9) * 1024u * 1024u + 731u;

static uint64_t monotonic_ns(void) {
#if defined(_WIN32)
  LARGE_INTEGER counter;
  LARGE_INTEGER frequency;
  if (!QueryPerformanceCounter(&counter) ||
      !QueryPerformanceFrequency(&frequency) || frequency.QuadPart <= 0) {
    return 0u;
  }
  return (uint64_t)(counter.QuadPart / frequency.QuadPart) *
             UINT64_C(1000000000) +
         (uint64_t)(counter.QuadPart % frequency.QuadPart) *
             UINT64_C(1000000000) / (uint64_t)frequency.QuadPart;
#else
  struct timespec timestamp;
  if (clock_gettime(CLOCK_MONOTONIC, &timestamp) != 0) {
    return 0u;
  }
  return (uint64_t)timestamp.tv_sec * UINT64_C(1000000000) +
         (uint64_t)timestamp.tv_nsec;
#endif
}

static int create_workspace(char *path, size_t path_len) {
#if defined(_WIN32)
  wchar_t temporary[MAX_PATH];
  wchar_t workspace[MAX_PATH];
  const DWORD temporary_len =
      GetTempPathW((DWORD)(sizeof(temporary) / sizeof(temporary[0])), temporary);
  const int workspace_len =
      swprintf(workspace, sizeof(workspace) / sizeof(workspace[0]),
               L"%lscoakka-file-lane-%lu-%llu", temporary,
               (unsigned long)GetCurrentProcessId(),
               (unsigned long long)GetTickCount64());
  if (temporary_len == 0u ||
      temporary_len >= sizeof(temporary) / sizeof(temporary[0]) ||
      workspace_len <= 0 || !CreateDirectoryW(workspace, NULL) ||
      WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, workspace, -1, path,
                          (int)path_len, NULL, NULL) <= 0) {
    return 1;
  }
  return 0;
#else
  const char *temporary = getenv("TMPDIR");
  if (temporary == NULL || temporary[0] == '\0') {
    temporary = "/tmp";
  }
  if (snprintf(path, path_len, "%s/coakka-file-lane-XXXXXX", temporary) < 0 ||
      mkdtemp(path) == NULL) {
    return 1;
  }
  return 0;
#endif
}

static int build_path(char *out, size_t out_len, const char *workspace,
                      const char *name) {
#if defined(_WIN32)
  const int written = snprintf(out, out_len, "%s\\%s", workspace, name);
#else
  const int written = snprintf(out, out_len, "%s/%s", workspace, name);
#endif
  return written < 0 || (size_t)written >= out_len;
}

static int write_pattern(const char *path, uint64_t bytes) {
  uint8_t block[FILE_LANE_EVIDENCE_BLOCK_BYTES];
  uint64_t written = 0u;
  size_t index;
  FILE *file = NULL;

  for (index = 0u; index < sizeof(block); ++index) {
    block[index] = (uint8_t)((index * 31u + 17u) & 0xffu);
  }
#if defined(_WIN32)
  {
    wchar_t wide_path[FILE_LANE_EVIDENCE_PATH_BYTES];
    if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path, -1, wide_path,
                            (int)(sizeof(wide_path) /
                                  sizeof(wide_path[0]))) <= 0) {
      return 1;
    }
    if (_wfopen_s(&file, wide_path, L"wb") != 0) {
      file = NULL;
    }
  }
#else
  file = fopen(path, "wb");
#endif
  if (file == NULL) {
    return 1;
  }
  while (written < bytes) {
    const uint64_t remaining = bytes - written;
    const size_t step = remaining < sizeof(block) ? (size_t)remaining
                                                  : sizeof(block);
    if (fwrite(block, 1u, step, file) != step) {
      fclose(file);
      return 1;
    }
    written += step;
  }
  {
    const int flush_failed = fflush(file) != 0;
    const int close_failed = fclose(file) != 0;
    return flush_failed || close_failed;
  }
}

static int terminal_state(uint32_t state) {
  return state == COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_REJECTED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_FAILED ||
         state == COAKKA_V2_FILE_TRANSFER_STATE_CANCELED;
}

static int wait_terminal(coakka_v2_file_lane_t *lane, const char *transfer_id,
                         uint32_t direction,
                         coakka_v2_file_transfer_snapshot_t *out) {
  const uint64_t deadline = monotonic_ns() + UINT64_C(30000000000);
  coakka_v2_status_t status;
  unsigned int waits = 0u;

  memset(out, 0, sizeof(*out));
  out->struct_size = sizeof(*out);
  status = coakka_v2_file_lane_get_transfer(lane, transfer_id, direction, out);
  while (status == COAKKA_V2_OK && !terminal_state(out->state) &&
         waits < 512u && monotonic_ns() < deadline) {
    const uint64_t sequence = out->update_sequence;
    out->struct_size = sizeof(*out);
    status = coakka_v2_file_lane_wait_transfer(
        lane, transfer_id, direction, sequence, 250u, out);
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
      status = COAKKA_V2_OK;
    }
    ++waits;
  }
  return status != COAKKA_V2_OK || !terminal_state(out->state);
}

static void remove_path(const char *path) {
  if (path == NULL || path[0] == '\0') {
    return;
  }
#if defined(_WIN32)
  wchar_t wide_path[FILE_LANE_EVIDENCE_PATH_BYTES];
  if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path, -1, wide_path,
                          (int)(sizeof(wide_path) / sizeof(wide_path[0]))) > 0) {
    (void)DeleteFileW(wide_path);
  }
#else
  (void)unlink(path);
#endif
}

static void remove_workspace(const char *workspace) {
  if (workspace == NULL || workspace[0] == '\0') {
    return;
  }
#if defined(_WIN32)
  wchar_t wide_path[FILE_LANE_EVIDENCE_PATH_BYTES];
  if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, workspace, -1,
                          wide_path,
                          (int)(sizeof(wide_path) / sizeof(wide_path[0]))) > 0) {
    (void)RemoveDirectoryW(wide_path);
  }
#else
  (void)rmdir(workspace);
#endif
}

int main(void) {
  static const char *transfer_id = "public-file-lane-roundtrip";
  /* Deterministic evidence only. Service B must generate a cryptographically
   * strong, single-use token in production and must never log it. */
  static const char *token = "public-file-lane-token";
  const char *failure = "";
  char workspace[FILE_LANE_EVIDENCE_PATH_BYTES] = {0};
  char source_path[FILE_LANE_EVIDENCE_PATH_BYTES] = {0};
  char destination_path[FILE_LANE_EVIDENCE_PATH_BYTES] = {0};
  char staging_path[FILE_LANE_EVIDENCE_PATH_BYTES] = {0};
  char checkpoint_path[FILE_LANE_EVIDENCE_PATH_BYTES] = {0};
  uint8_t source_digest[COAKKA_V2_FILE_LANE_SHA256_BYTES] = {0};
  uint8_t destination_digest[COAKKA_V2_FILE_LANE_SHA256_BYTES] = {0};
  uint64_t source_size = 0u;
  uint64_t destination_size = 0u;
  uint16_t receiver_port = 0u;
  coakka_v2_file_lane_t *receiver = NULL;
  coakka_v2_file_lane_t *sender = NULL;
  coakka_v2_file_lane_config_t receiver_config = {0};
  coakka_v2_file_lane_config_t sender_config = {0};
  coakka_v2_file_receive_spec_t receive_spec = {0};
  coakka_v2_file_send_spec_t send_spec = {0};
  coakka_v2_file_transfer_snapshot_t sent = {0};
  coakka_v2_file_transfer_snapshot_t received = {0};
  coakka_v2_file_lane_stats_t sender_stats = {0};
  coakka_v2_file_lane_stats_t receiver_stats = {0};
  int receiver_started = 0;
  int sender_started = 0;
  int passed = 0;

#define REQUIRE(condition, message)                                             \
  do {                                                                          \
    if (!(condition)) {                                                         \
      failure = (message);                                                      \
      goto cleanup;                                                             \
    }                                                                           \
  } while (0)

  REQUIRE(create_workspace(workspace, sizeof(workspace)) == 0,
          "workspace-create");
  REQUIRE(build_path(source_path, sizeof(source_path), workspace,
                     "source.bin") == 0,
          "source-path");
  REQUIRE(build_path(destination_path, sizeof(destination_path), workspace,
                     "received.bin") == 0,
          "destination-path");
  REQUIRE(snprintf(staging_path, sizeof(staging_path), "%s.coakka-part",
                   destination_path) > 0 &&
              strlen(destination_path) + strlen(".coakka-part") <
                  sizeof(staging_path),
          "staging-path");
  REQUIRE(snprintf(checkpoint_path, sizeof(checkpoint_path), "%s.checkpoint",
                   staging_path) > 0 &&
              strlen(staging_path) + strlen(".checkpoint") <
                  sizeof(checkpoint_path),
          "checkpoint-path");
  REQUIRE(write_pattern(source_path, k_file_bytes) == 0, "source-write");
  REQUIRE(coakka_v2_file_sha256_path(source_path, source_digest, &source_size) ==
              COAKKA_V2_OK &&
          source_size == k_file_bytes,
          "source-digest");

  /* Service B: keep a receiver lane alive, authorize the destination, and
   * prepare the exact size and digest before sharing a transfer grant. */
  receiver_config.struct_size = sizeof(receiver_config);
  receiver_config.flags = COAKKA_V2_FILE_LANE_ENABLE_RECEIVER;
  receiver_config.bind_host = "127.0.0.1";
  receiver_config.queue_capacity = 4u;
  receiver_config.max_file_size = UINT64_C(16) * 1024u * 1024u;
  receiver_config.io_timeout_ms = 5000u;
  receiver_config.checkpoint_bytes = 256u * 1024u;
  receiver_config.progress_bytes = 64u * 1024u;
  receiver_config.progress_interval_ms = 50u;
  receiver = coakka_v2_file_lane_create(&receiver_config);
  REQUIRE(receiver != NULL, "receiver-create");

  receive_spec.struct_size = sizeof(receive_spec);
  receive_spec.transfer_id = transfer_id;
  receive_spec.authorization_token = token;
  receive_spec.destination_path = destination_path;
  receive_spec.expected_size = source_size;
  memcpy(receive_spec.expected_sha256, source_digest, sizeof(source_digest));
  REQUIRE(coakka_v2_file_lane_prepare_receive(receiver, &receive_spec) ==
              COAKKA_V2_OK,
          "receiver-prepare");
  REQUIRE(coakka_v2_file_lane_start(receiver) == COAKKA_V2_OK,
          "receiver-start");
  receiver_started = 1;
  REQUIRE(coakka_v2_file_lane_get_bound_port(receiver, &receiver_port) ==
                  COAKKA_V2_OK &&
              receiver_port != 0u,
          "receiver-port");

  /* The production control API now returns transfer_id, token, size, digest,
   * host, and receiver_port to Service A. This loopback evidence passes those
   * values directly because both independent lanes live in this test process. */

  /* Service A: open its own sender lane and submit the source using the grant
   * that Service B created above. */
  sender_config.struct_size = sizeof(sender_config);
  sender_config.flags = COAKKA_V2_FILE_LANE_ENABLE_SENDER;
  sender_config.queue_capacity = 4u;
  sender_config.max_file_size = UINT64_C(16) * 1024u * 1024u;
  sender_config.io_timeout_ms = 5000u;
  sender_config.progress_bytes = 64u * 1024u;
  sender_config.progress_interval_ms = 50u;
  sender = coakka_v2_file_lane_create(&sender_config);
  REQUIRE(sender != NULL, "sender-create");
  REQUIRE(coakka_v2_file_lane_start(sender) == COAKKA_V2_OK, "sender-start");
  sender_started = 1;

  send_spec.struct_size = sizeof(send_spec);
  send_spec.transfer_id = transfer_id;
  send_spec.authorization_token = token;
  send_spec.remote_host = "127.0.0.1";
  send_spec.remote_port = receiver_port;
  send_spec.source_path = source_path;
  send_spec.expected_size = source_size;
  memcpy(send_spec.expected_sha256, source_digest, sizeof(source_digest));
  REQUIRE(coakka_v2_file_lane_submit_send(sender, &send_spec) == COAKKA_V2_OK,
          "sender-submit");

  /* Each service owns and checks its own terminal result. Sender completion
   * never substitutes for receiver completion. */
  REQUIRE(wait_terminal(sender, transfer_id,
                        COAKKA_V2_FILE_TRANSFER_DIRECTION_SEND, &sent) == 0,
          "sender-wait");
  REQUIRE(wait_terminal(receiver, transfer_id,
                        COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE,
                        &received) == 0,
          "receiver-wait");
  REQUIRE(sent.state == COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED &&
              sent.result == COAKKA_V2_FILE_TRANSFER_RESULT_OK &&
              sent.transferred_bytes == source_size &&
              sent.committed_offset == source_size &&
              sent.progress_milli == COAKKA_V2_FILE_LANE_PROGRESS_COMPLETE,
          "sender-terminal");
  REQUIRE(received.state == COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED &&
              received.result == COAKKA_V2_FILE_TRANSFER_RESULT_OK &&
              received.transferred_bytes == source_size &&
              received.committed_offset == source_size,
          "receiver-terminal");
  REQUIRE(coakka_v2_file_sha256_path(destination_path, destination_digest,
                                     &destination_size) == COAKKA_V2_OK &&
              destination_size == source_size &&
              memcmp(source_digest, destination_digest,
                     sizeof(source_digest)) == 0,
          "destination-digest");

  sender_stats.struct_size = sizeof(sender_stats);
  receiver_stats.struct_size = sizeof(receiver_stats);
  REQUIRE(coakka_v2_file_lane_get_stats(sender, &sender_stats) == COAKKA_V2_OK &&
              coakka_v2_file_lane_get_stats(receiver, &receiver_stats) ==
                  COAKKA_V2_OK &&
              sender_stats.completed_sends == 1u &&
              sender_stats.completed_send_bytes == source_size &&
              receiver_stats.completed_receives == 1u &&
              receiver_stats.completed_receive_bytes == source_size,
          "lane-stats");
  passed = 1;

cleanup:
  if (sender != 0 && sent.struct_size == 0u) {
    sent.struct_size = sizeof(sent);
    (void)coakka_v2_file_lane_get_transfer(
        sender, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_SEND, &sent);
  }
  if (receiver != 0 && received.struct_size == 0u) {
    received.struct_size = sizeof(received);
    (void)coakka_v2_file_lane_get_transfer(
        receiver, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE,
        &received);
  }
  if (sender_started) {
    (void)coakka_v2_file_lane_stop(sender);
  }
  if (receiver_started) {
    (void)coakka_v2_file_lane_stop(receiver);
  }
  coakka_v2_file_lane_destroy(sender);
  coakka_v2_file_lane_destroy(receiver);
  remove_path(checkpoint_path);
  remove_path(staging_path);
  remove_path(destination_path);
  remove_path(source_path);
  remove_workspace(workspace);
  if (!passed) {
    fprintf(stderr, "file-lane detail sender=%s receiver=%s\n", sent.detail,
            received.detail);
  }
  printf("{\"schema\":\"coakka.runtime.file-lane.evidence.v1\","
         "\"passed\":%s,\"failure\":\"%s\",\"fileBytes\":%" PRIu64 ","
         "\"senderState\":%u,\"senderResult\":%u,"
         "\"receiverState\":%u,\"receiverResult\":%u,"
         "\"senderCompletedBytes\":%" PRIu64 ","
         "\"receiverCompletedBytes\":%" PRIu64 "}\n",
         passed ? "true" : "false", failure, source_size, sent.state,
         sent.result, received.state, received.result,
         sender_stats.completed_send_bytes,
         receiver_stats.completed_receive_bytes);
  return passed ? 0 : 1;

#undef REQUIRE
}
