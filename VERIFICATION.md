# Focused implementation verification

## Patch release 0.13.5 — 4 November 2026

Bumped `versionName` to **0.13.5** and `versionCode` to **35**, based on `3e2b8b4`.
The release includes the upright-player layout fix and the separate verification
dialogs/account-settings recovery in `237392d`. Only version metadata changed in
app sources after the completed implementation checks below.

New release checks:

- `scripts/build-release.sh`: **passed**, running the unchanged standard
  `:app:testDebugUnitTest :app:assembleRelease :app:lintRelease` tasks.
  **497 JVM tests passed, 0 failed/skipped**; release lint **0 errors, 36 warnings**.
- From `android/`, `JAVA_HOME=/opt/android-studio/jbr
  ANDROID_HOME=/home/haha.m.lady/Android/Sdk ./gradlew :app:lintDebug --console=plain
  --offline`: **passed**, **0 errors, 24 warnings**.
- Build-tools 36.0.0 `apksigner verify --verbose --print-certs`, `aapt2 dump badging`
  and `zipalign -c -P 16 -v 4`: **passed**. Package `net.wingress.mobivious`, version
  **0.13.5**, code **35**, min SDK **33**, target SDK **37**. The signing certificate
  matches the local 0.13.4 APK, whose digest matches GitHub's published asset.
- `sha256sum -c Mobivious-0.13.5.apk.sha256` and
  `python3 artifacts/release-0.13.5/verify-packaging.py`: **passed**. DEX is
  uncompressed; the archived mapping matches the current release mapping and every
  archived `SHA256SUMS` entry verifies.
- `ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --label
  release-0_13_5-watch --record --journey watch`: **passed**, **1 case, 0 skipped**,
  using the isolated optimized benchmark package at density **560**. Its normal-speed
  video was validated and visually inspected; time sits above seeking and playback
  is centered. Density **480** was restored. This is an optimized UI check, with no
  timing benchmark or physical-device performance claim.
- `ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --label
  release-0_13_5-navigation --record --journey navigation`: **failed**, interrupted
  by Gboard's microphone-permission dialog before the next video could be found.
  The `release-0_13_5-navigation-retry` run also **failed** at that lookup while
  Gboard's voice-typing onboarding covered the search screen. No microphone access
  was granted. The retry temporarily set `show_ime_with_hard_keyboard=0`, which did
  not prevent the floating voice overlay; the prior absent setting was restored.
  Navigation/miniplayer acceptance remains **pending**, with both recordings/logs
  preserved. App and test source were not changed to bypass these failures.
- Documentation inventory/fingerprint/link checks and `git diff --check`: **passed**.

APK: **7,352,072 bytes**. SHA-256:
`257cc92e63f72decdcaf9594fd3e85660ff483255d15b14b4f1f4a72fc7390fa`.
The immutable R8 archive is
`artifacts/r8/0.13.5/257cc92e63f72decdcaf9594fd3e85660ff483255d15b14b4f1f4a72fc7390fa/`.
Raw build/packaging evidence is in ignored `artifacts/release-build-0.13.5.log`
and `artifacts/release-0.13.5/`; release notes are in
`artifacts/release-notes-0.13.5.md`.
Optimized journey evidence is under `.tools/ui-performance/release-0_13_5-*`.
The APK and `.apk.sha256` are the assets for
[v0.13.5](https://github.com/Hanif-F/codex-mobile-invidious/releases/tag/v0.13.5).

The completed **18 distinct player device cases**, two narrow-density reruns and
**39 verification/account-recovery fixture cases** below remain implementation
evidence. Version-only packaging does not rerun those debug journeys. The separate
Cloudflare flow requires the matching server `/verify?client=native-cloudflare`
handoff page; this APK does not deploy it. Physical-phone and full connected-suite
acceptance remain outside these results; existing ignored cases remain unchanged.

## Consistent upright player controls — 4 November 2026

Short upright players now keep time/fullscreen above full-width seeking and show
an ellipsized chapter title in the header. Play/Pause remains exactly centered.
Regular footer selection also requires room for readable chapter text, covering
tall/narrow pictures at large font scales. Individual control bounds determine
fit; compact SponsorBlock prompts clear those controls and mirror with time in RTL.
The tiny-player popup, complete accessibility labels and auto-hiding remain.

Final Android 16/API 36 `emulator-5554` checks:

- **18 distinct device cases passed, 0 failed/skipped**, including both complete
  presentation classes and the four chapter/preview journeys below. Presentation
  coverage includes 320×180, 360×203, 390×220 and 432×243dp, large text/RTL,
  tall/narrow readable chapters, tiny-popup transitions, keyboard/click actions,
  chapter position updates and SponsorBlock clearance.
- The two selected `ChaptersSmokeTest` methods also **passed at density 560**,
  producing a 368dp-wide real watch player. The original density **480** was
  restored and confirmed. Compact/regular watch and fullscreen screenshots were
  inspected; time is above seeking, chapter text is visible and transport centered.
- `JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/haha.m.lady/Android/Sdk
  android/gradlew -p android :app:testDebugUnitTest :app:lintDebug --console=plain
  --offline`: **497 JVM tests passed, 0 failed/skipped**; debug lint passed with
  **0 errors, 24 existing warnings**.
- `python3 scripts/check-documentation.py --refresh`,
  `python3 scripts/check-documentation.py` and `git diff --check`: **passed**.

The final 18-case command, from the repository root:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.PlayerControlsPresentationTest,net.wingress.mobivious.PlayerFeedbackPresentationTest,net.wingress.mobivious.ChaptersSmokeTest#chapterSelectionNeedsNoExtraRequestsAndScrubbingPreservesPlayback,net.wingress.mobivious.ChaptersSmokeTest#fullWidthTimelineAndCenteredPlaybackStayInPlaceAcrossFullscreen,net.wingress.mobivious.SeekPreviewsSmokeTest#draggingShowsFullPlayerHighestResolutionAndRestoresPlayingOrPausedIntent,net.wingress.mobivious.SeekPreviewsSmokeTest#dragCancellationAndFullscreenTransitionClearPreviewsWithoutCommitting
```

The narrow rerun used that runner with only the two `ChaptersSmokeTest` selectors,
between `adb -s emulator-5554 shell wm density 560` and restoration of the prior
density using an EXIT/signal trap. Connected tasks built their required debug APKs.
No release artifact, physical-phone run or full connected-suite acceptance is
claimed; existing unselected/ignored cases remain outside these focused results.
There are no server or API-contract changes.

Initial presentation attempts failed on test-harness details: a decimal screenshot
filename rejected by the capture helper and a fullscreen assertion using the
embedded Minimize label. Both were corrected; the final complete selection passed.
Logs use `.tools/upright-controls-*.log`; preserved initial/final XML, source diff,
base commit and screenshots are under `.tools/upright-controls-results/`.

## Separate verification flows and settings recovery — 4 November 2026

Cloudflare and backend Turnstile now have separately owned dialogs. The server
adds `/verify?client=native-cloudflare`, a localized, private/no-store handoff
page that does not read Turnstile policy/readiness or emit its widget/completion.
Accepted native clearance switches to the backend issue only when required;
recovery waits for both gates. A rejected cookie is tried once, while a newer
browser candidate may recover without reloading. Cancellation also survives a
delayed rotation exposing the remaining backend gate.

Full recovery reloads shared account settings before retrying browsing, configures
channel masking, refreshes blocked/watched/subscription/playlist reads and preserves
the current destination and failed pagination. A settings failure retains safe
masking and offers Retry on the affected browse list. Existing credential formats,
origin/user-agent/expiry fences and explicit write/download retries are preserved.

Final checks on Android 16 `emulator-5554`, WebView 155.0.8059.43:

- `JAVA_HOME=/opt/android-studio/jbr android/gradlew -p android :app:testDebugUnitTest --tests 'net.wingress.mobivious.CloudflareAccessTest' --tests 'net.wingress.mobivious.InstanceAccessTest' --console=plain --offline`: **35 focused tests passed**.
- After the final production edit, `JAVA_HOME=/opt/android-studio/jbr android/gradlew -p android :app:testDebugUnitTest :app:lintDebug --console=plain --offline`: **497 JVM tests passed, 0 skipped**; debug lint **0 errors, 24 warnings**. After tightening the live screenshot assertion, only the affected `:app:lintDebug` check was repeated and passed.
- All device selections used `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=...` with explicit class/method selectors. The initial six selected cases passed. The next **31 passed**: nine remaining Cloudflare journeys, all 20 `InstanceAccessSmokeTest` cases, and `ChannelSpoilersSmokeTest#accountPreferenceReadFailureKeepsSafePlaceholdersAndCanRetry` / `#accountSparseSaveFailureRetryAndResetPreserveOtherRules`. The Gradle build and XML report completed successfully; that wrapper received SIGTERM afterward during teardown.
- After the cancellation change and explicit History artwork fixture, **nine affected device cases passed**: `CloudflareAccessSmokeTest#cancelledCloudflareStaysDismissedWhenRotationRevealsBackendVerification`, `#lateRotationOpensTheSeparateBackendDialog`, `#cancellationSurvivesRecreationAndServerChangeDiscardsThePrompt`, all four initial-account-settings recovery cases, and `InstanceAccessSmokeTest#cancelledAndRecreatedVerificationCanBeRetried` / `#cancelledVerificationStaysDismissedAcrossBackgroundAndPipUntilExplicitRetry`.
- Final review separated sign-in dismissal from verification cancellation. **Seven affected device cases passed**: the new `CloudflareAccessSmokeTest#choosingRequiredSignInDoesNotSuppressALaterCloudflareChallenge`, existing native sign-in/Cloudflare and required-login/registration/backend-verification journeys, and the four affected cancellation/recreation/background/PiP cases. This is **39 distinct fixture device cases**, all passing, with no selected skips. Saved XML inventories and exact reproduction selectors are under ignored `.tools/verification-results/` and `device-reproduction-commands.txt` there.
- `python3 -m unittest discover -s scripts/tests -p test_instance_access_fixture.py`: **9 passed**.
- In the sibling server, `crystal run tests/frontend/render_fixtures.cr -Dskip_videojs_download` and `crystal run tests/frontend/render_recovery_fixtures.cr -Dapi_only -Dskip_videojs_download`: **passed**. From its `tests/frontend`, `node --test verification-surfaces.test.cjs bot-protection.test.cjs`: **28 passed**, covering Chromium/Firefox, ordinary/API-only pages, disabled/unconfigured backend policy, localization, no-JavaScript rendering and retained backend contracts.
- `BOT_PROTECTION_TEST_DATABASE_URL=postgres://postgres@127.0.0.1:55435/invidious_bot_protection_test crystal run tests/database/bot_protection.cr -Dskip_videojs_download`, then the same command with `-Dapi_only`: **both passed**, using a newly created disposable PostgreSQL 18 container and empty schema for each run. The container was removed afterward; no production database or policy was changed.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveInstanceAccessSmokeTest#signedInPopularAndHistoryLoadWithConfirmedSettings -Pandroid.testInstrumentationRunnerArguments.liveSignedInAccess=true`: **1 passed** after asserting the rendered History count. Confirmed settings, populated Popular/History, retained account session and absence of pending channel settings were checked. Screenshots were inspected. The original encrypted session was restored after fixture tests; its temporary backup was removed. The updated debug app was left open on that account.
- Android documentation inventory/fingerprint/link checks, server generated-reference/link checks, focused Crystal formatting and `git diff --check`: **passed**.

The initial History regressions needed actual history/playlist fixture rows; the
server assertion was corrected to allow the ordinary preference cookie while
forbidding issuance of a backend pass. Those setup failures are resolved.

Screenshots and preserved XML reports are under ignored
`.tools/verification-results/`; server browser screenshots are in the sibling
`tests/frontend/artifacts/d7-verification/`. Controlled History/Popular thumbnail
recovery and intentional masking were visually inspected. The live check retains
the existing account and clearance; it does not force a fresh Cloudflare challenge.
The earlier guest live check failed with `login_required` after native clearance
acceptance. The screenshot's native rejection was not reproduced on this emulator.

**Deployment remains pending:** deploy the backward-compatible server page before
distributing this client, then repeat fresh live Cloudflare handoff acceptance.
No production deployment, release publishing, new force-stop clearance check,
physical-device run or full connected-suite acceptance is claimed. Existing
unselected/ignored connected cases remain outside these focused results.

## Patch release 0.13.4 — 2 November 2026

Bumped `versionName` to **0.13.4** and `versionCode` to **34**. Release sources
include `8a307da` (downloaded-video sync) and `a3c9c17` (Cloudflare recovery without
relaunch). Only version metadata changed in app sources after the completed
functional checks. The existing **496 JVM tests**, **24 focused Android 16 emulator
cases**, debug lint and fixture/documentation results below were reused as requested;
**no JVM/device tests were rerun for this release**.

The one-time ignored `.tools/build-release-patch.sh` driver copies
`scripts/build-release.sh` with only its completed `:app:testDebugUnitTest` task
omitted. Signing-material checks, release lint, APK/checksum output and immutable
R8 mapping archival remain intact. The tracked release script is unchanged.

New release commands/checks:

- `bash .tools/build-release-patch.sh`: passed; executes
  `./gradlew :app:assembleRelease :app:lintRelease --console=plain` from `android/`.
  Release lint: **0 errors, 36 warnings**. No test tasks appear in the build log.
- SDK build-tools 36.0.0 `apksigner verify --verbose --print-certs`: passed;
  the certificate SHA-256 matches the previously published 0.13.3 APK.
- `aapt2 dump badging`: package `net.wingress.mobivious`, version **0.13.4**,
  code **34**, min SDK **33**, target SDK **37**.
- `zipalign -c -P 16 4`: passed. Every `classes*.dex` ZIP entry is uncompressed.
- APK checksum, current/archived R8 mapping identity and every archive
  `SHA256SUMS` entry: passed.
- Documentation inventory/fingerprint/link checks and `git diff --check`: passed.

APK: **7,352,072 bytes**. SHA-256:
`af2bbc13a06f7820e7126c81bdc78b984515a42c0c66520309ea938e5d688961`.
R8 archive: `artifacts/r8/0.13.4/af2bbc13a06f7820e7126c81bdc78b984515a42c0c66520309ea938e5d688961/`.
Raw build/packaging evidence and release notes are under ignored
`artifacts/release-build-0.13.4.log`, `artifacts/release-0.13.4/` and
`artifacts/release-notes-0.13.4.md`.

Published artifacts are the APK and its `.apk.sha256` file under tag
[v0.13.4](https://github.com/Hanif-F/codex-mobile-invidious/releases/tag/v0.13.4).
Download sync uses existing account APIs; no server contract/deployment was added.
Optimized runtime, full connected-suite and new download-sync force-stop/reboot
acceptance remain unestablished. Earlier implementation results are not presented
as release-runtime results, and existing ignored device cases remain unchanged.

## Cloudflare recovery without relaunch — 2 November 2026

The working tree over `4a9668f` fixes a process-local access-state race: a delayed
successful clearance rotation could leave a Cloudflare issue set even though the
replacement credential was saved. Restarting cleared that issue. Late rotations
now receive a coalesced native confirmation before publishing recovery; successful
in-flight native discovery also reconciles obsolete denials. A remaining backend
challenge receives the confirmed cookie in the open WebView. Account/origin/revision
fences, explicit write/download retry and encrypted credential formats remain intact.

Verified on Android 16 `emulator-5554`:

- From `android/`, `JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/haha.m.lady/Android/Sdk ./gradlew :app:testDebugUnitTest --tests 'net.wingress.mobivious.CloudflareAccessTest' --tests 'net.wingress.mobivious.InstanceAccessTest' --console=plain --offline`: **34 passed**. New cases cover both response orders, duplicate confirmations, rejection/outage/malformed/deleted candidates, context cancellation, unchanged/rotated discovery acceptance and an independent login gate.
- After final production edits, the same Gradle environment with `./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain --offline`: **480 JVM tests passed**, **0 skipped**, debug lint **0 errors, 18 existing warnings**.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.CloudflareAccessSmokeTest#lateCookieRotationRestoresBrowsingAndImagesWithoutRelaunching,net.wingress.mobivious.CloudflareAccessSmokeTest#aDelayedEdgeChallengeCannotUndoCompletedVerification,net.wingress.mobivious.CloudflareAccessSmokeTest#lateRotationLoadsTheRemainingBackendChallengeInTheOpenDialog`: **3 passed**. The activity/model identity, loaded feed/images, discarded late challenge and remaining backend WebView completion are checked.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.CloudflareAccessSmokeTest,net.wingress.mobivious.InstanceAccessSmokeTest`: **30 passed, 1 skipped, 0 failures**. This includes authentication, renderer/subframe safety, cancellation/recreation/background/PiP, browsing/pagination, playback and managed downloads. `clearanceRestoresAfterProcessRestart` remains intentionally skipped without its opt-in restart argument; no new force-stop acceptance is claimed.
- `python3 -m unittest discover -s scripts/tests -p 'test_instance_access_fixture.py' -v`: **8 passed**, including controlled delayed rotation and edge-denial contracts.
- `python3 scripts/check-documentation.py --refresh`, `python3 scripts/check-documentation.py` and `git diff --check`: **passed**; source descriptions, fingerprints, test inventory and local links were reviewed.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveInstanceAccessSmokeTest#realCloudflareClearanceRestoresNativeBrowsingAndPlayback -Pandroid.testInstrumentationRunnerArguments.liveCloudflareAccess=true -Pandroid.testInstrumentationRunnerArguments.liveKeepInstance=true`: **1 failed**. Native clearance was present and the native verification probe completed, but the subsequent guest feed request failed with `ApiException: login_required`. Live browsing/playback acceptance was not reached; no account credentials or production policy were changed.

Logs are under ignored `.tools/cloudflare-recovery-*.log`; preserved JVM, lint
and device XML reports are under `.tools/cloudflare-recovery-results/`, including
the failed live result and its final screen. This is focused emulator acceptance;
the full connected suite and live account-gated playback remain unverified.

## Patch release 0.13.3 — 2 November 2026

Version **0.13.3**, code **33**, packages the changes through
`8f3a52e4f4862040fb3fc1f3f170729568e0104e`: Cloudflare Managed Challenge/native
clearance and refresh recovery, legacy Reddit preference normalization, channel
Shows tabs/links, and FreeTube OPML/NewPipe JSON subscription exports. The only
application-source edit for this release is the version/code increment.

Release checks:

- `scripts/build-release.sh`: **474 JVM tests passed**, signed release build and
  release lint passed (**0 errors, 36 warnings**). Existing signing material was
  reused; checksum generation and nonempty matching R8 mapping archival passed.
- From `android/`, `./gradlew :app:lintDebug --offline --console=plain`:
  **passed, 0 errors and 18 warnings**.
- SDK 36.0.0 `apksigner verify --verbose --print-certs` and `aapt dump badging`:
  correct package `net.wingress.mobivious`, version/code, minimum SDK 33, target
  SDK 37, non-debuggable APK and unchanged certificate. The previous local APK's
  SHA-256 was checked against the GitHub v0.13.2 asset digest before comparing
  certificates. All four CPU architectures remain present.
- `sha256sum -c Mobivious-0.13.3.apk.sha256`: **passed**. ZIP inspection confirms
  uncompressed DEX. Archived `mapping.txt` matches the build output; `apk.sha256`
  identifies this APK and all archive `SHA256SUMS` entries passed.
- `python3 scripts/check-documentation.py --refresh`, its normal check and
  `git diff --check`: **passed** after the version/reference update.

APK: **7,319,300 bytes**, DEX: **6,306,444 bytes**. SHA-256:
`3d81252a893995bb82ecb16aee870e7ad3187d374fbb6ec748f209ecb44d1248`.
The same signing certificate as v0.13.2 supports in-place updates.
Matching R8 archive: `artifacts/r8/0.13.3/<APK-SHA256>/`. Raw build/package evidence
is under ignored `.tools/release-0.13.3-*` and `artifacts/release-0.13.3/`.

Optimized runtime acceptance is **pending**, not a passing gate: two attempts at
`ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --record --label release-0-13-3 --journey navigation`
were interrupted by the Gboard microphone permission dialog while locating
`Benchmark video 000`. The emulator then stopped and ADB reported no connected
devices, preventing the planned watch, settings-saving, downloads and channels
journeys. The failed attempts and first recording are retained under
`.tools/ui-performance/release-0-13-3/emulator-5554/20261101T191004Z/` and
`20261101T191416Z/`. They are diagnostic evidence, not optimized runtime acceptance.
The benchmark package was isolated; no installed release package/data was changed.

The underlying implementation's **27 selected debug emulator cases**, earlier
**474 JVM tests** and **39 Python tests** remain recorded below; they do not
establish signed/optimized runtime or full connected-suite acceptance. Existing
ignored/known device cases, including accepted-video-conversion on this emulator,
remain unaccepted. NewPipe/FreeTube application import acceptance remains separate.

Live backend checks were deferred while the server
recompiled. Shows requires advertised server support; the two new export formats
require the sibling mobile endpoint extension. Existing export-authorized tokens
need no renewed sign-in. This release does not deploy the backend.

## Upstream mobile compatibility (`588135f5`) — 2 November 2026

Implemented legacy Reddit → YouTube normalization for account/guest preferences
and link overrides; advertised playlist-based Shows tabs and `/show/VL…` links;
and typed Invidious/YouTube/FreeTube OPML plus NewPipe JSON exports. Open, Save and
Share use the selected MIME type, preserve attachment names and reject obsolete
format/account/instance responses. Successful export retries now clear the old
error instead of leaving file actions hidden. Existing DASH 720p remains supported.

Checks after the relevant final edits:

- Focused JVM: from `android/`, `./gradlew :app:testDebugUnitTest --offline --console=plain`
  with repeated `--tests` selectors for `net.wingress.mobivious.PreferencesTest`,
  `DeviceProfilesTest`, `ContentLinksTest`, `ChannelTest`, `CommunityTest` and
  `PlaylistRssTest`: **69 passed**. Device sources also compiled through
  `:app:compileDebugAndroidTestKotlin`.
- Final Android gate: from `android/`, `./gradlew :app:testDebugUnitTest :app:lintDebug --offline --console=plain`:
  **474 JVM tests passed, 0 failures/errors/skips**; debug lint **0 errors, 18 warnings**,
  matching the existing warning count. Gradle incremental outputs were retained.
- Emulator: `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=<selectors below>`:
  **27 distinct selected cases accepted**, no selected ignored/skipped cases. The
  first eight-case batch found the export-retry error-retention bug; after its fix,
  the two export cases and shared-consumer regressions passed in a **22-case batch**.
  Earlier passing checks of unaffected comments, Shows and presentation behavior
  remain valid. Connected tasks built and installed their debug app/test APKs.
- Python: `python3 -m unittest discover -s scripts/tests`: **39 passed**.
  `python3 scripts/check-community-fixture.py`: **passed**, including ten channel
  tabs and Shows continuation contracts. A concurrent Python workflow attempt
  initially collided with the emulator fixture's port 18080; the complete Python
  run passed after the device runner released the port.
- Server: in the sibling checkout, `ACCOUNT_TEST_DATABASE_URL=postgres://postgres@127.0.0.1:55435/invidious_accounts_test CRYSTAL_CACHE_DIR=/tmp/invidious-merge-accounts-cache crystal run tests/database/accounts.cr -Dskip_videojs_download`:
  **full guarded account harness passed**. New route cases cover all four formats,
  165 subscriptions including an uncached channel, empty lists, NewPipe metadata,
  MIME/filenames, no-store, unchanged export scope and rejected unsupported formats.
  PostgreSQL ran in a dedicated disposable container, removed after completion.
- Server source: `crystal tool format --check src/invidious/routes/api/v1/native_library.cr tests/database/playlist_rss_checks.cr`;
  `crystal build src/invidious.cr -Dskip_videojs_download --no-codegen`; and
  `crystal build src/invidious.cr -Dapi_only -Dskip_videojs_download --no-codegen`:
  **passed**.
- Documentation: Android `python3 scripts/check-documentation.py --refresh` then
  `python3 scripts/check-documentation.py`; sibling
  `python3 docs/tools/generate_reference.py --check` and
  `python3 docs/tools/check_docs.py`; both repositories' `git diff --check`:
  **passed**.

The exact selected device methods use package `net.wingress.mobivious`; the
OpeningLinks class was selected in full. Each `Class#method` is a comma-separated
selector to the focused command above.

| Class | Selected methods |
| --- | --- |
| `ChannelSmokeTest` | `channelTabsRetainTheirSortLoadedPagesAndVisiblePositionWithinTheAccount`, `delayedPagesCannotReplaceTheSelectedTabOrAppendToAnotherChannelRoute`, `streamsOnlyChannelsOpenOnStreamsAndRetryPreservesTheSelection` |
| `CommentsSmokeTest` | `entryHidesSpoilersAndFetchesOnlyWhenOpenedAndReusesLoadedComments`, `initialRetryEmptyStateAndDisabledPreferenceAreExplicit` |
| `CommunityPresentationTest` | `showsAndExportChoicesRemainReachableAtNarrowLargeFontSizes` |
| `CommunitySmokeTest` | `showsRetryTheirCursorAndIncomingLinksRestoreTheAdvertisedTab`, `specializedTabsSortAndRestoreAfterSearchAndChildNavigation` |
| `ContentLinksSmokeTest` | `recreationRetainsOverridesAndSuccessorsKeepOnlyCarriedOptions`, `sharedChannelAliasesResolveAndRetryWithoutSearching`, `sourceOnlyLinksApplyOptionsAndBoundsToTheFirstOccurrence`, `youtubeViewIntentsOpenColdAndWarmWithPreciseTimestampsAndDoNotReplayOnRecreation` |
| `DeviceProfileStorageTest` | `guestTwoAccountsAndRestartRestoreOnlyTheirOwnDeviceSaves`, `legacyCommentSourcesRestoreWithoutCrossingGuestAccountOrInstanceBoundaries` |
| `DeviceProfilesSmokeTest` | `guestAccountTransitionsRestoreSettingsAndNeverRewriteGuestSpeed` |
| `OpeningLinksPresentationTest` | `manifestExcludesUnrelatedPagesHostsAndSchemes`, `manifestMatchesAllEightHostsOverHttpAndHttps`, `settingsScreenExplainsSelectionAndOffersTheSystemShortcut`, `shortcutTargetsTheInstalledPackageAndFallsBackToAppDetails` |
| `PlaylistRssSmokeTest` | `allSubscriptionExportsOpenSaveAndShareTheSelectedBodyAndMimeType`, `rssSheetsOfferCorrectLinksFilesAndClearOnAccountChange`, `subscriptionExportRetryLegacyServersAndLateFormatsNeverReplaceTheCurrentSelection`, `typedDiscoveryAndChannelPaginationKeepMixSeeds` |
| `SettingsSmokeTest` | `failedAutomaticSaveKeepsRequestedValueAcrossNavigationRecreationAndRetry`, `guestPlaybackDefaultsAndLocalResumeReachPlayer`, `legacyCommentsNormalizeForGuestsAndAccountsAndFailedTogglesRetryAfterRecreation` |
| `SubscriptionJourneyTest` | `darkOpaqueFeedAndExportsRetainAccessibleControls` |

Visual QA inspected the actual Shows page/export sheet and the production tab
controls at 320dp width with 2× font scaling. Selected chips remained readable and
all choices were reachable by horizontal scrolling. Screenshots are retained under
ignored `.tools/upstream-screenshots/`; logs and result snapshots use
`.tools/upstream-*` and `/tmp/mobivious-upstream-*`.

The new file-intent test reads the actual Open/Share content URIs and saved bytes
for all formats, checks MIME types and filenames, verifies document cancellation,
and prevents an account switch from writing a pending export. The recovery case
covers old servers, delayed failing formats, recreation, dismissal and instance
changes. These fixture results do **not** establish full-device-suite acceptance;
existing ignored/known failures outside the selections remain as documented.
Actual import into NewPipe/FreeTube and production deployment were not performed.
Deploy the sibling export extension before relying on the two new formats. It
reuses `GET:subscriptions/export`; no migration or renewed sign-in is required.
No release publishing or production configuration changes were made.

## Cloudflare Managed Challenge — 2 November 2026

The client now handles Cloudflare edge challenges independently of backend
Turnstile and account authentication. Native clearance is encrypted per origin,
uses the actual WebView user agent and cookie expiry, and is accepted only after
a native discovery request. Session cookies remain memory-only. Bootstrap,
images, playback and managed downloads share the same transport boundary.
No live Cloudflare settings, backend policy or account credentials were changed.

Verified on Android 16 `emulator-5554`, WebView **155.0.8059.43**:

- `JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/haha.m.lady/Android/Sdk android/gradlew -p android :app:testDebugUnitTest :app:lintDebug --console=plain --offline`: **469 JVM tests passed**, debug lint passed. After explicit WebView feature guards and an additional cookie-path assertion, `android/gradlew -p android :app:testDebugUnitTest --tests 'net.wingress.mobivious.CloudflareAccessTest' :app:lintDebug --console=plain --offline` passed **13 focused cases**, with final lint **0 errors, 18 warnings**. The renderer handler is implemented and device-tested despite lint's `MissingOnRenderProcessGone` warning.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.CloudflareAccessSmokeTest,net.wingress.mobivious.InstanceAccessSmokeTest`: the new seven ordinary edge cases and twenty existing access cases passed across the combined run and focused rerun. A repeated-run image test initially timed out on its fixed cached URL; unique fixture query parameters now make its required recovery requests observable, and the changed case passed on rerun. The opt-in restart case is intentionally skipped by the ordinary class run.
- Seed/image rerun: `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.InstanceAccessSmokeTest#visibleImagesAndBannersRecoverWhileAnOldDenialArrivesAfterVerification,net.wingress.mobivious.CloudflareAccessSmokeTest#edgeOnlyRestoresBrowsingAndEncryptedClearanceSurvivesStoreRecreation -Pandroid.testInstrumentationRunnerArguments.cloudflareKeepClearance=true`: **2 passed**.
- After `adb -s emulator-5554 shell am force-stop net.wingress.mobivious.debug`, `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.CloudflareAccessSmokeTest#clearanceRestoresAfterProcessRestart -Pandroid.testInstrumentationRunnerArguments.cloudflareRestart=true`: **1 passed**, confirming encrypted native clearance survives actual process death without another WebView handoff.
- Live: `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveInstanceAccessSmokeTest#realCloudflareClearanceRestoresNativeBrowsingAndPlayback -Pandroid.testInstrumentationRunnerArguments.liveCloudflareAccess=true -Pandroid.testInstrumentationRunnerArguments.liveKeepInstance=true`: **1 passed** against `https://invidious.wingress.net`. Real edge clearance, native discovery/feed, playing media and an advancing playback position were accepted without an account. A subsequent normal cold app launch loaded the production feed without another verification dialog. The debug app remains on the production instance.
- `python3 -m unittest discover -s scripts/tests`: **37 passed**. After adding probe rotation, the affected `test_instance_access_fixture.py` selection passed **6 tests**; documentation-checker selection passed **7 tests**.
- `python3 scripts/check-documentation.py --refresh`, its normal check and `git diff --check`: passed. The sibling server change is documentation-only; its bot-protection guide passed `git diff --check -- docs/bot-protection.md`.

This is **28 distinct fixture device cases plus one live case**, with no remaining
focused failures. It does not establish full connected-suite, physical-device or
optimized-release acceptance. Live acceptance covers the current zone/network;
future expiry, browser updates or policy rejection can require verification again.

Logs, XML reports, preserved full JVM results, final lint XML and reviewed live
screenshots are under ignored `.tools/cloudflare-results/`. `live-playback.png`
shows production playback; `live-restarted.png` shows the normal app's feed after
a cold restart. No clearance values are recorded in this report.

## Video metadata recovery and release 0.13.2 — 24 October 2026

Version **0.13.2**, code **32**, converts empty or malformed video metadata into
a readable Retry error. HTTP error handling and successful empty writes retain
their contracts. Retry retains the queue occurrence and autoplay/paused intent.
The separately prepared server changes return no previews for missing or
wrong-shaped storyboard metadata and catch/log video serialization failures.
Those server changes remain local in the sibling checkout at the user's request;
neither the Android commit nor the APK deploys the server playback fix.

Checks on Android 16 `emulator-5554` and the local fixture:

- From `android/`, `./gradlew :app:testDebugUnitTest --tests 'net.wingress.mobivious.NetworkReliabilityTest' --tests 'net.wingress.mobivious.SeekPreviewTest' --console=plain`: **20 tests passed**.
- `ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AppSmokeTest#failedInitialPlaybackRetryPreservesAutoplayAndPausedIntent,net.wingress.mobivious.SeekPreviewsSmokeTest#unavailableAndInvalidCropsLeaveSeekingUsableAndNeverShowTheSpriteSheet`: **2 focused tests passed**, including HTTP/empty-response recovery for both autoplay and paused intent.
- `python3 -m unittest discover -s scripts/tests`: **34 passed**, including the new single-use empty-response/reset fixture contract.
- `scripts/build-release.sh`: **452 JVM tests**, signed release build and release lint passed (**0 errors, 36 warnings**). The script retained checksum and R8 archive validation.
- From `android/`, `./gradlew :app:lintDebug --console=plain`: passed after the version bump.
- `ANDROID_SERIAL=emulator-5554 scripts/test-ui-performance.sh --record --label release-0-13-2 --journey watch`: **1 optimized benchmark watch journey passed**, without timing measurement or source drift. The recording shows successful video playback; the installed release app and its data were preserved.
- `python3 scripts/check-documentation.py --refresh`, its normal check and `git diff --check`: passed after reviewing the API/error/testing guides and inventory.

The signed APK is **7,302,896 bytes**; SHA-256:
`aa0058e853729deac37026a56b0b402f8575c2839aa0205fc45275aa667d5d70`.
SDK `apksigner` and `aapt` checks confirm package/version/code, minimum SDK 33,
target SDK 37 and the unchanged release signing certificate. DEX is uncompressed;
the nonempty archived R8 mapping matches the release output and all archive
checksums pass. Evidence is in ignored `.tools/release-0.13.2-checks.json`,
`.tools/release-0.13.2-*.log`, `.tools/storyboards-*.log` and
`artifacts/r8/0.13.2/<APK-SHA256>/`. Optimized runtime evidence is under
`.tools/ui-performance/release-0-13-2/emulator-5554/20261024T063028Z/`.

In the sibling server checkout, `crystal run tests/video_metadata_routes.cr`
passed **97 checks** through the production accessor/parser/serializers/API
dispatcher, with synthetic metadata and cache boundaries. `crystal spec
-Dskip_videojs_download` passed **400 examples** (293 Spectator and 107 Spec).
GUI and API-only `crystal build src/invidious.cr -Dskip_videojs_download
--no-codegen` checks passed (adding `-Dapi_only` for the latter); both retain four
existing deprecation warnings. Changed Crystal formatting, generated-reference
and documentation/link checks passed. The earlier documented full-spec helper
compilation failure did not reproduce on this source.

This is focused emulator/fixture acceptance. Full connected-suite acceptance,
the remaining optimized background/PiP/download/clip/export journeys and live
playback after server deployment remain separate. Existing ignored device cases
retain their previous acceptance boundaries.

## Patch release 0.13.1 — 23 October 2026

Bumped version name to **0.13.1**, code **31**. Reused completed JVM, device,
Python and debug-lint results at the user's request. No tests were repeated.
Ran `:app:assembleRelease :app:lintRelease --console=plain`, retaining the
release script's signing/checksum/R8 archive steps and omitting its JVM task.
Release build and lint passed (**0 errors, 36 warnings**).

Verified package/version/code, minimum SDK 33 and target SDK 37, certificate
continuity with 0.13.0, APK checksum, uncompressed DEX, and matching nonempty
R8 mapping/archive checksums. APK: **7,302,896 bytes**; SHA-256:
`9afaba3fc7db30689442edc16466ccd5c1dfc0474c84ed9d761245591d67ef27`. Evidence is in ignored
`.tools/release-0.13.1-checks.json` and `artifacts/r8/0.13.1/<APK-SHA256>/`.
Documentation and whitespace checks passed. Optimized runtime journeys were
not repeated; existing acceptance boundaries remain unchanged.

## View-count availability — 23 October 2026

Android consumes supplied per-video count type/precision and legacy text across
cards, history, playlists/mixes, queues, clip sources and downloads. Genuine zero
remains visible; unknown sentinels and ambiguous untyped live counts stay hidden.
Queue insertion and clip serialization retain counts without a new cache or
count-only requests. Server code and contracts were unchanged.

The full JVM suite passed **450 tests**, with no failures, errors or skips.
Debug lint passed with **0 errors, 36 warnings**. Final completion command, from
`android/`, retaining incremental outputs:

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
```

**14 distinct focused Android 16 emulator cases passed** across the selection
below and affected-method reruns, with no ignored cases. The four new journeys
cover Up next and manual insertion; history/playlist/mix pagination; duplicate
queue occurrences and recreation; and clip source labels with saved offline
counts. Request assertions verify browsing adds no video-detail requests and
manual insertion/offline playback add none beyond the requested playback.
Presentation coverage includes light/dark, 2× text, narrow width, RTL numeric-unit
ordering, bounded queues, and existing queue loading/recovery behavior.

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.ViewCountsSmokeTest,net.wingress.mobivious.MetadataPresentationTest,net.wingress.mobivious.PlaybackQueuePresentationTest,net.wingress.mobivious.QueueLibrarySmokeTest#standaloneManualQueueCollapseAndNewSessionVisibility
```

Initial new-test selectors were corrected to inspect unmerged Compose children
and distinguish the clip source card from the underlying clip list. Screenshot
review then exposed reversed exact-count ordering in RTL; count paragraphs now
use content direction with an LTR fallback. The final presentation case verifies
actual glyph bounds and visual number-before-unit order in all four theme/direction
combinations. After that display fix, the 13 count/layout cases were rerun and
only the presentation assertion needed a focused rerun using the repository's
existing glyph-bounds helper (the generic overflow flag reported subpixel width
rounding). All selected cases are accepted. Screenshots were reviewed under
ignored `.tools/view-counts-preview/`; archived device results are in
`.tools/view-counts-*-results/`.

All **33 Python fixture/workflow/documentation tests** passed. An earlier overlap
with the Android fixture occupied port 18080; rerunning the workflow checks after
the device runner released it passed without workflow changes. Documentation
inventory, fingerprints, catalogs and local links passed for 305 maintained files
and 21 documents:

```sh
python3 -m unittest discover -s scripts/tests
python3 scripts/check-documentation.py --refresh
python3 scripts/check-documentation.py
git diff --check
```

This is focused acceptance, not full connected-suite acceptance. Existing
failures/ignored cases outside these selectors remain unaccepted. Improved
availability depends on the selected server returning counts; no production
deployment, release build or performance acceptance is claimed.

## Automatic Turnstile verification — 23 October 2026

Required verification now opens automatically in the foreground and closes after
the native pass is validated and encrypted. Browsing retries after any current
browse request settles, including a failed pagination request. Cancel/Back remains
effective through recreation, background and PiP until explicit Retry. Required
sign-in and unavailable-provider prompts remain separate; playback and downloads
retain explicit Retry actions. Server code, contracts and release packaging were
unchanged.

- Full JVM suite: **445 tests passed**, no failures/errors/skips. Debug lint:
  **0 errors, 36 warnings**. Ran once after the final production edits, retaining
  incremental outputs, from `android/`:

  ```sh
  JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/haha.m.lady/Android/Sdk \
    ./gradlew :app:testDebugUnitTest :app:lintDebug --offline --console=plain
  ```

- Fixture acceptance: **16 distinct device cases passed**, no ignored cases,
  on Android 16 `emulator-5554` (Pixel 8 Pro). Coverage includes automatic opening,
  duplicate denials/completion events, exactly one new browse retry, in-flight
  browse completion, preserved search pagination, Cancel/Back/recreation,
  background/PiP suppression and retained cancellation, stale account/server
  contexts, login precedence, encrypted persistence, renderer recovery,
  untrusted subframes, retained playback and protected-download recovery.

  ```sh
  ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline \
    -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.InstanceAccessSmokeTest
  ```

  The full class initially passed 14/16. Two new assertions were corrected: the
  request count included startup traffic carrying a previously persisted pass,
  and paused playback need not retain an error string after verification. Request
  assertions now measure the increase from their baseline; playback checks retain
  the queue occurrence and paused state. No production fix was needed. Reran only
  the four affected methods through the same runner/options with a comma-separated
  `net.wingress.mobivious.InstanceAccessSmokeTest#method` selector; all four passed:
  `automaticVerificationCoalescesDenialsAndRetriesBrowsingOnce`,
  `verificationWaitsForTheCurrentBrowseRequestBeforeRetrying`,
  `automaticVerificationRetriesTheFailedSearchPageWithoutLosingEarlierResults`,
  `backgroundAndPipAccessExpiryPauseUntilVerificationAndExplicitRetry`.
  The other 12 passing cases remained valid. An earlier five-case development
  selection also passed. Connected runs built and installed their required debug
  app/test APKs; the release app and its data were preserved.

- Live-provider acceptance: **1 test passed**, no skips. The real Managed
  Turnstile page on `https://invidious.wingress.net` completed without clicks,
  issued a native pass and passed discovery validation. Verification and success
  confirmation were absent afterward. The persisted pass remained usable after
  reopening the debug app. The production feed still returned `login_required`;
  real-account content acceptance was not exercised. No account credentials or
  server policy were changed. The default production instance was left selected.

  ```sh
  ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only --offline \
    -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveInstanceAccessSmokeTest \
    -Pandroid.testInstrumentationRunnerArguments.liveInstanceAccess=true \
    -Pandroid.testInstrumentationRunnerArguments.liveKeepInstance=true
  ```

- Reviewed prose/source descriptions, refreshed fingerprints and generated
  catalogs with `python3 scripts/check-documentation.py --refresh`, then ran
  `python3 scripts/check-documentation.py` and `git diff --check`: passed.

Logs are under ignored `.tools/access-auto-{focused,device,device-recheck,live,jvm-lint}.log`.
Device XML/logcat evidence is preserved under `.tools/access-auto-results/` in
`initial/`, `recheck/` and `live/`; the settled live screen is
`.tools/access-auto-settled.png`. This is focused device acceptance. The complete
device suite, release/optimized checks, physical-device runs and production
account-gated content were not run or claimed.

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

## Downloaded playback synchronization — 2 November 2026

Downloaded playback now shares account history/progress with streamed playback,
using a durable per-profile journal and revision-safe acknowledgements. Downloads
retain independent local resume. Confirmed history/resume settings apply to a
running download, including recovery when no settings/activity were cached.
Pending progress precedes remote resume, initial online resolution is bounded to
two seconds, and late responses cannot replace newer checkpoints or completion.
Retries run only while the application process lives, with recovery/next-launch
triggers; no persistent scheduler or server contract was added.

Final acceptance after production edits:

- **496 JVM tests passed**, zero failures/errors/skips (49 classes), including
  16 dedicated playback-sync persistence, ownership, settings, retry and race cases.
- **Debug lint passed**, zero errors and 36 warnings. The compiler also reports
  existing deprecation diagnostics and an unnecessary non-null assertion in the
  new service observer; neither blocks the checks.
- **24 focused Android 16 emulator cases passed**, zero failures/skips, on
  `emulator-5554` (Pixel 8 Pro AVD). Connected tasks built/installed debug app/test
  APKs. Seven new sync journeys cover history/progress/completion, server resume,
  retry/recreation, settings changes and authentication/account isolation. Existing
  consumers cover offline decode/background controls, indicators, stable-profile
  storage/transfer, settings, streamed playback and access recovery.
- **8 Python checks passed**: one sync fixture contract and seven documentation
  checker regressions. Documentation inventory/fingerprint/link checks passed for
  **314 maintained files and 21 documents**. `git diff --check` passed.

From `android/`:

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
```

From the repository root, the final device selector was:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --device-only -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.DownloadSyncSmokeTest,net.wingress.mobivious.DownloadsSmokeTest#allModesAndCaptionsDecodeOfflineWithoutMediaOrMetadataRequests,net.wingress.mobivious.DownloadsSmokeTest#offlinePlaybackUsesWatchMiniFullscreenPipAndBackgroundControls,net.wingress.mobivious.WatchedIndicatorsSmokeTest,net.wingress.mobivious.DeviceProfilesSmokeTest#guestAccountTransitionsRestoreSettingsAndNeverRewriteGuestSpeed,net.wingress.mobivious.DeviceProfilesSmokeTest#existingSessionDiscoversStableIdentityAndLateGuestSavesAreRejected,net.wingress.mobivious.DeviceProfileStorageTest#playbackJournalMigratesWithIdentitySurvivesRestartAndPurgesWithItsOwner,net.wingress.mobivious.SettingsSmokeTest#guestPlaybackDefaultsAndLocalResumeReachPlayer,net.wingress.mobivious.SettingsSmokeTest#failedAutomaticSaveKeepsRequestedValueAcrossNavigationRecreationAndRetry,net.wingress.mobivious.AppSmokeTest#hlsExplicitResumeCaptionsAndAudioOnly,net.wingress.mobivious.AppSmokeTest#accountBrowsingAndServicePlayback,net.wingress.mobivious.InstanceAccessSmokeTest#playbackVerificationAutomaticallyRetriesAndKeepsPositionAndOptions,net.wingress.mobivious.InstanceAccessSmokeTest#protectedDownloadsRetryWithoutRepeatingCompleteAssetsAndPlayOffline
python3 -m unittest discover -s scripts/tests -p test_download_sync_fixture.py
python3 -m unittest discover -s scripts/tests -p test_documentation.py
python3 scripts/check-documentation.py
git diff --check
```

Device XML/results are under `android/app/build/outputs/androidTest-results/connected/debug/`;
JVM XML is under `android/app/build/test-results/testDebugUnitTest/`; lint details
are in `android/app/build/reports/lint-results-debug.html`.

Acceptance covers repository recreation and actual preference-store/activity
recreation, not a new force-stop/reboot journey. Server outage/recovery is exercised
through controlled fixture failures and coordinator recovery; this does not claim
real network-radio outage or live-server acceptance. The full connected suite and
release checks were not run. Existing ignored export/conversion and opt-in restart
cases remain unchanged and were not selected. No server source/contracts changed,
so server checks were not required.
