// App installation owns files/processes, not native defaults or capabilities.
import { createHash } from "node:crypto";
import { existsSync, lstatSync, mkdirSync, readdirSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { admitPackage, admitRuntime, readRegular, runChild, verifyArchive, verifyInstalled, verifyMemberTree } from "./runtime-js-candidate.mjs";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sha = bytes => createHash("sha256").update(bytes).digest("hex");
const fail = message => { throw new Error(message); };
const exact = (object, names) => object && typeof object === "object" && !Array.isArray(object) &&
  Object.keys(object).sort().join("|") === [...names].sort().join("|");
const within = (root, path) => { const part = relative(root, path); return part === "" || (!part.startsWith(".." + sep) && part !== ".." && !isAbsolute(part)); };

export function admitElectronCatalog(catalog) {
  if (!exact(catalog, ["schema", "state", "core", "packages"]) || catalog.schema !== 1 ||
      catalog.state !== "private-candidate" || !exact(catalog.packages, ["node", "electron"]) ||
      !exact(catalog.core, ["version", "gitCommit", "abi"]) || catalog.core.version !== "2.5.1" ||
      catalog.core.gitCommit !== "f6b61768f60a08d81b1dc06625a211ffca8e89e2" || catalog.core.abi !== 1) fail("invalid Electron candidate catalog");
  const node = admitPackage(catalog.packages.node, "node"), electron = admitPackage(catalog.packages.electron, "electron");
  if (node.version !== electron.version || node.minimumRuntime !== "22.0.0" || electron.minimumRuntime !== "42.0.0") fail("candidate closure or baseline differs");
  return { node, electron };
}

export function verifyClosure(app, entries) {
  const modules = join(app, "node_modules");
  verifyInstalled(join(modules, entries.node.name), entries.node);
  verifyMemberTree(join(modules, entries.electron.name), entries.electron);
  const metadata = JSON.parse(readRegular(join(modules, entries.electron.name, "package.json"), 64 * 1024).toString("utf8"));
  if (metadata.name !== entries.electron.name || metadata.version !== entries.electron.version ||
      !exact(metadata.dependencies, [entries.node.name]) || metadata.dependencies[entries.node.name] !== entries.node.version ||
      !exact(metadata.peerDependencies, ["electron"]) || metadata.peerDependencies.electron !== ">=42" ||
      metadata.optionalDependencies) fail("Electron dependency closure differs");
  const found = readdirSync(modules).sort();
  const allowed = [entries.node.name, entries.electron.name];
  if (existsSync(join(modules, ".package-lock.json"))) {
    readRegular(join(modules, ".package-lock.json"), 128 * 1024); allowed.push(".package-lock.json");
  }
  allowed.sort();
  if (JSON.stringify(found) !== JSON.stringify(allowed)) fail("unexpected installed dependency");
}

export async function prepareCandidate(requested, environment = process.env, signal = new AbortController().signal) {
  const allowed = ["COAKKA_RUNTIME_CANDIDATE_PACKAGE", "COAKKA_NODE_CANDIDATE_PACKAGE", "COAKKA_SAMPLE_WORK_ROOT", "COAKKA_ELECTRON_EXECUTABLE", "COAKKA_ESBUILD_MODULE"];
  for (const [key, value] of Object.entries(environment)) {
    const name = key.toUpperCase();
    if (value && ((name.startsWith("COAKKA_") && !allowed.includes(name)) ||
        ["NODE_OPTIONS", "NODE_PATH"].includes(name) || name.startsWith("ELECTRON_") || name.startsWith("NPM_CONFIG_") ||
        name.startsWith("DYLD_") || name.startsWith("LD_"))) fail("remove sample override: " + key);
  }
  const entries = admitElectronCatalog(JSON.parse(readRegular(join(repo, "artifacts/runtime-electron-candidate.json"), 1024 * 1024).toString("utf8")));
  const nodeVersion = admitRuntime("node", entries.node.minimumRuntime);
  const electronArchive = environment.COAKKA_RUNTIME_CANDIDATE_PACKAGE, nodeArchive = environment.COAKKA_NODE_CANDIDATE_PACKAGE;
  const compiler = environment.COAKKA_ESBUILD_MODULE;
  if (![requested, electronArchive, nodeArchive, compiler].every(value => typeof value === "string" && isAbsolute(value))) fail("explicit absolute output, two archives and esbuild module required");
  if (existsSync(requested)) fail("fresh output required");
  const parent = realpathSync(dirname(requested));
  if (lstatSync(dirname(requested)).isSymbolicLink()) fail("output parent must be real");
  const output = join(parent, requested.slice(dirname(requested).length + 1));
  if (within(repo, output) || within(output, repo) || [electronArchive, nodeArchive, compiler].some(path => within(output, realpathSync(path)))) fail("output overlaps source or inputs");
  const archives = { electron: readRegular(electronArchive, 32 * 1024 * 1024), node: readRegular(nodeArchive, 32 * 1024 * 1024) };
  for (const role of ["electron", "node"]) verifyArchive(archives[role], entries[role]);
  readRegular(compiler, 1024 * 1024);
  const files = {};
  for (const name of ["main.mjs", "lifecycle.mjs", "preload.cjs", "index.html", "renderer.js"]) {
    files[name] = readRegular(join(repo, "runtime/electron/desktop-intent", name), 128 * 1024);
  }
  if (signal.aborted) fail("sample interrupted");
  mkdirSync(output);
  const app = join(output, "installed"), stage = join(output, "staging");
  const receipt = { state: "preparing", nodeVersion, coreCommit: "f6b61768f60a08d81b1dc06625a211ffca8e89e2",
    packages: { node: entries.node.sha256, electron: entries.electron.sha256 },
    sources: Object.fromEntries(Object.entries(files).map(([name, bytes]) => [name, sha(bytes)])), published: false };
  try {
    mkdirSync(app); mkdirSync(stage); mkdirSync(join(stage, "cache")); mkdirSync(join(output, "browser-data"));
    writeFileSync(join(app, "package.json"), JSON.stringify({ name: "coakka-electron-sample", private: true, type: "module", main: "main.mjs" }) + "\n", { flag: "wx" });
    for (const role of ["node", "electron"]) writeFileSync(join(stage, role + ".tgz"), archives[role], { flag: "wx" });
    writeFileSync(join(stage, "user.npmrc"), ""); writeFileSync(join(stage, "global.npmrc"), "");
    const env = { ...environment, TMPDIR: stage, TMP: stage, TEMP: stage, npm_config_cache: join(stage, "cache"),
      npm_config_userconfig: join(stage, "user.npmrc"), npm_config_globalconfig: join(stage, "global.npmrc") };
    const options = { cwd: app, env, stdio: "inherit" };
    const executable = realpathSync(process.execPath);
    const npm = process.platform === "win32" ? join(dirname(executable), "node_modules/npm/bin/npm-cli.js") :
      join(dirname(dirname(executable)), "lib/node_modules/npm/bin/npm-cli.js");
    readRegular(npm, 128 * 1024);
    // The external GUI host is deliberate; peer resolution must not download it
    // or rewrite the connector's exact dependency. Both archives remain intact.
    await runChild(executable, [npm, "install", join(stage, "electron.tgz"), join(stage, "node.tgz"),
      "--offline", "--ignore-scripts", "--legacy-peer-deps", "--no-audit", "--no-fund", "--save=false",
      "--package-lock=false", "--workspaces=false"], options, 60000, signal);
    verifyClosure(app, entries);
    for (const [name, bytes] of Object.entries(files)) writeFileSync(join(app, name), bytes, { flag: "wx" });
    await runChild(executable, [join(repo, "scripts/runtime-electron-preload.mjs"), compiler, app], options, 60000, signal);
    readRegular(join(app, "preload.bundle.cjs"), 32 * 1024);
    verifyClosure(app, entries);
    for (const role of ["electron", "node"]) verifyArchive(readRegular(role === "electron" ? electronArchive : nodeArchive, 32 * 1024 * 1024), entries[role]);
    receipt.state = "prepared-not-executed";
  } catch (error) {
    receipt.state = "failed"; receipt.error = String(error);
    try { if (existsSync(app)) rmSync(app, { recursive: true }); }
    catch (cleanup) { receipt.cleanupError = String(cleanup); }
    throw error;
  } finally {
    try { if (existsSync(stage)) rmSync(stage, { recursive: true }); }
    catch (error) { receipt.state = "failed"; receipt.cleanupError = String(error); }
    writeFileSync(join(output, "receipt.json"), JSON.stringify(receipt, null, 2) + "\n", { flag: "wx" });
    if (receipt.cleanupError) fail("candidate cleanup failed; see receipt");
  }
  console.log(JSON.stringify(receipt));
  return output;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const controller = new AbortController(); const stop = () => controller.abort();
  process.on("SIGINT", stop); process.on("SIGTERM", stop);
  try {
    if (process.argv.length !== 3) fail("usage: runtime-electron-candidate.mjs <fresh absolute output>");
    await prepareCandidate(process.argv[2], process.env, controller.signal);
  } catch (error) { console.error("[electron-sample] " + error.message); process.exitCode = 1; }
  finally { process.removeListener("SIGINT", stop); process.removeListener("SIGTERM", stop); }
}
