import { open } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { MAX_BYTES, withQuoteClient } from "./quote-client.mjs";

export async function readCart(file) {
  const handle = await open(file, "r");
  try {
    const buffer = Buffer.alloc(MAX_BYTES + 1);
    let length = 0;
    while (length < buffer.length) {
      const { bytesRead } = await handle.read(buffer, length, buffer.length - length, null);
      if (bytesRead === 0) break;
      length += bytesRead;
    }
    if (length > MAX_BYTES) throw new RangeError("Cart exceeds 16 KiB.");
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(buffer.subarray(0, length)));
  } finally { await handle.close(); }
}

export async function main(args = process.argv.slice(2)) {
  let port = 19360, json = false, file = new URL("./cart.json", import.meta.url);
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--json") json = true;
    else if (arg === "--port" && /^\d+$/.test(args[i + 1] ?? "")) port = Number(args[++i]);
    else if (arg === "--cart" && args[i + 1]) file = args[++i];
    else {
      console.error("Usage: node client.mjs [--port 19360] [--cart cart.json] [--json]");
      return 1;
    }
  }
  try {
    const cart = await readCart(file);
    const reply = await withQuoteClient(port, client => client.quote(cart));
    if (json) console.log(JSON.stringify(reply));
    else if (reply.status === "quoted") {
      console.log(`Quote ${reply.cartId}: ${reply.currency} ${(reply.totalCents / 100).toFixed(2)}`);
      console.log("Calculated by the Python pricing service.");
    } else console.log(`Cart rejected: ${reply.message}`);
    return reply.status === "quoted" ? 0 : 2;
  } catch {
    console.error("Quote unavailable. Check the cart file, start Python, and use the same port in both terminals.");
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main();
}
