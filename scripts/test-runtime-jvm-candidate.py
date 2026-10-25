"""Focused real Gradle admission controls; build tools/cache are explicit inputs.

Run with the same candidate/build environment as a sample and set
COAKKA_JVM_CONTROL_ROOT to a new absolute external evidence directory.
This test does not download/rebuild the runtime or change the original inputs.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest


REPO = Path(__file__).resolve().parents[1]
SAMPLES = ("basic", "deadletter", "java-basic", "java-deadletter")


class AdmissionTests(unittest.TestCase):
    def test_real_configuration(self):
        root = Path(os.environ["COAKKA_JVM_CONTROL_ROOT"])
        self.assertTrue(root.is_absolute())
        root.mkdir()  # New task-owned evidence only; never clean caller storage.
        fixture = root / "fixture"
        build = fixture / "runtime/jvm"
        build.mkdir(parents=True)
        for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties"):
            shutil.copyfile(REPO / "runtime/jvm" / name, build / name)
        for sample in SAMPLES:
            (build / sample).mkdir()
            shutil.copyfile(REPO / "runtime/jvm" / sample / "build.gradle.kts", build / sample / "build.gradle.kts")
        catalog = fixture / "artifacts/runtime-jvm-candidate.properties"
        catalog.parent.mkdir()
        original = (REPO / "artifacts/runtime-jvm-candidate.properties").read_bytes()
        candidate = Path(os.environ["COAKKA_JVM_CANDIDATE_HOME"])
        source_hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in (candidate / "runtime.jar", *sorted((candidate / "lib").glob("*.jar")))}
        work = root / "work"
        work.mkdir()
        environment = dict(os.environ, COAKKA_SAMPLE_WORK_ROOT=str(work),
                           COAKKA_SAMPLE_JVM_NATIVE_DIR=str(work / "native-cache"))
        records = []

        def run(label, changed=None, content=original, expected=None):
            catalog.unlink(missing_ok=True)
            catalog.write_bytes(content)
            invocation = ["bash", str(REPO / "gradlew"), "-p", str(build), "projects", "--offline",
                          "--no-daemon", "--max-workers=1", "--console=plain", "--project-cache-dir", str(work / "project-cache")]
            env = dict(environment)
            for key, value in (changed or {}).items():
                if value is None:
                    env.pop(key, None)
                else:
                    env[key] = value
            result = subprocess.run(invocation, env=env, capture_output=True, timeout=60, check=False)
            log = result.stdout + result.stderr
            (root / (label + ".log")).write_bytes(log)
            records.append(dict(label=label, exitCode=result.returncode,
                                logSha256=hashlib.sha256(log).hexdigest()))
            (root / "receipt.json").write_text(json.dumps(records, indent=2) + "\n")
            if expected is None:
                self.assertEqual(result.returncode, 0, log.decode(errors="replace"))
                for sample in SAMPLES:
                    self.assertIn(("Project ':" + sample + "'").encode(), log)
                self.assertNotIn(b"spring-boot", log)
                self.assertNotIn(b"quarkus", log)
            else:
                self.assertNotEqual(result.returncode, 0, label)
                self.assertIn(expected.encode(), log, log.decode(errors="replace"))

        run("positive-four-only")
        controls = [
            ("missing-root", {"COAKKA_JVM_CANDIDATE_HOME": None}, original, "set explicit COAKKA_JVM_CANDIDATE_HOME"),
            ("relative-work", {"COAKKA_SAMPLE_WORK_ROOT": "relative"}, original, "must be absolute"),
            ("duplicate-catalog", {}, original + b"files=5\n", "duplicate candidate catalog field"),
            ("unknown-catalog", {}, original + b"unexpected=1\n", "invalid private JVM candidate identity"),
            ("oversize-catalog", {}, b"x" * 65_537, "bounded candidate catalog required"),
            ("traversal", {}, original.replace(b"runtime.jar", b"../runtime.jar"), "invalid candidate member name"),
            ("extent", {}, original.replace(b"2990053", b"2990054"), "missing or non-regular candidate member"),
            ("hash", {}, original.replace(b"dcaadc43", b"00000000"), "candidate member identity differs"),
            ("native-override", {"COAKKA_RUNTIME_LIB": "/not/a/native/library"}, original, "remove sample override"),
            ("loader-override", {"LD_LIBRARY_PATH": "/not/a/search/path"}, original, "remove sample override"),
        ]
        for label, changed, content, expected in controls:
            with self.subTest(label=label):
                run(label, changed, content, expected)
        linked = root / "linked-candidate"
        linked.mkdir()
        (linked / "runtime.jar").symlink_to(candidate / "runtime.jar")
        run("linked-jar", {"COAKKA_JVM_CANDIDATE_HOME": str(linked)}, expected="missing or non-regular candidate member")
        (linked / "runtime.jar").unlink()
        linked.rmdir()
        self.assertFalse((work / "native-cache").exists())
        self.assertEqual(source_hashes, {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                                        for p in map(Path, source_hashes)})
        # Retain logs and tiny build fixture; the caller owns the reusable Gradle cache.
        self.assertEqual(len(records), 12)


if __name__ == "__main__":
    unittest.main()
