#!/usr/bin/env bash
# Runs the instrumented UI tests (app/src/androidTest) against the mock API on
# the running emulator or device. The mock is started here and stopped when the
# tests end. The app runs with the uiTesting flag, which keeps its session
# apart from a real sign-in.
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PORT=8787

if curl -s -o /dev/null "http://localhost:$PORT/"; then
  echo "Port $PORT is already in use. Stop the running mock (make mock) and try again." >&2
  exit 1
fi

mkdir -p Tools/out
python3 -u Tools/mock-server/mock_server.py --port "$PORT" > Tools/out/mock-server.log 2>&1 &
MOCK_PID=$!
trap 'kill "$MOCK_PID" 2>/dev/null || true; "$ADB" reverse --remove tcp:$PORT >/dev/null 2>&1 || true' EXIT

for _ in $(seq 50); do
  curl -s -o /dev/null "http://localhost:$PORT/" && break
  sleep 0.1
done

# Inside the emulator, localhost:8787 is the Mac's mock.
"$ADB" reverse tcp:$PORT tcp:$PORT >/dev/null

./gradlew --console=plain :app:connectedDebugAndroidTest
