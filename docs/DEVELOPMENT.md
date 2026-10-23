# Developing Mobivious

[Back to the README](../README.md)

For current behavior and implementation references, start with the
[complete documentation index](README.md). It includes architecture, all feature
workflows, settings and API contracts, subsystem guides, and source/test catalogs.

The [UI guide](UI.md) describes the current opaque Material 3 implementation.

This guide covers building, testing and maintaining Mobivious for human and AI
contributors. Commands run from this repository's root unless shown otherwise.

## Repository orientation

| Location | Purpose |
| --- | --- |
| [android/](../android/) | Native Android app, Gradle configuration and tests |
| [scripts/](../scripts/) | Build helpers, local API fixtures and test runners |
| [deploy/](../deploy/) and [docker-compose.yml](../docker-compose.yml) | Server deployment configuration |
| [Features](FEATURES.md) and [API](API.md) | Supported capabilities and server dependencies |
| [Testing](TESTING.md) | Repeatable checks and acceptance boundaries |

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

### Current toolchain and direct dependencies

These are the repository's configured versions for 0.13.1, rather than a request
to install the newest upstream release. The Gradle files are authoritative after
a dependency update.

| Component | Configured version / purpose |
| --- | --- |
| Gradle wrapper | 9.4.1, with distribution SHA-256 verification. |
| Android Gradle plugin | 9.2.1; `:app` application and `:macrobenchmark` test modules. |
| Compose compiler plugin | 2.3.10. |
| Android SDK | Minimum 33; compile/target 37. |
| Java source/bytecode target | 17. |
| Compose BOM | 2026.03.00; aligns Material3, icons, UI tooling and Compose tests. |
| Activity Compose | 1.12.3; activity/UI integration. |
| Lifecycle Runtime Compose | 2.10.0; lifecycle-aware collection. AndroidViewModel is hosted by the activity. |
| Media3 | 1.9.3; ExoPlayer, DASH/HLS, sessions, PlayerView and Transformer. |
| OkHttp / MockWebServer | 4.12.0; application HTTP / JVM contract fixtures. |
| Coil Compose | 2.7.0; images and caching. |
| Coroutines Android | 1.10.2; asynchronous work and state flows. |
| WindowManager | 1.5.1; opaque fold/hinge layout policy. |
| RE2/J | 1.8; bounded-complexity chat regex matching. |
| Macrobenchmark / UI Automator | 1.5.0 / 2.3.0; isolated performance journeys and input. |
| JUnit / JVM JSON | 4.13.2 / 20251224. |
| Android test JUnit / runner / Espresso | 1.3.0 / 1.7.0 / 3.7.0. |

Release uses `net.wingress.mobivious`, version code 31 and version name 0.13.1.
Debug adds `.debug` and `-debug`, allowing separate installation as Mobivious
Preview. BuildConfig and Compose are enabled. Release builds use R8 code
optimization/obfuscation and resource shrinking; debug builds remain unoptimized.
The benchmark variant inherits release optimization. Minimum SDK 33 keeps DEX
and native libraries uncompressed in the APK; shrinking removes unused payload
without changing that packaging policy. Android's default optimized rules and
dependency consumer rules are supplemented by `app/proguard-rules.pro` only when
verified indirect access needs a narrowly scoped keep rule.
Personal signing configuration is loaded only when local `signing.properties`
exists. JVM tests return Android default stub values where required; device-only
behavior still needs instrumentation.

### Local checks

Follow [Choosing checks](TESTING.md#choosing-checks) and the
[contributor policy](../AGENTS.md): use focused tests during development and run
the full JVM suite and debug lint once after final production-code edits. For
example, run `./gradlew :app:testDebugUnitTest --tests 'net.wingress.mobivious.AiFilterTest'`
from `android/` for a focused JVM iteration. Documentation, scripts and test-only
changes use their relevant checks without automatically adding unrelated Android
validation. Repeat a passing check when later edits affect its inputs.

These environment paths are Linux examples; adjust them for your installation:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
cd ..
```

Add `:app:assembleDebug` or `:app:assembleDebugAndroidTest` when packaging or
device-test compilation needs verification. Connected Gradle tasks build their
required APKs. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.
Building the instrumentation APK compiles device scenarios; it does not run them.

Release builds require HTTPS. Debug builds additionally allow HTTP to
`localhost`, `127.0.0.1` and the emulator's `10.0.2.2` for local tests.

### Connected device checks

Start an emulator or connect a device, then select the serial reported by ADB:

```sh
"$ANDROID_HOME/platform-tools/adb" devices
export ANDROID_SERIAL=emulator-5554  # Replace with your device's serial.
scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

The runner selects an already connected device, generates local test media, waits
for the fixture on localhost port 18080, reverses that port through ADB, and runs
the selected device tests. `--device-only` requires a nonempty class/method
selector and does not request JVM tests or lint. Without this flag, the runner
keeps its broad JVM/device/lint behavior for an intentional combined run.
`scripts/test-android.sh --help` needs no device. It never starts or restarts an emulator.
It stops its own fixture and removes only the ADB reverse mapping it created.
Stop any earlier fixture using the same port before starting it. The fixture tests
use generated media and local accounts rather than production accounts or YouTube
requests. Generated files stay in the ignored `.tools/` directory.

See [Testing](TESTING.md) and its test catalog for focused scenarios.
Pass additional Gradle options to run focused checks, for example:

```sh
scripts/test-android.sh --device-only --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

On a busy host, add `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m` to limit build
resource use. Distinguish compiled checks from runtime acceptance when recording
results; the complete device suite is not yet green.

The focused download checks use separate video/audio files, captions, Range
responses, slow transfers and controllable failures:

```sh
scripts/test-android.sh --device-only \
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
APK updates must use the same signing key. The build script refuses to build when
`signing.properties` is absent; restore the existing material before building an
update. For a first release only, `scripts/build-release.sh --init-signing` creates
a new identity in an empty keystore directory. It refuses existing material and
publishes no partial configuration after failed key generation. Keep keys and
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

The script requires a nonempty R8 mapping before copying release artifacts and
archives it under `artifacts/r8/<version>/<APK-SHA256>/mapping.txt`. Available
`configuration.txt`, `usage.txt`, `seeds.txt` and `resources.txt` reports are saved
there too. `apk.sha256` identifies the exact APK; `SHA256SUMS` verifies the archived
files. Rebuilding a version preserves previous archives, and a conflicting
mapping for the same APK is rejected. Keep these local archives for as long as
the corresponding APK may be installed; rebuilding overwrites Gradle's mapping
output, and a different build's mapping cannot reliably decode an older crash.

To recover an obfuscated crash, select the archive by the installed APK's
SHA-256 and pass its mapping plus the captured stack trace to the SDK's
`cmdline-tools/latest/bin/retrace`. Archive generation does not establish runtime
acceptance. Follow the [optimized-build checks](TESTING.md#optimized-build-checks)
before shipping.

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

Record the verified source, commands, device, results and acceptance gaps in the
release notes. Preserve raw output under ignored `.tools/`; do not infer current
acceptance from previous test totals.

## Upstream maintenance

Merge upstream changes into the independent Invidious repository, run its
account/API checks, rebuild the Docker image and pull Companion updates. Follow
the sibling checkout's `docs/mobile-api.md` and `tests/database/README.md`.
Run the database harness only against its empty disposable test database; preserve
production credentials and data. From the server checkout, `shards install` and
`crystal spec` run its dependency setup and specs. Initialize its `mocks` submodule
as documented there before running specs.

The client contains no YouTube scraping or signature deciphering. Server fixes
can be adopted without rebuilding the APK while API contracts remain compatible;
contract changes may require a client update. Maintain this fork's custom
extraction features and mobile extensions when merging upstream.
