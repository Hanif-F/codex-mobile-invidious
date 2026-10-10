# Liquid Glass design language

Mobivious has one visual design, inspired by Apple's Liquid Glass. Android 13
(API 33) is the minimum supported version. Light, Dark and System change the
appearance of this design; there is no theme collection or legacy-design switch.

## Apple references

- [Liquid Glass technology overview](https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass)
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
