# Bun Logger Samples

Bun samples consume the published `coakka-logger-bun` tarball from
`coakka-publish`.

Current samples:

- `basic`: install the published tarball into a temporary Bun project, load the
  embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the published tarball, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/bun/basic/run.sh
bash logger/bun/pressure/run.sh
```
