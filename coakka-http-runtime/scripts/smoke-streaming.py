#!/usr/bin/env python3
"""Exercise a native stream producer, require exact wire bytes and checked close."""
import argparse
from pathlib import Path
import re
import subprocess
import time

from wire_protocols import verify_streaming


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path)
    parser.add_argument("log", type=Path)
    args = parser.parse_args()
    with args.log.open("w+") as log:
        process = subprocess.Popen([str(args.executable)], stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 10
            port = None
            while time.monotonic() < deadline and process.poll() is None:
                log.seek(0)
                match = re.search(r"native-stream-port=(\d+)", log.read())
                if match:
                    port = int(match[1])
                    break
                time.sleep(0.02)
            if port is None:
                raise RuntimeError("stream server did not announce readiness")
            # Repetition exercises slot reuse and successful writer retirement.
            for _ in range(8):
                verify_streaming(port)
        finally:
            if process.poll() is None:
                process.terminate()
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
                raise RuntimeError("stream sample did not stop gracefully") from None
            if code != 0:
                log.seek(0)
                raise RuntimeError(f"stream sample exit {code}: {log.read()}")
    print("native-streaming-smoke=pass")


if __name__ == "__main__":
    main()
