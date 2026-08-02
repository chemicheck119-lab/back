#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  IMAGE_DIGEST
  RELEASE_GIT_COMMIT
  GCP_PROJECT_ID
  GCP_REGION
  GCP_ARTIFACT_REPOSITORY
  GCP_CLOUD_RUN_SERVICE
  GCP_RUNTIME_SERVICE_ACCOUNT
  GCP_MODEL_API_BASE_URL
  GCP_MODEL_API_KEY_SECRET
  GCP_MODEL_API_KEY_SECRET_VERSION
  GCP_SESSION_SECRET
  GCP_SESSION_SECRET_VERSION
  GCP_CORS_ALLOWED_ORIGINS
  GCP_PUBLIC_ANALYSIS_ENABLED
  GCP_REQUIRE_EXTERNAL_DATABASE
  GCP_STAGING_AUTH_ENABLED
)
for variable_name in "${required_variables[@]}"; do
  test -n "${!variable_name:-}" || {
    echo "Missing required deployment variable: $variable_name"
    exit 1
  }
done

minimum_instances="${GCP_MIN_INSTANCES:-0}"
maximum_instances="${GCP_MAX_INSTANCES:-1}"
cors_allowed_origins="${GCP_CORS_ALLOWED_ORIGINS:-}"
[[ "$minimum_instances" =~ ^[0-9]+$ ]]
[[ "$maximum_instances" =~ ^[1-9][0-9]*$ ]]
(( minimum_instances <= maximum_instances ))
test "$maximum_instances" = "1" || {
  echo "Staging is limited to one instance until shared DB concurrency and restore rehearsal pass."
  exit 1
}
[[ "$GCP_MODEL_API_KEY_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
[[ "$GCP_SESSION_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
[[ "$GCP_PUBLIC_ANALYSIS_ENABLED" =~ ^(true|false)$ ]]
[[ "$GCP_REQUIRE_EXTERNAL_DATABASE" =~ ^(true|false)$ ]]
[[ "$GCP_STAGING_AUTH_ENABLED" =~ ^(true|false)$ ]]
[[ "$RELEASE_GIT_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$GCP_MODEL_API_BASE_URL" =~ ^https://[a-z0-9.-]+\.run\.app/?$ ]]
[[ "$GCP_MODEL_API_KEY_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
[[ "$GCP_SESSION_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
test "$cors_allowed_origins" = "https://chemicheck119.site"

if [ "$GCP_REQUIRE_EXTERNAL_DATABASE" = "true" ]; then
  database_variables=(
    GCP_DATABASE_URL_SECRET
    GCP_DATABASE_URL_SECRET_VERSION
    GCP_DATABASE_USERNAME_SECRET
    GCP_DATABASE_USERNAME_SECRET_VERSION
    GCP_DATABASE_PASSWORD_SECRET
    GCP_DATABASE_PASSWORD_SECRET_VERSION
    GCP_VPC_NETWORK
    GCP_VPC_SUBNETWORK
    GCP_VPC_EGRESS
  )
  for variable_name in "${database_variables[@]}"; do
    test -n "${!variable_name:-}" || {
      echo "Missing required external database variable: $variable_name"
      exit 1
    }
  done
  [[ "$GCP_DATABASE_URL_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
  [[ "$GCP_DATABASE_USERNAME_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
  [[ "$GCP_DATABASE_PASSWORD_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
  [[ "$GCP_DATABASE_URL_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
  [[ "$GCP_DATABASE_USERNAME_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
  [[ "$GCP_DATABASE_PASSWORD_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
  [[ "$GCP_VPC_NETWORK" =~ ^[a-z][a-z0-9-]{0,62}$ ]]
  [[ "$GCP_VPC_SUBNETWORK" =~ ^[a-z][a-z0-9-]{0,62}$ ]]
  test "$GCP_VPC_EGRESS" = "private-ranges-only"
fi

if [ "$GCP_STAGING_AUTH_ENABLED" = "true" ]; then
  staging_auth_variables=(
    GCP_STAGING_AUTH_CALLBACK_URL
    GCP_STAGING_AUTH_USER_ID
    GCP_STAGING_AUTH_STATION_ID
    GCP_STAGING_AUTH_STATION_DISPLAY_NAME
    GCP_STAGING_AUTH_PASSWORD_SECRET
    GCP_STAGING_AUTH_PASSWORD_SECRET_VERSION
  )
  for variable_name in "${staging_auth_variables[@]}"; do
    test -n "${!variable_name:-}" || {
      echo "Missing required staging auth variable: $variable_name"
      exit 1
    }
  done
  [[ "$GCP_STAGING_AUTH_PASSWORD_SECRET_VERSION" =~ ^[1-9][0-9]*$ ]]
  [[ "$GCP_STAGING_AUTH_PASSWORD_SECRET" =~ ^[a-zA-Z0-9_-]+$ ]]
  [[ "$GCP_STAGING_AUTH_CALLBACK_URL" =~ ^https://chemicheck119\.site(/[^[:space:]]*)?$ ]]
  [[ "$GCP_STAGING_AUTH_USER_ID" =~ ^[A-Za-z0-9_.:@-]{1,128}$ ]]
  [[ "$GCP_STAGING_AUTH_STATION_ID" =~ ^[A-Za-z0-9_.:@-]{1,128}$ ]]
  [[ "$GCP_STAGING_AUTH_STATION_DISPLAY_NAME" != *";"* ]]
  [[ "$GCP_STAGING_AUTH_STATION_DISPLAY_NAME" != *$'\n'* ]]
fi

expected_image_prefix="$GCP_REGION-docker.pkg.dev/$GCP_PROJECT_ID/$GCP_ARTIFACT_REPOSITORY/be@sha256:"
[[ "$IMAGE_DIGEST" == "$expected_image_prefix"* ]]
[[ "$IMAGE_DIGEST" =~ @sha256:[0-9a-f]{64}$ ]]

run_attempt="${GITHUB_RUN_ATTEMPT:-1}"
revision_suffix="r${RELEASE_GIT_COMMIT:0:7}${run_attempt}"
candidate_tag="candidate-${RELEASE_GIT_COMMIT:0:7}-${run_attempt}"
service_snapshot="$(mktemp)"
candidate_snapshot="$(mktemp)"
cleanup() {
  rm -f "$service_snapshot" "$candidate_snapshot"
}
trap cleanup EXIT

previous_revision=""
if gcloud run services describe "$GCP_CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format=json >"$service_snapshot" 2>/dev/null; then
  previous_revision="$(python3 - "$service_snapshot" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
traffic = (payload.get("status") or {}).get("traffic") or []
active = [item for item in traffic if int(item.get("percent") or 0) > 0]
active.sort(key=lambda item: int(item.get("percent") or 0), reverse=True)
print((active[0].get("revisionName") if active else "") or "")
PY
)"
fi

env_vars="CHEMICHECK119_RELEASE_GIT_COMMIT=$RELEASE_GIT_COMMIT;CHEMICHECK119_RELEASE_ENVIRONMENT=staging;CHEMICHECK119_MODEL_API_BASE_URL=$GCP_MODEL_API_BASE_URL;CHEMICHECK119_MODEL_API_SCHEMA=chemiguard119-api-v1;CHEMICHECK119_MODEL_API_CONNECT_TIMEOUT_SECONDS=2;CHEMICHECK119_MODEL_API_RESPONSE_TIMEOUT_SECONDS=15;CHEMICHECK119_MODEL_API_MAX_RETRIES=1;CHEMICHECK119_MOVEMENT_ALLOW_DEMO_SIMULATION=false;CHEMICHECK119_CORS_ALLOWED_ORIGINS=$cors_allowed_origins;CHEMICHECK119_PUBLIC_ANALYSIS_ENABLED=$GCP_PUBLIC_ANALYSIS_ENABLED;CHEMICHECK119_REQUIRE_EXTERNAL_DATABASE=$GCP_REQUIRE_EXTERNAL_DATABASE;CHEMICHECK119_SESSION_COOKIE_SECURE=true;CHEMICHECK119_SESSION_COOKIE_SAME_SITE=Lax;CHEMICHECK119_STAGING_AUTH_ENABLED=$GCP_STAGING_AUTH_ENABLED"
secret_bindings="CHEMICHECK119_SESSION_SECRET=$GCP_SESSION_SECRET:$GCP_SESSION_SECRET_VERSION,CHEMICHECK119_MODEL_API_KEY=$GCP_MODEL_API_KEY_SECRET:$GCP_MODEL_API_KEY_SECRET_VERSION"

if [ "$GCP_REQUIRE_EXTERNAL_DATABASE" = "true" ]; then
  secret_bindings+=",CHEMICHECK119_DATABASE_URL=$GCP_DATABASE_URL_SECRET:$GCP_DATABASE_URL_SECRET_VERSION,CHEMICHECK119_DATABASE_USERNAME=$GCP_DATABASE_USERNAME_SECRET:$GCP_DATABASE_USERNAME_SECRET_VERSION,CHEMICHECK119_DATABASE_PASSWORD=$GCP_DATABASE_PASSWORD_SECRET:$GCP_DATABASE_PASSWORD_SECRET_VERSION"
fi

network_arguments=()
if [ "$GCP_REQUIRE_EXTERNAL_DATABASE" = "true" ]; then
  network_arguments=(
    --network "$GCP_VPC_NETWORK"
    --subnet "$GCP_VPC_SUBNETWORK"
    --vpc-egress "$GCP_VPC_EGRESS"
  )
fi

if [ "$GCP_STAGING_AUTH_ENABLED" = "true" ]; then
  env_vars+=";CHEMICHECK119_STAGING_AUTH_CALLBACK_URL=$GCP_STAGING_AUTH_CALLBACK_URL;CHEMICHECK119_STAGING_AUTH_USER_ID=$GCP_STAGING_AUTH_USER_ID;CHEMICHECK119_STAGING_AUTH_STATION_ID=$GCP_STAGING_AUTH_STATION_ID;CHEMICHECK119_STAGING_AUTH_STATION_DISPLAY_NAME=$GCP_STAGING_AUTH_STATION_DISPLAY_NAME;CHEMICHECK119_STAGING_AUTH_ROLES=RESPONDER;CHEMICHECK119_STAGING_AUTH_INCIDENT_SCOPES=*"
  secret_bindings+=",CHEMICHECK119_STAGING_AUTH_PASSWORD=$GCP_STAGING_AUTH_PASSWORD_SECRET:$GCP_STAGING_AUTH_PASSWORD_SECRET_VERSION"
fi

gcloud run deploy "$GCP_CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --platform managed \
  --image "$IMAGE_DIGEST" \
  --revision-suffix "$revision_suffix" \
  --tag "$candidate_tag" \
  --no-traffic \
  --allow-unauthenticated \
  --ingress all \
  --execution-environment gen2 \
  "${network_arguments[@]}" \
  --service-account "$GCP_RUNTIME_SERVICE_ACCOUNT" \
  --port 8080 \
  --cpu 1 \
  --memory 512Mi \
  --concurrency 10 \
  --timeout 60s \
  --min-instances "$minimum_instances" \
  --max-instances "$maximum_instances" \
  --cpu-boost \
  --deploy-health-check \
  --startup-probe="initialDelaySeconds=5,httpGet.path=/actuator/health/liveness,httpGet.port=8080,timeoutSeconds=3,periodSeconds=5,failureThreshold=12" \
  --liveness-probe="initialDelaySeconds=20,httpGet.path=/actuator/health/liveness,httpGet.port=8080,timeoutSeconds=3,periodSeconds=10,failureThreshold=3" \
  --readiness-probe="httpGet.path=/actuator/health/readiness,httpGet.port=8080,timeoutSeconds=3,periodSeconds=5,failureThreshold=3,successThreshold=1" \
  --update-labels="app=chemicheck119,component=be,environment=staging,git-sha=$RELEASE_GIT_COMMIT" \
  --set-env-vars="^;^$env_vars" \
  --set-secrets="$secret_bindings" \
  --quiet

gcloud run services describe "$GCP_CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format=json >"$candidate_snapshot"

read -r candidate_revision candidate_url service_url < <(
  CANDIDATE_TAG="$candidate_tag" python3 - "$candidate_snapshot" <<'PY'
import json
import os
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
status = payload.get("status") or {}
target_tag = os.environ["CANDIDATE_TAG"]
candidate = next(
    (item for item in status.get("traffic") or [] if item.get("tag") == target_tag),
    {},
)
print(candidate.get("revisionName", ""), candidate.get("url", ""), status.get("url", ""))
PY
)

test -n "$candidate_revision"
test -n "$candidate_url"
test -n "$service_url"
test "$candidate_revision" != "$previous_revision"

deployed_image="$(gcloud run revisions describe "$candidate_revision" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format='value(spec.containers[0].image)')"
if [ "$deployed_image" != "$IMAGE_DIGEST" ]; then
  echo "The deployed revision image does not match the requested digest."
  echo "Requested: $IMAGE_DIGEST"
  echo "Deployed: $deployed_image"
  exit 1
fi

smoke() {
  local base_url="$1"
  local health_file
  health_file="$(mktemp)"
  local http_code="000"

  for _attempt in $(seq 1 40); do
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      "$base_url/actuator/health/readiness" || true)"
    if [ "$http_code" = "200" ] && jq --exit-status '.status == "UP"' "$health_file" >/dev/null; then
      break
    fi
    sleep 2
  done
  if [ "$http_code" != "200" ] || ! jq --exit-status '.status == "UP"' "$health_file" >/dev/null; then
    echo "Cloud Run readiness smoke failed: HTTP $http_code"
    rm -f "$health_file"
    return 1
  fi

  curl --fail --silent --show-error \
    "$base_url/actuator/health/liveness" \
    | jq --exit-status '.status == "UP"' >/dev/null

  curl --fail --silent --show-error \
    "$base_url/actuator/info" \
    | jq --exit-status --arg gitCommit "$RELEASE_GIT_COMMIT" \
      '.release.gitCommit == $gitCommit and .release.environment == "staging"' >/dev/null

  if [ "$GCP_STAGING_AUTH_ENABLED" = "true" ]; then
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      "$base_url/auth/staging/login")"
    if [ "$http_code" != "200" ] || ! grep --quiet 'action="/auth/staging/login"' "$health_file"; then
      echo "Staging login start smoke failed: HTTP $http_code"
      rm -f "$health_file"
      return 1
    fi
  fi

  http_code="$(curl --silent --show-error \
    --output "$health_file" \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data '{"query":"chlorine"}' \
    "$base_url/api/c2guard/v1/substances/discover")"
  if [ "$GCP_PUBLIC_ANALYSIS_ENABLED" = "true" ]; then
    if [ "$http_code" != "200" ] || ! jq --exit-status \
      '.schemaVersion == "chemicheck119-dashboard-bff-v1"' "$health_file" >/dev/null; then
      echo "Public substance discovery smoke failed: HTTP $http_code"
      rm -f "$health_file"
      return 1
    fi

    local request_id="REQ-DEPLOY-${RELEASE_GIT_COMMIT:0:12}"
    local analyze_request
    analyze_request="$(jq --null-input --compact-output \
      --arg incidentId "INC-DEPLOY-${RELEASE_GIT_COMMIT:0:12}" \
      '{incidentId: $incidentId, text: "염소 누출 사고", inputType: "MANUAL_TEXT", evidenceTopK: 3}')"
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      --request POST \
      --header 'Content-Type: application/json' \
      --header "X-Request-Id: $request_id" \
      --data "$analyze_request" \
      "$base_url/api/c2guard/v1/incidents/analyze")"
    if [ "$http_code" != "200" ] || ! jq --exit-status --arg requestId "$request_id" \
      '.schemaVersion == "chemicheck119-dashboard-bff-v1" and .requestId == $requestId' \
      "$health_file" >/dev/null; then
      echo "Public FE to BFF to Model API analysis smoke failed: HTTP $http_code"
      rm -f "$health_file"
      return 1
    fi
  elif [ "$http_code" != "401" ] || ! jq --exit-status \
    '.error.code == "AUTH_REQUIRED"' "$health_file" >/dev/null; then
    echo "Unauthenticated BFF boundary smoke failed: HTTP $http_code"
    rm -f "$health_file"
    return 1
  fi
  rm -f "$health_file"
}

smoke "$candidate_url"

promoted=false
rollback() {
  if [ "$promoted" = true ] && [ -n "$previous_revision" ]; then
    echo "Post-promotion smoke failed; rolling back to $previous_revision."
    gcloud run services update-traffic "$GCP_CLOUD_RUN_SERVICE" \
      --project "$GCP_PROJECT_ID" \
      --region "$GCP_REGION" \
      --to-revisions "$previous_revision=100" \
      --quiet
  fi
}
on_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    rollback
  fi
  cleanup
  exit "$status"
}
trap on_exit EXIT

gcloud run services update-traffic "$GCP_CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --to-revisions "$candidate_revision=100" \
  --quiet
promoted=true

smoke "$service_url"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "revision=$candidate_revision"
    echo "service_url=$service_url"
    echo "candidate_url=$candidate_url"
    echo "previous_revision=$previous_revision"
  } >> "$GITHUB_OUTPUT"
fi

trap cleanup EXIT
echo "Cloud Run blue/green deployment completed: $candidate_revision"
