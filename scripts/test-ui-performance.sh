#!/usr/bin/env bash
# Disposable release-like UI measurements. No release package mutations.
set -euo pipefail
cd "$(dirname "$0")/.."
journeys=(popular popularCompact popularTextOnly popularColdArt searchPagination trending trendingChanges subscriptions channels search mixedSearch history playlists settings settingsSaving downloads watch watchAmbient navigation)
label='' journey=all adapter=redesigned record=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --list) printf '%s\n' "${journeys[@]}"; exit 0 ;;
    --record) record=true; shift ;;
    --label) label="${2:?Missing label}"; shift 2 ;;
    --journey) journey="${2:?Missing journey}"; shift 2 ;;
    --adapter) adapter="${2:?Missing adapter}"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ "$label" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Provide --label with letters, numbers, underscores or hyphens.' >&2; exit 2; }
[[ "$adapter" == redesigned || "$adapter" == current-glass || "$adapter" == pre-glass ]] || exit 2
if [[ "$journey" != all ]] && ! printf '%s\n' "${journeys[@]}" | rg -qx "$journey"; then
  echo "Unknown journey: $journey" >&2; exit 2
fi
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="$ANDROID_HOME/platform-tools/adb"
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  mapfile -t devices < <("$adb" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  [[ ${#devices[@]} -eq 1 ]] || { echo 'Select one running device with ANDROID_SERIAL.' >&2; exit 1; }
  export ANDROID_SERIAL="${devices[0]}"
fi
[[ "$("$adb" get-state)" == device ]]
benchmark_args=()
if "$record"; then benchmark_args+=("-Pandroid.testInstrumentationRunnerArguments.recordVideo=true"); fi
if [[ "$("$adb" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]; then
  benchmark_args+=("-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR")
  echo 'Emulator diagnostics only; task acceptance uses emulator verification by user instruction.' >&2
fi
if python3 - <<'PY'
import socket
with socket.socket() as s:
    raise SystemExit(0 if s.connect_ex(('127.0.0.1',18080)) == 0 else 1)
PY
then echo 'Port 18080 is occupied; stop its owner before measuring.' >&2; exit 1; fi
media_dir="$PWD/.tools/test-media"
mkdir -p "$media_dir"
if [[ ! -f "$media_dir/fixture.mp4" ]]; then
  ffmpeg -y -hide_banner -loglevel error -f lavfi -i testsrc2=size=640x360:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=48000 -t 120 -c:v libx264 \
    -preset ultrafast -g 48 -b:v 700k -c:a aac -movflags +faststart "$media_dir/fixture.mp4"
fi
if [[ ! -f "$media_dir/thumbnail.jpg" ]]; then
  ffmpeg -y -hide_banner -loglevel error -i "$media_dir/fixture.mp4" -frames:v 1 "$media_dir/thumbnail.jpg"
fi
python3 scripts/generate-player-fixture.py "$media_dir"
run="$(date -u +%Y%m%dT%H%M%SZ)"
recording_run="${run,,}-$RANDOM"
benchmark_args+=("-Pandroid.testInstrumentationRunnerArguments.evidenceRun=$recording_run")
if "$record"; then benchmark_args+=("-Pandroid.testInstrumentationRunnerArguments.recordingRun=$recording_run"); fi
device="${ANDROID_SERIAL//[^a-zA-Z0-9_.-]/_}"
output="$PWD/.tools/ui-ux-overhaul/performance/$label/$device/$run"
mkdir -p "$output"
python3 - "$media_dir" "$output/fixture-media.sha256.json" <<'PY_MEDIA'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1])
manifest={str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest()
    for path in sorted(root.rglob('*')) if path.is_file()}
Path(sys.argv[2]).write_text(json.dumps(manifest,indent=2)+'\n')
PY_MEDIA
ffmpeg -version > "$output/ffmpeg.txt"
python3 scripts/fixture-server.py --media-dir "$media_dir" --performance > "$output/fixture.log" 2>&1 &
fixture_pid=$!; owned_reverse=false
cleanup() {
  kill "$fixture_pid" 2>/dev/null || true; wait "$fixture_pid" 2>/dev/null || true
  if "$owned_reverse"; then "$adb" reverse --remove tcp:18080 2>/dev/null || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
python3 - <<'PY'
import time,urllib.request
for attempt in range(50):
    try:
        with urllib.request.urlopen('http://127.0.0.1:18080/test/state',timeout=1) as r:
            if r.status == 200: break
    except OSError: time.sleep(.1)
else: raise SystemExit('Fixture did not become ready')
PY
kill -0 "$fixture_pid"
mappings="$("$adb" reverse --list)"
if awk '$2 == "tcp:18080" && $3 != "tcp:18080" {f=1} END {exit !f}' <<< "$mappings"; then
  echo 'A conflicting reverse mapping already owns device port 18080.' >&2; exit 1
fi
if ! awk '$2 == "tcp:18080" && $3 == "tcp:18080" {f=1} END {exit !f}' <<< "$mappings"; then
  "$adb" reverse tcp:18080 tcp:18080; owned_reverse=true
fi
git rev-parse HEAD > "$output/source-commit.txt"
if [[ -f benchmark-base-source.txt ]]; then cp benchmark-base-source.txt "$output/source-commit.txt"; fi
python3 - "$output" <<'PY_HASH'
from pathlib import Path
import difflib,hashlib,io,json,subprocess,sys,tarfile,zipfile
output = Path(sys.argv[1])
def source(path):
    return not any(x in path.parts for x in ('build','.gradle','.idea','.kotlin','.cxx','__pycache__')) and path.name not in ('local.properties','signing.properties') and (path.suffix in ('.kt','.xml','.kts','.properties','.pro','.sh','.py','.jar','.png','.webp','.jpg','.json','.toml') or path.name in ('gradlew','gradlew.bat'))
files = {}; current = {}
with zipfile.ZipFile(Path(sys.argv[1],'source.zip'),'w',zipfile.ZIP_DEFLATED) as archive:
    for base in ('android','scripts'):
        for path in sorted(Path(base).rglob('*')):
            if not path.is_file() or not source(path): continue
            data = path.read_bytes()
            current[str(path)] = data
            files[str(path)] = hashlib.sha256(data).hexdigest()
            archive.writestr(str(path),data)
Path(sys.argv[1],'source-files.sha256.json').write_text(json.dumps(files,indent=2)+'\n')
commit = (output/'source-commit.txt').read_text().strip()
before = {}
with tarfile.open(fileobj=io.BytesIO(subprocess.check_output(['git','archive',commit,'android','scripts']))) as baseline:
    for member in baseline:
        if member.isfile() and source(Path(member.name)):
            before[member.name] = baseline.extractfile(member).read()
with (output/'source.diff').open('w') as diff:
    for name in sorted(before.keys() | current.keys()):
        old,new=before.get(name,b''),current.get(name,b'')
        if old==new: continue
        try:
            diff.writelines(difflib.unified_diff(old.decode().splitlines(keepends=True),new.decode().splitlines(keepends=True),fromfile='a/'+name,tofile='b/'+name))
        except UnicodeDecodeError:
            diff.write(f'Binary change {name}: {hashlib.sha256(old).hexdigest()} -> {hashlib.sha256(new).hexdigest()}\n')
PY_HASH
sha256sum "$output/source.diff" scripts/fixture-server.py android/macrobenchmark/src/main/java/net/wingress/mobivious/benchmarks/UiScrollBenchmark.kt > "$output/source-hashes.txt"
ui_mode=timing; ui_compilation=Full; ui_iterations=10
if "$record"; then ui_mode=normal-speed-recording; ui_compilation=none; ui_iterations=0; fi
printf '%s\n' "label=$label" "journey=$journey" "adapter=$adapter" "fixture=ui-overhaul-v2" "mode=$ui_mode" "compilation=$ui_compilation" "iterations=$ui_iterations" "recording=$record" > "$output/run.txt"
printf '%s\n' 'gradleJvmHeap=2g' 'gradleWorkers=1' 'gradleDaemon=single-use' 'kotlinCompiler=in-process' >> "$output/run.txt"
"$JAVA_HOME/bin/java" -version > "$output/java.txt" 2>&1
"$adb" shell getprop ro.build.fingerprint > "$output/os.txt"
"$adb" shell wm size > "$output/window.txt"
"$adb" shell wm density >> "$output/window.txt"
"$adb" shell settings get system font_scale >> "$output/window.txt"
"$adb" shell dumpsys display > "$output/display.txt"
"$adb" shell dumpsys SurfaceFlinger > "$output/surfaceflinger-before.txt"
"$adb" shell dumpsys power > "$output/power-before.txt"
"$adb" shell dumpsys thermalservice > "$output/thermal-before.txt"
"$adb" shell dumpsys battery > "$output/battery-before.txt"
# Reinstall only the disposable benchmark package, allowing historical version
# codes as well as clean origin/account setup through ordinary app controls.
"$adb" shell am force-stop net.wingress.mobivious.debug
"$adb" uninstall net.wingress.mobivious.benchmark > "$output/clear.txt" 2>&1 || true
selector=net.wingress.mobivious.benchmarks.UiScrollBenchmark
if [[ "$journey" != all ]]; then selector="$selector#$journey"; fi
set +e
(cd android && ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --offline --no-daemon --console=plain --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' \
  "-Pandroid.testInstrumentationRunnerArguments.class=$selector" \
  "-Pandroid.testInstrumentationRunnerArguments.adapter=$adapter" "${benchmark_args[@]}") 2>&1 | tee "$output/gradle.log"
result=${PIPESTATUS[0]}
set -e
if ! python3 - "$output/source-files.sha256.json" <<'PY_DRIFT'
from pathlib import Path
import hashlib,json,sys
changed=[name for name,digest in json.loads(Path(sys.argv[1]).read_text()).items()
    if not Path(name).is_file() or hashlib.sha256(Path(name).read_bytes()).hexdigest()!=digest]
Path(sys.argv[1]).with_name('source-drift.json').write_text(json.dumps(changed,indent=2)+'\n')
if changed: raise SystemExit('Source changed during the run; retain as diagnostic evidence only')
PY_DRIFT
then result=2; fi
for dir in android/macrobenchmark/build/outputs/connected_android_test_additional_output android/macrobenchmark/build/outputs/androidTest-results; do
  if [[ -d "$dir" ]]; then cp -R "$dir" "$output/"; fi
done
if [[ -f android/app/build/outputs/apk/benchmark/app-benchmark.apk ]]; then
  sha256sum android/app/build/outputs/apk/benchmark/app-benchmark.apk > "$output/apk.sha256"
fi
if "$record"; then
  mkdir -p "$output/recordings"
  while IFS= read -r file; do
    [[ "$file" == /data/local/tmp/mobivious-recording-"$recording_run"-*.mp4 ]] || continue
    "$adb" pull "$file" "$output/recordings/" >/dev/null
  done < <("$adb" shell 'ls /data/local/tmp/mobivious-recording-*.mp4' | tr -d '\r')
  if ! python3 - "$output/recordings" <<'PY_VIDEO'
import json, subprocess, sys
from pathlib import Path
files = list(Path(sys.argv[1]).glob('*.mp4'))
if not files: raise SystemExit('No recordings were produced for this run')
for path in files:
    metadata = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(path)]))
    if float(metadata['format']['duration']) <= 1 or not any(s['codec_type']=='video' for s in metadata['streams']):
        raise SystemExit(f'Invalid recording: {path}')
    path.with_suffix('.json').write_text(json.dumps(metadata,indent=2)+'\n')
PY_VIDEO
  then if [[ "$result" -eq 0 ]]; then result=1; fi; fi
fi
mkdir -p "$output/gestures"
if ! python3 - "$output" "$recording_run" "$journey" "$adapter" "$result" <<'PY_GESTURES'
import json,shutil,sys
from pathlib import Path
root=Path(sys.argv[1]); files=list((root/'connected_android_test_additional_output').rglob(f'mobivious-gestures-{sys.argv[2]}-*.json'))
for path in files:
    shutil.copy2(path,root/'gestures'/path.name)
    for gesture in json.loads(path.read_text()):
        if gesture['phase']=='measure' and not 400<=gesture['durationMs']<=475:
            raise SystemExit(f'Invalid gesture timing: {path}: {gesture}')
expected=(18 if sys.argv[4]=='pre-glass' else 19) if sys.argv[3]=='all' else 1
if sys.argv[5]=='0' and len(files)!=expected:
    raise SystemExit(f'Missing gesture provenance: expected {expected}, got {len(files)}')
PY_GESTURES
then result=2; fi
"$adb" pull /data/local/tmp/mobivious-benchmark-setup.png "$output/setup-failure.png" >/dev/null 2>&1 || true
"$adb" shell dumpsys thermalservice > "$output/thermal-after.txt" || true
printf '%s\n' "$result" > "$output/exit-code.txt"
echo "Evidence: $output"
exit "$result"
