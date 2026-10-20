#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
Usage: scripts/test-android.sh [--device-only] [Gradle options]

Default: run JVM tests, connected device tests and debug lint.
--device-only: run only connected tests and their required APK builds. Requires:
  -Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.Class[#method]
Multiple classes/methods can be comma-separated in the same property.
--help, -h: show this help without requiring a device or generating media.

Uses an already running device selected by ANDROID_SERIAL or the only device.
Other arguments, including --offline, are passed to Gradle unchanged.
EOF
}

device_only=false
selector=''
gradle_args=()
for argument in "$@"; do
  case "$argument" in
    --help|-h) usage; exit 0 ;;
    --device-only) device_only=true ;;
    -Pandroid.testInstrumentationRunnerArguments.class=*)
      selector="${argument#*=}"
      gradle_args+=("$argument") ;;
    *) gradle_args+=("$argument") ;;
  esac
done
if "$device_only" && [[ -z "${selector//[[:space:]]/}" ]]; then
  echo '--device-only requires a nonempty -Pandroid.testInstrumentationRunnerArguments.class=Class[#method] selector.' >&2
  exit 2
fi

source scripts/lib/android-fixture.sh
android_test_environment
# This runner uses an already running device; it never starts an emulator.
media_dir="$PWD/.tools/test-media"
python3 scripts/generate-player-fixture.py "$media_dir"
android_start_fixture "$media_dir" "$PWD/.tools/android-fixture.log"
cd android
tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug)
if "$device_only"; then tasks=(:app:connectedDebugAndroidTest); fi
# Keep the preview's app identity and Android Keystore keys across test runs.
./gradlew "${tasks[@]}" --console=plain \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "${gradle_args[@]}"
