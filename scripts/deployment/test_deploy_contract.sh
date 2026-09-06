#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIRECTORY}/../.." && pwd)"
DEPLOY_SCRIPT="${SCRIPT_DIRECTORY}/deploy_cloud_run_blue_green.sh"
WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/deploy-cloud-run-staging.yml"

for variable_name in \
  GCP_SPEECH_API_BASE_URL \
  GCP_SPEECH_API_KEY_SECRET \
  GCP_SPEECH_API_KEY_SECRET_VERSION; do
  grep -q "${variable_name}" "${DEPLOY_SCRIPT}"
  grep -q "${variable_name}" "${WORKFLOW}"
done

grep -q 'CHEMICHECK119_SPEECH_API_IAM_ENABLED=true' "${DEPLOY_SCRIPT}"
grep -q 'CHEMICHECK119_SPEECH_API_IAM_AUDIENCE=' "${DEPLOY_SCRIPT}"
grep -q 'CHEMICHECK119_SPEECH_API_SCHEMA=chemicheck119-speech-api-v1' "${DEPLOY_SCRIPT}"
grep -q 'CHEMICHECK119_SPEECH_API_RESPONSE_TIMEOUT_SECONDS=45' "${DEPLOY_SCRIPT}"
grep -q 'CHEMICHECK119_SPEECH_API_MAX_AUDIO_BYTES=16777216' "${DEPLOY_SCRIPT}"
grep -q 'CHEMICHECK119_SPEECH_API_KEY=' "${DEPLOY_SCRIPT}"
grep -q 'Authenticated BFF to private Speech API smoke failed' "${DEPLOY_SCRIPT}"
grep -q '.requiresResponderReview == true' "${DEPLOY_SCRIPT}"
grep -q '.input.audioRetained == false' "${DEPLOY_SCRIPT}"
grep -q '.safetyBoundary.casConfirmationPerformed == false' "${DEPLOY_SCRIPT}"

echo "Backend deployment contract checks passed."
