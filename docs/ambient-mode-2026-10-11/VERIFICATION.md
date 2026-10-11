# Vivid ambient mode verification

Implemented and verified on 2026-10-11 using the Pixel 8 Pro emulator running Android 16.

Ambient mode samples the existing SurfaceView into one reusable 32×18 bitmap, at most four times per second. Nine spatial colors animate over 600 ms behind the expanded watch view and its glass controls. Dark/light glow opacity is capped at 55%/30%; colors receive a 1.35× saturation boost. Illumination is bounded to preserve supporting-text contrast. The background recording excludes the video and consuming glass.

The device preference defaults to enabled and is shared by player settings and Appearance. Paused seeks refresh the glow; pause, buffering and completion retain its last colors. Sampling stops outside expanded watch, during transitions, in the background, and for audio-only playback. Reduce transparency, disabled system animations, battery saver and unavailable hardware acceleration suppress the effect. Fullscreen, PiP and mini-player retain their existing appearance.

## Results

| Check | Result |
| --- | --- |
| Unit tests | 347 passed, including spatial sampling, contrast bounds, fitted-picture geometry, throttling, failure backoff and stale/canceled copy handling |
| Glass/watch regression batch | 14 passed: ambient rendering and playback, glass materials, watch controls, player resizing and watch/mini viewport transitions |
| Additional presentation batch | 6 passed: ambient playback/settings, light mode, wide and large-text watch, comments and viewport regression |
| Finished APK integration batch | 3 passed: live color phases, system-animation round trip, paused seeking, both settings switches, persistence, audio-only mode, restoration, wide/large-text layouts and comments |
| Debug app and instrumentation APKs | Built successfully; final debug app installed and opened on the emulator |
| Lint | 0 errors, 36 existing warnings; no warnings in the new ambient implementation |
| Emulator | Remained connected and healthy; density, font scale and animation scale restored; owned fixture server and port reverse cleaned up |

The instrumentation batches overlap. Their screenshots use generated local media, with no YouTube dependency. Rendered samples verify that glass changes color and retains at least 4.5:1 supporting-text contrast over bright, dark and colored footage in both themes. Physical-device frame pacing and battery usage were not benchmarked.

## Visual evidence

- [Light/dark comparison](preview.jpg)
- [Normal-speed red → green → blue recording](ambient-motion.mp4)
- [Dark red](dark-red.png), [dark blue](dark-blue.png), [light blue](light-blue.png)
- [Wide watch](wide.png), [large text](large-text.png), [comments reading surface](comments.png)
- [Machine-readable check summary](verification.json)

The recording is a ten-second, normal-speed extract of the emulator display, cropped only to remove encoder pillarboxing. Its solid color phases deliberately make smoothing and the glass response easy to inspect.

## Reproduce

With one running emulator:

```sh
bash scripts/test-android.sh -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AmbientModePresentationTest,net.wingress.mobivious.AmbientModeSmokeTest,net.wingress.mobivious.WatchGlassPresentationTest,net.wingress.mobivious.DarkGlassPresentationTest,net.wingress.mobivious.WatchPlayerResizePresentationTest,net.wingress.mobivious.WatchCommentsPresentationTest,net.wingress.mobivious.VisualFixesSmokeTest#watchViewportStaysFixedAcrossMiniChromeChangesAndCoversTheBottomInset
```

To record the red/green/blue journey externally, also pass `-Pandroid.testInstrumentationRunnerArguments.ambientRecord=true`; this extends the live playback test through the blue phase. The test runner uses a disposable local fixture and does not start an emulator.
