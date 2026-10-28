#!/usr/bin/env python3
"""Select the fixed Rust build profile; never an installed runtime dependency."""
import importlib.util
from pathlib import Path
import sys

SPEC = importlib.util.spec_from_file_location("cargo_candidate", Path(__file__).with_name("runtime-cargo-candidate.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

if __name__ == "__main__":
    MODULE.main(MODULE.RUST)
