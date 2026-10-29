# Python Runtime Hot Reload

One process demonstrates route-snapshot control, not a requirement to reload
every deployment. Many deployments can use one startup snapshot and rollout.
Use the [exact private wheel workflow](../../../docs/coakka-runtime/previews/3.0.0/runtime-python-candidate.md),
then from the repository root:

```sh
bash coakka-runtime/python/hot-reload/run.sh
```

The workflow uses an offline standard installer and ordinary installed Core
resolution, no older public coordinate/source/native override. Native Windows
uses the direct Python entrypoint documented in that guide.

Generation1 routes the first target; stale1 must refuse with Core's typed
status. Valid2 replaces the table, and the old target becomes a route miss.
Invalid3 must refuse without advancing the active generation. Both business
responses and final three route-miss/deadletter matches/pending0 are checked.
Refusal checks consume `CoreCallError.status`/`CoakkaStatus`, never error text.
All inspection errors escape after context-managed host close.

The generation helper first reads already-applied Core health and can return
without a notification when monitor is OFF. The sample reports actual monitor
state and makes no claim that a doorbell arrived. It does not turn monitor ON
merely because the facade is named monitor. Keep Core AUTO/MULTIPLEX and
incidental tuning defaulted; route generations are explicit because this
sample teaches their contract. Existing2s asks/1s generation wait remain.
