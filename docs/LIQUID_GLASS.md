# Liquid Glass design language

Mobivious has one visual design, inspired by Apple's Liquid Glass. Android 13
(API 33) is the minimum supported version. Light, Dark and System change the
appearance of this design; there is no theme collection or legacy-design switch.

## Apple references

- [Liquid Glass technology overview](https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass)
- [Build a SwiftUI app with the new design, WWDC 2025](https://developer.apple.com/videos/play/wwdc2025/219/):
  material hierarchy, legibility over media, and avoiding stacked glass.
- [Meet Liquid Glass, WWDC 2025](https://developer.apple.com/videos/play/wwdc2025/284/):
  the Music mini-player accessory at 2:49 and Maps custom controls at 19:15.
- [Apple's iOS 26 app examples](https://www.apple.com/newsroom/2025/06/apple-elevates-the-iphone-experience-with-ios-26/):
  floating navigation and content-first layouts in Music, Safari and Photos.
- [Landmarks: Building an app with Liquid Glass](https://developer.apple.com/documentation/swiftui/landmarks-building-an-app-with-liquid-glass)

These examples guide the hierarchy: glass belongs to navigation and controls
floating over content. Video artwork, text, lists and forms use quiet surfaces.
A native Android implementation approximates the material; it does not use
Apple's proprietary rendering implementation.

## Navigation

The phone dock contains Discover, Subscriptions and You, with a separate circular
Search button. Discover contains Popular and Trending with independent saved
positions and feed options. Existing homepage values remain compatible.

Search replaces the phone dock with a bottom input that sits above the keyboard.
Close search returns to the originating feed or child page. Playback continues;
the mini-player gives the keyboard room while typing. Settings lives in You's
toolbar and is available to guests. You also owns downloads, history, clips and
playlists. At widths of 840dp or greater, a floating sidebar replaces the dock and
Discover cards use two columns.

The expanded watch page owns its controls and hides global navigation. Save,
Download, Share and More form the primary action row. More contains Create clip
and DeArrow title contributions. On wide displays, metadata and comments sit
beside the video. Native portrait, square, landscape and ultrawide geometry is
preserved. The mini-player floats above navigation while browsing.

## Subscriptions and channels

Subscriptions keeps the upload feed primary, with a pinned Channels pill and
compact avatar/name shortcuts. Its page-owned toolbar contains refresh, RSS/OPML
and scoped search. Channels uses a continuous directory with quiet separators,
40dp avatars, viewing statistics and a matching-channel count. Local search,
saved sorting and the statistics fallback remain independent of presentation.

`BrowseGlassUi.kt` records scrolling content separately from sibling floating
search, tab and sort controls. Measured toolbar height sets the initial list
padding, while scrolling artwork and rows can pass behind the glass. The shell
continues to own the dock, sidebar and mini-player. No glass control samples the
recording that contains it. The browse material uses the existing Liquid palette,
refraction, subtle tint and fine rim, with solid accessibility/software fallbacks.

Creator pages retain server-provided tabs and native content behavior. Banner and
avatar lead the identity, verification sits beside the name, and Subscribe uses
a glass pill over a separately recorded artwork wash. Narrow layouts and large
text stack the identity. Search, horizontally scrolling tabs and compact sorting
menus stay accessible above the content. Comfortable subscription and channel
video feeds use two columns at wide widths; the directory stays one column,
centered at a maximum of 800dp.

Channel descriptions, RSS/OPML and SponsorBlock panels use floating glass
toolbars over quiet scrolling bodies. Private-feed warnings, file operations,
rich links and settings semantics remain intact. Presentation and journey tests
save screenshots under `/data/local/tmp/mobivious-subscriptions-glass/`.

## Expanded watch

The watch page records its own background independently of its controls. A subtle
blurred thumbnail wash fades into the page; thin mode, unavailable artwork and
offline playback use a neutral wash. `WatchGlassUi.kt` owns the background,
refractive materials, disclosure rows and panel toolbars. It never records the
glass into the backdrop that glass samples.

Save, Download, Share and More use equal-width columns with centered labels below
56dp glass circles, reducing to 48dp on narrow layouts. Labels fit as whole words
on narrow layouts with large text. Accessible names, disabled states and long-press
tooltips remain available. The title wraps at 20sp semibold,
verification sits beside the channel name, and Subscribe uses a glass pill.
Comments and Description are the two disclosure rows; Description expands inline.
Chapters and chat replay are opened from the player.

`PlayerControlsUi.kt` uses white icons, text shadows and shallow neutral edge
contrast rather than tinted capsules or a full-picture scrim. Collapse is at the
top left, with chat beside settings at the top right. The footer puts the timestamp,
clickable current chapter and fullscreen above a full-width seek track. Separate
48dp rows keep their touch targets clear of seeking. In portrait the visible labels
sit lower and the seek track sits higher within those targets, giving closer spacing;
landscape retains the more open spacing. It measures time labels;
long chapters truncate, while narrow
layouts with large text use elapsed time and a chapter chevron while retaining
full accessibility descriptions. Play/Pause/Replay is centered when measured
chrome leaves room, otherwise it moves beside collapse. Very shallow pictures
share the upper footer row with header controls; minimize remains available through
the player's accessibility actions if narrow widths leave no room for its icon.
Seeking uses double taps,
the accessible timeline or the existing accessibility actions. Loading/retry,
buffering, chapter separators, SponsorBlock ranges and scrubbing labels remain.
Reduce transparency gives controls solid neutral contrast surfaces.

Watch comments have a floating glass toolbar and a segmented Top/Newest control.
The reading surface is recorded separately from its sibling glass controls;
scrolling comments pass behind them without a recursive backdrop. Measured header
padding keeps the first comment readable. Avatars sit beside comment bodies,
separators are quiet and replies use lightweight text controls. Community comments
retain their existing presentation through the explicit watch flag. Chapters,
chat replay and settings keep their existing glass toolbars and quiet lists.

## Shared components

`LiquidDesign.kt` defines colors, typography, shapes, spacing and material.
`LiquidNavigation.kt` owns dock and sidebar semantics. `ActionControlsUi.kt`
provides the shared action/settings rows. Comfortable video cards keep 16:9
artwork; compact density uses thumbnail rows. Thumbnail display preferences,
watched states, warnings, menus and content visibility continue to apply.

The palette uses pearl and charcoal content surfaces with a restrained blue
accent. Main margins are 20dp, cards 24dp, media 20dp and floating controls pills
or circles. Controls have at least 48dp targets; larger text can hide dock labels
while retaining accessible destination names. Press feedback uses interruptible
Compose springs and Compose respects the system animator duration scale.

## Rendering and accessibility

The shell records content into a Backdrop layer. Floating chrome, which is a
sibling of that recorded content, samples the layer with blur, vibrancy, lens
refraction, a tint plate and a fine highlight. A recording layer must never sample
itself: recorded content receives a null backdrop. This prevents render cycles.

Media3 retains its decoding SurfaceView and service ownership. Video overlays
use dark translucent plates and do not read back the decoding surface. This
preserves fullscreen, PiP, background playback and aspect ratios.

Appearance → Reduce transparency is a local, device-wide accessibility setting.
It replaces sampled materials with opaque plates. Software rendering also falls
back to solid plates. The material uses Backdrop 1.0.6 (Apache 2.0), pinned to the
project's Kotlin 2.3.10 and Compose toolchain. Attribution is available in About.

## Review

`LiquidRedesignSmokeTest` exercises the shell, independent feeds, search origin,
keyboard (including floating IME), guest settings/sign-in return, opaque mode,
native video shapes, playback continuity, wide layout, narrow layout with large
text and system reduced motion. Screenshots are saved on-device
under `/data/local/tmp/mobivious-liquid/`. The dated verification record records
actual completed checks and limitations.
