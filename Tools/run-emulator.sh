#!/usr/bin/env bash
# Builds the debug app, installs it on the running emulator or device and
# launches it.
#
#   Tools/run-emulator.sh                              # production API
#   API_URL=http://localhost:8787/api Tools/run-emulator.sh   # mock server
#
# The debug build keeps the API address it was launched with until another one
# is given; an empty value goes back to production.
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="ai.sotuvchi.app"

./gradlew --console=plain :app:installDebug

if [[ -n "${API_URL:-}" ]]; then
  # The emulator reaches the Mac's localhost through adb reverse.
  "$ADB" reverse tcp:8787 tcp:8787 >/dev/null
  "$ADB" shell "am start -n $APP_ID/.MainActivity --es apiURL '$API_URL'"
else
  # An empty value clears the saved override. It is quoted for the shell on the device,
  # which would otherwise drop the empty argument.
  "$ADB" shell "am start -n $APP_ID/.MainActivity --es apiURL ''"
fi
