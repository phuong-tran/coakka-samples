# Go Logger Samples

Go samples consume the published `coakka-logger-go` source tarball from
`coakka-publish`.

Current samples:

- `basic`: extract the published tarball into a temporary Go module, use a local
  `replace`, load the embedded native logger, emit one record, drain it, and
  print counters
- `pressure`: extract the published tarball, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/go/basic/run.sh
bash logger/go/pressure/run.sh
```
