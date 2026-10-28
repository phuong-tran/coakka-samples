"""Cold launcher controls. Fake build tools never claim native runtime facts."""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest

REPO = Path(__file__).resolve().parents[2]
ENTRY = REPO / "coakka-runtime/runtime-test/run.sh"
FAKE_CMAKE = r'''#!/usr/bin/env python3
import json, os, time
from pathlib import Path
import sys
args=sys.argv[1:]
with open(os.environ['CANDIDATE_TEST_LOG'],'a') as log:
    log.write(json.dumps(args)+'\n')
stage=('build' if '--build' in args else
    'admission' if '-S' in args and args[args.index('-S')+1].endswith('native-startup-preflight') else 'configure')
if os.environ.get('CANDIDATE_TEST_HOLD')==stage:
    Path(os.environ['CANDIDATE_TEST_MARKER']).write_text('ready')
    for _ in range(500):time.sleep(.01)
if args[:2]==['-E','env']:
    args=args[2:]
    while args and (args[0].startswith('--unset=') or '=' in args[0]):
        value=args.pop(0)
        if value.startswith('--unset='):os.environ.pop(value.split('=',1)[1],None)
        else:
            key,value=value.split('=',1);os.environ[key]=value
    os.execvpe(args[0],args,os.environ)
if '-P' in args:
    if os.environ.get('CANDIDATE_TEST_FAIL')=='probe':sys.exit(19)
    print(json.dumps({'fixture':'startup-probe','notNativeEvidence':True}));sys.exit(0)
if '--build' in args:
    if os.environ.get('CANDIDATE_TEST_FAIL')=='build':sys.exit(18)
    build=Path(args[args.index('--build')+1])
    for target in args[args.index('--target')+1:]:
        image=build/'custom output'/target
        image.parent.mkdir(exist_ok=True)
        image.write_text('#!/usr/bin/env python3\nimport json,os,sys\n'
            'with open(os.environ["CANDIDATE_TEST_LOG"],"a") as log: log.write(json.dumps(["@child",'+repr(target)+',*sys.argv[1:]])+"\\n")\n'
            'print(json.dumps(dict(target='+repr(target)+',args=sys.argv[1:],'
            'executionPath=os.environ.get("COAKKA_EVIDENCE_EXECUTION_PATH"),'
            'loaderPath=os.environ.get("LD_LIBRARY_PATH"))))\n'
            'if os.environ.get("CANDIDATE_TEST_SIGNAL")=="TERM":os.kill(os.getpid(),15)\n'
            'sys.exit(int(os.environ.get("CANDIDATE_TEST_EXIT","0")))\n')
        image.chmod(0o755)
        meta=build/('native-candidate-'+target+'-Release.path')
        style=os.environ.get('CANDIDATE_TEST_META','normal')
        if style=='missing':continue
        if style=='linked':
            other=build/(target+'.other');other.write_text(str(image)+'\n');meta.symlink_to(other)
        else:meta.write_text('x'*32769 if style=='oversize' else str(image)+ ('\nextra\n' if style=='extra' else '\n'))
    sys.exit(0)
source=args[args.index('-S')+1]
stage='admission' if source.endswith('native-startup-preflight') else 'configure'
if os.environ.get('CANDIDATE_TEST_FAIL')==stage:sys.exit(17)
build=Path(args[args.index('-B')+1])
if stage=='configure':(build/'CMakeCache.txt').write_text('fixture current native configure\n')
'''


class CandidateControls(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(
            prefix="native-launcher-controls-", dir=os.environ["COAKKA_DOC_TEST_ROOT"]
        )
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.sdk = self.root / "sdk"
        self.build = self.root / "build"
        self.bin = self.root / "bin"
        for folder in [self.sdk, self.build, self.bin]:
            folder.mkdir()
        self.log = self.root / "calls.jsonl"
        self.cache = self.build / "CMakeCache.txt"
        self.cache.write_text("retain original cache\n")
        tool = self.bin / "cmake"
        tool.write_text(FAKE_CMAKE)
        tool.chmod(0o755)
        self.env = {
            key: value for key, value in os.environ.items()
            if not key.startswith(("COAKKA_", "CANDIDATE_TEST_", "DYLD_", "LD_"))
        }
        self.env.update(
            PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
            COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=str(self.sdk),
            COAKKA_NATIVE_EVIDENCE_BUILD_DIR=str(self.build),
            CANDIDATE_TEST_LOG=str(self.log),
        )

    def run_entry(self, *args, changes=None):
        env = dict(self.env, **(changes or {}))
        return subprocess.run(
            ["bash", str(ENTRY), *args], env=env, cwd=self.root,
            capture_output=True, text=True, timeout=20, check=False,
        )

    def calls(self):
        return [json.loads(row) for row in self.log.read_text().splitlines()] if self.log.exists() else []

    def assert_no_admission_temp(self):
        # The retained admission.stdout is evidence, not a temporary directory.
        self.assertFalse([path for path in self.build.glob("native-startup-admission.*") if path.is_dir()])

    def assert_refusal(self, result, untouched=True):
        self.assertNotEqual(result.returncode, 0)
        self.assert_no_admission_temp()
        if untouched:
            self.assertEqual(self.cache.read_text(), "retain original cache\n")
            self.assertEqual(self.calls(), [])

    def test_default_uses_native_and_cmake_owned_layout(self):
        result = self.run_entry()
        self.assertEqual(result.returncode, 0, result.stderr)
        doc = json.loads(result.stdout)
        self.assertEqual(doc["target"], "coakka_runtime_v2_native_evidence")
        self.assertEqual(doc["args"], ["smoke"])
        self.assertEqual(doc["executionPath"], "installed-source-candidate")
        configure = next(row for row in self.calls() if row[:2] == ["-S", str(REPO / "coakka-runtime/runtime-test")])
        self.assertIn("-DCOAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP=ON", configure)
        self.assert_no_admission_temp()

    def test_modes_and_quoted_argument_handoff(self):
        cases = {
            "pressure": ("native", ["pressure", "--name", "one argument; not shell"]),
            "stress": ("native", ["stress"]), "soak": ("native", ["soak"]),
            "race": ("concurrency", ["race"]), "hot-reload": ("concurrency", ["hot-reload"]),
            "connection-strategies": ("connection_strategy", []),
            "file-lane": ("file_lane", []), "file-lane-simple": ("file_lane", []),
            "file-lane-owner-aware": ("file_lane", ["--owner-aware"]),
            "stream-lane": ("stream_lane", []), "stream-lane-simple": ("stream_lane", []),
            "stream-lane-owner-aware": ("stream_lane", ["--owner-aware"]),
        }
        for mode, (name, expected) in cases.items():
            with self.subTest(mode=mode):
                extra = expected[1:] if mode == "pressure" else []
                result = self.run_entry(mode, *extra)
                self.assertEqual(result.returncode, 0, result.stderr)
                doc = json.loads(result.stdout)
                self.assertEqual(doc["target"], "coakka_runtime_v2_" + name + "_evidence")
                self.assertEqual(doc["args"], expected)

    def test_startup_keeps_its_driver(self):
        result = self.run_entry("native-startup")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["fixture"], "startup-probe")
        self.assertFalse(any(row[0] == "@child" for row in self.calls()))

    def test_missing_relative_and_root_paths_refuse(self):
        for changes in [
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": ""},
            {"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": ""},
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": "relative"},
            {"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": "/"},
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": str(self.root / "missing")},
        ]:
            with self.subTest(changes=changes):
                self.assert_refusal(self.run_entry("smoke", changes=changes))

    def test_overlap_source_and_delimiter_paths_refuse(self):
        nested = self.sdk / "build"
        nested.mkdir()
        delimiter = self.root / "bad]==]path"
        delimiter.mkdir()
        line = self.root / "bad\npath"
        line.mkdir()
        for changes in [
            {"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": str(self.sdk)},
            {"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": str(nested)},
            {"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": str(REPO)},
            *({"COAKKA_NATIVE_EVIDENCE_BUILD_DIR": str(REPO / product)}
              for product in ("coakka-runtime", "coakka-logger", "coakka-http-runtime", "source-mirrors")),
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": str(delimiter)},
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": str(line)},
        ]:
            with self.subTest(changes=changes):
                self.assert_refusal(self.run_entry("smoke", changes=changes))

    def test_conflicting_modes_and_preferences_refuse(self):
        for changes in [
            {"COAKKA_NATIVE_EVIDENCE_USE_PREBUILT": "1"},
            {"COAKKA_NATIVE_EVIDENCE_USE_PREBUILT": "2"},
            {"COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR": str(self.root)},
            {"COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS": "2"},
            {"COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS": "1", "COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN": "1"},
        ]:
            with self.subTest(changes=changes):
                self.assert_refusal(self.run_entry("smoke", changes=changes))
        for args in [("unknown",), ("connection-strategies", "extra"), ("file-lane", "extra"), ("native-startup", "extra")]:
            with self.subTest(args=args):
                self.assert_refusal(self.run_entry(*args))

    def test_admission_failure_preserves_cache(self):
        result = self.run_entry("smoke", changes={"CANDIDATE_TEST_FAIL": "admission"})
        self.assert_refusal(result, untouched=False)
        self.assertEqual(self.cache.read_text(), "retain original cache\n")
        self.assertEqual(len(self.calls()), 1)

    def test_build_and_probe_failure_never_execute_traffic(self):
        for stage in ["configure", "build", "probe"]:
            with self.subTest(stage=stage):
                result = self.run_entry("smoke", changes={"CANDIDATE_TEST_FAIL": stage})
                self.assert_refusal(result, untouched=False)
                self.assertFalse(any(row[0] == "@child" for row in self.calls()))

    def test_metadata_bounds_links_and_extra_lines_refuse(self):
        for style in ["missing", "oversize", "extra", "linked"]:
            with self.subTest(style=style):
                # Each malformed generated metadata path is independently owned.
                for path in self.build.glob("native-candidate-*"):
                    path.unlink()
                result = self.run_entry("smoke", changes={"CANDIDATE_TEST_META": style})
                self.assert_refusal(result, untouched=False)
                self.assertFalse(any(row[0] == "@child" for row in self.calls()))

    def test_child_exit_and_loader_environment_are_preserved_or_cleared(self):
        result = self.run_entry("smoke", changes={"CANDIDATE_TEST_EXIT": "23", "LD_LIBRARY_PATH": str(self.sdk)})
        self.assertEqual(result.returncode, 23, result.stderr)
        self.assertIsNone(json.loads(result.stdout)["loaderPath"])
        self.assert_no_admission_temp()

    def test_unicode_spaces_and_explicit_sanitizer_selection(self):
        renamed = self.root / "SDK space-đường-dẫn"
        self.sdk.rename(renamed)
        result = self.run_entry("smoke", changes={
            "COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX": str(renamed),
            "COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS": "1",
        })
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(any("-DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON" in row for row in self.calls()))

    def test_signal_handoff_has_no_supervising_launcher(self):
        result = self.run_entry("smoke", changes={"CANDIDATE_TEST_SIGNAL": "TERM"})
        self.assertEqual(result.returncode, -15, result.stderr)
        self.assertTrue(any(row[0] == "@child" for row in self.calls()))
        self.assert_no_admission_temp()

    def test_group_interruption_cleans_admission_before_exec(self):
        marker = self.root / "holding"
        for stage in ["admission", "configure", "build"]:
            with self.subTest(stage=stage):
                env = dict(self.env, CANDIDATE_TEST_HOLD=stage, CANDIDATE_TEST_MARKER=str(marker))
                process = subprocess.Popen(
                    ["bash", str(ENTRY), "smoke"], env=env, cwd=self.root,
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
                )
                try:
                    deadline = time.monotonic() + 5
                    while not marker.exists() and process.poll() is None and time.monotonic() < deadline:
                        time.sleep(.01)
                    self.assertTrue(marker.exists())
                    os.killpg(process.pid, signal.SIGTERM)
                    process.communicate(timeout=5)
                    self.assertNotEqual(process.returncode, 0)
                    self.assert_no_admission_temp()
                finally:
                    if process.poll() is None:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.communicate(timeout=5)
                    marker.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
