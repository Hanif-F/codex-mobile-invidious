# Focused implementation verification

## Mobivious 0.13.0 release — 22 October 2026

Application source: `7cf9c7f` plus the version-only change to **0.13.0**, code
**30**. This release adds Android client support for required sign-in and instance
verification. Administration remains on the website; no server deployment or
production policy changes are included. The release preparation also scopes the
optimized smoke runner's Connect confirmation to its dialog and waits for stable
bounds, preventing a click on the underlying button during the opening animation.

- Reused the completed instance-access checks below without rerunning them:
  **445 JVM tests**, the subsequent **16 affected JVM cases**, **29 focused Android
  cases**, **32 Python checks**, and debug lint (**0 errors, 36 warnings**).
- Ran `./gradlew :app:assembleRelease :app:lintRelease --console=plain` from
  `android/`, retaining incremental outputs: passed. Release lint reported
  **0 errors, 36 warnings**. The existing release script's signing checks and
  artifact/R8 archive logic were retained; its already-completed JVM task was
  omitted from this invocation at the user's request. No release script change
  was committed and the existing signing key was preserved.
- The new optimized ordinary-speed watch smoke passed on Android 16
  `emulator-5554` (Pixel 8 Pro), covering fixture connection, account setup,
  navigation and playing media. Command: `ANDROID_SERIAL=emulator-5554
  scripts/test-ui-performance.sh --record --label release-0_13_0-watch-fixed
  --journey watch`. Recording/provenance checks passed with no source drift.
  The initial attempt failed in runner setup because it clicked the background
  Connect button while the confirmation dialog was opening; only this failed
  check was rerun after the test-helper correction. This was not a timing
  benchmark. The installed release and its data were preserved.
- Documentation inventory/fingerprint/link and Git whitespace checks passed.
- APK identity/version/SDK, checksum, uncompressed DEX/native payloads and matching
  R8 mapping/archive checksums passed. The signing certificate matches 0.12.5;
  the previous local APK matches GitHub's published asset digest. The packaged
  protected-download service is private and declares the dataSync foreground type.
- APK: **7,302,896 bytes**; uncompressed DEX: **6,284,908 bytes**. The prior release
  was 7,220,771 APK bytes and 6,210,516 DEX bytes (+1.1373% and +1.1978%). These
  different-feature release sizes are descriptive, not a controlled performance
  comparison. APK SHA-256:
  `976fd90c0c4a2d592bb492beeac88bd6c5809b18cb74f02e1e5bd551c69c9987`.
  Matching archive: `artifacts/r8/0.13.0/<APK-SHA256>/`.

Build/artifact evidence is retained under ignored `.tools/release-0.13.0/`.
Optimized smoke evidence is under `.tools/ui-performance/release-0_13_0-watch-fixed/`;
the failed setup attempt remains under `.tools/ui-performance/release-0_13_0-watch/`.
The detailed fixture and live-provider acceptance below remains valid and separate:
real Turnstile completion passed; real-account content access was not tested.
The complete device suite, exhaustive optimized access/download journeys and
physical-device performance were not rerun or claimed. The existing emulator
accepted-video-conversion limitation remains explicit.

## Mobivious 0.12.5 release — 21 October 2026

Application source: `47ce6db` plus the version-only change to **0.12.5**, code
**29**. This patch includes finger-tracking player resizing, YouTube HTTP/HTTPS
content-link registration and the Opening links setup screen. No backend update
or database migration is required for these fixes.

- `scripts/build-release.sh` passed: **434 JVM tests**, signed optimized release
  APK and release lint (**0 errors, 33 warnings**).
- The optimized ordinary-speed watch journey passed on Android 16
  `emulator-5554` (Pixel_8_Pro), including fixture connection, account setup,
  navigation and playing media. Recording validation passed with no source drift;
  this was a smoke check, not a timing benchmark. The release app/data were preserved.
- Documentation inventory/fingerprint/link and Git whitespace checks passed.
- Identity/version/SDK, APK checksum, uncompressed DEX/native payloads and matching
  R8 mapping/archive checksums passed. The signing certificate matches 0.12.4;
  the previous local APK matches GitHub's published asset digest. All eight
  YouTube domains are present in the packaged release manifest.
- APK: **7,220,771 bytes**, compared with 7,220,231 bytes
  for 0.12.4 (+0.0075%). Uncompressed DEX:
  **6,210,516 bytes**, previously 6,205,984 bytes.
- APK SHA-256:
  `6d62fe08fd6fb55ac2f87a5aad6cabe5c2dee2fccfa509034e9a9b3b1e010dd3`.
  Matching archive: `artifacts/r8/0.12.5/<APK-SHA256>/`.

Build/artifact evidence is retained under ignored `.tools/release-0.12.5/`.
Optimized runtime command: `ANDROID_SERIAL=emulator-5554
scripts/test-ui-performance.sh --record --label release-0_12_5-watch --journey watch`.
Its recording, source fingerprints, raw outputs and successful exit status are
under `.tools/ui-performance/release-0_12_5-watch/`.
The existing 18 focused debug emulator cases and Android default-routing check
below remain the feature's detailed runtime evidence; production code is unchanged
apart from the version bump. The complete device suite was not rerun. Android
user selection is still required for YouTube domains, and explicitly targeted
YouTube intents can bypass the default. Physical-device smoothness remains
outside emulator acceptance.

## Testing workflow optimization — 21 October 2026

Source: Android `b56217b` plus the existing working-tree changes and this testing
workflow update. The production app/server sources and release workflow receive
no changes from this update. Existing runtime acceptance below remains separate.

- All **434 JVM tests** passed with zero failures, errors or skips; the set of
  executed class/method names is identical to the saved pre-change report.
  Summed JUnit execution time fell from **25.852s to 13.466s**. The two AI-filter
  timer cases fell from **12.243s to 0.040s** combined while checking exact
  two-second/five-second boundaries, background request survival and no further
  automatic retry. Other real-dispatcher concurrency tests remain in place.
- The focused 12-case AI-filter run passed in **2.81s wall time**, including
  incremental test compilation. The subsequent full JVM command took **15.23s
  wall time**, with compilation up to date. The pre-change evidence is an existing
  JUnit report, so these figures establish execution-time savings rather than a
  controlled comparison of total build or development time.
- All **29 Python documentation/workflow tests** passed in **3.231s**, including
  five new runner tests for legacy/default tasks, device-only selection, selector
  and option forwarding, missing/blank selectors before setup, help without a
  device, and failure-status/owned-resource cleanup. Fake Gradle/ADB commands and
  disposable local fixtures were used; no emulator runtime check was needed for
  this script/test-only update. Debug lint was not requested.
- Shell syntax, documentation inventory/fingerprints/local links and generated
  catalogs passed. The contributor policy now selects checks by changed behavior,
  with full JVM/debug lint once at production-code task completion. Focused device
  commands use `--device-only`; release checks and known full-device-suite
  failures/ignored cases remain explicit.

Commands: from `android/`, with `JAVA_HOME=/opt/android-studio/jbr` and
`ANDROID_HOME=$HOME/Android/Sdk`, run `./gradlew :app:testDebugUnitTest --tests
net.wingress.mobivious.AiFilterTest --offline --console=plain`, then
`./gradlew :app:testDebugUnitTest --offline --console=plain`. From the repository
root, run `python3 -m unittest discover -s scripts/tests -v`,
`bash -n scripts/test-android.sh`, `scripts/test-android.sh --help`,
`python3 scripts/check-documentation.py` and `git diff --check`.

Evidence: ignored `.tools/testing-optimization/` contains `jvm-before.json`,
`jvm-focused.json`, `jvm-after.json`, Gradle logs and wall-time files, plus
`python-verified.log` and its wall-time file. Initial restricted-sandbox attempts
could not write Gradle cache locks or bind fixture sockets; successful runs used
the required local cache/socket access. The failed Python attempt remains in
`python.log` and is separate from the successful acceptance run.

## Finger-tracking player resize and Android YouTube links — 21 October 2026

Source: Android `b56217b` plus this working-tree change. Device: Android 16
`emulator-5554`, Pixel_8_Pro; isolated `net.wingress.mobivious.debug` preview.
The release package and its stored data were preserved.

- **434 JVM tests** passed, including variable height ranges, exact consumed
  distance, top-region crossings, reversals, fling bounds, zero range and
  normalized/legacy state restoration.
- **18 focused emulator tests** passed: five resize presentation cases, four
  link/manifest/settings presentation cases, eight content-link journeys and the
  Settings navigation/recreation case. Active drags changed both player height
  and metadata position by 24dp for a 24dp move and 12dp for a reversed 12dp move,
  within 1dp layout rounding. Held touches stayed stationary, and flings crossed
  from resizing into list scrolling and back without an added animation.
- Installed-manifest tests covered all eight YouTube hosts over HTTP/HTTPS,
  content aliases, short links and nocookie embeds; unrelated pages, hosts and
  schemes were rejected. Cold/warm VIEW delivery preserved precise timestamps;
  activity recreation did not replay the launch link.
- A separate OS check temporarily disabled competing YouTube/YouTube Music
  handling and selected all eight debug domains. Default-only resolution selected
  debug MainActivity for every host, and an unrestricted browsable VIEW opened it
  cold. All four packages' original link-handling flags and selected domains were
  restored in a finally block and compared afterward.
- Debug app/test APK builds, debug lint (**0 errors, 17 warnings**), documentation
  inventory/fingerprint/link checks and Git whitespace checks passed. The new
  Opening links page and its actual Android Open by default destination were
  visually inspected on the emulator.

Commands: `JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
:app:lintDebug --offline --console=plain` from `android/`; the focused
`ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --offline` command in
[Testing](docs/TESTING.md#scroll-tracking-and-android-link-routing), additionally
selecting `SettingsSmokeTest#openingLinksIsReachableAndSurvivesRecreation`.

Evidence: ignored `.tools/scroll-links-build.log`,
`.tools/scroll-links-device-final.log`, `.tools/scroll-links-results/final/`,
`.tools/scroll-links-routing.json`, `.tools/check-scroll-links-routing.py` and
`.tools/scroll-links-preview/`. The initial 17-case run had one test-only failure:
its Settings helper looked for an offscreen lazy row before scrolling it into
composition; the corrected final run and added fling case passed. Initial results
remain in `.tools/scroll-links-results/initial/`. The first shell resolution probe
omitted `MATCH_DEFAULT_ONLY` and returned a chooser; the corrected query and actual
unrestricted launch both passed.

No backend, preference migration, dependency or permission change is required.
This establishes emulator behavior, not physical-device smoothness, every browser's
internal navigation, or interception of intents explicitly addressed to YouTube.
The complete device suite and optimized release variant were not rerun.

## Mobivious 0.12.4 core release — 17 October 2026

Application source: `8193bdd` plus the version-only change to **0.12.4**, code **28**.
The requested core workflow passed 433 JVM tests, the signed optimized release
build and release lint (0 errors, 33 warnings). No new emulator or optimized
runtime journey was run; the feature's 21 focused emulator cases and final RTL/
clipboard presentation checks below remain its runtime evidence.

The APK identity/version, checksum, uncompressed DEX/native payloads and matching
R8 mapping/archive checksums were verified. Its signing certificate matches the
published 0.12.3 APK; that local prior APK matched GitHub's asset digest.
APK size: 7,220,231 bytes. APK SHA-256:
`801beea1462e75dae0b8716f771f8bc623bc1e02775577ba41c8affaced2b7d9`.
The archive is retained under `artifacts/r8/0.12.4/<APK-SHA256>/`.
Documentation inventory/fingerprint/link and Git whitespace checks passed.
Evidence: ignored `.tools/release-0.12.4-build.log` and
`.tools/release-0.12.4-verification.json`. No backend deployment is required for
these player changes. Existing broader runtime limitations remain unchanged.

## Playback statistics and diagnostic copying — 17 October 2026

Source: Android `24b9f58` plus the statistics implementation and existing working
tree changes. Debug package: `net.wingress.mobivious.debug`. Device: Android 16
`emulator-5554`, Pixel_8_Pro. The release-key package was not changed.

- **433 JVM tests** passed, including eight statistics bookkeeping/export tests.
- **21 focused emulator cases** passed: nine new statistics cases, eight existing
  automatic-recovery cases, two exact-track/audio refresh/reconnection cases, the
  existing manual-buffer-refresh case and the existing PiP/wake-ownership case.
  The final numeric-direction change also received a separate two-case statistics
  presentation rerun with large-text light/dark RTL and clipboard-failure recovery.
- Statistics acceptance covers More placement and On/Off state, actual decoder
  metrics, fresh JSON copying without metadata requests, unchanged paused
  playback, full-screen and recreation, rapid Back, mini-player/settings/background/
  PiP suspension and restoration, source refresh and queue changes, DASH/HLS/
  progressive/audio-only/live/downloaded playback, graphs and pinned controls.
- Debug app/test APK builds and debug lint passed. Lint retains **0 errors and
  33 warnings**; there is no new baseline or suppression file.
- All **24 Python documentation/workflow tests**, documentation inventory/
  fingerprint/link checks and Git whitespace checks passed. The reviewed catalog
  contains **291 maintained files and 20 documents**.

Evidence is in ignored `.tools/playback-statistics-final.log`,
`.tools/playback-statistics-rtl-final.log`, `.tools/playback-statistics-python-final.log`
and `.tools/playback-statistics-results/`. Visually reviewed captures are under
`.tools/playback-statistics-preview/`; device originals use
`/data/local/tmp/mobivious-ui/statistics-*.png`.

The overlay observes existing service playback; no backend, dependency, permission
or preference migration is required. Media-load metrics are Media3 callbacks,
not a trace of all HTTP traffic. Progressive transfer bytes/speed can remain
pending until a load finishes. Missing and offline-only fields are explicitly
unavailable. Fixtures establish Android behavior rather than production bandwidth,
upstream availability or physical-device decoder performance. The complete device
suite and optimized release variant were not rerun for this feature.

## Mobivious 0.12.3 release — 16 October 2026

Application source: `76b5a09` plus the version-only Android configuration change
to **0.12.3 / code 27**, published as `v0.12.3`. Release documentation was updated
after verification; the verified application sources remained unchanged.

- `scripts/build-release.sh` passed: 418 JVM/API tests, no failures/errors/skips,
  signed optimized release APK, and release lint (0 errors, 32 warnings).
- `scripts/test-ui-performance.sh --record --label release-0_12_3-watch --journey watch`
  passed its ordinary-speed optimized watch journey on Android 16
  `emulator-5554` (Pixel_8_Pro), including fixture connection/sign-in and playing
  media. Source drift was empty. This was a runtime smoke check, not a performance
  measurement.
- Direct ordinary-UI checks on `0.12.3-benchmark` passed: all five archived
  annotation styles, individual dismissal, captions over spherical video,
  accessible directional controls and recentering, independently generated EAC
  video, a new occurrence's front viewpoint, and viewpoint retention after
  mini-player and background restoration. System screenshot pixel samples
  confirmed green front and blue right directions after decoding and rendering.
- Optimized search UI checks passed Movies, Medium duration, simultaneous 4K/360°
  filters and individual chip removal. Fixture logs confirmed canonical `type`,
  `duration` and comma-separated `features` requests.
- Package `net.wingress.mobivious`, Android 13 minimum, version/name, uncompressed
  DEX/native entries, APK checksum and retained R8 mapping/checksums were verified.
  Signing certificate SHA-256 is
  `5673702abf411cf4aa9b85e2fe952a658b0611e813c9689603b47c475204ff87`, matching
  the published 0.12.2 APK. The prior local APK also matched GitHub's asset digest.
- APK: 7,171,083 bytes (6.84 MiB), +1.16% from 0.12.2's 7,089,159 bytes.
  Uncompressed DEX: 6,160,680 bytes, previously 6,071,256 bytes.
  APK SHA-256:
  `50c992fd914733adea250d66b74de2a72ca7217dc01e64577589db0e20176f70`.
- All 24 Python documentation/workflow regression tests and documentation
  inventory/fingerprint/link checks passed. Git whitespace checks passed.

Raw release evidence is in ignored `.tools/release-0.12.3/`; the recorded watch
journey is in `.tools/ui-performance/release-0_12_3-watch/`. The release-key app and
its data were preserved; UI checks used the isolated optimized benchmark package.
The earlier 25 focused feature scenarios and sibling server checks below cover
the feature implementation before this version-only bump. The full device suite
was not repeated for this release. Physical-device motion accuracy and the
existing emulator video-conversion limitation remain unverified. The backend was
recompiling during this run, so these release checks used local fixtures and do
not establish production server rollout or archive availability.

## Search filters, archived annotations and phone 360° viewing — 16 October 2026

Source bases: Android `f313166`, sibling Invidious `be5609ad`, with the local
feature changes in both working trees. Debug app ID:
`net.wingress.mobivious.debug`. Device: Android 16 `emulator-5554`, Pixel_8_Pro.
These results cover this request; older capability records remain in the feature
checklist and documentation.

The implementation completes the remaining search type/duration/feature controls
and adds archived annotation overlays plus monoscopic equirectangular and YouTube
EAC phone viewing. The server additions are boolean native preference patches
and nullable top-level projection metadata, automatically included by download
catalogs. The archive route also propagates direct responses without assuming a
redirect header, preserving missing-archive statuses. No database migration or renewed sign-in is required.

### Validation

- All 418 Android JVM/API tests passed, including seven new annotation/viewing
  and expanded-search tests.
- Debug app and instrumentation APKs built successfully. Debug lint passed.
- Eight player scenarios passed: independently generated EAC/spherical decoded
  direction pixels; drag/recenter/captions; fullscreen/recreation and occurrence
  reset; annotation style/dismiss/link/audio/mini behavior; error/404/retry/late
  responses; guest/account defaults and link precedence; absolute clip timing;
  mini/PiP/background and offline projection metadata.
- Seventeen search scenarios passed across `DiscoverySearchSmokeTest` and
  `SearchExperienceSmokeTest`, including the two new all-filter/restoration and
  stale-feature/pagination journeys. The final search run also repeated the
  annotation rendering/dismissal journey after close-button layout review.
- Sibling checks passed 77 Spectator examples and 13 native preference/archive specs
  with no failures, using native preference, direct/redirect archive response and
  YouTube/Invidious search filter suites. `crystal build src/invidious.cr --no-codegen` passed with GUI sources
  included; the final repeat used `-D skip_videojs_download` to reuse existing
  player assets. This is source/type validation, not a running server deployment.
- Fixture scripts passed Python compilation. All 24 Python documentation/workflow
  regression tests passed with the localhost sockets required by fixture tests.
  Documentation inventory, reviewed fingerprints and local links passed for 282
  maintained files and 20 documents.

Repeat native checks with the runner command in [Testing](docs/TESTING.md#complete-search-filters-archived-annotations-and-phone-360-viewing).
Logs and reviewed screenshots are retained in ignored `.tools/feature-04-21/`.
The runner owns and removes its local fixture process and ADB reverse mapping.
Captures are on the emulator under `/data/local/tmp/mobivious-ui/360-*.png` and
`annotations-styles.png`. Decoded pixels use composed system screenshots because
Compose view-only captures omit the GL surface. Visual review confirmed readable
annotation shapes and native captions over the projected video.

### Corrections and emulator recovery

The first connected attempt lost the emulator before returning test results.
Development/testing paused for recovery. A SwiftShader restart crashed during
boot with exit 139; the same AVD then booted successfully using host graphics
without a desktop window and completed the focused checks. The initial crash's
root cause has not been established.

Early failed checks found and corrected the EAC atlas face orientation, an
inappropriate view-only screenshot assertion, annotation/GL suspension depending
on background recomposition, and Reset being pushed below the expanded filter
list. Final search checks passed after moving Reset above the controls.

### Acceptance boundaries

Physical-device motion accuracy remains unverified; unit checks cover sensor
camera-direction mapping and camera bounds. Archive availability and legacy Shows
results depend on the selected instance and upstream data. Fixture accounts and
synthetic media were used; no production account settings were changed. The
sibling server update has not been deployed. Headset stereo is outside the
selected phone-viewing scope.


## Instance access support — 22 October 2026

Implemented Android client compatibility with server commits `446baa0548bc49ca4c10c99b43bc096ba092823c`
and `7325245318dd1fd0770499d544f599b821d7a2b0`. Administration remains on the site;
the sibling server checkout and production policy were unchanged. The app version
remains 0.12.5 (29). The debug app/test APKs were built and installed on
`emulator-5554` (Pixel 8 Pro AVD, Android 16); the installed release and its data
were not replaced. The debug app was left on `https://invidious.wingress.net`.

The change provides encrypted per-origin verification passes, native required
sign-in, a main-frame/origin-restricted verification WebView, credential stripping
on external redirects, context-scoped caches and typed access recovery throughout
metadata, images, captions, playback, clip previews and downloads. Playback keeps
its position/selections and requires explicit Retry. Gate-driven same-identity
sign-in can retain the stopped queue; different identities close it. Protected
transfers use a foreground OkHttp service; legacy credential-free transfers retain
DownloadManager. Download journals add an optional `managed` flag, without storing
credentials. Process death leaves unfinished managed work retryable and preserves
completed files and the encrypted pass.

### Checks and results

- Full JVM suite: **445 passed, 0 skipped/failed**, using
  `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain`
  from `android/`. Subsequent access-context/lifecycle refinements reran the
  affected **16 JVM cases** with `--tests 'net.wingress.mobivious.InstanceAccessTest'`
  and `--tests 'net.wingress.mobivious.BufferRecoveryTest'`, plus debug lint.
- Final debug lint: **0 errors, 36 warnings**. One AndroidX WebKit diagnostic flags
  the `WebViewClient` superclass constructor despite the implemented
  `onRenderProcessGone` override; actual renderer termination/retry passed.
- **29 distinct Android cases passed**, with no ignored cases counted as passes:
  11 `InstanceAccessSmokeTest` cases, 11 existing account/navigation/playback/
  captions/previews/images/download regression cases, two existing automatic
  buffer-recovery cases, one live-provider case and four force-stop phases.
  The first integration run passed 19/19; the final access/recovery/provider run
  passed 14/14. The two dialog-targeted renderer/subframe cases also passed a
  focused 2/2 run. Previously failing dialog-focus/PiP-animation test assumptions
  were corrected and their affected cases passed subsequently.
- `ANDROID_SERIAL=emulator-5554 scripts/test-download-restart.sh`: **four phases
  passed** across real app force-stops. Legacy transfer IDs survived. Protected
  unfinished assets restored as failed/retryable, retained their encrypted pass,
  completed after Retry and played offline without instance access.
- `python3 -m unittest discover -s scripts/tests -v`: **32 passed**, including three
  new fixture HTTP contract checks. `bash -n scripts/test-download-restart.sh`
  passed. Documentation inventory/fingerprint/link checks passed for **301
  maintained files and 21 documents**.
- Deployed discovery returned HTTP 200 with verification enabled; anonymous content
  returned HTTP 401 `login_required`. The real Managed Turnstile page issued native
  passes which discovery confirmed as verified. The final live run completed
  after the page's normal Retry and visible checkbox interaction following a
  provider load failure. No real account credentials or admin changes were used.

The final connected command was:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.InstanceAccessSmokeTest,net.wingress.mobivious.BufferRecoverySmokeTest#initialFailureAutomaticallyRecoversWithoutMetadataRefetch,net.wingress.mobivious.BufferRecoverySmokeTest#automaticRefreshPreservesExactTracksCaptionsSpeedPositionAndAudioOnly,net.wingress.mobivious.LiveInstanceAccessSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveInstanceAccess=true \
  -Pandroid.testInstrumentationRunnerArguments.liveKeepInstance=true
```

The existing regression selectors in the 19-case integration run were:

- `net.wingress.mobivious.AccountNavigationSmokeTest#contextualSignupReturnsToLibraryAndCredentialChangesRetainSignIn`
- `net.wingress.mobivious.AccountNavigationSmokeTest#fourRootsGuestSettingsAndSearchCancellationAndReturn`
- `net.wingress.mobivious.AppSmokeTest#accountBrowsingAndServicePlayback`
- `net.wingress.mobivious.AppSmokeTest#refreshPreservesPausedAndPlayingSettings`
- `net.wingress.mobivious.AvatarsSmokeTest#browsingChannelHeaderAndWatchReuseImagesWithoutExtraMetadataLookups`
- `net.wingress.mobivious.CaptionsSmokeTest#clipPreviewInheritsSelectedLanguageAndUsesTheSharedStyle`
- `net.wingress.mobivious.CaptionsSmokeTest#networkErrorsAreReportedForCaptionsAndRetryRecovers`
- `net.wingress.mobivious.DownloadsSmokeTest#failuresRetryWithoutRepeatingCompletedTracksAndDeleteIsIndependent`
- `net.wingress.mobivious.DownloadsSmokeTest#transfersSurviveRecreationCancellationAndProfileChanges`
- `net.wingress.mobivious.SeekPreviewsSmokeTest#draggingShowsFullPlayerHighestResolutionAndRestoresPlayingOrPausedIntent`
- `net.wingress.mobivious.StreamControlsSmokeTest#exactTracksMenusRefreshRetryAndReconnection`

All those connected selections used `scripts/test-android.sh --device-only` with
an explicit `-Pandroid.testInstrumentationRunnerArguments.class` selector.
Evidence is in ignored `.tools/access-*.log`, `.tools/access-results/`,
`.tools/download-verification/` and `.tools/access-live-complete.png`.

This is focused acceptance. The complete connected suite was not run and its
existing failures/ignored cases remain unaccepted, including the emulator-crashing
accepted video-conversion case. Production account-gated content was verified
with disposable fixtures; real-account content access was not tested. No release,
optimized-runtime or performance acceptance is claimed.
