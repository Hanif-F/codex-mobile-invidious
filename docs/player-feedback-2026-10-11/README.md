# Compact player feedback

SponsorBlock now uses a content-sized dark **Skip ×** pill at the bottom left. It sits above the playback footer while controls are visible and moves to the safe bottom edge when controls hide. Existing skip confirmations appear as a compact, single-line notice. In short players, the controls share the compact footer row; when space is insufficient for both the confirmation and actions, the actions take priority.

Backward and forward seek feedback now uses white direction icons and signed seconds with a black shadow and no background. Gesture accumulation and accessibility actions share this presentation.

## Verification

- Debug build and lint passed (no lint errors).
- All 347 unit tests passed.
- Seven existing player-control presentation checks and three new feedback presentation checks passed on the running emulator.
- Nine playback smoke checks passed, covering SponsorBlock settings, skip/dismiss behavior, notices, keyboard/accessibility seek, repeated taps, direction changes, fullscreen, background playback, and PiP.
- Visually inspected portrait/fullscreen SponsorBlock and seek feedback on bright/dark backgrounds, including Reduce transparency. Presentation checks also cover controls hidden, compact sizes, and large text.

## Screenshots

- [Portrait SponsorBlock](portrait-sponsorblock.png)
- [Fullscreen SponsorBlock with confirmation](fullscreen-sponsorblock.png)
- [Forward seek on a bright background](forward-light.png)
- [Backward seek on a dark background](back-dark.png)
