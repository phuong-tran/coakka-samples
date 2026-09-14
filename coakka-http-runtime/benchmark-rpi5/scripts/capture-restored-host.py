#!/usr/bin/env python3
"""Capture and validate the host after a controlled benchmark restores it."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


MAX_RECORDED_UNITS = 256


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(errors="replace").strip()
    except OSError:
        return None


def recorded_names(path: Path) -> list[str]:
    names = [line.strip() for line in path.read_text().splitlines() if line.strip()]
    if len(names) > MAX_RECORDED_UNITS or any(len(name) > 256 for name in names):
        raise RuntimeError(f"restoration inventory is outside its bound: {path}")
    return names


def capture(command: list[str], environment: dict[str, str] | None = None) -> dict[str, object]:
    completed = subprocess.run(
        command,
        text=True,
        capture_output=True,
        check=False,
        timeout=10,
        env=environment,
    )
    return {
        "command": command,
        "exit_code": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
    }


def unit_states(names: list[str], *, user: bool = False) -> dict[str, str]:
    environment = None
    prefix = ["systemctl"]
    if user:
        prefix.append("--user")
        environment = os.environ.copy()
        environment["XDG_RUNTIME_DIR"] = f"/run/user/{os.getuid()}"
    states: dict[str, str] = {}
    for name in names:
        result = capture([*prefix, "is-active", name], environment)
        state = str(result["stdout"]).strip()
        states[name] = state or f"exit-{result['exit_code']}"
    return states


def governors() -> dict[str, str]:
    return {
        path.parts[-3]: path.read_text().strip()
        for path in sorted(
            Path("/sys/devices/system/cpu").glob(
                "cpu[0-9]*/cpufreq/scaling_governor"
            )
        )
    }


def expected_governors(path: Path) -> dict[str, str]:
    expected: dict[str, str] = {}
    for line in path.read_text().splitlines():
        source, value = line.split(" ", 1)
        expected[Path(source).parts[-3]] = value
    return expected


def cooling_state() -> tuple[list[dict[str, str | None]], int | None]:
    devices = []
    for path in sorted(Path("/sys/class/thermal").glob("cooling_device*")):
        devices.append(
            {
                "device": path.name,
                "type": read_text(path / "type"),
                "current_state": read_text(path / "cur_state"),
                "maximum_state": read_text(path / "max_state"),
            }
        )
    fan_rpm = None
    for path in sorted(Path("/sys/class/hwmon").glob("hwmon*/fan1_input")):
        try:
            fan_rpm = int(path.read_text().strip())
            break
        except (OSError, ValueError):
            pass
    return devices, fan_rpm


def memory_total() -> str | None:
    for line in Path("/proc/meminfo").read_text().splitlines():
        if line.startswith("MemTotal:"):
            return line.split(":", 1)[1].strip()
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True, type=Path)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    campaign = json.loads((evidence / "campaign.json").read_text())
    control = evidence / "host-control"
    if campaign.get("status") != "complete":
        raise RuntimeError("restored-host capture requires a complete campaign")
    if not (control / "restored-at.txt").is_file():
        raise RuntimeError("host restoration evidence is missing")

    system_states = unit_states(recorded_names(control / "stopped-units.txt"))
    timer_states = unit_states(recorded_names(control / "stopped-timers.txt"))
    user_states = unit_states(
        recorded_names(control / "stopped-user-units.txt"), user=True
    )
    actual_governors = governors()
    expected = expected_governors(control / "governors-before.txt")
    temperature = capture(["vcgencmd", "measure_temp"])
    throttled = capture(["vcgencmd", "get_throttled"])
    cooling, fan_rpm = cooling_state()
    value = {
        "schema_version": 1,
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "board_model": Path("/sys/firmware/devicetree/base/model")
        .read_bytes()
        .rstrip(bytes([0]))
        .decode(),
        "memory_total": memory_total(),
        "cooling": cooling,
        "fan_rpm": fan_rpm,
        "temperature": temperature,
        "throttled": throttled,
        "governors": actual_governors,
        "expected_governors": expected,
        "restored_system_units": system_states,
        "restored_timers": timer_states,
        "restored_user_units": user_states,
    }
    generator = evidence / "restored-host-generator.py"
    shutil.copy2(Path(__file__).resolve(), generator)
    destination = evidence / "post-campaign-host.json"
    temporary = destination.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.replace(destination)

    restored = {**system_states, **timer_states, **user_states}
    if any(state != "active" for state in restored.values()):
        raise RuntimeError(f"host service restoration is incomplete: {restored}")
    if actual_governors != expected:
        raise RuntimeError(
            f"CPU governors were not restored: expected {expected}, found {actual_governors}"
        )
    throttle_clean = (
        throttled.get("exit_code") == 0
        and str(throttled.get("stdout", "")).strip() == "throttled=0x0"
    )
    if not throttle_clean:
        raise RuntimeError(f"post-campaign throttle state is not clean: {throttled}")
    print(destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
