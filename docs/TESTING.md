# Testing and documentation maintenance

[Documentation index](README.md) · [Development guide](DEVELOPMENT.md)

## What each check establishes

| Check | Evidence supplied |
| --- | --- |
| JVM `testDebugUnitTest` | Domain rules, parser/API contracts, request isolation, concurrency, cache and selection behavior using JUnit/MockWebServer. |
| `assembleDebug` / `assembleDebugAndroidTest` | Application and device-test sources compile; no device execution. |
| `lintDebug` / `lintRelease` | Static Android diagnostics; no runtime acceptance. |
| Connected smoke/journey tests | Interactions and service behavior on the selected running device against local fixtures. |
| Presentation tests | Layout/semantics/rendering checks and screenshots under device test paths. |
| Python fixture checks | Local fixture contract consistency, without proving the production server matches it. |
| Release checks | Unit/lint/build plus signed artifact/checksum and matching R8 archive verification; optimized runtime acceptance remains separate. |

[Test catalog](reference/TEST_CATALOG.md) enumerates every maintained JVM/device
test file and declared test method, including helper-only files and ignored cases.
It is an inventory, not a passing-results report. [Cleanup results](CLEANUP.md)
record the latest source/device checks and baseline failures. The complete
connected suite is not yet green.

## Build and unit/static checks

From the repository root, with JDK and SDK paths appropriate to the host:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Use `--offline` only when dependencies are already cached. The Gradle wrapper
pins 9.4.1 with a distribution checksum; app configuration pins SDK/tool/library
versions. See [Development](DEVELOPMENT.md) for prerequisites, signing and manual
release commands. App tests require no sibling server checkout.

## Optimized-build checks

Release and benchmark builds use R8 optimization and resource shrinking. Debug
tests still check domain behavior, but cannot establish that reflection, resource
loading and framework entry points survive optimization.

Build the optimized variants and run static/domain checks:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease \
  :app:assembleBenchmark :macrobenchmark:assembleBenchmark --console=plain
```

On a connected device, use the isolated optimized benchmark package for focused
ordinary-UI account setup, navigation, watch, settings-saving and download
journeys. The existing performance runner's `--record` mode exercises those
flows without a timing benchmark. Supplement these with background/PiP playback,
clip preview, offline resume and paired-media export checks. Keep the existing
accepted-video-conversion emulator limitation explicit; an ignored case is not
runtime acceptance. Preserve the installed release and its data during tests.

Before distribution, verify the release signature, uncompressed DEX ZIP entries
and the matching [R8 archive](DEVELOPMENT.md#prepare-and-verify). APK measurements
have no size threshold. Compare builds from the same application source and
report APK/DEX bytes and percentage change; keep baseline/candidate artifacts
separate from published releases. If a device is unavailable, report runtime
acceptance as pending rather than inferring it from a successful build.

## Device runner and media fixtures

Start/connect a device yourself, select its ADB serial and run:

```sh
export ANDROID_SERIAL=emulator-5554
scripts/test-android.sh
```

The runner requires one running device or an explicit serial and never launches
an emulator. It creates generated media in ignored `.tools/test-media`, starts
`fixture-server.py` on host loopback port 18080, waits for readiness, creates a
matching ADB reverse mapping when needed, then runs JVM/device tests and debug
lint. It rejects an occupied port or conflicting mapping and removes only its
own server/mapping on exit. FFmpeg generates synthetic audiovisual/caption media;
`generate-player-fixture.py` adds shapes, codec/track and download fixtures.
The runner leaves APKs installed afterward to preserve the preview's app identity
and Android Keystore namespace. Fixture journeys still replace app preferences
and accounts; preserve device settings before a run and restore them afterward.

Focus a class by passing a Gradle property:

```sh
scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

`ServiceComposeRule.kt` and `AppNavigationTestActions.kt` provide device setup/
cleanup and navigation helpers. Fixture `/test/*` controls reset scenario state,
inject delays/failures/visibility/metadata, and expose captured requests. They are
local test contracts, not production app endpoints. The fixture server supplies
account/queue/search/community/chat/clip/optional-tool responses and Range/slow/
failed media transfers without real accounts or upstream YouTube calls.

`AppThemePresentationTest` checks appearance migration, twelve palette galleries,
opaque components and preservation of open menus/focused field drafts.
`AppThemeTest` checks enabled text and control-boundary contrast numerically;
`AppWindowPresentationTest` covers RTL pane placement and software drawing.
`SettingsSmokeTest`
also exercises immediate saving without a shared draft, activity recreation,
account changes and System appearance updates. Dormant optical keys are migration
fixtures and have no rendering effect.

`FeedRefreshPresentationTest` checks pull progress, cancellation, the refresh
threshold, scroll behavior and the accessible Refresh action. `FeedRefreshJourneyTest`
checks the three root feeds against delayed/failed fixture requests, preserving
rows and active filters, replacing page one, empty results and navigation during
refresh. Player presentation tests sample known backgrounds to verify a uniform
25% dim layer, transparent individual controls and an unobscured picture after
controls hide; they also retain their layout and interaction checks.

Standalone checks:

```sh
python3 scripts/check-chat-fixture.py
python3 scripts/check-chapters-fixture.py
python3 scripts/check-content-links-fixture.py
python3 scripts/check-avatar-fixture.py --media-dir .tools/test-media
python3 scripts/check-community-fixture.py --media-dir .tools/test-media
```

Avatar/community checks require generated thumbnail media. Each checker manages
its disposable fixture on a free local port. They cover their fixture contracts,
not Android screen rendering or live service compatibility.

## Download restart and conversion acceptance

Build debug and instrumentation APKs and generate media first, then run:

```sh
scripts/test-download-restart.sh
```

This installs the test app/APKs on the selected device (one connected device or explicit `ANDROID_SERIAL`),
seeds background transfers, force-stops only `net.wingress.mobivious.debug`, and
starts a fresh instrumentation process to check restored/offline resume behavior.
It uses port 18080 and saves evidence in `.tools/download-verification`. It does
not reboot the device. `downloadRestart=true` gates the two persistence phases.

The accepted-video-conversion test in DownloadsSmokeTest is explicitly ignored
because Emulator 37.2.12 crashes in host gfxstream TextureResize. Remove Ignore
only on a capable physical device or fixed emulator. Unchanged paired export
and declining conversion have separate checks. Do not count ignored conversion
as accepted runtime evidence.

## Runner and release safeguards

The Android, download-restart and performance runners source
`scripts/lib/android-fixture.sh` for device selection, fixture readiness and
owned-process/reverse-mapping cleanup. They preserve pre-existing or replaced
mappings and reject occupied host/device ports. Signals also clean up owned
resources. `generate-player-fixture.py` owns all base and specialized media;
each missing base output is generated independently so a cached manifest does
not hide missing captions or artwork. Standalone Python contract checks use
`fixture_support.py` for readiness, startup diagnostics and bounded teardown.

Run workflow regression checks while port 18080 is free:

```sh
python3 -m unittest discover -s scripts/tests -v
```

They use disposable repositories and fake ADB/keytool executables to check
resource ownership, signals, conflicting/multiple devices and signing safeguards.
No real signing material or device data is modified. Release initialization is
explicit and refuses existing keys; see [Development](DEVELOPMENT.md#signed-releases).

## Coverage by behavior

JVM tests cover validators/models, preference deltas/migration, exact metadata,
HTTP auth/cancellation/cache, watched/blocked reconciliation, queue occurrences,
stream selection, chapters/geometry/gestures, comments/chat, clips/download records,
optional tools and feed/link contracts. Device suites exercise service playback,
background/PiP/recreation, offline transfers/export, account navigation, settings,
scoped/global search, library mutations and responsive/accessibility presentation.
Use the catalog's exact method names to locate boundary/error cases.

When adding behavior, place domain rules in testable data/player helpers, update
the appropriate JVM contract tests and use focused device checks for lifecycle/
decoder/rendering behavior. Preserve unrelated fixture routes/scenario reset state.
Record source, device, exact command, pass/fail/ignored status and evidence path;
production/server rollout is a separate acceptance step.

## Documentation checks

```sh
python3 scripts/check-documentation.py
```

The checker compares maintained files with `reference/coverage.json`, verifies
local links across every repository Markdown document and reviewed content hashes,
and checks generated source, test and coverage catalogs. It reports added/removed/changed files rather than silently treating
them as documented. It uses only the standard Python library and Git inventory.
It does not compile or execute Android code, or certify semantic accuracy.

After reviewing a source change, update the affected prose and per-file
description/guide in `docs/reference/coverage.json`, then refresh the reviewed
fingerprints and generated catalogs:

```sh
python3 scripts/check-documentation.py --refresh
python3 scripts/check-documentation.py
```

New maintained files must be explicitly assigned a guide and description first;
refresh refuses unknown inventory entries. Review the resulting diff. Refreshing
hashes without reviewing prose would defeat the freshness check. Update version/
source/date context in the documentation index when behavior/version changes.

Keep generated logs/media under ignored `.tools/`, with source, command and device
provenance. Update the current results report when checks change; Git retains
superseded documentation.

## Opaque UI performance diagnostics

`scripts/test-ui-performance.sh --list` lists deterministic release-like journeys.
Run with `ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --label opaque
--journey popular` (one shell command). The separate `.benchmark` package is
profileable, non-debuggable and debug-signed; release security/optimization remain
unchanged. Only loopback fixture HTTP is enabled. The runner owns its fixture and
reverse mapping, and preserves source/APK fingerprints, configuration, raw JSON,
per-iteration traces and exit status under `.tools/ui-performance/`.

Every timing uses Full compilation and ten iterations. Fixture account/origin are
configured through ordinary UI; account preference reads must finish before
measurement. Warm/cold artwork, density, pagination, mixed search, watch
and navigation have distinct scenarios. Use `scripts/summarize-ui-performance.py
RAW_JSON --output SUMMARY_JSON` to compute the median of per-iteration P50/P90/P95/
P99 CPU/overrun values and positive-overrun fractions without pooling frames or
silently removing outliers. Timing is separate from normal-speed visual recordings.

Scrolling injects ordinary touch events across 70% of the scrollable viewport for
400ms, six forward and six reverse swipes with 100ms gaps. The harness schedules
by elapsed time, retains each gesture's event duration and
injection completion time in additional test output, and rejects measured event
durations outside 400–475ms. The runner rejects missing gesture provenance.

Use `--record --journey navigation` with a fresh label for a separate normal-speed
video. Recording mode performs ordinary UI interactions without Macrobenchmark
timing; run-specific filenames and `ffprobe` checks prevent stale or empty videos
from being reported. Do not record during a timing run.

`searchPagination` exercises real pagination because Popular is a single-page
API. `trendingChanges` waits for the selected category/country's returned content;
`settingsSaving` confirms delayed/failing toggle and speed changes. Downloads
queues three slow fixture transfers. Warm scrolling primes artwork outside the
measured region; cold-artwork runs use fresh URL identities. All eighteen journeys
use current controls and automatic preference saving. The runner records HEAD
alongside the exact source archive/diff; retired UI adapters and commit overrides
have been removed.

Emulator results cannot be described as physical-device smoothness. Preserve
failed runs and inherited failures separately from successful acceptance evidence.
If a device disconnects, stop connected testing until it is available again.

The documentation inventory reflects existing files in the working tree, including
untracked additions and excluding locally deleted tracked files before a commit.

Search visibility choices apply while their sheet stays open and reset pagination;
chat timestamps save independently of invalid filter text. Context-bound chat
callbacks reject edits from another video/account. Download details are a local
opaque sheet. `captureWatchScreenshot` validates each saved PNG header so a path
or capture failure cannot silently count as screenshot evidence.

## Channel hiding acceptance

Run the channel and DeArrow journeys on the selected running device:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.ChannelSpoilersSmokeTest,net.wingress.mobivious.DeArrowSmokeTest
```

These cover guest persistence/validation, account sparse saves and retry/reset,
first-tap thumbnail reveal, duplicate occurrences, timed cache dates/misses, delayed
DeArrow cycling, AI precedence, account-read failures during playback, clips and
offline media metadata. JVM tests additionally cover sentinel/future dates, exact
UTC boundaries, sparse merge/isolation, lookup batching and stale occurrence/context
rejection. Screenshot names start with `spoiler-` under `/data/local/tmp/mobivious-ui`.
The local fixture exposes `/test/spoilers` controls and records only non-secret
channel patches and publication IDs. The sibling account harness tests production
route validation, scopes, CSRF, concurrent writes and cache-only date responses.
