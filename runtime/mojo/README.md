# Mojo Runtime Samples

Mojo runtime samples use the public source connector package
`0.2.0+b8ecfae-2d085e5` for the bundled native runtime library. The current
basic sample keeps its Mojo entrypoint and C shim in this repository and covers
lifecycle/control, raw request/reply, and route-miss deadletter handling.

Run the basic sample:

```sh
bash run.sh runtime mojo basic
```
