#!/usr/bin/env python3
"""Run a managed sample as a real process and verify its finite wire recipes.

The child is the exact packaged connector application, not a replacement HTTP
server. This driver owns only client sockets and process cleanup. Each poll,
read, frame and wait is bounded; process termination must report success.
"""
import argparse
import http.client
import json
import re
import subprocess
import socket
import time
import zlib
from pathlib import Path

from wire_protocols import verify_streaming, verify_websocket
from importlib.util import module_from_spec, spec_from_file_location


def upload(port: int, check_trailer: bool = False) -> None:
    """Exercise binary chunked input and a declared trailer without buffering a file."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.putrequest("POST", "/upload")
        connection.putheader("Transfer-Encoding", "chunked")
        connection.putheader("Trailer", "x-upload-check")
        connection.endheaders()
        connection.send(b"3\r\na\x00b\r\n2\r\ncd\r\n0\r\nx-upload-check: done\r\n\r\n")
        response = connection.getresponse()
        assert response.status == 200 and response.read(129) == b"a\x00bcd"
        if check_trailer:
            assert response.getheader("x-upload-observed") == "done"
    finally:
        connection.close()


def go_adverse(port: int, log_path: Path) -> None:
    """Check ordered metadata and a real body-reader exit after peer abort."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.putrequest("GET", "/parameters/a%2Fb?tag=one&flag&tag=&tag=two")
        connection.putheader("x-sample-value", "first")
        connection.putheader("x-sample-value", "second")
        connection.endheaders()
        response = connection.getresponse()
        assert response.status == 200
        value = json.loads(response.read(8193))
        assert value == {
            "path": [{"Name": "id", "EncodedValue": "a%2Fb"}],
            "query": [
                {"EncodedKey": "tag", "EncodedValue": "one", "HasValue": True},
                {"EncodedKey": "flag", "EncodedValue": "", "HasValue": False},
                {"EncodedKey": "tag", "EncodedValue": "", "HasValue": True},
                {"EncodedKey": "tag", "EncodedValue": "two", "HasValue": True},
            ],
            "headers": ["first", "second"],
        }
    finally:
        connection.close()

    aborted_upload(port, log_path, "go")


def aborted_upload(port: int, log_path: Path, prefix: str) -> None:
    """Observe the actual body reader entering/failing, then verify reuse."""
    def contents() -> str:
        with log_path.open(encoding="utf-8") as stream:
            data = stream.read(65537)
        if len(data) > 65536:
            raise RuntimeError("sample log exceeded the smoke bound")
        return data

    before = contents()

    def wait_marker(marker: str) -> None:
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if contents().count(marker) > before.count(marker):
                return
            time.sleep(0.01)
        raise RuntimeError(f"body reader did not report {marker}")

    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        client.sendall(b"POST /upload HTTP/1.1\r\nHost: localhost\r\n"
                       b"Content-Length: 64\r\n\r\nabc")
        wait_marker(prefix + "-upload-reading")
    wait_marker(prefix + "-upload-read-failed")
    # A subsequent full upload demonstrates service reuse, not an inferred
    # transport outcome or a claim that every native lease has retired.
    upload(port, check_trailer=True)


def verify_go_gzip(port: int) -> None:
    """Bound encoded and decoded bytes; require actual negotiated GZIP content."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.request("GET", "/parameters/" + "a" * 128,
                           headers={"Accept-Encoding": "gzip"})
        response = connection.getresponse()
        assert response.status == 200 and response.getheader("content-encoding") == "gzip"
        encoded = response.read(4097)
        assert len(encoded) <= 4096
        decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
        decoded = decoder.decompress(encoded, 4097)
        assert len(decoded) <= 4096 and decoder.eof and not decoder.unused_data
        assert json.loads(decoded)["path"] == [{"Name": "id", "EncodedValue": "a" * 128}]
    finally:
        connection.close()


def typescript_metadata(port: int) -> None:
    """Verify Core-parsed values using the sample's ordinary JS naming idiom."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.putrequest("GET", "/parameters/a%2Fb?tag=one&flag&tag=&tag=two")
        connection.putheader("x-sample-value", "first")
        connection.putheader("x-sample-value", "second")
        connection.endheaders()
        response = connection.getresponse()
        assert response.status == 200
        body = response.read(4097)
        assert len(body) <= 4096
        assert json.loads(body) == {
            "path": [{"name": "id", "encodedValue": "a%2Fb"}],
            "query": [{"encodedKey": "tag", "encodedValue": "one"},
                      {"encodedKey": "flag", "encodedValue": None},
                      {"encodedKey": "tag", "encodedValue": ""},
                      {"encodedKey": "tag", "encodedValue": "two"}],
            "headers": ["first", "second"],
        }
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--ready-prefix", required=True)
    parser.add_argument("--go-adverse", action="store_true")
    parser.add_argument("--gzip", action="store_true")
    parser.add_argument("--typescript-metadata", action="store_true")
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--termination-exit-code", type=int, default=0)
    parser.add_argument("--shutdown-marker")
    parser.add_argument("--check-upload-trailer", action="store_true")
    parser.add_argument("--upload-abort-prefix")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if not 0 <= args.termination_exit_code <= 255 or (args.termination_exit_code and not args.shutdown_marker):
        parser.error("nonzero signal exit requires an explicit successful-close marker")
    if args.upload_abort_prefix and not re.fullmatch(r"[a-z]{1,32}", args.upload_abort_prefix):
        parser.error("upload abort prefix must be a bounded diagnostic label")
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command or len(args.ready_prefix) > 64:
        parser.error("a bounded readiness prefix and child command are required")
    pattern = re.compile(re.escape(args.ready_prefix) + r"=http://127\.0\.0\.1:(\d+)")
    with args.log.open("w+", encoding="utf-8") as log:
        process = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 20
            port = None
            while time.monotonic() < deadline and process.poll() is None:
                log.seek(0)
                contents = log.read(65537)
                if len(contents) > 65536:
                    raise RuntimeError("sample readiness log exceeded its bound")
                match = pattern.search(contents)
                if match:
                    port = int(match[1])
                    break
                time.sleep(0.05)
            if port is None:
                raise RuntimeError("sample did not announce readiness")
            upload(port, check_trailer=args.go_adverse or args.check_upload_trailer)
            if args.go_adverse:
                go_adverse(port, args.log)
            if args.upload_abort_prefix:
                aborted_upload(port, args.log, args.upload_abort_prefix)
            if args.gzip:
                verify_go_gzip(port)
            if args.typescript_metadata:
                typescript_metadata(port)
            if args.assets:
                # Reuse the finite protocol client, not native server code.
                spec = spec_from_file_location("native_wire_checks", Path(__file__).with_name("smoke-native.py"))
                module = module_from_spec(spec)
                spec.loader.exec_module(module)
                module.verify_file_protocol(port, args.assets)
            verify_streaming(port)
            verify_websocket(port, abrupt=True, subprotocol="coakka.sample", welcome=b"welcome")
            for _ in range(8):
                verify_websocket(port, subprotocol="coakka.sample", welcome=b"welcome")
        finally:
            if process.poll() is None:
                process.terminate()
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
                raise RuntimeError("sample did not shut down gracefully") from None
            # A JVM handling SIGTERM normally preserves status143 even after
            # every shutdown hook finishes. Accept that explicitly declared
            # status only with the application's post-close success marker.
            log.seek(0)
            final_log = log.read(65537)
            if len(final_log) > 65536 or (args.shutdown_marker and args.shutdown_marker not in final_log):
                raise RuntimeError("sample did not prove completed shutdown within log bound")
            if code != args.termination_exit_code:
                log.seek(0)
                raise RuntimeError(f"sample exit {code}: {log.read(65536)}")
    print("managed-wire-smoke=pass")


if __name__ == "__main__":
    main()
