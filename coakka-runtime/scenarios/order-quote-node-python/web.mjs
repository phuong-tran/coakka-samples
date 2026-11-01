import { pathToFileURL } from "node:url";
import { startWebApp } from "./web-server.mjs";

export async function main(args = process.argv.slice(2)) {
  const options = {};
  for (let i = 0; i < args.length; i++) {
    const key = { "--port": "port", "--pricing-port": "pricingPort" }[args[i]];
    const value = args[++i];
    if (!key || !/^\d+$/.test(value ?? "") || Number(value) < 1 || Number(value) > 65535) {
      console.error("Usage: node web.mjs [--port 8080] [--pricing-port 19360]");
      return 1;
    }
    options[key] = Number(value);
  }
  let app;
  try { app = await startWebApp(options); }
  catch {
    console.error("Web app could not start. Check the web port and package installation.");
    return 1;
  }
  console.log(`Open ${app.url} in your browser.`);
  console.log("Start Python pricing in Terminal A, then request a quote. Press Ctrl+C to stop.");
  await new Promise(resolve => {
    const stop = () => {
      process.off("SIGINT", stop); process.off("SIGTERM", stop);
      app.close().then(() => { console.log("Web app stopped."); resolve(); }, () => {
        console.error("Web app could not close cleanly."); process.exitCode = 1; resolve();
      });
    };
    process.once("SIGINT", stop); process.once("SIGTERM", stop);
  });
  return process.exitCode || 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main();
}
