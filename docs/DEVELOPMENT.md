# Developing Mobivious

[Back to the README](../README.md)

This guide covers building, testing and maintaining Mobivious for human and AI
contributors. Commands run from this repository's root unless shown otherwise.

## Repository orientation

| Location | Purpose |
| --- | --- |
| [android/](../android/) | Native Android app, Gradle configuration and tests |
| [scripts/](../scripts/) | Build helpers, local API fixtures and test runners |
| [deploy/](../deploy/) and [docker-compose.yml](../docker-compose.yml) | Server deployment configuration |
| [FEATURE_PARITY.md](../FEATURE_PARITY.md) | Detailed native feature coverage and server dependencies |
| [VERIFICATION.md](../VERIFICATION.md) | Dated validation evidence and reproduction instructions |

The app's Kotlin source is under `android/app/src/main/java/net/wingress/mobivious/`:
`data/` handles APIs, models and preferences; `player/` owns service playback
and queues; `ui/` contains Compose screens and presentation. Unit/API tests live
in `android/app/src/test/`, and device tests in `android/app/src/androidTest/`.

### Independent server checkout

The Invidious fork is a separate Git repository, normally kept beside this one:

```text
projects/
├── androidInvidious/      # Android app and deployment configuration
└── invidious/             # Independent server fork, including .git
```

Commit app and deployment changes here; commit server changes in the server
repository. Preserve the server repository's origin/upstream remotes.

Docker Compose builds `../invidious` by default. Set `INVIDIOUS_SOURCE_DIR` in
this repository's `.env` for a different checkout. Relative paths resolve against
`docker-compose.yml`; absolute paths also work. Android builds and tests do not
require the server checkout.

The following are **local paths relative to this repository's root**, available
only when the sibling checkout is present:

- `../invidious/docs/mobile-api.md` — mobile API contracts and account requirements.
- `../invidious/docs/dearrow-contributions.md` — DeArrow contribution contracts.
- `../invidious/tests/database/README.md` — disposable database test harness.

For production setup, migrations, secrets and rollback, use the
[deployment guide](../deploy/README.md).

## Build and test

### Prerequisites

- Android 13 (API 33) or newer on target devices.
- Android Studio, or an Android SDK installation with platform 37 and Build Tools 36.0.0.
- JDK 17 or newer; Android Studio's bundled JBR can be used.
- Python 3 for the release and fixture scripts.
- FFmpeg and a working Android emulator or device for connected playback tests.

Open `android/` in Android Studio. Use the checked-in Gradle wrapper; Gradle and
library versions are pinned. SDK settings, signing material and build output are
ignored by Git.

### Local checks

These environment paths are Linux examples; adjust them for your installation:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --console=plain
cd ..
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.
Building the instrumentation APK compiles device scenarios; it does not run them.

Release builds require HTTPS. Debug builds additionally allow HTTP to
`localhost`, `127.0.0.1` and the emulator's `10.0.2.2` for local tests.

### Connected device checks

Start an emulator or connect a device, then select the serial reported by ADB:

```sh
"$ANDROID_HOME/platform-tools/adb" devices
export ANDROID_SERIAL=emulator-5554  # Replace with your device's serial.
scripts/test-android.sh
```

The runner selects an already connected device, generates local test media, waits
for the fixture on localhost port 18080, reverses that port through ADB, and runs
unit tests, device tests and debug lint. It never starts or restarts an emulator.
It stops its own fixture and removes only the ADB reverse mapping it created.
Stop any earlier fixture using the same port before starting it. The fixture tests
use generated media and local accounts rather than production accounts or YouTube
requests. Generated files stay in the ignored `.tools/` directory.

See [repeat Android checks](../VERIFICATION.md#repeat-android-checks) and the
feature-specific sections of the verification record for focused scenarios.
Pass additional Gradle options to run focused checks, for example:

```sh
scripts/test-android.sh --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

On a busy host, add `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m` to limit build
resource use. Distinguish compiled checks from runtime acceptance when recording
results; the complete device suite is not yet green.

The focused download checks use separate video/audio files, captions, Range
responses, slow transfers and controllable failures:

```sh
scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.DownloadsSmokeTest
scripts/test-download-restart.sh
```

The restart runner installs the existing debug/test APKs, seeds background
transfers, force-stops only the test app and checks recovery in a new process. It
does not reboot or restart the emulator. Build the APKs first; retain the generated
fixture media. Its evidence is under `.tools/download-verification/`.

`acceptedVideoConversionPreservesResolutionFrameRateAndBothTracks` is explicitly
ignored because Emulator 37.2.12 crashes in the host gfxstream `TextureResize`
code during this conversion. The other download tests include unchanged paired
MP4 export and rejecting conversion. Remove that test's `@Ignore` only when
testing on a physical device or an emulator with this graphics crash resolved.

## Signed releases

Releases are built and published manually. Set `JAVA_HOME` and `ANDROID_HOME`
as described above. The release script also requires `keytool`, Python 3 and
`sha256sum`; publishing uses an authenticated GitHub CLI (`gh`) and Git.

**Preserve `android/keystore/` and `android/signing.properties` securely.**
APK updates must use the same signing key. If `signing.properties` is absent,
the build script generates a new personal key; restore the existing signing
material before building an update to an already distributed APK. Keep keys and
passwords local and out of Git.

### Prepare and verify

Update `versionName` and increment `versionCode` in
`android/app/build.gradle.kts`. Review the intended source changes before building:

```sh
git status --short
git diff
scripts/build-release.sh

# Read the configured version instead of hardcoding it here.
release_version="$(awk -F '"' '/versionName =/ { print $2 }' android/app/build.gradle.kts)"

"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  "artifacts/Mobivious-$release_version.apk"
(cd artifacts && sha256sum -c "Mobivious-$release_version.apk.sha256")
```

The script runs unit tests, builds the signed release and runs release lint.
It writes `Mobivious-<version>.apk` and its `.apk.sha256` file to the ignored
`artifacts/` directory. Confirm the signing certificate matches the previously
published APK before distributing an update.

Write a short changelog and any server requirements to
`artifacts/release-notes.md`. Account features and other server-dependent additions
need the matching server rollout separately; an APK build does not deploy it.

### Publish the verified commit

Keep the source unchanged between the verified build and release commit. Stage
only intended changes; include any other release files explicitly before committing.
Run these commands in the same shell so `release_version` remains available:

```sh
git add android/app/build.gradle.kts
# Stage any other intended release changes explicitly.
git diff --cached
git commit -m "Prepare Mobivious $release_version release"
git push origin HEAD:main
git tag -a "v$release_version" -m "Mobivious $release_version"
git push origin "v$release_version"

gh release create "v$release_version" \
  "artifacts/Mobivious-$release_version.apk" \
  "artifacts/Mobivious-$release_version.apk.sha256" \
  --repo Hanif-F/codex-mobile-invidious --verify-tag --latest \
  --title "Mobivious $release_version" --notes-file artifacts/release-notes.md
```

Record checks and remaining acceptance gaps in the verification record as
appropriate. Its dated entries describe individual implementation and release
stages; older test totals or rollout statements are historical.

## Upstream maintenance

Merge upstream changes into the independent Invidious repository, run its
account/API checks, rebuild the Docker image and pull Companion updates. Follow
[repeat server checks](../VERIFICATION.md#repeat-server-checks) and the local
mobile API and database harness documentation.

The client contains no YouTube scraping or signature deciphering. Server fixes
can be adopted without rebuilding the APK while API contracts remain compatible;
contract changes may require a client update. Maintain this fork's custom
extraction features and mobile extensions when merging upstream.
