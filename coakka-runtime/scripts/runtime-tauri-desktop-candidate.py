#!/usr/bin/env python3
"""Build-only installed desktop consumer of the fixed private Tauri candidate."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
from pathlib import Path
import plistlib
import shutil
import sys
import tomllib

SPEC = importlib.util.spec_from_file_location(
    "cargo_candidate", Path(__file__).with_name("runtime-cargo-candidate.py")
)
assert SPEC is not None and SPEC.loader is not None
CARGO = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CARGO
SPEC.loader.exec_module(CARGO)

SOURCES = (
    "dist/index.html", "dist/main.js", "dist/styles.css", "dist/favicon.svg",
    "src-tauri/Cargo.toml", "src-tauri/Cargo.lock", "src-tauri/build.rs",
    "src-tauri/tauri.conf.json", "src-tauri/capabilities/default.json",
    "src-tauri/icons/icon.png", "src-tauri/icons/icon.ico",
    "src-tauri/src/main.rs", "src-tauri/src/lib.rs", "src-tauri/src/admission.rs",
    "src-tauri/src/browser_gate.rs", "src-tauri/src/browser.rs",
)
BINARY = "coakka-sample-tauri-desktop-intent"
PLATFORMS = {"macos-aarch64": "darwin", "linux-aarch64": "linux", "windows-x86_64": "win32"}


def admit(args: argparse.Namespace) -> tuple[bytes, bytes, dict[str, bytes]]:
    """Read all admitted inputs before reserving cleanup ownership of work."""
    if sys.platform != PLATFORMS[args.target]:
        raise ValueError("this recipe requires a matching native host")
    for path in (args.work.parent, args.source, args.cargo_home, args.target_cache):
        CARGO.directory(path)
    protected = (args.source, args.cargo_home, args.target_cache, args.archive, args.cargo)
    if (not args.work.is_absolute() or args.work.exists() or args.work.is_symlink()
            or any(args.work.is_relative_to(path) or path.is_relative_to(args.work) for path in protected)):
        raise ValueError("fresh non-overlapping absolute work directory required")
    CARGO.regular(args.cargo, 64 << 20)
    suffix = ".exe" if args.target == "windows-x86_64" else ""
    CARGO.regular(args.cargo.with_name("rustc" + suffix), 128 << 20)
    CARGO.regular(args.cargo.with_name("cargo-clippy" + suffix), 64 << 20)
    CARGO.regular(args.cargo.with_name("clippy-driver" + suffix), 128 << 20)
    if args.target == "macos-aarch64":
        CARGO.regular(CARGO.MACOS_TOOLS["codesign"], 32 << 20)
    content = CARGO.regular(args.archive, 16 << 20)
    tool = CARGO.archive_tool(content, CARGO.TAURI)
    sources = {name: CARGO.regular(args.source / name, 256 << 10) for name in SOURCES}
    if sum(map(len, sources.values())) > 2 << 20:
        raise ValueError("desktop source projection exceeds bound")
    manifest = tomllib.loads(sources["src-tauri/Cargo.toml"].decode("utf-8"))
    if (manifest["package"]["rust-version"] != "1.92"
            or manifest["profile"]["release"] != {
                "opt-level": "s", "lto": "thin", "codegen-units": 1,
                "strip": "symbols", "panic": "unwind"}):
        raise ValueError("qualified desktop compiler/release contract required")
    if args.target == "windows-x86_64":
        CARGO.regular(args.cargo.parent.parent /
                      "lib/rustlib/x86_64-pc-windows-msvc/bin/rust-lld.exe", 128 << 20)
    return content, tool, sources


def flags(args: argparse.Namespace, root: Path) -> list[str]:
    # More specific product mappings follow the general mapping. No host,
    # revision, extraction/staging layout or user path belongs in panic data.
    result = ["-Dwarnings", "--remap-path-prefix=" + str(args.work) + "=coakka",
              "--remap-path-prefix=" + str(args.cargo_home) + "=coakka-language-cache",
              "--remap-path-prefix=" + str(root / "app/src-tauri") + "=coakka-desktop",
              "--remap-path-prefix=" + str(root / "RuntimeCandidate/rust") + "=coakka-runtime",
              "--remap-path-prefix=" + str(root / "RuntimeCandidate/tauri-intents") + "=coakka-intents"]
    if args.target == "windows-x86_64":
        linker = args.cargo.parent.parent / "lib/rustlib/x86_64-pc-windows-msvc/bin/rust-lld.exe"
        result += ["-Ctarget-feature=+crt-static", "-Clinker=" + str(linker), "-Clinker-flavor=lld-link"]
    return result


def bundle_info() -> bytes:
    return plistlib.dumps({
        "CFBundleName": "CoAkka Tauri Intent", "CFBundleDisplayName": "CoAkka Tauri Intent",
        "CFBundleIdentifier": "dev.coakka.samples.tauri.intent", "CFBundleExecutable": BINARY,
        "CFBundlePackageType": "APPL", "CFBundleInfoDictionaryVersion": "6.0",
        "CFBundleVersion": "0.1.0", "CFBundleShortVersionString": "0.1.0",
        "LSMinimumSystemVersion": "13.0", "NSHighResolutionCapable": True,
    }, sort_keys=True)


def relocate_macos_payload(app: Path, image: Path, legal: tuple[str, ...]) -> dict[str, object]:
    """Place code and resource data in their normal bundle-owned locations."""
    source = image.parent
    expected = {image.name, "native-manifest.json", *legal}
    if {path.name for path in source.iterdir()} != expected:
        raise ValueError("unexpected deployed bundle component")
    contents = app.parent
    frameworks = contents / "Frameworks"
    resources = contents / "Resources/native/macos-aarch64"
    frameworks.mkdir()
    resources.mkdir(parents=True)
    mappings = {}
    for name in sorted(expected):
        original = source / name
        data = CARGO.regular(original, 64 << 20 if original == image else 1 << 20)
        destination = frameworks / name if original == image else resources / name
        original.rename(destination)
        if CARGO.digest(CARGO.regular(destination, 64 << 20)) != CARGO.digest(data):
            raise ValueError("bundle component changed during placement")
        source_name = "native/macos-aarch64/" + name if original == image else name
        mappings["rust/" + source_name] = {
            "installed_path": destination.relative_to(contents.parent).as_posix(),
            "bytes": len(data), "sha256": CARGO.digest(data),
        }
    source.rmdir()
    source.parent.rmdir()
    return {"source_archive_sha256": CARGO.TAURI.archive_sha,
            "native_manifest_role": "original dependency component provenance, not installed paths",
            "files": mappings}


def finalize_macos_bundle(args: argparse.Namespace, app: Path, project: Path,
                          env: dict[str, str]) -> list[dict[str, object]]:
    """Seal the owned app bundle without re-signing its frozen native image."""
    native = app.parent / "Frameworks/libcoakka_runtime_v2.dylib"
    before = CARGO.digest(CARGO.regular(native, 64 << 20))
    bundle = app.parent.parent
    identifier = plistlib.loads(bundle_info())["CFBundleIdentifier"]
    tool = str(CARGO.MACOS_TOOLS["codesign"])
    commands = (
        ("sign", [tool, "--force", "--sign", "-", "--identifier", identifier,
                  "--timestamp=none", str(bundle)]),
        ("verify-signature", [tool, "--verify", "--strict", str(bundle)]),
    )
    rows = [CARGO.child(command, project, env, args.work / (name + ".log"), 30)
            for name, command in commands]
    if CARGO.digest(CARGO.regular(native, 64 << 20)) != before:
        raise ValueError("native image changed during app finalization")
    return rows


def build(args: argparse.Namespace) -> Path:
    content, tool, sources = admit(args)
    args.work.mkdir()  # Exclusive reservation: only descendants created below are ours.
    root = args.work / "build"
    root.mkdir()
    rows: list[dict] = []
    receipt = {"state": "failed", "archive_sha256": CARGO.TAURI.archive_sha,
               "sources": {name: CARGO.digest(data) for name, data in sources.items()},
               "commands": rows, "core_rebuilt": False, "app_executed": False}
    try:
        helper = root / "package_tools.py"
        CARGO.write_new(helper, tool)
        spec = importlib.util.spec_from_file_location("desktop_package_tools", helper)
        if spec is None or spec.loader is None:
            raise ValueError("cannot load admitted package helper")
        package_tools = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(package_tools)
        snapshot = root / "candidate.tar.gz"
        CARGO.write_new(snapshot, content)
        package = root / "RuntimeCandidate"
        package_tools.unpack(snapshot, CARGO.TAURI.archive_sha, package)
        project = root / "app"
        for name, data in sources.items():
            path = project / name
            path.parent.mkdir(parents=True, exist_ok=True)
            # Fresh destination-host timestamps prevent stale build-script
            # reuse after transporting an earlier archive to a shared cache.
            CARGO.write_new(path, data)
        temp = root / "temp"
        temp.mkdir()
        env = {key: value for key, value in os.environ.items()
               if not key.upper().startswith(("COAKKA_", "CARGO_", "RUST", "DYLD_", "LD_"))}
        suffix = ".exe" if args.target == "windows-x86_64" else ""
        rustc = args.cargo.with_name("rustc" + suffix)
        env.update(PATH=str(args.cargo.parent) + os.pathsep + env.get("PATH", ""),
                   RUSTC=str(rustc), CARGO=str(args.cargo), CARGO_HOME=str(args.cargo_home),
                   CARGO_TARGET_DIR=str(args.target_cache), CARGO_NET_OFFLINE="true",
                   CARGO_ENCODED_RUSTFLAGS="\x1f".join(flags(args, root)),
                   TMPDIR=str(temp), TMP=str(temp), TEMP=str(temp))
        rows.append(CARGO.child([str(rustc), "-vV"], project, env, args.work / "compiler.log", 15))
        version = next(line.removeprefix("release: ") for line in
                       (args.work / "compiler.log").read_text().splitlines() if line.startswith("release: "))
        parts = version.split(".")
        if len(parts) != 3 or not all(part.isdecimal() for part in parts) or tuple(map(int, parts)) < (1, 92, 0):
            raise ValueError("stable Rust 1.92 or newer required for this GUI recipe")
        host = next(line.removeprefix("host: ") for line in
                    (args.work / "compiler.log").read_text().splitlines() if line.startswith("host: "))
        if host != CARGO.TARGETS[args.target]:
            raise ValueError("matching native compiler target required")
        clippy = args.cargo.with_name("cargo-clippy" + suffix)
        # Cargo's external-command lookup can prefer a cache-local rustup shim
        # over PATH. Invoke this selected toolchain's real component directly;
        # do not change a global default or allow an implicit tool download.
        rows.append(CARGO.child([str(clippy), "clippy", "-V"], project, env,
                                args.work / "clippy-version.log", 15))
        common = [str(args.cargo), "--locked", "--offline", "--release", "--target", CARGO.TARGETS[args.target], "-j", "4"]
        for command, selection, name in (
                ("build", ["--bin", BINARY], "build.log"),
                ("clippy", ["--no-deps", "--lib", "--bin", BINARY, "--", "-D", "warnings"], "clippy.log"),
                ("test", ["--lib", "--", "--test-threads=1"], "tests.log")):
            program = str(clippy) if command == "clippy" else common[0]
            rows.append(CARGO.child([program, command, *common[1:], *selection], project / "src-tauri",
                                    env, args.work / name, 300))
        filename = BINARY + suffix
        binary = args.target_cache / CARGO.TARGETS[args.target] / "release" / filename
        installed = args.work / "installed"
        app = installed
        if args.target == "macos-aarch64":
            app = installed / "CoAkka Tauri Intent.app/Contents/MacOS"
        app.mkdir(parents=True)
        executable = app / filename
        CARGO.write_new(executable, CARGO.regular(binary, 32 << 20))
        executable.chmod(0o755)
        deployed_image = package_tools.deploy(package / "rust", executable, args.target)
        if args.target == "macos-aarch64":
            CARGO.write_new(app.parent / "Info.plist", bundle_info())
            # deploy's selected-target projection describes its temporary CLI
            # staging. Keep the exact source component provenance in Resources;
            # the separate deployment receipt describes this app's real paths.
            component_manifest = CARGO.regular(package / "rust/native-manifest.json", 1 << 20)
            deployed_manifest = deployed_image.with_name("native-manifest.json")
            deployed_manifest.unlink()
            CARGO.write_new(deployed_manifest, component_manifest)
            receipt["bundle_deployment"] = relocate_macos_payload(
                app, deployed_image, tuple(package_tools.LEGAL))
            rows.extend(finalize_macos_bundle(args, app, project, env))
        receipt.update(state="built-not-executed", executable=executable.relative_to(args.work).as_posix(),
                       executable_sha256=CARGO.digest(executable.read_bytes()), flags=flags(args, root))
        return executable
    except BaseException:
        installed = args.work / "installed"
        if installed.exists():
            shutil.rmtree(installed)
        raise
    finally:
        # Shared offline caches are caller-owned and never cleanup targets.
        pending_failure = sys.exception()
        cleanup_failure = None
        try:
            shutil.rmtree(root)
        except OSError as error:
            cleanup_failure = error
            receipt.update(state="failed-cleanup", cleanup_failure=str(error))
        CARGO.write_new(args.work / "receipt.json", (json.dumps(receipt, indent=2) + "\n").encode())
        if cleanup_failure is not None and pending_failure is None:
            raise RuntimeError("desktop staging cleanup failed; retained receipt") from cleanup_failure


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("archive", "source", "work", "cargo", "cargo-home", "target-cache"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--target", required=True, choices=PLATFORMS)
    args = parser.parse_args()
    print(build(args))


if __name__ == "__main__":
    main()
