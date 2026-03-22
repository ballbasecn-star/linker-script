#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${PROJECT_ROOT}/deploy/linker-script"
COMPOSE_FILE="${DEPLOY_DIR}/compose.yaml"

PROD_HOST="${PROD_HOST:?PROD_HOST is required}"
PROD_USER="${PROD_USER:?PROD_USER is required}"
PROD_DEPLOY_DIR="${PROD_DEPLOY_DIR:?PROD_DEPLOY_DIR is required}"
PROD_PORT="${PROD_PORT:-22}"
PROD_PASSWORD="${PROD_PASSWORD:-}"

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-linkscript}"
IMAGE_TAG="${IMAGE_TAG:-}"
APP_VERSION="${APP_VERSION:-}"
RUN_TESTS="${RUN_TESTS:-true}"
SKIP_BUILD="${SKIP_BUILD:-false}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
SSH_OPTS=(-o StrictHostKeyChecking=no -p "${PROD_PORT}")
CONTROL_PATH="/tmp/linkscript-prod-${PROD_HOST//[^[:alnum:]]/_}-${PROD_PORT}.sock"
SSH_RUNTIME_OPTS=("${SSH_OPTS[@]}")

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

run_local() {
  echo "+ $*"
  "$@"
}

remote_exec() {
  local cmd="$1"
  ssh "${SSH_RUNTIME_OPTS[@]}" "${PROD_USER}@${PROD_HOST}" "${cmd}"
}

remote_copy() {
  local src="$1"
  local dest="$2"
  ssh "${SSH_RUNTIME_OPTS[@]}" "${PROD_USER}@${PROD_HOST}" "cat > '${dest}'" < "${src}"
}

start_ssh_master() {
  if [[ -n "${PROD_PASSWORD}" ]]; then
    rm -f "${CONTROL_PATH}"
    SSHPASS="${PROD_PASSWORD}" sshpass -e ssh \
      "${SSH_OPTS[@]}" \
      -o ControlMaster=yes \
      -o ControlPersist=600 \
      -o ControlPath="${CONTROL_PATH}" \
      -Nf "${PROD_USER}@${PROD_HOST}"
    SSH_RUNTIME_OPTS+=(-o ControlPath="${CONTROL_PATH}")
  fi
}

stop_ssh_master() {
  if [[ -S "${CONTROL_PATH}" ]]; then
    ssh "${SSH_OPTS[@]}" -o ControlPath="${CONTROL_PATH}" -O exit "${PROD_USER}@${PROD_HOST}" >/dev/null 2>&1 || true
    rm -f "${CONTROL_PATH}"
  fi
}

wait_for_remote_health() {
  local started_at
  started_at="$(date +%s)"
  while true; do
    if remote_exec "docker exec linker-script /bin/sh -lc 'wget -qO- http://127.0.0.1:8080/actuator/health'" \
      | grep -q '"status":"UP"'; then
      return 0
    fi

    if (( "$(date +%s)" - started_at >= HEALTH_TIMEOUT_SECONDS )); then
      return 1
    fi
    sleep 2
  done
}

require_command git
require_command docker
require_command ssh
if [[ -n "${PROD_PASSWORD}" ]]; then
  require_command sshpass
fi
trap stop_ssh_master EXIT
start_ssh_master

if [[ -z "${IMAGE_TAG}" ]]; then
  if [[ -n "$(git -C "${PROJECT_ROOT}" status --porcelain)" ]]; then
    DIRTY_SUFFIX="-dirty"
  else
    DIRTY_SUFFIX=""
  fi
  IMAGE_TAG="$(date +%Y%m%d)-$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)${DIRTY_SUFFIX}-amd64"
fi

if [[ -z "${APP_VERSION}" ]]; then
  APP_VERSION="${IMAGE_TAG}"
fi

ARCHIVE_PATH="${PROJECT_ROOT}/build/releases/${IMAGE_REPOSITORY}-${IMAGE_TAG}.tar.gz"
REMOTE_ARCHIVE_PATH="${PROD_DEPLOY_DIR}/${IMAGE_REPOSITORY}-${IMAGE_TAG}.tar.gz"
REMOTE_SCRIPT_PATH="/tmp/linkscript_upgrade_${IMAGE_TAG}.sh"

echo "Preparing prod upgrade"
echo "Target: ${PROD_USER}@${PROD_HOST}:${PROD_DEPLOY_DIR}"
echo "Image: ${IMAGE_REPOSITORY}:${IMAGE_TAG}"
echo "Archive: ${ARCHIVE_PATH}"

if [[ "${RUN_TESTS}" == "true" ]]; then
  run_local "${PROJECT_ROOT}/gradlew" test
fi

if [[ "${SKIP_BUILD}" != "true" ]]; then
  run_local env IMAGE_REPOSITORY="${IMAGE_REPOSITORY}" IMAGE_TAG="${IMAGE_TAG}" APP_VERSION="${APP_VERSION}" \
    "${PROJECT_ROOT}/scripts/build-release-image.sh"
fi

if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  echo "Missing archive: ${ARCHIVE_PATH}" >&2
  exit 1
fi

echo "Uploading artifact and compose file"
remote_copy "${ARCHIVE_PATH}" "${REMOTE_ARCHIVE_PATH}"
remote_copy "${COMPOSE_FILE}" "${PROD_DEPLOY_DIR}/compose.yaml"

cat > /tmp/linkscript_upgrade_remote.sh <<EOF
set -euo pipefail
cd ${PROD_DEPLOY_DIR}
VERSION=${IMAGE_TAG}
IMAGE_REPOSITORY=${IMAGE_REPOSITORY}
COMPOSE_BACKUP="compose.yaml.bak-\${VERSION}"
ENV_BACKUP=".env.bak-\${VERSION}"

rollback() {
  echo "Deployment failed, rolling back..."
  if [[ -f "\${COMPOSE_BACKUP}" ]]; then
    cp "\${COMPOSE_BACKUP}" compose.yaml
  fi
  if [[ -f "\${ENV_BACKUP}" ]]; then
    cp "\${ENV_BACKUP}" .env
  fi
  docker compose up -d || true
}

trap rollback ERR

cp compose.yaml "\${COMPOSE_BACKUP}"
cp .env "\${ENV_BACKUP}"

python3 <<'PY'
from pathlib import Path
path = Path('.env')
lines = path.read_text().splitlines()
updates = {
    'IMAGE_REPOSITORY': '${IMAGE_REPOSITORY}',
    'IMAGE_TAG': '${IMAGE_TAG}',
}
seen = set()
result = []
for line in lines:
    if '=' in line:
        key, value = line.split('=', 1)
        if key in updates:
            result.append(f'{key}={updates[key]}')
            seen.add(key)
            continue
    result.append(line)
for key, value in updates.items():
    if key not in seen:
        result.insert(0, f'{key}={value}')
path.write_text('\\n'.join(result) + '\\n')
PY

gunzip -c ${IMAGE_REPOSITORY}-${IMAGE_TAG}.tar.gz | docker load
docker compose up -d
docker ps --filter name=linker-script --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
EOF

chmod +x /tmp/linkscript_upgrade_remote.sh
remote_copy /tmp/linkscript_upgrade_remote.sh "${REMOTE_SCRIPT_PATH}"

echo "Executing remote upgrade"
remote_exec "bash ${REMOTE_SCRIPT_PATH}"

echo "Waiting for remote health"
if ! wait_for_remote_health; then
  echo "Remote health check failed after ${HEALTH_TIMEOUT_SECONDS}s" >&2
  exit 1
fi

echo "Remote health check passed"
remote_exec "docker inspect -f '{{.Config.Image}} {{.State.Status}}' linker-script"
remote_exec "grep -E '^IMAGE_(REPOSITORY|TAG)=' ${PROD_DEPLOY_DIR}/.env"

echo "Prod upgrade completed"
