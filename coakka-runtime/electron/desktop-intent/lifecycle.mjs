// App IPC references, not a second runtime queue or Core lifecycle observer.
export function createAdmission(closeRuntime) {
  const active = new Set();
  let closing = false, retirement;
  return {
    async run(work) {
      if (closing || active.size === 8) throw new Error("sample IPC admission refused");
      const accepted = Promise.resolve().then(work);
      active.add(accepted);
      try { return await accepted; }
      finally { active.delete(accepted); }
    },
    close() {
      if (retirement) return retirement;
      // Seal in this JS turn before snapshotting; no later accepted reference
      // can escape the join. Native shutdown follows, never races those calls.
      closing = true;
      retirement = (async () => {
        await Promise.allSettled([...active]);
        await closeRuntime();
      })();
      return retirement;
    },
  };
}

export function acceptedFrame(event, contents, entryUrl) {
  return event?.sender === contents && event.senderFrame != null &&
    event.senderFrame === contents.mainFrame && event.senderFrame.url === entryUrl;
}
