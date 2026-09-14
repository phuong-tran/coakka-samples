#!/usr/bin/env python3
"""Stage exact private application candidate inputs for benchmarking."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    script_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=script_root.parents[2],
    )
    parser.add_argument("--output", type=Path, default=script_root / "artifacts")
    args = parser.parse_args()

    lock = json.loads((script_root / "config/artifacts.lock.json").read_text())
    copied: list[dict[str, str]] = []
    for artifact in lock["artifacts"]:
        destination = args.output / artifact["destination"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        if artifact.get("type") == "git-archive":
            repository = args.workspace_root / artifact["repository"]
            commit = subprocess.run(
                ["git", "-C", str(repository), "rev-parse", f"{artifact['commit']}^{{commit}}"],
                text=True,
                capture_output=True,
                check=True,
            ).stdout.strip()
            if commit != artifact["commit"]:
                raise RuntimeError(f"source commit mismatch: {artifact['id']}")
            subtree = artifact["subtree"]
            tree_spec = f"{commit}^{{tree}}" if subtree == "." else f"{commit}:{subtree}"
            tree = subprocess.run(
                [
                    "git",
                    "-C",
                    str(repository),
                    "rev-parse",
                    tree_spec,
                ],
                text=True,
                capture_output=True,
                check=True,
            ).stdout.strip()
            if tree != artifact["tree"]:
                raise RuntimeError(f"source tree mismatch: {artifact['id']}")
            destination.unlink(missing_ok=True)
            archive_command = [
                "git",
                "-C",
                str(repository),
                "archive",
                "--format=tar",
                f"--output={destination.resolve()}",
                commit,
            ]
            if subtree != ".":
                archive_command.append(subtree)
            subprocess.run(archive_command, check=True)
        elif artifact.get("type") == "local-file":
            source = (
                args.workspace_root
                / artifact["repository"]
                / artifact["source"]
            )
            if not source.is_file():
                raise FileNotFoundError(
                    f"missing private candidate input for {artifact['id']}: {source}"
                )
            if sha256(source) != artifact["sha256"]:
                raise RuntimeError(f"source digest mismatch: {artifact['id']}")
            shutil.copy2(source, destination)
        else:
            publish_root = args.workspace_root / "coakka-publish"
            matches = sorted(publish_root.glob(artifact["source_glob"]))
            if len(matches) != 1:
                raise RuntimeError(
                    f"expected one artifact for {artifact['id']}; found {len(matches)}"
                )
            source = matches[0]
            if not source.is_file():
                raise FileNotFoundError(f"missing artifact: {source}")
            if sha256(source) != artifact["sha256"]:
                raise RuntimeError(f"source digest mismatch: {artifact['id']}")
            shutil.copy2(source, destination)
        copied_actual = sha256(destination)
        if copied_actual != artifact["sha256"]:
            raise RuntimeError(f"staged digest mismatch: {artifact['id']}")
        copied.append(
            {
                "id": artifact["id"],
                "path": str(destination),
                "sha256": copied_actual,
            }
        )
    print(json.dumps({"candidate": lock["candidate"], "artifacts": copied}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
