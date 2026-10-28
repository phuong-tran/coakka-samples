#!/usr/bin/env python3
"""Build and exercise only the two packaged framework candidate applications."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

HERE = Path(__file__).resolve().parent
SAMPLES = HERE.parents[3]
CATALOG = SAMPLES / "coakka-runtime/artifacts/runtime-frameworks-candidate.json"
SCENARIOS = {"spring": "spring-boot-starter-local", "quarkus": "quarkus-local"}
CORE_COMMIT = "f6b61768f60a08d81b1dc06625a211ffca8e89e2"
spec = importlib.util.spec_from_file_location("framework_admission", HERE / "candidate-admission.py")
assert spec is not None and spec.loader is not None
admission = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admission)


def directory(name: str) -> Path:
    value = os.environ.get(name)
    if not value:
        raise ValueError(f"set explicit {name}")
    path = Path(value)
    admission.no_links(path)
    if not path.is_dir():
        raise ValueError(f"{name} must name an existing absolute directory")
    return path


def inputs() -> tuple[Path, Path]:
    home = directory("COAKKA_FRAMEWORK_CANDIDATE_HOME")
    work = directory("COAKKA_SAMPLE_WORK_ROOT")
    directory("GRADLE_USER_HOME")
    java_home = directory("COAKKA_SAMPLE_JAVA_HOME" if os.environ.get("COAKKA_SAMPLE_JAVA_HOME") else "JAVA_HOME")
    if home == work or home in work.parents or work in home.parents:
        raise ValueError("candidate inputs and work directories must be separate")
    for key, value in os.environ.items():
        if value and (key.upper().startswith(("LD_", "DYLD_")) or key.upper() in {
                "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH",
                "COAKKA_RUNTIME_LIB", "COAKKA_FILE_LANE_RUNTIME_LIB", "COAKKA_STREAM_LANE_RUNTIME_LIB"}):
            raise ValueError(f"remove sample native/JVM override: {key}")
    admission.admit(CATALOG, home)
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    if not java.is_file():
        raise ValueError("consumer Java executable is missing")
    return work, java


def build(scenario: str, work: Path) -> Path:
    leaf = SCENARIOS[scenario]
    task = "bootJar" if scenario == "spring" else "quarkusBuild"
    command = ["bash", str(SAMPLES / "gradlew"), "-p", str(HERE),
               f":runtime:scenarios:customer-crud:{leaf}:customer-app:{task}",
               "--project-cache-dir", str(work / "gradle-project-cache"),
               "--no-daemon", "--console=plain"]
    if os.environ.get("COAKKA_FRAMEWORK_OFFLINE") == "1":
        command.append("--offline")
    print(f"[framework-sample] building {leaf}", flush=True)
    with tempfile.TemporaryDirectory(prefix=f"{scenario}-build-", dir=work) as temporary:
        command.extend((f"-Djava.io.tmpdir={temporary}",
                        f"-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8 -Djava.io.tmpdir={temporary}"))
        environment = os.environ.copy()
        environment.update({"TMPDIR": temporary, "TMP": temporary, "TEMP": temporary})
        process = subprocess.Popen(command, env=environment, start_new_session=os.name == "posix")
        try:
            exit_code = process.wait(timeout=600)
            if exit_code:
                raise subprocess.CalledProcessError(exit_code, command)
        except BaseException:
            # The launcher owns this build session, never another Gradle/client
            # process that happens to share a cache or network port.
            if process.poll() is None:
                if os.name == "posix":
                    os.killpg(process.pid, signal.SIGTERM)
                else:
                    process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    if os.name == "posix":
                        os.killpg(process.pid, signal.SIGKILL)
                    else:
                        process.kill()
                    process.wait(timeout=5)
            raise
    app = work / "build/runtime/scenarios/customer-crud" / leaf / "customer-app"
    jar = app / ("libs/customer-app.jar" if scenario == "spring" else "quarkus-app/quarkus-run.jar")
    if not jar.is_file() or jar.is_symlink():
        raise ValueError("packaged application output is missing")
    return jar


def unused_port() -> int:
    # Test bootstrap only. Identity is checked again after binding; a concurrent
    # claimant causes refusal rather than contacting an unrelated application.
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as reservation:
        reservation.bind(("127.0.0.1", 0))
        return int(reservation.getsockname()[1])


def request(port: int, path: str, method: str = "GET", value=None, timeout: float = 3.0):
    data = None if value is None else json.dumps(value).encode()
    req = Request(f"http://127.0.0.1:{port}{path}", data=data, method=method,
                  headers={"Content-Type": "application/json"} if data is not None else {})
    try:
        response = urlopen(req, timeout=timeout)
    except HTTPError as error:
        response = error
    with response:
        body = response.read(262_145)
        if len(body) > 262_144:
            raise ValueError("sample HTTP response exceeds bound")
        return response.status, json.loads(body)


def stop_owned(process: subprocess.Popen) -> tuple[str, float]:
    start = time.monotonic()
    if process.poll() is not None:
        return "already-exited", time.monotonic() - start
    process.terminate()
    try:
        process.wait(timeout=10)
        if os.name == "nt":
            return "hard-terminated", time.monotonic() - start
        result = "terminated" if process.returncode in (0, 143, -signal.SIGTERM) else "unexpected-exit"
        return result, time.monotonic() - start
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)
        return "force-killed", time.monotonic() - start


def smoke(scenario: str, work: Path, java: Path, jar: Path) -> dict:
    checks = []
    node = "framework-smoke-" + uuid.uuid4().hex
    port = unused_port()
    leaf = SCENARIOS[scenario]
    with tempfile.TemporaryDirectory(prefix=f"{scenario}-smoke-", dir=work) as temporary:
        owned = Path(temporary)
        command = [str(java), f"-Djava.io.tmpdir={owned}", f"-Dcoakka.runtime.nativeDir={owned / 'native-cache'}",
                   f"-Dcoakka.runtime.node-id={node}",
                   f"-D{'server.port' if scenario == 'spring' else 'quarkus.http.port'}={port}",
                   "-jar", str(jar)]
        log_path = owned / "application.log"
        with log_path.open("wb") as output:
            process = subprocess.Popen(command, cwd=owned, stdout=output, stderr=subprocess.STDOUT,
                                       start_new_session=os.name == "posix")
            try:
                deadline = time.monotonic() + 90
                while True:
                    if process.poll() is not None:
                        raise RuntimeError("sample application exited before readiness")
                    try:
                        status, initial = request(port, "/api/customers/runtime", timeout=1.0)
                        if status == 200:
                            break
                    except (OSError, URLError, ValueError):
                        pass
                    if time.monotonic() >= deadline:
                        raise TimeoutError("sample HTTP readiness exceeded 90 seconds")
                    time.sleep(0.1)
                config, info = initial["runtimeConfig"], initial["runtimeInfo"]
                if config["nodeId"] != node:
                    raise RuntimeError("HTTP port belongs to another application instance")
                assert info["abiVersion"] == 1 and info["gitCommit"] == CORE_COMMIT, info
                assert config["routeCount"] == (4 if scenario == "spring" else 1), config
                assert config["requestedCpuPolicy"] == 0 and config["connectionMode"] == 3, config
                assert config["monitorEnabled"] is False, config
                assert initial["connector"]["demoMode"] == leaf, initial
                checks.append("owned-package-runtime-identity/defaults/routes")

                status, before = request(port, "/api/customers")
                assert status == 200 and before["customers"] == [], (status, before)
                checks.append("initial-empty-store")

                customer = {"id": "candidate-customer", "name": "Ada", "email": "ada@example.com",
                            "tier": "silver", "notes": "candidate smoke"}
                status, created = request(port, "/api/customers", "POST", customer)
                assert status == 201 and created["operation"] == "create", (status, created)
                assert created["deliveryMode"] == "runtime", created
                checks.append("create")

                update = {key: value for key, value in customer.items() if key != "id"}
                update["tier"] = "gold"
                status, changed = request(port, "/api/customers/candidate-customer", "PUT", update)
                assert status == 200 and changed["operation"] == "update", (status, changed)
                status, listed = request(port, "/api/customers")
                assert status == 200 and listed["deliveryMode"] == "runtime", (status, listed)
                assert len(listed["customers"]) == 1 and listed["customers"][0]["tier"] == "gold", listed
                checks.extend(("update", "list"))

                status, deleted = request(port, "/api/customers/candidate-customer", "DELETE")
                assert status == 200 and deleted["operation"] == "delete", (status, deleted)
                status, empty = request(port, "/api/customers")
                assert status == 200 and empty["customers"] == [], (status, empty)
                checks.append("delete")

                status, missing = request(port, "/api/customers/route-miss", "POST", {})
                assert status == 200 and missing["reason"] == 2, (status, missing)
                assert missing["target"] == "samples.customer.missing", missing
                checks.append("typed-route-miss")
                status, final = request(port, "/api/customers/runtime")
                assert status == 200 and final["clientStats"]["matchedResponses"] >= 6, final
                assert final["clientStats"]["matchedDeadletters"] >= 1, final
                checks.append("runtime-terminal-counters")
            except BaseException:
                stopped, _ = stop_owned(process)
                if stopped in ("force-killed", "hard-terminated", "unexpected-exit"):
                    print(f"[framework-sample] failed-run cleanup: {stopped}", file=sys.stderr)
                output.flush()
                with log_path.open("rb") as failed_log:
                    failed_log.seek(max(0, log_path.stat().st_size - 32_768))
                    print(failed_log.read().decode(errors="replace"), file=sys.stderr)
                raise
            else:
                stopped, elapsed = stop_owned(process)
                if stopped != "terminated":
                    raise RuntimeError(f"sample did not complete expected owned termination: {stopped}")
                checks.append("owned-process-termination")
                return {"scenario": scenario, "checks": checks, "runtime": final, "port": port,
                        "shutdownSeconds": elapsed, "exitCodeAfterStop": process.returncode,
                        "shutdownResult": stopped,
                        "ownedRunDirectoryRemovedOnReturn": True}


def dev(scenario: str, work: Path, java: Path, jar: Path) -> int:
    with tempfile.TemporaryDirectory(prefix=f"{scenario}-dev-", dir=work) as temporary:
        owned = Path(temporary)
        command = [str(java), f"-Djava.io.tmpdir={owned}", f"-Dcoakka.runtime.nativeDir={owned / 'native-cache'}",
                   "-jar", str(jar)]
        process = subprocess.Popen(command, cwd=owned, start_new_session=os.name == "posix")
        try:
            return process.wait()
        finally:
            stopped, _ = stop_owned(process)
            if stopped == "force-killed":
                raise RuntimeError("sample shutdown required force-kill")


def interrupted(_signum, _frame):
    raise KeyboardInterrupt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenario", choices=SCENARIOS)
    parser.add_argument("command", nargs="?", choices=("check", "build", "dev", "app", "smoke", "stop"), default="check")
    args = parser.parse_args()
    if args.command == "stop":
        print("No background process is managed. Stop a foreground app with Ctrl-C; smoke closes only its own app.")
        return 0
    previous = signal.signal(signal.SIGTERM, interrupted)
    try:
        work, java = inputs()
        jar = build(args.scenario, work)
        if args.command in ("check", "build"):
            print("[framework-sample] packaged candidate application built")
        elif args.command in ("dev", "app"):
            return dev(args.scenario, work, java, jar)
        else:
            print(json.dumps(smoke(args.scenario, work, java, jar), sort_keys=True))
        return 0
    except KeyboardInterrupt:
        return 130
    except (OSError, ValueError, AssertionError, RuntimeError, TimeoutError, subprocess.SubprocessError) as error:
        print(f"[framework-sample] {error}", file=sys.stderr)
        return 1
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == "__main__":
    raise SystemExit(main())
