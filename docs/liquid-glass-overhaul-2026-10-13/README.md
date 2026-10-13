# Liquid Glass overhaul baseline — 13 October 2026

[Agent handoff](../LIQUID_GLASS_OVERHAUL_PLAN.md) · [Documentation index](../README.md)

This is a **pre-implementation** evidence collection. The images were copied
byte-for-byte from the supplied attachment and emulator captures collected during
planning. No application code was changed or build installed for this collection.
Keep these files unchanged for future before/after comparisons.

## Provenance and inspection boundaries

- Source inspected: `e956aabb4fbcd503f4dd51443f9a0b5812d96a7a`, clean working tree before the handoff was saved.
- Fresh emulator captures: `emulator-5554`, `sdk_gphone64_x86_64`, 1344 × 2992, physical density 480 (448dp width), animator duration scale 1.0.
- App inspected: release `net.wingress.mobivious`, version 0.10.2, version code 20. Installed debug/test packages were present; debug was 0.10.1-debug / 19 and was not used for these captures.
- Fresh captures show the existing dark appearance. No fresh light-mode, wide/large-text, opaque-mode, physical-device, performance or full-screen journey acceptance was performed.
- Watch was captured with the existing LG video ended at 0:45 / 0:45. Its black picture and subdued environment differ from the user's colored screenshot. Do not attribute that difference to an implementation change.
- Screen clocks are device presentation, not authoritative collection timestamps; the session/client inspection date is 13 October 2026.
- Pointer-down/expanded images document interaction states. They do not establish timing, smoothness or an exact animation peak. No normal-speed baseline recording was captured.
- The user's screenshot has unknown source revision, device and settings. Its colored environment is visible; an exact ambient-mode configuration was not established.

## Saved images

| Image | Origin and observation |
| --- | --- |
| [User-supplied Watch crop](baseline/user-watch-colored.png) | Original 643 × 585 attachment. Dark action circles, Subscribe and disclosure rows sit over a colored environment but read as flat, dark plates. |
| [Discover, initial](baseline/discover-dark.png) | Fresh emulator capture: feed header, segments, mini-player and dock over artwork. |
| [Discover, scrolled](baseline/discover-dark-scrolled.png) | Fresh capture after a vertical feed scroll; content changes behind floating chrome. |
| [You](baseline/you-dark.png) | Fresh capture: opaque-looking library launchers on a neutral dark page. |
| [Settings root](baseline/settings-dark.png) | Fresh capture: plain category cards below the floating title/back control. |
| [Playback preferences](baseline/settings-playback-dark.png) | Fresh capture: plain toggles, opaque choices and Save/Cancel footer. |
| [Watch, video ended](baseline/watch-dark-ended.png) | Fresh capture: action controls and disclosures, with the existing video ended. |
| [Description, pointer down](baseline/watch-description-pointer-down.png) | Fresh capture taken between injected DOWN and UP events on Description; state/timing alone does not prove visible press-animation progress. |
| [Description, expanded](baseline/watch-description-expanded.png) | Fresh capture after release: upward chevron and expanded description/metadata. |

[manifest.json](manifest.json) records each saved image's origin, original filename,
SHA-256, dimensions and byte size. Copies were checked against their source bytes.
The launcher screenshot from initial emulator connection is excluded because it
does not document the app's glass design.

## Historical comparisons — not new captures

These remain in their original **11 October 2026** collection:

- [Light Watch](../visual-audit-2026-10-11/screenshots/mobivious-liquid/watch-glass-light.png): existing light-mode material over fixture artwork/colors.
- [Light transparent material matrix](../visual-audit-2026-10-11/screenshots/mobivious-liquid/glass-matrix-light-transparent.png): shell, browse and Watch materials over black, white, saturated and checkerboard backdrops.
- [Original visual audit](../visual-audit-2026-10-11/REPORT.md) and [subsequent fixes](../visual-fixes-2026-10-11/FIXES.md): preserve the regression intent, especially shaped backgrounds, title legibility and player/system-bar continuity.

The older light captures were visually reviewed during planning. Their filenames
and historical dates do not establish fresh runtime acceptance or an identical
source/build to the new release captures.

## Collection procedure

The running emulator was selected explicitly because other ADB devices were
connected. Release Mobivious was opened by its MainActivity; existing screens
were navigated using ADB input. Screenshots used:

```sh
adb -s emulator-5554 exec-out screencap -p > /tmp/mobivious-overhaul-SCENE.png
adb -s emulator-5554 shell uiautomator dump /sdcard/mobivious-overhaul-ui.xml
adb -s emulator-5554 shell cat /sdcard/mobivious-overhaul-ui.xml
```

The accessibility dump helped identify controls/positions; it is a temporary
navigation aid, not saved test acceptance evidence. Description interaction used
DOWN/UP at (700, 1905) on the 1344 × 2992 display, with a screenshot between those
events. Coordinates are specific to this capture layout; future automated checks
should use control semantics instead of these fixed pixels.

No credentials, subscription writes, new downloads or fixture account mutations
were part of collection. Future implementation validation should use freshly built
debug/test APKs with local fixtures, as described in the handoff.

## Preparation verification

For this documentation pass, verify all nine PNG signatures/dimensions and hashes,
check local Markdown links through the documentation checker, and confirm the Git
diff is limited to documentation and this evidence collection. Android builds,
runtime tests, before/after frame timing and motion acceptance belong to the later
implementation and must be reported separately.
