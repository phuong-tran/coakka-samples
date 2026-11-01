# Runtime Examples And Use Cases

**Choose what you want to build, then open its runnable code.**

Runtime connects application-owned work. A small local example teaches the
API; a business example shows how that work fits into an application. Read the
version/status column before running a larger demonstration.

## Start in your language

[First-run examples](../getting-started.md) cover Node.js, Bun, Electron,
Python, C#, Kotlin, Java, Go and Swift with published 3.0.0 packages. Each README
names its source, prerequisites, run command, expected result and close behavior.

## Try a business operation

| Build or learn | Language | Package path | Open |
| --- | --- | --- | --- |
| Create a customer through a named application handler | Node.js | Published npm 3.0.0; one process | [No-checkout customer walkthrough](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/guides/first-npm-smoke.md) |
| Browser/API application with customer create, read, update and delete | Kotlin on Spring Boot or Quarkus | Published Maven 3.0.0; same-process handlers | [Framework first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/framework-registry) |
| A desktop window submitting application work | Electron | Published npm 3.0.0; Runtime lives in the main process | [Desktop first run and source](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic) |

The customer stores are in-memory teaching examples. The application owns its
data, validation and HTTP response. Add your own persistence and business rules
when integrating the pattern.

## Work across processes and languages

A local first reply does not demonstrate network delivery. These larger
examples show that distinction:

| Example | What you see | Version/status |
| --- | --- | --- |
| [Node.js checkout to Python pricing](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/scenarios/order-quote-node-python/README.md) | Browser basket and live quote; Node asks a separate Python process for prices and shipping. Two terminals on one computer; CLI also available. | Published npm/PyPI 3.0.0; no container required. |
| [Node.js web to Python store](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/containers/node-python) | Two containers; a browser action reaches work owned by the Python process. | Historical published 1.x images; Docker/Podman required. Keep the image versions from its README. |
| [Customer scenario catalog](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud) | Local, desktop and cross-process application shapes. | Only the dedicated framework registry build is the published 3.0.0 launcher; other launchers retain their own pinned/local package requirements. |
| [Two-machine Linux walkthrough](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/guides/two-machine-linux.md) | A web process and a store process on separate Linux machines. | Follow the walkthrough's pinned source/packages and network requirements. |

Keep a demonstration's complete code and dependencies together. The historical
container images are not a shortcut for installing current 3.0.0 packages.

## Add a capability when you need it

| Need | Guide and code | Scope |
| --- | --- | --- |
| Transfer a file | [File transfer](runtime-file-transfer.md) | Choose the exact package/example version named by the guide. |
| Send a sequence of data | [Streaming](runtime-streaming.md) | Read the example's lifetime and close requirements. |
| Deliver a file to several owners | [Go file fanout](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/go/replica-file-fanout) | Advanced local-package evaluation, not a registry-first recipe. |
| Raspberry Pi camera or Android integration | [Edge and device examples](../previews/edge-iot-android.md) | Device-specific prerequisites; Android remains preview. |
| Build an HTTP endpoint | [HTTP Runtime](../../coakka-http-runtime/getting-started.md) | Separate product and version; choose its language guide. |
| Record application logs | [Logger](../../coakka-logger/README.md) | Separate product and version; choose its language guide. |

## Move from sample to application

Use the [integration guide](runtime-integration-guide.md) to map the pattern to
your host language. Then read [defaults and monitoring](../operations/runtime-monitoring.md)
and [production readiness](../operations/production-readiness.md) for operation.

[First run](../getting-started.md) · [Runtime documentation](../README.md).
