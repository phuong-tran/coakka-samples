# Go Logger Samples

Go samples consume the published `coakka-logger-go` source tarball from
`coakka-publish`. The module path is fixed as
`github.com/phuong-tran/coakka-logger-go`, but these samples intentionally use
a tarball-local `replace` until the public Go module repository is opened and
tagged.

Current samples:

- `basic`: extract the published tarball into a temporary Go module, use a local
  `replace`, load the embedded native logger, emit one record, drain it, and
  print counters
- `pressure`: extract the published tarball, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

After the module repository exists, this lane can move to normal `go get`
without changing the runtime/logger sample behavior.

Run:

```sh
bash logger/go/basic/run.sh
bash logger/go/pressure/run.sh
```
