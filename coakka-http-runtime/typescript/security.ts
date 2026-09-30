/** Runnable TLS and mutual-TLS listener sample shared by Node.js and Bun. */

import {
  Builder,
  ListenerProtocol,
  Response,
  TransportSecurity,
} from "@coakka/http";
import { readFile } from "node:fs/promises";
import { get, type RequestOptions } from "node:https";
import { resolve } from "node:path";

/** Parse the generated identity directory without accepting silent defaults. */
function fixtureDirectory(): string {
  const index = process.argv.indexOf("--fixtures");
  const value = index >= 0 ? process.argv[index + 1] : undefined;
  if (value === undefined) {
    throw new Error("--fixtures requires a directory");
  }
  return resolve(value);
}

/** Complete one certificate-verifying request and copy its finite body. */
async function secureRequest(
  port: number,
  fixtures: string,
  withIdentity: boolean,
): Promise<Readonly<{ status: number; body: string }>> {
  const options: RequestOptions = {
    hostname: "localhost",
    port,
    path: "/secure",
    ca: await readFile(resolve(fixtures, "ca.pem")),
    minVersion: "TLSv1.2",
  };
  if (withIdentity) {
    options.cert = await readFile(resolve(fixtures, "client.pem"));
    options.key = await readFile(resolve(fixtures, "client.key"));
  }
  return await new Promise((resolveResponse, reject) => {
    const request = get(options, (response) => {
      const chunks: Uint8Array[] = [];
      response.on("data", (chunk: Uint8Array) => chunks.push(chunk));
      response.on("end", () => resolveResponse(Object.freeze({
        status: response.statusCode ?? 0,
        body: Buffer.concat(chunks).toString("utf8"),
      })));
    });
    request.on("error", reject);
  });
}

/** Prove server identity, client trust, and mandatory client identity. */
async function main(): Promise<void> {
  const fixtures = fixtureDirectory();
  const cases = [
    { security: TransportSecurity.TLS, generation: 7n, body: "tls-ready" },
    { security: TransportSecurity.MUTUAL_TLS, generation: 9n, body: "mtls-ready" },
  ] as const;
  for (const sample of cases) {
    const service = new Builder()
      .listener({
        host: "127.0.0.1",
        protocol: ListenerProtocol.HTTP_1_1,
        security: sample.security,
        credentialGeneration: sample.generation,
        credentialId: "typescript-sample-server",
        certificateChainFile: resolve(fixtures, "server.pem"),
        privateKeyFile: resolve(fixtures, "server.key"),
        ...(sample.security === TransportSecurity.MUTUAL_TLS
          ? { trustRootsFile: resolve(fixtures, "ca.pem") }
          : {}),
      })
      .get("/secure", () => Response.text(sample.body))
      .start();
    try {
      if (sample.security === TransportSecurity.MUTUAL_TLS) {
        let rejected = false;
        try {
          await secureRequest(service.port, fixtures, false);
        } catch {
          rejected = true;
        }
        if (!rejected) {
          throw new Error("mutual TLS accepted a client without an identity");
        }
      }
      const observed = await secureRequest(
        service.port,
        fixtures,
        sample.security === TransportSecurity.MUTUAL_TLS,
      );
      if (observed.status !== 200 || observed.body !== sample.body) {
        throw new Error(`secure response mismatch: ${JSON.stringify(observed)}`);
      }
    } finally {
      await service.close();
    }
  }
  process.stdout.write("typescript-security-smoke=pass\n");
}

await main();
