# Android Runtime Integration

This lane is an integration recipe, not a runnable Android sample project.
Android builds depend on an SDK, NDK, emulator or device image, application
namespace, signing setup, and lifecycle policy that this repository cannot
choose for a consuming app.

## Tagged Candidate

Use these identities together:

| Identity | Value |
| --- | --- |
| Android connector | `1.2.0` |
| Source tag | `android-runtime-1.2.0` at `53d39fd9b6dd417374662a25437af106198aff7a` |
| Native runtime package | Core `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` |
| Android ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Minimum Android API | `24` |
| Compile SDK | Android API `36.1` or newer |
| Exact AAR | SHA-256 `edadc38b61a47ad70de6e0a52afeec14cdac467b3cac1c7a15c1d6aa9fd4ad29`, 4,859,325 bytes |
| Publication state | signed Central bundle frozen; no accepted deployment; registry closed |

The exact release-minified AAR passed on
`google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys`
(API 36, `arm64-v8a`). The test verifies runtime identity/capabilities, one
request outcome, one owner-pinned File transfer, and one owner-pinned Stream
delivery. Connector `1.2.0` is not yet a public Maven coordinate because no
Central deployment has reached `PUBLISHED`; treat the instructions below as
candidate integration guidance rather than a registry claim.

## Gradle And Maven

After the coordinate appears in the public artifact manifest, resolve it from
Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Then add the exact candidate coordinate to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.phuong-tran.coakka:coakka-runtime-android:1.2.0")
}
```

Do not use that coordinate before publication. For candidate testing, consume
the exact local AAR file and its pinned protobuf dependency. Do not mix a Maven
coordinate and `app/libs`; that can package the same classes and native
libraries twice.

The Apache connector/JNI source projection lives under
[`source-mirrors/android-runtime/1.2.0`](../../source-mirrors/android-runtime/1.2.0/README.md).
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

The AAR also carries consumer R8 rules for the name-based JNI bridge. Keep those
rules enabled in minified applications; do not replace them with a blanket
`-dontobfuscate`. The release evidence app checks four exact JNI/callback class
identities in the R8 mapping and 34 native plus two callback members in the
final APK DEX before executing Runtime, request/reply, File Lane, and Stream Lane.

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
import coakka.v2.android.RuntimeNetworkConfig
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
                        network = RuntimeNetworkConfig.embedded(),
                        queueCapacity = 128,
                        strictNoDrop = true,
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

## Pipe And Worker Ownership

Connector `1.2.0` is a low-level host bridge. It exposes framed request,
delivered-request, response, deadletter, control, and monitor lanes. It does
not yet expose the higher-level Android `handler` or `ask` helpers available in
some other connectors. It now also exposes Simple and owner-aware File and
Stream Lane APIs; those lanes are separate bounded native resources rather
than runtime message pipes.

Use one blocking reader per output lane that the app consumes:

- `readDeliveredRequest()`;
- `readResponse()`;
- `readDeadletter()`;
- `waitForMonitorDoorbell()`, followed by `readHealth()`.

`waitForMonitorDoorbell()` sleeps in `poll(2)` without a periodic timeout and
returns `null` when `close()` cancels the lane. `consumeMonitorDoorbell()`
remains an immediate nonblocking drain for callers that already own a readiness
signal.

Do not read one lane from multiple workers. On shutdown:

1. stop admitting new app work;
2. signal workers to stop;
3. call `runtime.close()` so blocked descriptor reads can return;
4. join the workers.

Do not retain raw file descriptor integers, adopt a descriptor twice, or wait
for blocked readers before closing the runtime.

The exact tagged AAR now owns the basic release-minified emulator evidence.
Build envelope serialization from the exact `coakka.v2.transport` classes in
the AAR; do not invent an Android-only request/reply facade.

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

The exact-AAR instrumentation app passed the basic API 36 ARM64 Runtime,
request, File Lane, and Stream Lane checks. Physical device behavior,
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

For connector source identity and reproduction material, use the immutable
[`android-runtime-1.2.0` source tag](https://github.com/phuong-tran/coakka-samples/tree/android-runtime-1.2.0/source-mirrors/android-runtime/1.2.0).
