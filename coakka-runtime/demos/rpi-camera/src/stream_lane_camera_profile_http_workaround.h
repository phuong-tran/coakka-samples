#ifndef COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_PROFILE_HTTP_WORKAROUND_H
#define COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_PROFILE_HTTP_WORKAROUND_H

#include <cstdint>
#include <string>
#include <string_view>

struct coakka_v2_camera_profile_http_workaround_request_t {
  std::string_view host;
  uint16_t port = 0u;
  std::string_view authorization_token;
  uint32_t width = 0u;
  uint32_t height = 0u;
  uint32_t fps = 0u;
  uint32_t timeout_ms = 0u;
};

// Temporary compatibility boundary for CoAkka HTTP Runtime 1.1.2, whose
// outbound provider mistakes non-loopback numeric IP targets for DNS names.
// Delete this module and its call site after the camera sample's minimum HTTP
// Runtime version contains the numeric-target provider fix.
[[nodiscard]] bool coakka_v2_camera_profile_http_workaround_required(
    std::string_view host) noexcept;

// Sends the camera sample's one fixed, bounded plaintext POST. The caller owns
// authorization and invokes this only on its control path, never an HTTP or
// Stream Lane event-loop thread.
[[nodiscard]] bool coakka_v2_camera_profile_http_workaround_send(
    const coakka_v2_camera_profile_http_workaround_request_t &request,
    std::string *error);

#endif
