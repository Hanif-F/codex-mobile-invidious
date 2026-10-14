#!/usr/bin/env bash
# Shared setup for Android fixture runners. Source from the repository root.

android_test_environment() {
  export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
  adb="$ANDROID_HOME/platform-tools/adb"
  if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    mapfile -t devices < <("$adb" devices | awk 'NR > 1 && $2 == "device" {print $1}')
    if [[ ${#devices[@]} -ne 1 ]]; then
      echo 'Connect one running device, or set ANDROID_SERIAL to the device to test.' >&2
      return 1
    fi
    export ANDROID_SERIAL="${devices[0]}"
  fi
  [[ "$("$adb" get-state)" == device ]]
}

android_fixture_cleanup() {
  if [[ -n "${fixture_pid:-}" ]]; then
    kill "$fixture_pid" 2>/dev/null || true
    wait "$fixture_pid" 2>/dev/null || true
  fi
  if "${owned_reverse:-false}"; then
    # Preserve a mapping replaced by another process while tests were running.
    if "$adb" reverse --list 2>/dev/null | awk '$2 == "tcp:18080" && $3 == "tcp:18080" {found=1} END {exit !found}'; then
      "$adb" reverse --remove tcp:18080 2>/dev/null || true
    fi
  fi
}

android_start_fixture() {
  local media_dir="${1:?Missing media directory}" log="${2:?Missing fixture log}"
  shift 2
  if python3 - <<'PY'
import socket
with socket.socket() as connection:
    raise SystemExit(0 if connection.connect_ex(('127.0.0.1', 18080)) == 0 else 1)
PY
  then
    echo 'Port 18080 is already in use. Stop the previous fixture before running these tests.' >&2
    return 1
  fi
  local mappings
  mappings="$("$adb" reverse --list)"
  if awk '$2 == "tcp:18080" && $3 != "tcp:18080" {found=1} END {exit !found}' <<< "$mappings"; then
    echo 'Device port 18080 already forwards to another port; preserve or remove that mapping before testing.' >&2
    return 1
  fi
  fixture_pid='' owned_reverse=false
  trap android_fixture_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  python3 scripts/fixture-server.py --media-dir "$media_dir" "$@" > "$log" 2>&1 &
  fixture_pid=$!
  python3 - <<'PY'
import time
from urllib.request import urlopen
for attempt in range(50):
    try:
        with urlopen('http://127.0.0.1:18080/test/state', timeout=1) as response:
            if response.status == 200:
                break
    except OSError:
        time.sleep(.1)
else:
    raise SystemExit('Fixture server did not become ready.')
PY
  kill -0 "$fixture_pid"
  if ! awk '$2 == "tcp:18080" && $3 == "tcp:18080" {found=1} END {exit !found}' <<< "$mappings"; then
    "$adb" reverse tcp:18080 tcp:18080
    owned_reverse=true
  fi
}
