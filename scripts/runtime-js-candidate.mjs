// Sample installation only: Core/connector own runtime policy and native loading.
import { createHash } from "node:crypto";
import {
  closeSync, fstatSync, lstatSync, mkdirSync, mkdtempSync,
  openSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync,
} from "node:fs";
import { spawn } from "node:child_process";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const repository = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
const fail = (message) => { throw new Error(message); };
const plain = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const digest = (value) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
const size = (value, maximum) => Number.isSafeInteger(value) && value > 0 && value <= maximum;
const keys = (value, expected) => plain(value) &&
  Object.keys(value).sort().join("|") === [...expected].sort().join("|");

export function readRegular(path, maximum) {
  const before = lstatSync(path);
  if (!before.isFile() || !size(before.size, maximum)) fail("expected bounded regular file");
  const fd = openSync(path, "r");
  try {
    const opened = fstatSync(fd);
    if (!opened.isFile() || opened.dev !== before.dev || opened.ino !== before.ino ||
        opened.size !== before.size) fail("input changed before read");
    const content = readFileSync(fd);
    const after = fstatSync(fd);
    if (content.length !== before.size || after.size !== before.size ||
        after.mtimeMs !== opened.mtimeMs) fail("input changed during read");
    return content;
  } finally {
    closeSync(fd);
  }
}

export function admitCatalog(catalog, role) {
  if (!keys(catalog, ["schema", "state", "core", "packages"]) || catalog.schema !== 1 ||
      catalog.state !== "private-candidate" || !keys(catalog.packages, ["node", "bun"]) ||
      !keys(catalog.core, ["version", "gitCommit", "abi"]) || catalog.core.abi !== 1 ||
      !/^\d+\.\d+\.\d+$/.test(catalog.core.version) ||
      !/^[a-f0-9]{40}$/.test(catalog.core.gitCommit)) fail("invalid candidate catalog");
  if (role !== "node" && role !== "bun") fail("unknown sample runtime");
  return admitPackage(catalog.packages[role], role);
}

// Shared cold package-map mechanics; each runner admits its own closure.
export function admitPackage(entry, role) {
  if (!["node", "bun", "electron"].includes(role)) fail("unknown package role");
  if (!keys(entry, ["name", "version", "minimumRuntime", "archive", "bytes", "sha256", "members"]) ||
      entry.name !== `coakka-v2-connector-${role}` ||
      !/^\d+\.\d+\.\d+(?:-dev\.\d{8})?(?![\s\S])/.test(entry.version) ||
      !/^\d+\.\d+\.\d+$/.test(entry.minimumRuntime) ||
      entry.archive !== `${entry.name}-${entry.version}.tgz` ||
      !size(entry.bytes, 32 * 1024 * 1024) || !digest(entry.sha256) || !plain(entry.members)) {
    fail("invalid candidate package role or identity");
  }
  const members = Object.entries(entry.members);
  if (members.length === 0 || members.length > 64) fail("invalid member count");
  let bytes = 0;
  for (const [name, member] of members) {
    if (!/^[A-Za-z0-9_.\/-]+$/.test(name) || name.split("/").some((p) => p === "" || p === "." || p === "..") ||
        !keys(member, ["bytes", "sha256"]) || !size(member.bytes, 8 * 1024 * 1024) ||
        !digest(member.sha256)) fail("invalid installed member");
    bytes += member.bytes;
  }
  if (bytes > 64 * 1024 * 1024 || !entry.members["package.json"]) fail("invalid package extent");
  return entry;
}

/** @param {{node?: string, bun?: string}} [versions] */
export function admitRuntime(role, minimum, versions = process.versions) {
  const actual = role === "bun" ? versions.bun : (versions.bun ? undefined : versions.node);
  if (typeof actual !== "string" || !/^\d+\.\d+\.\d+$/.test(actual)) fail("wrong sample runtime");
  const a = actual.split(".").map(Number), b = minimum.split(".").map(Number);
  const first = a.findIndex((value, index) => value !== b[index]);
  if (first >= 0 && a[first] < b[first]) fail(`sample requires ${role} >= ${minimum}`);
  return actual;
}

export function verifyArchive(bytes, entry) {
  if (bytes.length !== entry.bytes || hash(bytes) !== entry.sha256) fail("candidate archive identity differs");
}

export function verifyMemberTree(root, entry) {
  if (!lstatSync(root).isDirectory() || lstatSync(root).isSymbolicLink()) fail("package root must be real");
  const found = new Set();
  let count = 0;
  function walk(directory, depth) {
    if (depth > 8) fail("package tree is too deep");
    for (const child of readdirSync(directory)) {
      if (++count > 256) fail("package tree is too large");
      const path = join(directory, child), stat = lstatSync(path);
      if (stat.isSymbolicLink()) fail("package links are not admitted");
      if (stat.isDirectory()) { walk(path, depth + 1); continue; }
      const name = relative(root, path).split(sep).join("/");
      const member = entry.members[name];
      if (!member || !stat.isFile() || stat.size !== member.bytes ||
          hash(readRegular(path, 8 * 1024 * 1024)) !== member.sha256) fail(`installed member differs: ${name}`);
      found.add(name);
    }
  }
  walk(root, 0);
  if (found.size !== Object.keys(entry.members).length) fail("installed package is incomplete");
}

export function verifyInstalled(root, entry) {
  verifyMemberTree(root, entry);
  const metadata = JSON.parse(readRegular(join(root, "package.json"), 64 * 1024).toString("utf8"));
  if (metadata.name !== entry.name || metadata.version !== entry.version ||
      ["dependencies", "optionalDependencies", "peerDependencies", "scripts"].some((key) =>
        metadata[key] && Object.keys(metadata[key]).length !== 0)) fail("package install closure differs");
}

// Exactly one owned child. On cancellation/deadline, reap it before removing its tree.
// No shell, detached process, retry or polling loop. This is not a hostile-process sandbox.
export function runChild(executable, args, options, timeoutMs, signal) {
  return new Promise((resolvePromise, reject) => {
    if (signal.aborted) { reject(new Error("sample interrupted")); return; }
    const child = spawn(executable, args, { ...options, shell: false });
    let failure, escalation;
    function stop(message) {
      if (failure) return;
      failure = new Error(message);
      child.kill("SIGTERM");
      escalation = setTimeout(() => child.kill("SIGKILL"), 1000);
    }
    const abort = () => stop("sample interrupted");
    signal.addEventListener("abort", abort, { once: true });
    const deadline = setTimeout(() => stop("sample child deadline exceeded"), timeoutMs);
    child.once("error", (error) => { failure ??= error; });
    child.once("close", (code, childSignal) => {
      clearTimeout(deadline);
      clearTimeout(escalation);
      signal.removeEventListener("abort", abort);
      if (failure) reject(failure);
      else if (code !== 0) reject(new Error(`sample child failed: ${code ?? childSignal}`));
      else resolvePromise();
    });
  });
}

export async function runSample(role, sample, environment = process.env, signal = new AbortController().signal) {
  const selections = { node: ["basic", "deadletter"], bun: ["basic"] };
  if (!selections[role]?.includes(sample)) fail("unknown sample selection");
  for (const [key, value] of Object.entries(environment)) {
    const name = key.toUpperCase();
    if (value && ((name.startsWith("COAKKA_") &&
        !["COAKKA_RUNTIME_CANDIDATE_PACKAGE", "COAKKA_SAMPLE_WORK_ROOT"].includes(name)) ||
        ["NODE_OPTIONS", "NODE_PATH", "BUN_OPTIONS"].includes(name) ||
        name.startsWith("DYLD_") || name.startsWith("LD_"))) fail(`remove sample override: ${key}`);
  }
  const catalog = JSON.parse(readRegular(join(repository, "artifacts/runtime-js-candidate.json"), 1024 * 1024).toString("utf8"));
  const entry = admitCatalog(catalog, role);
  const version = admitRuntime(role, entry.minimumRuntime);
  const archive = environment.COAKKA_RUNTIME_CANDIDATE_PACKAGE;
  const work = environment.COAKKA_SAMPLE_WORK_ROOT;
  if (!archive || !work || !isAbsolute(archive) || !isAbsolute(work)) {
    fail("set absolute COAKKA_RUNTIME_CANDIDATE_PACKAGE and COAKKA_SAMPLE_WORK_ROOT");
  }
  const root = realpathSync(work);
  if (!lstatSync(work).isDirectory() || lstatSync(work).isSymbolicLink()) fail("work root must be a real directory");
  const bytes = readRegular(archive, 32 * 1024 * 1024);
  verifyArchive(bytes, entry);
  const source = readRegular(join(repository, "runtime", role, sample, "main.mjs"), 128 * 1024);
  if (signal.aborted) fail("sample interrupted");
  const stage = mkdtempSync(join(root, "coakka-js-sample-"));
  try {
    const app = join(stage, "app"), cache = join(stage, "cache");
    mkdirSync(app); mkdirSync(cache);
    writeFileSync(join(app, "package.json"), '{"name":"coakka-runtime-sample","private":true,"type":"module"}\n');
    writeFileSync(join(app, "main.mjs"), source);
    writeFileSync(join(app, "connector.tgz"), bytes);
    writeFileSync(join(stage, "user.npmrc"), "");
    writeFileSync(join(stage, "global.npmrc"), "");
    const env = { ...environment, npm_config_cache: cache, BUN_INSTALL_CACHE_DIR: cache,
      npm_config_userconfig: join(stage, "user.npmrc"), npm_config_globalconfig: join(stage, "global.npmrc"),
      TMPDIR: stage, TMP: stage, TEMP: stage };
    const options = { cwd: app, env, stdio: "inherit" };
    if (role === "node") {
      const npm = join(dirname(realpathSync(process.execPath)), "node_modules/npm/bin/npm-cli.js");
      // Unix Node distributions put npm beside bin/, Windows puts it beside node.exe.
      const unixNpm = join(dirname(dirname(realpathSync(process.execPath))), "lib/node_modules/npm/bin/npm-cli.js");
      const cli = process.platform === "win32" ? npm : unixNpm;
      readRegular(cli, 128 * 1024);
      await runChild(process.execPath, [cli, "install", "./connector.tgz", "--offline", "--ignore-scripts",
        "--no-audit", "--no-fund", "--package-lock=false", "--save=false", "--workspaces=false"], options, 60000, signal);
    } else {
      await runChild(process.execPath, ["add", "./connector.tgz", "--ignore-scripts", "--no-save",
        "--cache-dir", cache], options, 60000, signal);
    }
    verifyInstalled(join(app, "node_modules", entry.name), entry);
    console.log(`coakka_sample_package runtime=${role} hostVersion=${version} version=${entry.version} sha256=${entry.sha256}`);
    await runChild(process.execPath, [join(app, "main.mjs")], options, 30000, signal);
  } finally {
    rmSync(stage, { recursive: true, force: true });
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const controller = new AbortController();
  const stop = () => controller.abort();
  process.on("SIGINT", stop); process.on("SIGTERM", stop);
  try {
    if (process.argv.length !== 4) fail("usage: runtime-js-candidate.mjs node|bun basic|deadletter");
    await runSample(process.argv[2], process.argv[3], process.env, controller.signal);
  } catch (error) {
    console.error(`[runtime-sample] ${error.message}`);
    process.exitCode = 1;
  } finally {
    process.removeListener("SIGINT", stop); process.removeListener("SIGTERM", stop);
  }
}
