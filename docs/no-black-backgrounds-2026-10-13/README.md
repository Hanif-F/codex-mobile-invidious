# App-wide black background cleanup — 2026-10-13

The supplied crops showed separate dark rectangles behind labels and dark discs
behind icons. `GlassLabel`, `GlassIcon` and `GlassMenuItem` painted those shapes
from `GlassContentColors.supportAlpha`. The same treatment was inherited by
navigation, tabs, subscriptions, Watch actions, disclosures and menus.

The helpers now paint only text/icons. `GlassForeground` no longer injects text
halos. Contrast is maintained by adaptive foreground polarity and a continuous
shared glass tint. Detailed artwork uses conservative bounds; generated scenes
retain lower tint where their analytic bounds guarantee readability.

Additional explicit black UI backings were removed from watched/duration labels,
clip badges, SponsorBlock prompts, player edge treatments and mini-player empty
previews. Native playback/clip-preview captions ignore window/background styles;
shutters, preview footers and native settings popups use slate, and the native
full-picture control scrim is transparent. All six modal bottom-sheet containers
also use transparent fills, removing the default dark band around drag handles.

## Audit scope

All 48 Kotlin files in the UI package and all application XML resources were
searched for background painting, dark fills, text styles, spans, native Android
views, captions and Media3 resource defaults. The bundled Media3 1.9.3 AAR was
inspected for native backgrounds omitted from the app's source.

| Area | Source and emulator checks |
| --- | --- |
| Shared rendering | Labels, multiline labels, icons, menu text and leading/trailing icons, controls, sections, dialogs, tooltips and snackbars; content-only pixel regression and contrast matrix. |
| Shell and discovery | Popular/Trending, categories/region, dock/sidebar, selection, mini-player, global search and filters, narrow/large text, wide layout, Light/Dark/System. |
| Settings and accounts | Every category, server, account, sign-in/create-account forms, sessions/API tokens, expiry menus, subscription errors, confirmations and keyboard layouts. |
| Subscriptions/channels | Feed, channel directory, search/sort, creator headers/tabs, subscription states, descriptions, RSS/OPML, channel SponsorBlock settings. |
| Library | You, playlists, playlist editors/details, history, clips and clip editor/preview, download selections, progress/failure/details and delete dialogs. |
| Watch | Actions, Subscribe, disclosures, rich description, metadata, comments/replies, chapters, queue, chat, player settings, SponsorBlock, seek feedback, portrait/fullscreen/PiP and opaque mode. |
| Secondary content | Community posts/galleries/comments, avatars, badges, rich links, empty/loading/error states and native captions. |

Black remains only as a contrast bound, alpha-mask input, thin glyph/track shadow
or actual decoded video content. No app modifier paints a `Color.Black` background
behind UI content. The app's dark appearance and shared glass containers remain.

## Verification

The native-caption/secondary run passed all 74 selected cases. Another 10
screen-family checks and 5 capture checks also passed. The combined core/journey
run passed 45 cases; its one caption-fixture failure was corrected by hiding the
empty decoder surface in that isolated rendering test, then passed in the
74-case run. The final 14 bottom-sheet rechecks also passed, including a further community
comments journey. Together these runs cover 123 distinct device tests with
passing results. [Machine-readable totals](verification.json) and the individual
run results are retained under [logs/](logs/).
The JVM suite passed all 347 cases. Lint reports zero errors and 36 existing
warnings. Documentation inventories/fingerprints and `git diff --check` pass.

The emulator remained running throughout. Tests use disposable localhost API
fixtures, Light/Dark/System appearances, narrow layouts with 2× text, wide
layouts, reduced transparency, fullscreen, PiP, keyboards and error states.

[Selected previews](screenshots/) and their source/preview hashes are indexed in
[screenshots.json](screenshots.json). The previews are reduced JPEGs for review.
[Contact sheets](contact-sheets/) cover all 237 fresh captures; 34 selected
previews are included alongside them.
[Raw capture metadata](raw-screenshots.json) records dimensions, device timestamps
and original PNG hashes; its paths resolve under the ignored local directory
`.tools/no-black-backgrounds-evidence/`, where the full-resolution originals are
retained. Keeping the raw collection there avoids adding hundreds of megabytes
of generated PNGs to the source tree.

The screenshots may include Android/Gboard clipboard suggestions or system
hover tooltips; those are separate platform windows. The app's text/icon content
and its native player/clip controls follow the transparent/slate policy above.
