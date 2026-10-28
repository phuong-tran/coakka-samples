# Swift runtime basic candidate

Use the exact private source package in the
[candidate guide](../../docs/runtime-swift-candidate.md), not public2.5.2.
Set explicit candidate/work/cache/deployment roots, then run `bash run.sh` here
or `bash run.sh coakka-runtime swift basic` from repo root. Native Windows uses the
guide's configured native developer shell, not WSL ELF.

Ordinary SwiftPM builds the app and its dynamic runtime module with actual
WMO/Osize/no-reflection/link stripping. Installed execution uses the bundled
Core normally. The sample expects `echo-hello-runtime-swift`, checks Core-issued
startup/queue/delivery facts and a matched route miss with the original target.
Both asks keep two-second budgets. It omits queue/port/transport/default knobs;
Core is the source of truth, not an example's copied defaults table.

Swift defer cannot throw. AppScope therefore preserves both operation and
shutdown errors; its four pure failure combinations are tested separately.
A failed close is not rewritten as success, timeout or an automatic retry.
This example does not claim whole-process leak freedom, a performance SLA or
coordinated release completion.
