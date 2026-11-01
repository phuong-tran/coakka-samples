const form = document.querySelector("#quote-form");
const fields = document.querySelector("#cart-fields");
const reference = document.querySelector("#cart-id");
const feedback = document.querySelector("#feedback");
const panel = document.querySelector("#quote-panel");
const badge = document.querySelector("#result-badge");
const placeholder = document.querySelector("#quote-placeholder");
const result = document.querySelector("#quote-result");
const reset = document.querySelector("#reset");
const products = { tea: "Tea", coffee: "Coffee", cocoa: "Cocoa" };
const inputs = Object.keys(products).map(sku => ({ sku, input: document.querySelector(`#qty-${sku}`) }));
const money = cents => new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(cents / 100);
let busy = false, hasRequested = false;

function say(message, error = false) {
  feedback.classList.toggle("error", error);
  feedback.setAttribute("role", error ? "alert" : "status");
  feedback.textContent = message;
}

function empty(title, copy, state) {
  result.hidden = true; placeholder.hidden = false;
  document.querySelector("#placeholder-title").textContent = title;
  document.querySelector("#placeholder-copy").textContent = copy;
  badge.textContent = state;
  badge.className = "state-badge";
}

function edited() {
  if (busy || !hasRequested) return;
  empty("Basket changed.", "Request a new quote for your updated basket.", "Needs a new quote");
  say("Your basket changed. Get a new quote to see the current total.");
}

form.addEventListener("input", edited);
for (const button of form.querySelectorAll("[data-step]")) {
  button.addEventListener("click", () => {
    const input = document.querySelector(`#qty-${button.dataset.sku}`);
    const value = Number(input.value);
    input.value = Math.min(100, Math.max(0, (Number.isFinite(value) ? Math.trunc(value) : 0) + Number(button.dataset.step)));
    edited();
  });
}
reset.addEventListener("click", () => {
  if (busy) return;
  reference.value = "cart-1001";
  for (const { sku, input } of inputs) input.value = { tea: 2, coffee: 1, cocoa: 0 }[sku];
  hasRequested = false;
  empty("Ready when you are.", "Your item prices, shipping and total will appear here.", "Not requested");
  say("Example basket restored. Ask Python for a quote.");
});

function showQuote(quote) {
  placeholder.hidden = true; result.hidden = false;
  badge.textContent = "Quote received"; badge.className = "state-badge received";
  document.querySelector("#quote-total").textContent = money(quote.totalCents);
  document.querySelector("#quote-reference").textContent = `USD · ${quote.cartId}`;
  const lines = document.querySelector("#quote-lines"); lines.replaceChildren();
  for (const item of quote.items) {
    const row = document.createElement("div"); row.className = "quote-line";
    const description = document.createElement("div");
    const name = document.createElement("strong"); name.textContent = products[item.sku] || item.sku;
    const details = document.createElement("small"); details.textContent = `${item.quantity} × ${money(item.unitPriceCents)} each`;
    const total = document.createElement("span"); total.textContent = money(item.lineTotalCents);
    description.append(name, details); row.append(description, total); lines.append(row);
  }
  document.querySelector("#quote-subtotal").textContent = money(quote.subtotalCents);
  document.querySelector("#quote-shipping").textContent = quote.shippingCents === 0 ? "Free" : money(quote.shippingCents);
  document.querySelector("#quote-final").textContent = money(quote.totalCents);
  say(`Quote ready for ${quote.cartId}: USD ${(quote.totalCents / 100).toFixed(2)}.`);
}

form.addEventListener("submit", async event => {
  event.preventDefault();
  if (busy || !form.reportValidity()) return;
  const items = inputs.map(({ sku, input }) => ({ sku, quantity: Number(input.value) })).filter(item => item.quantity > 0);
  if (items.length === 0) {
    hasRequested = true; empty("Your basket is empty.", "Add at least one product, then get a quote.", "Add a product");
    say("Add at least one product to your basket.", true); inputs[0].input.focus(); return;
  }
  const cart = { cartId: reference.value, items };
  busy = true; hasRequested = true; fields.disabled = true; reset.disabled = true;
  panel.setAttribute("aria-busy", "true");
  document.querySelector("#button-label").textContent = "Getting quote…";
  empty("Python is pricing your basket.", "Waiting for the result. Your total is calculated by the pricing service.", "Requesting");
  say("Requesting your quote…");
  try {
    const response = await fetch("/api/quote", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(cart) });
    const quote = await response.json();
    if (response.ok && quote.status === "quoted") showQuote(quote);
    else {
      const rejected = quote.status === "rejected";
      empty(rejected ? "Please check your basket." : "Couldn’t get a quote.", quote.message || "Please try again.", rejected ? "Cart rejected" : "Unavailable");
      badge.classList.add("error"); say(quote.message || "Please try again.", true);
    }
  } catch {
    const message = "Quote unavailable. Check the Node and Python terminals, then try again.";
    empty("Couldn’t get a quote.", message, "Unavailable"); badge.classList.add("error"); say(message, true);
  } finally {
    busy = false; fields.disabled = false; reset.disabled = false;
    panel.setAttribute("aria-busy", "false");
    document.querySelector("#button-label").textContent = "Get quote";
  }
});
