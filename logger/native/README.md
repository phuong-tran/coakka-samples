# Native C/C++ Logger Samples

These samples consume the published native C/C++ logger archive from
`coakka-publish`.

The native package is not a Maven artifact. The sample runner resolves:

```text
logger/native/releases/1.2.1+f50756ebff0d/coakka-logger-native-1.2.1.tar.gz
```

It then builds against the archive with CMake:

- `CoAkkaLoggerNative::core` for the public C ABI
- `CoAkkaLoggerNative::native_cpp_connector` for the small C++ wrapper

Run both native logger samples:

```sh
bash run.sh logger native basic
bash run.sh logger native pressure
```

The host application owns configuration and lifecycle. The native logger core
only receives explicit C ABI calls from the host process; the C++ wrapper is a
thin owner/convenience layer over that ABI.
