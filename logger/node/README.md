# Node.js Logger Samples

Node.js samples consume the published `coakka-logger-node` tarball from
`coakka-publish`.

Current samples:

- `basic`: install the published tarball into a temporary npm project, load the
  embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the published tarball, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/node/basic/run.sh
bash logger/node/pressure/run.sh
```
