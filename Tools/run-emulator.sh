#!/usr/bin/env bash
# Builds the debug app, installs it on the running emulator or device and
# launches it. It never picks production by itself:
#
#   Tools/run-emulator.sh mock    # the local mock API (started here if it is not running)
#   Tools/run-emulator.sh prod    # the live server, after you type "yes"
#
# The debug build keeps the API address it was launched with until another one
# is given, and shows a banner naming the server. The choice is also written
# to Tools/out/last-api-url, which `make ui-test` uses to put it back after the
# tests (they uninstall the app, and with it the saved address).
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="ai.sotuvchi.app"
PORT=8787
MOCK_URL="http://localhost:$PORT/api"
PROD_URL="https://sotuvchi.ai/api"
MODE="${1:-}"

case "$MODE" in
  mock) API_URL="$MOCK_URL" ;;
  prod)
    echo "This points the app at the LIVE server ($PROD_URL)."
    echo "Sign-ins, approvals, replies and deletions affect real data."
    if [[ "${CONFIRM:-}" != "yes" ]]; then
      read -r -p 'Type "yes" to continue: ' answer
      [[ "$answer" == "yes" ]] || { echo "Cancelled."; exit 1; }
    fi
    API_URL="$PROD_URL"
    ;;
  *)
    echo "Usage: $0 mock|prod" >&2
    exit 2
    ;;
esac

if [[ "$MODE" == "mock" ]] && ! curl -s -o /dev/null "http://localhost:$PORT/"; then
  mkdir -p Tools/out
  nohup python3 -u Tools/mock-server/mock_server.py --port "$PORT" > Tools/out/mock-server.log 2>&1 &
  for _ in $(seq 50); do
    curl -s -o /dev/null "http://localhost:$PORT/" && break
    sleep 0.1
  done
  echo "Started the mock API on port $PORT (stop it with: pkill -f mock_server.py)."
fi

./gradlew --console=plain :app:installDebug

mkdir -p Tools/out
echo "$API_URL" > Tools/out/last-api-url

# A running app has already read its API address; start it fresh so the new one applies.
"$ADB" shell am force-stop "$APP_ID"
if [[ "$MODE" == "mock" ]]; then
  # The emulator reaches the Mac's localhost through adb reverse.
  "$ADB" reverse tcp:$PORT tcp:$PORT >/dev/null
fi
"$ADB" shell "am start -n $APP_ID/.MainActivity --es apiURL '$API_URL'"
