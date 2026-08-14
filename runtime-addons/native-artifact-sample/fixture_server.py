#!/usr/bin/env python3
"""Bounded private-TLS fixture for the native artifact addon samples."""

import argparse
import http.server
import json
import pathlib
import ssl
import urllib.parse


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--addon", required=True)
    parser.add_argument("--certificate", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--payload", required=True)
    parser.add_argument("--port-file", required=True)
    parser.add_argument("--sha256", required=True)
    return parser.parse_args()


class FixtureHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, _format, *_args):
        # Request headers may contain sample credentials; do not log them.
        return

    def _reject(self, reason):
        body = reason.encode("ascii", "replace")
        self.send_response(400)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def _bearer_is(self, value):
        return self.headers.get("Authorization") == "Bearer " + value

    def _validate(self, method):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
        addon = self.server.addon

        if addon == "https":
            return method == "GET" and parsed.path == "/artifact/model.bin"
        if addon == "s3":
            authorization = self.headers.get("Authorization", "")
            return (
                method == "GET"
                and parsed.path == "/sample-bucket/models/model.bin"
                and query == {"versionId": ["sample-version-1"]}
                and authorization.startswith("AWS4-HMAC-SHA256 ")
            )
        if addon == "azure-blob":
            required = {
                "sv": ["2022-11-02"],
                "sp": ["r"],
                "spr": ["https"],
                "sr": ["bv"],
                "se": ["2030-08-12T05:05:06Z"],
                "sig": ["sample-signature"],
                "versionid": ["2026-08-12T04:05:06.0000000Z"],
            }
            return method == "GET" and parsed.path == "/container/model.bin" and query == required
        if addon == "gcs":
            expected = {
                "X-Goog-Algorithm": ["GOOG4-HMAC-SHA256"],
                "X-Goog-Credential": ["sample/20260815/auto/storage/goog4_request"],
                "X-Goog-Date": ["20260815T000000Z"],
                "X-Goog-Expires": ["600"],
                "X-Goog-SignedHeaders": ["host"],
                "X-Goog-Signature": ["0" * 64],
                "generation": ["1700000000000001"],
            }
            return method == "GET" and parsed.path == "/sample-bucket/model.bin" and query == expected
        if addon == "webdav":
            return (
                method == "GET"
                and parsed.path == "/dav/model.bin"
                and self.headers.get("If-Match") == '"coakka-sample-etag"'
            )
        if addon == "oci-registry":
            expected_path = "/v2/coakka/sample/blobs/sha256:" + self.server.sha256
            return method == "GET" and parsed.path == expected_path and self._bearer_is("sample-registry-token")
        if addon == "huggingface-hub":
            revision = "0123456789abcdef0123456789abcdef01234567"
            expected_path = "/coakka/sample/resolve/" + revision + "/model.bin"
            return method == "GET" and parsed.path == expected_path and self._bearer_is("hf_sample_read_token")
        if addon == "github-release":
            return (
                method == "GET"
                and parsed.path == "/repos/coakka/sample/releases/assets/1001"
                and self._bearer_is("github_sample_read_token")
                and self.headers.get("Accept") == "application/octet-stream"
                and self.headers.get("X-GitHub-Api-Version") == "2026-03-10"
            )
        if addon == "google-drive":
            return (
                method == "GET"
                and parsed.path == "/drive/v3/files/sample-file/revisions/sample-revision"
                and query == {"alt": ["media"]}
                and self._bearer_is("google_drive_sample_read_token")
            )
        if addon == "dropbox":
            expected_arg = json.dumps(
                {"path": "rev:015a01044acb99900000001aa8954d0"},
                separators=(",", ":"),
            )
            return (
                method == "POST"
                and parsed.path == "/2/files/download"
                and not parsed.query
                and self.headers.get("Dropbox-API-Arg") == expected_arg
                and self._bearer_is("dropbox_sample_read_token")
                and int(self.headers.get("Content-Length", "0")) == 0
            )
        return False

    def _serve(self, method):
        if not self._validate(method):
            self._reject("request shape rejected")
            return
        payload = self.server.payload
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("ETag", '"coakka-sample-etag"')
        self.send_header("Docker-Content-Digest", "sha256:" + self.server.sha256)
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        self._serve("GET")

    def do_POST(self):
        self._serve("POST")


def main():
    args = parse_args()
    payload = pathlib.Path(args.payload).read_bytes()
    if not payload or len(payload) > 4 * 1024 * 1024:
        raise SystemExit("fixture payload must be 1..4194304 bytes")
    server = http.server.HTTPServer(("127.0.0.1", 0), FixtureHandler)
    server.addon = args.addon
    server.payload = payload
    server.sha256 = args.sha256
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(args.certificate, args.key)
    server.socket = context.wrap_socket(server.socket, server_side=True)

    port_path = pathlib.Path(args.port_file)
    temporary = port_path.with_suffix(".tmp")
    temporary.write_text(str(server.server_port) + "\n", encoding="ascii")
    temporary.replace(port_path)
    server.serve_forever(poll_interval=0.1)


if __name__ == "__main__":
    main()
