# Repository cleanup — 14 October 2026

[Documentation index](README.md) · [Architecture](ARCHITECTURE.md) · [Testing](TESTING.md)

The audit started from clean `main` at `06ce670` (Mobivious 0.11.0, code 22).
Its scope is `androidInvidious`: the Android application, benchmark module,
tests, fixtures, build/release runners, deployment examples and documentation.
The sibling Invidious repository is an integration boundary and is unchanged.

## Watch presentation follow-up

The expanded watch page now uses four filled circular action buttons with labels
below; narrow rows reduce spacing and labels wrap without shrinking text. Portrait
Play/Pause/Replay stays centered, using a compact footer or a secondary-control
popup when the picture is too short. The shared player dim layer is 25%. Retired
visual settings, decoder sampling, dedicated fixtures/tests and the corresponding
performance journey are removed; legacy saved values have no runtime consumers.

Validation on `emulator-5554` (Pixel 8 Pro AVD, Android 16/API 36): 354 JVM tests,
27 focused device tests and 19 Python documentation/workflow tests passed, with
no skipped cases in the focused device run. The device selection combines
`WatchPresentationTest`, `PlayerControlsPresentationTest`,
`PlayerFeedbackPresentationTest`, `WatchPlayerResizePresentationTest`,
`AppSmokeTest.unifiedControlsGesturesAndFullscreen`, the two retired-setting/accent
checks in `SettingsSmokeTest`, and the retired-preference check in
`AppThemePresentationTest`. This covers geometry, label wrapping, RTL, opacity,
popup actions, recreation/account changes, seeking, fullscreen/PiP and resizing.

Debug/instrumentation and app/macrobenchmark APK builds passed. `lintDebug`
reported zero errors and 20 warnings. `scripts/check-documentation.py` verifies
the refreshed source/test catalogs and fingerprints. Current reports are under
`android/app/build/reports/`; screenshots are in ignored `.tools/`. The full
connected suite and the affected physical phone were not verified by this
follow-up; the earlier full-suite baseline limitations below remain separate.

## Application inventory and preservation

The `:app` module uses Kotlin/Compose, an activity-owned AndroidViewModel,
context-fenced OkHttp APIs and a service-owned Media3 player/queue. Local profiles
use checked SharedPreferences edits and Android Keystore session encryption;
downloads use a SQLite journal and app-specific media. `:macrobenchmark` measures
an isolated profileable variant. The manifest registers activity links, playback
and export services, system download broadcasts and the RSS FileProvider.

There are no repository CI pipelines, custom Git hooks, separate scheduled jobs,
package lockfiles or production database migrations in this Android checkout.
Builds/releases are manual Gradle/script workflows. Compose/proxy examples refer
to the independent backend; its volume names, credentials, migrations and deployment
configuration are preserved. No release, version bump or production operation is
part of this cleanup.

## Code and architecture

- Removed unused `WatchIconButton`. Reference checks found no app or test callers;
  current player controls use `PlayerIconButton`/`PlayerPlayControl`.
- Removed the unused media-overlay surface role, optical `AppContentColors` object
  and its composition local. Active controls now use Material color roles directly;
  `AppSurface` retains the shared opaque control/section/mini-player policy.
- Removed unused pill/default palette tokens and moved a contrast assertion helper
  into its device test. Tests use the canonical `appColorScheme` implementation.
- Renamed `SessionStore.guestDeArrow` to `guestPreferences`, migrating every runtime
  and test consumer. It already stored all supported guest preferences; persistence
  keys, values and migration behavior are unchanged.
- Moved runtime queue snapshots, occurrences and rules from `data/PlaybackQueue.kt`
  to `player/PlaybackQueue.kt`. Source-page/library data models are consolidated in
  `data/Models.kt`. This removes the data/player package cycle; implementation and
  serialized fields are preserved, and all consumers use the canonical player types.
  Occurrence-aware URL sharing now belongs to the queue snapshot; incoming-link
  parsing remains in data without importing runtime player state.
- Separated the system download broadcast adapter into `data/DownloadReceiver.kt`,
  keeping its registered class name, actions and behavior. Download persistence
  no longer imports the activity or application composition root.
- Removed superseded video/playlist search, channel-playlist and page-number
  playlist helpers. Tests now exercise `searchResults`, `channelPlaylistPage` and
  `queuePage`, which are already the app's current implementations. Removed the
  old manual-preference-save flag/notification and constant page-wide busy state;
  the current automatic per-key save pipeline remains canonical. SponsorBlock also
  loses its unused sign-in callback, coroutine scope and constant manual-save flag;
  its active channel-loading gate remains. Settings also loses an unchanging
  `saving` state and an unused preference-page Back callback; account-operation
  navigation gates remain.
- Removed the no-context `clearPositions` wrapper and migrated consumers to the
  existing context-checked method. The visibility-cache reset helper is retained
  because isolation and fallback tests actively require it.
- Removed unreachable Android <31 PiP and <29 export branches and the redundant
  sensitive-clipboard guard; minimum SDK is 33. The Android 35 export-type branch
  remains necessary. Media user agents use the configured build version.
- Cleaned unused/duplicate imports, fixed navigation modifier argument order and
  supplied a layout root for the native clip-preview XML inflation.

## Dependencies, configuration and hygiene

Removed direct `lifecycle-viewmodel-compose` and `ui-tooling-preview` declarations:
there is no Compose `viewModel()` or preview annotation consumer. Activity-based
view-model hosting and debug tooling remain. Versions and the Compose BOM are
unchanged; there is no lockfile to regenerate.

Moved the adaptive launcher icon from `mipmap-anydpi-v26` to `mipmap-anydpi`,
preserving its XML bytes and resource identity. Every supported device already
supports adaptive icons. Python bytecode/cache ignore rules keep newly shared
script modules from introducing generated files into the inventory.

## Workflows

Three Android runners share `scripts/lib/android-fixture.sh`: one device-selection,
readiness and owned-process/reverse-mapping teardown policy. Conflicting ports
are rejected; existing/replaced mappings are preserved; signals clean up owned
resources. Download restart selects a connected device consistently.

All media generation lives in `generate-player-fixture.py`, including base
MP4/DASH/HLS, captions, thumbnail and clip storyboard. Missing base outputs are
checked independently. Five contract checkers share `fixture_support.py` for
readiness, early-process diagnostics and bounded teardown; their contract
assertions are preserved.

`build-release.sh` refuses missing signing configuration. First identity creation
requires `--init-signing`, refuses existing material and publishes configuration
only after successful key generation. Existing release keys remain untouched.
Workflow regression tests use disposable repositories and fake ADB/keytool tools.

Removed both retired UI benchmark adapters, their source-commit override and old
manual-save/Discover navigation branches. All eighteen current journeys remain,
including automatic-save retry and gesture/video provenance gates.
Settings setup now opens You, matching its actual navigation ownership. Output
uses `.tools/ui-performance/`; this cleanup does not claim timing acceptance.

## Documentation

Corrected module inventory, dependencies, Settings navigation, About attribution,
release initialization and obsolete deployment/source-layout statements. Current
subsystem guides own supported behavior and repeatable commands.

Deleted both Liquid Glass documents, all ten dated design/audit/evidence bundles,
and the overlapping root feature-parity and chronological verification documents.
These described earlier revisions and contained stale commands, acceptance totals,
plans, screenshots, videos and temporary capture harnesses. Reference checks found
no runtime/test/workflow consumers of their assets. Necessary API requirements,
settings migrations, deployment operations and licensing remain in current guides
and source; older records remain recoverable from Git. This removes **1,036 tracked
files totaling 474,564,908 bytes (about 453 MiB)**, including 22 Markdown documents.
All surviving guide and catalog links were reconciled; no historical index or
replacement archive was added.

The documentation checker now requires every repository Markdown document in
the reviewed inventory, checks all local file links and generates coverage from
actual sources and Gradle version. Hardcoded counts and superseded renderer
references no longer define coverage. Disposable-inventory tests verify additions,
removals, review fingerprints, broken links and generated version/count accuracy.

## Validation and baseline issues

| Check | Result |
| --- | --- |
| Baseline JVM | 362 passed; zero failures/errors/skips. |
| Baseline builds/static checks | Debug and release APKs, test APK, debug/release lint and benchmark compilation passed. Lint: zero errors; 26 debug / 36 release warnings. |
| Baseline connected run | Interrupted after 53 completed tests. A null insets lookup failed in `AppUiSmokeTest.wideRailAndLargeTextRemainReachable`; the emulator then disconnected during `AppWindowPresentationTest`. |
| Final JVM/build/static | 362 JVM tests passed. Debug, release, instrumentation and benchmark APKs build; debug/release lint report zero errors, 19 / 18 warnings. Current benchmark sources compile without historical adapters. |
| Local fixture contracts | Chat, chapters, content links, avatar and community checks passed; original contract assertions retained. Missing-media repair was verified with real FFmpeg outputs. |
| Workflow/documentation regressions | 19 passed on the final scripts and documentation checker. |
| Broad cleanup connected run | 369 tests: 362 passed, four failed, three skipped. This used the first cleanup APK; its exact APK/source evidence is preserved separately from final-source focused checks. |
| Baseline focused comparison | Original APK: same header and system-PiP-return failures. Clip stale-read and guest SponsorBlock cases passed. |
| Final header check | Original pixel assertions pass across light/dark, banner/no-banner and thin mode after explicitly using the theme surface. |
| Consolidated final-source device checks | 52 executed and passed after readiness fixes: app, queues, incoming/share links, settings, downloads, avatars, clip stale-read and guest/account SponsorBlock editing. Accepted conversion remains ignored. |
| Download process restart | Both separately gated phases passed: transfers retain their IDs, a new process restores offline resume, interrupted export is recovered and deleting one entry preserves the other. |
| Current benchmark control diagnostic | `--record --journey settingsSaving --label cleanup-current` passed ordinary setup, failed-save retry and speed persistence; source/APK/gesture/video provenance checks pass. This is not a timing run. |
| Signing, documentation and final consistency | Final release certificate matches the existing release. All 18 Markdown file/heading links, source fingerprints/catalogs, Python/XML parsers, shell syntax, sample Compose configuration and diff checks pass. |

The four broad-run failures were the channel-header color assertion, system PiP
expansion timeout, clip stale-read playback readiness and guest SponsorBlock popup.
Both latter cases pass individually on baseline and cleanup APKs. The first
consolidated group also reproduced the guest SponsorBlock failure: it waited for
a row to exist, then clicked during channel loading. It now waits for an enabled row and
its popup without weakening persistence, request-isolation or reset assertions.
The clip readiness failure has not recurred in either consolidated focused group.
The corrected guest SponsorBlock test passes with the same preceding suites.
The header failure was reproduced on the original APK and fixed with an explicit surface background plus viewport synchronization.
The PiP check still times out finding the OS expand action after successfully
entering PiP; it fails identically on the original APK. Its assertions remain.
Accessibility-service flags now restore in `finally` even after this failure.

The three broad-run skips are the two download-restart phases (enabled by their
separate runner) and the pre-existing accepted-conversion Ignore. Final focused
checks do not retroactively turn the broad result into a fully passing suite.

The baseline hardware-rendered emulator crashed. It reconnected after a cold
start using `-gpu swangle -feature -Vulkan -no-snapshot-load -no-snapshot-save`,
preserving its data/snapshots. Insets waits now require a non-null visible IME
instead of dereferencing a temporarily detached view; their visibility assertion
is unchanged. Preview private state was backed up before fixture tests and
restored afterward: all four original private files have identical byte hashes.
The test-created external files were removed after evidence collection. Original
1344×2992 display, density 480, font/animation scales 1.0 and dark system mode were
restored; the emulator remains connected using the stable software renderer.
The final preview APK remains installed; release app/data and signing keys were
not modified.

Raw logs, baseline APKs/XML, current build results and workflow output are under
ignored `.tools/cleanup/`; private emulator backup is not committed or reproduced.
Final debug/instrumentation builds are recorded in `final-debug-build.log`,
unit/release/lint/benchmark builds in `final-build.log`, the 52-device run in
`final-focused-device.log`, and both persistence phases in
`download-restart-final.log`. Benchmark control evidence is under ignored
`.tools/ui-performance/cleanup-current/`. Original and final APK/source hashes
allow comparison without treating the current working tree as the baseline.

A complete final-source connected suite was not repeated after the broad run;
the focused suites exercise the changed paths. Physical-device/Android 13
acceptance, accepted video conversion, all eighteen performance timing journeys
and production/server rollout were not performed. There is no physical device;
the conversion test retains its known emulator-crash Ignore, and timing/production
acceptance is outside this local cleanup. Build/test success does not close those
gates.

## Deliberately retained risks and follow-up

1. Verify accepted video conversion on a capable physical device/fixed emulator.
   The existing gfxstream-crash Ignore remains; conversion consent, errors and
   unchanged export have separate tests.
2. Diagnose the inherited system PiP expansion timeout. Check complete-suite
   ordering around clip playback readiness; repeat the complete suite after fixes.
3. Measure the eighteen current performance journeys on the intended device.
   Emulator/software-renderer diagnostics cannot establish physical-device smoothness.
4. Keep account/profile migrations and older-server response fallbacks. They protect
   existing user data and supported instance compatibility; age alone is not
   evidence for removal.
5. Consider splitting the large AppViewModel and Compose shell only with a specific
   ownership/lifecycle improvement and regression coverage. A wholesale rewrite
   would be disproportionate to this cleanup.
6. Review dependency updates separately. Remaining version/KTX suggestions are
   advisory; the exported media service is intentional and checks trusted controller
   access plus app command context/package/token boundaries.

