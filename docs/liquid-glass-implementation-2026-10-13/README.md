# Liquid Glass implementation — 13 October 2026

[Implementation brief](../LIQUID_GLASS_OVERHAUL_PLAN.md) · [Immutable baseline](../liquid-glass-overhaul-2026-10-13/README.md) · [UI system](../UI.md) · [Design rationale](../LIQUID_GLASS.md)

The app-wide overhaul is implemented in the working tree over
`e956aabb4fbcd503f4dd51443f9a0b5812d96a7a`, retaining version 0.10.2 / 20,
Android 13 minimum support and Backdrop 1.0.6. This collection records debug
emulator acceptance, not a production release or physical-device benchmark.
[manifest.json](manifest.json) identifies source and APK fingerprints, device,
settings, assets and verification boundaries. [screenshots.json](screenshots.json)
indexes 86 selected suite captures with hashes and dimensions. Additional manual
captures and normal-speed videos are included below.

## Implemented scope

| Package | Result and evidence |
| --- | --- |
| Foundation | One control/section/media-overlay renderer; directional depth, shared press/focus/selection feedback, adaptive primary/secondary/icon foregrounds, bounded spatial statistics and opaque fallback. Actual text contrast ≥4.5:1 and icon contrast ≥3:1 are tested alongside clear interior transmission. |
| Settings and account | Blue/teal/violet environments, grouped preferences in every category, recognizable field boundaries, credentials/CAPTCHA, sessions/tokens, server, SponsorBlock, DeArrow, AI and About. [Category contact sheet](settings-final-contact.jpg). |
| Navigation and browsing | Dock/sidebar and moving selection markers, mini-player, Discover, global/scoped search, filters, feed/directory and channel controls. Safe page/background scene ownership replaces deliberate null sources. |
| You and library | Shared launchers, playlist/editor/history, downloads and track choices, clip editor, RSS/exports and destructive confirmations. [Forms and library contact sheet](families-contact.jpg). |
| Watch and secondary surfaces | Shared actions, subscription states, disclosures, comments, chapters, queue, chat/settings, images and player controls. Native decoder ownership, geometry, gesture priority and PiP remain covered. |
| Integration | Light/dark/System, 320dp/2× text, wide layouts, portrait/fullscreen landscape, IME, reduced motion/transparency, fallback policy, TalkBack state and three-button navigation. [Core contact sheet](core-contact.jpg). |

Final optical tokens remain the brief's starting calibration: control tint
`.14/.20` dark and `.20/.26` light for ordinary/prominent controls, section tint
`.20/.28`, blur 4dp/10dp, neutral exposure, and 8dp/18dp lens geometry. Media
overlays use a localized `.64` safe plate and never read the decoder. Transforms
are limited to 2dp lift and 2% small/1% broad deformation.

Sections sample background-only recordings; sibling floating chrome samples a
separate page recording. Each popup/dialog/tooltip owns an independent environment
seeded with originating colors. Group interiors use quiet fills and dividers,
without stacking another optical layer. Transient messages preserve Undo and
dismiss semantics through the same material system.

The shared sampler reads only a GPU-downsampled 24×48 image, at most every 250ms
while its scene changes. It reuses the scratch layer/buffer, stops when settled,
and is gated by lifecycle, hardware acceleration and transparency preference.
Generated Watch lighting reuses the existing ambient palette without additional
Compose/decoder readback. Its canvas opacity supplies conservative luminance
bounds even when radial lights overlap. Designed non-media environments use
analytic bounds from each light's peak opacity and painting order, so their
labels stay quiet without unnecessary backing. Foreground polarity changes
atomically with hysteresis. Arbitrary page imagery keeps conservative local
glyph/icon support even when the coarse sample appears uniform: a narrow bright
band can escape that grid. The [real-artwork dock check](real-artwork-dock.png)
and rendered regression cover that case. The final [You check](final-you.png)
confirms quiet generated backgrounds without that extra backing.

## Validation results

| Run | Result | Record |
| --- | --- | --- |
| Core material, foundation, controls, visual regressions and shell | 22 passed, 0 failed/skipped | [core.log](logs/core.log) |
| Accounts/settings, subscriptions/channels, library, Watch, player controls, ambient and chat | 58 passed, 0 failed/skipped | [families.log](logs/families.log) |
| Final footer/tooltip/ambient changes, CAPTCHA/system bars and playback gesture regressions | 16 passed, 0 failed/skipped | [integration.log](logs/integration.log) |
| Final exact 320dp/2× text footer/IME and explicit light channel/subscription journey | 2 passed, 0 failed/skipped | [layout-light.log](logs/layout-light.log) |
| Final artwork-band/quiet-background regressions, optical matrix, shell and every Settings category | 24 passed, 0 failed/skipped | [contrast.log](logs/contrast.log) |
| Final library journeys and explicit light download progress/failure/details | 2 passed, 0 failed/skipped | [library-light.log](logs/library-light.log) |
| Final no-artwork corners/menu continuity, library and light channel/subscription journey | 8 passed, 0 failed/skipped | [corners.log](logs/corners.log) |
| JVM domain suite | 347 passed, 0 failed/skipped | [JVM summary](logs/jvm-summary.json) |
| Debug build/test APK and lint | Successful; 0 lint errors, 36 warnings | Build tasks in the run logs; [lint summary](logs/lint-summary.json) |

The 132 selected device executions include repeated focused checks after shared changes.
The lint warnings concern existing dependency updates, SDK checks, KTX suggestions,
the exported playback service and native clip-preview inflation. No dependency,
permission or service contract was changed to silence them. Exact class filters
and commands are recorded in [commands.txt](logs/commands.txt).

Visual review covered the optical matrix (black, white, saturated and checker
sources; both appearances; ordinary/prominent/section and opaque variants),
changing video lighting, shaped corners, wrapped titles, initial/scrolled settings,
forms, menus, confirmations, Watch drawers and responsive layouts. Screenshots
are selected from the named runs; contact sheets are inspection aids, not substitutes
for the full-resolution PNGs or pixel assertions.

Manual checks additionally confirmed TalkBack bound with
`touchExplorationEnabled=true`, named controls and visible popup focus
([state](logs/accessibility.txt), [menu](talkback-menu.png)); ADB actions opened
and dismissed the menu and changed disclosure state. This is not a human spoken
screen-reader usability assessment. Three-button navigation was checked with
[Playback](three-button-playback.png), a [docked keyboard](three-button-server-ime-restarted.png),
and [320dp/2× text field](three-button-server-320dp-2x-docked-ime.png) / [Connect action](three-button-server-320dp-2x-docked-action.png).
Gboard initially exposed a stuck collapsed input view; restarting its process
restored the docked keyboard without app changes. Device configuration and
accessibility settings were restored after inspection.

## Normal-speed motion and frame timing

The original baseline assets remain unchanged. A separate pre-change debug
recording was made from the unchanged application source before implementation:
[Settings before](motion/settings-before/motion.mp4). The matching
[Settings after](motion/settings-after/motion.mp4) uses the same emulator,
1344×2992 display, density 480, font scale 1.0, dark appearance and animation
scale 1.0. Android's encoder fell back to 720×1280 for both recordings. Playback
speed and frame timestamps were retained.

Both runs open Settings → Playback, swipe up twice/down once, Cancel, then return
to You. [Before input events](motion/settings-before/input-events.json) and
[after input events](motion/settings-after/input-events.json) are within about
0.15 seconds for each action. Grouping and item heights intentionally change;
the scroll offsets/content painted are therefore not pixel-identical. The server
label and some preference values also differ. This is a repeatable interaction
comparison, not a controlled statistical benchmark.

| Metric | Before | After |
| --- | ---: | ---: |
| Rendered frames | 111 | 188 |
| Deadline-missed/janky frames | 24 (21.62%) | 28 (14.89%) |
| 50th percentile | 27ms | 29ms |
| 90th percentile | 48ms | 44ms |
| 95th percentile | 101ms | 93ms |
| 99th percentile | 150ms | 200ms |
| Legacy jank classification | 55.86% | 90.43% |

Raw [before](motion/settings-before/frame-timing.txt) and
[after](motion/settings-after/frame-timing.txt) `gfxinfo framestats` are retained.
The distributions are mixed: the median, 99th percentile and legacy classification worsened,
while deadline-miss percentage and the 90th/95th percentiles improved. More frames
are rendered during the new interaction feedback. Emulator GPU histogram sentinel
buckets are not reliable physical GPU measurements. Physical/low-end performance
has not been measured, and these numbers do not establish a universal speedup.

[Comparison frames](motion-comparison.jpg) show the new grouped colored treatment.
The [Watch recording](motion/watch-after/motion.mp4) and
[input timeline](motion/watch-after/input-events.json) cover a localized DOWN/UP,
description expansion/collapse, More/dismiss, Watch → mini, interruption of
restoration with Back, and full restoration. [Transition frames](watch-motion-contact.jpg)
were inspected for clipping, end jumps, stale pictures and lost actions. The live
picture remains continuous; the intentional Watch/browse crossfade remains.
The emulator still has occasional frame hitches, reflected in the timing data.

## Corrections, adaptations and limits

- Fixed background lighting painting outside a small Canvas, which had tinted
  foreground content; environment recordings now clip to their bounds.
- Fixed first-frame sampling before a recording completed; sampling waits for
  the next frame and keeps conservative local support until valid statistics exist.
- Fixed a delayed browse return resurrecting an empty Watch page after queue
  closure; restoration now checks current playback and destination actions read
  current presentation state.
- Fixed arriving ambient lighting disposing open menus; Watch content keeps one
  stable composition call site while its material source changes. A regression
  test covers both arrival and removal of the ambient source.
- Fixed large-text preferences drawing behind Save/Cancel by reserving a measured
  footer viewport. Pinned title environments retain glyph masking and measured
  insets while their controls remain transparent.
- Fixed narrow bright artwork bands defeating navigation-label contrast. Page
  imagery now keeps conservative local support; generated backgrounds use their
  actual opacity bounds to avoid adding visible backing to otherwise readable
  library and Settings labels. Both cases have rendered regression tests.
- Removed a rectangular wash behind standalone download actions in light mode.
  No-artwork control rows now reuse the safe background scene instead of painting
  a local artwork environment. The shaped-corner regression covers this wrapper,
  and library/channel journeys recheck the affected call sites.
- The toolbar title's generated reading environment deliberately masks scrolling
  text. This preserves the prior overlap fix and uses the new colored environment.
- Native file/share/IME/PiP UI and the existing Media3 clip-preview transport remain
  native. The preview is a content editing tool; replacing its transport would
  risk preview functionality outside this material overhaul.
- Software fallback is tested by providing a non-accelerated View policy and
  rendering the opaque result on the hardware emulator. A complete software
  Compose renderer and a physical Android 13 device were not separately exercised.
- No known overhaul regression remains in the covered scenarios. Live-server
  account mutations, release publishing and every possible third-party server
  response were outside this fixture-based acceptance.

The current UI guides and source/test inventories were refreshed after these
checks. The immutable nine-image baseline is rechecked against its original hashes.
