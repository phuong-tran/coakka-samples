# Containerized Runtime Notes

This note explains how to think about CoAkka runtime identity when the same
application image runs as multiple containers or Kubernetes pods.

The short rule:

```text
systemName is planned by humans.
nodeId is assigned at runtime by the platform.
host/port are advertised endpoint config read from the environment or control plane.
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

The same rule applies to endpoint `host` and `port`. Local samples often use
`127.0.0.1`, but production connectors should read advertised endpoint
addresses from environment variables, Kubernetes metadata, service discovery,
or a control-plane route snapshot.

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
  - name: COAKKA_LOCAL_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.podIP
  - name: COAKKA_LOCAL_PORT
    value: "19301"
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
val localHost = System.getenv("COAKKA_LOCAL_HOST")
    ?: InetAddress.getLocalHost().hostAddress
val localPort = System.getenv("COAKKA_LOCAL_PORT")?.toInt()
    ?: 19301

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

## Endpoint Host And Port

`host` and `port` answer this question:

```text
What address should other runtime participants use for this endpoint?
```

They are not meant to be manually edited in application source for every
deployment. The connector should map environment or control-plane data into the
route snapshot.

Common sources:

- `status.podIP` for a direct pod endpoint
- Kubernetes Service DNS for service-level routing
- StatefulSet DNS for stable per-pod names
- Docker Compose service names for local multi-container samples
- Consul, DNS, or another service registry
- ConfigMap, Helm values, or a custom control plane

Examples:

```text
local dev:
  host = 127.0.0.1
  port = 19301

Kubernetes pod endpoint:
  host = ${COAKKA_LOCAL_HOST}        # status.podIP
  port = ${COAKKA_LOCAL_PORT}

Kubernetes service endpoint:
  host = customer-store.default.svc.cluster.local
  port = 19301

StatefulSet endpoint:
  host = customer-store-0.customer-store.default.svc.cluster.local
  port = 19301

Docker Compose:
  host = python-store
  port = 19301
```

Choosing service DNS versus per-pod endpoints is a topology decision. Service
DNS lets the platform perform service-level balancing. Per-pod endpoints give
the CoAkka route snapshot and route strategy direct visibility into each
eligible runtime endpoint.

For replicated application roles, keep the runtime port stable across replicas.
If `billing` runs three pods, each `billing` pod should normally expose the same
runtime port, such as `19301`. The value that changes per pod is the advertised
host identity, not the port:

```text
Kubernetes Service endpoint:
  target = billing.invoice.create
  host = billing.default.svc.cluster.local
  port = 19301

Expanded pod endpoints:
  target = billing.invoice.create
  endpoints =
    billing-0.billing.default.svc.cluster.local:19301
    billing-1.billing.default.svc.cluster.local:19301
    billing-2.billing.default.svc.cluster.local:19301
```

With a Service DNS endpoint, the runtime sees one logical endpoint and the
platform resolves that service name to backing pods. With expanded pod
endpoints, the route snapshot sees each replica explicitly and route strategy
can choose among those eligible endpoints. Both shapes are valid; choose one
intentionally and keep the port convention consistent for the application role.

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
      COAKKA_LOCAL_HOST: billing
      COAKKA_LOCAL_PORT: "19301"
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
