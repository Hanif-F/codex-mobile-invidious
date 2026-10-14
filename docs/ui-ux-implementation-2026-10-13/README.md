# Opaque Android UI implementation and verification

The UI/UX overhaul is implemented and verified within the user's revised scope:
**emulator verification, with performance acceptance deferred to the user** on
14 October 2026. No release, version bump, server deployment or PR was requested.
Existing uncommitted documentation changes are preserved.

Starting source: `62c91828215759c3c50f6301808b2d9accb4ed92`.
The [specification](../ui-ux-overhaul-2026-10-13/README.md) defines the contracts;
the [39-screen audit](SCREEN_ACCEPTANCE.md) and
[method-level results](evidence/functional-aggregate.json) map their verification.
One inherited emulator conversion Ignore remains explicitly unverified below.

## Implementation packages

| Package | Result | Evidence |
| --- | --- | --- |
| P0 | Isolated non-debuggable/profileable `.benchmark` variant, Full compilation, fixed fixtures, 19 journeys, source/APK/configuration hashes, raw trace/JSON and separate recording mode implemented. Current and 0.9.1 source adapters retained. | Corrected ten-iteration Popular run produced trace/JSON and gesture provenance. Performance acceptance/comparisons are deferred to the user. |
| P1 | Exact six light/dark accent palettes, opaque Material controls, shared widths/insets and ≥48dp targets; migration preserves stored scopes/choices. | Numeric text/control-boundary checks, twelve galleries, focus/menu/theme migration and software drawing passed. |
| P2 | Popular, Trending, Subscriptions, You; independent stacks/positions; top search; bar/rail; full-width mini-player. | Root return/reselection, search/link/scoped return, keyboard/Enter, IME and 36 window/font/theme cases passed. |
| P3 | Continuous library rows, visible playlist creation, subscription/channel hierarchy and bounded per-channel tab/sort/page/position snapshots; native secondary surfaces. | Browsing/library/domain journeys, large text, retained pagination, membership failures and download restart checks passed. |
| P4 | Revisioned ordered/coalesced automatic writes, sparse patches, Retry/Revert, invalid local drafts, lifecycle flush and context isolation; explicit entity/security forms retained. | Controller/domain and device failure/recreation/context/grouped-field cases passed. Auth modes now wrap at narrow 2× text; all 13 final account/CAPTCHA/layout checks passed. |
| P5 | Native player scrims, labeled actions, supporting panes/full-height queue, mini buffering/error/Retry, independent opt-in ambient sampler. | Decoder/occurrence continuity, tools, gestures, tracks/captions, offline media, ambient gates and TalkBack playback activation passed. |
| P6 | Optical renderer/helpers and Backdrop dependency removed; dormant stored keys retained without consumers; neutral test names and current guides/catalogs updated. | Build, JVM, lint, fixture, functional, rendered/input and documentation checks recorded below. No runtime optical shader/window readback remains. |

## Actual verification results

| Check | Result | Raw evidence under `.tools/ui-ux-overhaul/` |
| --- | --- | --- |
| JVM/domain | **361 passed**, zero failures/errors/skips. | `final-auth-reflow-results/unit/` |
| Build / lint | Debug app and test compilation, packaging and lint passed. | `final-auth-reflow.log` |
| Android aggregate | **361 current methods: 360 passed, one inherited Ignore.** | `functional-aggregate.json`; referenced raw XML per method |
| Final auth reflow | **13 passed**: full account suite, CAPTCHA keyboard case and real-window auth layout check. | `final-auth-reflow-results/` |
| Final presentation fixtures | **12 passed**: corrected dark fixtures, neutral names and narrow/wide auth layout. | `final-review-fixtures-results/` |
| Standalone download restart | Seed/restored stages passed; process PID changed, durable transfer IDs/local resume and interrupted export cleanup verified. | `download-restart-final.log`; originals in `.tools/download-verification/` |
| Fixture contracts | Chat, chapters, content links, community and avatar checkers passed. | Corresponding `check-*-fixture.py` commands |
| Documentation | Source/prose reviewed and registered; checker/catalog refresh and whitespace/link audit passed. | `final-documentation-check.log` |

The Android aggregate combines the full suite and ordered focused reruns. It is
**not** a claim of one uninterrupted fully green run. The final full run had 357
methods: 341 passed, 13 failed, three skipped. All 13 failed retained journeys
were resolved and rechecked. Later focused checks cover new keyboard, mini Retry,
channel-tab retention and auth reflow behavior. The aggregate identifies each
method's authoritative XML, hashes current main/test source and records the
last auth-only production change. Two restart-gated skips were replaced by the
successful standalone workflow.

The inherited Ignore is
`DownloadsSmokeTest.acceptedVideoConversionPreservesResolutionFrameRateAndBothTracks`:
emulator 37.2.12 host gfxstream previously segfaulted during accepted conversion.
Conversion consent, cancellation, error handling and export behavior are covered;
**successful video conversion is not certified** by this task.

The original baseline was 363 tests: 348 passed, 12 failed, three skipped; its
original evidence remains under `baseline/`. Intermediate failed runs remain
diagnostics, including setup/IME selector failures and renderer crashes.

## Rendered and input review

The [media manifest](evidence/media-manifest.json) records original capture paths
and SHA-256 hashes. Representative artifacts are retained under `evidence/`:

| Family | Reviewed coverage | Examples |
| --- | --- | --- |
| Theme/components | All twelve Light/Dark × accent galleries; exact numeric role/boundary checks. | [Dark Blue](evidence/palettes/opaque-gallery-dark-blue.png), [Light Amber](evidence/palettes/opaque-gallery-light-amber.png) |
| Shell/windows | 320/360/448/600/840/1200dp × font 1.0/1.3/2.0 × both appearances; no navigation-label clipping/target overlap. | [320dp, 2×](evidence/windows/responsive-320-2-0-light.png), [1200dp, 2×](evidence/windows/responsive-1200-2-0-dark.png) |
| Browse/library | Light/dark feeds, channels, continuous You rows, history/playlists/clips/download states; narrow 2× and wide representative layouts. | [You](evidence/library/you-light.png), [narrow library](evidence/library/dashboard-narrow-large-dark-opaque.png), [wide channels](evidence/browse-wide-dark/channels-wide.png) |
| Settings/accounts | Both appearances, category groups, explicit security forms, pending/failure/retry, focused fields and submit above IME; auth choices reflow visibly. | [Light Settings](evidence/flows/neutral-settings-light.png), [320dp auth modes](evidence/authentication/acceptance-auth-320-light-modes.png), [wide auth with IME](evidence/authentication/acceptance-auth-840-dark-submit.png) |
| Watch/tools | Light/dark, narrow 2× and wide supporting pane; comments, chapters, chat, player settings/fullscreen, menus and mini/IME. | [Narrow watch](evidence/flows/watch-native-narrow-large-text.png), [wide watch](evidence/flows/watch-native-wide.png), [chat](evidence/flows/watch-native-chat.png) |

Isolated component fixtures contain bounded 320dp surfaces; unused white space
outside them is not app chrome. Review caught missing theme content-color setup
in several standalone fixtures; they now use AppTheme and their refreshed dark
captures have readable text. Full-app captures use the normal application theme.

Actual Google TalkBack was enabled/bound with touch exploration confirmed.
Native focus/activation navigated Popular → Trending → You → Settings/Playback.
On synthetic watch, TalkBack activation changed MediaSession PLAYING (3) to
PAUSED (2) and opened Watch More. Screenshots and service/session dumps remain in
`accessibility-final/`; original accessibility settings were restored exactly.
[The TalkBack recording](evidence/talkback/native-talkback.mp4) is 141.31 seconds.
This is visual/input evidence, not an auditory/TTS recording claim.

Native OS Tab/Enter single-dispatch search, RTL pane geometry, software drawing,
zero-animation/reduced-motion behavior and ambient lifecycle/power/software gates
passed. Normal-speed recordings were made separately from timing and their input
sequences/final states reviewed; each run exited successfully with no source drift:

- [Navigation/search/menu/watch restoration](evidence/recordings/mobivious-recording-20261014t032421z-18359-navigation.mp4), 16.38s.
- [Preference failure/retry and confirmed speed choice](evidence/recordings/mobivious-recording-20261014t032914z-5738-settings-saving.mp4), 4.12s.
- [Playlist scrolling](evidence/recordings/mobivious-recording-20261014t033002z-15769-playlists.mp4), 8.54s.
- [Playing watch/details/recommendation scrolling](evidence/recordings/mobivious-recording-20261014t033047z-18160-watch.mp4), 8.03s.

[Recording run provenance](evidence/recording-runs.json) preserves the original
folders. These recordings use unchanged browse/watch/save code before the final
auth-only reflow; that correction has separate final screenshots and passing tests.
They establish input/presentation behavior, not a smoothness/FPS claim.

## Deferred performance handoff

The user explicitly instructed **“defer performance target. I'll do it myself”**.
The absolute deadline target and current/pre-glass/new comparative acceptance are
therefore left to the user. No performance pass or improvement is claimed.

The corrected ten-iteration software-rendered Popular run completed with ten
raw traces/JSON iterations, no source drift and 120 measured gestures at 400–434ms.
Its median P95 frame overrun was **+166.427ms**, positive fraction 1.0: it fails the
original ≤0ms target. Evidence and summary:
`.tools/ui-ux-overhaul/performance/native-swangle-popular-final/emulator-5554/20261014T030813Z/`.
Hardware-rendered attempts repeatedly segfaulted, including a no-desktop-window
attempt. Work stopped for recovery; the same AVD booted successfully using
`-gpu swangle -feature -Vulkan -no-snapshot-load -no-snapshot-save`, preserving
its data and snapshots. Failed runs remain retained and cannot be blamed solely
on either UI. Original and redesigned renderers both experienced disconnects.

The harness schedules 400ms ordinary touch events over 70% of the viewport,
records event/injection completion durations and rejects measured gestures beyond
400–475ms. UI Automator 2.4 step-based swipes wait for display frames, so older
80-step pilot runs are invalid diagnostics. Full compilation, ten iterations,
readiness, warm priming and separate cold/pagination/change scenarios remain.
Summaries use medians of per-iteration P50/P90/P95/P99 CPU/overrun metrics, retaining
frame counts, positive fractions and spread; no frame pooling or outlier dropping.

From repository root, with a stable selected emulator/device and the desired
matching display/font/theme/power configuration:

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SERIAL=emulator-5554
scripts/test-ui-performance.sh --list
scripts/test-ui-performance.sh --label redesigned --journey all
(cd .tools/ui-ux-overhaul/current-glass-source && scripts/test-ui-performance.sh --label current-glass --adapter current-glass --journey all)
(cd .tools/ui-ux-overhaul/pre-glass-source && scripts/test-ui-performance.sh --label pre-glass --adapter pre-glass --journey all)
```

Archived bases are current glass `62c91828215759c3c50f6301808b2d9accb4ed92` and
pre-glass 0.9.1 `da6ef88e086aa9eea944b104267fe359f3358fac`. Their renderer and
optimization settings are preserved; minimal build/HTTP/selector adapters and
notes are retained in those checkouts. Pre-glass ambient-on is N/A. Only the
corrected native Popular timing and listed recording journeys have execution
proof; all historical comparison and remaining timing journeys await follow-up.
The runner owns/tears down port 18080 and its reverse mapping, targets only the
isolated benchmark package and records actual configuration/source/APK/media
fingerprints plus raw JSON/Perfetto. Never compare mismatched renderer/display
configurations or run recording during timing.

[Final provenance](evidence/provenance.json) identifies current source/APK hashes
and the verification environment. Original display settings were 1344×2992,
density 480, font 1.0; [restoration is recorded here](evidence/restored-emulator-state.json). The release package,
existing release signing material and sibling Invidious server remain untouched.
