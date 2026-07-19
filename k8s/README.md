# Kubernetes deployment

A production-shaped alternative to `docker compose up` for running the same
Pedidos stack: three Helm-templated application charts, one infra chart
(Postgres x3, RabbitMQ, Prometheus, Grafana, Jaeger), and a
[kind](https://kind.sigs.k8s.io/) cluster config that maps every NodePort to
the same host ports docker-compose already uses. No paid cloud cluster, no
registry — everything builds and runs locally.

## Prerequisites

- Docker Desktop (or another Docker daemon) — already required for the
  docker-compose path.
- [`kind`](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) —
  runs a real Kubernetes cluster inside Docker containers.
- [`helm`](https://helm.sh/docs/intro/install/) v3+.
- `kubectl`, any reasonably recent version (Docker Desktop ships one).

If you don't have admin rights to install via a package manager, both `kind`
and `helm` ship as single self-contained binaries — download the Windows
`.exe` (kind) or `.zip` (helm) from their GitHub releases and drop them
anywhere on your `PATH`; no installer or elevation required.

## Quick start

```bash
k8s/scripts/setup.sh
```

This creates a kind cluster named `pedidos`, builds the four application
images, loads them straight into the cluster (kind has no built-in registry,
and none is needed for a single local node), installs the infra chart,
waits for it to be healthy, then installs the four application charts. It's
idempotent — re-run it after a code change and it rebuilds the affected
image and rolls out the new version via `helm upgrade --install`.

Once it finishes, the stack is reachable at the exact same URLs as
docker-compose:

| Service | URL |
|---|---|
| Frontend | <http://localhost:5173> |
| orders-service | <http://localhost:8081> |
| inventory-service | <http://localhost:8082> |
| notification-service | <http://localhost:8083> |
| RabbitMQ management UI | <http://localhost:15672> (guest/guest) |
| Prometheus | <http://localhost:9090> |
| Grafana | <http://localhost:3000> (admin/admin) |
| Jaeger UI | <http://localhost:16686> |

```bash
k8s/scripts/teardown.sh   # deletes the kind cluster and everything on it
```

## Layout

```
k8s/
├── kind-config.yaml           # cluster config: one control-plane node,
│                               # NodePort → host port mappings
├── scripts/
│   ├── setup.sh                # cluster up, build, load, helm install
│   └── teardown.sh
└── helm/
    ├── orders-service/         # one chart per backend service
    ├── inventory-service/
    ├── notification-service/
    ├── frontend/
    └── infra/                  # Postgres x3, RabbitMQ, Prometheus,
                                 # Grafana, Jaeger — everything the app
                                 # charts depend on but don't own
```

Each application chart is intentionally standalone rather than built on a
shared library chart: there are only three of them, they're small, and a
reviewer can read any one of them top to bottom without having to resolve
template helpers defined somewhere else. The three Postgres instances
inside the infra chart *do* share a single templated `range` — at that
point it's three near-identical StatefulSet/Service/Secret trios that would
otherwise be near-impossible to eyeball-diff for drift.

## How this differs from docker-compose

- **NodePort, not Ingress.** There's no ingress controller in this setup —
  each service that needs host access is a `NodePort` Service, and
  `kind-config.yaml`'s `extraPortMappings` forwards those fixed ports to
  `localhost`. This keeps the two deployment paths using identical URLs,
  at the cost of not being how you'd expose a real multi-host cluster
  (that's what an Ingress/Gateway is for, deliberately left out of scope
  here).
- **Replica counts are real, not cosmetic.** `orders-service`,
  `inventory-service`, and `notification-service` default to
  `replicaCount: 1` in their `values.yaml`, but every consumer is
  idempotent and every stock update goes through optimistic locking (see
  the main [README](../README.md#resilience-notes)), so
  `helm upgrade orders-service ./k8s/helm/orders-service --set replicaCount=3`
  is safe to run against a live cluster and is a reasonable way to see the
  idempotency guarantees actually being exercised under concurrent pods.
- **Liveness/readiness are two different endpoints.** Each Deployment
  probes `/actuator/health/liveness` and `/actuator/health/readiness`
  separately (Spring Boot's health groups, already configured in each
  service's `application.yml` since Phase 1) rather than the aggregate
  `/actuator/health` docker-compose's healthcheck uses — readiness reflects
  "can this pod serve traffic right now" (DB/broker reachable) and
  liveness reflects "is the process itself stuck," which is the distinction
  Kubernetes actually acts on (readiness failures pull a pod out of the
  Service; liveness failures restart the container).
- **Resource requests/limits are enforced**, not just documented — every
  container has both, sized from what was observed running the same images
  under docker-compose's `deploy.resources` blocks.

## Notable things found getting this to run

- **`kindest/node`'s latest tag assumes cgroup v2.** This project's own dev
  machine runs Docker Desktop on a VM that's still on cgroup v1, and
  `kind create cluster` with the default (latest, Kubernetes 1.36) node
  image failed `kubeadm init` outright — no useful error beyond a
  deprecation notice and a stalled control plane. `kindest/node:v1.29.2`,
  pinned in both `kind-config.yaml`'s companion `setup.sh` and documented
  there, boots cleanly on both cgroup v1 and v2 hosts. If your machine is
  confirmed to run cgroup v2, dropping the `--image` flag to use kind's
  current default works too.
- **`rabbitmq-diagnostics` is too heavy for an exec probe.** The natural
  choice for a RabbitMQ liveness/readiness check —
  `rabbitmq-diagnostics -q check_running` — spins up its own short-lived
  Erlang node and does a distributed-Erlang handshake with the broker just
  to answer "are you up." Under this cluster's CPU limits that consistently
  exceeded even a generous 10s probe timeout and put the pod into a
  crash-loop, even though the broker's own logs showed a complete startup
  in under 10 seconds. Both probes in
  [`templates/rabbitmq.yaml`](helm/infra/templates/rabbitmq.yaml) use a
  plain `tcpSocket` check against the AMQP port instead — cheaper, and
  "can I open a TCP connection to 5672" is a perfectly good proxy for "is
  the broker accepting connections" here.
- **The frontend's API URLs are a build-time concern, not a runtime one.**
  Vite inlines `VITE_*` variables into the JS bundle at `npm run build`
  time; setting them as container env vars in the frontend's Deployment
  would have no effect; they're set once during `docker build` in
  `setup.sh` instead, pointed at the same NodePort-mapped `localhost` URLs
  the browser will actually call.

## Iterating

After changing a service's code, re-run `k8s/scripts/setup.sh` — it rebuilds
all four images and re-runs `helm upgrade --install` for everything, which
is a few seconds of redundant work for the services you didn't touch but
keeps the script simple and safe to run unconditionally. For faster
iteration on a single service:

```bash
docker build -t orders-service:local ./orders-service
kind load docker-image orders-service:local --name pedidos
kubectl rollout restart deployment/orders-service -n pedidos
```

(`kubectl rollout restart` is needed because the image tag doesn't change —
`:local` is reused on every build — so the Deployment spec itself is
identical and Kubernetes wouldn't otherwise know to pull the new image.)
