#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace coakka::camera_json {

enum class value_kind_t {
  string,
  number,
  boolean,
  null_value,
  composite,
};

struct value_t {
  value_kind_t kind = value_kind_t::null_value;
  std::string text;
  bool boolean = false;
};

struct object_t {
  std::vector<std::pair<std::string, value_t>> fields;

  const value_t *find(std::string_view name) const noexcept;
};

// Parses one bounded JSON object. Top-level duplicate keys and malformed UTF-8
// fail closed. Nested values are syntax-checked but retained only as the
// `composite` kind because the camera control protocol has no nested fields.
bool parse_object(std::string_view input, object_t *output, std::string *error);

bool value_as_string(const value_t *value, std::string *output);
bool value_as_bool(const value_t *value, bool *output) noexcept;
bool value_as_uint32(const value_t *value, uint32_t *output) noexcept;

class object_writer_t {
public:
  explicit object_writer_t(size_t reserve_bytes = 256u);

  void add_string(std::string_view name, std::string_view value);
  void add_bool(std::string_view name, bool value);
  void add_uint64(std::string_view name, uint64_t value);
  std::string finish();

private:
  void add_name(std::string_view name);

  std::string output_;
  bool first_ = true;
  bool finished_ = false;
};

} // namespace coakka::camera_json
