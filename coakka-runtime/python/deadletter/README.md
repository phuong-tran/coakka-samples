# Python Runtime Deadletter

Send one request to an absent route, require its matched route-miss result,
then observe the same event through one bounded subscription. Use the
[private candidate wheel/workflow](../../docs/runtime-python-candidate.md),
not the old public package or a separately installed native image.

After selecting the exact local wheel and explicit work root, run:

```sh
bash coakka-runtime/python/deadletter/run.sh
```

Native Windows uses the direct Python runner in the guide. The standard
installer stays offline with no dependency/source/native fallback.

Output includes:

```text
coakka_runtime_deadletter reason=ROUTE_MISS target=samples.runtime.python.deadletter.missing generation=1
coakka_runtime_deadletter_observed matchedPending=true target=samples.runtime.python.deadletter.missing
coakka_runtime_stats routeMisses=1 deadletters=1 matchedDeadletters=1
```

The unknown-preserving public numeric reason must equal the documented route
miss, and the nullable original envelope must contain the expected target.
One observed event must match the pending request; counters/pending0 are checked.
Observation unregisters on every body failure before host close. Inspection,
ask and worker errors propagate after cleanup, not as fabricated empty facts.
The2s ask/1s observation bounds remain unchanged. AUTO/MULTIPLEX, queue/monitor
and other incidental tuning remain Core defaults.
