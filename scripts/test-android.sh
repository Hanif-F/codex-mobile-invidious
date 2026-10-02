#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
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
python3 scripts/fixture-server.py --media-dir "$media_dir" &
fixture_pid=$!
trap 'kill "$fixture_pid" 2>/dev/null || true' EXIT
"$ANDROID_HOME/platform-tools/adb" reverse tcp:18080 tcp:18080
cd android
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain
