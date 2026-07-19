#!/usr/bin/env bash
set -euo pipefail

# Brings up the full Pedidos stack on a local kind cluster: creates the
# cluster (or reuses it), builds the 4 application images, loads them
# straight into kind's node (no registry needed for a local cluster),
# installs the infra chart (Postgres x3, RabbitMQ, Prometheus, Grafana,
# Jaeger) and waits for it, then installs the 4 application charts.
# `helm upgrade --install` makes every step idempotent, so re-running this
# after a code change just rebuilds the image and rolls the new one out.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="pedidos"
NAMESPACE="pedidos"

# kind's own default node image tracks the latest Kubernetes release, which
# has dropped support for cgroup v1 hosts. Docker Desktop can still land on
# a cgroup v1 VM depending on the backend/kernel in use (confirmed on this
# project's own dev machine), and a cgroup-v2-only node image fails
# `kubeadm init` outright there. v1.29.2 is pinned because it boots cleanly
# on both cgroup v1 and v2 hosts — if you know your machine runs cgroup v2,
# feel free to drop --image below and let kind use its own default.
NODE_IMAGE="kindest/node:v1.29.2"

echo "==> Creating kind cluster '$CLUSTER_NAME' (or reusing it if it already exists)"
if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  kind create cluster --name "$CLUSTER_NAME" --config "$ROOT_DIR/k8s/kind-config.yaml" --image "$NODE_IMAGE"
else
  echo "cluster '$CLUSTER_NAME' already exists, skipping create"
fi
kubectl config use-context "kind-$CLUSTER_NAME"

echo "==> Building application images"
docker build -t orders-service:local "$ROOT_DIR/orders-service"
docker build -t inventory-service:local "$ROOT_DIR/inventory-service"
docker build -t notification-service:local "$ROOT_DIR/notification-service"
# Unlike the backend images, VITE_*_API_URL are baked into the JS bundle at
# `npm run build` time, not read from the container at runtime — and they
# must point at the same host-mapped NodePort URLs k8s/kind-config.yaml
# exposes, since it's the browser (not another pod) that calls these APIs.
docker build -t frontend:local \
  --build-arg VITE_ORDERS_API_URL=http://localhost:8081 \
  --build-arg VITE_INVENTORY_API_URL=http://localhost:8082 \
  --build-arg VITE_NOTIFICATIONS_API_URL=http://localhost:8083 \
  "$ROOT_DIR/frontend"

echo "==> Loading images into kind"
kind load docker-image orders-service:local inventory-service:local notification-service:local frontend:local --name "$CLUSTER_NAME"

echo "==> Installing infra chart (Postgres x3, RabbitMQ, Prometheus, Grafana, Jaeger)"
helm upgrade --install infra "$ROOT_DIR/k8s/helm/infra" --namespace "$NAMESPACE" --create-namespace --wait --timeout 5m

echo "==> Installing application charts"
for chart in orders-service inventory-service notification-service frontend; do
  helm upgrade --install "$chart" "$ROOT_DIR/k8s/helm/$chart" --namespace "$NAMESPACE" --wait --timeout 3m
done

cat <<'EOF'

==> Stack is up. Reachable at:
  Frontend:                http://localhost:5173
  orders-service:          http://localhost:8081 (/actuator/health, /api/v1/orders)
  inventory-service:       http://localhost:8082 (/actuator/health, /api/v1/products)
  notification-service:    http://localhost:8083 (/actuator/health, /api/v1/notifications)
  RabbitMQ management UI:  http://localhost:15672 (guest/guest)
  Prometheus:              http://localhost:9090
  Grafana:                 http://localhost:3000 (admin/admin)
  Jaeger UI:               http://localhost:16686

Run k8s/scripts/teardown.sh to delete the cluster when you're done.
EOF
