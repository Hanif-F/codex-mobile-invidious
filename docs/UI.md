# UI, rendering and accessibility

[Documentation index](README.md) · [Extended design rationale](LIQUID_GLASS.md)

## Shell and screen ownership

`MobiviousApp` composes the browse shell, watch host, settings, dialogs and panels.
`LiquidNavigation` maps internal tabs to Discover, Subscriptions, You and Search.
Phones use a floating dock with a separate Search circle; widths ≥840dp use a
204dp sidebar. Search uses a bottom input above the IME and restores its origin.
Settings belongs to You; expanded watch hides global navigation. A mini-player
floats above the dock while browsing.

Lists preserve raw data and transform it for display. Comfortable feeds use
large artwork, compact feeds use rows and thin mode removes thumbnails. Wide
comfortable discovery/subscription/channel feeds use columns; the subscription
directory remains centered, one column, at most 800dp. You launchers use two
columns from 600dp with ordinary text sizing. Library/settings content is centered
up to 800dp. Large text can stack controls or hide visible dock labels while
retaining accessible names.

`BrowseGlassSurface` measures floating toolbar height in the same layout pass,
then sets content top padding. Bottom insets account for system bars, navigation,
mini-player and keyboard. Settings/form Save remains outside scrolling content
and above relevant insets. Dialog/menu state is keyed to identity/context so old
actions do not remain attached to a new account or row.

## Materials and reusable components

`LiquidDesign` defines light/dark palettes, typography, shapes and 20dp main
insets. `GlassMaterial` owns the renderer and optical tokens. Control, section
and media-overlay roles share directional highlights, restrained reflection and
concentric clipping; prominence changes emphasis. Shell/browse/watch modifier
facades delegate to this renderer. Backdrop remains pinned to 1.0.6, on API 33+.

Ordinary/prominent control tint is .14/.20 in dark appearance and .20/.26 in light;
section tint is .20/.28. Refractive controls use 12dp depth/18dp displacement;
sections use 18dp/20dp, with depth clamped to half the smaller dimension. The
background alone is magnified by 1.035 before depth-enabled refraction, with no
backdrop blur or chromatic aberration. Glyphs and icons remain sharp. Page surfaces
default to neutral #F5F5F5 in light appearance and #121212 in dark appearance.
The device-wide Colored backgrounds preference enables the existing broad blue,
teal and violet pools. Its composition policy covers visible environments,
background-only recordings and overlay fallbacks; disabled environments have
exact solid-color contrast bounds. Artwork and the existing bounded ambient
palette supply media environments. Stationary screens have no ornamental loops.

Settings root categories and related preference groups, account forms, playlist/
clip/RSS editors, player/chat settings, download choices and community tools share
section surfaces. Interior rows have quiet state fills, dividers and editing
boundaries; they do not stack independent glass. Content lists remain continuous.
`ActionControlsUi`, `LibraryGlassUi` and `WatchGlassUi` share these primitives.

`GlassScene` separates background-only section sources from page-content sources
for sibling floating chrome. Recorded children inherit the independent background
scene, never the page recording containing themselves. Header reading environments
mask scrolled glyphs across their complete measured height, including wrapped
2× titles; the trailing edge fades that same environment. Toolbar controls remain
clear and transmit the page. `GlassWindowBackdrop` gives menus, alert dialogs and
tooltips native-resolution crops from their originating Window using PixelCopy.
Each overlay records those pixels locally; graphics layers never cross windows.
Screen-coordinate alignment includes source-window offsets and clips crops to its
DecorView. Captures follow parent redraw/geometry changes, capped at four per
second while resumed, with two reusable crops, one request in flight and stale
callback rejection. Settled overlays do not poll. Capture failure, software drawing
and Reduce transparency use the readable solid fallback. Full-page panels continue
to record their own content. Video controls, player settings and the mini-player
use the internal deferred-player profile, retaining their prior optics and
palette-based popup backgrounds; surrounding watch-page glass uses refraction.

`GlassContentColors` supplies actual primary/secondary/icon colors through
`GlassForeground`. Foreground and material polarity default to the resolved app
Color mode. The device-wide Adaptive control colors preference enables atomic
backdrop-driven polarity changes with hysteresis. Theme and preference changes
reset polarity immediately without relocating content or discarding control state.
Both device preferences are provided before any glass scenes are created.
Labels, icons
and menu slots paint only their content, with no rectangular
backing, circular backing or inherited text halo. Readability comes from the
continuous surrounding glass material: its tint increases only when the
foreground needs more contrast over detailed artwork. Secondary/help text uses the
same foreground contract. Errors retain semantic hues; selection retains markers
and state descriptions, and disabled/busy controls remain distinguishable.

Changing Compose scenes share a GPU-downsampled 24×48 color grid. Sampling is
conflated, limited to one update per 250ms, resumed-lifecycle/hardware gated, and
stops without redraws. The first sample waits for the source recording to finish.
The small graphics layer and scratch buffer are reused; no full-window bitmap or
per-control readback is allocated. Active Watch reuses generated ambient palette
statistics instead of adding another pixel readback. Hidden shell chrome does not
sample. Unavailable statistics retain stable colors with a conservative shared
material tint.

Arbitrary artwork receives a conservative shared material tint: a coarse grid
cannot rule out a narrow bright band between samples. Generated environments
instead use analytic channel bounds from the light pools' actual peak opacity
and painting order. These bounds remain valid where pools overlap and avoid
unnecessary tint on the designed Settings and library backgrounds.

Press/focus illumination observes the existing semantic interaction source and
press position. Small controls deform by at most 2%, broad surfaces by 1%, with
at most 2dp lift through Backdrop's `layerBlock`; thin inset controls share feedback
without another material. Dock, tabs and segments spring toward measured selection
positions. Reduced motion suppresses deformation; system animation scale governs
transitions. Reduce transparency and software rendering use readable solid plates
and skip contrast readback. These are accessibility fallbacks, not the dark default.

Player chrome uses white icons and glyph shadows without circular plates, borders
or edge washes, preserving measured rails and separate ≥48dp targets. Every video
player has solid black backing and a black native loading shutter, including watch,
mini-player and fullscreen letterbox areas. It never records the decoder.
Save/Download/Share/More retain labels and long-press tooltips; narrow
layouts compact time/chapter labels while preserving full semantic descriptions.

## Video and motion

Media3 PlayerView uses a decoding SurfaceView with fit geometry and Compose surface
synchronization. `PlayerPresentationHost` retains service/controller ownership
through mini/watch/fullscreen transitions. `VideoGeometry` binds dimensions and
pixel ratio to a media ID; unknown geometry uses 16:9 until decoded dimensions
arrive. Portrait embedded height is capped by available viewport and can shrink
through nested scrolling; comments use the compact height. New occurrences reset
resize state. Fullscreen orientation and PiP use decoded shape.

Gesture distance/velocity is density-independent. Mini horizontal dismissal,
watch downward collapse and fullscreen return have deliberate axis/threshold
rules. Claimed drags cannot fall through into taps/seeks. Controls and scrollable
panels retain gesture priority. System reduced motion is observed by Compose
animations; touch exploration suppresses gesture-only interactions and dock
compaction. Android's fullscreen top-edge system gesture is left unconsumed.

## Ambient mode

The device-wide toggle defaults on and is shared by Appearance and player
settings. `PlayerPresentationHost` gates it on controller/frame availability,
hardware acceleration, motion/power policy, video track/presentation and
transparency settings. Sampling is limited to active expanded watch presentation;
PiP, mini, hidden/background/settings/editor states do not run continuous sampling.

`AmbientFrameSampler` uses PixelCopy from the SurfaceView into a **32×18** bitmap.
`AmbientCaptureGate` permits one in-flight copy, limits requests to one per **250
ms**, invalidates late callbacks when source/context changes and backs off **1
second** after failure. Cancellation does not recycle a bitmap still owned by a
pending copy; disposal waits for its callback. Paused frames can sample once after
render/seek readiness instead of looping indefinitely.

`AmbientPalette` averages a **3×3** spatial grid and modestly boosts color
separation. Only nine integer colors enter Compose state; full frames stay within
the sampler and are not saved/transmitted. Colors animate over 600 ms. Generated
radial light is recorded separately from consuming glass and the decoder. It
follows fitted picture bounds instead of letterbox bars and uses bounded luminance
with lower light-theme opacity to retain supporting-text contrast. Reduce
transparency, disabled animator duration and power-save mode pause the effect.
When ambient colors are unavailable or disabled, the expanded watch canvas and
metadata use flat theme surface color, with no thumbnail fallback or decorative wash.

## Rich content and imagery

`CommentsUi` parses supported rich text into native annotations for formatting,
links, timestamps/hashtags and emoji spans, with plain-text fallback. It executes
no web scripts. Video description/information, channel description and community
posts reuse native link resolution. Gallery failures retain navigation and Close.
Avatar handling reads existing metadata without adding creator lookup requests,
proxies supported image hosts and handles Unicode initials conservatively.
Channel headers keep their banner and avatar above a flat theme surface; their
glass controls sample that plain background rather than an enlarged banner.
MetadataUi and DisplayFormats share date/count precision rules across cards.

## Panels and accessibility

Comments/replies, chapters and chat use independent target state. Opening one
watch discussion tool closes incompatible panels. Back closes an active inner
panel/thread before leaving watch/fullscreen. Chat presentation visibility is
separate from its open preference so mini/PiP/background can suspend fetching
and reopen at current playback. Overlay adjustment has explicit Done/Cancel and
accessible movement/resizing actions; playback chrome stays above ordinary chat.

Controls expose content descriptions, selected/expanded/progress state, roles,
heading semantics and alternatives to gestures. Stable test tags identify inputs,
rows, panels and transport controls. Presentation tests cover narrow/large-text,
wide, light/dark, opaque and reduced-motion variants. Android XML resources supply
launch theme/system-bar colors, adaptive/monochrome launcher icons, backup rules,
RSS sharing paths and the separate TextureView clip preview. UI copy is currently
primarily literal English Kotlin text; locale-aware country/date/number formatting
does not imply a fully translated string-resource system.

The native `PlayerView` uses transparent caption/window backgrounds, including
cues with embedded background spans. Caption font/typeface preferences are
retained, with a glyph shadow when no edge is requested. Native clip-preview
controls use a transparent scrim and slate footer/settings popup through
`media_colors.xml`; video backing and loading shutters use black. Thumbnail durations, watched
labels and clip badges have transparent backgrounds.

Modal bottom sheets use transparent container fills, so their drag-handle region
does not introduce a separate dark band above the shared glass content.
