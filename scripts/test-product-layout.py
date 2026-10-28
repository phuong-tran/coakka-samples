#!/usr/bin/env python3
"""Cold dispatcher regression: product ownership, argument handoff and CWD independence."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]


class ProductLayoutControls(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="product-layout-", dir=os.environ["COAKKA_DOC_TEST_ROOT"])
        self.addCleanup(self.temporary.cleanup)
        self.work = Path(self.temporary.name)
        self.repo = self.work / "repository"
        self.cwd = self.work / "unrelated"
        self.cwd.mkdir()
        for name in ("run.sh", "scripts/sample-utils.sh", "scripts/sample-metadata.sh",
                     "coakka-runtime/run.sh", "coakka-runtime/scripts/sample-metadata.sh",
                     "coakka-logger/run.sh", "coakka-logger/scripts/sample-metadata.sh"):
            destination = self.repo / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / name, destination)
        for name in ("coakka-runtime/node/basic", "coakka-logger/node/basic", "coakka-runtime/runtime-test",
                     "coakka-runtime/tools/coakka-client", "coakka-runtime/tools/coakka-runtime-inspect",
                     "coakka-runtime/addons/artifact-publisher-https/native", "coakka-runtime/containers/node-python"):
            directory = self.repo / name
            directory.mkdir(parents=True, exist_ok=True)
            (directory / "run.sh").write_text("#!/usr/bin/env bash\nprintf '%s\\n' '" + name + "' \"$@\"\n")

    def run_entry(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(["bash", str(self.repo / "run.sh"), *args], cwd=self.cwd,
                              capture_output=True, text=True, timeout=10, check=False)

    def test_full_products_and_semantic_selectors(self) -> None:
        for product, alias in (("coakka-runtime", "runtime"), ("coakka-logger", "logger")):
            for selector in (product, alias):
                for args in ((selector, "node", "basic"), (selector + "/node/basic",)):
                    with self.subTest(args=args):
                        result = self.run_entry(*args)
                        self.assertEqual(result.returncode, 0, result.stderr)
                        self.assertEqual(result.stdout.splitlines(), [product + "/node/basic"])

    def test_tool_harness_addon_and_container_argument_ownership(self) -> None:
        for selector, expected in (("runtime-test", "runtime-test"), ("client", "tools/coakka-client"),
                                   ("inspect", "tools/coakka-runtime-inspect")):
            result = self.run_entry("coakka-runtime", selector, "one argument; no shell", "--flag")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.splitlines(), ["coakka-runtime/" + expected, "one argument; no shell", "--flag"])
        for args, expected_lines in [(("coakka-runtime", "addons", "https", "check"), ["coakka-runtime/addons/artifact-publisher-https/native", "check"]),
                               (("coakka-runtime", "containers", "node-python", "smoke"), ["coakka-runtime/containers/node-python", "smoke"])]:
            result = self.run_entry(*args)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.splitlines(), expected_lines)

    def test_traversal_absolute_unknown_and_incomplete_selection_refuse(self) -> None:
        for args in (("coakka-runtime/../coakka-logger/node/basic",), ("coakka-runtime", "../coakka-logger/node/basic"),
                     ("coakka-runtime", "/absolute"), ("coakka-logger", "../node/basic"),
                     ("unknown-product",), ("coakka-runtime", "scenario", "only-one")):
            with self.subTest(args=args):
                result = self.run_entry(*args)
                self.assertNotEqual(result.returncode, 0)

    def test_list_is_product_separated_from_unrelated_cwd(self) -> None:
        result = self.run_entry("list")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("coakka-runtime/node/basic", result.stdout)
        self.assertIn("coakka-logger/node/basic", result.stdout)
        self.assertIn("coakka-http-runtime/verify", result.stdout)
        for line in result.stdout.splitlines()[1:]:
            self.assertTrue(line.strip().startswith(("coakka-runtime/", "coakka-logger/", "coakka-http-runtime/")))

    def test_every_product_language_listing_has_a_real_entrypoint(self) -> None:
        # The repository's actual discovery output, not the synthetic fixture, must resolve.
        result = subprocess.run(["bash", str(REPO / "run.sh"), "list"], cwd=self.cwd,
                                capture_output=True, text=True, timeout=10, check=True)
        for line in result.stdout.splitlines():
            name = line.strip().split(maxsplit=1)[0]
            parts = name.split("/")
            if len(parts) == 3 and parts[0] in ("coakka-runtime", "coakka-logger") and parts[1] in (
                "jvm", "python", "node", "bun", "electron", "tauri", "go", "csharp", "rust", "swift", "zig", "mojo", "native"):
                self.assertTrue((REPO / name / "run.sh").is_file(), name)


if __name__ == "__main__":
    unittest.main()
