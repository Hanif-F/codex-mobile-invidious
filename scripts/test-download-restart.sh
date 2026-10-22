#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/android-fixture.sh
android_test_environment
app_id=net.wingress.mobivious.debug
runner="$app_id.test/androidx.test.runner.AndroidJUnitRunner"
media_dir="$PWD/.tools/test-media"
python3 scripts/generate-player-fixture.py "$media_dir"
android_start_fixture "$media_dir" "$PWD/.tools/download-fixture.log"
"$adb" install -r android/app/build/outputs/apk/debug/app-debug.apk
"$adb" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p .tools/download-verification
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.DownloadsPersistenceSmokeTest#seedBackgroundTransfers "$runner" | tee .tools/download-verification/restart-seed.txt
rg -q 'OK \(1 test\)' .tools/download-verification/restart-seed.txt
"$adb" shell am force-stop "$app_id"
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.DownloadsPersistenceSmokeTest#restoreTransfersAndOfflineResume "$runner" | tee .tools/download-verification/restart-restored.txt
rg -q 'OK \(1 test\)' .tools/download-verification/restart-restored.txt
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.ProtectedDownloadsPersistenceSmokeTest#seedProtectedTransfers "$runner" | tee .tools/download-verification/protected-restart-seed.txt
rg -q 'OK \(1 test\)' .tools/download-verification/protected-restart-seed.txt
"$adb" shell am force-stop "$app_id"
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.ProtectedDownloadsPersistenceSmokeTest#restoreProtectedTransfersRequireRetryAndRetainOfflinePlayback "$runner" | tee .tools/download-verification/protected-restart-restored.txt
rg -q 'OK \(1 test\)' .tools/download-verification/protected-restart-restored.txt
