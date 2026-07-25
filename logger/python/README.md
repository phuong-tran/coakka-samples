# Python Logger Samples

Python samples consume `coakka-logger==1.2.1` from PyPI. The GitHub Release
wheel in `coakka-publish` remains the checksum-tracked artifact mirror.

Current samples:

- `basic`: install the PyPI package into a temporary environment, load the
  embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the PyPI package, fill a queue with capacity `2`, observe
  rejected writes, drain the accepted records, and print dropped counters

Run:

```sh
bash logger/python/basic/run.sh
bash logger/python/pressure/run.sh
```
