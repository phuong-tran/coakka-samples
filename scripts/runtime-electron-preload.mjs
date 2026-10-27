// Cold build tool only; never installed or loaded by the renderer.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { join } from "node:path";
import { writeFileSync } from "node:fs";

if (process.argv.length !== 4) throw new Error("expected esbuild module and app output");
const [modulePath, app] = process.argv.slice(2);
const compiler = createRequire(import.meta.url)(modulePath);
assert.equal(compiler.version, "0.27.7", "explicit qualified preload compiler");
try {
  const result = await compiler.build({ entryPoints: [join(app, "preload.cjs")],
    outfile: join(app, "preload.bundle.cjs"), bundle: true, platform: "node", format: "cjs",
    target: "es2022", minify: true, sourcemap: false, legalComments: "none",
    external: ["electron"], metafile: true, logLevel: "silent" });
  assert.equal(result.warnings.length, 0);
  assert.deepEqual(Object.values(result.metafile.outputs).flatMap(value => value.imports).map(value => value.path), ["electron"]);
  const sources = Object.keys(result.metafile.inputs);
  assert.equal(sources.length, 2);
  assert.ok(sources.some(name => name.endsWith("coakka-v2-connector-electron/dist/preload.cjs")));
  assert.ok(!sources.some(name => name.includes("coakka-v2-connector-node")));
  writeFileSync(join(app, "../preload-metafile.json"), JSON.stringify(result.metafile, null, 2) + "\n", { flag: "wx" });
} finally { compiler.stop(); }
