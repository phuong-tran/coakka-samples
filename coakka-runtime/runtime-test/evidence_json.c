#include "evidence_json.h"

void evidence_json_write_string(FILE* stream, const char* text) {
  const unsigned char* cursor = (const unsigned char*)(text != NULL ? text : "");

  fputc('"', stream);
  while (*cursor != '\0') {
    switch (*cursor) {
      case '"':
        fputs("\\\"", stream);
        break;
      case '\\':
        fputs("\\\\", stream);
        break;
      case '\b':
        fputs("\\b", stream);
        break;
      case '\f':
        fputs("\\f", stream);
        break;
      case '\n':
        fputs("\\n", stream);
        break;
      case '\r':
        fputs("\\r", stream);
        break;
      case '\t':
        fputs("\\t", stream);
        break;
      default:
        if (*cursor < 0x20u) {
          fprintf(stream, "\\u%04x", (unsigned int)*cursor);
        } else {
          fputc((int)*cursor, stream);
        }
        break;
    }
    ++cursor;
  }
  fputc('"', stream);
}
