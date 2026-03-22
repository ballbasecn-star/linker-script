#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-linkscript}"
GIT_SHA="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)"
FULL_SHA="$(git -C "${PROJECT_ROOT}" rev-parse HEAD)"
if [[ -n "$(git -C "${PROJECT_ROOT}" status --porcelain)" ]]; then
  DIRTY_SUFFIX="-dirty"
else
  DIRTY_SUFFIX=""
fi
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d)-${GIT_SHA}${DIRTY_SUFFIX}-amd64}"
APP_VERSION="${APP_VERSION:-${IMAGE_TAG}}"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/build/releases}"

echo "Building ${IMAGE_REPOSITORY}:${IMAGE_TAG}"
docker build \
  --platform linux/amd64 \
  --build-arg APP_VERSION="${APP_VERSION}" \
  --build-arg VCS_REF="${FULL_SHA}" \
  -t "${IMAGE_REPOSITORY}:${IMAGE_TAG}" \
  "${PROJECT_ROOT}"

mkdir -p "${OUTPUT_DIR}"
ARCHIVE_PATH="${OUTPUT_DIR}/${IMAGE_REPOSITORY}-${IMAGE_TAG}.tar.gz"
docker save "${IMAGE_REPOSITORY}:${IMAGE_TAG}" | gzip > "${ARCHIVE_PATH}"

echo "Image: ${IMAGE_REPOSITORY}:${IMAGE_TAG}"
echo "Archive: ${ARCHIVE_PATH}"
