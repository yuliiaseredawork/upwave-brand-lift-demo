#!/usr/bin/env bash
#
# End-to-end demo of the brand lift flow. Assumes the app is already running locally:
#
#   docker compose up -d
#   ./gradlew bootRun --args='--spring.profiles.active=local'
#
# Then, from the project root:
#
#   chmod +x scripts/demo.sh
#   ./scripts/demo.sh
#
# It creates a campaign, ingests exposure events (including a duplicate to show
# idempotency), ingests exposed and control survey responses, recalculates the lift
# summary, then fetches the summary and the generated insight.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

post() { curl -sS -X POST "$BASE_URL$1" -H 'Content-Type: application/json' -d "$2"; }
get()  { curl -sS "$BASE_URL$1"; }

# Pull the first "id":"..." out of a JSON response without requiring jq.
extract_id() { grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"\([^"]*\)"/\1/'; }

echo "== 1. Create campaign =="
CAMPAIGN_JSON=$(post /api/campaigns '{
  "name": "Spring Awareness Push",
  "brandName": "Acme",
  "startsAt": "2025-01-01T00:00:00Z",
  "endsAt": "2025-01-15T00:00:00Z"
}')
echo "$CAMPAIGN_JSON"
CAMPAIGN_ID=$(echo "$CAMPAIGN_JSON" | extract_id)
echo "campaignId=$CAMPAIGN_ID"
echo

echo "== 2. Ingest exposure events (3 distinct, then a duplicate of evt-0001) =="
for n in 1 2 3; do
  post /api/exposure-events "{
    \"campaignId\": \"$CAMPAIGN_ID\",
    \"userIdHash\": \"u-000$n\",
    \"channel\": \"CTV\",
    \"creativeId\": \"creative-1\",
    \"placementId\": \"placement-1\",
    \"impressionTimestamp\": \"2025-01-05T12:00:00Z\",
    \"idempotencyKey\": \"evt-000$n\"
  }"
  echo
done
echo "-- resend evt-0001 (should report duplicate=true, no new row) --"
post /api/exposure-events "{
  \"campaignId\": \"$CAMPAIGN_ID\",
  \"userIdHash\": \"u-0001\",
  \"channel\": \"CTV\",
  \"creativeId\": \"creative-1\",
  \"placementId\": \"placement-1\",
  \"impressionTimestamp\": \"2025-01-05T12:00:00Z\",
  \"idempotencyKey\": \"evt-0001\"
}"
echo; echo

echo "== 3. Ingest survey responses (2 exposed, 2 control) =="
# exposed: awareness 70/65, consideration 40/50, purchase intent 25/15
# control: awareness 60/58, consideration 30/36, purchase intent 10/14
ingest_survey() { # exposed userHash awareness consideration purchaseIntent
  post /api/survey-responses "{
    \"campaignId\": \"$CAMPAIGN_ID\",
    \"userIdHash\": \"$2\",
    \"exposed\": $1,
    \"awarenessScore\": $3,
    \"considerationScore\": $4,
    \"purchaseIntentScore\": $5,
    \"responseTimestamp\": \"2025-01-06T09:00:00Z\",
    \"late\": false
  }"
  echo
}
ingest_survey true  u-0001 70 40 25
ingest_survey true  u-0002 65 50 15
ingest_survey false u-9001 60 30 10
ingest_survey false u-9002 58 36 14
echo

echo "== 4. Recalculate lift summary =="
post "/api/campaigns/$CAMPAIGN_ID/lift-summary/recalculate" ''
echo; echo

echo "== 5. Get lift summary =="
get "/api/campaigns/$CAMPAIGN_ID/lift-summary"
echo; echo

echo "== 6. Get insights =="
get "/api/campaigns/$CAMPAIGN_ID/insights"
echo
