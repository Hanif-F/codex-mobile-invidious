# Testing and documentation maintenance

[Documentation index](README.md) · [Development guide](DEVELOPMENT.md)

## Choosing checks

[Contributor policy](../AGENTS.md) applies to every verification category: JVM
tests, lint, emulator/device tests, Python checks, builds and benchmarks. Choose
checks from the changed behavior and its immediate dependencies. Keep passing
results until a later edit affects their inputs; rerun the affected checks after
fixes. Keep Gradle's incremental outputs instead of cleaning builds routinely.

| Change or stage | Checks to run |
| --- | --- |
| Development iteration | Focused JVM classes/methods or device cases for the affected behavior and its immediate dependencies. |
| Production-code task completion | Full JVM suite and debug lint once after the final relevant edits, plus required focused runtime checks. |
| UI, playback, lifecycle, storage or Android integration | Relevant device journeys and regression cases; inspect screenshots when rendering changes. |
| Shared navigation, preferences, authentication, playback services, manifests or dependencies | Widen device coverage across affected consumers, including relevant failures and recovery. |
| Documentation only | Documentation inventory/fingerprint/link checks; documentation-checker tests when its behavior changes. |
| Scripts or fixtures | Relevant Python workflow/contract checks; affected device consumers when their fixture contract changes. |
| Tests only | Changed tests; add broader checks only if the change affects what they verify. |
| Packaging or installation | Required APK builds; connected Gradle tasks already build their app/test APKs. |
| Release or performance work | Existing release validation; relevant benchmark journeys for performance work or requested measurements. |

For focused JVM feedback, from `android/`:

```sh
./gradlew :app:testDebugUnitTest --tests 'net.wingress.mobivious.AiFilterTest' --console=plain
```

Use `Class.method` for one JVM method, or repeat `--tests` for related classes.
For focused device feedback, from the repository root:

```sh
scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest#unifiedControlsGesturesAndFullscreen
```

`--device-only` requests connected tests and their APK builds, without requesting
JVM tests or lint. It requires a nonempty `class` property, supports comma-separated
classes/methods, and forwards other Gradle arguments unchanged. `--help` works
without a device or media generation. Without this flag, the runner retains its
broad JVM/device/lint behavior; use that mode for an intentional combined run.
Lint still checks the full app at completion. The full connected suite is not a
routine completion gate and its known failures/ignored cases remain explicit.

Add tests for meaningful behavior and regressions at the lowest suitable layer.
Extend existing cases where practical; trivial edits and assertions that merely
mirror implementation do not need new tests. Preserve existing coverage unless a
test has a demonstrated obsolete or duplicate purpose. Server checks follow actual
server-code or contract changes; Android-only work does not require the sibling
checkout. Report commands, results and material gaps, including pending device
acceptance when a required device is unavailable.

The two AI-filter timer cases use `runTest` and a shared virtual-time scheduler:
they check the two-second page-wait boundary without cancelling the background
request, and the five-second retry boundary with no further automatic retry.
Production timeouts and real-dispatcher concurrency cases remain unchanged.
Measured timings and runner acceptance are recorded in
[the verification report](../VERIFICATION.md#testing-workflow-optimization--21-october-2026).

## Video metadata failures

`NetworkReliabilityTest` covers empty, whitespace, malformed and non-object video
responses, explicit recovery without storyboard previews, retained HTTP errors
and successful empty writes. `AppSmokeTest#failedInitialPlaybackRetryPreservesAutoplayAndPausedIntent`
covers both HTTP failures and empty successful metadata responses, the displayed
error/Retry action, and queue/autoplay/paused-intent preservation. The opt-in
`/test/stream` control `emptyNext=true` returns one empty JSON response;
`scripts/tests/test_video_metadata_fixture.py` checks recovery and reset behavior.
Retain `SeekPreviewsSmokeTest#unavailableAndInvalidCropsLeaveSeekingUsableAndNeverShowTheSpriteSheet`
as the unavailable-preview runtime regression.

## View counts

View-count coverage uses `VideoViewCountsTest` for parsing, formatting, numeric
precision, clip/queue handoffs and request counts. The opt-in `/test/view-counts`
fixture supplies zero, legacy text, typed unknown precision, absent counts and
duplicate paginated playlist rows. `ViewCountsSmokeTest` checks Up next, organized
history, playlists/mixes, manual and source queues, recreation, clip source labels
and offline downloads. `MetadataPresentationTest` and
`PlaybackQueuePresentationTest` cover large-text wrapping and existing queue
failure/recovery presentation. Run these through the focused device runner;
`scripts/tests/test_view_counts_fixture.py` checks the fixture contract separately.

## Instance access verification

`InstanceAccessTest` covers redirect credential boundaries, bootstrap exclusions,
stale requests, pass validation, account cache isolation and recovery policy.
`InstanceAccessSmokeTest` uses the disposable fixture for automatic verification,
coalesced denials, automatic browsing/pagination retry, in-flight browse completion,
the native WebView event/Keystore round trip, either/both gates, disabled registration,
cancellation/recreation, stale account/instance contexts, expiry/policy/outage
recovery, retained playback and protected-download retry/cancellation. Its
background/PiP cases check deferred presentation, retained cancellation and that
access failures do not enter buffer recovery.

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.InstanceAccessSmokeTest
python3 -m unittest discover -s scripts/tests
```

`scripts/test-download-restart.sh` runs the existing system-transfer phases and
`ProtectedDownloadsPersistenceSmokeTest` across real force-stops. System transfers
retain IDs; managed unfinished transfers require explicit Retry and retain
completed assets and the encrypted instance pass. Build app/test APKs through the
focused runner first. These cases require `downloadRestart=true`; ordinary
connected selection skips them.

Real Cloudflare acceptance is separate from synthetic fixture acceptance. The
opt-in `LiveInstanceAccessSmokeTest` opens the default production instance's
verification page automatically, obtains a native pass, confirms it through
discovery and checks dismissal without a success confirmation. It
uses no account credentials and changes no admin policy. Run it intentionally:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveInstanceAccessSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveInstanceAccess=true
```

Set `liveKeepInstance=true` to leave the tested instance selected afterward.
A managed widget may require its visible Retry/checkbox interaction.
This confirms the live provider handoff; production account-gated content remains
separate from fixture account acceptance. Evidence is kept under ignored
`.tools/access-*.log` and `.tools/access-results/`.

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

## Scroll tracking and Android link routing

`WatchPlayerScrollTest` covers real height ranges, conservation of consumed scroll,
threshold crossings, reversals, fling bounds, window changes, normalized/legacy
restoration and zero range. `WatchPlayerResizePresentationTest` measures player
and metadata movement during an active drag, pause and reversal, and checks short
pages/fling expansion, drawers, restoration, window resizing and landscape fit.
`OpeningLinksPresentationTest` queries the installed manifest across all eight
YouTube hosts, supported content paths and rejected unrelated URLs, and checks
setup guidance and package-specific settings intents/fallback. The YouTube VIEW
case in `ContentLinksSmokeTest` covers cold/warm launches, precise timestamps,
recreation without replay, and rejected origins.

Run on an already running emulator:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.WatchPlayerResizePresentationTest,net.wingress.mobivious.OpeningLinksPresentationTest,net.wingress.mobivious.ContentLinksSmokeTest,net.wingress.mobivious.SettingsSmokeTest#openingLinksIsReachableAndSurvivesRecreation
```

For OS routing acceptance, snapshot the release/debug/YouTube/YouTube Music app-link
states, temporarily disable YouTube's conflicting link handling, and select the
debug package for a supported YouTube domain. Start an unrestricted browsable VIEW
intent and verify Android resolves it to the debug MainActivity. Shell resolution
queries must include `--query-flags 0x10000` (`MATCH_DEFAULT_ONLY`) to match a
normal activity launch. Restore every
previous link-handling flag and selected domain afterward; preserve the installed
release app and its data. Manifest matching alone does not prove default routing.

## Menu and download-label acceptance

On **17 October 2026**, the menu overhaul passed **425 JVM tests**, **31 distinct
focused Android 16 emulator cases**, debug app/test APK builds, debug lint and
all **24 Python documentation/workflow tests**. The focused cases cover grouped
video actions and channel/settings access, More collapse/navigation/recreation/
occurrence changes, VR metadata with Flat/offline/failure states, audio-only,
PiP, manual refresh and the committed automatic buffer-recovery regressions.
Large-text light/dark/RTL checks cover targets, disabled actions, readable
codec labels and numeric-unit ordering. Download choices retain their exact keys.
The subscription journey waits for Compose to apply the IME inset frame before
asserting that the mini-player is hidden.

Logs are in ignored `.tools/menu-overhaul-*.log`; device XML summaries are in
`.tools/menu-overhaul-results/` and reviewed screenshots in
`.tools/menu-overhaul-preview/`. This is focused acceptance, not a claim that
the complete connected suite is green.

## Build and unit/static checks

Original-title selection is covered by `OriginalTitlesTest` (default/round-trip
and sparse saves, concurrent/stale preference resolution, supported request
queries, account isolation, cache separation and RSS URLs) and
`OriginalTitlesSmokeTest` (guest/account automatic saves and recreation,
Retry/Revert, refresh/reopen and pagination, playback continuity/track selection,
DeArrow/spoiler precedence and unavailable-original fallback). The fixture's
`/test/original-titles` switch supplies a literal Unicode original title without
YouTube access; `unavailable=true` retains upstream titles. Focus both this class
and `DeArrowSmokeTest` through the fixture runner below. Passing results and live
backend checks are recorded separately from test inventories.

Original-title acceptance on **16 October 2026** passed **411 JVM tests**, five
original-title, three DeArrow, ten settings and one RSS journey on the Android 16
`emulator-5554`, debug app/test APK builds, debug lint and all 24 Python
documentation/workflow regression tests. The standalone fixture checks also
verified fallback, custom clip/playlist names, mutation exclusion and query
validation. Build/device logs are in ignored `.tools/original-title-*.log`.
The live public backend returned 200 for both choices on Popular, Search and
video metadata, and 400 for invalid/duplicate values. The same YOASOBI search
result changed from an English translated title to its Japanese original.
Account saves, failure recovery and synchronization were verified against local
fixtures; no real account settings were modified for live validation.

Caption appearance acceptance on 16 October 2026 passed 403 JVM tests, nine
focused Android 16 emulator cases, app/test APK builds and debug lint. Run the
`CaptionsSmokeTest`, `CaptionAppearancePresentationTest` and existing
`AppFoundationTest#nativeCaptionsIgnoreBlackCueWindowsAndBackgroundSpans` cases
through the fixture runner below. Coverage includes decoded cues and visible
pixels, an on/off comparison against the same paused video frame, languages,
normal cue gaps, failures/retry, recreation, both editor entry points/reset,
HLS/progressive/offline playback, fullscreen, mini-player, system PiP and clip
preview. Native presentation cases verify explicit backgrounds/windows,
transparency and large RTL/multiline text. Captures are under
`/data/local/tmp/mobivious-ui/caption-*.png` on the emulator.

The sibling server passed 19 focused specs, its isolated production caption-route
harness, a linked GUI build and an API-only compile check. The live public
caption API returned header-only VTT before deployment, while its Companion
returned timed English/Indonesian cues. Server deployment remains necessary for
live-instance recovery; local fixture acceptance does not establish deployment.

From the repository root, with JDK and SDK paths appropriate to the host:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
```

Run these completion checks once after the final production-code edits. Add
`:app:assembleDebug` or `:app:assembleDebugAndroidTest` when packaging or device-test
compilation needs verification; connected tasks already build their required APKs.

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

Playback-statistics acceptance on **17 October 2026** passed **433 JVM tests** and
**21 focused Android 16 emulator cases**, followed by two final presentation cases
for fixed RTL numeric ordering and copy-failure recovery. Debug app/test builds,
debug lint (0 errors, 33 warnings), the 24 Python documentation/workflow tests and
documentation checks passed. This is focused acceptance; see
[VERIFICATION.md](../VERIFICATION.md) for evidence and boundaries.

`PlaybackStatisticsTest` verifies cumulative load-byte accounting, error/timeout/
cancellation handling, visible observation and stall exclusions, bounded graph
history, frame/buffer calculations and allowlisted JSON with availability reasons.
`PlaybackStatisticsSmokeTest` exercises real Media3 metrics and copying through
Player settings → More, source refresh and queue changes, fullscreen/recreation,
mini-player/settings/background/PiP suspension, DASH/HLS/progressive/audio-only/live/offline playback.
`PlaybackStatisticsPresentationTest` checks pinned actions, large-text light/dark
RTL layouts, scrolling and selectable clipboard-failure recovery. Focus these
classes through the fixture runner below; screenshots use
`/data/local/tmp/mobivious-ui/statistics-*.png`.

`BufferRecoveryTest` checks the three-attempt schedule, exhaustion, cancellation,
stable-playback budget reset, permanent/offline exclusions and sanitized diagnostic
fields. `BufferRecoverySmokeTest` exercises real malformed and HTTP-failed DASH
manifests using `/test/recovery`, preserving paused/playing intent, exact tracks,
captions, speed, position, audio-only mode, live-edge behavior and clip boundaries.
It also checks cancellation and manual recovery after exhaustion. These synthetic
failures verify resilience; they do not identify a past upstream playback failure.

Acceptance on **17 October 2026** passed **423 JVM tests**, all eight recovery
journeys and three existing manual-refresh, track/reconnection and offline-playback
regressions on Android 16 `emulator-5554`, plus debug app/test APK builds and debug
lint. The final run is recorded in ignored `.tools/buffer-recovery-acceptance.log`.
The historical upstream trigger remains unconfirmed; the fixture failures verify
the automatic recovery behavior and its limits.

Start/connect a device yourself, select its ADB serial and the relevant cases:

```sh
export ANDROID_SERIAL=emulator-5554
scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

The runner requires one running device or an explicit serial and never launches
an emulator. It creates generated media in ignored `.tools/test-media`, starts
`fixture-server.py` on host loopback port 18080, waits for readiness, creates a
matching ADB reverse mapping when needed, then runs the selected device tests.
Without `--device-only`, it also requests JVM tests and debug lint. It rejects an
occupied port or conflicting mapping and removes only its
own server/mapping on exit. FFmpeg generates synthetic audiovisual/caption media;
`generate-player-fixture.py` adds shapes, codec/track and download fixtures.
The runner leaves APKs installed afterward to preserve the preview's app identity
and Android Keystore namespace. Fixture journeys still replace app preferences
and accounts; preserve device settings before a run and restore them afterward.

Focus a class by passing a Gradle property:

```sh
scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest
```

`MediaListPresentationTest` checks shared artwork dimensions, density, stacking,
RTL and hidden-thumbnail layouts. `ChannelControlsPresentationTest` checks
adaptive sorting, inline search focus/blur, explicit submission and restored
drafts. The channel-search cases in `SearchHistorySmokeTest` also exercise real
activity recreation and retained tab results. `DownloadsSmokeTest` covers entry
tap targets, menu-only details, Cancel/Retry and disabled incomplete entries;
`ChannelSpoilersSmokeTest` checks that local artwork/title reveals do not start
playback. Library and clips journeys capture the corresponding screen layouts.

`ServiceComposeRule.kt` and `AppNavigationTestActions.kt` provide device setup/
cleanup and navigation helpers. Fixture `/test/*` controls reset scenario state,
inject delays/failures/visibility/metadata, and expose captured requests. They are
local test contracts, not production app endpoints. The fixture server supplies
account/queue/search/community/chat/clip/optional-tool responses and Range/slow/
failed media transfers without real accounts or upstream YouTube calls.

Preferred-audio checks in `AudioSelectionTest`, `StreamControlsSmokeTest` and
`SettingsSmokeTest` cover preference normalization/sparse saves, first-segment
selection, Stable Volume and regional fallback, legacy manifest-only metadata,
audio-only mode, manual refresh/retry/reconnection, next-occurrence defaults and
failed account saves. Rich media variants reuse generated segments; the server
repository's native preference specs and disposable account harness verify the
real PATCH validator and web/native synchronization separately.

Audio menu assertions check the active track name and selected row for saved
preferences, original-audio fallback, manual refresh/retry/reconnection and the
next occurrence. Progressive playback verifies the active label without an
explicit override. The Audio menu contains no Auto option; the existing quality
menu scenarios still exercise adaptive video Auto.

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

`SeekPreviewTest` exercises storyboard parsing, quality selection, clip offsets,
cache reuse, cancellation, stale results and the tap-only 250 ms download
timer with a virtual clock. `SeekPreviewsSmokeTest` uses multiple locally generated
storyboard sheets/resolutions and timestamped request logs to check actual caching,
full-player fit, pause/resume, drag cancellation, clips, fullscreen, miniplayer/PiP,
spoilers, audio-only/live playback and delayed/failed images on the connected emulator. Run with
`scripts/test-android.sh --device-only -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.SeekPreviewsSmokeTest`.
The `/test/seek-previews` fixture mode accepts delay/failure/bad-crop controls;
its index and image request arrays record URL, start time and authentication.

Seek interval coverage checks all four choices, batch capture during settings
changes, reversed taps, clip bounds, account sparse patches, older-server errors,
per-instance guest persistence and profile isolation. Connected journeys cover
Settings save/retry/recreation, runtime Media3/PiP/accessibility actions at 0.5×
and 2×, and fixed jumps during offline playback. The sibling server fixture harness
checks the shared JSON/YAML/cookie/account/backup contract and browser shortcuts.

Seek interval acceptance on 15 October 2026 passed all 396 JVM tests and 26 focused
Android 16 emulator journeys, plus debug/instrumentation APK builds and lint.
The final set includes real PiP labels/actions, explicit video end bounds, clips,
offline playback, runtime setting changes, accessibility, tap cancellation and
recreation, all storyboard scenarios and seven chapter regressions. The existing
chapter system-PiP expansion test below remains excluded. The sibling backend
passed linked GUI/API-only builds, 28 focused Crystal specs, six browser journeys,
production fixtures and the full guarded PostgreSQL account harness. Its full
Crystal suite has a separate existing playlist test-helper compilation error,
reproduced against unchanged HEAD and recorded in the sibling development guide.

The initial seek-preview acceptance on 15 October 2026 passed 393 JVM tests and 27 focused
device scenarios on the Android 16 `emulator-5554`, including all nine new preview
scenarios, tap recreation/gesture regressions, chapters, clips, spoilers and offline
playback. Debug/instrumentation APK builds, lint, the chapter fixture contract and
documentation checks passed. The existing chapter system-PiP expansion test still
times out at the OS expand-action lookup, matching the baseline failure documented
in [Cleanup](CLEANUP.md); it was excluded from the final focused rerun. Preview PiP
cancellation and playback restoration passed. Captures are saved under
`/data/local/tmp/mobivious-ui/seek-preview-*.png` on the device.

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

## Drawer and panel verification

`PanelPresentationTest` covers pinned drawer headers, large-text/RTL navigation
and form actions, scrolling to the final modal option, and disabled dismissal
while a library action is busy. `SecondaryScreensJourneyTest` also checks a modal
channel settings form with the real software keyboard open. Run these alongside `WatchCommentsPresentationTest`,
`ChaptersPresentationTest`, `PlaybackQueuePresentationTest`,
`ChannelDescriptionPresentationTest` and `LibraryPresentationTest` for panel UI
changes. Review the captured drawer screenshots in portrait and landscape;
`StreamControlsSmokeTest` checks player-settings selection and playback continuity
against the local media fixture.

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

They use disposable repositories and fake Gradle/ADB/keytool executables to check
device-only/default task selection, exact selector/argument forwarding, validation
and help before device/media setup, failure status/cleanup, resource ownership,
signals, conflicting/multiple devices and signing safeguards.
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
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only \
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

## Search experience acceptance

`SearchAssistanceTest` covers history normalization, bounded profile-local recents,
opt-out, optional failures, debounce/cancellation, obsolete responses, single-use
submission and the unauthenticated suggestions API contract.

Run against an already running emulator with the disposable fixture:

```sh
scripts/test-android.sh --device-only --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.SearchExperienceSmokeTest,net.wingress.mobivious.SearchHistorySmokeTest,net.wingress.mobivious.DiscoverySearchSmokeTest,net.wingress.mobivious.NavigationHubSmokeTest
```

`SearchExperienceSmokeTest` checks blank new searches, edit/cancel/recreation,
transient-filter resets, suggestion/recent actions, failed/unsupported/delayed
suggestions, profile isolation, links and distinct mixed-result presentation.
Screenshots go to `/data/local/tmp/mobivious-search-experience/`, including
light/dark, thin mode, narrow large text and landscape. The test restores system
font scale, density and orientation after presentation checks. Existing search
journeys retain submission, scoped search, visibility, pagination/retry and
navigation-return coverage. Fresh searches reset filters; edit submissions retain
them. These tests establish emulator behavior, not physical-device performance.


## Complete search filters, archived annotations and phone 360° viewing

`DiscoverySearchTest` checks all six search types and canonical feature query
values. `AnnotationsViewingTest` checks bounded XML, disabled entities, unsupported
entries, appearance/timing/actions, cancellation and occurrence isolation, public
archive requests, preference/link round-trips, projection mapping and sensor math.

Use the existing emulator and disposable fixture:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AnnotationsViewingSmokeTest,net.wingress.mobivious.DiscoverySearchSmokeTest,net.wingress.mobivious.SearchExperienceSmokeTest
```

The eight player scenarios cover independent FFmpeg-converted EAC and labeled
spherical directions, actual composed-screen pixel colors, drag/recenter,
captions, fullscreen/recreation, annotation styles/dismissal/links, source-relative
clip timing, failures/404/retry/obsolete responses, guest/account defaults and link
overrides, mini-player/PiP/background and downloaded projection metadata. Compose
view captures omit the GL surface; use `UiAutomation.takeScreenshot` with the
player's window bounds for decoded pixel assertions. Captures use
`/data/local/tmp/mobivious-ui/360-*.png` and `annotations-styles.png`.

Search acceptance also covers all eleven feature controls, Medium, Movies/Shows,
chips, retained inactive filters, new/edit reset rules, recreation, pagination and
obsolete feature responses. Reset stays above the long feature list.

These checks verify emulator rendering and interactions. Physical-device motion
accuracy, broad live archive availability, production server deployment and
headset viewing are not established by the fixture. Results and emulator recovery
are recorded in [VERIFICATION.md](../VERIFICATION.md).
