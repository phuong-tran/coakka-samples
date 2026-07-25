# Python Logger Samples

Python samples consume the published `coakka-logger` GitHub Release wheel from
`coakka-publish`. PyPI publishing is a separate package-manager step; these
samples do not claim that normal `pip install coakka-logger` is available yet.

Current samples:

- `basic`: install the published wheel into a temporary site-packages directory,
  load the embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the published wheel, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/python/basic/run.sh
bash logger/python/pressure/run.sh
```
