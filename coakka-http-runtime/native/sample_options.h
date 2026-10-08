/* Sample-only configuration shared by the C and C++ applications.
 * Declarations are borrowed until create copies them. Defaults belong to the
 * runtime; this file only chooses application routes, files and test identity.
 */
#ifndef SAMPLE_OPTIONS_H
#define SAMPLE_OPTIONS_H
#include <coakka/http/http.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct sample_options {
  coakka_http_server_options_t server;
  coakka_http_static_mount_t mount;
  coakka_http_file_authority_t files;
  coakka_http_monitor_options_t monitor;
  coakka_http_server_tuning_t tuning;
  coakka_http_compression_t compression;
  char certificate[4096], key[4096], trust[4096];
  const char *response;
} sample_options_t;

static inline coakka_http_bytes_t sample_bytes(const char *text) {
  coakka_http_bytes_t result = {(const uint8_t *)text, strlen(text)};
  return result;
}

/* Bounded CLI vocabulary mapping, never a request-path lookup or a table of
 * Core defaults. The runtime validates and resolves the selected profile. */
static inline int sample_profile(const char *name, coakka_http_notification_profile_t *out) {
  if (strcmp(name, "auto") == 0) *out = COAKKA_HTTP_NOTIFICATION_AUTO;
  else if (strcmp(name, "small") == 0) *out = COAKKA_HTTP_NOTIFICATION_SMALL;
  else if (strcmp(name, "medium") == 0) *out = COAKKA_HTTP_NOTIFICATION_MEDIUM;
  else if (strcmp(name, "large") == 0) *out = COAKKA_HTTP_NOTIFICATION_LARGE;
  else if (strcmp(name, "xlarge") == 0) *out = COAKKA_HTTP_NOTIFICATION_XLARGE;
  else if (strcmp(name, "xxlarge") == 0) *out = COAKKA_HTTP_NOTIFICATION_XXLARGE;
  else if (strcmp(name, "ultra") == 0) *out = COAKKA_HTTP_NOTIFICATION_ULTRA;
  else return 0;
  return 1;
}

/* Unknown options fail, including unsupported tuning knobs. Credential files
 * stay outside source control and must outlive startup's credential load.
 */
static inline int sample_options_parse(sample_options_t *config, int argc, char **argv) {
  const char *assets = NULL, *fixtures = NULL, *security = "plain", *protocol = "http1";
  int index;
  memset(config, 0, sizeof(*config));
  coakka_http_server_options_init(&config->server);
  coakka_http_server_tuning_init(&config->tuning);
  coakka_http_compression_init(&config->compression);
  config->server.bind_address = sample_bytes("127.0.0.1");
  config->response = "plain-ready";
  for (index = 1; index < argc; index += 2) {
    const char *value;
    if (index + 1 == argc) return 0;
    value = argv[index + 1];
    if (strcmp(argv[index], "--assets") == 0) assets = value;
    else if (strcmp(argv[index], "--fixtures") == 0) fixtures = value;
    else if (strcmp(argv[index], "--security") == 0) security = value;
    else if (strcmp(argv[index], "--protocol") == 0) protocol = value;
    else if (strcmp(argv[index], "--cpu") == 0) {
      if (strcmp(value, "auto") == 0) config->tuning.cpu_policy = COAKKA_HTTP_CPU_AUTO;
      else if (strcmp(value, "single") == 0) config->tuning.cpu_policy = COAKKA_HTTP_CPU_SINGLE;
      else return 0;
      config->server.tuning = &config->tuning;
    } else if (strcmp(argv[index], "--request-batch") == 0) {
      if (!sample_profile(value, &config->tuning.request_notification_profile)) return 0;
      config->server.tuning = &config->tuning;
    } else if (strcmp(argv[index], "--terminal-batch") == 0) {
      if (!sample_profile(value, &config->tuning.terminal_notification_profile)) return 0;
      config->server.tuning = &config->tuning;
    } else if (strcmp(argv[index], "--compression") == 0) {
      if (strcmp(value, "gzip") == 0) {
        config->compression.mode = COAKKA_HTTP_COMPRESSION_GZIP;
        /* Deliberate demo choices: make small wire responses observable.
         * These are not a copied table of runtime defaults. */
        config->compression.minimum_body_bytes = 1;
        config->compression.gzip_level = 6;
      } else if (strcmp(value, "disabled") == 0) coakka_http_compression_init(&config->compression);
      else return 0;
      config->tuning.compression = &config->compression;
      config->server.tuning = &config->tuning;
    }
    else if (strcmp(argv[index], "--port") == 0) {
      char *end = NULL;
      unsigned long port = strtoul(value, &end, 10);
      if (*value == '\0' || *end != '\0' || port > 65535UL) return 0;
      config->server.port = (uint16_t)port;
    } else return 0;
  }
  if (strcmp(protocol, "http1") == 0) config->server.protocol = COAKKA_HTTP_PROTOCOL_HTTP_1_1;
  else if (strcmp(protocol, "http2") == 0) config->server.protocol = COAKKA_HTTP_PROTOCOL_HTTP_2;
  else if (strcmp(protocol, "http3") == 0) config->server.protocol = COAKKA_HTTP_PROTOCOL_HTTP_3;
  else return 0;
  if (strcmp(security, "plain") != 0) {
    int a, b, c;
    if (fixtures == NULL) return 0;
    if (strcmp(security, "tls") == 0) {
      config->server.security = COAKKA_HTTP_SECURITY_TLS; config->response = "tls-ready";
    } else if (strcmp(security, "mtls") == 0) {
      config->server.security = COAKKA_HTTP_SECURITY_MUTUAL_TLS; config->response = "mtls-ready";
    } else return 0;
    a = snprintf(config->certificate, sizeof(config->certificate), "%s/server.pem", fixtures);
    b = snprintf(config->key, sizeof(config->key), "%s/server.key", fixtures);
    c = snprintf(config->trust, sizeof(config->trust), "%s/ca.pem", fixtures);
    if (a < 0 || b < 0 || c < 0 || (size_t)a >= sizeof(config->certificate) ||
        (size_t)b >= sizeof(config->key) || (size_t)c >= sizeof(config->trust)) return 0;
    config->server.credential_generation = 1;
    config->server.credential_id = sample_bytes("sample.identity");
    config->server.certificate_chain_file = sample_bytes(config->certificate);
    config->server.private_key_file = sample_bytes(config->key);
    if (config->server.security == COAKKA_HTTP_SECURITY_MUTUAL_TLS)
      config->server.trust_roots_file = sample_bytes(config->trust);
  }
  if (assets != NULL) {
    coakka_http_static_mount_init(&config->mount);
    config->mount.mount_id = 1; config->mount.url_prefix = sample_bytes("/app");
    config->mount.root_path = sample_bytes(assets);
    config->mount.index_file = sample_bytes("index.html");
    config->mount.spa_fallback_file = sample_bytes("index.html");
    config->mount.has_index_file = 1; config->mount.has_spa_fallback_file = 1;
    config->server.static_mounts = &config->mount; config->server.static_mount_count = 1;
    coakka_http_file_authority_init(&config->files);
    config->files.authority_id = 82; config->files.root_path = sample_bytes(assets);
    config->files.max_active_files = 2; config->files.max_file_bytes = 1048576;
    config->server.file_authorities = &config->files; config->server.file_authority_count = 1;
  }
  coakka_http_monitor_options_init(&config->monitor);
  config->monitor.collection = COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS;
  config->monitor.event_capacity = 32;
  config->monitor.max_events_per_read = 8;
  config->monitor.aggregate_categories = COAKKA_HTTP_MONITOR_CATEGORY_BIT_LIFECYCLE;
  config->monitor.event_categories = COAKKA_HTTP_MONITOR_CATEGORY_BIT_LIFECYCLE;
  config->server.monitor = &config->monitor;
  return 1;
}
#endif
