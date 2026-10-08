# Invidious web → Mobivious Android feature checklist

Reviewed on **7 October 2026** against the local source checkouts:

- Web: `../invidious` at `f260f8e5` plus native clip token scopes, channel Clips metadata and additive account-profile metadata. Includes this fork's custom features and native API extensions.
- Android: `32d5c14` plus the release version bump. Configured version: `0.7.0` (version code 13); release tag: `v0.7.0`.

**The status column describes implementation in the Android app compared with the web version, within the intended native scope.** The web feature is the baseline; a server endpoint by itself does not count as an Android feature unless the app uses it and provides the relevant interaction. Deliberately omitted web capabilities are recorded under [Intentionally excluded parity](#intentionally-excluded-parity) and do not make the related native feature Partial.

- **Implemented:** the listed core capability has an Android implementation.
- **Partial:** some parts exist; the last two columns identify what exists and what is missing.
- **Not implemented:** no corresponding Android flow was found in the reviewed source.

The status combines source/build checks with the focused native checks recorded in [VERIFICATION.md](VERIFICATION.md). Clips passed 284 Android unit/API tests, debug app/test APK builds, focused Android 16 emulator checks and server authorization/database checks. Older dated entries retain their individual runtime limitations. Native clip creation, ownership and channel lists require the sibling API/token-scope update and renewed sign-in; migration 19 already supplies storage. This audit does not verify a production rollout or upstream YouTube clip availability.

Account-separated device saves add stable ownership, repeatable local migration,
guest channel SponsorBlock overrides and guest channel blocking. Their Android,
emulator and server evidence is recorded in the 8 October entry in [VERIFICATION.md](VERIFICATION.md).

**Summary: 48 broad feature areas — 31 implemented, 10 partial, 7 not implemented.** These counts describe the in-scope grouping below, not a weighted completion percentage. Excluded capabilities at the bottom are not counted as missing work. IDs 01–45 are retained; newly listed player tools use IDs 46–48.

## Discovery and channels

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 01 | Anonymous viewing through Invidious, without a Google account or official YouTube API | Implemented | Public browsing/playback through Invidious and Companion; no client-side YouTube extraction. | — |
| 02 | Popular and trending discovery, with regional/category selection | Partial | Popular and trending Home chips; editable trending region. The fork's default trending source is Livestreams. | Livestreams/Gaming category selector. Music/Movies are not categories offered by the current web fork. |
| 03 | Custom homepage and configurable feed navigation | Partial | Default homepage selection across Popular, Trending, Search, Subscriptions and Playlists; saved web feed-menu preferences order Home discovery chips. Four fixed bottom tabs: Home, Subscriptions, Library, Account. The top-right search field submits to existing results and Back restores the originating screen and browsing state. Search homepage support is retained. Guests fall back to public browsing for account-only homepages. | Full web menu visibility/removal; core native tabs remain fixed and Popular/Trending share Home. |
| 04 | Search across videos, channels and playlists, with filters | Partial | Videos remain the default; Playlists & mixes uses typed playlist search, pagination, source labels and preserved mix seeds. Video relevance/views sorting, upload-date filters, Short/Long duration, links and content-visibility controls remain available. | Channel result cards; All/Channel/Movie/Show type controls; Medium (4–20 minute) duration; feature-filter controls for Live, 4K/HD, captions, Creative Commons, 360°/VR180/3D, HDR, location and purchased content. Native filters do not expose the web's individual active-filter removal chips. |
| 05 | Channel browsing and specialized tabs | Implemented | Availability-driven Videos/Shorts/Streams/Podcasts/Releases/Courses/Playlists/Clips/Posts/Channels in website order; community/posts normalization and Videos fallback; video/playlist/clip/channel/post cards, opaque continuation pagination and 30-item clip pages. Shared video Newest/Oldest/Popular sorting and independent playlist Last added/Newest/Oldest sorting; controls hidden where ignored. Banner/avatar/name/subscribers, verification and pronouns; three-line description preview and complete selectable rich description with native links. Tab/sort/content/scroll restoration after search and child navigation; subscribe/unsubscribe and RSS. Public Clips lists contain clips created on the selected instance. | — for the intended native scope. Clips metadata requires the sibling server update; broader historical channel runtime limits remain recorded below. |
| 06 | Search within a channel or the subscription library | Implemented | Search fields on channel and Subscriptions screens; public paginated channel video search independent of Videos/Streams; authenticated full-library title/channel subscription search, original-page pagination and saved search visibility controls. Clear restores the selected channel tab or configured feed. Icon, keyboard Search and physical Enter share submission, including pasted links on general Search. | — for the core capability; subscription search requires the sibling API update and renewed native tokens. Native runtime/layout acceptance remains unverified. |
| 07 | Community posts and post comments | Implemented | Paginated channel posts with six-line rich-text previews and outlined Read more/Show less controls; expansion survives feed scrolling and Activity recreation. Read-only likes and a Share icon occupy one row, followed by a full-width Comments button that opens the independent sheet directly over the Posts tab. Dedicated native details remain available for incoming/rich links and reuse the same card actions. Author/publication/edit metadata, single images/enlarged swipeable galleries, native video/playlist attachments, read-only polls/quizzes and unknown-attachment fallback. Comments support Top/Newest, paginated replies, Back, retained scroll and cursor-preserving retry; stale navigation/sort/account/instance responses rejected. Opening shared posts minimizes active playback and preserves video comments. | See the post-card overhaul verification entry for current focused emulator results; full-suite acceptance remains separate. |
| 08 | Hashtag browsing | Not implemented | — | Hashtag result pages and navigable hashtag links. |

Evidence: [web routing][w-routing], [web trending][w-trending], [web search filters][w-search], [web filter controls][w-search-ui], [web channel routes][w-channels], [web channel header][w-channel-ui], [web community contracts][w-community]; [Android browsing/navigation][a-ui], [full channel description][a-channel-description], [community models/link parsing][a-community], [native posts and comment sheet][a-community-ui], [Android view model][a-vm], [Android API][a-api].

## Watching and player controls

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 09 | Standard video playback | Implemented | Media3 DASH/HLS and fallback playback with decoded geometry/pixel aspect ratio and aspect-preserving fit. Watch height follows natural size up to 70% of content, smoothly resizing toward a 40% cap over 96dp after the first 24dp of detail scrolling; comments use the 40% cap. Shape-aware fullscreen/PiP; one live video surface across watch/mini/fullscreen, swipe minimize/restore/dismiss, visible/accessibility alternatives, fading controls, timeline, buffering/retry and accumulated double-tap seeking. Watch scroll/resize/description state follows the queue occurrence; visible video/buffering keeps the screen awake. | — for core playback; real decoding, gestures, shape/scroll transitions, orientation, PiP and layout acceptance remain unverified on a device. |
| 10 | Livestream playback | Implemented | HLS playback path and LIVE labels; playable live videos can be opened directly or from channel Streams tabs. | —; channel browsing coverage is tracked in row 05, and chat replay in row 23. |
| 11 | Quality, audio-track and speed controls | Implemented | Auto or exact supported video representations with codec/FPS labels, numeric bitrates and available file sizes; at most four menu entries per resolution/FPS group, prioritizing AV1/H.264 bitrate extremes while selection uses the full catalog; original/stable-volume/dubbed audio groups and bitrate tiers, with conservative unlabeled-track fallback; unified captions and speed 0.25×–2× controls. Shared quality defaults include Auto, Best, 4320p–144p and Worst, plus preferred codec Auto/AV1/H.264. DASH selection applies before the first segment, resolves resolution before codec, and falls back to supported streams. Auto remains explicit even with one eligible stream. In-player exact choices apply to the current video; refresh/retry and controller reconnection preserve available selections, and successors use saved defaults. Synced changes apply to the next video. | — for the web capability; reliable audio enrichment uses the additive sibling video API update. Runtime/layout acceptance remains unverified; available streams and device decoding support determine offered choices. |
| 12 | Audio-only listening and background playback | Implemented | Disable video tracks, background playback setting and service-owned playback. | — |
| 13 | Captions/subtitles | Implemented | Load VTT captions, select available language, turn subtitles on/off through the unified player gear; available-track selection and active-selection display. Three saved language priorities select the first available caption when opening a video; preferences are shared with the account or saved locally for guests. | — for core captions; appearance controls are tracked in row 48 and caption-file export in row 46. Runtime acceptance of the defaults remains unverified. |
| 14 | Remember playback position and resume | Implemented | Signed-in account positions shared with the website, local fallback, periodic saves, completion reset and explicit timestamp precedence. Guests can enable device-local resume; changing the preference updates active service-owned playback too. | — for core resume; runtime acceptance of guest resume remains unverified. |
| 15 | Video information and rich descriptions | Implemented | Selectable native rich description formatting, safe links, clickable timestamps and hashtags, with plain-text fallback; title/author/avatar, views/publication/member labels, optional likes, channel verification and subscribers, upcoming/premiere/unlisted notices. Expandable genre, license, family-friendly status, localized allowed regions and supplied music credits. Same-video timestamps seek the current occurrence; content links navigate natively or open an external handler. | — for the core capability; license display requires the additive sibling video API field. Missing optional metadata stays hidden; device/layout acceptance remains unverified. |
| 16 | Related/recommended videos | Implemented | Recommended video list on the watch screen; manual selection, channel navigation and a shared visibility preference. | —; automatic advancement is tracked separately. |
| 17 | Automatic next-video playback, playlist queue and repeat | Implemented | Service-owned next-recommendation playback; public/private playlists and dynamic mixes; source occurrence context, overlapping-page loading, previous/next and queue selection; temporary Play next/Add to queue; local queue removal and separate owned-playlist deletion; session Off/One/All repeat (All for finite queues). Explicit queues open inline before Up next, with bounded compact rows, current-occurrence following and animated collapse retained across navigation/Activity recreation. Shared next/autoplay-next/loop defaults; background/audio/PiP advancement. | — for the core capability; native runtime/layout acceptance remains unverified. Queues last for the current service session; shuffle and restart restoration are outside scope. |
| 18 | Chapters and timeline thumbnail previews | Partial | Manual chapters parsed from the already loaded description; timeline divisions and tappable current/scrub chapter title, watch entry, Comments-style watch drawer and fullscreen player sheet. Timestamp selection retains paused/playing intent; active highlighting, independent list scroll, Back and occurrence/instance restoration rules. No additional metadata or image requests. | Storyboard previews while seeking are deliberately deferred to avoid YouTube image-CDN downloads. Native runtime/layout acceptance remains unverified because the emulator crashes before boot. |
| 19 | Searchable transcripts | Not implemented | — | Transcript panel, language choice, text search and timestamp navigation. Captions alone do not provide this flow. |
| 20 | Playback diagnostics and buffer recovery | Partial | Playback errors and Retry in both embedded/fullscreen modes; Retry reloads stream details and resumes at the current position. A dedicated Refresh buffer action reloads the current source while retaining paused/playing state, speed, quality, captions and audio-only mode; live playback returns to the live edge. | Web's detailed playback statistics/copy action. |
| 21 | Legacy annotations and VR/360° viewing | Not implemented | — | Annotation overlays/toggles and specialized VR projection controls. |
| 22 | Read-only YouTube comments (Reddit excluded) | Implemented | Spoiler-free entry without preview text; drawer beneath the visible player; Top/Newest sorting; independently paginated reply threads with Back and saved scroll positions; avatars, author/date/body hierarchy, thumb-up counts, creator/verified/pinned/member/heart metadata; expandable native rich text, links, timestamps and custom emoji; loading/empty/error states and cursor-preserving retry. | — for the requested YouTube capability. Reddit is deliberately excluded; posting and liking remain unsupported. Native runtime/layout acceptance remains unverified. |
| 23 | Livestream archive chat replay | Implemented | On-demand read-only replay synchronized to service playback, seeks, speed and pause; bounded seek caches and cursor-preserving retry. Dock below watch/portrait fullscreen or beside landscape fullscreen; movable/resizable overlay with saved geometry and accessible controls. Timestamps/user IDs, font/dock size/opacity, user/word/RE2 regex filters and per-video timing offsets. Shared account timestamps/filters/timing; per-instance guest persistence and device-local appearance; miniplayer/PiP/background suspension and occurrence isolation. | Focused native replay, docking, overlay, scrolling, recreation and persistence checks passed on the connected Android 16 emulator. Account sync requires the sibling native token-scope update and renewed sign-in. RE2 does not support browser lookaround/backreferences; unsupported imported filters are reported and skipped without overwriting them. Sending live messages is outside this replay capability. |
| 24 | Sharing and opening content links | Implemented | Direct Android Share with service position, actual playlist occurrence/index, mix continuation, active end boundary and URL options. One router handles incoming/pasted/rich-text video, clip, playlist/mix, channel, handle/custom/user channel, post/community and hashtag links; public channel resolution with loading/retry/external fallback and native hashtag pagination. Watch/w/v/e/Shorts/Live/embed aliases; millisecond timestamps, absolute end bounds, pause/replay/loop in the service, and per-link playback/visibility overrides without changing saved settings. Listen, speed and proxy carry through the queue; other overrides belong to the linked occurrence. Bundled-host app links and recreation replay prevention. | — for the intended native scope. Clip permalinks preserve their origin and foreign native clips offer a browser action. Embed hosting is intentionally excluded. Older instances may lack channel-resolution/hashtag/clip APIs; see row 39 and the dated verification records. |

Evidence: [web watch page][w-watch], [web player component][w-player], [web player logic][w-player-js], [web stream controls][w-streams], [web playlist queue][w-watch-js], [web comments API][w-videos], [web comments UI][w-comments]; [Android watch UI][a-ui], [YouTube comment state][a-comments], [native comments drawer][a-comments-ui], [chat replay state][a-chat], [native chat panels][a-chat-ui], [Android player controls/settings][a-player], [player gestures][a-gestures], [player presentation][a-presentation], [scroll resizing][a-scroll], [video surface/screen wake][a-surface], [native queue panel][a-queue-ui], [Android playback setup][a-vm], [Android playback service][a-service], [Android link parsing][a-models], [activity/PiP][a-activity], [manifest][a-manifest].

## Accounts, subscriptions and library

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 25 | Account sign-in and sign-out | Implemented | Account tab with native sign-in, encrypted bearer-session storage, expiry handling and logout revocation/cleanup. Contextual sign-in returns to the interrupted screen/action. Delayed authentication and account-management responses are isolated by account and instance; incorrect current passwords retain the session. Settings is available within Account for guests and members. | —; requires the mobile login endpoint. Device acceptance remains unverified. |
| 26 | Account registration and account/session management | Implemented | Native signup with instance availability and single-use CAPTCHA; password-confirmed username/password changes and account deletion; opaque browser-session/API-token listing with creation/available expiry and This session; individual/current-session revocation; guided permissions, advanced scopes, expiry choices and one-time token display/Copy. Credential changes atomically revoke old sessions/tokens and return a replacement mobile session, preserving internal identity and library data. | — for website parity; deploy the sibling server update and renew mobile sign-in for management scopes. No additional migration. Native runtime/layout acceptance remains unverified. |
| 27 | Channel subscriptions and subscription feed | Implemented | Subscribe/unsubscribe from channel/watch pages; paginated subscription feed sharing the web account. A pinned Channels button opens a searchable directory with Relevance (default), Latest upload, Most watched and A–Z sorts, upload/watch-count details, avatars, independent refresh/retry and retained position when returning from a channel. Sorting and filtering are immediate and local; the choice is saved per instance on the device. Ranking reuses the website's 90-day habits and seven-day unwatched-upload boost. | Sorting statistics require the sibling API update; older servers or tokens without history access fall back to A–Z without replacing the saved choice. Unsubscribe from a directory entry requires opening its channel. |
| 28 | Subscription-feed filtering and sorting | Partial | The shared feed API applies account settings. A dedicated settings screen edits latest-only/unwatched-only, page size, sort and pending-notifications-only. Feed/history requests respect server page sizes; notifications-only shows no ordinary videos when notifications are empty. | Separated notification/feed presentation and Android alerts. |
| 29 | New-upload/livestream notifications | Not implemented | Pending notification videos returned by the feed API are included in the ordinary subscription list; this is not an alert implementation. | Upload notification subscription/stream handling, notification count/badge and Android alerts for new uploads/livestreams. The playback notification is a separate feature. |
| 30 | Personal Invidious playlist management | Implemented | List/create/edit/delete playlists; public/unlisted/private privacy; edit title/description; add the watched video; remove items; paginated playlist contents. | — for core management; shortcuts, external playlists, sharing and queue behavior are tracked in rows 17, 24, 31, 32 and 36. |
| 31 | Public/YouTube playlists, saved external playlists and mixes | Implemented | Typed discovery and channel Playlists tab; My playlists and Subscribed playlists with counts and source/ownership labels. Subscribe/unsubscribe YouTube, seeded mixes and others’ public/unlisted Invidious sources; current owner metadata/videos on open/refresh and cached metadata fallback. Subscribed sources stay read-only and are excluded from save/default destinations. Guest sign-in intent, duplicate prevention, retry and account/instance response isolation; unsubscribing preserves playback. | — for requested parity; requires migration 20 and renewed native sign-in. Device/runtime acceptance remains unverified. |
| 32 | Video-card/context-menu library actions | Implemented | Shared Save to playlist/create-and-save sheet from cards and watch pages, writable/default playlist choices, failed-draft and partial-creation retry; Audio mode, Play next/Add to queue, channel navigation, Block/Unblock with scoped Undo; history/owned-playlist removal and success/error feedback. | Watch on YouTube and Switch Invidious instance actions are intentionally excluded from Android. Native runtime/layout acceptance remains unverified. |
| 33 | Watch history and history organization | Implemented | Record watched videos; full-history title/channel search before pagination; account-timezone Today/Yesterday/Last 7 days/Last 30 days/Older groups; saved title/channel/duration/release/latest-watch metadata and unknown-value fallbacks; unavailable entries, single removal, clear confirmation and shared enable/disable setting. Basic viewing remains on older servers with a search update explanation. | — for website parity; organized history requires the sibling API update, using existing scopes. Archived-date expansion and date-range filters are outside the requested scope. Native runtime/layout acceptance remains unverified. |
| 34 | Automatic watched/progress indicators | Implemented | Shared watched badges/thumbnail overlays and saved-progress bars across native video cards, including recommendations; compact and thumbnail-free presentation; accessible history/progress descriptions. Account state uses the shared playback API; guests see device-local progress when resume is enabled. Service-owned playback updates indicators in background/audio/PiP. Existing history removal preserves progress; clearing history clears both. | —; manual watched-state actions are listed under [Intentionally excluded parity](#intentionally-excluded-parity). |
| 35 | Data import/export and migration | Partial | Complete subscription OPML export with Invidious or YouTube feed URLs; owned private playlist Atom snapshots via document picker or file sharing. | Full account JSON export/Invidious import; YouTube subscription, playlist CSV and watch-history JSON imports; NewPipe subscription JSON and FreeTube subscription DB imports. The native token has only the dedicated subscription-export permission for account export. |
| 36 | Shared account preferences | Partial | Dedicated Settings and submenu screens; shared history/resume, playback defaults (autoplay, next recommendation, autoplay next, single-video loop, audio only, proxy, speed, preferred DASH codec, ranked quality, caption priorities), color mode/density/thumbnails, homepage/feed order, region, members-only and comments/recommendations/description visibility, feed filters/sort/page size, default playlist, DeArrow, SponsorBlock and chat timestamps/user/word filters (with per-video chat timing through its dedicated API). Sparse patches preserve unrelated server values; refresh on sign-in, settings opening and foreground return. History consumes the account timezone supplied by the server. | Native history timezone selection/detection and shared preload control; interface locale, annotations and VR. Chat appearance stays device-local, matching the website's persistence split. Browser-specific search privacy, non-DASH quality choices and nickname display have no native equivalents. Background/PiP stay device-local. The expanded shared settings require the sibling settings API update. |

Evidence: [web account][w-account], [web authenticated APIs][w-auth], [web feed rules][w-users], [web playlist routes][w-playlists], [web common playlist/mix API][w-playlist-api], [web history][w-history], [web watched indicators][w-indicator], [web data control][w-data], [web preferences][w-prefs]; [Android account/library UI][a-ui], [subscribed channel directory][a-subscriptions-ui], [native settings][a-settings], [preference model][a-prefs], [Android API][a-api], [Android watched/progress state][a-watched], [Android session/local settings][a-store], [mobile API contract][w-mobile].

## Enhancements, appearance and instances

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 37 | SponsorBlock | Implemented | Instance-proxied segments; all eight web categories and four modes; colored timeline ranges and scrub labels; manual Skip/Dismiss with remaining time; service-owned auto skipping in background/audio/PiP; overlapping-range merging and manual replay after an auto skip; custom colors; shared account settings and inherited per-channel overrides with a native manager. Guests save global settings and per-channel overrides per instance. | — for the web capability; shared settings require the SponsorBlock preference API update. Native runtime/layout acceptance is unverified because the emulator crashes before boot; see VERIFICATION.md. |
| 38 | DeArrow titles and contributions | Implemented | Optional replacement titles throughout video lists, watch/mini-player and system playback metadata; accessible original-title toggle; native suggestion/voting sheet with all four guideline acknowledgements, locked/original vote restrictions, refresh and preserved failed drafts; shared account settings and encrypted contribution identity, including private-ID import. Guests keep per-instance local title settings. | — for the web capability; requires the native DeArrow API server update and an updated sign-in token. This fork does not implement thumbnail replacement. |
| 39 | Clips | Implemented | Native/YouTube clip resolution with validated millisecond bounds; My Clips in Library and public channel Clips pages with pagination, retained scroll and owner deletion. Public 5–120-second creation with Unicode titles, tenths trimming, storyboard filmstrip/fallback, separate preview and retained drafts. Clip-relative service/system timelines, loop/end stopping, share/copy, origin notice and same-scene full-video transition; source history/progress writes, chapters, chat replay and SponsorBlock skipping suppressed in clip mode. | Production deployment and upstream YouTube availability are unverified. Native sign-in tokens need renewed clip scopes. Active-live clipping, published editing, remix, embeds, federation and import/export are outside scope. |
| 40 | Channel blocking | Implemented | Shared account block list or independent device-local guest blocks; card and channel Block/Unblock actions; Browsing settings manager with channel navigation, errors and retry; immediate discovery/search/recommendation filtering and saved search inclusion override. Confirmed snapshots support offline filtering and isolate accounts/instances. Subscriptions, history, playlists and direct access remain available. The watch action row omits the blocking overflow menu. | — for the web capability; requires the sibling blocking API deployment and renewed sign-in tokens. Native runtime/layout acceptance remains unverified. |
| 41 | Members-only content visibility controls | Implemented | Shared show-members preference, per-instance guest preference, device-local search override/reset, explicit member metadata and Members only labels. Filters discovery, search, channels, subscriptions, playlists and recommendations; preserves history and direct access. Original pages and continuation state keep hidden pages navigable. | — for visibility controls; requires additive member metadata and preference PATCH support on the server. Missing metadata remains visible. This does not grant membership access. Native runtime/layout acceptance remains unverified. |
| 42 | Native appearance and layout preferences | Implemented | Shared light/dark/system color mode, compact video-list density and thumbnail-free thin mode; dedicated Appearance settings screen. | —; multiple web themes, random theme selection and alternative player skins are listed under [Intentionally excluded parity](#intentionally-excluded-parity). |
| 43 | Interface localization | Not implemented | — | Translated app interface and language selector. Android's user-facing strings are hardcoded in English; caption/audio language selection is a separate capability. |
| 44 | Instance selection/switching and proxy preference | Partial | Dedicated server screen for an HTTPS instance; fresh installs default to `https://invidious.wingress.net`; clear local session/cache/player when switching. A shared proxy preference is used by video and manifest requests. | Instance discovery/automatic redirection and per-content switch-instance flow. Switching servers does not migrate account data. |
| 45 | RSS subscriptions | Implemented | Open/Copy/Share selected-instance channel, public/unlisted playlist, seeded mix and secret subscription-feed links. Subscription access explanation and memory-only link; authenticated Open/Save/Share private-owned Atom files through a restricted FileProvider; complete OPML in both web formats, shared server serializers and empty/error/retry/account-change handling. | — for website feed/export parity; no built-in RSS reader, polling or upload alerts. Device/export-app acceptance remains unverified. |

Evidence: [web preferences][w-prefs], [web player/SponsorBlock integration][w-player], [web SponsorBlock behavior][w-sponsor-js], [web SponsorBlock data/settings][w-sponsor], [DeArrow implementation][w-dearrow], [DeArrow contribution contract][w-dearrow-doc], [native clip contract][w-clips], [web blocking][w-blocked], [web routing/RSS][w-routing]; [Android content visibility][a-visibility], [Android blocking controls][a-visibility-ui], [Android settings/appearance][a-ui], [Android SponsorBlock sheets][a-sponsor-ui], [Android SponsorBlock settings/data][a-sponsor], [Android SponsorBlock skip engine][a-sponsor-engine], [Android DeArrow UI][a-dearrow-ui], [Android title resolver][a-dearrow], [Android API][a-api], [Android models][a-models], [Android instance/session switching][a-vm].

## Additional player tools

These web capabilities were missing from the original 45-area checklist. They have separate IDs so the existing feature references remain stable.

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 46 | Download video, audio and caption files | Not implemented | — | The web Download selector exports available combined, video-only, audio-only and VTT caption formats, subject to instance restrictions; Companion handles downloads when configured. Android has no media/caption file download flow. Cached JSON, streaming buffers and RSS/OPML exports do not implement this capability. |
| 47 | Player keyboard shortcuts and fine seeking | Partial | Focusable native controls; Enter/Space restores the mini-player; Enter activates an available SponsorBlock skip; MediaSession transport controls, timeline and ±10-second seeking. | The web's player shortcut map for play/pause, seek, mute/volume, speed, captions and fullscreen; numeric percentage jumps and paused frame stepping. Android system volume/media controls do not reproduce the full web map. |
| 48 | Caption appearance controls | Not implemented | Captions render through the Media3 PlayerView (row 13). | In-app caption size and text/window opacity controls with remembered styling, as exposed by the web player. Android has no corresponding caption appearance editor. |

Evidence: [web download widget][w-download], [web download routing][w-watch-route], [web shortcuts/caption styling][w-player-js]; [native player controls][a-player], [native video/caption surface][a-surface], [playback service][a-service], [Android API][a-api].

## Integration details affecting the checklist

1. **Search sorting now uses the fork’s contract:** [Android API][a-api] sends `sort=relevance` or `sort=views`, matching [the search parser][w-search]. The native menu exposes these supported choices; the former `view_count` value maps to `views`, while unsupported legacy values fall back to relevance. Date and duration retain their correctly named parameters. Wire behavior is covered by API tests; live result-order testing is not claimed.
2. **External playlists and mixes have native flows:** [Android API][a-api] calls public playlist endpoints and `/api/v1/mixes/:id` directly, retaining mix seeds/continuations without following redirects. Typed search, channel playlists and shared/pasted links reach these sources. The native bookmark API supports subscribe/unsubscribe, and ownership metadata gates editing/deletion and save/default destinations. Migration 20 and renewed native sign-in are required for account bookmarks/RSS; public playlist/mix playback works for guests. See rows 17, 24, 31 and 45.
3. **Feed settings now have native controls:** [shared server feed rules][w-users] are applied by the authenticated feed endpoint. Android no longer overrides account page size with 30. Pending-notifications-only filters the returned notifications even when the web endpoint falls back to ordinary feed videos; latest-only and notifications-only lists do not expose redundant pagination. Other feeds still merge notifications and ordinary items.
4. **Shared settings require the expanded PATCH API:** [the mobile PATCH endpoint][w-mobile] now validates supported native booleans, playback defaults, captions, appearance, browsing and feed values in addition to SponsorBlock. It merges changed fields under the account lock and preserves unknown preferences. The existing preference token scopes suffice; no migration or token renewal is added. Guests save native preferences per instance. Background playback and PiP remain device settings, isolated by account or guest and instance. Unsupported web capabilities have no native switches.
5. **Channel first pages omit empty continuation values:** [Android API][a-api] omits the parameter for empty/blank tokens and preserves opaque follow-up tokens for Videos, Streams and Playlists. Earlier recorded public-instance checks found a YouTube 400 error with `continuation=`; this audit does not rerun them. [Channel metadata][a-models] selects supported tabs in Videos/Streams/Playlists order, preserves the selection on Refresh/Retry when available, and falls back to Videos if no supported tab is advertised. A channel advertising Streams, Podcasts and Posts selects Streams. These public browsing endpoints require no native account extension.
6. **The current trending baseline has two categories:** [web trending extraction][w-trending] supports Gaming and Livestreams, with Livestreams as the default. Android sends a region but no category parameter. Music/Movies should not be listed as current web parity gaps.
7. **History timezone consumption is separate from editing:** [Android history parsing][a-history] uses server-provided calendar dates/timezone for grouping. [Web preferences][w-prefs] expose timezone selection and detection; [native settings][a-settings] do not. This gap is included in row 36 rather than claiming that history groups use the device timezone.

## Platform-specific context

The web checkout also supplies embeddable players, developer JSON APIs, browser OpenSearch integration, server-side HTML with many no-JavaScript fallbacks, and self-hosting/administration controls. These are web/server capabilities rather than native screen parity items. Android consumes the APIs and inherits server extraction/proxy behavior; it does not need to duplicate the server.

Android adds a live-video mini-player with swipe minimize/restore/dismiss, scroll-driven watch-player resizing, animated description/queue disclosures, screen-awake handling, MediaSession/system controls, playback notification, picture-in-picture with seek/play actions, background service playback, encrypted session storage and short-lived cached discovery/search/subscription results. The response cache is not video downloading or offline media playback. Web media/caption downloads are a parity gap (row 46); a managed offline-media library and casting were not found as implemented web features in this checkout and are not counted.

The original checklist was a documentation-only source audit. The subsequent Android player overhaul consolidates playback settings under one in-player gear, removes the persistent fullscreen/back overlays, and adds touch seeking and Refresh buffer. Row 11 also corrects an audit omission: the old Media3 gear already exposed 0.25× speed; the new unified sheet retains it. Rows 09, 11, 13 and 20 reflect the overhaul; their broad statuses and summary counts are unchanged. Detailed statistics remain missing. Runtime validation for this revision is recorded separately in [VERIFICATION.md](VERIFICATION.md).

The DeArrow implementation covers row 38 and the DeArrow portion of row 36. Titles retain their canonical original metadata, contributions use the web account's encrypted identity, and write requests are never automatically retried. The server API update needs deployment; existing native tokens must be renewed by signing in again. Validation and production limitations are recorded in [VERIFICATION.md](VERIFICATION.md).

The SponsorBlock revision implements row 37 and the SponsorBlock portion of row 36. It follows the local web fork's opt-in defaults, modes, colors, inheritance, dismissal and once-per-session automatic skipping. Active livestreams are excluded. Segment failures leave playback usable. Segment submission/voting is outside the web baseline. Server checks and Android build/unit validation passed; connected tests and native screenshots remain unverified. The existing production rollout remains separate.

## Source references

Links are repository-relative so this checklist works with the documented sibling-checkout layout. The listed commits identify the reviewed snapshot.

[a-ui]: android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt
[a-dearrow-ui]: android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt
[a-dearrow]: android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt
[a-player]: android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt
[a-chapters]: android/app/src/main/java/net/wingress/mobivious/data/Chapters.kt
[a-chapters-ui]: android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt
[a-channel-description]: android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt
[a-gestures]: android/app/src/main/java/net/wingress/mobivious/ui/PlayerGestures.kt
[a-presentation]: android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt
[a-scroll]: android/app/src/main/java/net/wingress/mobivious/player/WatchPlayerScroll.kt
[a-surface]: android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt
[a-queue-ui]: android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt
[a-settings]: android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt
[a-subscriptions-ui]: android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt
[a-prefs]: android/app/src/main/java/net/wingress/mobivious/data/AppPreferences.kt
[a-vm]: android/app/src/main/java/net/wingress/mobivious/ui/AppViewModel.kt
[a-api]: android/app/src/main/java/net/wingress/mobivious/data/InvidiousApi.kt
[a-models]: android/app/src/main/java/net/wingress/mobivious/data/Models.kt
[a-store]: android/app/src/main/java/net/wingress/mobivious/data/SessionStore.kt
[a-watched]: android/app/src/main/java/net/wingress/mobivious/data/WatchedState.kt
[a-service]: android/app/src/main/java/net/wingress/mobivious/player/PlaybackService.kt
[a-activity]: android/app/src/main/java/net/wingress/mobivious/MainActivity.kt
[a-manifest]: android/app/src/main/AndroidManifest.xml
[w-routing]: ../invidious/src/invidious/routing.cr
[w-search]: ../invidious/src/invidious/search/filters.cr
[w-search-ui]: ../invidious/src/invidious/frontend/search_filters.cr
[w-trending]: ../invidious/src/invidious/trending.cr
[w-search-api]: ../invidious/src/invidious/routes/api/v1/search.cr
[w-channels]: ../invidious/src/invidious/routes/channels.cr
[w-channel-ui]: ../invidious/src/invidious/views/components/channel_info.ecr
[w-watch]: ../invidious/src/invidious/views/watch.ecr
[w-watch-route]: ../invidious/src/invidious/routes/watch.cr
[w-download]: ../invidious/src/invidious/frontend/watch_page.cr
[w-player]: ../invidious/src/invidious/views/components/player.ecr
[w-player-js]: ../invidious/assets/js/player.js
[w-streams]: ../invidious/assets/js/player-stream-menu.js
[w-watch-js]: ../invidious/assets/js/watch.js
[w-videos]: ../invidious/src/invidious/routes/api/v1/videos.cr
[w-comments]: ../invidious/assets/js/comments.js
[w-account]: ../invidious/src/invidious/views/user/account.ecr
[w-auth]: ../invidious/src/invidious/routes/api/v1/authenticated.cr
[w-users]: ../invidious/src/invidious/users.cr
[w-playlists]: ../invidious/src/invidious/routes/playlists.cr
[w-playlist-api]: ../invidious/src/invidious/routes/api/v1/misc.cr
[w-history]: ../invidious/src/invidious/views/feeds/history.ecr
[w-indicator]: ../invidious/assets/js/watched_indicator.js
[w-data]: ../invidious/src/invidious/views/user/data_control.ecr
[w-prefs]: ../invidious/src/invidious/views/user/preferences.ecr
[w-mobile]: ../invidious/docs/mobile-api.md
[w-dearrow]: ../invidious/src/invidious/dearrow.cr
[w-dearrow-doc]: ../invidious/docs/dearrow-contributions.md
[w-clips]: ../invidious/docs/native-clips.md
[w-blocked]: ../invidious/src/invidious/frontend/blocked_channels.cr
[w-themes]: ../invidious/src/invidious/themes.cr

[a-sponsor-ui]: android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt
[a-sponsor]: android/app/src/main/java/net/wingress/mobivious/data/SponsorBlock.kt
[a-sponsor-engine]: android/app/src/main/java/net/wingress/mobivious/player/SponsorBlockEngine.kt
[w-sponsor-js]: ../invidious/assets/js/sponsorblock.js
[w-sponsor]: ../invidious/src/invidious/sponsorblock.cr


## Revision history

The dated notes below describe checks and rollout status at each implementation stage. Older test totals, installed APK versions and statements about deployment/publication apply to that stage; the reviewed snapshots and current feature totals are at the top of this file. Current release and acceptance evidence is in [VERIFICATION.md](VERIFICATION.md).

### Settings overhaul — 3 October 2026

Rows 03, 11, 13, 14, 16, 28, 36, 42 and 44 were updated against the current source. This adds two implemented areas (captions and guest resume) and moves homepage/navigation to partial. The 37 Android unit/API tests, debug APK builds, lint, focused server specs and disposable account/API harness passed. New Compose settings scenarios compile; they have not run because the installed emulator exits with SIGSEGV before boot. See `VERIFICATION.md` for exact validation and deployment requirements.

### Channel loading and Streams tabs — 3 October 2026

Rows 05 and 10 now include native Streams browsing and automatic selection for streams-only channels. Row 05 remains Partial and the summary counts are unchanged. All 58 Android unit/API tests, debug/instrumentation APK builds, lint and disposable channel fixture checks passed. Public live API checks returned 15 WAN Show streams and two 60-video pages of regular uploads. Four new Compose channel scenarios compile but have not run because no device is connected; native runtime/layout acceptance remains pending. See `VERIFICATION.md` for the checks and repeat commands.

### Watched/progress indicators — 4 October 2026

Row 34 now includes automatic watched indicators and saved-progress bars throughout the shared native video cards, with guest progress, account/instance isolation, background service updates and existing history-control synchronization. The supported capability is Implemented; manual mark-watched/mark-unwatched actions are recorded under [Intentionally excluded parity](#intentionally-excluded-parity). No new server API, token scope or database migration is needed.

All 78 Android unit/API tests, debug/instrumentation APK builds, lint and disposable watched/progress fixture HTTP checks passed. Six new Compose scenarios compile but have not run: the installed emulator exited with SIGSEGV before Android booted. Runtime, layout and screenshot acceptance remain pending; see `VERIFICATION.md`.

### Members-only visibility and channel blocking — 4 October 2026

Rows 40–41 now implement the web visibility capabilities. Signed-in blocking shares the existing website block table; guest blocking is saved locally for that instance. Android keeps public responses unpersonalized and filters the original loaded lists locally, retaining pagination and playlist occurrence indexes. Block changes and preference changes update lists/recommendations without stopping playback. Search visibility overrides are device-local and isolated by instance/account or guest; they do not overwrite global preferences. History and directly opened videos remain accessible.

The server adds boolean `isMember` to search/channel/feed/video/recommendation metadata, permits `show_member_videos` sparse preference patches, and exposes scoped block-list read/block/unblock endpoints. No new migration is introduced. Blocking needs the updated server and renewed native tokens; membership preferences need no new scope. Old-server/token errors explain the required update.

All 93 Android unit/API tests, debug/instrumentation APK builds and lint passed, alongside focused Crystal specs, normal/API-only executable builds, the guarded disposable account/API harness and visibility fixture HTTP checks. Six new Compose scenarios compile but remain unexecuted: the emulator exited with SIGSEGV before Android booted. Native layout, screenshots and runtime acceptance remain pending; see `VERIFICATION.md`.

[a-visibility]: android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt
[a-visibility-ui]: android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt

### Scoped search and organized history — 4 October 2026

Rows 06 and 33 now implement the requested website capabilities. Search fields on
channel, subscription and history screens keep submitted queries separate from
drafts; clear restores the underlying tab/feed. Shared icon, keyboard Search and
physical Enter submission also fixes the main Search screen, retaining pasted-link
timestamps. Pagination uses original search pages, and account/instance changes
discard stale scoped responses. Channel search follows channel visibility;
subscription search uses the saved search visibility overrides.

Organized history shares website matching and ordering before pagination, groups
saved calendar dates against the account-timezone server date, and presents saved
title/channel/duration/release/latest-watch metadata. Unknown and future dates
remain in Older; missing videos remain accessible. Older servers retain basic
history viewing and explain the organized-history update. Archived-date expansion
and date-range filters were explicitly excluded from the requested scope.

All 105 Android unit/API tests, debug/instrumentation APK builds and lint passed.
Five standard Crystal history examples and 51 search/preference examples passed,
along with normal/API-only server builds, the guarded disposable PostgreSQL
account/API harness and local fixture HTTP checks. Nine new Compose scenarios
compile but have not executed: the installed emulator crashed with SIGSEGV before
Android booted. Native interactions, layout and screenshots remain unverified;
see `VERIFICATION.md`.

Deploy the sibling subscription-search and organized-history API update. New
native sign-ins include exact `GET:subscriptions/search` permission, so existing
sessions need sign-out/sign-in for subscription search. History uses existing
permissions. No migration, secret, production deployment or release publication
was introduced. Implementation: [history metadata and grouping][a-history],
[shared search submission][a-search-ui], [Android view model][a-vm],
[mobile API contract][w-mobile].

[a-history]: android/app/src/main/java/net/wingress/mobivious/data/History.kt
[a-search-ui]: android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt

### Video library actions and playback queues — 4 October 2026

Rows 17 and 32 now implement the requested native capability. Cards share library,
audio and queue actions across browsing and recommendations. Save preserves the
selected video through sign-in, supports inline creation, and retries the add step
without recreating an already-created playlist. Channel blocks offer scoped Undo.
Watch on YouTube and Switch Invidious instance are deliberately omitted; Settings →
Server remains available.

The playback service resolves streams and owns queue transitions, track setup and
errors. Playlist occurrences retain positional indexes and stable removal IDs;
overlapping pages and duplicate videos remain distinct. Repeat All loads missing
pages rather than jumping over them. Queue removal is local; deleting an owned
playlist item changes the server and keeps a removed playing item alive until it
finishes. Public playlist and mix links work for guests. Dynamic mixes support Off
and One, and finite queues additionally support All. Queue state survives activity
navigation/background/PiP but is not persisted after service termination.

Shared `continue`, `continue_autoplay` and `video_loop` defaults use the existing
preference API/scopes and storage. Deploy the sibling native PATCH allowlist update;
no migration or renewed token is needed. Runtime repeat selection stays session-local.
External-playlist save/unsave was outside the initial queue revision and was
subsequently implemented by playlist subscriptions (row 31). Shuffle and restart
restoration remain outside scope.
See VERIFICATION.md for checks and the emulator limitation.

Explicit queues now appear expanded inline beneath watch metadata and before
Up next. The collapsible header replaces both the watch action chip and player
queue icon. Standalone and implicit recommendation playback have no queue panel.
A bounded list of compact rows follows the current occurrence and marks it with
a tinted background, play marker and accessible selected state. Collapse survives
item changes, navigation and Activity recreation. Loading and retry remain inline
when video details are absent; existing paging, repeat and occurrence actions are
retained. This presentation update adds no server API or storage changes.

### Playlist subscriptions and RSS — 4 October 2026

Rows 31 and 45 are implemented in source. The Android Library and English web
headings now use My playlists (count) and Subscribed playlists (count). Source
references follow owner updates; only owned playlists expose mutations or saving
destinations. Mix seeds survive discovery, links, subscriptions and playback. Evidence: [playlist UI](android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt), [RSS UI](android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt), [server serializers](../invidious/src/invidious/rss.cr), [native API](../invidious/src/invidious/routes/api/v1/native_library.cr).

Migration 20 adds per-account bookmarks and cached metadata, preserving legacy
external-save rows during backfill. Unsubscribe removes only the caller’s bookmark
and any caller-owned legacy external row. Deploy the server migration/API update
and sign out/in to renew native permissions. The installed 0.2.1 APK does not gain
these changes until a new app build is installed. Validation and outstanding device
checks are recorded in VERIFICATION.md; no deployment or release was performed.


### Read-only YouTube comments — 4 October 2026

Row 22 now covers the requested YouTube-only capability. Reddit is intentionally
excluded from the native scope. A visible Comments card contains no author or
comment preview, and requests start only after opening. The drawer occupies the
area below the player; Top/Newest sorting, reply threads, Back navigation and
cached scroll positions preserve reading context without changing playback.

Native rows separate author/metadata, comment body and informational likes with
avatars, typography, spacing and dividers. Available creator, verification,
pinned, membership and creator-heart metadata is retained. Rich text supports
links, line breaks, formatting and inline custom emoji with accessible fallback.
Current-video timestamp links seek through the existing player; channel/video/
playlist links use existing navigation, and other HTTP(S) links open externally.

The existing public YouTube comments endpoint supplies both comments and replies.
No server patch, migration or renewed token is needed. Stale video/account/instance/
sort responses are rejected, overlapping pages are deduplicated and failed pages
retain their cursor and loaded rows. Comments remain session-local. Saved comment
visibility is respected; posting and liking are outside scope.

All 146 Android unit/API tests, debug/instrumentation APK builds, lint and
localhost comments fixture checks passed. Nine new Compose scenarios compile but
have not run: the installed Pixel emulator again exited with SIGSEGV (139) before
boot, and no device was connected. Native interactions, layout and screenshots
remain unverified; see `VERIFICATION.md`.

[a-comments]: android/app/src/main/java/net/wingress/mobivious/data/Comments.kt
[a-comments-ui]: android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt

### Channel avatar visibility — 4 October 2026

Android now displays circular avatars in channel/watch headers, subscription chips,
comments/replies and creator hearts, shared video cards (including recommendations,
history, playlists and queues), and YouTube playlist creator rows. Video titles
retain their full width; thin mode omits avatars and a channel's own upload/stream
lists suppress repeated owner images. The same author action handles image/name
navigation. Missing/loading/failed images retain an initial or person icon matching
the native color mode.

Existing JSON responses gain optional `authorThumbnails` from supplied metadata or
the existing migration 21 cache, with batched page/nested-entry reads and no extra
YouTube metadata requests. Images use the selected instance's `/ggpht` proxy with
query preservation, a shared native cache, and no credentials or redirects. No
additional migration, endpoint or token scope is introduced. Install a new app
build and deploy the additive server API update for full cached-list coverage.
Row 05 remains Partial for the remaining channel-page capabilities.

Validation and outstanding native device/screenshot checks are recorded in
VERIFICATION.md. No installation, deployment or release publication was performed.


### Codec-aware quality parity — 4 October 2026

Rows 11 and 36 now follow sibling commit `4ac0170`. Video choices use codec names
and numeric bitrates, retain at most four variants per rounded resolution/FPS
group, and keep a complete catalog for defaults, restoration and Auto adaptation.
A service-owned selector enforces the captured DASH policy before the first video
segment. Manual/Auto state survives refresh, retry and activity reconnection;
manual choices end at a new queue occurrence. Codec preference is shared with the
web account through `video_codec`; guests save it per instance. Deploy the sibling
native PATCH allowlist extension before distributing the new app; no migration
or new token scopes are required.

All 165 Android unit/API tests, debug/instrumentation builds and lint passed.
Focused server specs, normal/API-only builds and the guarded disposable account
harness passed, including codec writes in both directions, account isolation and
unrelated-setting preservation. Real H.264/AV1 fixtures decode and their HTTP
variants/request logs passed checks. Four new device scenarios compile but have
not run: the installed emulator exited with SIGSEGV before boot. See VERIFICATION.md.

### Native account parity and navigation — 4 October 2026

The native account revision implements row 26 and updates rows 03, 09 and 25. Account replaces Search in the fixed bottom navigation; global search expands in the top-right and submits to the existing results screen. Settings is inside Account. The sibling server adds native registration and dedicated password-confirmed account APIs using existing account/session tables. Production requires that server update and renewed mobile sign-in; deployment and release publishing are separate. Android unit/build/lint, server builds/specs and the disposable account harness are recorded in [VERIFICATION.md](VERIFICATION.md); device acceptance remains unverified.


### Player gestures, watch resizing and complete channel descriptions — 4 October 2026

Rows 05 and 09 cover Android commits `cab1eda` and `a6848ee` in configured release
0.5.1 (version code 8). Watch/fullscreen downward
swipes, mini-player upward/sideways swipes and button/accessibility alternatives
share one live video surface. Watch details smoothly resize the fitted player
between the 70% and 40% caps after the 24dp top region, using 96dp of scroll
travel. Comments retain the 40% cap; returning near the top expands the player.
Scroll/resize/description state follows the queue occurrence, and description/queue
disclosures respect the system animation-duration setting.

Channel descriptions retain their preview and expose complete selectable text in
a scrollable sheet. Row 05 stays Partial because rich links/formatting, additional
channel tabs, video sorting and header metadata are still missing. These changes
require no new server API, migration or token scope. Previously recorded checks
include 197 passing Android unit/API tests, debug/instrumentation builds and lint,
plus signed 0.5.1 release validation. Device decoding, gestures, layout and
screenshots remain unverified; see [VERIFICATION.md](VERIFICATION.md).

### Checklist coverage audit — 4 October 2026

Rechecked the current sibling web routes, watch/download UI, player controls,
search filters, channel headers, preferences and data controls against native
models/API calls and screens. Added rows 46–48 for media/caption downloads, player
keyboard shortcuts/fine seeking and caption appearance. At that audit stage, the
previous 45 row statuses were unchanged; three additional areas brought the total
to 48. The subsequent native scope decision below updates rows 34 and 42.

Corrected release/snapshot metadata, the obsolete external-playlist/mix integration
note, trending categories, native channel-description coverage, the subscribed
channel directory and player presentation. Expanded the existing gaps for advanced
search filters, video metadata, content-link variants, playlist-aware watch sharing,
import formats and history timezone/preload settings. Search suggestions are
API-only in this web checkout,
and playlist reordering is a server TODO; neither is counted as an implemented web
feature. This documentation update adds no app/server behavior or runtime checks.


### Native scope exclusions — 4 October 2026

Rows 34 and 42 are Implemented for the intended Android scope. Explicit manual
watched-state actions, multiple visual themes, scheduled random theme changes and
alternative player skins are unwanted native features rather than parity gaps.
Row 36 no longer lists theme/player-skin preferences as missing. Light/dark/system
color mode and native layout preferences remain supported. The checklist now has
25 implemented, 12 partial and 11 not implemented areas. Device acceptance remains
a separate verification status, as recorded above and in [VERIFICATION.md](VERIFICATION.md).

### Channel tabs and community posts — 5 October 2026

Row 05 now includes every available non-Clip channel tab, shared video sorting,
independent playlist sorting, related-channel navigation, supplied header metadata
and selectable rich channel descriptions. Tab changes and sorting restart paging;
returning from search or child content retains the loaded channel and scroll
position. Row 05 remains Partial solely for deferred Clips.

Row 07 is Implemented in source. Channel posts open on a dedicated native page
with images/galleries, video/playlist attachments, read-only polls/quizzes and
unavailable-attachment fallback. A separate post-comment controller drives a
Top/Newest sheet with replies, retained reading positions and cursor-preserving
retry. Public requests use the resolved post channel ID and reject superseded
navigation, sort, account and instance responses. Video comments and playback
remain independent. Row 24 now includes native shared/pasted/rich-text post links,
legacy community links carrying `lb`, Copy link/Share and bundled `/post` app links.
Activity recreation does not replay an already-consumed launch intent.

All 213 unit/API tests, debug/instrumentation APK builds, debug lint and localhost
community/avatar fixture checks passed. Ten new Compose scenarios compile;
device interaction, playback continuity, layout and screenshots remain unverified
because the emulator exited with SIGSEGV (139) before boot. The totals are now
26 implemented, 12 partial and 10 not implemented. Existing sibling public APIs
supply the contracts; this change adds no server migration or token scope.
See [VERIFICATION.md](VERIFICATION.md) for commands and acceptance limits.

[a-community]: android/app/src/main/java/net/wingress/mobivious/data/Community.kt
[a-community-ui]: android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt
[w-community]: ../invidious/src/invidious/channels/community.cr

### Rich video information and content links — 5 October 2026

Rows 15 and 24 are Implemented for the accepted non-clip native scope. Watch
uses the existing native HTML subset with selection and plain-text fallback;
timestamps seek without replacing the queue occurrence. Supplied likes,
verification, subscribers, premiere/unlisted information, genre, license,
family-friendly status, regions and music credits appear without inferring
unknown metadata. The sibling public video serializer adds `license`; older
servers omit that optional row. No migration or token scope is added.

Shared, pasted and rich-text links use one origin-validated router. Channel
handles/custom/user URLs resolve through the selected instance, with cancel,
retry and external fallback. Hashtags use public paginated native video results.
Relative links and watch aliases retain timestamp precision and normalized
playlist indexes. Direct Share uses the current service position and matched
source occurrence; inserted videos and unresolved seeds omit playlist context.

End bounds remain in absolute video coordinates. The service clamps seeks,
pauses at the end, replays from the linked start and loops when requested,
including background/system controls. Link overrides do not write preferences.
Listen, speed and proxy carry to successors; bounds and other overrides remain
on the linked occurrence and survive refresh/retry. Bare source links retain
browse-first behavior and apply options when Play is chosen. Clip functionality
stays deferred in row 39; parsing embed URLs does not introduce embed hosting.

All 224 unit/API tests, debug/instrumentation APK builds and debug lint passed.
The localhost content-link fixture, normal Invidious build, three video extraction
specs and production serializer/template fixtures passed. Seven link smoke and
three presentation scenarios compile; actual intents, chooser behavior, bounds,
background/recreation playback, layout and screenshots remain unverified because
the emulator exited with SIGSEGV (139) before boot. The totals are 28 implemented,
10 partial and 10 not implemented. See [VERIFICATION.md](VERIFICATION.md).

### Chapters — 5 October 2026

Row 18 is Partial: [manual description chapters][a-chapters] and [native chapter
navigation][a-chapters-ui] are implemented. The parser matches the web fork's
timestamp rules rather than YouTube's stricter creator publishing requirements;
automatic chapters and live videos are excluded. The current chapter title opens
a list beneath the player using the Comments drawer's sizing and styling; fullscreen
uses the existing player bottom-sheet pattern. Selection seeks without changing
playing intent or closing the list. Timeline divisions and chapter/SponsorBlock
scrub labels share the existing controls. List state survives rotation and
watch/fullscreen transitions and resets on new queue occurrences or instances;
minimization, PiP and Back close the list appropriately.

Thumbnail previews mean scene images while hovering or seeking. The web player
supports storyboard previews, and deployed static preview assets matched this
checkout during investigation. A relative-image-URL resolution bug was reproduced
locally; that does not establish the cause of the reported blank rectangle.
Storyboard metadata comes from normal video loading, but preview images require
separate image-CDN downloads through Invidious. Previews and web repairs remain
deferred under the user's request limit; no server code, endpoint, scope or
migration changed for chapters.

All 230 Android unit/API tests, debug/instrumentation APK builds, lint and local
chapter/content-link fixture checks passed. Four chapter presentation and seven
service/playback scenarios compile but remain unexecuted: the emulator exited
with SIGSEGV (139) before boot. Native layout, interaction and request-count
assertions still require device acceptance. Totals are 28 implemented, 11 partial
and 9 not implemented. See [VERIFICATION.md](VERIFICATION.md).

## Intentionally excluded parity

These web capabilities are deliberately omitted from the Android product scope.
They are retained here for reference and are not missing implementation work or
reasons to classify the related native feature as Partial.

| Related rows | Excluded web capability | Android scope |
| --- | --- | --- |
| Row 24 | Hosting/exporting an embeddable player | Open embed video URLs in native playback; do not provide an embed-hosting flow. Native clip playback is covered by row 39. |
| Row 34 | Explicit mark-watched/mark-unwatched actions | Use automatic watched indicators and saved progress; retain the existing history-removal and clear-history actions. No separate manual watched-state controls are planned. |
| Rows 36, 42 | Multiple visual themes (Modern Neon/Diary), scheduled random theme selection and alternative player skins | Use the native appearance with light/dark/system color mode, list density and thumbnail visibility. No visual-theme catalog, automatic theme rotation or player-skin selector is planned. |

Evidence: [web watched-state actions][w-watch-route], [web appearance/player preferences][w-prefs], [web theme registry][w-themes]; [native watched/progress state][a-watched], [native appearance settings][a-settings].

### Livestream archive chat replay — 5 October 2026

Row 23 is Implemented. Archived videos explicitly advertising `liveChatReplay`
open read-only chat on demand. Service positions drive message visibility,
including seeks, repeat, SponsorBlock jumps, pause, speed and signed timing
offsets. Paging preserves IDs, replacements/removals and failed cursors, stops
repeated-token loops, prefetches 30 seconds and bounds caches to 5,000 messages
across 24 seek segments; the visible window has at most 120 rows.

Native docking adapts to watch, portrait fullscreen and landscape fullscreen.
Overlay geometry stays inside the fitted picture and offers drag handles,
Save/Cancel, accessible movement/resize actions and position/size sliders.
Appearance stays device-local per instance. Account timestamps, user/word filters
and per-video timing use the existing dedicated APIs; guests keep their own
per-instance values. Imported regex unsupported by RE2/J is reported and skipped
without rewriting the saved value, and delayed timing reads preserve edits.

All 267 JVM tests and 15 distinct replay device scenarios passed, alongside 11
focused comments/chapter/player presentation regressions, debug app/test APK
builds, lint and isolated fixture checks. Six server replay specs, two native
scope specs and normal/API-only Invidious builds passed. No server endpoint or
new database migration was needed. Account sync requires deployment of the
native scope update and renewed sign-in tokens. Broader device, production
account and upstream replay acceptance remain unexecuted; see
[VERIFICATION.md](VERIFICATION.md) for detailed evidence and limits.

[a-chat]: android/app/src/main/java/net/wingress/mobivious/data/LiveChat.kt
[a-chat-ui]: android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt

### Native Clips — 7 October 2026

Rows 39 and 05 are Implemented. Library’s My Clips and each channel’s Clips tab
show the selected instance’s public, immutable clips in newest-first pages of 30.
Opening playback preserves the list position; publication/deletion refreshes
previously loaded pages, and deletion removes cached entries and stops an active
deleted clip. Native and YouTube clip links resolve through the selected API;
foreign native links retain their origin and offer a browser action.

The creation editor uses account identity, public visibility, a Unicode title
counter, absolute tenths-of-a-second fields, draggable storyboard handles and
selection, accessible adjustments and an independent preview. Missing storyboard
assets leave every trim control available. Drafts survive rotation, keyboard
changes and failed requests; guest sign-in returns to the editor. Publishing
offers Share, Copy link, Watch clip and Done.

Service-owned clip occurrences preserve absolute millisecond bounds while
Media3 exposes a timeline from zero to the clip duration. Loop defaults to on;
turning it off stops without automatic advancement. Bounds survive track/buffer
refresh, recreation, fullscreen, mini-player, PiP and background playback. Watch
full video continues at the clip start plus its current position, preserving
pause and playback selections. Preview/clip playback does not write source
history/progress, and suppresses chapters, chat replay and SponsorBlock skipping.

All 284 JVM tests and the combined 40-scenario emulator run passed, with debug
builds and lint. Thirteen focused server specs, normal/API-only source checks and
the guarded disposable PostgreSQL clip route/authorization harness passed.
See [VERIFICATION.md](VERIFICATION.md) for artifacts, reproduction and limits.
Native clip tokens require deployment of the sibling scope update and renewed
sign-in; existing migration 19 supplies storage. Production/upstream acceptance
is separate. Active-live clipping, published editing, remix, embeds, federation
and clip import/export remain outside scope.

[a-clips]: android/app/src/main/java/net/wingress/mobivious/data/Clips.kt
[a-clips-ui]: android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt

### Account-separated device saves — 8 October 2026

All personal device saves use a normalized instance plus an opaque account profile
or a separate guest profile. Background playback, picture in picture, chat layout,
subscription-directory sorting, search visibility, blocked-channel snapshots, chat
timing and resume positions remain separate across accounts and sign-out/restart.
First-time accounts use native device defaults and load their existing website
preferences normally; local speed is no longer written into a shared device scalar.

Guests can manage channel SponsorBlock overrides and blocked channels locally,
including inheritance/reset and unblock/undo, without authenticated requests.
Existing local playback, appearance, browsing, captions, DeArrow, chat filters and
resume settings remain supported. History, subscriptions, playlists, contribution
identity and account notification state still require accounts.

A checked versioned migration assigns unowned legacy saves to guests and retains
previously separated records under their owners. Sign-out, expiry and instance
changes preserve profiles; successful account deletion removes only that profile.
The additive server `profileId` and preference response header let existing sessions
resolve stable ownership without token renewal or a database migration. Older
servers use username-based profiles; external renames and reused usernames cannot
be distinguished reliably there. Deploy the server addition before the app for
full identity guarantees. Validation is recorded in `VERIFICATION.md`.
