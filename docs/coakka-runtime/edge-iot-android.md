# Edge, IoT, And Industrial Android

## Short Answer

CoAkka can fit edge and IoT systems because it does not require Kubernetes, a
cloud control plane, a broker, a service mesh, or a public HTTP service
boundary just to call application-owned capabilities. It needs an app host, a
supported native runtime package, route snapshots, handlers, and the normal
platform services around it.

Android industrial tablets are feasible. Android connector `1.2.0` provides
a private four-ABI AAR with an obfuscated library and a product message API.
Its current qualification uses independently minified apps on native ARM64
Mac emulators at API 24 and API 36. An
Android app or background service can host the connector, load the native
runtime through the Android NDK, register handlers, and call stable targets.
Physical-device behavior and industrial field operation need application-specific
evaluation; they are not inferred from emulator tests.

Its normal construction leaves CPU and connection choices to Core: `AUTO`
and `MULTIPLEXED` are the defaults. Monitoring is OFF unless the application
requests it at startup. Android reports Core's accepted settings rather than
keeping its own default table. Optional network intent preserves absent versus explicit fields;
`networkSnapshot()` reports Core-accepted mode, endpoints and provenance.
An optional grouped monitor choice distinguishes
absence from explicit OFF or ON; health remains available without extra
collection. See [Runtime Defaults And Monitoring](runtime-monitoring.md) for
when to enable it. Core owns request delivery and reply matching; Android
adapts values and application lifecycle without duplicating that work.

## Edge Linux First

The first practical edge lane should be Linux devices and gateways:

- Raspberry Pi;
- BeagleBone;
- industrial ARM64 Linux gateways;
- small x86_64 edge boxes;
- local factory, retail, kiosk, or field gateway machines.

These environments are closer to the current native runtime packaging model and
are easier to validate for ABI, threading, filesystem, networking, and memory
behavior. They also match CoAkka's embedded-style defaults: bounded queues,
explicit overload, fewer moving parts, and runtime delivery evidence.

The deployment does not need Kubernetes, service mesh, a broker, or public HTTP
endpoints just to call local application-owned capabilities. It can still use
those tools when they are the right boundary.

## Where CoAkka Fits

```text
field device, sensor, PLC adapter, or local application
  -> app-host policy
    -> CoAkka target
      -> local or nearby handler
        -> reply or deadletter
```

Typical targets can stay domain-shaped:

```text
sensor.read
device.scan
gateway.forward-reading
inventory.sync
printer.print-label
payment.offline-authorize
```

The target names the capability. The transport, device address, local process,
gateway hostname, or deployment layout can change underneath the route snapshot.

## What CoAkka Does Not Replace

CoAkka does not replace device or platform protocols:

- MQTT;
- Modbus;
- BLE;
- CAN bus;
- OPC UA;
- serial drivers;
- vendor SDKs;
- Android lifecycle APIs;
- cloud sync;
- durable offline storage.

Those protocols and stores can remain where they belong. CoAkka is useful when
the application wants a stable runtime target, bounded admission, reply,
timeout, rejection, and deadletter evidence around capability work.

## Why Edge And IoT Fit The Model

Edge and IoT deployments often have constrained resources, unreliable
networks, and mixed language or process boundaries. CoAkka fits that shape
when the work is still application-owned:

- bounded admission instead of unbounded local queues;
- explicit timeout, rejection, and deadletter outcomes;
- stable capability targets instead of local REST endpoints created only for
  addressing;
- local work can stay local when the network is unavailable;
- app policy keeps ownership of retry, idempotency, auth, and business meaning;
- existing protocol adapters can remain the device-facing boundary.

## Securing Edge Connections

When traffic crosses a device, gateway, LAN, or another network boundary that
requires confidentiality or peer identity, configure runtime TLS or mTLS
through the host-language connector if the exact artifact reports the required
capability. The device host still owns trustworthy clock readiness, certificate
provisioning and renewal, and private-key access. See
[Runtime TLS And mTLS](tls-and-mtls.md) for the connector-first configuration
and edge-specific security boundary.

## Android And Industrial Tablets

CoAkka can integrate with Android industrial tablets. The architecture does not
depend on Kubernetes, a browser, a cloud control plane, or a server-side web
framework. An Android app or Android background service can host a connector,
load a native runtime through the Android NDK, register handlers, and call
stable targets.

Connector `1.2.0` has an internal candidate AAR with `arm64-v8a`, `armeabi-v7a`,
`x86`, and `x86_64` payloads, a thin JNI layer, and Android lifecycle guidance.
The current candidate is qualified through real minified apps on Mac ARM64
emulators at API 24 and API 36 for startup inspection, request/reply, close,
File and Stream. `AndroidRuntimeMessages` creates requests, reads their values,
builds replies and reads response payloads through Core. Apps do not need a
separate message codec. See the [Android integration recipe](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/android/README.md)
for the exact candidate identity and code.
The AAR remains private; publication needs a separate release decision.

A proper Android lane should include:

- Android NDK builds;
- `arm64-v8a` native library packaging;
- AAR packaging;
- Kotlin/Java connector binding;
- lifecycle guidance for Activity, Service, and background work;
- emulator or device smoke tests;
- an industrial-tablet sample app.

So the short answer is: yes, CoAkka can fit Android industrial tablets.
Compatibility evidence should identify the exact host, ABI, lifecycle path,
connector version, and device or emulator that were exercised.

## Failure Model

The runtime evidence should stay boring and explicit:

- missing target -> route-miss deadletter;
- local overload -> rejection or deadletter evidence;
- unavailable peer or gateway -> timeout or delivery failure evidence;
- app policy decides retry, compensation, idempotency, and user-facing meaning;
- logger evidence can report pressure without hiding drops or overload.

CoAkka reports runtime delivery facts. The app-host still owns business
semantics.

## Current Support Boundary

Treat this as the support boundary:

- current public package lanes are the released artifact surface;
- edge Linux is the first practical expansion target for device and gateway
  validation;
- Android connector `1.2.0` has a clean private source checkpoint and an
  unsigned emulator-tested candidate; its current API has no public source tag
  or supported coordinate. The older public 1.2.0 tag is a different immutable
  artifact and must not be used as its source;
- current acceptance uses the Mac's native 64-bit emulators; physical-device
  and Android 32-bit execution are excluded by explicit user direction, while
  all four ABI payloads retain static inspection;
- emulator process restart and bounded load evidence do not establish physical
  Activity/service behavior, industrial LAN or thermal behavior, or field-soak
  support.
