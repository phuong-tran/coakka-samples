import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export const root = fileURLToPath(new URL("..", import.meta.url));
export const python = process.env.PYTHON || join(root, ".venv", process.platform === "win32" ? "Scripts/python.exe" : "bin/python");

// Only test children and small test files are supervised here. No business transport.
export function child(exe, args) {
  const proc = spawn(exe, args, { cwd: root, stdio: ["pipe", "pipe", "pipe"],
    env: { ...process.env, PYTHONDONTWRITEBYTECODE: "1", PYTHONUNBUFFERED: "1" } });
  let output = "", errors = "";
  proc.stdout.setEncoding("utf8"); proc.stderr.setEncoding("utf8");
  proc.stdout.on("data", data => { output = (output + data).slice(-65536); });
  proc.stderr.on("data", data => { errors = (errors + data).slice(-65536); });
  const exited = new Promise((res, rej) => { proc.once("error", rej); proc.once("close", (code, signal) => res({ code, signal })); });
  // Attach immediately so a spawn failure is never an unhandled rejection.
  exited.catch(() => {});
  return { proc, exited, get output() { return output; }, get errors() { return errors; } };
}

export async function line(c, text) {
  if (c.output.includes(text)) return;
  await new Promise((res, rej) => {
    const timer = setTimeout(() => done(new Error(`Missing ${text}: ${c.output} ${c.errors}`)), 10000);
    const data = () => { if (c.output.includes(text)) done(); };
    const end = () => done(new Error(`Child exited before ${text}: ${c.output} ${c.errors}`));
    const done = error => { clearTimeout(timer); c.proc.stdout.off("data", data); c.proc.off("close", end); c.proc.off("error", done); error ? rej(error) : res(); };
    c.proc.stdout.on("data", data); c.proc.once("close", end); c.proc.once("error", done);
    data();
  });
}

export async function stop(c, force = false) {
  if (c.proc.exitCode !== null || c.proc.signalCode !== null) return c.exited;
  if (force) c.proc.kill("SIGKILL"); else c.proc.stdin.end("\n");
  const timer = setTimeout(() => c.proc.kill("SIGKILL"), 5000);
  try { return await c.exited; } finally { clearTimeout(timer); }
}

export async function freePort() {
  const server = createServer();
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  const port = server.address().port;
  await new Promise((res, rej) => server.close(error => error ? rej(error) : res()));
  return port;
}

export async function service(t, mode) {
  const port = await freePort();
  const args = mode ? ["tests/service_fixture.py", String(port), mode] : ["pricing_service.py", "--port", String(port)];
  const c = child(python, ["-B", "-Werror", ...args]);
  t.after(() => stop(c));
  await line(c, `Pricing service ready at 127.0.0.1:${port}`);
  return { ...c, child: c, port };
}
