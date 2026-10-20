# Android Studio's bundled JDK builds the project; the SDK path comes from local.properties.
export JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ADB ?= $(ANDROID_HOME)/platform-tools/adb
GRADLE = ./gradlew --console=plain
APP_ID = ai.sotuvchi.app
MOCK_URL = http://localhost:8787/api

.PHONY: build test ui-test run run-mock mock l10n-check bundle clean

build:
	$(GRADLE) :app:assembleDebug

# Translation check, then the JVM unit tests. Needs no device or server.
test: l10n-check
	$(GRADLE) :app:testDebugUnitTest

# End-to-end UI tests against the mock API, on the running emulator or device.
# The mock is started here and stopped afterwards.
ui-test:
	ADB='$(ADB)' Tools/run-ui-tests.sh

# Builds, installs and launches against production.
run:
	ADB='$(ADB)' Tools/run-emulator.sh

# Same, against the local mock API (start it with `make mock`).
run-mock:
	ADB='$(ADB)' API_URL=$(MOCK_URL) Tools/run-emulator.sh

mock:
	python3 Tools/mock-server/mock_server.py

l10n-check:
	python3 Tools/l10n_keys.py --check

# Release app bundle for Google Play. Sign it with your upload key (see README).
bundle:
	$(GRADLE) :app:bundleRelease

clean:
	$(GRADLE) clean
	rm -rf Tools/out
