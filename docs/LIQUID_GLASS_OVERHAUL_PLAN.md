# App-wide Liquid Glass overhaul: agent handoff

**Superseded for future UI work on 13 October 2026** by the
[complete opaque Android UI/UX overhaul](ui-ux-overhaul-2026-10-13/README.md).
The implementation status, approved-at-the-time decisions and evidence below
remain a historical record. Follow the new package for the next implementation.

Status: **implemented and emulator-validated on 13 October 2026**. See the
[implementation report](liquid-glass-implementation-2026-10-13/README.md) for
package coverage, exact results, source fingerprints and validation limits.
The preparation findings below describe the unchanged pre-overhaul baseline.
Prepared on **13 October 2026** from source
`e956aabb4fbcd503f4dd51443f9a0b5812d96a7a` (Mobivious **0.10.2**, version code
**20**). The working tree was clean before this documentation/evidence handoff.

[Documentation index](README.md) · [Baseline evidence](liquid-glass-overhaul-2026-10-13/README.md) · [Current UI system](UI.md) · [Current design rationale](LIQUID_GLASS.md)

## 1. Purpose, authority and agreed direction

This file prepares the overhaul for later implementation agents. The preparation
request authorizes documentation and evidence only. It does not authorize this
documentation pass to change application code, install a build, update the server
or publish a release. When a later request authorizes implementation, execute the
packages in section 5 and use section 6 for acceptance.

The user made these product decisions:

- Cover the **whole app**, with dark mode leading and coordinated light-mode improvements. The supplied Watch screenshot is a symptom, not the scope boundary.
- Target the **transparent, dimensional character of Apple's original WWDC25 presentation**. The user expressly prefers that direction over subsequent, more opaque treatments.
- Make the material respond to interaction, scrolling and changing content. Resting controls remain still; existing video-driven ambient lighting remains.
- Give non-media screens visible **blue, teal and violet** color environments. Plain Settings/account areas are unfinished redesign work, not intentional exclusions.
- Present Settings and other form-heavy screens using **shared glass sections**, rather than separate glass bubbles for every preference.
- Allow foreground labels/icons and local contrast support to adapt over bright backgrounds.
- Improve layout, spacing, shapes and grouping where they help, while retaining navigation, features, workflows and behavior.

These decisions supersede the old design rationale where its neutral-dark or
"quiet surfaces" wording would leave Settings, account forms or other destinations
visually unfinished. This brief describes future behavior; the current guides
continue to describe implemented behavior until implementation updates them.

### Research and how to use it

The following are references, not instructions that override the user's request:

| Reference | Application to this overhaul |
| --- | --- |
| [Apple: Liquid Glass overview](https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass) | Overall design/development entry point; consistency across controls, navigation and device sizes. |
| [Apple: Meet Liquid Glass, WWDC25](https://developer.apple.com/videos/play/wwdc2025/219/) | Original visual/motion reference: Dynamics at 1:29, Adaptivity at 6:00, Principles at 10:31. |
| [Apple: Get to know the new design system, WWDC25](https://developer.apple.com/videos/play/wwdc2025/356/) | Concentric shapes, grouping, hierarchy and transitions related to their source controls. |
| [Apple: Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views) | Interactive effects, related shape transitions and shared containers for rendering efficiency. SwiftUI APIs are conceptual references, not Android dependencies. |
| [Apple HIG: Materials](https://developer.apple.com/design/human-interface-guidelines/materials) | Functional/content hierarchy, contextual legibility, accessibility and avoidance of stacked glass. |
| [Backdrop: Interactive Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar) | Transform glass through `drawBackdrop`'s `layerBlock` so pressing does not displace the sampled background. |
| [Backdrop: Backdrops](https://kyant.gitbook.io/backdrop/api/backdrops) | Recording ownership, coordinates and scene composition. |

Apple's original presentation describes adaptive Regular glass and a more
transparent Clear variant with specific media/foreground constraints. The user's
request for clear-looking glass is an aesthetic requirement, **not an instruction
to apply Apple's `clear` variant everywhere**. This is a custom Android adaptation:
use transparent material, adaptive foregrounds and local support across the app.

Apple recommends reserving Liquid Glass for the functional layer. The approved
Android design also extends translucent grouped surfaces to form-heavy screens.
Treat that as a deliberate product adaptation: organize controls on one shared
section surface and keep explanatory text, field semantics and hierarchy clear.
Do not describe the adaptation as Apple's exact implementation or guidance.

## 2. Baseline, findings and constraints

The [evidence index](liquid-glass-overhaul-2026-10-13/README.md) contains the supplied
screenshot, fresh emulator captures, provenance and checksums. Inspection used the
already-running `emulator-5554`, at **1344 × 2992**, density **480** (448dp width),
with animator duration scale **1.0**. The inspected installed release was
`net.wingress.mobivious` **0.10.2 / 20**; the installed debug app was older,
**0.10.1-debug / 19**, and was not the app used for fresh captures.

Fresh inspection covered Discover before/after scrolling, You, Settings root,
Playback preferences, Watch with an ended video, and Description interaction
states. The supplied screenshot additionally shows a colored Watch environment;
its device/build/settings provenance is not established. Historical light-mode
captures from **11 October 2026** were reviewed separately. No fresh light-mode
journey, runtime test suite or frame-timing acceptance was performed in this audit.

| Finding | Current source behavior and consequence |
| --- | --- |
| Dark glass suppresses its backdrop | `DarkGlass` uses charcoal tint `.58f` / `.62f`, saturation `.78f` and exposure `-2.8f`; the sampled environment is strongly suppressed before the fill is applied. |
| Watch has a divergent ambient treatment | `watchGlass` changes dark exposure to `-.7f`, saturation to `1.1f`, and tint to `.52f` / `.60f` when ambient material is available. Ordinary dark chrome delegates to `darkLiquidGlass`. |
| Some intended glass has no source | `LibraryControlScene` explicitly supplies `LocalGlassBackdrop = null`. Library launchers, standalone actions and menus using it take the opaque fallback. |
| Settings remains incompletely redesigned | `SettingsToggle` uses plain rows; `ActionRow` uses opaque `surfaceContainer` cards; choices inherit that presentation. The toolbar alone does not finish the screen. |
| Motion mostly changes geometry | Shared buttons/disclosures animate scale; navigation and segments animate selection position. The overhaul needs coordinated material lighting and deformation. |
| Current tests encode opaque-era assumptions | `DarkGlassPresentationTest` checks fixed `onSurfaceVariant` against sampled pixels. `VisualSurfaceRegressionTest` requires the toolbar to match an opaque surface color. |

Preserve Android 13 / API 33 minimum support, Compose/Media3 ownership and the
pinned **Backdrop 1.0.6** dependency in the [app build configuration](../android/app/build.gradle.kts).
Inspection of the installed dependency's API confirmed directional highlights,
shadows, inner shadows, `layerBlock`, and access to a layer backdrop's graphics
layer. No dependency upgrade is needed for the approved direction. Bounded
downsample/contrast sampling still requires implementation and device validation;
API availability alone is not proof of acceptable performance.

### Preserve the purpose of prior fixes

Review the [11 October visual fixes](visual-fixes-2026-10-11/FIXES.md) before changing
scene/toolbar ownership. Retain these guarantees while replacing their old treatments:

- Rounded actions expose the surrounding container at their outer corners; no rectangular backing artifacts.
- Scrolled content cannot create overlapping letterforms behind a pinned or wrapped title, including at 2× text.
- Keyboard-open forms keep focused fields and Save/Cancel reachable.
- Expanded Watch owns its full-window presentation, including system-bar areas; previous browse content must not leak through.
- Mini/watch transitions remain continuous and reversible, without an end-of-animation layout jump.
- Native video geometry, gesture priority, accessibility actions and player feedback remain correct.

## 3. Visual, rendering and interface specification

### One material system

Create internal material roles **control**, **section**, and **media overlay**.
Prominence is an emphasis parameter, not a separate optical renderer. Existing
`liquidGlass`, `browseGlass`, `watchGlass` and `mediaGlass` entry points should
delegate to the shared system while their callers are migrated.

Start calibration with the following tokens; these are design inputs, not
verified contrast results:

| Role | Dark tint alpha | Light tint alpha | Sampled blur |
| --- | ---: | ---: | ---: |
| Ordinary control | .14 | .20 | 4dp |
| Prominent control | .20 | .26 | 4dp |
| Grouped section | .20 | .28 | 10dp |

Begin with neutral exposure and the existing 8dp / 18dp control lens geometry.
Remove blanket dark exposure crushing. Calibrate against section 6 and record
final tokens centrally, including any changes from this starting table. Screen
implementations must not introduce their own competing optical constants.

Media overlays do not sample the decoder, so the sampled-blur table does not
apply to the video surface. Use shared optical styling with localized foreground
support or a safe generated environment. Keep the picture clear of a full-frame
contrast scrim introduced solely to make glass controls readable.

Define the material through directional highlights, restrained reflections, edge
refraction and soft elevation. Replace conspicuous continuous outlines with a
rim whose intensity changes around the geometry. Background hue and movement
must survive inside the shape; a transparent fill on an already-crushed backdrop
does not meet the requirement.

### Color, shape and grouping

Use broad blue, teal and violet pools over a dark blue-black environment, with
a pale counterpart in light mode. The colored environment should remain visible
across non-media destinations, especially Settings and You. These are background
lighting layers, not saturated solid fills on every control.

Use artwork/existing ambient palettes in media contexts; missing artwork uses
the designed non-media environment. Colors may change with content or navigation
but must not run ornamental idle loops. Preserve semantic error/destructive hues.

Use one glass surface per related form section. Interior rows use dividers,
subtle state fills and interaction feedback, rather than additional stacked glass.
Keep text-field editing boundaries, toggles and validation recognizable. Preserve
48dp minimum touch targets, wrapping text and concentric spacing/radii. The
existing 20dp main inset and 24dp card radius are starting geometry, not reasons
to retain awkward grouping or oversized capsules.

### Scene ownership and backdrop data flow

Introduce shared scene infrastructure exposing a safe backdrop and its
color/contrast information. The source graph must remain acyclic:

```text
background/environment recording -> grouped glass sections
page content recording           -> sibling floating chrome
generated ambient colors         -> Watch material (never the decoder)
```

Grouped sections sample a background-only scene. Floating chrome samples a
separately recorded page scene. No consuming glass may sample a recording that
contains itself; children inside the page recording must never inherit that same
recording as their source. Do not simply remove all current null providers.

Replace deliberate null sources where they currently prevent intended glass.
Clip backing layers and consuming material consistently. Popup/dialog windows
own independent scenes seeded with their originating palette, without references
to another window's recording. A missing source on an intended glass target is
an integration defect, not an acceptable finished appearance.

Media3 retains the sole decoding `SurfaceView`, service/controller ownership and
existing geometry/lifecycle behavior. Reuse the current bounded ambient palette;
do not add decoder copies to individual controls.

### Adaptive foreground interfaces

Add internal `GlassContentColors`, material-role and scene-contrast state.
Glass surfaces must provide their actual foreground colors to labels and icons,
instead of assuming the fixed light foreground of the dark theme. Use a shared
surface/composition-local interface for consumers that need those colors;
modifier-only styling must not silently leave the old foreground assumptions.

Use existing generated palettes when available. For scrolling content, obtain
bounded shared statistics from a downsampled Compose recording, with **at most
four updates per second while content changes**, no idle polling and no
per-control readback. Bound allocations and reuse sampling resources. Verify
this path on-device as part of the foundation package before spreading it to
all screens.

Choose light/dark foregrounds to minimize required contrast support. Use local
support around labels/icons over mixed backgrounds. Stabilize polarity changes
to prevent flicker and avoid a transition through unreadable intermediate colors.
If fresh statistics are unavailable, retain a stable foreground with local
support while awaiting the next valid scene update. Do not darken the entire
capsule/page merely to keep every label pearl-colored.

Preserve distinct error, destructive, pending, disabled, focused and selected
states. Keep non-color selection cues and existing accessible state descriptions.
Adaptive foregrounds must cover secondary/help text as well as primary labels.

These interfaces are internal. No server API, persisted preference schema,
account synchronization contract or feature behavior change is required.

### Interaction and motion

Create shared interaction state for press position, pressed, focused and selected
states. Reuse existing interaction sources and control semantics.

- Touch produces localized illumination, a small lift and restrained elastic deformation.
- Limit displacement to **2dp**; begin near **2%** deformation for small controls and **1%** for broad surfaces.
- Apply transforms through Backdrop's `layerBlock` so the sampled background remains aligned.
- Coordinate selection position, highlight and shape changes across dock destinations, tabs and segments.
- Present menus/panels from their triggering control where geometry permits; animate disclosure content with its chevron.
- Keep motion interruptible and reverse from its current displayed state. Retain existing player presentation/gesture ownership.
- Preserve click, long-press tooltips, scroll, seek, drag, keyboard and accessibility priority. Decorative pointer tracking must not consume those gestures.

Honor system animation duration scale and reduced motion. Reduced transparency
uses coherent opaque surfaces; software rendering retains a solid fallback.
Neither fallback becomes the default dark appearance. Preserve current ambient
sampling gates for lifecycle, power saving, motion, transparency, PiP and playback.

## 4. Source and test map

Links below resolve relative to this file. Inspect current source again before
implementation if the checkout has advanced from the recorded revision.

| Subsystem | Source entry points |
| --- | --- |
| Theme, material, shared selection | [LiquidDesign.kt](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/main/java/net/wingress/mobivious/ui/LiquidDesign.kt): `Liquid`, `DarkGlass`, material modifiers, `GlassIconButton`, `LiquidSegments`. |
| Browse scenes and chrome | [BrowseGlassUi.kt](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/main/java/net/wingress/mobivious/ui/BrowseGlassUi.kt): `BrowseGlassSurface`, `BrowseArtworkSurface`, tabs, sorting and panel toolbar. |
| Library scenes and panels | [LibraryGlassUi.kt](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/main/java/net/wingress/mobivious/ui/LibraryGlassUi.kt): `LibraryControlScene`, `LibraryActionButton`, `LibraryPanel`, confirmations and toolbar. |
| Watch scenes, actions and disclosures | [WatchGlassUi.kt](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/main/java/net/wingress/mobivious/ui/WatchGlassUi.kt), [WatchActionsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/WatchActionsUi.kt), [SubscriptionControl.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionControl.kt). |
| Application shell, dock, sidebar and mini-player | [MobiviousApp.kt](../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt), [LiquidNavigation.kt](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/main/java/net/wingress/mobivious/ui/LiquidNavigation.kt). |
| Settings, preference rows, menus and accounts | [SettingsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt), [ActionControlsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/ActionControlsUi.kt), [AccountUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt). |
| Optional settings/tools | [SponsorBlockUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt), [DeArrowUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt), [AiFilterUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt), [VisibilityUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt). |
| Discovery, search and subscriptions/channels | [DiscoveryUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/DiscoveryUi.kt), [SearchUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt), [SubscriptionBrowseUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionBrowseUi.kt), [SubscriptionsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt), [ChannelDescriptionUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt). |
| You/library destinations | [YouUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/YouUi.kt), [PlaylistUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt), [LibraryActionsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/LibraryActionsUi.kt), [DownloadsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/DownloadsUi.kt), [ClipsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt), [RssUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt). |
| Reading and secondary surfaces | [CommentsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt), [VideoInformationUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/VideoInformationUi.kt), [ChaptersUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt), [PlaybackQueueUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt), [LiveChatUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt), [CommunityUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt). |
| Video, motion and ambient invariants | [PlayerPresentation.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt), [PlayerControlsUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlayerControlsUi.kt), [PlayerUi.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt), [WatchAmbient.kt](../android/app/src/main/java/net/wingress/mobivious/ui/WatchAmbient.kt), [AmbientPalette.kt](../android/app/src/main/java/net/wingress/mobivious/ui/AmbientPalette.kt), [PlaybackVideoSurface.kt](../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt). |

Existing device tests are under
`android/app/src/androidTest/java/net/wingress/mobivious/`. The
[test catalog](reference/TEST_CATALOG.md) lists exact methods and ignored cases.

| Area | Existing suites to extend/run |
| --- | --- |
| Optical matrix and palette contrast | [DarkGlassPresentationTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/DarkGlassPresentationTest.kt), [AmbientModePresentationTest](../android/app/src/androidTest/java/net/wingress/mobivious/AmbientModePresentationTest.kt). |
| Control states and previous visual fixes | [GlassControlPresentationTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/GlassControlPresentationTest.kt), [VisualSurfaceRegressionTest](../android/app/src/androidTest/java/net/wingress/mobivious/VisualSurfaceRegressionTest.kt). |
| Settings, forms, popups and accounts | [RemainingGlassJourneyTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/RemainingGlassJourneyTest.kt). |
| Browse/subscription/channel flows | [SubscriptionGlassPresentationTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionGlassPresentationTest.kt), [SubscriptionGlassJourneyTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionGlassJourneyTest.kt). |
| You/library/downloads/forms | [LibraryGlassPresentationTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/LibraryGlassPresentationTest.kt), [LibraryGlassJourneyTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/LibraryGlassJourneyTest.kt). |
| Watch controls, comments and feedback | [WatchGlassPresentationTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/WatchGlassPresentationTest.kt), [WatchCommentsPresentationTest](../android/app/src/androidTest/java/net/wingress/mobivious/WatchCommentsPresentationTest.kt), [PlayerFeedbackPresentationTest](../android/app/src/androidTest/java/net/wingress/mobivious/PlayerFeedbackPresentationTest.kt). |
| Shell/System/responsive/reduced motion/playback | [LiquidRedesignSmokeTest](https://github.com/Hanif-F/codex-mobile-invidious/blob/62c91828215759c3c50f6301808b2d9accb4ed92/android/app/src/androidTest/java/net/wingress/mobivious/LiquidRedesignSmokeTest.kt), [AmbientModeSmokeTest](../android/app/src/androidTest/java/net/wingress/mobivious/AmbientModeSmokeTest.kt), [LiveChatSmokeTest](../android/app/src/androidTest/java/net/wingress/mobivious/LiveChatSmokeTest.kt). |

## 5. Implementation packages and integration order

Start with the foundation. Settings/accounts, browse/navigation, library and Watch
packages depend on its scene, content-color and interaction interfaces. Separate
agents can own surface families once that contract is stable; shared renderer
changes remain coordinated through one foundation owner. No agents were spawned
as part of preparing this document.

| Package | Work and exit criteria |
| --- | --- |
| 1. Foundation | Shared renderer/roles, environment, scene ownership, adaptive foregrounds, interaction primitives and a test-only material showcase. Demonstrate transparency, bounded contrast sampling and correct transform alignment before migration. |
| 2. Settings/accounts | Redesign root categories and every settings page; group preferences on shared surfaces. Cover switches, choices, fields, save/error footers, authentication/CAPTCHA, server settings, account credentials/sessions/tokens, blocked channels and community tools. Retain save/cancel, validation and account isolation. |
| 3. Navigation/browsing | Migrate dock/sidebar, mini-player chrome, Discover, global/scoped search, filters/country selection, subscriptions, channel identity/tabs/sorting and creator panels. Retain independent feed positions and search-origin restoration. |
| 4. You/library | Migrate all dashboard launchers, playlist segments/details/editors, history, downloads/progress/details/export actions, clips/editors, RSS and confirmations. Cover guest, signed-in, empty, loading, error and pending states. |
| 5. Watch/secondary surfaces | Migrate Watch actions, subscription controls, disclosures, descriptions, comments/replies, chapters, queues, chat/player settings, community posts and image viewers. Preserve video geometry, gesture priorities, decoder ownership and player presentation continuity. |
| 6. Integration | Audit every destination against this brief, verify light/dark/System, accessibility/responsive behavior and performance, finish evidence, then update current design/UI documentation and reviewed source coverage. |

The overhaul must preserve persisted data, account/instance boundaries, API
requests, playback ownership and existing workflows. No backend deployment,
preference migration, new theme collection or release is part of this design
work unless separately requested. Native Android file pickers, IME, system
permission UI and PiP remain platform-owned surfaces.

## 6. Verification, commands and completion

### Rendered acceptance matrix

Extend the existing presentation suites with meaningful rendered checks:

- Black, white, saturated, checkerboard and moving-artwork backgrounds; include ordinary/prominent controls, grouped sections, popup scenes and ambient Watch.
- Light/dark/System and reduced transparency, plus default, pressed, focused, selected, disabled, busy and error states.
- Actual adaptive foreground contrast: **4.5:1** for normal text and **3:1** for meaningful icons/large text. Check secondary text and applicable enabled states; disabled appearance must remain recognizable.
- Visible backdrop transmission and response to background changes. Include samples away from rims, shadows and glyphs so a bright border cannot disguise an opaque interior.
- Transparent/opaque-mode comparisons and fixed-source/no-source behavior. Test that real scene integration exists on intended glass targets.
- Press-transform alignment, rapid repetition/cancellation, selection reversals, menu dismissal and disclosure transitions.

Update `DarkGlassPresentationTest` to use the surface's actual foregrounds and
retain contrast assertions. Replace
`wrappedToolbarTitleHasAnOpaqueBackdropAcrossItsEntireHeight` with tests of title
readability, absence of overlapping background glyphs, measured insets and clear
glass behavior. Retain the rounded-corner regression and multipart toolbar checks.
Do not solve test failures by weakening legibility thresholds or reinstating a
full dark plate on every control.

### Device and behavior matrix

Verify ordinary phone width, **320dp / 2× text**, wide/sidebar layouts,
portrait/landscape, keyboard-open forms, gesture/three-button navigation,
software fallback and reduced motion. Include both large-text scroll states and
initial presentation. Preserve PiP/background playback, ambient sampling gates,
mini/watch/fullscreen transitions, touch exploration and accessible control names.

Use fixture-based debug tests for interactions and account writes, not the live
release account. Other ADB devices are connected: explicitly select
`ANDROID_SERIAL=emulator-5554`. Rebuild debug and test APKs; the installed old
debug APK is not acceptance evidence for current source.

### Exact commands for later implementation

Run from the repository root with the existing JDK/SDK. These commands are
instructions for the later implementation; they were not run as UI acceptance
during this documentation pass.

```sh
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SERIAL=emulator-5554

# Compilation, domain checks and static diagnostics.
(
  cd android
  ./gradlew :app:testDebugUnitTest :app:assembleDebug \
    :app:assembleDebugAndroidTest :app:lintDebug --console=plain
)

# Core materials, visual regression, shared control and shell behavior.
scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.DarkGlassPresentationTest,net.wingress.mobivious.GlassControlPresentationTest,net.wingress.mobivious.VisualSurfaceRegressionTest,net.wingress.mobivious.LiquidRedesignSmokeTest

# Settings/accounts, subscriptions/channels and library journeys.
scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.RemainingGlassJourneyTest,net.wingress.mobivious.SubscriptionGlassPresentationTest,net.wingress.mobivious.SubscriptionGlassJourneyTest,net.wingress.mobivious.LibraryGlassPresentationTest,net.wingress.mobivious.LibraryGlassJourneyTest

# Watch, ambient behavior, comments, player feedback and chat.
scripts/test-android.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.WatchGlassPresentationTest,net.wingress.mobivious.WatchCommentsPresentationTest,net.wingress.mobivious.PlayerFeedbackPresentationTest,net.wingress.mobivious.AmbientModePresentationTest,net.wingress.mobivious.AmbientModeSmokeTest,net.wingress.mobivious.LiveChatSmokeTest

python3 scripts/check-documentation.py
```

The [test runner](../scripts/test-android.sh) uses an already-running device,
generates ignored fixture media and owns its local fixture/reverse mapping on
port 18080. It also runs JVM tests and debug lint. Run focused groups separately;
do not start competing fixtures on that port. See the [testing guide](TESTING.md)
for readiness requirements and existing suite limitations. Record exact failures,
ignored cases and source revision; do not report a historical pass as current.

For repeatable normal-speed evidence and performance, use the same emulator,
scene, duration, refresh rate and animation settings before/after implementation:

```sh
# Capture logs outside the frozen baseline directory, using unique run names.
mkdir -p .tools/liquid-glass-overhaul
adb -s emulator-5554 shell dumpsys gfxinfo net.wingress.mobivious.debug reset
# Perform the documented interaction script at normal speed.
adb -s emulator-5554 shell dumpsys gfxinfo net.wingress.mobivious.debug framestats \
  > .tools/liquid-glass-overhaul/frame-timing.txt

adb -s emulator-5554 shell screenrecord --time-limit 30 \
  /sdcard/mobivious-liquid-overhaul-motion.mp4
# During recording, perform the same interaction script in another terminal.
adb -s emulator-5554 pull /sdcard/mobivious-liquid-overhaul-motion.mp4 \
  .tools/liquid-glass-overhaul/motion.mp4
```

Compare frame duration distributions, missed frames and visible hitches. Stop
unneeded sampling/animation in settled screens. Report physical-device performance
separately; emulator timing does not prove low-end hardware behavior. If emulator
renderer limitations block a scenario, record the gap rather than claiming a pass.

### Evidence and completion checklist

Keep the [13 October baseline](liquid-glass-overhaul-2026-10-13/README.md) immutable.
Save future implementation captures in a new dated evidence collection, with
source/build/device/settings, exact commands, pass/fail/ignored status, paths and
before/after comparisons. Existing tests write screenshots under
`/data/local/tmp/mobivious-liquid/`, `/data/local/tmp/mobivious-subscriptions-glass/`,
`/data/local/tmp/mobivious-library-glass/`, and related suite-specific directories;
inspect each suite for its actual output path and pull only that run's artifacts.

- [x] All six packages complete, including every settings category and secondary surface family.
- [x] Dark glass visibly transmits backdrop color and movement and has directional depth.
- [x] Light mode shares the new system and passes its own rendered/behavior checks.
- [x] Foreground contrast passes without making ordinary transparent controls uniformly opaque.
- [x] Press, selection, disclosure and menu behavior is responsive, interruption-safe and visible in normal-speed recordings.
- [x] Reduced motion/transparency, software fallback policy, large text, keyboard and accessibility scenarios pass; complete software-renderer and human spoken usability coverage remain documented limits.
- [x] Prior corner, title-overlap, system-bar and player-transition fixes remain covered.
- [x] Builds, lint, focused tests and documentation checks have current recorded results; limitations are explicit.
- [x] Matching screenshots, motion evidence and same-emulator frame timing are delivered.

For **this preparation step**, completion means the handoff, baseline assets,
provenance and links are saved and documentation checks pass. It does not mean
the overhaul, proposed numerical calibration, contrast sampler, animation quality,
physical-device performance or future acceptance checklist has been validated.
