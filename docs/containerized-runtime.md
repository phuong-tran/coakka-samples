# Containerized Runtime Notes

This note explains how to think about CoAkka runtime identity when the same
application image runs as multiple containers or Kubernetes pods.

The short rule:

```text
systemName is planned by humans.
nodeId is assigned at runtime by the platform.
```

Do not bake a different `nodeId` into each image.
Build one reusable image and inject instance identity when each container
starts.

## Build Time Versus Runtime

At build time, every replica should use the same image:

```text
billing:1.0.0
```

The image can include:

- application code
- CoAkka connector package
- native runtime artifacts
- default config
- entrypoint scripts

The image should not include:

- fixed pod identity
- fixed container identity
- a unique `nodeId` for one future replica

At runtime, the orchestrator supplies a different identity per process:

```text
billing pod A -> COAKKA_NODE_ID=billing-7d9f8c-a
billing pod B -> COAKKA_NODE_ID=billing-7d9f8c-b
billing pod C -> COAKKA_NODE_ID=billing-7d9f8c-c
```

The connector reads that value and passes it into `RuntimeStartSpec`.

## Kubernetes

Use the Kubernetes Downward API to inject pod metadata.

Pod name is usually the practical default:

```yaml
env:
  - name: COAKKA_SYSTEM_NAME
    value: billing
  - name: COAKKA_NODE_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
```

Use pod UID when identity must not be reused across pod restarts or
rescheduling:

```yaml
env:
  - name: COAKKA_SYSTEM_NAME
    value: billing
  - name: COAKKA_NODE_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.uid
```

In application code:

```kotlin
val systemName = System.getenv("COAKKA_SYSTEM_NAME") ?: "billing"
val nodeId = System.getenv("COAKKA_NODE_ID")
    ?: InetAddress.getLocalHost().hostName

val startSpec = RuntimeStartSpec(
    systemName = systemName,
    nodeId = nodeId,
    queueCapacity = 128,
    strictNoDrop = true,
    separateDeliveredRequestLane = true,
    generation = generation,
    routes = routes,
)
```

## Docker Compose

For a small fixed topology, the sample Compose files may use stable service or
container names because each service runs one replica.

For scaled Compose-style deployments, do not rely on a value baked into the
image. Prefer an environment value supplied by the runtime, or fall back to the
container hostname if it is unique in that environment:

```yaml
services:
  billing:
    image: billing:1.0.0
    environment:
      COAKKA_SYSTEM_NAME: billing
      COAKKA_NODE_ID: ${HOSTNAME}
```

Exact Compose interpolation behavior varies by runtime and shell. When in
doubt, use an entrypoint script that reads `hostname` inside the container and
exports `COAKKA_NODE_ID` before starting the app.

## What Must Be Unique?

Multiple replicas can share `systemName`:

```text
systemName = billing
nodeId = billing-pod-a

systemName = billing
nodeId = billing-pod-b
```

That means both runtime participants belong to the same logical service.

`nodeId` should be unique within that `systemName`.
Duplicate `nodeId` values make logs, stats, health, deadletters, and route
ownership ambiguous. Request delivery may still work if route selection does
not depend on node identity, but operations become unreliable.

## Route Strategy Still Decides Work Placement

`systemName` and `nodeId` identify runtime participants.
They do not by themselves decide how work is distributed.

Work placement is still determined by:

- route snapshot
- target name
- endpoint list
- endpoint flags such as `LOCAL` or `UNAVAILABLE`
- route strategy such as `SINGLE_OWNER`, `WEIGHTED_ROUND_ROBIN`, or
  `RENDEZVOUS_HASH`

For example, three billing pods may share one `systemName`, each with a unique
`nodeId`. Whether a target uses one owner, round-robin distribution, or stable
hashing depends on the route strategy published by the connector/control plane.

