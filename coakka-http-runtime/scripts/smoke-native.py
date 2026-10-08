#!/usr/bin/env python3
"""Drive a native sample through HTTP, then require successful process shutdown.

This finite test client is not part of server dispatch. The listener binds port
zero and announces its selected port, avoiding a reserve/close/bind port race.
"""
import argparse
import http.client
import re
import socket
import subprocess
import time
from pathlib import Path
import urllib.request
import zlib
from wire_protocols import verify_websocket


def verify_stream_upload(port: int, log_path: Path) -> None:
    """Exercise incremental bytes/trailers and observe actual callback cleanup.

    Log markers contain no request data. Wait for DATA admission before peer
    disconnect, then require the native cancellation event, not a guessed delay.
    """
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        client.sendall(b"POST /upload HTTP/1.1\r\nHost: localhost\r\n"
                       b"Transfer-Encoding: chunked\r\nTrailer: x-upload-check\r\n\r\n"
                       b"3\r\na\x00b\r\n2\r\ncd\r\n0\r\nx-upload-check: done\r\n\r\n")
        response = http.client.HTTPResponse(client)
        response.begin()
        assert response.status == 200
        assert response.read(128) == b"bytes=5 trailers=1"
        response.close()

    def wait_marker(marker: str, prior: int) -> None:
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if log_path.read_text().count(marker) > prior:
                return
            time.sleep(0.01)
        raise RuntimeError(f"upload did not report {marker}")

    before = log_path.read_text()
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        client.sendall(b"POST /upload HTTP/1.1\r\nHost: localhost\r\n"
                       b"Content-Length: 64\r\n\r\nabc")
        wait_marker("native-upload-data", before.count("native-upload-data"))
    wait_marker("native-upload-cancelled", before.count("native-upload-cancelled"))
    # A later request demonstrates continued service after cancellation.
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/version", timeout=5) as response:
        assert response.status == 200 and response.read(16) == b"v2"


def verify_file_protocol(port: int, assets: Path) -> None:
    """Check Core-owned ranges/validators through both public file surfaces.

    Each request has a finite socket wait and bounded response read. A fresh
    connection keeps a failed assertion from contaminating the next case.
    The sample does not parse Range or generate ETags in its handler.
    """
    expected = (assets / "sample.txt").read_bytes()

    def get(path: str, headers: dict[str, str]) -> tuple[int, dict[str, str], bytes]:
        connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
        try:
            connection.request("GET", path, headers=headers)
            response = connection.getresponse()
            body = response.read(2 * 1024 * 1024 + 1)
            if len(body) > 2 * 1024 * 1024:
                raise RuntimeError("file response exceeded the smoke read bound")
            return response.status, {key.lower(): value for key, value in response.getheaders()}, body
        finally:
            connection.close()

    for path in ("/app/sample.txt", "/download"):
        status, headers, body = get(path, {})
        if status != 200 or body != expected or not headers.get("etag"):
            raise RuntimeError(f"{path}: full representation or ETag mismatch")
        etag = headers["etag"]
        status, headers, body = get(path, {"Range": "bytes=0-3"})
        if (status != 206 or body != expected[:4] or
                headers.get("content-range") != f"bytes 0-3/{len(expected)}"):
            raise RuntimeError(f"{path}: partial representation mismatch")
        status, _, body = get(path, {"If-None-Match": etag})
        if status != 304 or body:
            raise RuntimeError(f"{path}: unchanged representation was not bodyless 304")
        status, headers, _ = get(path, {"Range": f"bytes={len(expected)}-"})
        if status != 416 or headers.get("content-range") != f"bytes */{len(expected)}":
            raise RuntimeError(f"{path}: unsatisfiable range was not explicit")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path)
    parser.add_argument("assets", type=Path)
    parser.add_argument("log", type=Path)
    parser.add_argument("--stream-upload", action="store_true")
    parser.add_argument("--websocket", action="store_true")
    parser.add_argument("--tuning", action="store_true")
    parser.add_argument("--cpu", choices=("auto", "single"), default="auto")
    args = parser.parse_args()
    with args.log.open("w+") as log:
        command = [str(args.executable), "--assets", str(args.assets)]
        if args.tuning:
            command.extend(["--cpu", args.cpu, "--request-batch", "small",
                            "--terminal-batch", "medium", "--compression", "gzip"])
        process = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 10
            port = None
            while time.monotonic() < deadline and process.poll() is None:
                log.seek(0)
                match = re.search(r"native-sample-port=(\d+)", log.read())
                if match:
                    port = int(match[1])
                    break
                time.sleep(0.05)
            if port is None:
                raise RuntimeError("sample never announced readiness")
            cases = [("/echo", b"binary\x00\xff", 201, b"binary\x00\xff"),
                     ("/version", None, 200, b"v2"),
                     ("/download", None, 200, (args.assets / "sample.txt").read_bytes()),
                     ("/app/index.html", None, 200, (args.assets / "index.html").read_bytes())]
            for path, body, status, expected in cases:
                request = urllib.request.Request(f"http://127.0.0.1:{port}{path}", data=body)
                with urllib.request.urlopen(request, timeout=5) as response:
                    assert response.status == status and response.read(2 * 1024 * 1024) == expected, path
            verify_file_protocol(port, args.assets)
            with urllib.request.urlopen(
                    f"http://127.0.0.1:{port}/items/a%2Fb?tag=one&flag&tag=&tag=two", timeout=5) as response:
                assert response.status == 200 and response.read(32) == b"a%2Fb"
                assert response.getheader("x-query-count") == "4"
                assert response.getheader("x-query-with-value") == "3"
            if args.tuning:
                log.seek(0)
                observed = log.read()
                assert "request-batch=1 terminal-batch=4" in observed
                if args.cpu == "single":
                    assert "cpu-policy=1 placement=1 selected=1 verified=1" in observed
                request = urllib.request.Request(f"http://127.0.0.1:{port}/items/" + "a" * 128,
                                                 headers={"Accept-Encoding": "gzip"})
                with urllib.request.urlopen(request, timeout=5) as response:
                    assert response.getheader("content-encoding") == "gzip"
                    encoded = response.read(257)
                    assert len(encoded) <= 256
                    # Bound both compressed input and decoded output, even if
                    # the tested endpoint accidentally returns another body.
                    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
                    assert decoder.decompress(encoded, 129) == b"a" * 128
                    assert decoder.eof and not decoder.unconsumed_tail
            if args.stream_upload:
                verify_stream_upload(port, args.log)
            if args.websocket:
                verify_websocket(port, abrupt=True)
                for _ in range(8):
                    verify_websocket(port)
        finally:
            if process.poll() is None:
                process.terminate()
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise RuntimeError("sample did not shut down gracefully") from None
            if code != 0:
                log.seek(0)
                raise RuntimeError(f"sample exit {code}: {log.read()}")
    print(f"native-sample-smoke=pass tuning={args.tuning} cpu={args.cpu}")


if __name__ == "__main__":
    main()
