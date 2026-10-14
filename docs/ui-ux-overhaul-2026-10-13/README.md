# Complete Android UI/UX overhaul

**Status: approved direction and implementation specification; app implementation pending.**
Prepared on **13 October 2026** against Mobivious **0.10.3 / 21**, source
`62c91828215759c3c50f6301808b2d9accb4ed92`.

[Documentation index](../README.md) · [Current implemented UI](../UI.md)

## Purpose and authority

Give implementation agents one complete specification for replacing Liquid Glass
with a clean, opaque Android interface. Cover the whole application: navigation,
information hierarchy, layouts, controls, workflows, accessibility and rendering.
The user reports inconsistent UI/UX and jagged scrolling after the glass redesign,
and remembers smoother scrolling before it. These are product observations;
their precise causes and performance magnitude have not been measured.

This package supersedes the future-design instructions in
[Liquid Glass](../LIQUID_GLASS.md), the
[previous overhaul brief](../LIQUID_GLASS_OVERHAUL_PLAN.md) and subsequent glass
refinement plans. Their screenshots, measurements and implementation records stay
historical evidence. [UI](../UI.md), [Settings](../SETTINGS.md) and the other current
guides continue to describe shipped source until implementation changes them.

The request for this package authorizes documentation. Application changes,
device installation, server changes and publishing are separate work. During
implementation, follow this package for intended UI behavior and the subsystem
guides for API, playback and storage contracts. Preserve a contract unless an
explicit change is specified here. Record newly discovered conflicts in the
implementation report rather than silently reviving glass requirements.

## Locked product decisions

| Area | Required direction |
| --- | --- |
| Main navigation | Popular → Trending → Subscriptions → You, in that order. |
| Search | Top app-bar action; dedicated search screen; return to the originating destination and position. |
| Material | Opaque Material 3 surfaces throughout, including navigation, mini-player, player settings, menus, sheets and dialogs. |
| Color | Neutral light/dark surfaces; Blue, Teal, Green, Violet, Amber and Rose accent presets; Blue default. |
| Appearance | Preserve Light / Dark / System and their existing storage scope. Accent is device-wide and immediate. |
| Ambient lighting | Optional, independent of the core theme; absent preference means off; explicit saved true/false remains respected. |
| Saving | Preferences save automatically; account operations, content editors and destructive actions retain explicit actions. |
| UX freedom | Reorganize navigation, layouts and interaction patterns across the whole app while preserving capabilities and user data. |
| Performance | Smooth, responsive use on Android phones generally; no acceptance dependency on the user's particular phone. |

There is one opaque design, with coordinated light/dark appearances. Accent
presets are color choices within that design. Do not add a legacy glass mode,
wallpaper-derived colors, an arbitrary accent picker, ornamental gradients,
refraction, background readback for controls or deformation animations.

## Reading order and ownership

| Order | Guide | Owns |
| --- | --- | --- |
| 1 | [Baseline](BASELINE.md) | Source facts, historical references, inventory and invariants. |
| 2 | [Design system](DESIGN_SYSTEM.md) | Theme tokens, component contracts, responsive and accessible presentation. |
| 3 | [Screens and flows](SCREENS_AND_FLOWS.md) | Destination hierarchy, wireframes, screen states, navigation and actions. |
| 4 | [Implementation](IMPLEMENTATION.md) | Packages, source ownership, interfaces, migration and integration order. |
| 5 | [Verification](VERIFICATION.md) | Test mapping, commands, performance protocol and evidence gates. |

Design tokens belong in DESIGN_SYSTEM; navigation and screen behavior belong in
SCREENS_AND_FLOWS; save concurrency and migration belong in IMPLEMENTATION.
Other guides link to those rules instead of creating competing variants.

## Capability boundary

Retain discovery/search, subscriptions, channel tabs and community content,
playlists/mixes, history, downloads/export, clips, accounts/sessions/tokens,
all supported preferences, blocking, RSS, SponsorBlock, DeArrow and AI filtering.
Retain watch/listen, queue occurrences, fullscreen, PiP, background playback,
captions, chapters, comments, archived chat and supported content links.

Existing read-only and server-compatibility limits remain. This overhaul does not
add casting, uploads, notifications, comment posting, poll voting, timeline
thumbnail previews, durable queue recovery after process death or a local RSS
reader. It does not change server endpoints or require a database migration.

## Completion rules

**Documentation complete** means all six guides exist, each inventoried screen has
a specified hierarchy/actions/state treatment, each implementation package has
acceptance criteria, local links and the documentation checker pass, and no app
source/build/test implementation was changed by the documentation pass.

**Implementation complete** additionally requires every package and the
[verification gates](VERIFICATION.md#completion-and-evidence) to pass, removed
glass dependencies and work, retained capability coverage, and honest runtime
evidence. Compilation, emulator screenshots and a completed checklist alone do
not establish physical-phone smoothness.

Use separate statuses: specified, implemented, build-verified, device-verified,
performance-measured and released. As of this package's creation, only the
documentation is being delivered; the redesigned app has not been implemented,
benchmarked or released.
