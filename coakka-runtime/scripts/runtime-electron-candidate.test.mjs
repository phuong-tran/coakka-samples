import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { admitElectronCatalog, prepareCandidate, verifyClosure } from "./runtime-electron-candidate.mjs";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const catalog = JSON.parse(readFileSync(join(repo, "coakka-runtime/artifacts/runtime-electron-candidate.json"), "utf8"));
const parent = process.env.COAKKA_SAMPLE_WORK_ROOT;
assert.ok(parent, "explicit external fixture root required");
const sha = bytes => createHash("sha256").update(bytes).digest("hex");
function fixture(work) {
  return async () => {
    const root = mkdtempSync(join(parent, "electron-control-"));
    try { await work(root); } finally { rmSync(root, { recursive: true }); }
  };
}
function installed(root) {
  const entries = {};
  mkdirSync(join(root, "node_modules"));
  for (const role of ["node", "electron"]) {
    const name = "coakka-v2-connector-" + role;
    const metadata = { name, version: "1" };
    if (role === "electron") {
      metadata.dependencies = { "coakka-v2-connector-node": "1" };
      metadata.peerDependencies = { electron: ">=42" };
    }
    const bytes = JSON.stringify(metadata), directory = join(root, "node_modules", name);
    mkdirSync(directory); writeFileSync(join(directory, "package.json"), bytes);
    entries[role] = { name, version: "1", members: { "package.json": { bytes: Buffer.byteLength(bytes), sha256: sha(bytes) } } };
  }
  return entries;
}

test("closed exact two-package candidate with fixed Core and runtime baselines", () => {
  assert.equal(admitElectronCatalog(catalog).electron.name, "coakka-v2-connector-electron");
  for (const change of [c => { c.extra = true; }, c => { c.state = "public"; },
    c => { c.core.abi = 2; }, c => { c.core.gitCommit = "0".repeat(40); },
    c => { c.packages.electron.name = c.packages.node.name; },
    c => { c.packages.electron.minimumRuntime = "41.0.0"; },
    c => { c.packages.node.minimumRuntime = "24.0.0"; },
    c => { c.packages.electron.version = "2.5.6-dev.20261024"; c.packages.electron.archive = "coakka-v2-connector-electron-2.5.6-dev.20261024.tgz"; },
    c => { c.packages.other = c.packages.node; },
    c => { c.packages.electron.members["../outside"] = { bytes: 1, sha256: "0".repeat(64) }; }]) {
    const changed = structuredClone(catalog); change(changed); assert.throws(() => admitElectronCatalog(changed));
  }
});

test("exact installed closure with optional npm hidden lock", fixture(root => {
  const entries = installed(root); verifyClosure(root, entries);
  writeFileSync(join(root, "node_modules/.package-lock.json"), "{}"); verifyClosure(root, entries);
  writeFileSync(join(root, "node_modules/foreign"), "x"); assert.throws(() => verifyClosure(root, entries));
}));

test("missing/extra/damaged package member refuses", fixture(root => {
  const entries = installed(root), metadata = join(root, "node_modules/coakka-v2-connector-electron/package.json");
  const original = readFileSync(metadata);
  writeFileSync(metadata, "{}"); assert.throws(() => verifyClosure(root, entries));
  rmSync(metadata); assert.throws(() => verifyClosure(root, entries));
  writeFileSync(metadata, original);
  writeFileSync(join(dirname(metadata), "extra"), "x"); assert.throws(() => verifyClosure(root, entries));
}));

test("even pinned metadata cannot redirect the Node dependency", fixture(root => {
  const entries = installed(root), path = join(root, "node_modules/coakka-v2-connector-electron/package.json");
  const metadata = JSON.parse(readFileSync(path, "utf8")); metadata.dependencies[entries.node.name] = "*";
  const bytes = JSON.stringify(metadata); writeFileSync(path, bytes);
  entries.electron.members["package.json"] = { bytes: Buffer.byteLength(bytes), sha256: sha(bytes) };
  assert.throws(() => verifyClosure(root, entries), /closure differs/);
}));

test("real symlink/junction and hidden lock link refuse", fixture(root => {
  const entries = installed(root), modules = join(root, "node_modules");
  symlinkSync(modules, join(modules, "link"), process.platform === "win32" ? "junction" : "dir");
  assert.throws(() => verifyClosure(root, entries)); rmSync(join(modules, "link"));
  symlinkSync(modules, join(modules, ".package-lock.json"), process.platform === "win32" ? "junction" : "dir");
  assert.throws(() => verifyClosure(root, entries));
}));

test("case-insensitive source/native/security/package-manager overrides refuse before staging", fixture(async root => {
  for (const name of ["COAKKA_RUNTIME_LIB", "coakka_connector_root", "NODE_OPTIONS", "node_path", "DYLD_LIBRARY_PATH",
    "LD_PRELOAD", "ELECTRON_RUN_AS_NODE", "electron_disable_sandbox", "npm_config_global", "NPM_CONFIG_PREFIX"]) {
    const output = join(root, "out");
    await assert.rejects(prepareCandidate(output, { [name]: "bad" }), /remove sample override/);
    assert.equal(existsSync(output), false);
  }
}));

test("relative inputs and reused output refuse without overwriting", fixture(async root => {
  await assert.rejects(prepareCandidate("relative", {}), /explicit absolute/);
  const file = join(root, "input"); writeFileSync(file, "bad");
  const env = { COAKKA_RUNTIME_CANDIDATE_PACKAGE: file, COAKKA_NODE_CANDIDATE_PACKAGE: file, COAKKA_ESBUILD_MODULE: file };
  const output = join(root, "out"); mkdirSync(output); writeFileSync(join(output, "WIP"), "keep");
  await assert.rejects(prepareCandidate(output, env), /fresh output/);
  assert.equal(readFileSync(join(output, "WIP"), "utf8"), "keep");
}));

test("archive digest fails before creating new output", fixture(async root => {
  const file = join(root, "input"); writeFileSync(file, "bad");
  const output = join(root, "out");
  await assert.rejects(prepareCandidate(output, { COAKKA_RUNTIME_CANDIDATE_PACKAGE: file,
    COAKKA_NODE_CANDIDATE_PACKAGE: file, COAKKA_ESBUILD_MODULE: file }), /archive identity/);
  assert.equal(existsSync(output), false);
}));

// Product relocation must not narrow the repository-wide output exclusion.
test("all repository products and provenance trees reject output before staging", fixture(async root => {
  const file = join(root, "input"); writeFileSync(file, "bad");
  const env = { COAKKA_RUNTIME_CANDIDATE_PACKAGE: file, COAKKA_NODE_CANDIDATE_PACKAGE: file, COAKKA_ESBUILD_MODULE: file };
  for (const product of ["coakka-runtime", "coakka-logger", "coakka-http-runtime", "source-mirrors", "docs"]) {
    const output = join(repo, product, "p524-refused-output");
    assert.equal(existsSync(output), false);
    await assert.rejects(prepareCandidate(output, env), /output overlaps source or inputs/);
    assert.equal(existsSync(output), false);
  }
}));
