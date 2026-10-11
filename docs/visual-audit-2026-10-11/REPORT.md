# Mobivious visual audit — 11 October 2026

Status: audit complete. **All eight findings have now been fixed and verified**; see the [fixes and verification report](../visual-fixes-2026-10-11/FIXES.md). The descriptions and evidence below preserve the original, pre-fix audit. The implemented screens and states in the coverage inventory were inspected on the available emulator.

| ID | Priority | Finding |
| --- | --- | --- |
| V01 | P2 | Rectangular backing outside rounded Comments, Retry and session buttons |
| V02 | P2 | Launcher icon silhouette conflicts with adaptive masks |
| V03 | P3 | Sharp playlist attachment background inside rounded posts |
| V04 | P2 | Scrolling text overlaps settings titles with large text |
| V05 | P2 | Old browse content remains below expanded watch, around the gesture bar |
| V06 | P2 | Watch/mini animation includes an abrupt layout resize |
| V07 | P2 | Compact AI warning text is clipped and covered by the duration |
| V08 | P2 | Floating navigation covers the focused CAPTCHA field when the keyboard opens |

P2 denotes a visible usability/presentation defect; P3 denotes a smaller consistency defect. These priorities are audit judgments.

## Build and environment

- Source: `94caf8645b0944311f93471577eea344336c4bf5`.
- Release installed: Mobivious 0.10.1, version code 19. The source debug build is installed separately as Mobivious Preview.
- Running Pixel 8 Pro emulator, Android 16, 1344 × 2992 pixels, density 480 (448dp portrait width), initial font scale 1.0.
- Additional inspection uses 320dp width/2.0 font scale, wide layouts, landscape/fullscreen, light/dark/system appearance, reduced transparency, compact lists and hidden thumbnails.
- Live release browsing plus disposable localhost fixture content/accounts. No real account credentials or account mutations.
- Screenshots are fresh captures from this audit. Earlier screenshots left on the emulator are excluded using a start-time marker.

## Confirmed findings

### V01 — Square backing corners outside rounded action buttons

Priority: P2. Confirmed in light and dark appearance.

Open a channel → Posts and scroll to the Comments button below a post. The pill has rounded ends, but its backing surface remains rectangular. Pale/charcoal triangular corners remain outside the pill, inside the post card. This is the corner defect described in the request; it also appears on standalone post details and every post attachment type.

The same defect is particularly obvious on the Retry button inside the red browse error card and on Sign out/Revoke buttons inside account session cards. These are additional affected locations of the shared backing defect.

Evidence: [light post](screenshots/mobivious-visual-audit/post-1-light.png), [dark post](screenshots/mobivious-visual-audit/post-1-dark.png), [post details](screenshots/mobivious-visual-audit/post-detail-light.png), [Retry on red error card](screenshots/mobivious-visual-audit/browse-error-dark.png), [session actions](screenshots/mobivious-visual-audit/account-sessions-loaded-dark.png).

Relevant source: `CommunityUi.kt` → `PostActions`; `LibraryGlassUi.kt` → `LibraryControlScene` / `LibraryActionButton`. The scene draws a full rectangular `surface` backing, while only its child button receives the pill clipping. The post card uses `surfaceContainer`, exposing the backing corners.

Suggested correction: make the sampled backing invisible outside the control, or clip the backing and control to one shared shape. Check all callers of the shared scene on contrasting container surfaces.

### V02 — Adaptive launcher icon conflicts with outer masks

Priority: P2. Confirmed actual circular launcher rendering; square and rounded-square diagnostic renders use the packaged Android foreground/background layers.

The foreground already contains a nearly full-size rounded-square tile and a lower shadow. A circular launcher mask cuts through that tile/shadow, leaving flat strips and clipped corners. Square masks show a second rounded frame inside the outer frame, with a conspicuous lower gray edge. The play symbol itself remains visible; the problem is the surrounding silhouette and its sizing.

Evidence: [actual launcher](screenshots/003-launcher-circle.png), [circle mask](icons/icon-circle.png), [square mask](icons/icon-square.png), [rounded-square mask](icons/icon-rounded-square.png).

Relevant source: `res/drawable/ic_launcher.xml`, `res/drawable/ic_launcher_monochrome.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`. Both normal and round icons use the same adaptive drawable. The tile occupies approximately x=22–86/y=21–85 of the 108-unit foreground; its corners and lower shadow extend outside the circular mask's visible region after adaptive scaling.

Suggested correction: let the adaptive background define the outer silhouette, keep the main foreground symbol within a conservative safe region, and validate circle/square/rounded-square plus themed icons at launcher sizes.

### V03 — Playlist attachments introduce a sharp rectangular background inside post cards

Priority: P3. Confirmed in light and dark appearance.

Open a channel → Posts and find a post with an attached playlist. The playlist row sits on a separate sharp rectangle, including a bottom divider, instead of blending into the containing rounded post card. The darker rectangle is especially conspicuous in dark appearance; light appearance shows a gray block inside the white post.

Evidence: [dark playlist post](screenshots/mobivious-visual-audit/post-5-dark.png), [light playlist post](screenshots/mobivious-visual-audit/post-5-light.png).

Relevant source: `CommunityUi.kt` embeds `PlaylistCard`; `PlaylistUi.kt` wraps the row in `BrowseArtworkSurface(null)`; `BrowseGlassUi.kt` paints the app-wide `surface` behind that row. Its parent is a `surfaceContainer` post card.

Suggested correction: give embedded playlist rows a presentation that inherits the post surface, with consistent padding and deliberate rounded media boundaries.

### V04 — Large-text settings titles overlap scrolling text

Priority: P2. Confirmed at 320dp width with 2.0 font scale, light and dark appearance.

Open Settings → DeArrow (also Playback, Browsing, History & library and Subscriptions), then scroll. Body text passes visibly through the pinned title area. In DeArrow, “identity. This is not a” occupies the same line as the bold DeArrow heading. This produces overlapping letterforms rather than a quiet backdrop.

Evidence: [dark DeArrow overlap](screenshots/mobivious-visual-audit/settings-DeArrow-narrow-dark-2x-3.png), [initial light overlap detail](details/settings-DeArrow-narrow-dark-2x-3-detail.png). The initial detail image is light despite its old filename; the full screenshot has been replaced by the correctly controlled dark recheck. The same issue appears in the AI filter, About, Appearance and account token screens.

Relevant source: `BrowseGlassUi.kt` → `BrowseGlassSurface`, `LibraryGlassUi.kt` → `LibraryToolbar`. At large font scale the title is placed in a separate lower row, near the transparent end of the toolbar's vertical fade. Large scrolling text remains visible behind it.

Suggested correction: keep an opaque or adequately blurred reading backdrop behind the entire title area; start the fade below the title's measured bounds. Verify while scrolled, not only at the initial position.

### V05 — Browse content leaks into the expanded watch view's bottom system-bar area

Priority: P2. Reproduced during ordinary app use, after the expansion has fully settled.

1. Open a populated, paginated mixed search and play a result.
2. Collapse to the mini-player, then tap its preview to restore the expanded watch view.
3. Inspect the bottom edge around the Android gesture bar.

The watch page stops above the navigation-bar inset. A strip of the earlier browse page remains visible behind the gesture bar, with a hard horizontal seam. In the fixture reproduction, the watch recommendation thumbnail ends at one pattern position while the unrelated browse thumbnail continues below it. This confirms the persistent bottom defect described in the follow-up.

Evidence: [bottom close-up](details/watch-bottom-light.png), [full settled watch](screenshots/mobivious-visual-audit/manual-watch-bottom-light.png), [normal-speed recording](motion/watch-realtime.mp4).

Relevant source: `MobiviousApp.kt` → Scaffold `bottomBar`, computed `watchPadding`, and `WatchScreen`. `WatchScreen` receives bottom padding before its opaque background; the sibling browse layer fills the whole window. The excluded navigation-bar region is therefore painted by the older browse layer.

Suggested correction: give the expanded watch layer an opaque, full-window background including the bottom inset, while applying safe-area padding only to its contents. Verify over busy browse content with gesture and three-button navigation.

### V06 — Watch/mini transition changes its layout abruptly while animation is running

Priority: P2. Reproduced with normal device taps, Back and drag input, outside the Compose test clock.

Restore the mini-player over Search, then minimize with Back or drag down. The expanded details and live player animate while the bottom search field/mini layout still reserves a different amount of space. At the end of restoration, the search field disappears and the watch viewport abruptly grows; minimization performs the reverse reflow. This creates a visible step alongside the fade and moving player, especially with colorful content behind it. The recorded restore around 4.2–4.5 seconds shows the search field still visible under an almost opaque watch view, followed by the sudden bottom resize. Drag collapse around 10 seconds also shows the two page layouts visibly superimposed while the live surface moves.

Evidence: [normal-speed recording](motion/watch-realtime.mp4), [restore frame sequence](motion/realtime-expand-frames.jpg), [Back collapse sequence](motion/realtime-back-frames.jpg), [drag collapse sequence](motion/realtime-drag-frames.jpg). Frames are time-labeled samples; use the recording to assess motion.

Relevant source: `PlayerPresentation.kt` → `allocatesMini` / `showsWatch` / `present` / `settle`; `MobiviousApp.kt` → conditional Scaffold bottom chrome, `MiniPlayer.layout`, and `watchPadding`. Mini height allocation and bottom chrome change when `mode`/`target` change, while the surface rectangle and page opacity are animated separately. This is a source-supported explanation for the observed layout discontinuity; exact hardware frame pacing was not isolated.

Suggested correction: keep source and destination geometry stable throughout the transition, interpolate layout/insets consistently, and commit the final chrome allocation without a visible resize. Check restore, Back collapse, drag completion/cancellation and repeated reversal over both Discover and Search.

### V07 — Compact AI warning thumbnails clip their text and cover it with the duration badge

Priority: P2. Confirmed in the compact discovery list at default font scale.

Enable AI Channel Filter → Replace thumbnails, then use Appearance → Video list density → Compact. An affected video's replacement thumbnail shows oversized warning text. The lower “AiSList Blocklist” / “AiSList Warnlist” line is cut off vertically, and the duration badge obscures part of “AI-generated”. The intended warning/source cannot be read cleanly.

Evidence: [compact warning close-up](details/ai-compact-warning.png), [full compact list](screenshots/mobivious-ai-screenshots/warnings-dark-compact.png). Compare the correctly fitting comfortable layout in [light warnings](screenshots/mobivious-ai-screenshots/warnings-light.png).

Relevant source: `MobiviousApp.kt` → `VideoCard` computes `compact` from list density but passes `compact = prefs.thinMode` to `AiThumbnail`. Consequently a normal compact row receives the large 16sp/12sp treatment from `AiFilterUi.kt`. The duration badge also paints over the same area.

Suggested correction: select the warning typography from the actual thumbnail size/list layout, reserve space for badges, and verify both warning levels with compact, library, queue and large-text layouts.

### V08 — Keyboard opening hides the focused CAPTCHA answer behind floating navigation

Priority: P2. Confirmed at default font scale with the docked Gboard keyboard, using ordinary taps and text input outside instrumentation.

1. Use an instance that returns a registration CAPTCHA and open You → Sign in → Create account.
2. Scroll until the Clock time field is comfortably above the bottom navigation.
3. Tap the field and enter a time.

Opening the keyboard pans the page so the floating Discover/Subscriptions/You navigation sits directly over the focused Clock time field. Its label, caret and typed answer are hidden. The Sign in toolbar is also pushed into the Android status bar. Dismissing the keyboard reveals the typed `4:00:00`, confirming that the hidden field retained focus and accepted input. This is more than initial content passing behind floating chrome: the focused input remains covered after the keyboard has settled.

Evidence: [focused field with keyboard](screenshots/mobivious-visual-audit/manual-captcha-keyboard-light.png), [typed answer still hidden](screenshots/mobivious-visual-audit/manual-captcha-keyboard-typed.png), [answer visible after keyboard dismissal](screenshots/mobivious-visual-audit/manual-captcha-keyboard-dismissed.png).

Relevant source: `AccountUi.kt` → `SignInScreen` applies `imePadding` and uses `LocalContentBottomInset` for scroll padding; `MobiviousApp.kt` independently applies keyboard/navigation insets to its floating bottom bar. `MainActivity` enables edge-to-edge, and the activity manifest does not explicitly select a soft-input adjustment mode. These are the relevant inset/focus paths to investigate; the precise combination causing the observed window pan was not isolated.

Suggested correction: keep the focused field and its caret above both the keyboard and floating navigation, consume keyboard insets consistently, and keep the toolbar inside the top safe area. Verify CAPTCHA and other low-positioned form inputs with normal keyboard input, including large text.

## Coverage and final results

The inspection covers the implemented UI areas below, including populated, empty, loading, error and modal states where applicable. The 440 full screenshots are under [screenshots](screenshots/); [evidence-manifest.txt](evidence-manifest.txt) lists their paths, and [contact sheets](contact-sheets/) provide overview images. Captures were visually reviewed; a successful interaction test alone was not treated as proof that a screen looks correct. All saved image files passed decoding checks, all report links resolve, and the motion recording was verified as a readable 30-second video.

| Area | Inspected screens and states | Result |
| --- | --- | --- |
| App shell and discovery | Discover/Subscriptions/You navigation, Popular/Trending, region and category menus, refresh, pagination, loading/empty/error, search field and docked keyboard | V01 on error Retry |
| Search | Mixed results, videos/channels/playlists, filter sheet, scoped channel search, no matches, hashtag results, populated paginated list under watch | V05/V06 when watch overlays Search |
| Subscriptions and creators | Guest and signed-in feeds, subscribed directory, channel header/actions, RSS, description sheet, all ten channel tabs: Videos, Shorts, Streams, Podcasts, Releases, Courses, Playlists, Posts, Channels, Clips | V01/V03 in Posts |
| Community posts | Text, image, gallery, video, playlist, poll, quiz and unsupported attachment; long text, standalone post, image viewer, comments, replies, pagination and sharing | V01/V03 |
| Expanded watch | Player plus Comments/Description buttons, recommendations, rich description and chapters, portrait/square/landscape/ultrawide media, wide/fullscreen layouts, loading/errors | V05 at the bottom inset |
| Player controls | Seek/play/pause, exact video/audio tracks, quality/captions/speed menus, audio-only mode, controls settings, refresh/retry/reconnection, queue and manual queue | No additional visual finding |
| Watch overlays | Comments and replies; live/replay chat, dock/overlay controls, dimensions/settings; SponsorBlock controls and category settings; DeArrow original/replacement titles and artwork | No additional visual finding |
| Player presentation | Mini-player, restoration, Back collapse, drag collapse and reversal, repeated normal-speed transitions, reduced motion, picture-in-picture, background media notification | V06; V05 persists after settling |
| You and playlists | Guest/empty/populated library, owned/subscribed playlists, detail/edit/create, title/privacy/default controls, signed-in/guest Save sheets and empty/title/error states | Shared backing issue V01 in contrasting containers |
| History, clips, downloads | History and deletion; clip editor/preview/detail/delete; download selection, active/failed/detail/delete and offline playback UI; export/conversion consent | No additional visual finding |
| Settings | Playback, Appearance, Browsing, Subscriptions, History & library, SponsorBlock, DeArrow, AI Channel Filter, Server and About; scrolled content and selection menus | V04; V07 in compact AI warnings |
| Account | Guest sign-in/create account, loading/credential errors, registration CAPTCHA/disabled state, username/password, sessions, token scopes/expiry and delete confirmation | V01 on session buttons; V04 on large-text token page; V08 on focused CAPTCHA |
| Appearance/accessibility | Light/dark/system and explicit appearance overrides, normal/reduced transparency, large text (2.0), narrow width (320dp), wide layout, compact lists, hidden thumbnails, AI replacement warning levels | V04/V07 |
| Android surfaces | Actual circular launcher, packaged icon layers under three diagnostic masks, share chooser, docked keyboard on search/sign-in/registration, picture-in-picture and media notification | V02/V08 |

### Automated support and reproducibility

The existing instrumentation suites supplied realistic navigation/content states and captured the rendered emulator. Additional temporary capture journeys exercised screens not already covered. [The capture harness](capture-harness.kt.txt) is retained as text for reproduction, rather than left in the app's test source tree. Fixture services used disposable local content and accounts.

| Run | Outcome | Evidence |
| --- | --- | --- |
| Core UI journeys | 20 tests passed | [core log](logs/visual-audit-core.txt) |
| Advanced discovery/community/chat/queue/stream journeys | 76 tests passed | [advanced log](logs/visual-audit-advanced.txt) |
| Initial supplemental capture journeys | 4 tests passed; incorrect theme setup was subsequently corrected and recaptured | [supplement log](logs/visual-audit-supplement.txt) |
| Final presentation/capture batch | 24 of 27 passed; two capture-harness setup errors were corrected; one AI interaction timed out | [final log](logs/visual-audit-final.txt) |
| Corrected settings/account capture | Passed; the AI interaction timeout repeated | [recheck log](logs/visual-audit-recheck.txt) |
| Watch capture and remaining sheets | Motion capture, supplemental panels, and corrected remaining sheets passed | [motion](logs/visual-audit-motion.txt), [panels](logs/visual-audit-panels.txt), [remaining](logs/visual-audit-remaining-final.txt) |
| Glass materials and palette | Two tests passed, including rendered backdrop contrast checks | [contrast log](logs/visual-audit-conditional.txt) |
| Conditional registration states | Corrected capture journey passed; CAPTCHA focus was additionally reproduced with normal input | [registration recheck](logs/visual-audit-conditional-recheck.txt) |

The repeated `AiFilterSmokeTest` guest-toggle timeout is recorded separately from visual findings. Its settings and warning layouts were visually inspected, but this audit does not establish the cause of that interaction failure. Earlier capture-only failures and their corrected reruns remain in [logs](logs/).

The ordinary-input [watch recording](motion/watch-realtime.mp4) is the motion evidence for V06. Instrumentation's Compose clock can freeze or skip animations during capture, so those instrumented transitions were not used to judge real-time smoothness. The aggregate [graphics statistics](logs/visual-audit-gfxinfo.txt) mix navigation, loading and recording; they are not an isolated transition benchmark.

### Inspection boundaries

This completes the screen/state inventory above on the available Android 16 emulator. It cannot establish that every possible content length, server response, Android version or device is defect-free. Square and rounded-square icon previews are diagnostic renders of packaged adaptive layers, not captures of alternative launchers. Actual background export/conversion execution was outside this visual audit; its consent UI was inspected. No emulator crash occurred during this audit.

The temporary capture test was removed from the Android source tree after preserving a text copy. Audit fixture services and their ADB reverse mappings were stopped. Display density/font scale were restored to 480/1.0, and the emulator was left running Mobivious. Only this report/evidence folder remains as a repository change.

### Source locations for follow-up

- V01: [PostActions](../../android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt), [shared button backing](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryGlassUi.kt).
- V02: [launcher foreground](../../android/app/src/main/res/drawable/ic_launcher.xml), [monochrome foreground](../../android/app/src/main/res/drawable/ic_launcher_monochrome.xml), [adaptive icon](../../android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml).
- V03: [post attachments](../../android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt), [playlist card](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt), [artwork surface](../../android/app/src/main/java/net/wingress/mobivious/ui/BrowseGlassUi.kt).
- V04: [toolbar](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryGlassUi.kt), [toolbar backdrop](../../android/app/src/main/java/net/wingress/mobivious/ui/BrowseGlassUi.kt).
- V05/V06: [watch padding and bottom chrome](../../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt), [presentation state and animation](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt).
- V07: [video card](../../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt), [AI thumbnail](../../android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt).
- V08: [authentication form and scroll insets](../../android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt), [floating chrome](../../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt), [activity](../../android/app/src/main/java/net/wingress/mobivious/MainActivity.kt), [manifest](../../android/app/src/main/AndroidManifest.xml).
