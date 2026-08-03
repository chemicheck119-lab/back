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
  GCP_PUBLIC_INCIDENT_REPLAY_ENABLED
  GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED
  GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED
  GCP_REQUIRE_EXTERNAL_DATABASE
  GCP_STAGING_AUTH_ENABLED
  GCP_PUBLIC_PILOT_ACCESS_ENABLED
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
[[ "$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED" =~ ^(true|false)$ ]]
[[ "$GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED" =~ ^(true|false)$ ]]
[[ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" =~ ^(true|false)$ ]]
if [ "$GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED" = "true" ]; then
  test "$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED" = "true"
fi
[[ "$GCP_REQUIRE_EXTERNAL_DATABASE" =~ ^(true|false)$ ]]
[[ "$GCP_STAGING_AUTH_ENABLED" =~ ^(true|false)$ ]]
[[ "$GCP_PUBLIC_PILOT_ACCESS_ENABLED" =~ ^(true|false)$ ]]
if [ "$GCP_PUBLIC_PILOT_ACCESS_ENABLED" = "true" ]; then
  test "$GCP_STAGING_AUTH_ENABLED" = "true"
  test "$GCP_PUBLIC_ANALYSIS_ENABLED" = "false"
  test "$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED" = "false"
  test "$GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED" = "false"
fi
if [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
  test "$GCP_PUBLIC_PILOT_ACCESS_ENABLED" = "true"
fi
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

incident_replay_enabled="$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED"
synthetic_confirmation_enabled="$GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED"
if [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
  incident_replay_enabled=true
  synthetic_confirmation_enabled=true
fi

env_vars="CHEMICHECK119_RELEASE_GIT_COMMIT=$RELEASE_GIT_COMMIT;CHEMICHECK119_RELEASE_ENVIRONMENT=staging;CHEMICHECK119_MODEL_API_BASE_URL=$GCP_MODEL_API_BASE_URL;CHEMICHECK119_MODEL_API_SCHEMA=chemiguard119-api-v1;CHEMICHECK119_MODEL_API_CONNECT_TIMEOUT_SECONDS=2;CHEMICHECK119_MODEL_API_RESPONSE_TIMEOUT_SECONDS=15;CHEMICHECK119_MODEL_API_MAX_RETRIES=1;CHEMICHECK119_MOVEMENT_ALLOW_DEMO_SIMULATION=false;CHEMICHECK119_CORS_ALLOWED_ORIGINS=$cors_allowed_origins;CHEMICHECK119_PUBLIC_ANALYSIS_ENABLED=$GCP_PUBLIC_ANALYSIS_ENABLED;CHEMICHECK119_INCIDENT_REPLAY_ENABLED=$incident_replay_enabled;CHEMICHECK119_INCIDENT_REPLAY_PUBLIC_ENDPOINT_ENABLED=$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED;CHEMICHECK119_SYNTHETIC_CONFIRMATION_ENABLED=$synthetic_confirmation_enabled;CHEMICHECK119_SYNTHETIC_INCIDENT_TTL=30m;CHEMICHECK119_MAX_ACTIVE_SYNTHETIC_INCIDENTS=100;CHEMICHECK119_INCIDENT_REPLAY_DELAY=1s;CHEMICHECK119_INCIDENT_REPLAY_TIMEOUT=10s;CHEMICHECK119_REQUIRE_EXTERNAL_DATABASE=$GCP_REQUIRE_EXTERNAL_DATABASE;CHEMICHECK119_SESSION_COOKIE_NAME=__session;CHEMICHECK119_SESSION_COOKIE_SECURE=true;CHEMICHECK119_SESSION_COOKIE_SAME_SITE=Lax;CHEMICHECK119_STAGING_AUTH_ENABLED=$GCP_STAGING_AUTH_ENABLED;CHEMICHECK119_STAGING_AUTH_PUBLIC_PILOT_ENABLED=$GCP_PUBLIC_PILOT_ACCESS_ENABLED"
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
    local expected_auth_action="/auth/staging/login"
    if [ "$GCP_PUBLIC_PILOT_ACCESS_ENABLED" = "true" ]; then
      expected_auth_action="/auth/staging/pilot"
    fi

    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      "$base_url/auth/staging/login")"
    if [ "$http_code" != "200" ] || ! grep --quiet "action=\"$expected_auth_action\"" "$health_file"; then
      echo "Staging login start smoke failed: HTTP $http_code"
      rm -f "$health_file"
      return 1
    fi
  fi

  local session_cookie_file=""
  if [ "$GCP_PUBLIC_PILOT_ACCESS_ENABLED" = "true" ]; then
    local pilot_station_id
    session_cookie_file="$(mktemp)"
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      "$base_url/auth/staging/pilot/stations")"
    pilot_station_id="$(jq --raw-output '.regions[0].stations[0].stationId // empty' \
      "$health_file")"
    if [ "$http_code" != "200" ] || [ -z "$pilot_station_id" ] \
        || ! jq --exit-status \
          '.schemaVersion == "chemicheck119-fire-station-catalog-v1" and (.regions | length) == 17' \
          "$health_file" >/dev/null; then
      echo "Public pilot station catalog smoke failed: HTTP $http_code"
      rm -f "$session_cookie_file" "$health_file"
      return 1
    fi
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      --cookie-jar "$session_cookie_file" \
      --request POST \
      --header 'Origin: https://chemicheck119.site' \
      --data-urlencode "stationId=$pilot_station_id" \
      "$base_url/auth/staging/pilot")"
    if [ "$http_code" != "303" ]; then
      echo "Public pilot session issue smoke failed: HTTP $http_code"
      rm -f "$session_cookie_file" "$health_file"
      return 1
    fi
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      --cookie "$session_cookie_file" \
      "$base_url/api/c2guard/v1/session")"
    if [ "$http_code" != "200" ] || ! jq --exit-status \
      --arg stationId "$pilot_station_id" \
      '.stationId == $stationId and .stationLocation.coordinateSource == "NFA_PUBLIC_DATA"
       and (.roles | index("RESPONDER")) != null' \
      "$health_file" >/dev/null; then
      echo "Public pilot signed session smoke failed: HTTP $http_code"
      rm -f "$session_cookie_file" "$health_file"
      return 1
    fi
    if [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" != "true" ]; then
      rm -f "$session_cookie_file"
      session_cookie_file=""
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

  local replay_file
  replay_file="$(mktemp)"
  local replay_cookie_arguments=()
  if [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
    http_code="$(curl --silent --show-error \
      --output "$health_file" \
      --write-out '%{http_code}' \
      --header 'Accept: text/event-stream' \
      "$base_url/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001")"
    if [ "$http_code" != "401" ] || ! jq --exit-status \
      '.error.code == "AUTH_REQUIRED"' "$health_file" >/dev/null; then
      echo "Anonymous incident replay boundary smoke failed: HTTP $http_code"
      rm -f "$session_cookie_file" "$health_file" "$replay_file"
      return 1
    fi
    replay_cookie_arguments=(--cookie "$session_cookie_file")
  fi
  http_code="$(curl --silent --show-error \
    --max-time 15 \
    --output "$replay_file" \
    --write-out '%{http_code}' \
    --header 'Accept: text/event-stream' \
    "${replay_cookie_arguments[@]}" \
    "$base_url/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001")"
  if [ "$GCP_PUBLIC_INCIDENT_REPLAY_ENABLED" = "true" ] \
      || [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
    local replay_data
    replay_data="$(sed -n 's/^data://p' "$replay_file")"
    if [ "$http_code" != "200" ] \
      || ! grep --quiet '^event:incident.accepted$' "$replay_file" \
      || ! jq --exit-status \
        '.sourceType == "SYNTHETIC_DISPATCH_REPLAY"
          and .dataClassification == "PUBLIC_SYNTHETIC"
          and .containsPersonalInformation == false
          and .sourceProvider == "CHEMICHECK119_PUBLIC_REPLAY"' \
        <<<"$replay_data" >/dev/null; then
      echo "Synthetic incident replay smoke failed: HTTP $http_code"
      rm -f "$session_cookie_file" "$health_file" "$replay_file"
      return 1
    fi

    local replay_incident_id
    replay_incident_id="$(jq --raw-output '.incidentId' <<<"$replay_data")"
    if [ "$GCP_PUBLIC_SYNTHETIC_CONFIRMATION_ENABLED" = "true" ] \
        || [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
      local confirmation_file
      confirmation_file="$(mktemp)"
      local confirmation_index=0
      for role_and_cas in "INCIDENT:7681-52-9" "FACILITY:7647-01-0"; do
        local confirmation_role="${role_and_cas%%:*}"
        local expected_cas="${role_and_cas#*:}"
        confirmation_index=$((confirmation_index + 1))
        http_code="$(curl --silent --show-error \
          --output "$confirmation_file" \
          --write-out '%{http_code}' \
          --request POST \
          --header "X-Request-Id: REQ-DEPLOY-SYNTHETIC-$confirmation_role-${RELEASE_GIT_COMMIT:0:8}" \
          "${replay_cookie_arguments[@]}" \
          "$base_url/api/c2guard/v1/intake/replays/$replay_incident_id/confirmations/$confirmation_role")"
        if [ "$http_code" != "200" ] || ! jq --exit-status \
          --arg incidentId "$replay_incident_id" \
          --arg role "$confirmation_role" \
          --arg cas "$expected_cas" \
          --argjson confirmedCount "$confirmation_index" \
          '.schemaVersion == "chemicheck119-synthetic-replay-confirmation-v1"
            and .incidentId == $incidentId
            and .role == $role
            and .casNumber == $cas
            and .dataClassification == "PUBLIC_SYNTHETIC"
            and .confirmationType == "SYNTHETIC_DEMO_CONFIRMATION"
            and .confirmedCount == $confirmedCount
            and .reanalyzeRequired == true' "$confirmation_file" >/dev/null; then
          echo "Synthetic confirmation smoke failed for $confirmation_role: HTTP $http_code"
          rm -f "$session_cookie_file" "$health_file" "$replay_file" "$confirmation_file"
          return 1
        fi
      done

      if [ "$GCP_PUBLIC_ANALYSIS_ENABLED" = "true" ] \
          || [ "$GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED" = "true" ]; then
        local replay_analysis_request
        replay_analysis_request="$(jq --compact-output '{
          incidentId: .incidentId,
          text: .reportText,
          inputType: "DISPATCH_TEXT",
          occurredAt: .occurredAt,
          location: {
            facilityName: .facilityName,
            address: .addressText,
            province: "경기도",
            latitude: .location.latitude,
            longitude: .location.longitude,
            coordinateSource: "DISPATCH_SYSTEM",
            resolvedAt: .receivedAt
          },
          operationsContext: {
            dispatchStationName: .stationDisplayName,
            journeyState: "EN_ROUTE"
          },
          evidenceTopK: 5
        }' <<<"$replay_data")"
        http_code="$(curl --silent --show-error \
          --output "$confirmation_file" \
          --write-out '%{http_code}' \
          --request POST \
          --header 'Content-Type: application/json' \
          --header "X-Request-Id: REQ-DEPLOY-SYNTHETIC-ANALYZE-${RELEASE_GIT_COMMIT:0:8}" \
          --data "$replay_analysis_request" \
          "${replay_cookie_arguments[@]}" \
          "$base_url/api/c2guard/v1/incidents/analyze")"
        if [ "$http_code" != "200" ] || ! jq --exit-status \
          '.state == "SCREENING_COMPLETED"
            and .confirmationGate.incidentConfirmed == true
            and .confirmationGate.facilityConfirmed == true
            and .confirmationGate.allRequiredConfirmed == true
            and .confirmationGate.ruleExecutionAllowed == true
            and .conflictReview.executed == true
            and .conflictReview.result.ruleId == "CAMEO-REACTIVE-GROUP-COMPATIBILITY-MATRIX"
            and .riskDisplayAllowed == true' "$confirmation_file" >/dev/null; then
          echo "Synthetic replay to BFF to AI to CAMEO smoke failed: HTTP $http_code"
          rm -f "$session_cookie_file" "$health_file" "$replay_file" "$confirmation_file"
          return 1
        fi
      fi
      rm -f "$confirmation_file"
    else
      http_code="$(curl --silent --show-error \
        --output "$health_file" \
        --write-out '%{http_code}' \
        --request POST \
        "$base_url/api/c2guard/v1/intake/replays/$replay_incident_id/confirmations/INCIDENT")"
      if [ "$http_code" != "401" ] || ! jq --exit-status \
        '.error.code == "AUTH_REQUIRED"' "$health_file" >/dev/null; then
        echo "Protected synthetic confirmation boundary smoke failed: HTTP $http_code"
        rm -f "$session_cookie_file" "$health_file" "$replay_file"
        return 1
      fi
    fi
  elif [ "$http_code" != "401" ] || ! jq --exit-status \
    '.error.code == "AUTH_REQUIRED"' "$replay_file" >/dev/null; then
    echo "Protected incident replay boundary smoke failed: HTTP $http_code"
    rm -f "$session_cookie_file" "$health_file" "$replay_file"
    return 1
  fi
  rm -f "$replay_file"
  if [ -n "$session_cookie_file" ]; then
    rm -f "$session_cookie_file"
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
