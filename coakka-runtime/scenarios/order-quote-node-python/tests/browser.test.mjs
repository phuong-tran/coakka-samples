import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import { test } from "node:test";
import { chromium } from "playwright";
import { startWebApp } from "../web-server.mjs";
import { service, stop } from "./process-fixture.mjs";

test("browser: real cart, quote, pending/stale/error states, keyboard and mobile", { timeout: 60000 }, async t => {
  const s = await service(t);
  const app = await startWebApp({ port: 0, pricingPort: s.port });t.after(() => app.close());
  const browser = await chromium.launch({ headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined });
  t.after(() => browser.close());
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];page.on("pageerror", error => errors.push(error.message));
  let submissions = 0;page.on("request", request => { if (request.url().endsWith("/api/quote")) submissions++; });
  await page.goto(app.url);
  const submit = page.getByRole("button", { name: "Get quote", exact: true });
  const total = page.locator("#quote-total");
  const ready = () => page.getByText("Quote received", { exact: true }).waitFor();
  const noOverflow = async () => assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);

  await t.test("default cart is priced by Python and shown with line details", async () => {
    assert.equal(await total.isVisible(), false);await submit.click();await ready();
    assert.equal(await total.textContent(), "$48.00");assert.equal(await page.locator("#quote-shipping").textContent(), "$5.00");
    assert.match(await page.locator("#quote-lines").textContent(), /2 × \$12.50 each/);
    assert.match(await page.locator("#feedback").textContent(), /Quote ready for cart-1001: USD 48.00/);
    await noOverflow();
    const contrastFailures = await page.evaluate(() => {
      const rgb = value => (value.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
      const luminance = values => values.map(v => v / 255).map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      const failures = [];
      while (walker.nextNode()) {
        const node = walker.currentNode, el = node.parentElement;
        if (!node.textContent.trim() || !el.getClientRects().length || el.closest('[aria-hidden="true"]')) continue;
        const style = getComputedStyle(el);let ancestor = el, background;
        while (ancestor) {
          const color = getComputedStyle(ancestor).backgroundColor;
          if (color !== "rgba(0, 0, 0, 0)" && color !== "transparent") { background = color;break; }
          ancestor = ancestor.parentElement;
        }
        if (!background) continue;
        const a = luminance(rgb(style.color)), b = luminance(rgb(background));
        const ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        const large = parseFloat(style.fontSize) >= 24 || (parseFloat(style.fontSize) >= 18.66 && Number(style.fontWeight) >= 700);
        if (ratio < (large ? 3 : 4.5)) failures.push({ text: node.textContent.trim(), ratio: Number(ratio.toFixed(2)) });
      }
      return failures;
    });
    assert.deepEqual(contrastFailures, []);
    if (process.env.COAKKA_DEMO_BROWSER_EVIDENCE) {
      await mkdir(process.env.COAKKA_DEMO_BROWSER_EVIDENCE, { recursive: true });
      await page.screenshot({ path: join(process.env.COAKKA_DEMO_BROWSER_EVIDENCE, "desktop-quote.png"), fullPage: true });
    }
  });

  await t.test("editing invalidates quote; pending is announced and cannot submit twice", async () => {
    await page.getByRole("spinbutton", { name: "Tea quantity", exact: true }).fill("4");
    await page.getByRole("spinbutton", { name: "Coffee quantity", exact: true }).fill("0");
    assert.equal(await total.isVisible(), false);assert.equal(await page.locator("#result-badge").textContent(), "Needs a new quote");
    let release;const gate = new Promise(resolve => { release = resolve; });
    const intercept = async route => { await gate;await route.continue(); };
    await page.route("**/api/quote", intercept);
    try {
      await submit.click();
      await page.getByRole("button", { name: "Getting quote…", exact: true }).waitFor();
      assert.equal(await page.getByRole("spinbutton", { name: "Tea quantity", exact: true }).isDisabled(), true);
      assert.equal(await page.getByRole("button", { name: "Getting quote…", exact: true }).isDisabled(), true);
      assert.equal(await total.isVisible(), false);assert.equal(await page.locator("#quote-panel").getAttribute("aria-busy"), "true");
    } finally { release(); }
    await ready();await page.unroute("**/api/quote", intercept);
    assert.equal(await total.textContent(), "$50.00");assert.equal(await page.locator("#quote-shipping").textContent(), "Free");
  });

  await t.test("quantity bounds, empty cart, reset and keyboard submit", async () => {
    const tea = page.getByRole("spinbutton", { name: "Tea quantity", exact: true });
    await tea.fill("101");const before = submissions;await submit.click();assert.equal(submissions, before);
    assert.equal(await tea.evaluate(el => el.validity.rangeOverflow), true);
    await tea.fill("0");await submit.click();
    assert.match(await page.locator("#feedback").textContent(), /Add at least one product/);assert.equal(submissions, before);
    await page.getByRole("button", { name: "Reset example" }).click();assert.equal(await total.isVisible(), false);
    await page.locator("#cart-id").focus();await page.keyboard.press("Tab");
    assert.equal(await page.getByRole("button", { name: "Decrease tea quantity" }).evaluate(el => el === document.activeElement), true);
    await page.locator("#cart-id").focus();await page.keyboard.press("Enter");await ready();assert.equal(await total.textContent(), "$48.00");
  });

  await t.test("Python business rejection and server text are displayed safely", async () => {
    await page.route("**/api/quote", async route => {
      const cart = JSON.parse(route.request().postData());cart.items[0].sku = "missing";
      await route.continue({ postData: JSON.stringify(cart) });
    }, { times: 1 });
    await submit.click();await page.getByText("Cart rejected", { exact: true }).waitFor();
    assert.equal(await total.isVisible(), false);assert.match(await page.locator("#feedback").textContent(), /Choose tea, coffee or cocoa/);
    await page.route("**/api/quote", route => route.fulfill({ status: 422, contentType: "application/json", body: JSON.stringify({ status: "rejected", message: '<img src=x onerror="window.injected=true">' }) }), { times: 1 });
    await submit.click();await page.waitForFunction(() => document.querySelector("#feedback").textContent.includes("<img"));
    assert.equal(await page.locator("#feedback img").count(), 0);assert.equal(await page.evaluate(() => window.injected), undefined);
  });

  await t.test("mobile layout remains usable and shows a real result", async () => {
    await page.setViewportSize({ width: 390, height: 844 });await page.getByRole("button", { name: "Reset example" }).click();
    await submit.click();await ready();assert.equal(await total.textContent(), "$48.00");await noOverflow();
    if (process.env.COAKKA_DEMO_BROWSER_EVIDENCE) {
      await mkdir(process.env.COAKKA_DEMO_BROWSER_EVIDENCE, { recursive: true });
      await page.screenshot({ path: join(process.env.COAKKA_DEMO_BROWSER_EVIDENCE, "mobile-quote.png"), fullPage: true });

    }
  });

  await page.setViewportSize({ width: 320, height: 720 });await noOverflow();

  await t.test("stopped Python is a visible failure, with no stale price or automatic retry", async () => {
    await stop(s.child);const before = submissions;
    await submit.click();await page.getByText("Unavailable", { exact: true }).waitFor();
    assert.equal(submissions, before + 1);assert.equal(await total.isVisible(), false);
    assert.match(await page.locator("#feedback").textContent(), /Make sure Python is running/);
    assert.equal(await submit.isEnabled(), true);
  });
  assert.deepEqual(errors, []);
});
