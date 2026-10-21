#!/usr/bin/env bash
# Builds the QA app and its test APK, installs both and runs the instrumentation by hand,
# printing the crash if the shrunk app lacks a class the tests need. For diagnosing keep rules.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
./gradlew --console=plain -PtestBuild=qa :app:assembleQa :app:assembleQaAndroidTest >/dev/null
"$ADB" install -r -t app/build/outputs/apk/qa/app-qa.apk >/dev/null
"$ADB" install -r -t app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk >/dev/null
"$ADB" logcat -c
"$ADB" shell am instrument -w ai.sotuvchi.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tail -15
"$ADB" logcat -d -b crash | grep -E "Exception|Error|Caused" | head -8 || true
