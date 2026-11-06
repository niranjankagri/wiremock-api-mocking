#!/usr/bin/env bash
# Runs the tests with coverage, sends the analysis to SonarQube and fails if the quality gate fails.
set -euo pipefail

url="${SONAR_URL:-http://localhost:9000}"
here="$(cd "$(dirname "$0")" && pwd)"

"$here/../mvnw" -B verify sonar:sonar \
  -Dsonar.host.url="$url" \
  -Dsonar.token="$(cat "$here/.token")" \
  -Dsonar.qualitygate.wait=true
