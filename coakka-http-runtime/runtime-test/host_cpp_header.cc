#include <coakka/http/host.h>

#include <cstdint>
#include <cstdio>
#include <type_traits>

static_assert(std::is_standard_layout_v<coakka_http_host_configuration_t>);
static_assert(std::is_trivially_copyable_v<coakka_http_host_request_view_t>);

int main() {
  coakka_http_host_service_t *service = nullptr;
  coakka_http_host_configuration_t config;
  coakka_http_host_limits_t effective;
  std::uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  coakka_http_host_configuration_init(&config);
  bool ok = coakka_http_host_abi_version() == COAKKA_HTTP_HOST_ABI_VERSION;
  if (ok) ok = coakka_http_host_service_create(&config, nullptr, 0U, &service,
                                               &failed) == COAKKA_HTTP_HOST_OK;
  if (ok) {
    coakka_http_host_limits_init(&effective);
    ok = coakka_http_host_service_limits(service, &effective) == COAKKA_HTTP_HOST_OK &&
         effective.max_connections > 0U;
  }
  /* Native ownership remains explicit and fallible in C++. Never discard a
   * retained owner through a deleter that silently ignores destruction failure. */
  const bool closed = coakka_http_host_service_destroy(&service) == COAKKA_HTTP_HOST_OK &&
                      service == nullptr;
  std::printf("{\"case\":\"cpp_public_header\",\"ok\":%s,\"cleanup\":%s}\n",
              ok && closed ? "true" : "false", closed ? "true" : "false");
  return ok && closed ? 0 : 1;
}
