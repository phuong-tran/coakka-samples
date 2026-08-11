# Android Runtime Integration

This lane is an integration recipe, not a runnable Android sample project.
Android builds depend on an SDK, NDK, emulator or device image, application
namespace, signing setup, and lifecycle policy that this repository cannot
choose for a consuming app.

## Exact Candidate

Use these identities together:

| Identity | Value |
| --- | --- |
| Android connector | `1.1.0` |
| Native runtime package | `2.3.0+345e97b2` |
| Android ABIs | `arm64-v8a`, `x86_64` |
| Minimum Android API | `24` |
| AAR SHA-256 | `3ce799885322c9ac92664bf028591bc77432960e7b2d85ecbd3c4e73362bf3cb` |

This AAR is a packaged candidate in `coakka-publish`, but it is not in the
current public artifact manifest because matching Android device or emulator
execution has not been recorded. Treat the instructions below as
source-candidate integration guidance, not as a supported public package claim.

## Download And Verify

From the Android project root on macOS or Linux:

```sh
mkdir -p app/libs
curl -fL \
  "https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/maven/android/releases/1.1.0+345e97b2/coakka-runtime-android-1.1.0.aar" \
  -o app/libs/coakka-runtime-android-1.1.0.aar

printf '%s  %s\n' \
  '3ce799885322c9ac92664bf028591bc77432960e7b2d85ecbd3c4e73362bf3cb' \
  'app/libs/coakka-runtime-android-1.1.0.aar' \
  | shasum -a 256 -c -
```

PowerShell:

```powershell
New-Item -ItemType Directory -Force app/libs | Out-Null
Invoke-WebRequest `
  -Uri "https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/maven/android/releases/1.1.0+345e97b2/coakka-runtime-android-1.1.0.aar" `
  -OutFile "app/libs/coakka-runtime-android-1.1.0.aar"

(Get-FileHash "app/libs/coakka-runtime-android-1.1.0.aar" -Algorithm SHA256).Hash
```

The PowerShell digest must equal the SHA-256 value in the table above.

## Gradle

Add the AAR and its exact language dependencies to
`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/coakka-runtime-android-1.1.0.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    implementation("com.google.protobuf:protobuf-javalite:4.31.1")
}
```

Keep the ABI that matches the evaluation target. Keeping both is useful while
testing physical ARM64 devices and x86-64 emulators:

```kotlin
android {
    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
}
```

The AAR carries both `libcoakka_runtime_v2.so` and
`libcoakka_android_jni.so` for each declared ABI. Do not copy or load those
libraries separately.

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

Connector `1.1.0` is a low-level host bridge. It exposes framed request,
delivered-request, response, deadletter, control, and monitor lanes. It does
not yet expose the higher-level Android `handler` or `ask` helpers available in
some other connectors.

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

The current evidence is sufficient to show exact lifecycle and configuration
API names. It is not sufficient to invent a full Android request/reply helper
or claim a device-run sample. Build envelope serialization from the exact
`coakka.v2.transport` classes in the AAR, or wait for the higher-level Android
facade before presenting application request/reply as a stable beginner API.

## Device Evaluation Checklist

Record all of these against the exact AAR digest:

- device or emulator name, Android build, API level, and ABI;
- AAR load and runtime open/close;
- Activity recreation while the service remains the owner;
- service restart and process-death recovery;
- one terminal request outcome and one deadletter outcome;
- network loss and reconnect for outbound mode;
- inbound reachability for network-node mode;
- queue pressure, resident memory, and long-running behavior.

Promote Android to a runnable sample lane only after those results and the
corresponding app source are committed.

## Troubleshooting

`UnsatisfiedLinkError` usually means the APK omitted the device ABI or another
packaging rule removed the AAR's JNI libraries. Inspect the APK and confirm it
contains both CoAkka libraries under the same `lib/<abi>/` directory.

An immediate network-node startup failure usually means the bind address is not
local, the port is already in use, or the app lacks network permission. An
unreachable peer after a successful bind usually means `advertiseHost`, device
firewall, Wi-Fi isolation, VPN, or route configuration is wrong.

For release identity and remaining evidence gaps, read the Android candidate's
[`RELEASE.md`](https://github.com/phuong-tran/coakka-publish/blob/main/maven/android/releases/1.1.0+345e97b2/RELEASE.md).
