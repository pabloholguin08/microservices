#!/usr/bin/env bash
set -euo pipefail

# Deletes the local kind cluster and everything on it. Nothing here is
# meant to be durable — there's no persistent volume backup step because
# the whole point of the local-cluster setup is that k8s/scripts/setup.sh
# can rebuild it from scratch in a couple of minutes.
CLUSTER_NAME="pedidos"

kind delete cluster --name "$CLUSTER_NAME"
