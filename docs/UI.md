# UI, rendering and accessibility

[Documentation index](README.md) · [Testing](TESTING.md)

This guide describes the opaque Material 3 implementation in the working tree.
The [testing guide](TESTING.md) describes validation; [cleanup results](CLEANUP.md)
record the latest tested scope and known failures.

## Instance access

Required verification opens the embedded verification dialog automatically when
the activity is resumed, outside PiP and sign-in. Required sign-in and unavailable
verification retain their access prompts. Later/Cancel or Back leaves navigation,
Server settings and local Downloads usable; cancellation survives recreation and
PiP until Retry on the failed action reopens verification. Concurrent access
failures share one dialog. Verification shows the selected origin,
an origin-restricted WebView, load/error feedback and Retry/Cancel actions. It
retains the default browser user agent, JavaScript and DOM storage for Turnstile.
Only same-origin main-frame completion messages are accepted; external top-level
navigation is blocked. An outdated WebView shows update guidance.

After successful verification, the dialog closes and browsing retries automatically
after any current browse request settles, preserving a failed pagination retry.
Playback and downloads retain their own explicit Retry actions. Access prompts
wait until the activity leaves PiP; background playback failures retain their issue
for the next foreground visit. Account or server changes discard the previous verification
flow. Account and verification expiry remain separate. Cloudflare may still
require interaction with its managed widget.

## Shell and screen ownership

`MobiviousApp` owns browsing, watch presentation, settings and modal tools.
`AppScaffold` defines four direct roots: **Popular, Trending, Subscriptions, You**.
Available window width selects bottom navigation below 600dp and a labeled rail
from 600dp. Navigation remains visible during browse scrolling. Search is a
transient opaque editor with one search bar, clear, keyboard submission and
Back/Close. Every search icon opens blank; tapping the result query edits it.
Cancel preserves the origin, loaded results, scroll position and player
presentation. Typing shows recent queries and live suggestions, without result
tabs or filters. Results use the query bar followed by type tabs/filter control
and removable active-filter chips, with no separate Search title row. Opening a child keeps its
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

Media list rows share 16:9 artwork at 160×90dp in comfortable mode and 144×81dp
in compact mode. This includes playlist/history videos, clips, playlist cards,
downloads, compact feeds and queue items. Artwork stacks above metadata when the
usable row width is below 320dp or font scale exceeds 1.3. Thin mode keeps the
space available to text; spoiler controls, AI warnings and watched indicators
retain their existing behavior.

Supplied view counts appear on video cards (including Up next, history and
playlist/mix entries), playback queue rows and downloads. Live audience counts
say “watching”; totals say “views”. Clip rows and full-video cards label the
count “Source video” to distinguish it from clip statistics. Missing counts stay
hidden, genuine zero stays visible, and expanded watch statistics show grouped
counts only when the source explicitly marks them exact. Count text wraps in
compact/thin layouts and large text; browsing adds no requests to obtain counts.

Channel tabs occupy their own row. Sorting shares the next row with a search
icon: measured sort choices appear as pills when they fit, otherwise as a
dropdown. Sort pills use fully rounded ends, smaller labels and a default 32dp visual
height, below the page tabs' size, while retaining 48dp touch targets.
Tapping search expands and focuses the field inline, reserving at
least 160dp and condensing sorting as needed. An empty field collapses after
focus leaves it; drafts and submitted queries stay visible. Opening and
dismissing an empty field do not submit a search.

Downloads open offline playback when any non-control part of a playable entry
is tapped. A trailing three-dot menu contains Details, available Cancel/Retry
actions, Save to files and Delete. Incomplete entries keep their status and
actions available while playback is disabled. Spoiler reveals and menu actions
keep their independent tap behavior.

`AppWindowLayout` observes WindowManager folding features. An opaque separating
hinge selects the larger contiguous pane. Watch stacks unless an expanded window
can provide at least 480dp for the player and 320dp for its supporting pane.
Root-coordinate anchors keep the existing decoder attached while presentation
moves between embedded, mini, fullscreen and PiP modes.

When the embedded player can shrink, the first 24dp scrolls the details list.
Further scrolling changes player height by the consumed scroll distance, using
the actual expanded-to-compact height difference rather than a fixed gesture
distance. The list receives the remainder, so content follows the finger during
collapse and expansion. Expansion starts only near the top; flings retain
Compose's normal physics without extra animation or snapping. Normalized progress
survives recreation, window changes, drawers and minimization and resets for a new
queue occurrence. Videos whose fitted height already meets the compact cap leave
scrolling entirely to the list.

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

## Action menus and player settings

Action menus use decorative 24dp Material icons, text labels and at least 48dp
row targets, with shared enabled and destructive colors. Video actions group
Play next/Add to queue/Play audio only, Save to playlist/Download, Block channel,
and any removal actions using dividers between populated groups. Channel navigation
uses the existing avatar/author link rather than a video-menu action.
Channel title and thumbnail settings belong to channel pages and Settings rather
than individual video menus. Sort/filter selectors retain their selection checks.

Player settings place Quality, Audio, Captions and Playback speed first. Viewing
mode appears only for metadata-marked equirectangular or EAC videos, including
when Flat is selected or automatic VR is off. More expands inline to show
Annotations, Audio only, Picture in picture, Refresh buffer and Playback statistics. It starts
collapsed on each opening, preserves expansion through nested pages and activity
recreation while open, and resets for a new occurrence. Icons, page chevrons,
expanded/collapsed semantics and stacked values at large text keep rows readable.

Playback statistics overlays the picture with a compact four-metric view and a
scrollable detailed view. Its pinned header exposes playback, expand/collapse,
Close and (in Details) Copy diagnostics; the timeline/fullscreen footer stays
available. Panel input cannot fall through to seeking, panning or minimizing.
Back collapses Details, then closes statistics before normal watch navigation.
The session toggle survives fullscreen/recreation and queue changes; sampling and
the panel suspend in mini-player, PiP, background and player settings. Copy failures
retain selectable JSON with retry; success uses an accessible status message.

Download choices and saved-download details share two-line media labels: video
resolution/frame rate/codec or audio track name/codec above available language,
Stable volume, bitrate and size. Friendly codec names match playback (AV1,
H.264, HEVC, VP9, AAC, Opus and others); unknown identifiers remain visible and
missing codec metadata says Unknown codec. Container names are not codec names.
Technical values keep their numbers beside their units in RTL layouts.
Raw metadata and selection identities remain unchanged.

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

**You → Settings → Playback → Seek interval** offers 5, 10, 20 and 30 seconds,
with 10 selected by default. Saved changes update subsequent seek actions while
the video remains open. Accessibility and PiP labels use the selected distance.
The interval represents video timeline seconds, including at different speeds.

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

While dragging the timeline or accumulating seek taps, scene previews replace the
visible picture inside the existing player bounds. Images retain their aspect
ratio with black letterboxing; controls and gesture targets remain above the
image. Preview drawing covers native captions from the old playback position.
Time, chapter and SponsorBlock labels follow the pending target. Releasing or
cancelling seeking removes the image; unavailable previews preserve usable controls.

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

## Search presentation

Search videos retain large artwork even when ordinary feed density is compact;
thin mode, spoiler masking, AI warnings and watched indicators remain active.
Channels use outlined identity cards with a 64dp circular avatar and verification
beside the name. Thin mode retains a circular person placeholder. Playlists/mixes
use stacked rectangular covers and a playlist/shuffle icon; known playlist counts
appear on the cover. Missing artwork keeps that shape; thin mode uses a compact stacked placeholder. Large text stacks
playlist artwork and metadata vertically. Mixed order remains server-defined.

New queries reset result type to All, sorting to Relevance, upload date to Any time
and duration to Any duration. Editing retains choices. Filters apply immediately;
Reset result filters resets those four choices while visibility preferences stay
in their separate device section. Activity recreation retains the editor draft.
