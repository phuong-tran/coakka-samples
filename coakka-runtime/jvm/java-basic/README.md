# JVM Runtime Basic (Java8)

The Java-facing consumer uses public overloads, text handlers and future
shutdown; it does not call Kotlin synthetic defaults or use reflection.
Select the [exact private ProGuard candidate workflow](../../../docs/coakka-runtime/previews/3.0.0/runtime-jvm-candidate.md),
then from the repository root:

```sh
bash coakka-runtime/jvm/java-basic/run.sh
```

The standalone build uses--release8/strict warnings and a separate selected
consumer Java executable, not the build JDK as the minimum. It admits five
exact local JARs, no old public-coordinate or native-source override. The
2s ask/3s shutdown budget is unchanged. Check the actual echo and one-request
counters/pending0, report Core's startup/ABI/configuration facts rather than
reconstructing them from input. Close runs before errors escape, with operation
and cleanup failures preserved.

See the candidate guide for native PowerShell Gradle invocation, intentional
cache retention and Windows Java8's ASCII native-cache requirement. No Core
library/provider install or whole-process memory/performance claim.
