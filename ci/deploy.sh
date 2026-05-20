#!/usr/bin/env bash
#
# Deploy the roles service to one of the secure-vault-* k3s clusters.
# Same shape as the Authentication / notes / UI deploys.
#
# Required environment variables (set per Bitbucket Deployment):
#   VPS_USER, VPS_HOST, REMOTE_DIR, LXD_CONTAINER, KUBE_NAMESPACE,
#   APP_NAME, IMAGE_REPO, IMAGE_TAG, INGRESS_HOST, LXD_BRIDGE_IP,
#   DB_URL, DB_PASSWORD,
#   INTERNAL_ROLE_SERVICE_KEY     # shared with Authentication
#   AUTHENTICATION_SERVICE_URL    # internal cluster URL
# Optional:
#   DB_USERNAME (postgres), REPLICAS (1), SWAGGER_ENABLED (false)

set -euo pipefail

: "${VPS_USER:?}"
: "${VPS_HOST:?}"
: "${REMOTE_DIR:?}"
: "${LXD_CONTAINER:?}"
: "${KUBE_NAMESPACE:?}"
: "${APP_NAME:?}"
: "${IMAGE_REPO:?}"
: "${IMAGE_TAG:?}"
: "${INGRESS_HOST:?}"
: "${LXD_BRIDGE_IP:?}"
: "${DB_URL:?}"
: "${DB_PASSWORD:?}"
: "${INTERNAL_ROLE_SERVICE_KEY:?}"
: "${AUTHENTICATION_SERVICE_URL:?}"

DB_USERNAME="${DB_USERNAME:-postgres}"
REPLICAS="${REPLICAS:-1}"
SWAGGER_ENABLED="${SWAGGER_ENABLED:-false}"

REMOTE_DIR="${REMOTE_DIR}/${APP_NAME}"
REMOTE_TARGET="${VPS_USER}@${VPS_HOST}"

SSH_OPTS=(
  -o StrictHostKeyChecking=no
  -o BatchMode=yes
  -o ConnectTimeout=15
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=10
)

echo "==> Rendering manifests locally"
mkdir -p rendered
render_file() {
  local in="$1" out="$2"
  sed \
    -e "s|\${APP_NAME}|${APP_NAME}|g" \
    -e "s|\${KUBE_NAMESPACE}|${KUBE_NAMESPACE}|g" \
    -e "s|\${IMAGE_REPO}|${IMAGE_REPO}|g" \
    -e "s|\${IMAGE_TAG}|${IMAGE_TAG}|g" \
    -e "s|\${INGRESS_HOST}|${INGRESS_HOST}|g" \
    -e "s|\${REPLICAS}|${REPLICAS}|g" \
    -e "s|\${DB_URL}|${DB_URL}|g" \
    -e "s|\${DB_USERNAME}|${DB_USERNAME}|g" \
    -e "s|\${DB_PASSWORD}|${DB_PASSWORD}|g" \
    -e "s|\${INTERNAL_ROLE_SERVICE_KEY}|${INTERNAL_ROLE_SERVICE_KEY}|g" \
    -e "s|\${AUTHENTICATION_SERVICE_URL}|${AUTHENTICATION_SERVICE_URL}|g" \
    -e "s|\${SWAGGER_ENABLED}|${SWAGGER_ENABLED}|g" \
    "$in" > "$out"
}
render_file deployment.yml rendered/deployment.yml
render_file service.yml    rendered/service.yml
render_file ingress.yml    rendered/ingress.yml

echo "==> Rendering nginx location snippet"
sed -e "s|\${LXD_BRIDGE_IP}|${LXD_BRIDGE_IP}|g" \
    ci/nginx/roles.location.conf > rendered/roles.location.conf

echo "=== Rendered manifests (secrets redacted) ==="
for f in rendered/*.yml; do
  echo "--- $f ---"
  sed \
    -e "s|${DB_PASSWORD}|***DB_PASSWORD***|g" \
    -e "s|${INTERNAL_ROLE_SERVICE_KEY}|***INTERNAL_ROLE_SERVICE_KEY***|g" \
    "$f"
done

echo "==> Preparing remote staging dir ${REMOTE_DIR} on ${VPS_HOST}"
ssh "${SSH_OPTS[@]}" "$REMOTE_TARGET" "mkdir -p '${REMOTE_DIR}'"

echo "==> Shipping manifests + deploy-remote.sh to ${VPS_HOST}"
scp "${SSH_OPTS[@]}" \
    rendered/deployment.yml \
    rendered/service.yml \
    rendered/ingress.yml \
    rendered/roles.location.conf \
    ci/deploy-remote.sh \
    "${REMOTE_TARGET}:${REMOTE_DIR}/"

echo "==> Executing deploy-remote.sh on ${VPS_HOST}"
ssh "${SSH_OPTS[@]}" "$REMOTE_TARGET" \
    "env \
      APP_NAME='${APP_NAME}' \
      KUBE_NAMESPACE='${KUBE_NAMESPACE}' \
      IMAGE_REPO='${IMAGE_REPO}' \
      IMAGE_TAG='${IMAGE_TAG}' \
      INGRESS_HOST='${INGRESS_HOST}' \
      REPLICAS='${REPLICAS}' \
      REMOTE_DIR='${REMOTE_DIR}' \
      LXD_CONTAINER='${LXD_CONTAINER}' \
      LXD_BRIDGE_IP='${LXD_BRIDGE_IP}' \
      INSTALL_PUBLIC_INGRESS='${INSTALL_PUBLIC_INGRESS:-true}' \
      bash '${REMOTE_DIR}/deploy-remote.sh'"
