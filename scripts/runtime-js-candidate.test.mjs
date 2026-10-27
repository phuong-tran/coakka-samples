import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { admitCatalog, admitRuntime, readRegular, runChild, runSample, verifyArchive, verifyInstalled } from "./runtime-js-candidate.mjs";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const catalog = JSON.parse(readFileSync(join(repo, "artifacts/runtime-js-candidate.json"), "utf8"));
const sha = (value) => createHash("sha256").update(value).digest("hex");
const parent = process.env.COAKKA_SAMPLE_WORK_ROOT;
assert.ok(parent, "tests require an explicit external work root");
function fixture(fn) {
  return async () => {
    const dir = mkdtempSync(join(parent, "coakka-js-control-"));
    try { await fn(dir); } finally { rmSync(dir, { recursive: true, force: true }); }
  };
}

test("catalog admits both exact roles and rejects foreign structure", () => {
  for (const role of ["node", "bun"]) assert.equal(admitCatalog(catalog, role).name, `coakka-v2-connector-${role}`);
  assert.throws(() => admitCatalog(catalog, "other"));
  for (const mutate of [
    (c) => { c.extra = true; }, (c) => { c.state = "published"; },
    (c) => { c.packages.node.name = c.packages.bun.name; },
    (c) => { c.packages.node.bytes = 33 * 1024 * 1024; },
    (c) => { c.packages.node.members["../outside"] = { bytes: 1, sha256: "0".repeat(64) }; },
    (c) => { c.packages.node.members["package.json"].bytes = 0; },
    (c) => { c.packages.node.sha256 = "oops"; },
    (c) => { c.packages.node.members = {}; },
  ]) {
    const changed = structuredClone(catalog); mutate(changed);
    assert.throws(() => admitCatalog(changed, "node"));
  }
});

test("baseline comparisons distinguish runtimes and patch versions", () => {
  assert.equal(admitRuntime("node", "22.0.0", { node: "22.0.0" }), "22.0.0");
  assert.equal(admitRuntime("bun", "1.3.14", { bun: "1.3.14" }), "1.3.14");
  assert.equal(admitRuntime("bun", "1.3.14", { bun: "1.4.0" }), "1.4.0");
  /** @type {Array<[string, string, {node?: string, bun?: string}]>} */
  const refusals = [
    ["node", "22.0.0", { node: "21.9.9" }], ["bun", "1.3.14", { bun: "1.3.13" }],
    ["node", "22.0.0", { node: "22.0.0", bun: "1.3.14" }],
    ["bun", "1.3.14", { node: "22.0.0" }], ["node", "22.0.0", { node: "22.0.0-dev" }],
  ];
  for (const [role, minimum, versions] of refusals) assert.throws(() => admitRuntime(role, minimum, versions));
});

test("private candidate admits stable and dated dev identities without accepting version ranges", () => {
  for (const version of ["2.5.5", "2.5.5-dev.20261024"]) {
    const changed = structuredClone(catalog);
    changed.packages.node.version = version;
    changed.packages.node.archive = `coakka-v2-connector-node-${version}.tgz`;
    assert.equal(admitCatalog(changed, "node").version, version);
    changed.state = "published";
    assert.throws(() => admitCatalog(changed, "node"));
  }
  for (const version of ["^2.5.5", "2.5", "2.5.5-dev", "2.5.5-dev.2026102", "2.5.5-rc.1", "2.5.5+other", "2.5.5\n"]) {
    const changed = structuredClone(catalog);
    changed.packages.node.version = version;
    changed.packages.node.archive = `coakka-v2-connector-node-${version}.tgz`;
    assert.throws(() => admitCatalog(changed, "node"));
  }
});

test("archive length and digest both gate admission", () => {
  const bytes = Buffer.from("archive"), entry = { bytes: bytes.length, sha256: sha(bytes) };
  verifyArchive(bytes, entry);
  assert.throws(() => verifyArchive(Buffer.from("archivf"), entry));
  assert.throws(() => verifyArchive(Buffer.from("archiv"), entry));
});

test("regular input bounds and links refuse", fixture((dir) => {
  const file = join(dir, "input"); writeFileSync(file, "abc");
  assert.equal(readRegular(file, 3).toString(), "abc");
  assert.throws(() => readRegular(file, 2));
  assert.throws(() => readRegular(dir, 3));
  // Windows file symlinks require privileges; use its unprivileged directory
  // junction to exercise the real reparse-point refusal, not a skipped check.
  symlinkSync(process.platform === "win32" ? dir : file, join(dir, "link"),
    process.platform === "win32" ? "junction" : "file");
  assert.throws(() => readRegular(join(dir, "link"), 3));
}));

test("installed exact tree refuses changed/missing/extra/link/dependency closure", fixture((dir) => {
  const metadata = JSON.stringify({ name: "test", version: "1" });
  const entry = { name: "test", version: "1", members: {
    "package.json": { bytes: Buffer.byteLength(metadata), sha256: sha(metadata) },
    "main.js": { bytes: 3, sha256: sha("abc") },
  } };
  writeFileSync(join(dir, "package.json"), metadata); writeFileSync(join(dir, "main.js"), "abc");
  verifyInstalled(dir, entry);
  writeFileSync(join(dir, "main.js"), "abd"); assert.throws(() => verifyInstalled(dir, entry));
  rmSync(join(dir, "main.js")); assert.throws(() => verifyInstalled(dir, entry));
  writeFileSync(join(dir, "main.js"), "abc"); writeFileSync(join(dir, "extra"), "x");
  assert.throws(() => verifyInstalled(dir, entry)); rmSync(join(dir, "extra"));
  symlinkSync(process.platform === "win32" ? dir : join(dir, "main.js"), join(dir, "link"),
    process.platform === "win32" ? "junction" : "file");
  assert.throws(() => verifyInstalled(dir, entry));
  rmSync(join(dir, "link"));
  const bad = JSON.stringify({ name: "test", version: "1", dependencies: { other: "*" } });
  writeFileSync(join(dir, "package.json"), bad);
  entry.members["package.json"] = { bytes: Buffer.byteLength(bad), sha256: sha(bad) };
  assert.throws(() => verifyInstalled(dir, entry));
}));

test("preflight failure does not create a task directory or delete caller data", fixture(async (dir) => {
  writeFileSync(join(dir, "keep"), "keep");
  await assert.rejects(runSample("node", "basic", { COAKKA_RUNTIME_LIB: "wrong" }));
  await assert.rejects(runSample("node", "basic", { coakka_runtime_lib: "wrong" }));
  await assert.rejects(runSample("node", "unknown", {}));
  await assert.rejects(runSample("node", "basic", {
    COAKKA_SAMPLE_WORK_ROOT: dir, COAKKA_RUNTIME_CANDIDATE_PACKAGE: "relative.tgz",
  }));
  const archive = join(dir, "bad.tgz"); writeFileSync(archive, "bad");
  await assert.rejects(runSample("node", "basic", {
    COAKKA_SAMPLE_WORK_ROOT: dir, COAKKA_RUNTIME_CANDIDATE_PACKAGE: archive,
  }));
  assert.deepEqual(readdirSync(dir).sort(), ["bad.tgz", "keep"]);
}));

test("child success, exit failure and spawn failure are reaped", async () => {
  const signal = new AbortController().signal;
  await runChild(process.execPath, ["-e", "process.exit(0)"], { stdio: "ignore" }, 2000, signal);
  await assert.rejects(runChild(process.execPath, ["-e", "process.exit(4)"], { stdio: "ignore" }, 2000, signal));
  await assert.rejects(runChild(join(repo, "not-an-executable"), [], { stdio: "ignore" }, 2000, signal));
});

test("child deadline and cancellation terminate before returning", fixture(async (dir) => {
  const marker = join(dir, "late");
  const args = ["-e", `setTimeout(() => require('node:fs').writeFileSync(${JSON.stringify(marker)}, 'bad'), 700)`];
  await assert.rejects(runChild(process.execPath, args, { stdio: "ignore" }, 80, new AbortController().signal), /deadline/);
  const controller = new AbortController();
  const pending = runChild(process.execPath, args, { stdio: "ignore" }, 2000, controller.signal);
  setTimeout(() => controller.abort(), 80);
  await assert.rejects(pending, /interrupted/);
  assert.equal(existsSync(marker), false);
  controller.abort();
  await assert.rejects(runChild(process.execPath, args, { stdio: "ignore" }, 2000, controller.signal));
}));

for (const [role, sample] of [["node", "basic"], ["bun", "basic"], ["node", "deadletter"]]) {
  for (const failure of [false, true]) {
    test(`${role}/${sample} ${failure ? "failure" : "success"} awaits lifecycle cleanup`, fixture(async (dir) => {
      const fake = `export const state = {closed:false,retired:false};
        export const DeliveryHint = {ROUTER_DEFAULT:0}, PayloadFormat={JSON:1};
        export class PayloadIdentity { constructor() {} }
        export const localRoute = () => ({});
        export class DeadletterError extends Error { constructor(d) {super('typed');this.deadletter=d;} }
        const envelope={target:'samples.runtime.node.deadletter.missing'};
        const deadletter={reason:2,original_envelope:envelope,active_generation:1};
        const runtime = { runtimeInfo:()=>({abiVersion:1,runtimeVersion:'test',gitCommit:'test'}),
          runtimeStartupConfig:()=>({requestedCpuPolicy:0,connection:{mode:3},monitorEnabled:false,delivery:{requestMode:2,responseMode:2}}),
          registerHandler:()=>{}, askJson:async()=>{
            if (${failure}) throw new Error('injected ask failure');
            if (${sample === "deadletter"}) throw new DeadletterError(deadletter);
            return {echo:'hello-runtime-${role}'}; },
          stats:()=>({appliedGeneration:1,routeCount:1,routeMissCount:1,deadletterCount:1}),
          clientStats:()=>({deliveredRequests:1,matchedResponses:1,matchedDeadletters:1}),
          deadletters:()=>({next:async()=>({done:false,value:{matchedPendingRequest:true,deadletter}}),
            return:async()=>{await new Promise(r=>setTimeout(r,5));state.retired=true;}}),
          close:async()=>{await new Promise(r=>setTimeout(r,5));state.closed=true;} };
        export const RuntimeHost={start:()=>runtime}, BunRuntimeHost=RuntimeHost;
        export const NodeRuntimeClient={}, BunRuntimeClient=NodeRuntimeClient;`;
      writeFileSync(join(dir, "fake.mjs"), fake);
      const source = readFileSync(join(repo, "runtime", role, sample, "main.mjs"), "utf8")
        .replace(`"coakka-v2-connector-${role}"`, '"./fake.mjs"');
      writeFileSync(join(dir, "main.mjs"), source);
      const driver = `import assert from 'node:assert/strict'; import {state} from './fake.mjs';
        ${failure ? "await assert.rejects(import('./main.mjs'), /injected ask failure/);" : "await import('./main.mjs');"}
        assert.equal(state.closed,true); assert.equal(state.retired,${sample === "deadletter"});`;
      writeFileSync(join(dir, "driver.mjs"), driver);
      await runChild(process.execPath, [join(dir, "driver.mjs")], { stdio: "ignore" }, 3000, new AbortController().signal);
    }));
  }
}
