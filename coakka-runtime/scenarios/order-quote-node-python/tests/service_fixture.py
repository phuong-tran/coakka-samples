"""Test-only business handlers; never selected by the normal service CLI."""
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from pricing import quote_payload
from pricing_service import serve


def handler(payload):
    mode = sys.argv[2]
    if mode == "delayed":
        print("Handler entered.", flush=True)
        time.sleep(0.4)  # Simulated slow business work, not a Runtime polling loop.
    if mode == "raises":
        raise ValueError("test-only application failure")
    if mode == "malformed":
        return b"not-json"
    if mode == "oversized":
        return b" " * (16 * 1024 + 1)
    result = quote_payload(payload)
    if mode == "delayed":
        print("Handler returning.", flush=True)
    return result


serve(int(sys.argv[1]), handler)
