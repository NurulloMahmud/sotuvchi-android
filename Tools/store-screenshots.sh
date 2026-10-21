#!/usr/bin/env bash
# Takes the Play Store phone screenshots (en, ru, uz) from the mock API's sample data on the
# running emulator, into store/graphics/screenshots/<language>/.
#
#   Tools/store-screenshots.sh
#
# The emulator is put in demo mode (clock 12:00, full battery, wifi only) and its screen is set to
# 1080 x 2160, the tallest shape Play accepts (2:1). Both are undone afterwards. The pictures
# are written without an alpha channel, as Play requires.
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="ai.sotuvchi.app"
PORT=8787
OUT="store/graphics/screenshots"
DEVICE_DIR="/sdcard/Android/data/$APP_ID/files/store-screens"

demo() { "$ADB" shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }

MOCK_PID=""
if ! curl -s -o /dev/null "http://localhost:$PORT/"; then
  mkdir -p Tools/out
  python3 -u Tools/mock-server/mock_server.py --port "$PORT" > Tools/out/mock-server.log 2>&1 &
  MOCK_PID=$!
  for _ in $(seq 50); do curl -s -o /dev/null "http://localhost:$PORT/" && break; sleep 0.1; done
fi
"$ADB" reverse tcp:$PORT tcp:$PORT >/dev/null

cleanup() {
  demo -e command exit || true
  "$ADB" shell wm size reset || true
  [[ -n "$MOCK_PID" ]] && kill "$MOCK_PID" 2>/dev/null || true
}
trap cleanup EXIT

"$ADB" shell settings put global sysui_demo_allowed 1
demo -e command enter
demo -e command clock -e hhmm 1200
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4 -e fully true
demo -e command network -e mobile hide
demo -e command notifications -e visible false
"$ADB" shell wm size 1080x2160

./gradlew --console=plain :app:assembleDebug :app:assembleDebugAndroidTest >/dev/null
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

rm -rf "$OUT"
mkdir -p "$OUT"
for lang in en ru uz; do
  echo "Screenshots: $lang"
  "$ADB" shell rm -rf "$DEVICE_DIR/$lang"
  "$ADB" shell am instrument -w -e class ai.sotuvchi.app.StoreScreenshots -e screenshotLanguage "$lang" \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee Tools/out/screenshots-$lang.log | grep -E "OK \(|FAILURES|Error" || true
  "$ADB" pull "$DEVICE_DIR/$lang" "$OUT/" >/dev/null
done

echo "$(find "$OUT" -name '*.png' | wc -l | tr -d ' ') screenshots in $OUT"
