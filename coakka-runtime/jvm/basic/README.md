# JVM Runtime Basic (Kotlin)

One text echo with the beginner CoAkka.local/use API, no manual route wiring
or private-state access. Use the [exact private ProGuard workflow](../../docs/runtime-jvm-candidate.md)
and its explicit candidate/work/tool/native-cache inputs, then:

```sh
bash coakka-runtime/jvm/basic/run.sh
```

The standalone coakka-runtime/jvm build targets Java8 APIs/major52, independent of
the JDK17+ build tool. It admits the exact local coakka-runtime/four managed JARs and
does not resolve the older public artifact or a separate native image.
The original1s ask is retained; response must be hello-runtime-jvm and use
closes the facade before failure escapes. No reflection is used to reveal
hidden orchestrator state. Expected output:

```text
coakka_runtime_response payload=hello-runtime-jvm
```

Native Windows uses the matching native Gradle/JDK tools in the candidate
guide; its Java8 native cache path is ASCII and created by the package loader.
Caller build/native caches are retained for validated reuse, not recursively
deleted by the wrapper. This is not a benchmark or publication approval.
