#ifndef COAKKA_RUNTIME_NATIVE_EVIDENCE_JSON_H
#define COAKKA_RUNTIME_NATIVE_EVIDENCE_JSON_H

#include <stdio.h>

/* Writes one valid JSON string. NULL is represented as an empty string. */
void evidence_json_write_string(FILE* stream, const char* text);

#endif
