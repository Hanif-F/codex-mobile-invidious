# Opaque Android design system

[Overhaul entry point](README.md) · [Screen contracts](SCREENS_AND_FLOWS.md)

**Target specification; not the currently implemented theme.** Build on
[Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)
using its semantic color, typography and shape roles. Keep the existing pinned
Compose stack unless a separately justified compatibility change is necessary.

## Surfaces and color roles

All theme colors below are opaque sRGB hex values. No wallpaper lookup or
backdrop-dependent foreground polarity. Neutral surfaces stay the same for every
accent. Apply the resolved Light/Dark/System choice once above the app shell.

| Material role | Light | Dark |
| --- | --- | --- |
| background, surface | #F8F9FA | #131416 |
| onBackground, onSurface | #1B1B1F | #E4E4E9 |
| onSurfaceVariant | #45464F | #C6C6D0 |
| surfaceDim | #D9DADE | #131416 |
| surfaceBright | #F8F9FA | #393A3E |
| surfaceContainerLowest | #FFFFFF | #0E0F11 |
| surfaceContainerLow | #F2F3F5 | #1B1C1F |
| surfaceContainer | #EEEFF1 | #202124 |
| surfaceContainerHigh | #E9EAEC | #2B2C2F |
| surfaceContainerHighest, surfaceVariant | #E3E4E8 | #343539 |
| secondary | #595A63 | #C6C6D0 |
| onSecondary | #FFFFFF | #2F3038 |
| secondaryContainer | #E2E2EB | #45464F |
| onSecondaryContainer | #1A1B22 | #E2E2EB |
| outline | #74747E | #90909B |
| outlineVariant | #C5C6D0 | #45464F |
| error | #BA1A1A | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #410002 | #FFDAD6 |
| inverseSurface | #303034 | #E4E4E9 |
| inverseOnSurface | #F2F0F4 | #303034 |
| shadow, scrim | #000000 | #000000 |

`surfaceTint` is transparent; tonal elevation must not recolor neutral surfaces.
Use surface-container roles for hierarchy. `inversePrimary` is the selected
accent's primary from the opposite appearance. Tertiary roles alias primary roles
to avoid an unrelated second accent. Fixed primary/tertiary roles use the selected
accent's light container for fixed, dark primary for fixedDim, light on-container
for onFixed and dark container for onFixedVariant. Secondary fixed roles follow
the same mapping using secondary colors. Set these explicitly rather than
inheriting library-default purple.

### Accent presets

Each cell contains `primary / onPrimary / primaryContainer / onPrimaryContainer`.
These are the complete accent-role values, not seeds for runtime palette generation.

| Stable ID / label | Light roles | Dark roles |
| --- | --- | --- |
| blue / Blue | #125BB5 / #FFFFFF / #D6E4FF / #001B3D | #A8C7FA / #062E60 / #153F6B / #D6E4FF |
| teal / Teal | #006B60 / #FFFFFF / #9FF2DF / #00201A | #7AD7C6 / #00382F / #005047 / #9FF2DF |
| green / Green | #356A20 / #FFFFFF / #BDECA2 / #062100 | #A2D884 / #123800 / #215112 / #BDECA2 |
| violet / Violet | #6750A4 / #FFFFFF / #EADDFF / #21005D | #D0BCFF / #381E72 / #4F378B / #EADDFF |
| amber / Amber | #7A5900 / #FFFFFF / #FFDF92 / #261900 | #F3C34D / #402D00 / #5C4300 / #FFDF92 |
| rose / Rose | #A33358 / #FFFFFF / #FFD9E3 / #3F0017 | #FFB1C8 / #630E2F / #812044 / #FFD9E3 |

Blue is the absent/unknown-value default. Render the picker as labeled radio rows
with a swatch and checkmark. Color alone must not identify selection. Theme and
accent changes update an open screen, menu or editor without resetting its state.

Use primary for enabled text actions and active indicators; primaryContainer for
selected chips/navigation items. Preserve SponsorBlock category colors, artwork,
warnings and errors independently of the selected accent. Error copy and icons
identify a problem even when the user selects Rose.

### Surface assignment and contrast

| Content | Surface / foreground |
| --- | --- |
| Page and continuous feeds | surface / onSurface; supporting text onSurfaceVariant |
| Top app bar, navigation bar/rail, mini-player | surfaceContainer / onSurface; selected navigation primaryContainer / onPrimaryContainer |
| Settings groups and editor sections | surfaceContainerLow / onSurface; one shared group, no card per row |
| Sheets/dialogs/menus/tooltips | surfaceContainerHigh / onSurface; opaque handle region and popup content |
| Fields | surfaceContainerHighest / onSurface; outline at rest, primary when focused |
| Filled primary action | primary / onPrimary |
| Snackbar | inverseSurface / inverseOnSurface; action inversePrimary |
| Player overlays | White content over a local black scrim; opaque neutral player settings sheet |

Normal enabled text must reach 4.5:1; essential icons, control boundaries and
non-text indicators 3:1 against adjacent colors. These project gates follow
[text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
and [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)
guidance. Do not lower supporting-text contrast with arbitrary alpha. The preset
on-role pairs and primary text on neutral surfaces must pass these gates.

`outlineVariant` is for decorative dividers, not the sole boundary of an editable
control. Disabled Material controls use the standard disabled state treatment and
disabled semantics; they are not presented as available actions.

Opaque means no scenery shows through ordinary UI surfaces. A standard modal
scrim uses black at 32%; ripple/focus state layers remain permitted. Over video,
top/bottom control scrims use black at 72% at their rail and fade to transparent
within at most 96dp. Keep controls in the contrast-safe portion; a central
play/pause control uses a black 72% local backing. No video readback is needed.
Captions keep the existing transparent-window/glyph-edge behavior and native
video backing/shutters remain black. User-controlled chat overlay opacity remains.

## Typography, geometry and motion

Use the platform sans-serif font and standard Material 3 `Typography()` metrics;
no bundled font, negative letter spacing or custom oversized page branding.
The role assignments below are fixed:

| Usage | Role | Size / line height / weight |
| --- | --- | --- |
| Top app-bar title | titleLarge | 22sp / 28sp / 400 |
| Main screen section heading | titleMedium | 16sp / 24sp / 500 |
| Watch title | titleLarge | 22sp / 28sp / 400; allow wrapping |
| Feed/video title | titleMedium | 16sp / 24sp / 500; two lines then ellipsis |
| Preference label and body | bodyLarge | 16sp / 24sp / 400 |
| Metadata/help/comment body | bodyMedium | 14sp / 20sp / 400 |
| Compact metadata | bodySmall | 12sp / 16sp / 400 |
| Buttons/chips | labelLarge | 14sp / 20sp / 500 |
| Navigation labels/badges | labelMedium | 12sp / 16sp / 500 |

Unused typography roles retain Material defaults. Essential error/help text wraps
without a fixed line limit. Dates/counts reuse existing shared formatters.

| Token | Value and application |
| --- | --- |
| Spacing scale | 4, 8, 12, 16, 24, 32dp; no per-screen substitute scale. |
| Page inset | 16dp compact, 24dp medium/expanded; full-bleed fitted video may extend to the content edge. |
| Gaps | 8dp icon/text; 12dp row internal; 16dp list-item/group gap; 24dp section separation. |
| Shape extraSmall/small/medium/large/extraLarge | 4 / 8 / 12 / 16 / 28dp; circles only for avatars/swatch marks; full rounding for standard chips/buttons. |
| Thumbnails / media groups | 12dp corners; no surrounding elevated bubble for every feed item. |
| Standard icon / avatar | 24dp icon; 40dp row avatar; 64dp identity avatar. |
| Minimum target | 48×48dp, laid out without overlapping neighboring targets. |
| Standard row | Minimum 56dp single-line, 72dp with supporting text; grows with text. |
| Mini-player | Minimum 72dp, grows if needed; full content width with a top divider, no floating margins. |
| Elevation | 0dp pages/rows; 1dp app chrome; 3dp dialogs/sheets/menus; no custom glow/reflection. |

Use default Material ripple and focus feedback. No press scaling, lens distortion,
springy selection travel, bounce or decorative idle animation. For custom app
transitions use 150ms fades; player presentation retains a bounded 200ms transition
with the existing single-decoder geometry handoff. New interactions interrupt
transitions immediately. Never delay navigation or persistence until animation
completion. Respect system animator scale; with reduced motion/zero scale, use
immediate state changes. Buffering/progress indicators animate only while work is
active.

## Shared components

Wrap native Material controls only to enforce common layout, semantics and state:

| Component family | Contract |
| --- | --- |
| App scaffold / top bar | One inset owner, standard title/back/actions; chrome never records content beneath it. |
| Video item | Comfortable artwork above metadata; Compact horizontal row; text-only when Hide thumbnails is on. Whole item opens video; separate overflow target exposes existing actions. |
| Creator/playlist/clip/download item | Reuse text/metadata/action spacing, identity keys and error treatment; retain type-specific badges/progress. |
| Preference row / choice | Whole row activates one semantic control; label, optional summary/scope and trailing switch/choice. Show pending or error inline. |
| Section | Heading plus continuous rows on one low container; internal divider only when needed to distinguish adjacent actions. |
| State panel / inline state | Message, specific recovery action, polite status semantics; preserve usable retained content. |
| Action row / action strip | Standard buttons/chips with labels; wrap or scroll horizontally without shrinking targets. |
| Sheet / menu / dialog | Material modality, opaque container, consistent dismissal and focus restoration. No originating-window capture. |
| Editor | Labeled fields, inline validation, visible explicit submit for entity operations; footer clears IME/system bars. |

Stable occurrence/row keys and existing useful test tags remain. Rename
design-specific helpers/tags with updated call sites and tests in the same package;
do not preserve glass-only abstractions as a permanent compatibility layer.

## Responsive layout and inset ownership

Use available **window width**, not model, orientation alone or physical pixels:

| Width | Navigation | Content |
| --- | --- | --- |
| <600dp | Four-item NavigationBar, labels visible | One-column feeds; stacked watch and tools. |
| 600–839dp | Four-item NavigationRail | Comfortable feeds use two columns when each has ≥280dp; compact/text-only lists one centered column. Watch stays stacked. |
| ≥840dp | Same NavigationRail with labels | Comfortable feeds use as many ≥280dp columns as fit within a 1200dp content maximum; watch may use a supporting pane. |

This product explicitly uses a rail at medium/expanded widths, consistent with
[adaptive navigation patterns](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation).
Use existing Material NavigationBar/NavigationRail and a shared width
classification; an adaptive-navigation dependency is not required.

Settings/library lists center at max 800dp; authentication/entity forms max 600dp;
subscription directory max 800dp and one column. Wide watch uses 62% picture/main
information and 38% supporting content only when those panes fit ≥480/320dp;
otherwise stack. Keep panes clear of an occluding fold/hinge and fall back to a
single usable pane when the split cannot fit.

Scaffold owns system bars and app-bar/navigation padding exactly once. Its bottom
slot contains mini-player then navigation. List padding includes the measured
slot height, never a guessed dock height. Wide mini-player spans the content
region beside the rail. During IME entry hide bottom navigation and mini-player
presentation, preserve playback, and apply IME padding once to the input viewport.
Restore chrome when the IME closes. Fullscreen/PiP have no global shell chrome.
No collapsing or scroll-direction-driven navigation hiding.

At large font scales, grow rows/bars and wrap navigation labels (including
Subscriptions); retain full accessible names. Reduce feed column count before
clipping text or shrinking type. Do not disable system font scaling.

## Accessibility and optional ambient lighting

Expose names, roles, checked/selected/expanded/progress states and headings.
Avoid duplicate row/switch announcements. Restore focus after sheets/menus;
errors associate with fields; save progress uses polite announcements, not repeated
snackbars. Provide visible and TalkBack actions for minimize/restore/dismiss,
seek and chat movement/resizing. Test keyboard focus and RTL ordering. Compose
[accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
provide a starting point; layout must still prevent expanded hit-area overlap.

Ambient is the only retained background-color effect. It sits behind the expanded
watch picture; metadata, actions and discussion panels use opaque neutral
containers. It never changes theme foreground colors or supplies a backdrop to
controls. Preserve bounded 32×18/250ms sampling, lifecycle/video/power gates and
late-callback safety. Ambient off must create no ambient capture loop. Keep
Reduce transparency as a device accessibility preference with copy explaining
that it suppresses optional ambient lighting; the rest of the UI is already
opaque. Do not retain its old glass renderer or scene dependencies.
