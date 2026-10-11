# Mobivious visual fixes — 11 October 2026

Status: **all eight findings fixed and verified**. The [original audit](../visual-audit-2026-10-11/REPORT.md) retains the before images and descriptions. These changes are in the working tree based on `94caf8645b0944311f93471577eea344336c4bf5`.

The updated debug app is installed as **Mobivious Preview** (`net.wingress.mobivious.debug`). The separately installed Mobivious 0.10.1 release remains the original build. No version bump or publication was requested.

| Finding | Implemented correction | After evidence |
| --- | --- | --- |
| V01 — Action corners | Removed the rectangular wash behind shared quiet controls. Comments, Retry, session actions and confirmation buttons paint their own rounded material. | [Comments](screenshots/post-comments-dark.png), [Retry](screenshots/retry-dark.png), [sessions](screenshots/session-actions-dark.png) |
| V02 — Launcher icon | Moved the full-bleed gradient to the adaptive background. The color and themed foregrounds contain only the play symbol, inside the safe region. | [Six native mask renders](icons/masks.jpg): circle, square and rounded square, color and themed; [actual launcher](screenshots/launcher-preview-circle.png) |
| V03 — Embedded playlist | Embedded rows inherit the containing post surface and omit the browse divider and extra horizontal inset. | [Light](screenshots/post-playlist-light.png), [dark](screenshots/post-playlist-dark.png) |
| V04 — Title overlap | The entire measured toolbar has an opaque reading backdrop; its fade begins below the controls and wrapped title. Multi-part toolbars remain vertically arranged. | [Scrolled DeArrow at 2× text](screenshots/settings-dearrow-large-dark.png), [Playback](screenshots/settings-playback-large-dark.png), [API token](screenshots/token-large-dark.png) |
| V05 — Watch bottom edge | Expanded watch owns an opaque full-window layer. System-bar insets apply to content inside that layer. | [Normal-input restoration](screenshots/watch-manual-bottom-light.png), [dark](screenshots/watch-restored-dark.png), [three-button navigation](screenshots/watch-threebutton-light.png) |
| V06 — Watch/mini motion | Watch geometry is independent of browse Scaffold chrome and mini height allocation. Reversal retains the current animation fraction. Back during restoration reverses it. | [Normal-speed recording](motion/watch-realtime.mp4), [restore](motion/restore-frames.jpg), [Back](motion/back-frames.jpg), [completed drag](motion/drag-complete-frames.jpg), [canceled drag](motion/drag-cancel-frames.jpg), [rapid reversal](motion/rapid-reversal-frames.jpg) |
| V07 — AI warning layout | Typography follows actual density. Warning tiles reserve badge space and grow to fit scaled text, including library rows and clips. | [Compact](screenshots/ai-compact-dark.png), [text-only](screenshots/ai-text-only.png), [large-text warnings](screenshots/ai-warnlist-large-dark.png), [large-text clips](screenshots/ai-clips-large-dark.png) |
| V08 — CAPTCHA keyboard | Explicit resize behavior and consumed bottom insets keep the form above the dock/IME. Its scroll viewport starts below the pinned toolbar, keeping focused fields clear of both edges. | [Ordinary clock input](screenshots/captcha-manual-keyboard-light.png), [320dp/2× text](screenshots/captcha-large-keyboard.png) |

## Verification

Pixel 8 Pro emulator, Android 16, 1344 × 2992. Checks include 448dp/default text and 320dp/2× text, light/dark appearance, compact/text-only lists, both AI warning levels, account forms and scrolled settings. In the launcher capture, the updated Preview icon is at the left of the row below the original Mobivious release icon. Color bars in images are intentional local fixture media. The large-text automated CAPTCHA uses that fixture thumbnail; the normal-input reproduction uses a clock image.

| Check | Result | Evidence |
| --- | --- | --- |
| Debug app, permanent instrumentation APK, unit tests and lint | Build passed; **339 unit tests passed**; lint has **0 errors, 20 warnings** | [Build](logs/build.txt), [machine-readable summary](logs/checks.json) |
| Visual regression and capture batch | **17 tests passed** | [Log](logs/verified-17-tests.txt) |
| Final form correction at 320dp and font scale 2.0 | **1 test passed**; focused input is below the full toolbar and above navigation | [Log](logs/captcha-large.txt) |
| Normal-size CAPTCHA, account/subscription authentication and return-to-watch | **7 tests passed** after the final form correction | [Log](logs/final-forms.txt) |
| Ordinary device input | Restore, Back, drag completion/cancellation and rapid reversal recorded over mixed, paginated Search; settled bottom inspected with gesture and three-button navigation | [Input timings](motion/input-events.json), [video metadata](motion/video-info.json) |

The watch regression also samples its bounds during restoration and collapse over both Discover and Search, then checks bottom pixels after settling. The motion frame sequences show stable watch details throughout the transition, without the prior final resize. Physical-device frame pacing was not benchmarked.

Rendered screenshots were inspected in addition to interaction assertions. Native icon tests check the painted foreground against the safe circle. Surface tests verify button corner pixels, full-height title opacity and multi-part toolbar placement. AI tests verify rendered text bounds and separation from duration badges. The fixture now supports an optional registration CAPTCHA while keeping its default response unchanged.

An [intermediate 49-test run](logs/intermediate-49-tests.txt) exposed three failures: an incorrect warning-width assertion, a feed-only classification wait while still on You, and a toolbar-container regression introduced during the fix. The assertion and test navigation were corrected; the toolbar container was corrected and given a regression check. All affected paths passed in the final batches above. A further large-text manual check led to the final form viewport correction and the additional passing form checks.

The temporary [capture harness](capture-harness.kt.txt) is retained as text, outside the app's test source tree. Permanent regression tests remain in `VisualSurfaceRegressionTest.kt`, `VisualFixesSmokeTest.kt`, `AiFilterSmokeTest.kt` and `PlayerGesturesTest.kt`. Display density, text size and gesture navigation were restored. The Preview was returned to its normal HTTPS instance; the owned fixture server and ADB port reverse were removed. The emulator remained healthy throughout verification.
