#include "camera_json.h"

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  constexpr size_t kHarnessInputLimit = 16u * 1024u;
  if (data == nullptr || size == 0u || size > kHarnessInputLimit) {
    return 0;
  }

  const std::string_view input(reinterpret_cast<const char *>(data), size);
  coakka::camera_json::object_t object;
  std::string error;
  if (!coakka::camera_json::parse_object(input, &object, &error)) {
    return 0;
  }

  // Exercise every typed projection without granting the fuzzer an
  // unbounded-output path. Production commands perform the same projections
  // only for a fixed, small set of known field names.
  for (const auto &field : object.fields) {
    std::string text;
    bool boolean = false;
    uint32_t integer = 0u;
    (void)coakka::camera_json::value_as_string(&field.second, &text);
    (void)coakka::camera_json::value_as_bool(&field.second, &boolean);
    (void)coakka::camera_json::value_as_uint32(&field.second, &integer);
  }
  return 0;
}
