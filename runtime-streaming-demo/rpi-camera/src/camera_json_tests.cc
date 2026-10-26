#include "camera_json.h"

#include <cstdint>
#include <iostream>
#include <string>

namespace {

bool expect(bool condition, const char *message) {
  if (!condition) {
    std::cerr << message << '\n';
  }
  return condition;
}

bool parse(std::string_view text, coakka::camera_json::object_t *object) {
  std::string error;
  if (!coakka::camera_json::parse_object(text, object, &error)) {
    std::cerr << "parse failed: " << error << '\n';
    return false;
  }
  return true;
}

} // namespace

int main() {
  using coakka::camera_json::object_t;

  object_t command;
  if (!parse(R"({"id":"cam-\u03c0","command":"resolution.set",)"
             R"("width":6.40e2,"height":480.0,"fps":30,)"
             R"("audio":true,"ignored":{"nested":[1,false,null]}})",
             &command)) {
    return 1;
  }

  std::string id;
  bool audio = false;
  uint32_t width = 0u;
  uint32_t height = 0u;
  uint32_t fps = 0u;
  const std::string expected_id =
      std::string("cam-") + static_cast<char>(0xcf) + static_cast<char>(0x80);
  if (!expect(coakka::camera_json::value_as_string(command.find("id"), &id) &&
                  id == expected_id,
              "unicode string decode failed") ||
      !expect(
          coakka::camera_json::value_as_bool(command.find("audio"), &audio) &&
              audio,
          "boolean decode failed") ||
      !expect(
          coakka::camera_json::value_as_uint32(command.find("width"), &width) &&
              width == 640u,
          "exponent integer decode failed") ||
      !expect(coakka::camera_json::value_as_uint32(command.find("height"),
                                                   &height) &&
                  height == 480u,
              "fractional spelling decode failed") ||
      !expect(coakka::camera_json::value_as_uint32(command.find("fps"), &fps) &&
                  fps == 30u,
              "integer decode failed")) {
    return 1;
  }

  coakka::camera_json::object_writer_t writer;
  writer.add_string("type", "status");
  writer.add_string("error", "quote=\" newline=\n");
  writer.add_bool("ok", false);
  writer.add_uint64("frames", 42u);
  const std::string encoded = writer.finish();
  object_t roundtrip;
  if (!parse(encoded, &roundtrip)) {
    return 1;
  }
  std::string error_text;
  uint32_t frames = 0u;
  if (!expect(coakka::camera_json::value_as_string(roundtrip.find("error"),
                                                   &error_text) &&
                  error_text == "quote=\" newline=\n",
              "writer string escaping failed") ||
      !expect(coakka::camera_json::value_as_uint32(roundtrip.find("frames"),
                                                   &frames) &&
                  frames == 42u,
              "writer integer encoding failed")) {
    return 1;
  }

  std::string parse_error;
  object_t rejected;
  const std::string invalid_utf8 = std::string("{\"id\":\"") +
                                   static_cast<char>(0xc0) +
                                   static_cast<char>(0x80) + "\"}";
  if (!expect(!coakka::camera_json::parse_object(
                  R"({"id":"first","id":"second"})", &rejected, &parse_error),
              "duplicate key was accepted") ||
      !expect(!coakka::camera_json::parse_object(R"({"width":01})", &rejected,
                                                 &parse_error),
              "leading-zero number was accepted") ||
      !expect(!coakka::camera_json::parse_object(invalid_utf8, &rejected,
                                                 &parse_error),
              "invalid UTF-8 was accepted") ||
      !expect(
          !coakka::camera_json::value_as_uint32(command.find("audio"), &width),
          "boolean was accepted as an integer")) {
    return 1;
  }

  object_t fractional;
  if (!parse(R"({"value":1.25,"zero":0,"large":4294967296})", &fractional) ||
      !expect(!coakka::camera_json::value_as_uint32(fractional.find("value"),
                                                    &width),
              "fractional number was accepted as uint32") ||
      !expect(!coakka::camera_json::value_as_uint32(fractional.find("zero"),
                                                    &width),
              "zero was accepted as positive uint32") ||
      !expect(!coakka::camera_json::value_as_uint32(fractional.find("large"),
                                                    &width),
              "overflowing number was accepted as uint32")) {
    return 1;
  }

  return 0;
}
