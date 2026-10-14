# Verification and evidence requirements

[Overhaul entry point](README.md) · [Implementation packages](IMPLEMENTATION.md)

**Status: verification specification.** This document defines future app checks;
it is not a passing-results report. The documentation pass's actual checks are
recorded at the end.

## Documentation checks

Register all six guides in the `documents` list of
[coverage.json](../reference/coverage.json). Link them from the current index and
add supersession notices to the old design/overhaul instructions. Preserve
historical measurements and current implemented behavior.

From repository root:

```sh
python3 scripts/check-documentation.py
git diff --check
git diff --stat
git status --short
```

Review the diff to confirm only documentation and its registration changed.
Check local Markdown anchors as well as file links; the repository checker
checks file existence, not heading anchors, semantic accuracy or color contrast.
Validate every enabled palette pair against the design-system gates. Confirm
every baseline screen ID appears with hierarchy/actions/state treatment and
every implementation package has a defined acceptance gate.

Do not run `--refresh` just to acknowledge unchanged app source. During later
implementation, review source/prose and explicitly add new coverage entries
before refreshing hashes/catalogs as described in [Testing](../TESTING.md).

## Functional and presentation matrix

Run each screen family's applicable shared states, not only its happy path:

| Dimension | Required scenarios |
| --- | --- |
| Appearance | Light and Dark × six accents; System changes while foregrounded; saved/absent/invalid accent; open menus/focused text survive theme changes. |
| Windows/type | 320dp, 360dp and 448dp compact widths; 600dp rail transition; 840dp and 1200dp; portrait/landscape/multi-window; font scale 1.0, 1.3 and 2.0; one usable pane around occluding folds. |
| Input/accessibility | Touch, TalkBack, keyboard/Enter/Tab, RTL, system animation scale zero and reduced motion; no overlapping ≥48dp targets; no essential text/action clipped. |
| Data | Loading, refresh and pagination failures, retry, empty/all-hidden, missing artwork/metadata, optional-server incompatibility, blocked/member/AI rules. |
| Identity/network | Guest/account, sign-in return/cancel, account switch/sign-out/server switch with pending work, offline local settings/downloads and failed account saves. |
| Media | 16:9, portrait, square and ultrawide; starting/buffering/error/end; playing/paused; mini/watch/fullscreen/PiP/background; audio-only; local download/clip; captions/tracks. |
| Effects | Ambient absent/off/on/saved true/saved false; Reduce transparency; power save; unsupported/software rendering; no hidden/idle capture loop. |

At minimum, capture representative rendered screens for each family in both
appearances, narrow 2× text and wide mode. Test all accent role pairs numerically
and capture the shared component gallery in all twelve appearance/accent
combinations. Do not require the full Cartesian product for every server scenario.
Use fixture accounts and synthetic media; never capture real tokens/private links.

## Existing test mapping

Names below are current suites; update expectations with the implementation.
Use [the test catalog](../reference/TEST_CATALOG.md) for exact declared methods.
Rename glass-specific suites/helpers when they become general UI tests; retain
behavioral coverage rather than merely deleting red tests.

| Current suites | Keep / revise / remove |
| --- | --- |
| NavigationHubSmokeTest, AccountNavigationSmokeTest, DiscoverySearchSmokeTest, SearchHistorySmokeTest, HomeSubscriptionsSmokeTest | Keep state/return/auth/launch/search behavior. Revise selectors to four direct tabs/top search and rail, preserving all scoped-search returns. |
| LiquidRedesignSmokeTest, LiquidNavigationTestActions | Replace Discover/bottom-input/floating-sidebar assumptions with the new shell. Keep service continuity, aspect ratios, reduced motion, authentication cancellation and reachable actions. Rename to neutral UI/navigation names. |
| SettingsSmokeTest, PreferencesTest, DeviceProfileStorageTest, DeviceProfilesTest | Replace page-draft Save expectations with automatic writes. Keep normalization, save scope, recreation, codec/default precedence and partial-PATCH behavior. Add controller and migration cases below. |
| AppearancePresentationTest, DarkGlassPresentationTest | Keep System/Light/Dark, persistence, enabled contrast and open-menu/focused-field stability. Replace colored-backdrop/adaptive-polarity assertions with preset/opaque-surface assertions. |
| GlassControlPresentationTest, ActionControlsPresentationTest, MetadataPresentationTest | Retain pending membership, labels, semantics, long text and action reachability; use shared Material component expectations. |
| GlassFoundationTest | Retain caption windows, independent hit areas and press-cancellation/single-action behavior. Replace artwork/background/backing/readback tests with opaque surfaces and absence of optical work; remove obsolete scene tests once their production code is removed. |
| GlassRefractionTest | Remove bend/magnification/window-copy/deferred-profile assertions with removed renderer. Carry forward relevant popup alignment, clipping, idle/disposal and no-readback intent in general overlay tests. |
| VisualSurfaceRegressionTest, VisualFixesSmokeTest | Keep safe insets, wrapped titles, menu identity and launcher mask checks. Replace transparent-corner/header masking assumptions with correct opaque clipping/measured layout. |
| SubscriptionGlassJourneyTest, SubscriptionGlassPresentationTest, SubscriptionsPresentationTest, SubscriptionSortingSmokeTest, SubscriptionsTest | Keep feeds/directory/sort/fallback/export/origin/playback behavior; revise appearance and grid/rail expectations. |
| LibraryGlassJourneyTest, LibraryGlassPresentationTest, RemainingGlassJourneyTest | Keep account/library/editor/download/subscribe failure journeys; replace glass/group/footer expectations with shared surfaces, automatic preferences and explicit entity forms. |
| WatchGlassPresentationTest, PlayerControlsPresentationTest, PlayerFeedbackPresentationTest, WatchPlayerResizePresentationTest, PlaybackVisibilitySmokeTest | Keep labels, tooltips, disclosure semantics, transport geometry/gestures, decoder lifetime and aspect ratios. Revise circular-action/glass expectations. |
| AmbientModeSmokeTest, AmbientModePresentationTest, AmbientPaletteTest | Keep palette/gate/callback safety tests; explicitly enable opt-in runs. Add absent-key off and stored-choice migration; remove any dependence on glass or adaptive foregrounds. |
| QueueLibrarySmokeTest, PlaybackQueuePresentationTest, PlaybackQueueTest, SessionTimelineTest | Keep occurrence identity/duplicates, source-list versus queue removal, continuation and repeat. Update panel presentation only. |
| Comments*, Chapters*, LiveChat*, Community*, ChannelDescriptionPresentationTest, VideoInformationPresentationTest, ContentLinks*, Avatars* | Keep parsers, rich content, ordering, links, read-only states and native metadata/imagery. Revise shared rows/panels and chat autosave UI. |
| Downloads*, Clips*, PlaylistRss*, AccountApiTest, StreamControls*, SponsorBlock*, DeArrow*, AiFilter*, NetworkReliabilityTest | Keep domain/API/storage/security/codec/cancellation contracts and focused device journeys. Adjust UI selectors; no weakening of functional tests to make the redesign pass. |

The existing complete connected suite is not documented as fully green.
Establish baseline failures first; record inherited failures separately with
reproduction and impact. A failing retained journey affecting changed behavior
must be resolved before that family is accepted.

### New focused cases

Add controller/domain tests for:

- Two fast changes to the same setting; newest intent wins without an old response
  overwriting it. Independent-field edits survive serialization.
- Slow response followed by edit, failure, Retry, Revert to saved, validation
  failure and navigation away; no false Saved message or lost unrelated fields.
- Debounced text, Done/blur/background flush, slider release and grouped fields;
  invalid input never reaches a sink.
- Account/instance generation changes and per-video delay changes during a
  pending request; late results cannot alter the new context.
- Guest normalization, shared-versus-device/profile scope, local persistence
  rollback, partial-map edits and unsupported unchanged imported values.
- Ambient missing/explicit true/explicit false; unknown accent → Blue; dormant
  old glass keys have no rendering effect.

Add focused device checks for direct Popular/Trending tabs, top-field search and
IME clearance, rail resizing, automatic-save pending/failure/recreation, accessible
player operations, opaque overlays and ambient independence. Assert behavior and
observable work; do not add tests that merely mirror helper implementation.

## Commands for later application implementation

Build/static/domain checks, from repository root:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain)
```

Use the host's actual JDK/SDK paths if different. Device tests install disposable
debug/test APKs; select an already running device, with fixture accounts only:

```sh
export ANDROID_SERIAL='<selected-device-serial>'
scripts/test-android.sh -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.NavigationHubSmokeTest,net.wingress.mobivious.SettingsSmokeTest
scripts/test-android.sh
scripts/test-download-restart.sh
```

The final restart command exercises the isolated debug package and its existing
restart workflow; read [Testing](../TESTING.md#download-restart-and-conversion-acceptance)
first. Record ignored conversion/emulator cases honestly; compilation is not
execution. Use neutral renamed suite names after migration.

Run the existing fixture checkers when their contracts change. Application
verification does not require changing/deploying the sibling server.

## General-phone performance protocol

Measure representative physical Android phones: at least one resource-constrained
supported device and one mainstream device, spanning minimum-supported and
current Android versions where available. The user's specific handset is optional.
Emulators support layout/functional checks; their numbers cannot close the phone
performance gate.

Use the planned P0 harness and a non-debuggable, profileable, release-like isolated
benchmark package. Android's [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
requires a separate test module and suitable target variant. Fix source/build,
optimization, compilation mode, fixtures, viewport, font, theme, refresh-rate
mode and power policy across each comparison. Record actual effective refresh
rate rather than assuming a 120Hz panel renders this app at 120Hz.

Use `CompilationMode.Full()` consistently for the primary UI comparisons;
initial startup/JIT behavior is a separate diagnostic. Run ten measured iterations
per journey, with setup/content readiness and one untimed warm-up outside the
measurement. Reset fixture/account/page state per iteration. Wait for readiness
through semantics and content counts, not arbitrary sleeps in timed blocks.

### Repeatable journeys

For scrolling journeys, use the same scrollable viewport fraction (70%) and
gesture duration (400ms): six forward swipes and six reverse swipes, with a
100ms gap. Warm-data runs have enough loaded rows to avoid intentional load-more;
run a separate cold-artwork/load-more variant so network/decoding cost is visible.
Stable fixture IDs/counts/settings and selector maps belong in the evidence.

| Journey ID | Setup and measured interaction |
| --- | --- |
| popular | Comfortable Popular feed; repeat scroll; repeat Compact and Hide thumbnails variants. |
| trending | Fixed region/category; scroll as above, then separate category/region-change responsiveness run. |
| subscriptions | Fixture account with uploads and creator shortcuts; scroll uploads, then separate one-column Channels directory run. |
| search | Fixed mixed-result query; submit/readiness outside scroll timing; scroll videos/channels/playlists with the same fixture order. |
| library | Fixture history and playlists with enough rows; scroll; also Downloads with multiple progressing fixture transfers as a separate run. |
| settings | Open Playback preferences; scroll, then a separate sequence of toggle/choice changes with controlled response delay and failure. |
| watch | Fixed playing fixture video, recommendations visible; scroll details/recommendations with ambient off; repeat ambient on and compare separately. |
| navigation | Switch all four tabs, open/close search and a menu, restore/minimize watch; retain normal-speed input timing. |

Capture current glass, redesigned UI, and comparable pre-glass 0.9.1 journeys.
For 0.9.1, map existing equivalent routes/fixtures and record harness adapter
changes; mark newer features N/A. A missing historical build or incomparable
fixture is a documented limitation, not fabricated evidence. The current/new
comparison remains mandatory.

### Metrics and acceptance

Collect `FrameTimingMetric` JSON and Perfetto traces; report P50/P90/P95/P99
`frameDurationCpuMs` and `frameOverrunMs`, frame counts and positive-overrun
fraction computed from the corresponding trace frames. Positive overrun denotes
a missed frame deadline; CPU time alone does not capture the whole pipeline.
Use the [official metric definitions](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).

For warm steady scrolling, the project target is **P95 frameOverrunMs ≤0** at
the recorded display mode: at least 95% of frames meet their actual deadline.
At 60Hz a frame interval is approximately 16.7ms; faster display modes have
smaller intervals. This is a general responsiveness target, not a model-specific
FPS certification or a claim already achieved.

Require the redesigned UI to meet that target on the representative runs and
show no repeatable P95/P99 regression against current source. An improvement
claim additionally needs repeatable evidence in the reported jagged-scroll
journeys. Investigate worst frames through traces; do not hide cold-image,
pagination or transition results by presenting only warmed runs.

Use medians of per-iteration percentile results for the comparison table and
retain the per-iteration spread/raw outputs. If results overlap materially,
repeat an independent session after temperature stabilizes and report the
uncertainty. Do not average percentile values across different phones or
discard inconvenient iterations without a recorded environmental reason.
Record thermal state, battery/power mode, OS/build and benchmark warnings.
Do not screen-record during benchmark timing; make separate normal-speed videos
for visual/input review.

With ambient off, profiling must show no glass/background/window capture work.
With ambient on, verify bounded sampling only in eligible visible watch, no
full-frame allocation loop, and no decorative capture while idle/hidden.
Functional media/chat/progress timers are assessed separately.

### Planned runner commands

These commands become runnable **after P0 adds the runner/module**:

```sh
export ANDROID_SERIAL='<selected-physical-device-serial>'
scripts/test-ui-performance.sh --list
scripts/test-ui-performance.sh --label current-glass --journey all
scripts/test-ui-performance.sh --label redesigned --journey all
```

Run labels from their corresponding source checkout, never overwrite the original
baseline, and record the actual commit in addition to the human label. The runner
owns fixture startup/teardown and invokes
`(cd android && ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --console=plain)`
with selectors for the requested journey. Do not invoke the bare Gradle task
without its fixture/setup prerequisites.

## Completion and evidence

Store future reports under a new dated evidence directory with:

- Source commit and uncommitted diff fingerprint; APK hash/build variant;
  device/OS/window/font/theme/accent/refresh/power configuration.
- Exact commands, fixture revision/scenario and benchmark selector/adapter map.
- Tests passed/failed/ignored, inherited failures, screenshots and normal-speed
  interaction recordings; no secrets/private feed tokens.
- Raw benchmark JSON/Perfetto traces, per-iteration summaries and side-by-side
  comparable current/pre-glass/new results with explicit N/A and gaps.
- Screen-ID/package acceptance checklist and remaining blockers.

The documentation gate is independent of app acceptance. Later app acceptance
requires the relevant builds/static/domain checks, changed-family device journeys,
rendered/accessibility review and physical-phone performance evidence. Unavailable
hardware means that gate is pending; emulator evidence cannot substitute for it.
Release acceptance and server deployment are separate from all of these.

## Documentation-pass results

Reviewed on **13 October 2026**, against the source recorded in BASELINE:

| Check | Actual result |
| --- | --- |
| `python3 scripts/check-documentation.py` | Passed: 230 maintained files, 29 registered documents; source fingerprints and local file links valid. |
| `git diff --check` | Passed; no whitespace errors in the tracked diff. New-guide whitespace was checked separately. |
| Local link/heading audit | Passed: 13 heading-anchor links across the six new and seven changed Markdown files; local targets exist. |
| Screen/package inventory audit | Passed: all 39 baseline screen IDs have entries; all seven P0–P6 package rows have acceptance criteria. |
| Numeric palette review | Passed: 220 text/on-role/fixed-role/inverse/boundary pairs; minimum checked text contrast 4.595:1, minimum checked boundary contrast 3.310:1. Computed from the documented sRGB values using relative luminance. |
| Change-boundary review | Six new Markdown guides, documentation entry-point/supersession notes and the document-registration list only; maintained implementation fingerprints unchanged. |

These are documentation checks, not application acceptance. Application
implementation, builds, device installation, runtime tests, fresh screenshots
and phone benchmarks were not performed by this task.
