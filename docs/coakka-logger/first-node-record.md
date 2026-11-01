# Node.js Logger: One Record

Install the published Node.js Logger package, submit one record, read it and
close. This is a small application-owned logging flow, separate from Runtime.

**You need:** Node.js and npm, following the
[Node Logger guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/node/README.md)
for its version and platform requirements.

## Install

~~~sh
mkdir coakka-logger-first-run
cd coakka-logger-first-run
npm init -y
npm install coakka-logger-node@1.2.7
~~~

## Write the application

Create logger.mjs:

~~~js
import { CoakkaLoggerLevel, Logger } from "coakka-logger-node";

const logger = Logger.start({
  systemName: "first-user-logger",
  minLevel: CoakkaLoggerLevel.INFO,
});

try {
  const sequence = logger.info("first.user", JSON.stringify({ hello: "logger" }));
  const record = logger.awaitNext(1000);
  if (sequence == null || record == null) {
    throw new Error("expected one accepted and drained log record");
  }

  console.log({ sequence: record.sequence, category: record.category });
} finally {
  logger.close();
}
~~~

## Run and check

~~~sh
node logger.mjs
~~~

Expected shape:

~~~text
{ sequence: 1, category: 'first.user' }
~~~

The app reads the record itself and then closes Logger. This example does not
configure a background file writer or remote collector. Reading a record is
not a durable-storage guarantee.

Next, [choose your language or a pressure example](README.md), or read
[the Logger sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/README.md)
to understand output choices and counters.
