#include "camera_json.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <charconv>
#include <limits>

namespace coakka::camera_json {
namespace {

constexpr size_t kMaxInputBytes = 16u * 1024u;
constexpr size_t kMaxFields = 64u;
constexpr size_t kMaxDepth = 8u;
constexpr size_t kMaxDecodedStringBytes = 8u * 1024u;

bool is_continuation(unsigned char value) noexcept {
  return (value & 0xc0u) == 0x80u;
}

bool valid_utf8(std::string_view value) noexcept {
  size_t index = 0u;
  while (index < value.size()) {
    const auto first = static_cast<unsigned char>(value[index]);
    if (first <= 0x7fu) {
      ++index;
      continue;
    }
    if (first >= 0xc2u && first <= 0xdfu) {
      if (index + 1u >= value.size() ||
          !is_continuation(static_cast<unsigned char>(value[index + 1u]))) {
        return false;
      }
      index += 2u;
      continue;
    }
    if (first >= 0xe0u && first <= 0xefu) {
      if (index + 2u >= value.size()) {
        return false;
      }
      const auto second = static_cast<unsigned char>(value[index + 1u]);
      const auto third = static_cast<unsigned char>(value[index + 2u]);
      if (!is_continuation(second) || !is_continuation(third) ||
          (first == 0xe0u && second < 0xa0u) ||
          (first == 0xedu && second >= 0xa0u)) {
        return false;
      }
      index += 3u;
      continue;
    }
    if (first >= 0xf0u && first <= 0xf4u) {
      if (index + 3u >= value.size()) {
        return false;
      }
      const auto second = static_cast<unsigned char>(value[index + 1u]);
      const auto third = static_cast<unsigned char>(value[index + 2u]);
      const auto fourth = static_cast<unsigned char>(value[index + 3u]);
      if (!is_continuation(second) || !is_continuation(third) ||
          !is_continuation(fourth) || (first == 0xf0u && second < 0x90u) ||
          (first == 0xf4u && second >= 0x90u)) {
        return false;
      }
      index += 4u;
      continue;
    }
    return false;
  }
  return true;
}

int hex_value(char value) noexcept {
  if (value >= '0' && value <= '9') {
    return value - '0';
  }
  if (value >= 'a' && value <= 'f') {
    return 10 + value - 'a';
  }
  if (value >= 'A' && value <= 'F') {
    return 10 + value - 'A';
  }
  return -1;
}

bool append_utf8(uint32_t codepoint, std::string *output) {
  if (codepoint <= 0x7fu) {
    output->push_back(static_cast<char>(codepoint));
  } else if (codepoint <= 0x7ffu) {
    output->push_back(static_cast<char>(0xc0u | (codepoint >> 6u)));
    output->push_back(static_cast<char>(0x80u | (codepoint & 0x3fu)));
  } else if (codepoint <= 0xffffu) {
    if (codepoint >= 0xd800u && codepoint <= 0xdfffu) {
      return false;
    }
    output->push_back(static_cast<char>(0xe0u | (codepoint >> 12u)));
    output->push_back(static_cast<char>(0x80u | ((codepoint >> 6u) & 0x3fu)));
    output->push_back(static_cast<char>(0x80u | (codepoint & 0x3fu)));
  } else if (codepoint <= 0x10ffffu) {
    output->push_back(static_cast<char>(0xf0u | (codepoint >> 18u)));
    output->push_back(static_cast<char>(0x80u | ((codepoint >> 12u) & 0x3fu)));
    output->push_back(static_cast<char>(0x80u | ((codepoint >> 6u) & 0x3fu)));
    output->push_back(static_cast<char>(0x80u | (codepoint & 0x3fu)));
  } else {
    return false;
  }
  return output->size() <= kMaxDecodedStringBytes;
}

class parser_t {
public:
  explicit parser_t(std::string_view input) : input_(input) {}

  bool parse(object_t *output, std::string *error) {
    if (output == nullptr || error == nullptr) {
      return false;
    }
    if (input_.empty() || input_.size() > kMaxInputBytes) {
      return fail("JSON input is outside bounds", error);
    }
    output->fields.clear();
    skip_space();
    if (!parse_top_object(output, error)) {
      output->fields.clear();
      return false;
    }
    skip_space();
    if (position_ != input_.size()) {
      output->fields.clear();
      return fail("trailing JSON data", error);
    }
    error->clear();
    return true;
  }

private:
  bool fail(const char *message, std::string *error) const {
    *error = message;
    return false;
  }

  void skip_space() noexcept {
    while (position_ < input_.size()) {
      const char value = input_[position_];
      if (value != ' ' && value != '\t' && value != '\r' && value != '\n') {
        break;
      }
      ++position_;
    }
  }

  bool take(char expected) noexcept {
    if (position_ >= input_.size() || input_[position_] != expected) {
      return false;
    }
    ++position_;
    return true;
  }

  bool parse_hex_quad(uint32_t *output) noexcept {
    if (input_.size() - position_ < 4u) {
      return false;
    }
    uint32_t value = 0u;
    for (size_t index = 0u; index < 4u; ++index) {
      const int digit = hex_value(input_[position_ + index]);
      if (digit < 0) {
        return false;
      }
      value = (value << 4u) | static_cast<uint32_t>(digit);
    }
    position_ += 4u;
    *output = value;
    return true;
  }

  bool parse_string(std::string *output, std::string *error) {
    if (!take('"')) {
      return fail("expected JSON string", error);
    }
    output->clear();
    while (position_ < input_.size()) {
      const auto value = static_cast<unsigned char>(input_[position_++]);
      if (value == '"') {
        if (!valid_utf8(*output)) {
          return fail("JSON string is not valid UTF-8", error);
        }
        return true;
      }
      if (value < 0x20u) {
        return fail("unescaped JSON control byte", error);
      }
      if (value != '\\') {
        output->push_back(static_cast<char>(value));
        if (output->size() > kMaxDecodedStringBytes) {
          return fail("decoded JSON string exceeds bound", error);
        }
        continue;
      }
      if (position_ >= input_.size()) {
        return fail("truncated JSON escape", error);
      }
      const char escaped = input_[position_++];
      switch (escaped) {
      case '"':
      case '\\':
      case '/':
        output->push_back(escaped);
        break;
      case 'b':
        output->push_back('\b');
        break;
      case 'f':
        output->push_back('\f');
        break;
      case 'n':
        output->push_back('\n');
        break;
      case 'r':
        output->push_back('\r');
        break;
      case 't':
        output->push_back('\t');
        break;
      case 'u': {
        uint32_t codepoint = 0u;
        if (!parse_hex_quad(&codepoint)) {
          return fail("invalid JSON unicode escape", error);
        }
        if (codepoint >= 0xd800u && codepoint <= 0xdbffu) {
          if (input_.size() - position_ < 6u || input_[position_] != '\\' ||
              input_[position_ + 1u] != 'u') {
            return fail("missing JSON low surrogate", error);
          }
          position_ += 2u;
          uint32_t low = 0u;
          if (!parse_hex_quad(&low) || low < 0xdc00u || low > 0xdfffu) {
            return fail("invalid JSON low surrogate", error);
          }
          codepoint =
              0x10000u + ((codepoint - 0xd800u) << 10u) + (low - 0xdc00u);
        } else if (codepoint >= 0xdc00u && codepoint <= 0xdfffu) {
          return fail("unexpected JSON low surrogate", error);
        }
        if (!append_utf8(codepoint, output)) {
          return fail("JSON codepoint is outside bounds", error);
        }
        break;
      }
      default:
        return fail("invalid JSON escape", error);
      }
      if (output->size() > kMaxDecodedStringBytes) {
        return fail("decoded JSON string exceeds bound", error);
      }
    }
    return fail("unterminated JSON string", error);
  }

  bool parse_number(std::string *output, std::string *error) {
    const size_t begin = position_;
    if (position_ < input_.size() && input_[position_] == '-') {
      ++position_;
    }
    if (position_ >= input_.size()) {
      return fail("truncated JSON number", error);
    }
    if (input_[position_] == '0') {
      ++position_;
      if (position_ < input_.size() &&
          std::isdigit(static_cast<unsigned char>(input_[position_])) != 0) {
        return fail("JSON number has a leading zero", error);
      }
    } else if (input_[position_] >= '1' && input_[position_] <= '9') {
      do {
        ++position_;
      } while (position_ < input_.size() &&
               std::isdigit(static_cast<unsigned char>(input_[position_])) !=
                   0);
    } else {
      return fail("invalid JSON number", error);
    }
    if (position_ < input_.size() && input_[position_] == '.') {
      ++position_;
      const size_t fraction = position_;
      while (position_ < input_.size() &&
             std::isdigit(static_cast<unsigned char>(input_[position_])) != 0) {
        ++position_;
      }
      if (position_ == fraction) {
        return fail("empty JSON number fraction", error);
      }
    }
    if (position_ < input_.size() &&
        (input_[position_] == 'e' || input_[position_] == 'E')) {
      ++position_;
      if (position_ < input_.size() &&
          (input_[position_] == '+' || input_[position_] == '-')) {
        ++position_;
      }
      const size_t exponent = position_;
      while (position_ < input_.size() &&
             std::isdigit(static_cast<unsigned char>(input_[position_])) != 0) {
        ++position_;
      }
      if (position_ == exponent) {
        return fail("empty JSON number exponent", error);
      }
    }
    output->assign(input_.substr(begin, position_ - begin));
    return true;
  }

  bool parse_literal(std::string_view literal) noexcept {
    if (input_.substr(position_, literal.size()) != literal) {
      return false;
    }
    position_ += literal.size();
    return true;
  }

  bool skip_array(size_t depth, std::string *error) {
    if (depth > kMaxDepth || !take('[')) {
      return fail("JSON nesting exceeds bound", error);
    }
    skip_space();
    if (take(']')) {
      return true;
    }
    size_t entries = 0u;
    for (;;) {
      if (++entries > kMaxFields || !skip_value(depth, error)) {
        return entries > kMaxFields
                   ? fail("JSON array entry count exceeds bound", error)
                   : false;
      }
      skip_space();
      if (take(']')) {
        return true;
      }
      if (!take(',')) {
        return fail("expected JSON array separator", error);
      }
      skip_space();
    }
  }

  bool skip_object(size_t depth, std::string *error) {
    if (depth > kMaxDepth || !take('{')) {
      return fail("JSON nesting exceeds bound", error);
    }
    skip_space();
    if (take('}')) {
      return true;
    }
    size_t fields = 0u;
    for (;;) {
      std::string name;
      if (++fields > kMaxFields || !parse_string(&name, error)) {
        return fields > kMaxFields
                   ? fail("JSON object field count exceeds bound", error)
                   : false;
      }
      skip_space();
      if (!take(':')) {
        return fail("expected JSON name separator", error);
      }
      skip_space();
      if (!skip_value(depth, error)) {
        return false;
      }
      skip_space();
      if (take('}')) {
        return true;
      }
      if (!take(',')) {
        return fail("expected JSON object separator", error);
      }
      skip_space();
    }
  }

  bool skip_value(size_t parent_depth, std::string *error) {
    skip_space();
    if (position_ >= input_.size()) {
      return fail("missing JSON value", error);
    }
    const char value = input_[position_];
    if (value == '"') {
      std::string decoded;
      return parse_string(&decoded, error);
    }
    if (value == '{') {
      return skip_object(parent_depth + 1u, error);
    }
    if (value == '[') {
      return skip_array(parent_depth + 1u, error);
    }
    if (value == 't') {
      return parse_literal("true") || fail("invalid JSON literal", error);
    }
    if (value == 'f') {
      return parse_literal("false") || fail("invalid JSON literal", error);
    }
    if (value == 'n') {
      return parse_literal("null") || fail("invalid JSON literal", error);
    }
    std::string number;
    return parse_number(&number, error);
  }

  bool parse_top_value(value_t *output, std::string *error) {
    skip_space();
    if (position_ >= input_.size()) {
      return fail("missing JSON value", error);
    }
    const char value = input_[position_];
    if (value == '"') {
      output->kind = value_kind_t::string;
      return parse_string(&output->text, error);
    }
    if (value == 't' || value == 'f') {
      output->kind = value_kind_t::boolean;
      output->boolean = value == 't';
      const std::string_view literal = output->boolean ? "true" : "false";
      return parse_literal(literal) || fail("invalid JSON literal", error);
    }
    if (value == 'n') {
      output->kind = value_kind_t::null_value;
      return parse_literal("null") || fail("invalid JSON literal", error);
    }
    if (value == '{' || value == '[') {
      output->kind = value_kind_t::composite;
      return skip_value(0u, error);
    }
    output->kind = value_kind_t::number;
    return parse_number(&output->text, error);
  }

  bool parse_top_object(object_t *output, std::string *error) {
    if (!take('{')) {
      return fail("expected top-level JSON object", error);
    }
    skip_space();
    if (take('}')) {
      return true;
    }
    for (;;) {
      if (output->fields.size() >= kMaxFields) {
        return fail("JSON object field count exceeds bound", error);
      }
      std::string name;
      if (!parse_string(&name, error)) {
        return false;
      }
      if (std::any_of(
              output->fields.begin(), output->fields.end(),
              [&name](const auto &field) { return field.first == name; })) {
        return fail("duplicate top-level JSON key", error);
      }
      skip_space();
      if (!take(':')) {
        return fail("expected JSON name separator", error);
      }
      value_t value;
      if (!parse_top_value(&value, error)) {
        return false;
      }
      output->fields.emplace_back(std::move(name), std::move(value));
      skip_space();
      if (take('}')) {
        return true;
      }
      if (!take(',')) {
        return fail("expected JSON object separator", error);
      }
      skip_space();
    }
  }

  std::string_view input_;
  size_t position_ = 0u;
};

void append_escaped(std::string_view input, std::string *output) {
  static constexpr std::array<char, 16u> kHex = {'0', '1', '2', '3', '4', '5',
                                                 '6', '7', '8', '9', 'a', 'b',
                                                 'c', 'd', 'e', 'f'};
  const bool input_is_utf8 = valid_utf8(input);
  output->push_back('"');
  for (const unsigned char value : input) {
    switch (value) {
    case '"':
      output->append("\\\"");
      break;
    case '\\':
      output->append("\\\\");
      break;
    case '\b':
      output->append("\\b");
      break;
    case '\f':
      output->append("\\f");
      break;
    case '\n':
      output->append("\\n");
      break;
    case '\r':
      output->append("\\r");
      break;
    case '\t':
      output->append("\\t");
      break;
    default:
      if (value < 0x20u || (!input_is_utf8 && value >= 0x80u)) {
        output->append("\\u00");
        output->push_back(kHex[value >> 4u]);
        output->push_back(kHex[value & 0x0fu]);
      } else {
        output->push_back(static_cast<char>(value));
      }
      break;
    }
  }
  output->push_back('"');
}

} // namespace

const value_t *object_t::find(std::string_view name) const noexcept {
  const auto found =
      std::find_if(fields.begin(), fields.end(),
                   [name](const auto &field) { return field.first == name; });
  return found == fields.end() ? nullptr : &found->second;
}

bool parse_object(std::string_view input, object_t *output,
                  std::string *error) {
  return parser_t(input).parse(output, error);
}

bool value_as_string(const value_t *value, std::string *output) {
  if (value == nullptr || output == nullptr ||
      value->kind != value_kind_t::string) {
    return false;
  }
  *output = value->text;
  return true;
}

bool value_as_bool(const value_t *value, bool *output) noexcept {
  if (value == nullptr || output == nullptr ||
      value->kind != value_kind_t::boolean) {
    return false;
  }
  *output = value->boolean;
  return true;
}

bool value_as_uint32(const value_t *value, uint32_t *output) noexcept {
  if (value == nullptr || output == nullptr ||
      value->kind != value_kind_t::number || value->text.empty() ||
      value->text.front() == '-') {
    return false;
  }

  const std::string_view text = value->text;
  const size_t exponent_marker = text.find_first_of("eE");
  const std::string_view significand = text.substr(0u, exponent_marker);
  int exponent = 0;
  if (exponent_marker != std::string_view::npos) {
    std::string_view exponent_text = text.substr(exponent_marker + 1u);
    bool negative = false;
    if (!exponent_text.empty() &&
        (exponent_text.front() == '+' || exponent_text.front() == '-')) {
      negative = exponent_text.front() == '-';
      exponent_text.remove_prefix(1u);
    }
    uint32_t magnitude = 0u;
    const auto parsed =
        std::from_chars(exponent_text.data(),
                        exponent_text.data() + exponent_text.size(), magnitude);
    if (parsed.ec != std::errc{} ||
        parsed.ptr != exponent_text.data() + exponent_text.size() ||
        magnitude > 100000u) {
      return false;
    }
    exponent =
        negative ? -static_cast<int>(magnitude) : static_cast<int>(magnitude);
  }

  const size_t decimal = significand.find('.');
  const size_t fraction_digits = decimal == std::string_view::npos
                                     ? 0u
                                     : significand.size() - decimal - 1u;
  std::string digits;
  digits.reserve(significand.size());
  for (const char character : significand) {
    if (character != '.') {
      digits.push_back(character);
    }
  }
  const size_t first_nonzero = digits.find_first_not_of('0');
  if (first_nonzero == std::string::npos) {
    return false;
  }
  digits.erase(0u, first_nonzero);

  const int64_t scale =
      static_cast<int64_t>(fraction_digits) - static_cast<int64_t>(exponent);
  if (scale > 0) {
    const auto removed = static_cast<uint64_t>(scale);
    if (removed >= digits.size()) {
      return false;
    }
    const size_t keep = digits.size() - static_cast<size_t>(removed);
    if (digits.find_first_not_of('0', keep) != std::string::npos) {
      return false;
    }
    digits.resize(keep);
  } else if (scale < 0) {
    const auto appended = static_cast<uint64_t>(-scale);
    if (appended > 10u || digits.size() + appended > 10u) {
      return false;
    }
    digits.append(static_cast<size_t>(appended), '0');
  }
  if (digits.size() > 10u) {
    return false;
  }
  uint64_t parsed_value = 0u;
  const auto parsed = std::from_chars(
      digits.data(), digits.data() + digits.size(), parsed_value);
  if (parsed.ec != std::errc{} || parsed.ptr != digits.data() + digits.size() ||
      parsed_value == 0u ||
      parsed_value > std::numeric_limits<uint32_t>::max()) {
    return false;
  }
  *output = static_cast<uint32_t>(parsed_value);
  return true;
}

object_writer_t::object_writer_t(size_t reserve_bytes) {
  output_.reserve(reserve_bytes);
  output_.push_back('{');
}

void object_writer_t::add_name(std::string_view name) {
  if (finished_) {
    return;
  }
  if (!first_) {
    output_.push_back(',');
  }
  first_ = false;
  append_escaped(name, &output_);
  output_.push_back(':');
}

void object_writer_t::add_string(std::string_view name,
                                 std::string_view value) {
  add_name(name);
  if (!finished_) {
    append_escaped(value, &output_);
  }
}

void object_writer_t::add_bool(std::string_view name, bool value) {
  add_name(name);
  if (!finished_) {
    output_.append(value ? "true" : "false");
  }
}

void object_writer_t::add_uint64(std::string_view name, uint64_t value) {
  add_name(name);
  if (finished_) {
    return;
  }
  std::array<char, 32u> buffer{};
  const auto converted =
      std::to_chars(buffer.data(), buffer.data() + buffer.size(), value);
  if (converted.ec == std::errc{}) {
    output_.append(buffer.data(), converted.ptr);
  }
}

std::string object_writer_t::finish() {
  if (!finished_) {
    output_.push_back('}');
    finished_ = true;
  }
  return output_;
}

} // namespace coakka::camera_json
