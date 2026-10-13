# Clear refraction — 13 October 2026

[Documentation index](../README.md) · [Material contract](../LIQUID_GLASS.md)

The shared non-player material transmits sharp detail, magnifies its background
by 3.5%, and bends it with depth-enabled edge refraction. Controls use 12dp/18dp
depth/displacement and sections 18dp/20dp. Refraction depth is clamped to half the
smaller dimension. There is no backdrop blur or chromatic aberration; foreground
glyphs, icons, interaction semantics and contrast-driven tint remain intact.

Menus, alert dialogs and tooltips use native-resolution PixelCopy crops of their
originating Window, recorded independently in the overlay. Only the shaped glass
paints the crop. Parent redraws and geometry changes request updates, capped at
four per second while resumed. Two reusable buffers, one in-flight request and
generation checks bound resource use and reject stale results. Settled overlays
do not poll. Software rendering, reduced transparency and unavailable captures
use the readable fallback. Full-page panels keep their own content recordings.

Video controls, player settings and the mini-player are deferred through an
internal composition profile. Their prior material and popup environments remain
in place. Descriptions, comments and surrounding watch-page actions use refraction.
No dependency, public API, stored preference or server change is involved.

## Verification

Verification uses the already-running `emulator-5554` (Pixel 8 Pro, API 36,
1344×2992, density 480). JVM tests use local fixtures; no release was published.
The debug app is installed on that emulator.

- `GlassRefractionTest`: sharp stripe displacement in rounded/circular controls
  and sections; undistorted foreground glyphs; originating dialog pixels and
  redraws; cropped capture bounds; resizing, idle/disposal, reduced transparency
  and deferred-player capture gates.
- Existing foundation/contrast/watch/surface tests: foreground contrast, opaque
  and software fallbacks, nested recordings, transparent labels, press
  cancellation, tooltips, open-menu stability and responsive toolbar geometry.
- Selected real-app journeys: discovery/search and IME, settings forms/categories
  and menus, light/dark/opaque appearances, wide sidebar and large text, reduced
  motion, watch descriptions/comments/actions, fullscreen and player settings.

Exact commands, results and remaining device limits are recorded in
[verification.json](verification.json). Screenshots and normal-speed recordings
are supporting visual evidence, not substitutes for assertions.

347 JVM tests passed. All 34 selected device checks passed after rerunning the
five refraction scenarios with the corrected shadow tolerance; four recorded
journeys also passed on the final debug build. Debug compilation, instrumentation
compilation, lint (0 errors, 36 existing warnings), documentation checks and
`git diff --check` passed. The first corner assertion rejected a 3/255-alpha
elevation shadow; its final <.03 threshold still rejects opaque crop rectangles.

## Evidence

- [Original dock](screenshots/before-dock.png)
- [Refractive dock](screenshots/after-dock.png)
- [Real page through a menu](screenshots/real-page-menu.png)
- [Sharp stripe refraction](screenshots/refraction-sharp-stripes.png)
- [Originating-window dialog](screenshots/refraction-dialog-real-page.png)
- [Keyboard/search](screenshots/search-ime.png), [settings](screenshots/settings-light.png),
  [wide dark layout](screenshots/discover-wide-dark.png),
  [narrow large text](screenshots/discover-narrow-large-text.png)
- [Surrounding watch glass](screenshots/watch-glass-light.png),
  [comments](screenshots/watch-glass-comments.png),
  [deferred player settings](screenshots/watch-glass-settings.png)
- [Normal-speed journeys](normal-speed-journeys.mp4): dialog refresh/resize,
  discovery/search, surrounding watch controls and settings. The emulator encoder
  falls back to 720×1280; timestamps/playback speed are preserved.
- [Scrolling baseline](gfx-before.txt)
- [Initial after trace](gfx-after.txt), [warm after trace](gfx-after-warm.txt)

| Trace | Frames | Janky frames | Median / p90 |
| --- | ---: | ---: | --- |
| Before | 82 | 40.24% | 44ms / 109ms |
| Initial after | 64 | 59.38% | 65ms / 150ms |
| Warm after, compilation finished | 127 | 15.75% | 32ms / 48ms |

`gfxinfo` measurements are informal emulator comparisons. Compilation, image
loading, different offsets/navigation expansion and the host GPU affect frame
timing. The cold after run is slower and the warm repeat faster; these traces do
not isolate a renderer performance change or establish physical-device battery,
HDR or GPU performance. No performance improvement is claimed.

After verification, the user reported that the emulator crashed and restarted
it. The agent reconnected (transport 11), confirmed that the installed APK hash
matches the final build, reopened the app and verified real touch navigation to
You. The fresh-boot Android crash buffer was empty and no host crash trace was
found in the inspected locations, so the cause is unconfirmed. See the
[post-restart screen](screenshots/after-emulator-restart.png).
