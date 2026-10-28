# Android Runtime Integration

This lane is an integration recipe, not a runnable Android sample project.
Android builds depend on an SDK, NDK, emulator or device image, application
namespace, signing setup, and lifecycle policy that this repository cannot
choose for a consuming app.

## Contents

- [Private Candidate](#private-candidate)
- [Gradle And Local AAR](#gradle-and-local-aar)
- [Android Manifest](#android-manifest)
- [Embedded Lifecycle](#embedded-lifecycle)
- [Network Modes](#network-modes)
- [Defaults And Monitoring](#defaults-and-monitoring)
- [Core-Owned Delivery And Worker Ownership](#core-owned-delivery-and-worker-ownership)
- [File And Stream Owner Grants](#file-and-stream-owner-grants)
- [Device Evaluation Checklist](#device-evaluation-checklist)
- [Troubleshooting](#troubleshooting)

## Private Candidate

Use these identities together for the current private API:

| Identity | Value |
| --- | --- |
| Android connector | `1.2.0` private candidate |
| Connector source checkpoint | `655fcf27dd84eaeb25d654371568084b79a69427` |
| Native runtime package | Core `2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2` |
| Android ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Minimum Android API | `24` |
| Compile SDK | Android API `36.1` or newer |
| Exact AAR | SHA-256 `1ec164434373b378071c46e9040807889149b15ea59c9c2f283a8f6081c296c3`, 3,026,710 bytes |
| Publication state | unsigned private candidate; no public Maven coordinate |

The exact AAR was consumed by a real R8-minified application and independently
minified instrumentation APKs on Windows API 37 native x86_64 and macOS API 36
native ARM64 emulators, using separate ABI-filtered APKs from the same AAR. It verifies accepted startup settings, monitoring absent/OFF/ON,
network intent and accepted facts, request/reply, close interruption, and
owner-pinned File and Stream delivery.
The Windows emulator also advertises translated ARM64; the ARM64 proof comes
from the separate native ARM64 Mac guest. `armeabi-v7a` and `x86` have
package/architecture checks only. Neither emulator establishes physical-device
lifecycle, minimum-API execution or industrial-soak support.

The historical `android-runtime-1.2.0` tag at
`53d39fd9b6dd417374662a25437af106198aff7a` and signed AAR SHA-256
`edadc38b61a47ad70de6e0a52afeec14cdac467b3cac1c7a15c1d6aa9fd4ad29`
are immutable older identities. They are not the source or artifact for the
current API below. A new reviewed public version/tag and source projection
remain necessary before publication. Do not replace that old tag or mirror.

## Gradle And Local AAR

Use normal repositories for the AAR's Kotlin and protobuf dependencies:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Place the exact candidate AAR under `app/libs` and pin its dependencies in
`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/coakka-runtime-android-1.2.0.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    implementation("com.google.protobuf:protobuf-javalite:4.31.1")
}
```

Verify the local AAR SHA-256 before use. Do not add an unpublished Maven
coordinate alongside `app/libs`; that can package the same classes and native
libraries twice.

The [historical source archive](../../source-mirrors/android-runtime/1.2.0/README.md)
belongs to the older tagged artifact above. It is not a current source projection.
The AAR carries `LICENSE`, `NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and
`NOTICE` offline because its Kotlin/JNI connector and bundled Native Core files
have different file-scoped terms.

Keep only the ABI set the application actually ships, or keep all four while
testing the complete AAR matrix:

```kotlin
android {
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86",
                "x86_64",
            )
        }
    }
}
```

The AAR carries both `libcoakka_runtime_v2.so` and
`libcoakka_android_jni.so` for each declared ABI. Do not copy or load those
libraries separately.

The library AAR's managed classes are not pre-obfuscated. It carries consumer
R8 rules for the name-based JNI bridge; the tested shrink/obfuscation step is
applied to the consuming application and instrumentation APKs. Library-level
managed hardening remains a publication gate. Keep those
rules enabled in minified applications; do not replace them with a blanket
`-dontobfuscate`. The release evidence app checks exact JNI/callback/exception class and
constructor identities, cross-APK startup/ask/error/monitor types, 39 native
methods and two callbacks in the final APK DEX, before executing Runtime,
request/reply, File Lane, and Stream Lane.

## Android Manifest

`EMBEDDED` mode does not open a TCP listener. Add `INTERNET` only when the app
uses `OUTBOUND_ONLY` or `NETWORK_NODE`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Declare the app-owned service when a service owns the runtime:

```xml
<application ...>
    <service
        android:name=".CoAkkaRuntimeService"
        android:exported="false" />
</application>
```

Keeping the service non-exported prevents another application from starting or
binding it directly. This does not replace runtime network authorization when
the process listens on a network interface.

## Embedded Lifecycle

One Android component should own one runtime instance. A started or bound
service is preferable when the runtime must survive Activity recreation:

```kotlin
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import coakka.v2.android.AndroidRuntimeConfig
import coakka.v2.android.AndroidRuntimeRoute
import coakka.v2.android.CoAkkaAndroidRuntime
import java.util.concurrent.Executors

class CoAkkaRuntimeService : Service() {
    private val lifecycleExecutor = Executors.newSingleThreadExecutor()
    private var runtime: CoAkkaAndroidRuntime? = null

    override fun onCreate() {
        super.onCreate()

        lifecycleExecutor.execute {
            try {
                val nodeId = "tablet-17"
                runtime = CoAkkaAndroidRuntime.open(
                    config = AndroidRuntimeConfig(
                        systemName = "factory-floor",
                        nodeId = nodeId,
                    ),
                    routes = listOf(
                        AndroidRuntimeRoute.local(
                            target = "device.scan",
                            nodeId = nodeId,
                        ),
                    ),
                )
            } catch (failure: Exception) {
                Log.e("CoAkkaRuntime", "runtime startup failed", failure)
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        lifecycleExecutor.execute {
            val ownedRuntime = runtime
            runtime = null
            try {
                ownedRuntime?.close()
            } catch (failure: Exception) {
                Log.e("CoAkkaRuntime", "runtime shutdown failed", failure)
            } finally {
                lifecycleExecutor.shutdown()
            }
        }
        super.onDestroy()
    }
}
```

The single lifecycle executor serializes open and close without performing
native startup or shutdown on the Android main thread. Route updates and app
submissions need their own explicit ownership; do not reach into `runtime`
from arbitrary Activity callbacks.

The local route uses port `0` as an in-process route identity. It does not bind
`127.0.0.1` and does not allocate a TCP port.

Android may kill the process without calling `onDestroy()`. Treat a later
process start as a fresh runtime lifecycle; do not rely on in-memory state for
durability or recovery decisions.

## Network Modes

Use outbound-only mode when the app calls remote runtime nodes but must not
accept inbound runtime connections:

```kotlin
val network = RuntimeNetworkConfig.outboundOnly()
```

Use network-node mode only when peers must connect to the Android process:

```kotlin
val network = RuntimeNetworkConfig.networkNode(
    bindHost = "0.0.0.0",
    bindPort = 19301,
    advertiseHost = "192.168.1.40",
    advertisePort = 19301,
)
```

`bindHost` selects the local listener. `advertiseHost` must be a concrete
address reachable by peers and cannot be a wildcard. Binding to `127.0.0.1`
restricts the listener to the Android device itself.

If the runtime must continue while the app UI is absent, make the service a
foreground service and implement the notification, service type, permission,
and start restrictions required by the target Android version. Those policies
belong to the consuming application and are not hidden by the connector.

Network choices are optional. Omitting `network` leaves Core's normal embedded
choice untouched. With an explicit `RuntimeNetworkConfig`, `null` ports mean
absent and `0` means explicitly supplied zero; Core validates the complete
choice when opening. Node callers supply both bind and advertised ports.
The connector never substitutes one for the other. Use `networkSnapshot()` to
read Core's accepted mode, endpoints and `explicitPolicy` fact. A defaulted
choice and an explicit embedded choice can have the same mode but different
provenance. Copied strings belong to the Java/Kotlin snapshot.

## Defaults And Monitoring

Normal construction leaves CPU and connection choices to Core: `AUTO` and
`MULTIPLEXED` are the defaults. Monitoring is OFF unless requested. Queue
capacity `0` also delegates to Core; ordinary samples should omit that option.
`startupConfig()` reports what Core accepted, including which groups were
left to its defaults. It does not report input preferences as observed state.
See [Runtime Defaults And Monitoring](../../docs/runtime-monitoring.md) for
why these defaults exist and when monitoring is useful. These principles apply
to all host languages, not only Kotlin or Android.

When extra diagnostic collection is needed, choose it before opening:

```kotlin
val config = AndroidRuntimeConfig(
    systemName = "factory-floor",
    nodeId = "tablet-17",
    monitor = AndroidRuntimeMonitorOptions(enabled = true),
)
val runtime = CoAkkaAndroidRuntime.open(config, routes = emptyList())
check(runtime.startupConfig().monitorEnabled)
val health = runtime.readHealth()
println("state=${health.state}; route configuration version=${health.appliedGeneration}")
```

Import `coakka.v2.android.AndroidRuntimeMonitorOptions` for this optional
example. Use `monitor = null` to let Core choose; an explicit
`AndroidRuntimeMonitorOptions(false)` asks for OFF and remains distinct from
absence in the accepted settings. Changing collection requires closing the
instance and opening a new one with the chosen intent.

Health can be read on demand while collection is OFF. When collection is ON,
an application I/O worker can use `waitForMonitorDoorbell()` and refresh with
`readHealth()` after a change. `close()` wakes the wait and makes it return
`null`. When Core accepts OFF, that wait returns `null` immediately and
`consumeMonitorDoorbell()` returns `0`. The method names are the Android API;
applications do not need to manage its internal notification mechanism.
Do not run a monitor loop when collection is OFF.

Core creation/startup refusal retains its exact `status` in
`CoAkkaNativeException`. Invalid bridge arguments and local allocation failures
retain Java argument/allocation exception types; they are not reclassified as
a Core refusal.

## Core-Owned Delivery And Worker Ownership

Core receives requests, selects their destinations and matches replies to
callers. The application owns its business handlers. Use:

- `readDeliveredRequest()` on one bounded application I/O worker for handler
  input;
- `submitEnvelope(...)` for a handler reply or one-way message;
- `ask(envelope, timeoutMillis)` for request/reply; the result identifies a
  `RESPONSE` or `DEADLETTER`.

Do not call `readDeliveredRequest()` concurrently. On shutdown:

1. stop admitting new app work;
2. signal workers to stop;
3. call `runtime.close()` to wake pending request/ask calls;
4. join the workers.

Do not wait for blocked calls before closing the runtime. Build messages from
the selected AAR's included message classes so they match its Core version.
File and Stream APIs remain separate bounded resources.

## File And Stream Owner Grants

The Simple API remains available through `FileLane.open(...)` and
`StreamLane.open(...)`. Use owner-aware creation when a prepare request can
land on any replica:

```kotlin
fun awaitTerminal(
    lane: FileLane,
    transferId: String,
    direction: FileTransferDirection,
): FileTransferSnapshot {
    val deadlineNs = System.nanoTime() + 30_000_000_000L
    var snapshot = lane.transfer(transferId, direction)
    while (!snapshot.terminal) {
        val remainingNs = deadlineNs - System.nanoTime()
        check(remainingNs > 0) { "$direction $transferId did not reach terminal state" }
        val remainingMs = ((remainingNs + 999_999L) / 1_000_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        snapshot = lane.waitTransfer(
            transferId,
            direction,
            afterUpdateSequence = snapshot.updateSequence,
            timeoutMs = remainingMs,
        )
    }
    return snapshot
}

val receiver = FileLane.openOwned(
    FileLaneConfig(flags = FileLaneFlags.RECEIVER, bindHost = "0.0.0.0"),
    LaneOwnerConfig(
        ownerInstanceId = "worker-2",
        advertisedHost = "10.0.0.12",
    ),
)
val fileGrant = receiver.prepareReceiveGrant(receiveSpec)

val transferFailures = mutableListOf<String>()
FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER)).use { sender ->
    sender.submitSend(fileGrant.toSendSpec(sourceFile))
    val sent = awaitTerminal(sender, fileGrant.transferId, FileTransferDirection.SEND)
    if (!sent.completed) {
        transferFailures += "SEND ${sent.state}/${sent.result}: ${sent.detail}"
    }
    sender.forget(fileGrant.transferId, FileTransferDirection.SEND)
}

val received = awaitTerminal(receiver, fileGrant.transferId, FileTransferDirection.RECEIVE)
if (!received.completed) {
    transferFailures +=
        "RECEIVE ${received.state}/${received.result}: ${received.detail}"
}
receiver.forget(fileGrant.transferId, FileTransferDirection.RECEIVE)
check(transferFailures.isEmpty()) { transferFailures.joinToString("; ") }
```

Run these blocking waits on a bounded worker, never an Android UI thread. The
sender and receiver each require their own terminal check and `forget`; sender
success does not prove that the destination was committed.

For Stream Lane, the exact publisher owner returns a single-admission grant:

```kotlin
val publisher = StreamLane.openOwned(
    StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER),
    LaneOwnerConfig("camera-3", "10.0.0.23"),
)
val streamGrant = publisher.preparePublishGrant(publishSpec)
subscriber.subscribe(streamGrant.toSubscribeSpec(initialWindowBytes, consumer))
```

`formatId` must be in `1..Long.MAX_VALUE`. Source and consumer buffers are
borrowed only for the callback. Calling `close()` on that same Stream Lane from
either callback fails fast; schedule shutdown on a different thread so native
stop can join its bounded worker.

Never replace the owner endpoint in either grant with a load-balancing Service
address. `ONE` uses one selected owner's grant. `ALL` enumerates every exact
owner, obtains one fresh grant per owner, and tracks one independent terminal
outcome per transfer/session. The full Android types, callback borrowing law,
ONE/ALL Mermaid flows, and token lifetime rules are in
[Runtime Lane Owner Grants](../../docs/runtime-lane-owner-grants.md).

## Device Evaluation Checklist

Record all of these against the exact AAR digest:

- device or emulator name, Android build, API level, and ABI;
- AAR load and runtime open/close;
- Activity recreation while the service remains the owner;
- service restart and process-death recovery;
- one terminal request outcome and one deadletter outcome;
- owner-aware File and Stream prepare/grant terminal outcomes;
- network loss and reconnect for outbound mode;
- inbound reachability for network-node mode;
- queue pressure, resident memory, and long-running behavior.

The exact-AAR instrumentation apps passed API 37 native x86_64 and API 36
native ARM64 startup, monitor/network intent, Runtime request/reply, close,
File and Stream checks. Physical device behavior,
Activity/service restart, process death, LAN paths, pressure, and soak remain
explicit support gates rather than inferred evidence.

## Troubleshooting

`UnsatisfiedLinkError` usually means the APK omitted the device ABI or another
packaging rule removed the AAR's JNI libraries. Inspect the APK and confirm it
contains both CoAkka libraries under the same `lib/<abi>/` directory.

An immediate network-node startup failure usually means the bind address is not
local, the port is already in use, or the app lacks network permission. An
unreachable peer after a successful bind usually means `advertiseHost`, device
firewall, Wi-Fi isolation, VPN, or route configuration is wrong.

For the older artifact only, its immutable source archive remains at the
[`android-runtime-1.2.0` source tag](https://github.com/phuong-tran/coakka-samples/tree/android-runtime-1.2.0/source-mirrors/android-runtime/1.2.0).
