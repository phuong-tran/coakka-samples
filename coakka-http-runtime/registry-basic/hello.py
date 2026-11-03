from urllib.request import urlopen

from coakka_http import Builder, Response


service = (
    Builder()
    .listen("127.0.0.1", 0)
    .get("/hello", lambda _: Response.text("hello"))
    .start()
)
try:
    with urlopen(f"http://127.0.0.1:{service.port}/hello", timeout=5) as reply:
        if reply.status != 200 or reply.read() != b"hello":
            raise RuntimeError("unexpected HTTP response")
finally:
    service.close()
print("HTTP 200 hello")
