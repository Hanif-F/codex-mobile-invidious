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
| Release checks | Unit/lint/build plus signed artifact/checksum verification; physical-device acceptance remains separate. |

[Test catalog](reference/TEST_CATALOG.md) enumerates every maintained JVM/device
test file and declared test method, including helper-only files and ignored cases.
It is an inventory, not a passing-results report. [VERIFICATION.md](../VERIFICATION.md)
and dated evidence bundles record actual results and gaps. The developer guide
explicitly notes that the complete connected suite is not yet green.

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
`generate-player-fixture.py` adds shapes, codec/track/ambient and download fixtures.

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

This installs the test app/APKs on the selected device (default emulator-5554),
seeds background transfers, force-stops only `net.wingress.mobivious.debug`, and
starts a fresh instrumentation process to check restored/offline resume behavior.
It uses port 18080 and saves evidence in `.tools/download-verification`. It does
not reboot the device. `downloadRestart=true` gates the two persistence phases.

The accepted-video-conversion test in DownloadsSmokeTest is explicitly ignored
because Emulator 37.2.12 crashes in host gfxstream TextureResize. Remove Ignore
only on a capable physical device or fixed emulator. Unchanged paired export
and declining conversion have separate checks. Do not count ignored conversion
as accepted runtime evidence.

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
guide/source links and reviewed content hashes, and checks generated source/test
catalogs. It reports added/removed/changed files rather than silently treating
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

Historical visual logs/media stay in their dated collections. Avoid rewriting
old test counts as current status; add new evidence or clear superseding notes.

## Opaque UI performance diagnostics

`scripts/test-ui-performance.sh --list` lists deterministic release-like journeys.
Run with `ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --label opaque
--journey popular` (one shell command). The separate `.benchmark` package is
profileable, non-debuggable and debug-signed; release security/optimization remain
unchanged. Only loopback fixture HTTP is enabled. The runner owns its fixture and
reverse mapping, and preserves source/APK fingerprints, configuration, raw JSON,
per-iteration traces and exit status under `.tools/ui-ux-overhaul/performance/`.

Every timing uses Full compilation and ten iterations. Fixture account/origin are
configured through ordinary UI; account preference reads must finish before
measurement. Warm/cold artwork, density, pagination, mixed search, watch ambient
and navigation have distinct scenarios. Use `scripts/summarize-ui-performance.py
RAW_JSON --output SUMMARY_JSON` to compute the median of per-iteration P50/P90/P95/
P99 CPU/overrun values and positive-overrun fractions without pooling frames or
silently removing outliers. Timing is separate from normal-speed visual recordings.

Scrolling injects ordinary touch events across 70% of the scrollable viewport for
400ms, six forward and six reverse swipes with 100ms gaps. UI Automator 2.4's
step-based swipe waits for display frames, so a step count cannot define duration.
The harness schedules by elapsed time, retains each gesture's event duration and
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
measured region; cold-artwork runs use fresh URL identities. Historical adapters
map existing ordinary controls and record their original source commit alongside
the exact adapted source archive/diff. Unavailable newer features remain explicit
N/A entries.

This task uses emulator acceptance by user instruction. Emulator results cannot
be described as physical-device smoothness. Preserve failed runs and inherited
failures separately from successful acceptance evidence. If the emulator is lost,
stop development/testing while attempting recovery; if recovery fails, wait for
the user to restart it.

The documentation inventory reflects existing files in the working tree, including
untracked additions and excluding locally deleted tracked files before a commit.

The screen audit is tracked in [Screen acceptance](ui-ux-implementation-2026-10-13/SCREEN_ACCEPTANCE.md).
Search visibility choices apply while their sheet stays open and reset pagination;
chat timestamps save independently of invalid filter text. Context-bound chat
callbacks reject edits from another video/account. Download details are a local
opaque sheet. `captureWatchScreenshot` validates each saved PNG header so a path
or capture failure cannot silently count as screenshot evidence.
