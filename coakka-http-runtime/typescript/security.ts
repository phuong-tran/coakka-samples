/** Runnable TLS and mutual-TLS listener sample shared by Node.js and Bun. */

import {
  Builder,
  ListenerProtocol,
  OutboundReason,
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
      let bytes = 0;
      response.on("error", reject);
      response.on("data", (chunk: Uint8Array) => {
        bytes += chunk.byteLength;
        if (bytes > 4096) { response.destroy(new Error("secure peer response exceeds bound")); return; }
        chunks.push(chunk);
      });
      response.on("end", () => resolveResponse(Object.freeze({
        status: response.statusCode ?? 0,
        body: Buffer.concat(chunks).toString("utf8"),
      })));
    });
    request.setTimeout(5000, () => request.destroy(new Error("secure peer timeout")));
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
      // The native outbound lane performs certificate verification too. The
      // https client above is only a test peer, never a connector substitute.
      const clientBuilder = new Builder()
        .outboundTrust(11n, await readFile(resolve(fixtures, "ca.pem"), "utf8"))
        .outboundTarget({ name: "secure", generation: 3n, endpoints: [{
          nodeId: "secure-loopback", connectHost: "127.0.0.1", connectPort: service.port,
          httpAuthority: `localhost:${service.port}`, security: sample.security,
          tlsPeerIdentity: "localhost", tlsTrustGeneration: 11n,
          tlsClientIdentityGeneration: sample.security === TransportSecurity.MUTUAL_TLS ? 12n : 0n,
        }] })
        .get("/ready", () => Response.text("ready"));
      if (sample.security === TransportSecurity.MUTUAL_TLS) {
        clientBuilder.outboundIdentity(12n,
          await readFile(resolve(fixtures, "client.pem"), "utf8"),
          await readFile(resolve(fixtures, "client.key"), "utf8"));
      }
      const client = clientBuilder.start();
      try {
        const call = client.outboundSubmit({ logicalTarget: "secure", method: "GET",
          target: "/secure", timeoutMillis: 3000 });
        const terminal = await client.takeOutbound(4000);
        if (terminal === null || terminal.call.slot !== call.slot ||
            terminal.call.generation !== call.generation || terminal.reason !== OutboundReason.RESPONSE ||
            terminal.targetGeneration !== 3n || terminal.responseStatus !== 200 ||
            new TextDecoder().decode(terminal.body) !== sample.body) {
          throw new Error("Core outbound TLS identity/result mismatch");
        }
      } finally { await client.close(); }
    } finally {
      await service.close();
    }
  }
  process.stdout.write("typescript-security-smoke=pass\n");
}

await main();
