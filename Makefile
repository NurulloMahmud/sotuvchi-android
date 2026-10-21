# Android Studio's bundled JDK builds the project; the SDK path comes from local.properties.
export JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ADB ?= $(ANDROID_HOME)/platform-tools/adb
GRADLE = ./gradlew --console=plain
APP_ID = ai.sotuvchi.app
MOCK_URL = http://localhost:8787/api

.PHONY: build test ui-test ui-test-qa run run-prod run-mock mock l10n-check bundle clean

build:
	$(GRADLE) :app:assembleDebug

# Translation check, then the JVM unit tests. Needs no device or server.
test: l10n-check
	$(GRADLE) :app:testDebugUnitTest

# End-to-end UI tests against the mock API, on the running emulator or device.
# The mock is started here and stopped afterwards.
# The previous `make run` address is put back afterwards.
ui-test:
	ADB='$(ADB)' Tools/run-ui-tests.sh

# The same suite against the shrunk (R8) QA build, the code that ships.
ui-test-qa:
	ADB='$(ADB)' Tools/run-ui-tests.sh qa

# Builds, installs and launches against the local mock API (started if needed).
run: run-mock

run-mock:
	ADB='$(ADB)' Tools/run-emulator.sh mock

# The live server, after typing "yes". Never the default.
run-prod:
	ADB='$(ADB)' Tools/run-emulator.sh prod

mock:
	python3 Tools/mock-server/mock_server.py

l10n-check:
	python3 Tools/l10n_keys.py --check

# Signed release app bundle for Google Play (needs keystore.properties, see README):
# app/build/outputs/bundle/release/app-release.aab, checked after the build.
bundle:
	$(GRADLE) :app:bundleRelease
	python3 Tools/check-bundle.py

clean:
	$(GRADLE) clean
	rm -rf Tools/out
