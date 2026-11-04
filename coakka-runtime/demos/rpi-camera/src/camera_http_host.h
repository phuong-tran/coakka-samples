#ifndef COAKKA_SAMPLES_RPI_CAMERA_HTTP_HOST_H
#define COAKKA_SAMPLES_RPI_CAMERA_HTTP_HOST_H

#include <coakka/http/host.h>

#include <array>
#include <cctype>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace coakka::camera_http {

inline coakka_http_host_bytes_t bytes(std::string_view value) noexcept {
  return {reinterpret_cast<const uint8_t *>(value.data()), value.size()};
}

inline std::string_view view(coakka_http_host_bytes_t value) noexcept {
  return {reinterpret_cast<const char *>(value.data), value.size};
}

inline bool ascii_case_equal(std::string_view left,
                             std::string_view right) noexcept {
  if (left.size() != right.size()) {
    return false;
  }
  for (size_t index = 0u; index < left.size(); ++index) {
    const auto left_byte = static_cast<unsigned char>(left[index]);
    const auto right_byte = static_cast<unsigned char>(right[index]);
    if (std::tolower(left_byte) != std::tolower(right_byte)) {
      return false;
    }
  }
  return true;
}

// Returned text borrows the request event and must not outlive its lease.
inline std::optional<std::string_view>
header_value(const coakka_http_host_request_event_t &event,
             std::string_view name) noexcept {
  const uint32_t count = coakka_http_host_request_header_count(&event);
  for (uint32_t index = 0u; index < count; ++index) {
    coakka_http_host_header_t header{};
    if (coakka_http_host_request_header(&event, index, &header) != 0u &&
        ascii_case_equal(view(header.name), name)) {
      return view(header.value);
    }
  }
  return std::nullopt;
}

inline std::string status_text(coakka_http_host_status_t status) {
  const char *name = coakka_http_host_status_name(status);
  return name != nullptr ? std::string(name) : "unknown";
}

inline bool same_exchange(coakka_http_host_exchange_t left,
                          coakka_http_host_exchange_t right) noexcept {
  return left.slot == right.slot && left.generation == right.generation;
}

inline bool same_websocket(coakka_http_host_websocket_t left,
                           coakka_http_host_websocket_t right) noexcept {
  return left.slot == right.slot && left.generation == right.generation;
}

inline coakka_http_host_status_t respond(coakka_http_host_service_t *service,
                                         coakka_http_host_exchange_t exchange,
                                         uint32_t status_code,
                                         std::string_view content_type,
                                         std::string_view body) noexcept {
  const std::array<coakka_http_host_header_t, 2u> headers{{
      {bytes("content-type"), bytes(content_type)},
      {bytes("cache-control"), bytes("no-store")},
  }};
  coakka_http_host_response_t response;
  coakka_http_host_response_init(&response);
  response.status_code = status_code;
  response.headers = headers.data();
  response.header_count = static_cast<uint32_t>(headers.size());
  response.body = bytes(body);
  return coakka_http_host_respond(service, exchange, &response);
}

inline std::string startup_error(coakka_http_host_status_t status,
                                 const coakka_http_host_issue_t &issue) {
  std::string detail = status_text(status);
  if (issue.detail[0] != '\0') {
    detail += ": ";
    detail += issue.detail;
  }
  return detail;
}

} // namespace coakka::camera_http

#endif
