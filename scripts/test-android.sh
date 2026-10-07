#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="$ANDROID_HOME/platform-tools/adb"
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  mapfile -t devices < <("$adb" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo 'Connect one running device, or set ANDROID_SERIAL to the device to test.' >&2
    exit 1
  fi
  export ANDROID_SERIAL="${devices[0]}"
fi
[[ "$("$adb" get-state)" == device ]]
# This runner uses an already running device; it never starts an emulator.
if python3 - <<'PY'
import socket
with socket.socket() as connection:
    raise SystemExit(0 if connection.connect_ex(('127.0.0.1', 18080)) == 0 else 1)
PY
then
  echo 'Port 18080 is already in use. Stop the previous fixture before running these tests.' >&2
  exit 1
fi
media_dir="$PWD/.tools/test-media"
mkdir -p "$media_dir"
if [[ ! -f "$media_dir/master.m3u8" ]]; then
  ffmpeg -y -hide_banner -loglevel error -f lavfi -i testsrc2=size=640x360:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=48000 -t 120 -c:v libx264 \
    -preset ultrafast -g 48 -b:v 700k -c:a aac -movflags +faststart "$media_dir/fixture.mp4"
  ffmpeg -y -hide_banner -loglevel error -i "$media_dir/fixture.mp4" -c copy \
    -f dash -seg_duration 4 -hls_playlist 1 "$media_dir/dash.mpd"
  ffmpeg -y -hide_banner -loglevel error -i "$media_dir/fixture.mp4" -frames:v 1 "$media_dir/thumbnail.jpg"
  printf 'WEBVTT\n\n00:00.000 --> 00:30.000\nMobivious caption test\n' > "$media_dir/captions.vtt"
fi
if [[ ! -f "$media_dir/clip-storyboard.jpg" ]]; then
  ffmpeg -y -hide_banner -loglevel error -i "$media_dir/fixture.mp4" \
    -vf 'fps=1/20,scale=160:90,tile=6x1' -frames:v 1 "$media_dir/clip-storyboard.jpg"
fi
python3 scripts/generate-player-fixture.py "$media_dir"
python3 scripts/fixture-server.py --media-dir "$media_dir" &
fixture_pid=$!
owned_reverse=false
cleanup() {
  kill "$fixture_pid" 2>/dev/null || true
  wait "$fixture_pid" 2>/dev/null || true
  if "$owned_reverse"; then "$adb" reverse --remove tcp:18080 2>/dev/null || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
python3 - <<'PY'
import time, urllib.request
for attempt in range(50):
    try:
        with urllib.request.urlopen('http://127.0.0.1:18080/test/state', timeout=1) as response:
            if response.status == 200: break
    except OSError:
        time.sleep(.1)
else:
    raise SystemExit('Fixture server did not become ready.')
PY
kill -0 "$fixture_pid"
reverse_mappings="$("$adb" reverse --list)"
if awk '$2 == "tcp:18080" && $3 != "tcp:18080" {found=1} END {exit !found}' <<< "$reverse_mappings"; then
  echo 'Device port 18080 already forwards to another port; preserve or remove that mapping before testing.' >&2
  exit 1
fi
if ! awk '$2 == "tcp:18080" && $3 == "tcp:18080" {found=1} END {exit !found}' <<< "$reverse_mappings"; then
  "$adb" reverse tcp:18080 tcp:18080
  owned_reverse=true
fi
cd android
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain "$@"
