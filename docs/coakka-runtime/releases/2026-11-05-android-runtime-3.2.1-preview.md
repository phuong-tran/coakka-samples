# Android Runtime 3.2.1 Preview

Released on 2026-11-05 as a standalone Android AAR on `coakka-publish`.
The preview includes Core 3.2 build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45` (ABI 1).
The minimum Android API is 24 and the AAR requires compile SDK 36.1 or later.

- [Download coakka-runtime-android-3.2.1.aar](https://raw.githubusercontent.com/phuong-tran/coakka-publish/fe81cabbfbeb02672b02b19de8cfbe1a846630aa/coakka-runtime/android/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-android-3.2.1.aar) (3,007,524 bytes)
- [SHA256SUMS](https://raw.githubusercontent.com/phuong-tran/coakka-publish/fe81cabbfbeb02672b02b19de8cfbe1a846630aa/coakka-runtime/android/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) · [Artifact manifest](https://raw.githubusercontent.com/phuong-tran/coakka-publish/fe81cabbfbeb02672b02b19de8cfbe1a846630aa/coakka-runtime/android/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/manifest.json)

SHA-256 `1ce5d292305988b506f388ee5561b7b6f446ed80205e365c3cc9d56f417fb3df`. The AAR was downloaded anonymously and its digest
matched the qualified input and the public checksum file. The AAR is a local
Gradle dependency and carries no Maven Central coordinate. See the
[Android integration guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/android/README.md)
for the dependency declaration and app lifecycle example.

## ABI and emulator evidence

The AAR contains `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`.
All four passed native inventory, architecture, export, dependency and
binary checks. The installed app tests used the published AAR bytes and
checked Core identity, request/reply, File and Stream delivery, and
lifecycle behavior.

| Emulator | Result |
| --- | --- |
| API 24, `x86_64` | Installed tests passed. 613 lifecycle cycles in about 3 minutes. |
| API 36, `arm64-v8a` | Installed tests passed. 477 lifecycle cycles in about 3 minutes. |
| API 24, `arm64-v8a` | Emulator kernel panic before APK installation. No app result. |

The `armeabi-v7a` and `x86` payloads have static inspection evidence.
The preview's execution evidence covers the two listed emulators. Physical
device behavior and field soak remain separate evaluation work.
