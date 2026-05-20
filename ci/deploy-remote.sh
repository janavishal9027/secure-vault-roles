#!/usr/bin/env bash
#
# Runs ON THE LXD HOST (the VPS) for the roles service.
# Same shape as Authentication / notes — only differences are the
# routing-test path and an opt-out flag for the public Ingress.
#
# INSTALL_PUBLIC_INGRESS:
#   true  (default) — apply ingress.yml + install host nginx snippet so
#                     /roles/* is reachable from the public host (handy
#                     for Swagger access in dev/stage)
#   false           — skip both, leaving roles reachable only on the
#                     cluster's internal network (the safe default for
#                     prod since the frontend never calls roles directly)

set -euxo pipefail

: "${APP_NAME:?}"
: "${KUBE_NAMESPACE:?}"
: "${IMAGE_REPO:?}"
: "${IMAGE_TAG:?}"
: "${INGRESS_HOST:?}"
: "${REMOTE_DIR:?}"
: "${LXD_CONTAINER:?}"
: "${LXD_BRIDGE_IP:?}"
REPLICAS="${REPLICAS:-1}"
INSTALL_PUBLIC_INGRESS="${INSTALL_PUBLIC_INGRESS:-true}"

export PATH="/snap/bin:$PATH"

mkdir -p "$REMOTE_DIR"
exec > >(tee -a "$REMOTE_DIR/deploy.log") 2>&1

echo "=== deploy-remote.sh starting at $(date -Iseconds) ==="
echo "    APP_NAME=$APP_NAME  LXD_CONTAINER=$LXD_CONTAINER  KUBE_NAMESPACE=$KUBE_NAMESPACE"
echo "    IMAGE=$IMAGE_REPO:$IMAGE_TAG  INGRESS_HOST=$INGRESS_HOST  REPLICAS=$REPLICAS"
echo "    INSTALL_PUBLIC_INGRESS=$INSTALL_PUBLIC_INGRESS"

cd "$REMOTE_DIR"

lxc_retry() {
  local attempt=1
  local max_attempts=4
  local rc=0
  while [ "$attempt" -le "$max_attempts" ]; do
    if "$@"; then return 0; fi
    rc=$?
    if [ "$attempt" -ge "$max_attempts" ]; then
      echo "  lxc operation failed after ${max_attempts} attempts (rc=${rc}): $*" >&2
      return $rc
    fi
    echo "  lxc operation failed (attempt ${attempt}/${max_attempts}, rc=${rc}); retrying in 2s..." >&2
    sleep 2
    attempt=$((attempt + 1))
  done
}

echo "=== Waiting for k3s to be ready in $LXD_CONTAINER (max 5 min) ==="
ATTEMPT=0
MAX_ATTEMPTS=60
until lxc exec "$LXD_CONTAINER" -- /usr/local/bin/k3s kubectl get nodes >/dev/null 2>&1; do
  ATTEMPT=$((ATTEMPT + 1))
  if [ "$ATTEMPT" -ge "$MAX_ATTEMPTS" ]; then
    echo "ERROR: k3s never came up in $LXD_CONTAINER after 5 minutes." >&2
    lxc_retry lxc exec "$LXD_CONTAINER" -- tail -n 80 /var/log/cloud-init-output.log >&2 || true
    exit 1
  fi
  echo "  attempt ${ATTEMPT}/${MAX_ATTEMPTS}: k3s API not yet responding, retrying in 5s..."
  sleep 5
done

kubectl_in_container() {
  lxc_retry lxc exec "$LXD_CONTAINER" -- /usr/local/bin/k3s kubectl "$@"
}

echo "=== Namespace ==="
kubectl_in_container get namespace "$KUBE_NAMESPACE" >/dev/null 2>&1 \
  || kubectl_in_container create namespace "$KUBE_NAMESPACE"

echo "=== Pushing manifests into $LXD_CONTAINER ==="
lxc_retry lxc exec "$LXD_CONTAINER" -- mkdir -p "/tmp/manifests/${APP_NAME}"

# deployment.yml and service.yml are always applied. ingress.yml is only
# applied if INSTALL_PUBLIC_INGRESS=true.
ALWAYS_FILES=(deployment.yml service.yml)
for f in "${ALWAYS_FILES[@]}"; do
  lxc_retry lxc file push "$f" "${LXD_CONTAINER}/tmp/manifests/${APP_NAME}/${f}"
done

if [[ "$INSTALL_PUBLIC_INGRESS" == "true" ]]; then
  lxc_retry lxc file push ingress.yml "${LXD_CONTAINER}/tmp/manifests/${APP_NAME}/ingress.yml"
fi

echo "=== Applying manifests to namespace '$KUBE_NAMESPACE' ==="
for f in "${ALWAYS_FILES[@]}"; do
  echo "--- applying $f ---"
  kubectl_in_container -n "$KUBE_NAMESPACE" apply -f "/tmp/manifests/${APP_NAME}/$f" -o name
done
if [[ "$INSTALL_PUBLIC_INGRESS" == "true" ]]; then
  echo "--- applying ingress.yml ---"
  kubectl_in_container -n "$KUBE_NAMESPACE" apply -f "/tmp/manifests/${APP_NAME}/ingress.yml" -o name
else
  # If a previous deploy installed an ingress and we're now in private
  # mode, remove it so /roles/* stops resolving on the public host.
  kubectl_in_container -n "$KUBE_NAMESPACE" \
    delete ingress "${APP_NAME}-ingress" --ignore-not-found
fi

verify_resource() {
  local kind="$1" name="$2"
  if ! kubectl_in_container -n "$KUBE_NAMESPACE" get "$kind" "$name" >/dev/null 2>&1; then
    echo "ERROR: $kind/$name not found in namespace $KUBE_NAMESPACE after apply" >&2
    kubectl_in_container -n "$KUBE_NAMESPACE" get all,ingress -o wide >&2 || true
    exit 1
  fi
  kubectl_in_container -n "$KUBE_NAMESPACE" get "$kind" "$name"
}
verify_resource deployment "${APP_NAME}-deployment"
verify_resource service    "${APP_NAME}-service"
if [[ "$INSTALL_PUBLIC_INGRESS" == "true" ]]; then
  verify_resource ingress  "${APP_NAME}-ingress"
fi

echo "=== Waiting for deployment to be Available (max 3 min) ==="
kubectl_in_container -n "$KUBE_NAMESPACE" \
  wait "deployment/${APP_NAME}-deployment" \
  --for=condition=Available --timeout=180s

echo "=== Service endpoints ==="
endpoints=$(kubectl_in_container -n "$KUBE_NAMESPACE" \
  get "endpoints/${APP_NAME}-service" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)
if [[ -z "$endpoints" ]]; then
  echo "ERROR: service ${APP_NAME}-service has no endpoints — pod not Ready or selector mismatch" >&2
  exit 1
fi
echo "endpoints: $endpoints"

# Public routing test only when we deployed the public ingress.
if [[ "$INSTALL_PUBLIC_INGRESS" == "true" ]]; then
  echo "=== Routing test (Traefik forwards /roles/* to the service?) ==="
  body=$(lxc_retry lxc exec "$LXD_CONTAINER" -- curl -s --max-time 10 \
    -H "Host: $INGRESS_HOST" \
    http://127.0.0.1/roles/ || true)
  status=$(lxc_retry lxc exec "$LXD_CONTAINER" -- curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    -H "Host: $INGRESS_HOST" \
    http://127.0.0.1/roles/ || true)
  echo "GET /roles/ -> HTTP $status"
  echo "body: ${body:-<empty>}"
  if [[ "$body" == "404 page not found" ]]; then
    echo "ERROR: Traefik returned its 404 page — ingress not registered for $INGRESS_HOST/roles" >&2
    kubectl_in_container -n "$KUBE_NAMESPACE" describe "ingress/${APP_NAME}-ingress" >&2 || true
    exit 1
  fi
  echo "OK: response came from roles-service (not Traefik 404)"
else
  echo "=== Skipping public routing test (INSTALL_PUBLIC_INGRESS=false) ==="
  echo "    roles is reachable only via cluster DNS http://${APP_NAME}-service/roles"
fi

# Host nginx snippet — only when we want roles publicly reachable.
if [[ "$INSTALL_PUBLIC_INGRESS" == "true" ]]; then
  echo "=== Installing nginx location snippet for $APP_NAME ==="
  SNIPPET_DIR="/etc/nginx/snippets/${KUBE_NAMESPACE}"
  SNIPPET_DST="${SNIPPET_DIR}/${APP_NAME}.location.conf"
  mkdir -p "$SNIPPET_DIR"
  cp "$REMOTE_DIR/roles.location.conf" "$SNIPPET_DST"
  echo "Installed snippet at $SNIPPET_DST"

  echo "=== nginx -t (config validation) ==="
  nginx -t

  echo "=== Reloading nginx ==="
  systemctl reload nginx
  echo "nginx reloaded"
else
  # If a previous deploy installed the snippet and we're now in private
  # mode, remove it so the host nginx stops claiming /roles/* publicly.
  echo "=== Removing host nginx snippet (INSTALL_PUBLIC_INGRESS=false) ==="
  SNIPPET_DST="/etc/nginx/snippets/${KUBE_NAMESPACE}/${APP_NAME}.location.conf"
  if [[ -f "$SNIPPET_DST" ]]; then
    rm -f "$SNIPPET_DST"
    nginx -t && systemctl reload nginx
    echo "Removed $SNIPPET_DST and reloaded nginx"
  else
    echo "No snippet to remove at $SNIPPET_DST"
  fi
fi

echo "=== Final namespace state ==="
kubectl_in_container -n "$KUBE_NAMESPACE" get all,ingress -o wide
echo "=== Deploy complete: $APP_NAME -> $LXD_CONTAINER/$KUBE_NAMESPACE @ $IMAGE_REPO:$IMAGE_TAG ==="
echo "=== deploy-remote.sh finished at $(date -Iseconds) ==="
