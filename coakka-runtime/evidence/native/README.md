# Native Runtime Test Compatibility Path

Use the [Runtime test guide](../../coakka-runtime-test.md) to choose the released SDK
and run a test from Samples.

```sh
COAKKA_NATIVE_EVIDENCE_USE_PREBUILT=1 bash run.sh coakka-runtime coakka-runtime-test smoke
```

This directory's runner forwards the same options to the Runtime test runner.
The [public test source](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-runtime/coakka-runtime-test) includes build instructions.
