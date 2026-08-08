# Mojo Runtime Samples

Mojo runtime samples use the pinned public source connector package
`1.4.1-source` over native runtime generation `1.4.1+9e02a51d`. The basic
sample keeps its Mojo entrypoint and C shim in this repository and covers
lifecycle/control, raw request/reply, and route-miss deadletter handling.

Run the basic sample:

```sh
bash run.sh runtime mojo basic
```

Watch the Mojo runtime walkthrough:

![CoAkka Runtime Mojo walkthrough](../../docs/assets/coakka-runtime-mojo.gif)

Full recording: [coakka-runtime-mojo.mp4](../../docs/assets/coakka-runtime-mojo.mp4)
