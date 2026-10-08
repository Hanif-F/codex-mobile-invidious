#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
[[ "$("$adb" get-state)" == device ]]
app_id=net.wingress.mobivious.debug
runner="$app_id.test/androidx.test.runner.AndroidJUnitRunner"
media_dir="$PWD/.tools/test-media"
if python3 - <<'PY'
import socket
with socket.socket() as connection:
    raise SystemExit(0 if connection.connect_ex(('127.0.0.1', 18080)) == 0 else 1)
PY
then
  echo 'Port 18080 is already in use. Stop the prior fixture first.' >&2
  exit 1
fi
python3 scripts/fixture-server.py --media-dir "$media_dir" &
fixture_pid=$!
owned_reverse=false
cleanup() {
  kill "$fixture_pid" 2>/dev/null || true
  wait "$fixture_pid" 2>/dev/null || true
  if "$owned_reverse"; then "$adb" reverse --remove tcp:18080 2>/dev/null || true; fi
}
trap cleanup EXIT
python3 - <<'PY'
import time, urllib.request
for attempt in range(50):
    try:
        urllib.request.urlopen('http://127.0.0.1:18080/test/state', timeout=1).close()
        break
    except OSError:
        time.sleep(.1)
else:
    raise SystemExit('Fixture did not start')
PY
kill -0 "$fixture_pid"
reverse_mappings="$("$adb" reverse --list)"
if awk '$2 == "tcp:18080" && $3 != "tcp:18080" {found=1} END {exit !found}' <<< "$reverse_mappings"; then
  echo 'Device port 18080 forwards elsewhere; preserve or remove that mapping first.' >&2
  exit 1
fi
if ! awk '$2 == "tcp:18080" && $3 == "tcp:18080" {found=1} END {exit !found}' <<< "$reverse_mappings"; then
  "$adb" reverse tcp:18080 tcp:18080
  owned_reverse=true
fi
"$adb" install -r android/app/build/outputs/apk/debug/app-debug.apk
"$adb" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p .tools/download-verification
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.DownloadsPersistenceSmokeTest#seedBackgroundTransfers "$runner" | tee .tools/download-verification/restart-seed.txt
rg -q 'OK \(1 test\)' .tools/download-verification/restart-seed.txt
"$adb" shell am force-stop "$app_id"
"$adb" shell am instrument -w -e downloadRestart true -e class net.wingress.mobivious.DownloadsPersistenceSmokeTest#restoreTransfersAndOfflineResume "$runner" | tee .tools/download-verification/restart-restored.txt
rg -q 'OK \(1 test\)' .tools/download-verification/restart-restored.txt
