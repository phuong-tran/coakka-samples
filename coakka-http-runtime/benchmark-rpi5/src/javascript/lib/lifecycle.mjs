export function ready(port, applicationPath) {
  const record = { ready: true, bound_port: port };
  if (applicationPath !== undefined) {
    record.application_path = applicationPath;
  }
  console.log(JSON.stringify(record));
}

export function stopOnSignals(close) {
  let stopping = false;
  async function stop() {
    if (stopping) {
      return;
    }
    stopping = true;
    try {
      await close();
      console.log(JSON.stringify({ stopped: true }));
    } catch (error) {
      console.error(error);
      process.exitCode = 1;
    }
  }
  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
}
