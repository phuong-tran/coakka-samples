// Include the private owner once: exercise its real serialized status and
// callback handoff without adding a product API solely for white-box access.
#include "stream_lane_camera_web_gateway.cc"

#include <cstdlib>
#include <iostream>

namespace {
void require(bool condition, const char *detail) {
  if (!condition) {
    std::cerr << detail << '\n';
    std::exit(1);
  }
}

coakka::camera_json::object_t status(const Gateway &gateway) {
  coakka::camera_json::object_t value;
  std::string error;
  require(coakka::camera_json::parse_object(
              gateway.status_json("test", true, {}), &value, &error),
          "actual status JSON must parse");
  require(value.find("profile_fps") == nullptr,
          "status must not claim observed frame rate");
  return value;
}

void expect(const Gateway &gateway, const char *state, uint32_t width,
            uint32_t height, uint32_t requested_fps) {
  const auto value = status(gateway);
  std::string actual_state;
  uint32_t actual_width = 0u;
  uint32_t actual_height = 0u;
  uint32_t actual_fps = 0u;
  // The control parser deliberately admits positive profile inputs only;
  // status additionally uses literal zero for an unknown or absent fact.
  const auto read_status_uint = [](const coakka::camera_json::value_t *item,
                                   uint32_t *out) {
    if (item != nullptr &&
        item->kind == coakka::camera_json::value_kind_t::number &&
        item->text == "0") {
      *out = 0u;
      return true;
    }
    return coakka::camera_json::value_as_uint32(item, out);
  };
  require(coakka::camera_json::value_as_string(value.find("profile_state"),
                                             &actual_state) &&
              read_status_uint(value.find("profile_width"), &actual_width) &&
              read_status_uint(value.find("profile_height"), &actual_height) &&
              read_status_uint(
                  value.find("profile_requested_fps"), &actual_fps),
          "status must carry typed requested/confirmed fields");
  require(actual_state == state && actual_width == width &&
              actual_height == height && actual_fps == requested_fps,
          "status differs from owner-issued profile facts");
}
} // namespace

int main() {
  coakka_v2_camera_web_gateway_config_t config;
  config.max_frame_bytes = 4096u;
  config.fps = 30u; // Recorder preference must not become observed camera facts.
  Gateway gateway(config);
  expect(gateway, "unknown", 0u, 0u, 0u);
  constexpr uint8_t jpeg[] = {0xffu, 0xd8u, 0xffu, 0xc0u, 0x00u, 0x07u,
                              0x08u, 0x01u, 0xe0u, 0x02u, 0x80u, 0xffu, 0xd9u};
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "unknown", 0u, 0u, 0u);

  gateway.requested_width = 640u;
  gateway.requested_height = 480u;
  gateway.requested_fps = 30u;
  gateway.profile_request_pending = true;
  gateway.profile_state = "switching";
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "switching", 0u, 0u, 30u);
  gateway.profile_request_taken = true;
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "switching", 0u, 0u, 30u);

  gateway.profile_state = "confirming"; // Existing Pi queue-admission ACK.
  constexpr uint8_t malformed[] = {0xffu, 0xd8u, 0xffu, 0xd9u};
  gateway.confirm_profile_frame(malformed, sizeof(malformed));
  expect(gateway, "confirming", 0u, 0u, 30u);
  gateway.requested_width = 800u;
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "confirming", 0u, 0u, 30u);
  gateway.requested_width = 640u;
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "active", 640u, 480u, 30u);
  require(!gateway.profile_request_pending && !gateway.profile_request_taken,
          "confirmed request must retire its handoff");

  gateway.requested_width = 1280u;
  gateway.requested_height = 720u;
  gateway.profile_request_pending = true;
  gateway.profile_request_taken = true;
  gateway.profile_state = "confirming";
  gateway.profile_requested_at = std::chrono::steady_clock::now() -
                                 kProfileConfirmationTimeout;
  gateway.expire_profile_request();
  expect(gateway, "failed", 640u, 480u, 30u);
  gateway.confirm_profile_frame(jpeg, sizeof(jpeg));
  expect(gateway, "failed", 640u, 480u, 30u);
  return 0;
}
