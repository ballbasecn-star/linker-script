#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${PROJECT_ROOT}/deploy/linker-script"
ENV_FILE="${DEPLOY_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example and fill the real values first." >&2
  exit 1
fi

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-linkscript}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"

if ! docker network inspect shared-proxy >/dev/null 2>&1; then
  docker network create shared-proxy >/dev/null
fi

echo "Deploying ${IMAGE_REPOSITORY}:${IMAGE_TAG}"
cd "${DEPLOY_DIR}"
IMAGE_REPOSITORY="${IMAGE_REPOSITORY}" IMAGE_TAG="${IMAGE_TAG}" docker compose up -d
docker compose ps
