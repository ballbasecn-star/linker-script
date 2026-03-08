#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PORT="${APP_PORT:-18080}"
BASE_URL="http://127.0.0.1:${APP_PORT}"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-90}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-1}"
LOG_FILE="${LOG_FILE:-${PROJECT_ROOT}/build/smoke-dev.log}"

: "${PGHOST:=117.72.207.52}"
: "${PGPORT:=5432}"
: "${PGDATABASE:=postgres}"
: "${PGUSER:=postgres}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" >/dev/null 2>&1; then
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
  fi
}

json_field() {
  local expr="$1"
  python3 -c 'import json,sys
data=json.load(sys.stdin)
expr=sys.argv[1]
value=data
for part in expr.split("."):
    if part.isdigit():
        value=value[int(part)]
    else:
        value=value[part]
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)' "${expr}"
}

wait_for_health() {
  local started_at
  started_at="$(date +%s)"
  while true; do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi

    if (( "$(date +%s)" - started_at >= START_TIMEOUT_SECONDS )); then
      echo "Timed out waiting for ${BASE_URL}/actuator/health" >&2
      return 1
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done
}

wait_for_analysis() {
  local script_uuid="$1"
  local started_at
  started_at="$(date +%s)"
  while true; do
    local body
    body="$(curl -fsS "${BASE_URL}/api/v1/scripts/${script_uuid}")"
    local status
    status="$(printf '%s' "${body}" | json_field status)"

    if [[ "${status}" == "COMPLETED" ]]; then
      printf '%s' "${body}"
      return 0
    fi

    if [[ "${status}" == "FAILED" ]]; then
      echo "Analysis failed for ${script_uuid}" >&2
      printf '%s\n' "${body}" >&2
      return 1
    fi

    if (( "$(date +%s)" - started_at >= START_TIMEOUT_SECONDS )); then
      echo "Timed out waiting for async analysis of ${script_uuid}" >&2
      return 1
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done
}

echo "Starting dev profile on port ${APP_PORT}"
mkdir -p "$(dirname "${LOG_FILE}")"
cd "${PROJECT_ROOT}"
env \
  PGHOST="${PGHOST}" \
  PGPORT="${PGPORT}" \
  PGDATABASE="${PGDATABASE}" \
  PGUSER="${PGUSER}" \
  PGPASSWORD="${PGPASSWORD}" \
  ./gradlew --no-daemon bootRun --args="--spring.profiles.active=dev --server.port=${APP_PORT}" \
  >"${LOG_FILE}" 2>&1 &
APP_PID=$!
trap cleanup EXIT

wait_for_health
echo "Health check passed: ${BASE_URL}/actuator/health"

external_id="smoke-$(date +%s)"
ingest_payload="$(cat <<JSON
{"title":"项目汇报别再铺垫太长","content":"老板最想先听到的不是你做了什么，而是这件事到底有没有结果。很多人汇报时一上来就讲背景，三句话过去还没到重点。你只要先给结论，再补动作，最后讲风险和下一步，整个沟通效率会直接提升。想要这套汇报模板，评论区留一个结果。","sourcePlatform":"douyin","externalId":"${external_id}","statistics":{"likes":88,"shares":12}}
JSON
)"

ingest_response="$(curl -fsS -X POST "${BASE_URL}/api/v1/scripts/ingest" \
  -H 'Content-Type: application/json' \
  -d "${ingest_payload}")"
script_uuid="$(printf '%s' "${ingest_response}" | json_field scriptUuid)"
echo "Ingest passed: scriptUuid=${script_uuid}"

detail_response="$(wait_for_analysis "${script_uuid}")"
fragment_count="$(printf '%s' "${detail_response}" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["fragments"]))')"
echo "Async analysis passed: status=COMPLETED fragments=${fragment_count}"

search_response="$(curl -fsS "${BASE_URL}/api/v1/fragments/search?topic=%E6%B1%87%E6%8A%A5&type=HOOK&limit=3")"
search_count="$(printf '%s' "${search_response}" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')"
first_search_uuid="$(printf '%s' "${search_response}" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data[0]["scriptUuid"] if data else "")')"
if [[ "${search_count}" -lt 1 ]]; then
  echo "Fragment search returned no results" >&2
  exit 1
fi
echo "Fragment search passed: results=${search_count} firstScriptUuid=${first_search_uuid}"

generate_payload="$(cat <<JSON
{"topic":"向管理层汇报项目结果时怎么开场","sampleUuids":["${script_uuid}"],"options":{"tone":"专业、锋利","length":260}}
JSON
)"
generate_response="$(curl -fsS -X POST "${BASE_URL}/api/v1/compositions/generate" \
  -H 'Content-Type: application/json' \
  -d "${generate_payload}")"
log_id="$(printf '%s' "${generate_response}" | json_field logId)"
generated_refs="$(printf '%s' "${generate_response}" | python3 -c 'import json,sys; print(",".join(json.load(sys.stdin)["referenceUuids"]))')"
generated_content="$(printf '%s' "${generate_response}" | json_field content)"
generated_length="$(printf '%s' "${generated_content}" | python3 -c 'import sys; print(len(sys.stdin.read().strip()))')"
if [[ "${generated_length}" -lt 40 ]]; then
  echo "Generated content is unexpectedly short" >&2
  exit 1
fi
echo "Generation passed: logId=${log_id} referenceUuids=${generated_refs}"

auto_generate_response="$(curl -fsS -X POST "${BASE_URL}/api/v1/compositions/generate" \
  -H 'Content-Type: application/json' \
  -d '{"topic":"老板最关心的项目结果怎么讲","sampleUuids":[],"options":{"tone":"克制、清晰","length":220}}')"
auto_log_id="$(printf '%s' "${auto_generate_response}" | json_field logId)"
auto_ref_count="$(printf '%s' "${auto_generate_response}" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["referenceUuids"]))')"
auto_generated_content="$(printf '%s' "${auto_generate_response}" | json_field content)"
auto_generated_length="$(printf '%s' "${auto_generated_content}" | python3 -c 'import sys; print(len(sys.stdin.read().strip()))')"
if [[ "${auto_ref_count}" -lt 1 ]]; then
  echo "Automatic sample selection returned no references" >&2
  exit 1
fi
if [[ "${auto_generated_length}" -lt 40 ]]; then
  echo "Auto-generated content is unexpectedly short" >&2
  exit 1
fi
echo "Auto-sample generation passed: logId=${auto_log_id} references=${auto_ref_count}"

echo "Smoke test passed."
