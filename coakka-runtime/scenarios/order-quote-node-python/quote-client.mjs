import { randomUUID } from "node:crypto";
import { RuntimeHost, outboundOnlyNetwork } from "coakka-v2-connector-node";

export const TARGET = "samples.order.quote";
export const MAX_BYTES = 16 * 1024;

export function openQuoteClient(port = 19360) {
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new RangeError("port must be between 1 and 65535");
  }
  const host = RuntimeHost.start({
    systemName: "order-quote-demo", nodeId: `node-checkout-${randomUUID()}`,
    routes: [{ target: TARGET, endpoints: [{ host: "127.0.0.1", port }] }],
    network: outboundOnlyNetwork(),
  });
  return {
    async quote(cart, timeoutMs = 2000) {
      if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 10000) {
        throw new RangeError("timeout must be between 1 and 10000 ms");
      }
      const payload = Buffer.from(JSON.stringify(cart));
      if (payload.length > MAX_BYTES) throw new RangeError("Cart exceeds 16 KiB.");
      const bytes = await host.askPayload("checkout", TARGET, payload, timeoutMs);
      if (bytes.length > MAX_BYTES) throw new Error("Quote exceeds 16 KiB.");
      const reply = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
      if (reply === null || typeof reply !== "object" || !["quoted", "rejected"].includes(reply.status)) {
        throw new Error("Pricing service returned an invalid quote.");
      }
      if (reply.status === "quoted" &&
          (reply.cartId !== cart.cartId || reply.currency !== "USD" ||
           !Number.isSafeInteger(reply.totalCents) || reply.totalCents < 0)) {
        throw new Error("Pricing service returned an invalid quote.");
      }
      if (reply.status === "quoted" && (
          ![reply.subtotalCents, reply.shippingCents].every(value => Number.isSafeInteger(value) && value >= 0) ||
          !Array.isArray(reply.items) || reply.items.length < 1 || reply.items.length > 20 ||
          reply.items.some(item => item === null || typeof item !== "object" ||
            typeof item.sku !== "string" || item.sku.length > 64 ||
            !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 100 ||
            ![item.unitPriceCents, item.lineTotalCents].every(value => Number.isSafeInteger(value) && value >= 0)))) {
        throw new Error("Pricing service returned invalid quote details.");
      }
      if (reply.status === "rejected" && typeof reply.message !== "string") {
        throw new Error("Pricing service returned an invalid rejection.");
      }
      return reply;
    },
    close: () => host.close(),
  };
}

export async function withQuoteClient(port, action, open = openQuoteClient) {
  const client = open(port);
  let result, failure;
  try { result = await action(client); } catch (error) { failure = { error }; }
  try { await client.close(); } catch (error) {
    if (failure) throw new AggregateError([failure.error, error], "Quote and shutdown failed.");
    throw error;
  }
  if (failure) throw failure.error;
  return result;
}
