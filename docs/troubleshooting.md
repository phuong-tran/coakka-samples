# CoAkka Troubleshooting

Use this guide as the common first stop for runtime, connector, sample, and
artifact problems. Keep secrets out of commands, logs, and public reports.

## Collect Facts First

Record:

- CoAkka package and native runtime versions
- release channel and exact archive or library SHA-256
- operating system, version, CPU architecture, and container or VM boundary
- connector language and framework version
- runtime ABI, build ID, compiled capabilities, and active connection/security
  snapshots from public introspection
- stable numeric status, apply reason, validation code, and deadletter or
  transport-failure facts

Do not diagnose from an error string alone when structured fields are
available.

## Startup And Configuration

| Symptom | Likely cause | Action |
| --- | --- | --- |
| `COAKKA_V2_ERR_BAD_STATE` while applying connection options | Runtime already started or is terminally stopped | Apply connection strategy in `CREATED`; create a new runtime for a live strategy change |
| `COAKKA_V2_ERR_FEATURE_UNAVAILABLE` | Mode, TLS source, or tuning is not compiled in this profile | Query capabilities and use a supported mode; do not infer capability from edition name alone |
| `COAKKA_V2_ERR_FEATURE_NOT_ENTITLED` | Compiled feature is not entitled for the active distribution | Check release and license metadata; do not retry as a transient transport error |
| `changed == 0` after apply | Rejected attempt or already-effective configuration | Inspect `apply_status`, `reason`, `validation`, and the returned active/effective snapshot |
| Startup fails without an active route snapshot or exported handles | Lifecycle prerequisites are incomplete | Export requested host lanes, apply an initial valid control snapshot, then start |
| Stale control generation | Generation is not strictly newer | Serialize control ownership and submit a higher monotonic generation |

Configuration apply is atomic. A rejected connection or security apply keeps
the previous active configuration; do not issue a follow-up getter and assume
it describes the failed proposal.

## Connection Strategy

- Confirm the mode exists in the runtime capability snapshot.
- Remember that Community bounded pool uses fixed defaults and rejects numeric
  tuning.
- Do not change strategy after startup. Use a new runtime and an
  application-owned cutover.
- Do not automatically replay after an ambiguous write or connection-scoped
  multiplexing failure.

See [Runtime Connection Strategies](connection-strategies.md) for defaults,
availability, and code examples.

## TLS And mTLS

| Symptom | Check |
| --- | --- |
| Credential reload rejected | Generation must be strictly newer, mode must match the active TLS/mTLS mode, and all required file fields must be present |
| Active generation did not change | Inspect `reason` and `validation`; the previous generation remains active by design |
| Unknown CA or untrusted peer | Verify the intended CA bundle on both peers and the complete presented chain |
| Hostname or IP mismatch | Certificate identities must match the exact endpoint used by the connector |
| Client certificate required | mTLS server requires a valid client identity and trust chain |
| Certificate appears expired or not yet valid | Verify host wall clock and certificate validity; edge devices need an explicit time-readiness gate |
| Old connection remains briefly after drain reload | Drain is bounded retirement, not a synchronous barrier; an in-flight exchange may finish on its captured generation |

Runtime introspection never returns private keys, PEM bytes, or credential file
paths. Inspect those through the host secret-management boundary without
copying them into application logs.

See [Runtime TLS And mTLS](tls-and-mtls.md) for parameter, ownership, rotation,
and deployment guidance.

## Network Placement

A private address, LAN, VLAN, Kubernetes cluster network, or device subnet is a
controlled network, not an automatically secure network. Decide explicitly
where peer identity, encryption, certificate rotation, and authorization live.

- In Kubernetes, prefer ingress or service-mesh TLS when that platform already
  owns identity and policy for the complete path.
- Use runtime TLS/mTLS for end-to-end runtime identity, traffic outside the
  mesh, or deployments without that infrastructure.
- On Raspberry Pi, BeagleBone, bare metal, edge Linux, and industrial Android,
  verify architecture, clock readiness, CA provisioning, key permissions, and
  release evidence separately.

## Artifact Integrity And Platform Warnings

First compare the archive SHA-256 and, after extraction, the inner runtime
library SHA-256 with release metadata. A candidate is not a promoted release
merely because its archive can be downloaded.

Do not confuse signed release receipts with Apple code signing/notarization or
Windows Authenticode. CoAkka's current native-signing status and verification
flow are documented in
[Runtime Release Signing And Platform Trust](runtime-release-signing-and-platform-trust.md).

For Linux load failures, check in this order:

1. CPU architecture and binary format.
2. Archive and inner-library digest.
3. Execute/read permissions and mount policy.
4. Loader search path and required native dependencies.
5. C/C++ runtime and OpenSSL compatibility recorded by the release.
6. Organization-specific integrity enforcement.

For macOS Gatekeeper or Windows publisher warnings, verify the bytes and follow
local policy. Do not disable system-wide security controls as a generic fix.

If `codesign -d --verbose=4` reports `adhoc` or `linker-signed` with no Team ID,
the Mach-O file has no Apple publisher identity. That output does not conflict
with release metadata that says publisher signing is absent. On Windows, check
the PE certificate table or an approved Authenticode verifier; a zero-sized
certificate table means no embedded Authenticode signature. Neither result is
a Linux loader requirement.

## Escalation

If the issue remains reproducible, send the collected facts and the smallest
reproduction through the correct issue tracker. Report vulnerabilities,
credential exposure, and sensitive artifact-integrity concerns privately.
See [Contact And Support](contact-and-support.md).
