# Mojo Runtime Samples

Mojo runtime samples use the pinned public source connector package
`1.3.1+bda2ef5-0a0aa76` for the bundled native runtime library. The basic
sample keeps its Mojo entrypoint and C shim in this repository and covers
lifecycle/control, raw request/reply, and route-miss deadletter handling.

Run the basic sample:

```sh
bash run.sh runtime mojo basic
```

Watch the Mojo runtime walkthrough:

![CoAkka Runtime Mojo walkthrough](../../docs/assets/coakka-runtime-mojo.gif)

Full recording: [coakka-runtime-mojo.mp4](../../docs/assets/coakka-runtime-mojo.mp4)
