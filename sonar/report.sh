#!/usr/bin/env bash
# Prints the analysis result as Markdown: the gate's conditions, then every open issue.
set -euo pipefail

url="${SONAR_URL:-http://localhost:9000}"
here="$(cd "$(dirname "$0")" && pwd)"
key="com.niranjankagri:wiremock-api-mocking"

get() { curl -sf -u "$(cat "$here/.token"):" "$url/api/$1"; }

get "qualitygates/project_status?projectKey=$key" | jq -r '
  "Quality gate: **\(.projectStatus.status)**\n",
  "| Condition | Value | Limit | Result |", "|---|---:|---:|---|",
  (.projectStatus.conditions[] | "| \(.metricKey) | \(.actualValue) | \(.errorThreshold) | \(.status) |")'

echo
get "issues/search?components=$key&issueStatuses=OPEN,CONFIRMED&ps=100" | jq -r '
  "Open issues: **\(.total)**\n",
  (if .total > 0 then
    "| File | Line | Rule | Message |", "|---|---:|---|---|",
    (.issues[] | "| \(.component | split("/") | last) | \(.line // "") | \(.rule) | \(.message) |")
  else empty end)'
