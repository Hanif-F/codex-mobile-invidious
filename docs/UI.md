# UI, rendering and accessibility

[Documentation index](README.md) · [Testing](TESTING.md)

This guide describes the opaque Material 3 implementation in the working tree.
The [testing guide](TESTING.md) describes validation; [cleanup results](CLEANUP.md)
record the latest tested scope and known failures.

## Shell and screen ownership

`MobiviousApp` owns browsing, watch presentation, settings and modal tools.
`AppScaffold` defines four direct roots: **Popular, Trending, Subscriptions, You**.
Available window width selects bottom navigation below 600dp and a labeled rail
from 600dp. Navigation remains visible during browse scrolling. Search is a
transient top field with clear, keyboard submission and Back/Close; its origin,
loaded results and scroll position remain available. Opening a child keeps its
originating root selected. Each root retains its own stack and list position.
Reselecting a root scrolls to the top without refreshing; reselecting from its
child returns to that root.

Popular and Trending keep only Search in their title bars. Subscriptions keeps
Search and its signed-in RSS/OPML menu; Channels remains beside scoped search.
The app Settings gear belongs to You. All three feeds use Material 3 pull-to-refresh
below the toolbar: pull downward at the top, then release beyond the standard
threshold. The indicator follows the pull and stays active during the request.
Existing rows remain visible until the refreshed first page replaces them;
failures retain rows and offer Retry. Initial loading and pagination are separate.
Refresh is also an accessibility action and is unavailable on the guest subscription
prompt. Returning to the top alone does not refresh.

The opaque mini-player spans the content width immediately above navigation. Its
minimum height is 72dp with independent 48dp play/pause and close targets. The IME
hides navigation and mini-player presentation while playback continues. Scaffold,
top bars and dialogs own their insets once. `BrowseAppSurface` places a pinned,
opaque toolbar above the list.

Comfortable feeds use fitted artwork; compact feeds use rows; thin mode removes
ordinary thumbnails. Wide comfortable feeds use columns of at least 280dp.
Feed content caps at 1200dp; library/settings lists at 800dp; account/server forms
at 600dp. Insets are 16dp on compact windows and 24dp otherwise. You uses continuous
library rows in the order Playlists, History, My Clips, Downloads, following
identity and guest sign-in guidance. Subscription creator shortcuts precede
Channels/scoped search and uploads. Channel banner/identity precede tabs, sorting,
scoped search and results. The channel header uses the same explicit neutral
surface in light/dark appearance, with or without banner artwork or thin mode.

`AppWindowLayout` observes WindowManager folding features. An opaque separating
hinge selects the larger contiguous pane. Watch stacks unless an expanded window
can provide at least 480dp for the player and 320dp for its supporting pane.
Root-coordinate anchors keep the existing decoder attached while presentation
moves between embedded, mini, fullscreen and PiP modes.

## Theme and components

Drawers, modal sheets and full-screen action panels share a flat panel header and
one opaque `surfaceContainerLow` background. Headers keep a readable title,
optional supporting label, trailing Close and leading Back on nested pages.
They stay pinned above independently scrolling content, use 48dp control targets
and 16dp content alignment, and move form actions to another row at large text
sizes. Counts include their noun, such as “31 comments.” Comments sorting uses
flat Material tabs. Settings values stack beneath their labels at large text.

`PanelComponents` owns this styling locally; ordinary browse toolbars and section
cards keep their existing roles. Inside panels, section cards become flat groups
and action rows use dividers. Embedded watch tools, wide queue panes and modal
tools use the same header. Modal sheets retain a single outer rounded shape and
drag handle on the same background; their 85% safe-window height cap includes
the handle and content insets. Fullscreen playback uses the same sheet styling.
Floating chat overlays retain their adjustable opacity and geometry.

`AppTheme` supplies exact light/dark neutral Material 3 roles, standard typography
and shape sizes. Six device-wide accent presets have stable IDs: blue (default),
teal, green, violet, amber and rose. Unknown IDs resolve to blue. Color mode keeps
its existing account/guest scope; artwork never changes theme roles. Neutral
surfaces remain opaque in both appearances, including menus and dialogs.

`AppComponents`, `AppLayout`, `BrowseComponents`, `LibraryComponents` and
`WatchComponents` use native Material components and shared surface roles.
Sections use surface-container-low; controls, menus and tooltips use appropriate
opaque container roles; the mini-player uses surface-container. Navigation uses
primary-container selection. Focus, selected state, disabled state, errors,
progress and ripple remain native. [Storage](STORAGE_AND_SECURITY.md) describes
appearance-key migration and retained saved values.

## App icon and splash

Mobivious uses a flat angular cyan M (`#00B6F0`) and pale dot (`#F0F0F0`) on
charcoal (`#131416`). Its outward-slanted legs and dot above the left peak are
derived from the original Invidious lambda-shaped mark. The reference was the
`invidious.svg` supplied from `wingressHomepage/static/img/`; that reference stays
unchanged. This artwork is distributed under the repository's existing
[AGPL-3.0-only license](../LICENSE).

The reusable [master icon](../branding/mobivious.svg),
[transparent mark](../branding/mobivious-mark.svg),
[1024px PNG](../branding/mobivious.png) and
[preview sheet](../branding/mobivious-preview.png) live in `branding/`.
Both SVGs use the Android artwork's 108-unit coordinates with a `18 18 72 72`
viewBox, matching the launcher's visible central area. Edit the two paths in the
master, then copy their geometry to the transparent SVG, launcher foreground and
monochrome vector. Regenerate the PNG with
`magick -background none branding/mobivious.svg branding/mobivious.png` from the
repository root, and update the previews when the artwork changes.

The adaptive icon separates a full-bleed charcoal background from a transparent
foreground. All M-and-dot artwork fits inside the centered 66dp safe circle on
the 108dp canvas; masks belong to the launcher. The monochrome layer uses the same
two paths with solid white fills and lets Android supply themed colors. Both
release and Mobivious Preview use this artwork, independent of app accent presets.
The system splash uses the color foreground with a charcoal icon backing and
retains the existing light/dark window backgrounds. Gradients, translucent fills
and the former play-triangle icon are no longer used.

## Saving and editing

Preferences save automatically. Choices commit immediately, sliders commit on
release, and valid text commits after 500ms, blur, Done, page exit or backgrounding.
`PreferenceSaveController` owns confirmed/requested values, revisions, pending,
invalid and failed states. Unsent changes to one key coalesce. The actual sink
serializes writes and applies sparse patches against current confirmed settings.
Older responses cannot erase newer intent. Context changes discard obsolete work.
Invalid text remains editable and never enters a request.

Field feedback provides Retry/Revert, and the Settings root links to pages with
outstanding changes. Leaving a page keeps its pending/error state. Account/server
changes warn before discarding unconfirmed edits. Passwords, identities, tokens,
playlist/clip creation and edits, destructive actions, exports and chat overlay
geometry retain explicit submission and appropriate dismissal confirmations.

## Watch presentation

Watch metadata and tools use opaque neutral surfaces. Native white player controls
have transparent backgrounds above one full-picture black dim layer at 25% opacity.
The layer fades with control visibility over 160ms in embedded and fullscreen
playback. SponsorBlock prompts and seek feedback have no individual backings.
Settings sheets, chat panels, captions, mini-player and PiP retain their styling. Save,
Download, Share and More use four equal-width columns with filled circular icon
buttons and centered labels underneath. Circles are 56dp where space permits and
48dp on narrow rows; labels wrap at large text sizes without shrinking type.
Portrait Play/Pause/Replay stays centered in the player. Short pictures use a
single-row footer for time, seeking and fullscreen, with chapters in the header.
Tiny pictures put secondary controls and SponsorBlock prompts in More player
controls; opening this popup keeps controls visible. Queue opens as a full-height
phone sheet or wide supporting pane. Comments, chapters, queue and docked chat
are mutually exclusive; closing a tool preserves playback and occurrence identity.

## Accessibility and verification

Controls keep at least 48dp targets, semantic roles, names, state and focus.
Visible navigation labels remain available at large text. Responsive layouts use
window width and measured text rather than model/orientation assumptions. RTL,
keyboard, large text, recreation, inset changes and decoder ownership are covered
by focused device tests; the implementation report records which runs pass and
which still need review. Theme unit tests check all twelve appearance/accent
combinations for opaque roles and text/boundary contrast.

Use [Testing](TESTING.md) for functional, screenshot, accessibility and benchmark
runners. Emulator diagnostics establish behavior in that environment; they do
not establish physical-phone performance.

Search filters apply each valid choice immediately, keep the query and reset
pagination. Active filters remain summarized beside results. Outstanding search,
chat, Trending and channel-directory choices link to their actual controls from
the Settings status section. Chat timestamps are independent of the validated
user/word filter group; stale callbacks carry their originating context/video and
cannot edit a different session. Download details open in a local opaque sheet.
Wide watch keeps information below the picture in its primary pane, with tools
or recommendations in the supporting pane.

## Channel title and thumbnail masking

The native channel editor uses the existing opaque settings surfaces and automatic
save feedback. Hidden artwork uses a neutral text placeholder with a 48dp-or-larger
interactive image area; its first tap reveals without navigation. Title buttons
announce Reveal title, Show original title or Hide title according to state.
Passive action/dialog labels use masked text. Upload-date uncertainty is visible in
the placeholder. Independent card occurrences keep independent reveals; watch and
mini-player share the active playback reveal. Account loading uses safe placeholders
and read failures offer Retry in the channel settings page.
