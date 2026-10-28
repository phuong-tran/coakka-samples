#include "runtime_addon_web_wire.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

namespace coakka::runtime_addon_web {
namespace {

std::string trim_ascii_whitespace(std::string value) {
    while (!value.empty() &&
           (value.back() == '\n' || value.back() == '\r' || value.back() == ' ' ||
            value.back() == '\t')) {
        value.pop_back();
    }

    std::size_t start = 0u;
    while (start < value.size() &&
           (value[start] == '\n' || value[start] == '\r' || value[start] == ' ' ||
            value[start] == '\t')) {
        start += 1u;
    }
    if (start == 0u) {
        return value;
    }
    return value.substr(start);
}

std::string lower_ascii(std::string value) {
    for (char& ch : value) {
        if (ch >= 'A' && ch <= 'Z') {
            ch = static_cast<char>(ch - 'A' + 'a');
        }
    }
    return value;
}

std::string status_text(int status_code) {
    switch (status_code) {
        case 101: return "Switching Protocols";
        case 200: return "OK";
        case 202: return "Accepted";
        case 400: return "Bad Request";
        case 403: return "Forbidden";
        case 404: return "Not Found";
        case 405: return "Method Not Allowed";
        case 409: return "Conflict";
        case 426: return "Upgrade Required";
        case 500: return "Internal Server Error";
        case 503: return "Service Unavailable";
        default: return "Unknown";
    }
}

std::string base64_encode_bytes(const std::uint8_t* data, std::size_t len) {
    static constexpr char k_table[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    std::size_t index = 0u;
    out.reserve(((len + 2u) / 3u) * 4u);

    while (index + 3u <= len) {
        const std::uint32_t chunk =
            (static_cast<std::uint32_t>(data[index]) << 16u) |
            (static_cast<std::uint32_t>(data[index + 1u]) << 8u) |
            static_cast<std::uint32_t>(data[index + 2u]);
        out.push_back(k_table[(chunk >> 18u) & 0x3fu]);
        out.push_back(k_table[(chunk >> 12u) & 0x3fu]);
        out.push_back(k_table[(chunk >> 6u) & 0x3fu]);
        out.push_back(k_table[chunk & 0x3fu]);
        index += 3u;
    }

    if (index < len) {
        const std::uint32_t first = static_cast<std::uint32_t>(data[index]);
        const std::uint32_t second =
            (index + 1u < len) ? static_cast<std::uint32_t>(data[index + 1u]) : 0u;
        const std::uint32_t chunk = (first << 16u) | (second << 8u);
        out.push_back(k_table[(chunk >> 18u) & 0x3fu]);
        out.push_back(k_table[(chunk >> 12u) & 0x3fu]);
        out.push_back(index + 1u < len ? k_table[(chunk >> 6u) & 0x3fu] : '=');
        out.push_back('=');
    }

    return out;
}

std::array<std::uint8_t, 20> sha1_digest(std::string_view text) {
    std::uint64_t bit_len = static_cast<std::uint64_t>(text.size()) * 8u;
    std::vector<std::uint8_t> bytes(text.begin(), text.end());
    bytes.push_back(0x80u);
    while ((bytes.size() % 64u) != 56u) {
        bytes.push_back(0u);
    }
    for (int shift = 56; shift >= 0; shift -= 8) {
        bytes.push_back(static_cast<std::uint8_t>((bit_len >> shift) & 0xffu));
    }

    std::uint32_t h0 = 0x67452301u;
    std::uint32_t h1 = 0xefcdab89u;
    std::uint32_t h2 = 0x98badcfeu;
    std::uint32_t h3 = 0x10325476u;
    std::uint32_t h4 = 0xc3d2e1f0u;

    const auto rotl = [](std::uint32_t value, int bits) -> std::uint32_t {
        return (value << bits) | (value >> (32 - bits));
    };

    for (std::size_t offset = 0u; offset < bytes.size(); offset += 64u) {
        std::array<std::uint32_t, 80> w{};
        for (std::size_t i = 0u; i < 16u; ++i) {
            const std::size_t index = offset + (i * 4u);
            w[i] = (static_cast<std::uint32_t>(bytes[index]) << 24u) |
                   (static_cast<std::uint32_t>(bytes[index + 1u]) << 16u) |
                   (static_cast<std::uint32_t>(bytes[index + 2u]) << 8u) |
                   static_cast<std::uint32_t>(bytes[index + 3u]);
        }
        for (std::size_t i = 16u; i < 80u; ++i) {
            w[i] = rotl(w[i - 3u] ^ w[i - 8u] ^ w[i - 14u] ^ w[i - 16u], 1);
        }

        std::uint32_t a = h0;
        std::uint32_t b = h1;
        std::uint32_t c = h2;
        std::uint32_t d = h3;
        std::uint32_t e = h4;

        for (std::size_t i = 0u; i < 80u; ++i) {
            std::uint32_t f = 0u;
            std::uint32_t k = 0u;
            if (i < 20u) {
                f = (b & c) | ((~b) & d);
                k = 0x5a827999u;
            } else if (i < 40u) {
                f = b ^ c ^ d;
                k = 0x6ed9eba1u;
            } else if (i < 60u) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8f1bbcdcu;
            } else {
                f = b ^ c ^ d;
                k = 0xca62c1d6u;
            }
            const std::uint32_t temp = rotl(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = rotl(b, 30);
            b = a;
            a = temp;
        }

        h0 += a;
        h1 += b;
        h2 += c;
        h3 += d;
        h4 += e;
    }

    return {
        static_cast<std::uint8_t>((h0 >> 24u) & 0xffu),
        static_cast<std::uint8_t>((h0 >> 16u) & 0xffu),
        static_cast<std::uint8_t>((h0 >> 8u) & 0xffu),
        static_cast<std::uint8_t>(h0 & 0xffu),
        static_cast<std::uint8_t>((h1 >> 24u) & 0xffu),
        static_cast<std::uint8_t>((h1 >> 16u) & 0xffu),
        static_cast<std::uint8_t>((h1 >> 8u) & 0xffu),
        static_cast<std::uint8_t>(h1 & 0xffu),
        static_cast<std::uint8_t>((h2 >> 24u) & 0xffu),
        static_cast<std::uint8_t>((h2 >> 16u) & 0xffu),
        static_cast<std::uint8_t>((h2 >> 8u) & 0xffu),
        static_cast<std::uint8_t>(h2 & 0xffu),
        static_cast<std::uint8_t>((h3 >> 24u) & 0xffu),
        static_cast<std::uint8_t>((h3 >> 16u) & 0xffu),
        static_cast<std::uint8_t>((h3 >> 8u) & 0xffu),
        static_cast<std::uint8_t>(h3 & 0xffu),
        static_cast<std::uint8_t>((h4 >> 24u) & 0xffu),
        static_cast<std::uint8_t>((h4 >> 16u) & 0xffu),
        static_cast<std::uint8_t>((h4 >> 8u) & 0xffu),
        static_cast<std::uint8_t>(h4 & 0xffu),
    };
}

std::string websocket_accept_key(const std::string& sec_websocket_key) {
    static constexpr std::string_view k_guid =
        "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    const std::string source = sec_websocket_key + std::string(k_guid);
    const auto digest = sha1_digest(source);
    return base64_encode_bytes(digest.data(), digest.size());
}

}  // namespace

std::string make_http_response_text(int status_code,
                                    const http_headers_t& headers,
                                    const std::string& body) {
    std::ostringstream response;
    response << "HTTP/1.1 " << status_code << " " << status_text(status_code) << "\r\n";
    bool has_content_length = false;
    bool has_connection = false;
    for (const auto& [key, value] : headers) {
        if (lower_ascii(key) == "content-length") {
            has_content_length = true;
        } else if (lower_ascii(key) == "connection") {
            has_connection = true;
        }
        response << key << ": " << value << "\r\n";
    }
    if (!has_content_length) {
        response << "Content-Length: " << body.size() << "\r\n";
    }
    if (!has_connection) {
        response << "Connection: close\r\n";
    }
    response << "\r\n" << body;
    return response.str();
}

std::string make_http_response_text(int status_code,
                                    const std::string& content_type,
                                    const std::string& body) {
    return make_http_response_text(
        status_code,
        {
            {"Content-Type", content_type},
            {"Cache-Control", "no-store"},
        },
        body);
}

bool header_contains_token(const std::unordered_map<std::string, std::string>& headers,
                           const std::string& key,
                           const std::string& token) {
    const auto it = headers.find(key);
    if (it == headers.end()) {
        return false;
    }
    return lower_ascii(it->second).find(lower_ascii(token)) != std::string::npos;
}

std::string request_path_without_query(std::string_view request_path) {
    const std::size_t query_pos = request_path.find_first_of("?#");
    return std::string(request_path.substr(0u, query_pos));
}

bool try_extract_http_request_bytes(const std::string& buffer,
                                    std::size_t* out_size,
                                    std::string* out_error) {
    const std::size_t header_end = buffer.find("\r\n\r\n");
    if (header_end == std::string::npos) {
        if (buffer.size() > 1024u * 1024u) {
            *out_error = "request headers too large";
            return true;
        }
        return false;
    }

    std::size_t content_length = 0u;
    std::istringstream stream(buffer.substr(0u, header_end));
    std::string line;
    while (std::getline(stream, line)) {
        line = trim_ascii_whitespace(line);
        if (line.empty()) {
            continue;
        }
        const std::size_t colon = line.find(':');
        if (colon == std::string::npos) {
            continue;
        }
        const std::string key = lower_ascii(trim_ascii_whitespace(line.substr(0u, colon)));
        const std::string value = trim_ascii_whitespace(line.substr(colon + 1u));
        if (key == "transfer-encoding" && !value.empty()) {
            *out_error = "transfer-encoding is unsupported";
            return true;
        }
        if (key == "content-length") {
            try {
                content_length = static_cast<std::size_t>(std::stoul(value));
            } catch (...) {
                *out_error = "invalid content-length";
                return true;
            }
        }
    }

    const std::size_t total_bytes = header_end + 4u + content_length;
    if (buffer.size() > 1024u * 1024u) {
        *out_error = "request body too large";
        return true;
    }
    if (buffer.size() < total_bytes) {
        return false;
    }
    *out_size = total_bytes;
    return true;
}

bool parse_request_from_text(const std::string& text,
                             http_request_t* out_request,
                             std::string* out_error) {
    const std::size_t header_end = text.find("\r\n\r\n");
    if (header_end == std::string::npos) {
        *out_error = "incomplete http headers";
        return false;
    }

    std::istringstream stream(text.substr(0u, header_end));
    std::string request_line;
    if (!std::getline(stream, request_line)) {
        *out_error = "missing request line";
        return false;
    }
    request_line = trim_ascii_whitespace(request_line);

    {
        std::istringstream line(request_line);
        if (!(line >> out_request->method >> out_request->path)) {
            *out_error = "invalid request line";
            return false;
        }
    }

    out_request->headers.clear();
    std::string header_line;
    while (std::getline(stream, header_line)) {
        header_line = trim_ascii_whitespace(header_line);
        if (header_line.empty()) {
            continue;
        }
        const std::size_t colon = header_line.find(':');
        if (colon == std::string::npos) {
            *out_error = "invalid header line";
            return false;
        }
        out_request->headers[lower_ascii(trim_ascii_whitespace(header_line.substr(0u, colon)))] =
            trim_ascii_whitespace(header_line.substr(colon + 1u));
    }

    std::size_t content_length = 0u;
    const auto content_length_it = out_request->headers.find("content-length");
    if (content_length_it != out_request->headers.end()) {
        try {
            content_length = static_cast<std::size_t>(std::stoul(content_length_it->second));
        } catch (...) {
            *out_error = "invalid content-length";
            return false;
        }
    }

    out_request->body = text.substr(header_end + 4u);
    if (out_request->body.size() < content_length) {
        *out_error = "incomplete request body";
        return false;
    }
    if (out_request->body.size() > content_length) {
        out_request->body.resize(content_length);
    }
    return true;
}

std::string websocket_frame(std::uint8_t opcode, std::string_view payload) {
    std::string frame;
    frame.reserve(payload.size() + 16u);
    frame.push_back(static_cast<char>(0x80u | (opcode & 0x0fu)));
    if (payload.size() < 126u) {
        frame.push_back(static_cast<char>(payload.size()));
    } else if (payload.size() <= 0xffffu) {
        frame.push_back(static_cast<char>(126u));
        frame.push_back(static_cast<char>((payload.size() >> 8u) & 0xffu));
        frame.push_back(static_cast<char>(payload.size() & 0xffu));
    } else {
        frame.push_back(static_cast<char>(127u));
        for (int shift = 56; shift >= 0; shift -= 8) {
            frame.push_back(static_cast<char>((payload.size() >> shift) & 0xffu));
        }
    }
    frame.append(payload);
    return frame;
}

std::string make_websocket_upgrade_response(const std::string& sec_websocket_key) {
    std::ostringstream response;
    response << "HTTP/1.1 101 " << status_text(101) << "\r\n"
             << "Upgrade: websocket\r\n"
             << "Connection: Upgrade\r\n"
             << "Sec-WebSocket-Accept: " << websocket_accept_key(sec_websocket_key)
             << "\r\n\r\n";
    return response.str();
}

}  // namespace coakka::runtime_addon_web
