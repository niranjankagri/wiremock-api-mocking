#!/usr/bin/env bash
# Prepares a new SonarQube server: waits for it, replaces the default password, creates this
# project's quality gate and writes a token to sonar/.token (ignored by git).
set -euo pipefail

url="${SONAR_URL:-http://localhost:9000}"
here="$(cd "$(dirname "$0")" && pwd)"

echo "Waiting for SonarQube at $url"
for _ in $(seq 1 90); do
  status=$(curl -s "$url/api/system/status" | grep -o '"status":"[A-Z_]*"' || true)
  [ "$status" = '"status":"UP"' ] && break
  sleep 2
done
[ "$status" = '"status":"UP"' ] || { echo "SonarQube did not start: $status"; exit 1; }

# The server lives only for this run, so its password is random; sonar/.password (ignored by git) is
# there for signing in to the web page
password="Sonar-$(openssl rand -hex 12)-1a"
printf '%s' "$password" > "$here/.password"
curl -sf -u admin:admin -X POST "$url/api/users/change_password" \
  --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" \
  --data-urlencode "password=$password"

api() {
  local path="$1"; shift
  curl -sf -u "admin:$password" -X POST "$url/api/$path" "$@"
}

# The built-in gate judges new code only. A new server sees no new code, so it would always
# pass: this gate judges all of the code instead.
gate="Whole project"
api qualitygates/create --data-urlencode "name=$gate" > /dev/null
condition() {
  api qualitygates/create_condition --data-urlencode "gateName=$gate" \
    -d "metric=$1" -d "op=$2" -d "error=$3" > /dev/null
}
condition software_quality_reliability_rating GT 1      # no reliability issue at all: rating A
condition software_quality_security_rating GT 1         # no security issue at all: rating A
condition software_quality_maintainability_rating GT 1  # rating A
condition coverage LT 90
condition duplicated_lines_density GT 3
api qualitygates/set_as_default --data-urlencode "name=$gate"

# A user token, not an analysis token: sonar/report.sh also reads the results with it
api user_tokens/generate -d "name=scan" -d "type=USER_TOKEN" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4 > "$here/.token"
echo "Ready: quality gate \"$gate\" is the default"
