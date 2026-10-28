#!/usr/bin/env python3
"""Verify actual settings/project-directory mapping without unrelated product builds."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]


class GradleLayoutControls(unittest.TestCase):
    def test_actual_settings_maps_all_product_descriptors(self) -> None:
        with tempfile.TemporaryDirectory(prefix="gradle-layout-", dir=os.environ["COAKKA_DOC_TEST_ROOT"]) as temporary:
            work = Path(temporary)
            source = (REPO / "settings.gradle.kts").read_text()
            (work / "settings.gradle.kts").write_text(source)
            (work / "build.gradle.kts").write_text("// Empty tasks: verify directory ownership only.\n")
            projects = re.findall(r'^include\("([^"]+)"\)', source, re.MULTILINE)
            self.assertTrue(projects)
            expected = {}
            for logical in projects:
                parts = logical.split(":")
                parts[0] = {"runtime": "coakka-runtime", "logger": "coakka-logger"}[parts[0]]
                directory = work.joinpath(*parts)
                directory.mkdir(parents=True, exist_ok=True)
                expected[":" + logical] = str(directory)
            init = work / "verify.gradle"
            init.write_text("gradle.projectsEvaluated { gradle.rootProject.allprojects { println('P524_PROJECT=' + groovy.json.JsonOutput.toJson([path: path, directory: projectDir.absolutePath])) } }\n")
            env = dict(os.environ)
            result = subprocess.run(["bash", str(REPO / "gradlew"), "-p", str(work), "projects", "--offline",
                                     "--no-daemon", "--max-workers=1", "--console=plain", "-I", str(init),
                                     "--project-cache-dir", str(work / "project-cache")],
                                    env=env, capture_output=True, text=True, timeout=60, check=False)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            observed = {}
            for line in result.stdout.splitlines():
                if line.startswith("P524_PROJECT="):
                    row = json.loads(line.removeprefix("P524_PROJECT="))
                    observed[row["path"]] = row["directory"]
            for logical, physical in expected.items():
                self.assertEqual(observed.get(logical), physical, logical)


if __name__ == "__main__":
    unittest.main()
