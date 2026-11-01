"""Hold an OS-exclusive test port; no application requests are served here."""
import socket
import sys

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as owner:
    if sys.platform == "win32":
        owner.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
    owner.bind(("127.0.0.1", int(sys.argv[1])))
    owner.listen(1)
    print(f"Port reserved at 127.0.0.1:{owner.getsockname()[1]}", flush=True)
    sys.stdin.read(1)
