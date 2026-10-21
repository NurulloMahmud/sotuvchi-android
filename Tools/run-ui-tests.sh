#!/usr/bin/env bash
# Runs the instrumented UI tests (app/src/androidTest) against the mock API on
# the running emulator or device. The mock is started here (or reused when
# `make run` already started it). The app runs with the uiTesting flag, which
# keeps its session apart from a real sign-in.
#
#   Tools/run-ui-tests.sh          # the debug build
#   Tools/run-ui-tests.sh qa       # the shrunk (R8) QA build, as it would ship
#
# The tests uninstall the app, and with it the API address saved by
# `make run`. Afterwards the debug app is installed again with the address it
# had (Tools/out/last-api-url), so nothing silently falls back to production.
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="ai.sotuvchi.app"
PORT=8787
BUILD="${1:-debug}"

mkdir -p Tools/out
LAST_URL=""
[[ -f Tools/out/last-api-url ]] && LAST_URL="$(cat Tools/out/last-api-url)"

start_mock() {
  nohup python3 -u Tools/mock-server/mock_server.py --port "$PORT" > Tools/out/mock-server.log 2>&1 &
  MOCK_PID=$!
  for _ in $(seq 50); do
    curl -s -o /dev/null "http://localhost:$PORT/" && break
    sleep 0.1
  done
}

MOCK_PID=""
if ! curl -s -o /dev/null "http://localhost:$PORT/"; then
  start_mock
fi

restore() {
  [[ -n "$MOCK_PID" ]] && kill "$MOCK_PID" 2>/dev/null || true
  if [[ -z "$LAST_URL" ]]; then
    "$ADB" reverse --remove tcp:$PORT >/dev/null 2>&1 || true
    return 0
  fi
  echo "Restoring the debug app with $LAST_URL"
  if [[ "$LAST_URL" == http://localhost:* ]]; then
    # Leave a mock running for the restored app, as `make run` does.
    sleep 0.5
    curl -s -o /dev/null "http://localhost:$PORT/" || { MOCK_PID=""; start_mock; MOCK_PID=""; }
    "$ADB" reverse tcp:$PORT tcp:$PORT >/dev/null || true
  fi
  ./gradlew --console=plain :app:installDebug >/dev/null || return 0
  "$ADB" shell am force-stop "$APP_ID" || true
  "$ADB" shell "am start -n $APP_ID/.MainActivity --es apiURL '$LAST_URL'" >/dev/null || true
}
trap restore EXIT

# Inside the emulator, localhost:8787 is the Mac's mock.
"$ADB" reverse tcp:$PORT tcp:$PORT >/dev/null

if [[ "$BUILD" == "qa" ]]; then
  ./gradlew --console=plain -PtestBuild=qa :app:connectedQaAndroidTest
  RESULTS=app/build/outputs/androidTest-results/connected/qa
else
  ./gradlew --console=plain :app:connectedDebugAndroidTest
  RESULTS=app/build/outputs/androidTest-results/connected/debug
fi

# A shrunk test APK that lost its tests "passes" with nothing run; make that a failure.
python3 - "$RESULTS" <<'PY'
import glob, re, sys
ran = sum(int(re.search(r'tests="(\d+)"', open(f).read()).group(1)) for f in glob.glob(sys.argv[1] + "/**/TEST-*.xml", recursive=True))
print(f"UI tests run: {ran}")
sys.exit(0 if ran > 0 else "No UI tests ran.")
PY
