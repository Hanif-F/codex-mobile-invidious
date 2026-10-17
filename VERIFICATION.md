# Focused implementation verification

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
