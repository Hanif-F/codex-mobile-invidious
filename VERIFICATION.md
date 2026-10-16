# Focused implementation verification

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
