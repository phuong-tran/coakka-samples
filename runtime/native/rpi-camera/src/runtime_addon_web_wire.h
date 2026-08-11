#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

namespace coakka::runtime_addon_web {

struct http_request_t {
    std::string method;
    std::string path;
    std::unordered_map<std::string, std::string> headers;
    std::string body;
};

using http_headers_t = std::vector<std::pair<std::string, std::string>>;

std::string make_http_response_text(int status_code,
                                    const http_headers_t& headers,
                                    const std::string& body);

std::string make_http_response_text(int status_code,
                                    const std::string& content_type,
                                    const std::string& body);

bool header_contains_token(const std::unordered_map<std::string, std::string>& headers,
                           const std::string& key,
                           const std::string& token);

std::string request_path_without_query(std::string_view request_path);

bool try_extract_http_request_bytes(const std::string& buffer,
                                    std::size_t* out_size,
                                    std::string* out_error);

bool parse_request_from_text(const std::string& text,
                             http_request_t* out_request,
                             std::string* out_error);

std::string websocket_frame(std::uint8_t opcode, std::string_view payload);

std::string make_websocket_upgrade_response(const std::string& sec_websocket_key);

}  // namespace coakka::runtime_addon_web
