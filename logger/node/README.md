# Node.js Logger Samples

Node.js samples consume `coakka-logger-node@1.2.7` from npm.

Current samples:

- `basic`: install the published package into a temporary npm project, load the
  embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the published package, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/node/basic/run.sh
bash logger/node/pressure/run.sh
```
