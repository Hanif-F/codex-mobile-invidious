#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/android-fixture.sh
android_test_environment
# This runner uses an already running device; it never starts an emulator.
media_dir="$PWD/.tools/test-media"
python3 scripts/generate-player-fixture.py "$media_dir"
android_start_fixture "$media_dir" "$PWD/.tools/android-fixture.log"
cd android
# Keep the preview's app identity and Android Keystore keys across test runs.
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "$@"
