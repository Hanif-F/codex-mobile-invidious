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

`LiquidDesign` defines light/dark colors, typography, spacing, shapes and material
rendering. Main insets are 20dp; content uses quiet surfaces and actions use pills/
circles. `DarkGlass` shares dark control tokens. Backdrop 1.0.6 supplies sampled
blur/vibrancy/refraction; a fine rim/tint gives definition. Opaque plates are used
for Reduce transparency and software rendering.

The recorded backdrop must be a sibling of its consuming glass. Content inside
that recording receives no consuming backdrop, preventing recursive rendering.
Shell, browse, watch, library artwork and popup windows use separate scenes where
needed. `ActionControlsUi` supplies overflow menus/settings rows; `LibraryGlassUi`
supplies toolbars/buttons/confirmation/panels; `WatchGlassUi` supplies watch
disclosures/actions/panel chrome. Reading text, forms and content rows remain quiet.

The watch background has a thumbnail/neutral wash independent of controls.
Player controls use white foregrounds, localized edge contrast and measured rails;
they never use the full decoder surface as a glass recording. Save/Download/Share/
More have labeled circles with accessible names and long-press tooltips. Disabled
actions retain legible disabled state. Narrow layouts compact timestamp/chapter
labels but preserve full semantic descriptions and separate ≥48dp touch areas.

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

## Rich content and imagery

`CommentsUi` parses supported rich text into native annotations for formatting,
links, timestamps/hashtags and emoji spans, with plain-text fallback. It executes
no web scripts. Video description/information, channel description and community
posts reuse native link resolution. Gallery failures retain navigation and Close.
Avatar handling reads existing metadata without adding creator lookup requests,
proxies supported image hosts and handles Unicode initials conservatively.
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
