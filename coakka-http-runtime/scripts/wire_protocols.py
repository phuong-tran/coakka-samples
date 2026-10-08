"""Bounded standard-library wire checks, shared by feature sample drivers.

This is a test client, not a server parser or connector implementation. Every
socket has a finite timeout and every frame/header has a fixed read ceiling.
"""
import base64
import hashlib
import http.client
import os
import socket
import struct


def verify_streaming(port: int) -> None:
    """Read HTTP/1.1 chunks and final trailers rather than discarding trailers."""
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        client.sendall(b"GET /stream HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        with client.makefile("rb") as reader:
            status = reader.readline(1025)
            assert status.startswith(b"HTTP/1.1 200 "), status

            def fields() -> dict[bytes, bytes]:
                result = {}
                size = 0
                for _ in range(64):
                    line = reader.readline(4097)
                    size += len(line)
                    assert len(line) <= 4096 and size <= 16384
                    if line == b"\r\n":
                        return result
                    assert b":" in line and line.endswith(b"\r\n")
                    key, value = line.split(b":", 1)
                    result[key.lower()] = value.strip()
                raise AssertionError("header/trailer count exceeded")

            headers = fields()
            assert headers[b"transfer-encoding"].lower() == b"chunked"
            body = bytearray()
            for _ in range(128):
                line = reader.readline(65)
                assert line.endswith(b"\r\n") and len(line) <= 64
                size = int(line.split(b";", 1)[0], 16)
                assert 0 <= size <= 65536 - len(body), "response exceeded bound"
                if size == 0:
                    break
                chunk = reader.read(size)
                assert len(chunk) == size and reader.read(2) == b"\r\n"
                body.extend(chunk)
            else:
                raise AssertionError("too many response chunks")
            assert body == b"stream-ready"
            assert fields()[b"x-stream-end"] == b"done"

    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.request("GET", "/events")
        response = connection.getresponse()
        assert response.status == 200
        assert response.getheader("content-type", "").startswith("text/event-stream")
        body = response.read(65537)
        assert len(body) <= 65536
        lines = body.replace(b"\r\n", b"\n").split(b"\n")
        for expected in (b"event: state", b"id: 1", b"retry: 1000", b"data: ready", b"data: second line"):
            assert expected in lines, (expected, body)
        assert body.endswith(b"\n\n")
    finally:
        connection.close()


def verify_websocket(port: int, *, abrupt: bool = False,
                     subprotocol: str = "", welcome: bytes = b"") -> None:
    """Check upgrade, text/binary echo, ping/pong and an ordinary close handshake."""
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        # Explicit fixture parameters, not guessed server behavior. Native
        # samples have no greeting; managed examples select coakka.sample.
        assert subprotocol in ("", "coakka.sample")
        protocol_header = f"Sec-WebSocket-Protocol: {subprotocol}\r\n" if subprotocol else ""
        client.sendall((f"GET /socket HTTP/1.1\r\nHost: localhost:{port}\r\n"
                        "Connection: Upgrade\r\nUpgrade: websocket\r\n"
                        f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
                        f"{protocol_header}\r\n").encode("ascii"))
        with client.makefile("rb", buffering=0) as reader:
            def exact(count: int) -> bytes:
                data = bytearray()
                while len(data) < count:
                    part = reader.read(count - len(data))
                    if not part:
                        raise AssertionError("truncated WebSocket frame")
                    data.extend(part)
                return bytes(data)

            status = reader.readline(1025)
            assert status.startswith(b"HTTP/1.1 101 "), status
            headers = {}
            total = len(status)
            for _ in range(64):
                line = reader.readline(4097)
                total += len(line)
                assert len(line) <= 4096 and total <= 16384, "upgrade header bound"
                if line == b"\r\n":
                    break
                assert line.endswith(b"\r\n") and b":" in line, "invalid upgrade header"
                name, value = line.split(b":", 1)
                headers[name.lower()] = value.strip()
            else:
                raise AssertionError("too many upgrade headers")
            # SHA-1 here is the WebSocket protocol handshake, not a security hash.
            expected = base64.b64encode(hashlib.sha1(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")).digest())
            assert headers[b"sec-websocket-accept"] == expected
            assert headers[b"upgrade"].lower() == b"websocket"
            if subprotocol:
                assert headers.get(b"sec-websocket-protocol") == subprotocol.encode("ascii")
            assert b"upgrade" in [part.strip() for part in headers[b"connection"].lower().split(b",")]

            def send(opcode: int, body: bytes) -> None:
                assert len(body) <= 4096
                mask = os.urandom(4)
                length = bytes([0x80 | len(body)]) if len(body) < 126 else b"\xfe" + struct.pack("!H", len(body))
                client.sendall(bytes([0x80 | opcode]) + length + mask +
                               bytes(value ^ mask[index % 4] for index, value in enumerate(body)))

            def receive() -> tuple[int, bytes]:
                flags, length = exact(2)
                assert flags & 0x80 and not flags & 0x70, "expected an uncompressed final frame"
                assert not length & 0x80, "server frames must not be masked"
                if length == 126:
                    length = struct.unpack("!H", exact(2))[0]
                elif length == 127:
                    length = struct.unpack("!Q", exact(8))[0]
                assert length <= 4096, "frame exceeded smoke bound"
                return flags & 15, exact(length)

            if welcome:
                assert receive() == (1, welcome), "application welcome frame"
            for opcode, body in ((1, b"hello websocket"), (2, bytes(range(256))), (9, b"ping")):
                send(opcode, body)
                assert receive() == (10 if opcode == 9 else opcode, body)
            if abrupt:
                return  # Closing the TCP owner without a WebSocket close frame.
            send(8, struct.pack("!H", 1000))
            opcode, body = receive()
            assert opcode == 8 and body[:2] == struct.pack("!H", 1000), "close handshake"
