# Invidious web → Mobivious Android feature checklist

Reviewed on **4 October 2026** against the local source checkouts:

- Web: `../invidious`, baseline audit at `a21a5513` plus the expanded native settings API changes; channel routes/API rechecked at `68e51ac2`. Includes this fork's custom features.
- Android: baseline audit at `c43eb76`; rows 05 and 10 updated against channel implementation `febc567`; rows 06 and 33 now include scoped search and organized history; row 34 includes watched/progress, rows 40–41 content visibility, and rows 17/32 service-owned queues and video library actions; rows 31/45 now include playlist subscriptions and RSS with migration 20, reviewed on 4 October 2026. Row 22 now implements read-only YouTube comments with a spoiler-free entry and native drawer; Reddit is intentionally excluded. Current release version: `0.5.0` (version code 7).

**The status column describes implementation in the Android app compared with the web version.** The web feature is the baseline; a server endpoint by itself does not count as an Android feature unless the app uses it and provides the relevant interaction.

- **Implemented:** the listed core capability has an Android implementation.
- **Partial:** some parts exist; the last two columns identify what exists and what is missing.
- **Not implemented:** no corresponding Android flow was found in the reviewed source.

This is a source audit, not a new native runtime or production acceptance test. Features can depend on instance configuration, upstream content availability, and device capabilities. Validation is recorded in [VERIFICATION.md](VERIFICATION.md). That file reports the production mobile sign-in/account API rollout as still pending; account features below are implemented in source, but need the server patch deployed before production use. The channel revision additionally checks public channel endpoints on the existing live instance; it does not validate the production account rollout.

**Summary: 45 broad feature areas — 23 implemented, 13 partial, 9 not implemented.** These counts describe the grouping below, not a weighted completion percentage.

## Discovery and channels

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 01 | Anonymous viewing through Invidious, without a Google account or official YouTube API | Implemented | Public browsing/playback through Invidious and Companion; no client-side YouTube extraction. | — |
| 02 | Popular and trending discovery, with regional/category selection | Partial | Popular and trending tabs; editable trending region. | Trending category selector, such as Music/Gaming/Movies. |
| 03 | Custom homepage and configurable feed navigation | Partial | Default homepage selection across Popular, Trending, Search, Subscriptions and Playlists; saved web feed-menu preferences order Home discovery chips. Four fixed bottom tabs: Home, Subscriptions, Library, Account. The top-right search field submits to existing results and Back restores the originating screen and browsing state. Search homepage support is retained. Guests fall back to public browsing for account-only homepages. | Full web menu visibility/removal; core native tabs remain fixed and Popular/Trending share Home. |
| 04 | Search across videos, channels and playlists, with filters | Partial | Videos remain the default; Playlists & mixes uses typed playlist search, pagination, source labels and preserved mix seeds. Video relevance/views, date/duration filters, links and content-visibility controls remain available. | Channel search result cards and the remaining advanced web filters. |
| 05 | Channel browsing and specialized tabs | Partial | Avatar, name, subscriber count, plain-text description; available Videos/Streams/Playlists tabs, continuation pagination, selected-tab restoration after search, and playlist Last added/Newest/Oldest sorting; channel subscribe/unsubscribe and RSS. | Shorts, Podcasts, Releases, Courses, Clips, Posts and related Channels tabs; video sorting; banner/verification/pronoun presentation and expandable rich description. Tabs depend on channel availability. |
| 06 | Search within a channel or the subscription library | Implemented | Search fields on channel and Subscriptions screens; public paginated channel video search independent of Videos/Streams; authenticated full-library title/channel subscription search, original-page pagination and saved search visibility controls. Clear restores the selected channel tab or configured feed. Icon, keyboard Search and physical Enter share submission, including pasted links on general Search. | — for the core capability; subscription search requires the sibling API update and renewed native tokens. Native runtime/layout acceptance remains unverified. |
| 07 | Community posts and post comments | Not implemented | — | Community/post pages, media/polls and their read-only comment threads. |
| 08 | Hashtag browsing | Not implemented | — | Hashtag result pages and navigable hashtag links. |

Evidence: [web routing][w-routing], [web search filters][w-search], [web channel routes][w-channels], [web channel header][w-channel-ui]; [Android browsing/navigation][a-ui], [Android view model][a-vm], [Android API][a-api].

## Watching and player controls

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 09 | Standard video playback | Implemented | Media3 DASH/HLS and fallback playback with decoded video geometry and pixel aspect ratio. Aspect-preserving fit; regular natural height capped at 70% of watch content, 40% with comments; shape-aware fullscreen orientation and clamped PiP ratio. Bounded mini-player, shared fading controls, timeline, buffering/retry and accumulated double-tap seeking retain the service-owned player. | — for core playback; real decoding, shape transitions, orientation, PiP and layout acceptance remain unverified on a device. |
| 10 | Livestream playback | Implemented | HLS playback path and LIVE labels; playable live videos can be opened directly or from channel Streams tabs. | —; channel browsing coverage is tracked in row 05, and chat replay in row 23. |
| 11 | Quality, audio-track and speed controls | Implemented | Auto or exact supported video representations with codec/FPS labels, numeric bitrates and available file sizes; at most four menu entries per resolution/FPS group, prioritizing AV1/H.264 bitrate extremes while selection uses the full catalog; original/stable-volume/dubbed audio groups and bitrate tiers, with conservative unlabeled-track fallback; unified captions and speed 0.25×–2× controls. Shared quality defaults include Auto, Best, 4320p–144p and Worst, plus preferred codec Auto/AV1/H.264. DASH selection applies before the first segment, resolves resolution before codec, and falls back to supported streams. Auto remains explicit even with one eligible stream. In-player exact choices apply to the current video; refresh/retry and controller reconnection preserve available selections, and successors use saved defaults. Synced changes apply to the next video. | — for the web capability; reliable audio enrichment uses the additive sibling video API update. Runtime/layout acceptance remains unverified; available streams and device decoding support determine offered choices. |
| 12 | Audio-only listening and background playback | Implemented | Disable video tracks, background playback setting and service-owned playback. | — |
| 13 | Captions/subtitles | Implemented | Load VTT captions, select available language, turn subtitles on/off through the unified player gear; available-track selection and active-selection display. Three saved language priorities select the first available caption when opening a video; preferences are shared with the account or saved locally for guests. | — for core captions; runtime acceptance of the new defaults remains unverified. |
| 14 | Remember playback position and resume | Implemented | Signed-in account positions shared with the website, local fallback, periodic saves, completion reset and explicit timestamp precedence. Guests can enable device-local resume; changing the preference updates active service-owned playback too. | — for core resume; runtime acceptance of guest resume remains unverified. |
| 15 | Video information and rich descriptions | Partial | Title, author, views, publication text and expandable plain-text description. | Rendered description links, clickable timestamps/hashtags, richer web metadata/actions such as likes and external source links. |
| 16 | Related/recommended videos | Implemented | Recommended video list on the watch screen; manual selection, channel navigation and a shared visibility preference. | —; automatic advancement is tracked separately. |
| 17 | Automatic next-video playback, playlist queue and repeat | Implemented | Service-owned next-recommendation playback; public/private playlists and dynamic mixes; source occurrence context, overlapping-page loading, previous/next and queue selection; temporary Play next/Add to queue; local queue removal and separate owned-playlist deletion; session Off/One/All repeat (All for finite queues). Shared next/autoplay-next/loop defaults; background/audio/PiP advancement. | — for the requested capability; native runtime/layout acceptance remains unverified. Queues last for the current service session; shuffle and restart restoration are outside scope. |
| 18 | Chapters and timeline thumbnail previews | Not implemented | — | Chapter navigation/markers and storyboard previews while seeking. |
| 19 | Searchable transcripts | Not implemented | — | Transcript panel, language choice, text search and timestamp navigation. Captions alone do not provide this flow. |
| 20 | Playback diagnostics and buffer recovery | Partial | Playback errors and Retry in both embedded/fullscreen modes; Retry reloads stream details and resumes at the current position. A dedicated Refresh buffer action reloads the current source while retaining paused/playing state, speed, quality, captions and audio-only mode; live playback returns to the live edge. | Web's detailed playback statistics/copy action. |
| 21 | Legacy annotations and VR/360° viewing | Not implemented | — | Annotation overlays/toggles and specialized VR projection controls. |
| 22 | Read-only YouTube comments (Reddit excluded) | Implemented | Spoiler-free entry without preview text; drawer beneath the visible player; Top/Newest sorting; independently paginated reply threads with Back and saved scroll positions; avatars, author/date/body hierarchy, thumb-up counts, creator/verified/pinned/member/heart metadata; expandable native rich text, links, timestamps and custom emoji; loading/empty/error states and cursor-preserving retry. | — for the requested YouTube capability. Reddit is deliberately excluded; posting and liking remain unsupported. Native runtime/layout acceptance remains unverified. |
| 23 | Livestream archive chat replay | Not implemented | — | Replay synchronized to playback; docked/overlay chat; timestamp/font/size/opacity controls; user/word filters and saved timing offsets. This fork implements replay, not sending live chat messages. |
| 24 | Sharing and opening content links | Partial | Android share sheet for the current video with timestamp; receive shared/pasted YouTube and configured-instance video, playlist and mix links, preserving list/index/timestamp context and normalizing YouTube indexes. Bare source links open a browser with Play; public lists work for guests. App-link registration includes `/watch`, `/playlist` and `/mix` on bundled hosts. | Channel, post and clip link navigation and broader web URL parameters. An embed URL can identify a video, but Android does not host a web embed player. |

Evidence: [web watch page][w-watch], [web player component][w-player], [web player logic][w-player-js], [web stream controls][w-streams], [web playlist queue][w-watch-js], [web comments API][w-videos], [web comments UI][w-comments]; [Android watch UI][a-ui], [YouTube comment state][a-comments], [native comments drawer][a-comments-ui], [Android player controls/settings][a-player], [Android playback setup][a-vm], [Android playback service][a-service], [Android link parsing][a-models], [activity/PiP][a-activity], [manifest][a-manifest].

## Accounts, subscriptions and library

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 25 | Account sign-in and sign-out | Implemented | Account tab with native sign-in, encrypted bearer-session storage, expiry handling and logout revocation/cleanup. Contextual sign-in returns to the interrupted screen/action. Delayed authentication and account-management responses are isolated by account and instance; incorrect current passwords retain the session. Settings is available within Account for guests and members. | —; requires the mobile login endpoint. Device acceptance remains unverified. |
| 26 | Account registration and account/session management | Implemented | Native signup with instance availability and single-use CAPTCHA; password-confirmed username/password changes and account deletion; opaque browser-session/API-token listing with creation/available expiry and This session; individual/current-session revocation; guided permissions, advanced scopes, expiry choices and one-time token display/Copy. Credential changes atomically revoke old sessions/tokens and return a replacement mobile session, preserving internal identity and library data. | — for website parity; deploy the sibling server update and renew mobile sign-in for management scopes. No additional migration. Native runtime/layout acceptance remains unverified. |
| 27 | Channel subscriptions and subscription feed | Implemented | Subscribe/unsubscribe from channel/watch pages, subscribed channel chips, paginated subscription feed sharing the web account. A pinned Channels button opens an alphabetical channel directory with immediate name search, independent refresh/retry and retained position when returning from a channel. | — |
| 28 | Subscription-feed filtering and sorting | Partial | The shared feed API applies account settings. A dedicated settings screen edits latest-only/unwatched-only, page size, sort and pending-notifications-only. Feed/history requests respect server page sizes; notifications-only shows no ordinary videos when notifications are empty. | Separated notification/feed presentation and Android alerts. |
| 29 | New-upload/livestream notifications | Not implemented | Pending notification videos returned by the feed API are included in the ordinary subscription list; this is not an alert implementation. | Upload notification subscription/stream handling, notification count/badge and Android alerts for new uploads/livestreams. The playback notification is a separate feature. |
| 30 | Personal Invidious playlist management | Implemented | List/create/edit/delete playlists; public/unlisted/private privacy; edit title/description; add the watched video; remove items; paginated playlist contents. | — for core management; shortcuts, external playlists, sharing and queue behavior are tracked in rows 17, 24, 31, 32 and 36. |
| 31 | Public/YouTube playlists, saved external playlists and mixes | Implemented | Typed discovery and channel Playlists tab; My playlists and Subscribed playlists with counts and source/ownership labels. Subscribe/unsubscribe YouTube, seeded mixes and others’ public/unlisted Invidious sources; current owner metadata/videos on open/refresh and cached metadata fallback. Subscribed sources stay read-only and are excluded from save/default destinations. Guest sign-in intent, duplicate prevention, retry and account/instance response isolation; unsubscribing preserves playback. | — for requested parity; requires migration 20 and renewed native sign-in. Device/runtime acceptance remains unverified. |
| 32 | Video-card/context-menu library actions | Implemented | Shared Save to playlist/create-and-save sheet from cards and watch pages, writable/default playlist choices, failed-draft and partial-creation retry; Audio mode, Play next/Add to queue, channel navigation, Block/Unblock with scoped Undo; history/owned-playlist removal and success/error feedback. | Watch on YouTube and Switch Invidious instance actions are intentionally excluded from Android. Native runtime/layout acceptance remains unverified. |
| 33 | Watch history and history organization | Implemented | Record watched videos; full-history title/channel search before pagination; account-timezone Today/Yesterday/Last 7 days/Last 30 days/Older groups; saved title/channel/duration/release/latest-watch metadata and unknown-value fallbacks; unavailable entries, single removal, clear confirmation and shared enable/disable setting. Basic viewing remains on older servers with a search update explanation. | — for website parity; organized history requires the sibling API update, using existing scopes. Archived-date expansion and date-range filters are outside the requested scope. Native runtime/layout acceptance remains unverified. |
| 34 | Watched/progress indicators and manual watched state | Partial | Shared watched badges/thumbnail overlays and saved-progress bars across native video cards, including recommendations; compact and thumbnail-free presentation; accessible history/progress descriptions. Account state uses the shared playback API; guests see device-local progress when resume is enabled. Service-owned playback updates indicators in background/audio/PiP. Existing history removal preserves progress; clearing history clears both. | Explicit mark-watched/mark-unwatched actions are intentionally excluded from the Android UX. Native runtime/layout acceptance remains unverified. |
| 35 | Data import/export and migration | Partial | Complete subscription OPML export with Invidious or YouTube feed URLs; owned private playlist Atom snapshots via document picker or file sharing. | Full account data import/export; YouTube playlist/history imports; NewPipe/FreeTube imports and other data migration flows. The native token has only the dedicated subscription-export permission. |
| 36 | Shared account preferences | Partial | Dedicated Settings and submenu screens; shared history/resume, playback defaults (autoplay, next recommendation, autoplay next, single-video loop, audio only, proxy, speed, preferred DASH codec, ranked quality, caption priorities), color mode/density/thumbnails, homepage/feed order, region, members-only and comments/recommendations/description visibility, feed filters/sort/page size, default playlist, DeArrow and SponsorBlock. Sparse patches preserve unrelated server values; refresh on sign-in, settings opening and foreground return. | Preferences for capabilities outside the implemented native scope, including web theme registry/randomization, interface locale, annotations, VR, chat replay. Background/PiP stay device-local. The expanded shared settings require the sibling settings API update. |

Evidence: [web account][w-account], [web authenticated APIs][w-auth], [web feed rules][w-users], [web playlist routes][w-playlists], [web common playlist/mix API][w-playlist-api], [web history][w-history], [web watched indicators][w-indicator], [web data control][w-data], [web preferences][w-prefs]; [Android account/library UI][a-ui], [Android API][a-api], [Android watched/progress state][a-watched], [Android session/local settings][a-store], [mobile API contract][w-mobile].

## Enhancements, appearance and instances

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 37 | SponsorBlock | Implemented | Instance-proxied segments; all eight web categories and four modes; colored timeline ranges and scrub labels; manual Skip/Dismiss with remaining time; service-owned auto skipping in background/audio/PiP; overlapping-range merging and manual replay after an auto skip; custom colors; shared account settings and inherited per-channel overrides with a native manager. Guests save global settings per instance. | — for the web capability; shared settings require the SponsorBlock preference API update. Native runtime/layout acceptance is unverified because the emulator crashes before boot; see VERIFICATION.md. |
| 38 | DeArrow titles and contributions | Implemented | Optional replacement titles throughout video lists, watch/mini-player and system playback metadata; accessible original-title toggle; native suggestion/voting sheet with all four guideline acknowledgements, locked/original vote restrictions, refresh and preserved failed drafts; shared account settings and encrypted contribution identity, including private-ID import. Guests keep per-instance local title settings. | — for the web capability; requires the native DeArrow API server update and an updated sign-in token. This fork does not implement thumbnail replacement. |
| 39 | Clips | Not implemented | — | Resolve/play YouTube or native clips; create/preview/share bounded native clips; My Clips/channel Clips lists, loop control and deletion. Existing web native clips are public, immutable, 5–120 seconds; active-live clipping/editing/clip embeds are excluded there too. |
| 40 | Channel blocking | Implemented | Shared account block list; card and channel Block/Unblock actions; Browsing settings manager with channel navigation, errors and retry; immediate discovery/search/recommendation filtering and saved search inclusion override. Confirmed snapshots support offline filtering and isolate accounts/instances. Subscriptions, history, playlists and direct access remain available. The watch action row omits the blocking overflow menu. | — for the web capability; requires the sibling blocking API deployment and renewed sign-in tokens. Native runtime/layout acceptance remains unverified. |
| 41 | Members-only content visibility controls | Implemented | Shared show-members preference, per-instance guest preference, device-local search override/reset, explicit member metadata and Members only labels. Filters discovery, search, channels, subscriptions, playlists and recommendations; preserves history and direct access. Original pages and continuation state keep hidden pages navigable. | — for visibility controls; requires additive member metadata and preference PATCH support on the server. Missing metadata remains visible. This does not grant membership access. Native runtime/layout acceptance remains unverified. |
| 42 | Appearance and layout preferences | Partial | Shared manual light/dark/system mode, compact video-list density and thumbnail-free thin mode; dedicated Appearance settings screen. | Registered Modern Neon/Diary themes, scheduled random theme selection and alternative player styles. |
| 43 | Interface localization | Not implemented | — | Translated app interface and language selector. Android's user-facing strings are hardcoded in English; caption/audio language selection is a separate capability. |
| 44 | Instance selection/switching and proxy preference | Partial | Dedicated server screen for an HTTPS instance; fresh installs default to `https://invidious.wingress.net`; clear local session/cache/player when switching. A shared proxy preference is used by video and manifest requests. | Instance discovery/automatic redirection and per-content switch-instance flow. Switching servers does not migrate account data. |
| 45 | RSS subscriptions | Implemented | Open/Copy/Share selected-instance channel, public/unlisted playlist, seeded mix and secret subscription-feed links. Subscription access explanation and memory-only link; authenticated Open/Save/Share private-owned Atom files through a restricted FileProvider; complete OPML in both web formats, shared server serializers and empty/error/retry/account-change handling. | — for website feed/export parity; no built-in RSS reader, polling or upload alerts. Device/export-app acceptance remains unverified. |

Evidence: [web preferences][w-prefs], [web player/SponsorBlock integration][w-player], [web SponsorBlock behavior][w-sponsor-js], [web SponsorBlock data/settings][w-sponsor], [DeArrow implementation][w-dearrow], [DeArrow contribution contract][w-dearrow-doc], [native clip contract][w-clips], [web blocking][w-blocked], [web theme registry][w-themes], [web routing/RSS][w-routing]; [Android content visibility][a-visibility], [Android blocking controls][a-visibility-ui], [Android settings/appearance][a-ui], [Android SponsorBlock sheets][a-sponsor-ui], [Android SponsorBlock settings/data][a-sponsor], [Android SponsorBlock skip engine][a-sponsor-engine], [Android DeArrow UI][a-dearrow-ui], [Android title resolver][a-dearrow], [Android API][a-api], [Android models][a-models], [Android instance/session switching][a-vm].

## Integration details affecting the checklist

1. **Search sorting now uses the fork’s contract:** [Android API][a-api] sends `sort=relevance` or `sort=views`, matching [the search parser][w-search]. The native menu exposes these supported choices; the former `view_count` value maps to `views`, while unsupported legacy values fall back to relevance. Date and duration retain their correctly named parameters. Wire behavior is covered by API tests; live result-order testing is not claimed.
2. **External playlists are only partly reachable:** [authenticated playlist listing][w-auth] includes account-stored playlists; Android reads them through its authenticated playlist method. It has no public playlist/deep-link flow or external-playlist subscription method, and exposes edit/delete controls without distinguishing an external saved playlist from an owned native playlist. The server limits what those mutations can do. Mix IDs redirect in [the common API][w-playlist-api], while Android's HTTP client disables redirects and has no mix API method.
3. **Feed settings now have native controls:** [shared server feed rules][w-users] are applied by the authenticated feed endpoint. Android no longer overrides account page size with 30. Pending-notifications-only filters the returned notifications even when the web endpoint falls back to ordinary feed videos; latest-only and notifications-only lists do not expose redundant pagination. Other feeds still merge notifications and ordinary items.
4. **Shared settings require the expanded PATCH API:** [the mobile PATCH endpoint][w-mobile] now validates supported native booleans, playback defaults, captions, appearance, browsing and feed values in addition to SponsorBlock. It merges changed fields under the account lock and preserves unknown preferences. The existing preference token scopes suffice; no migration or token renewal is added. Guests save native preferences per instance. Background playback and PiP remain device settings. Unsupported web capabilities have no native switches.
5. **Channel first pages omit empty continuation values:** [Android API][a-api] omits the parameter for empty/blank tokens and preserves opaque follow-up tokens for both Videos and Streams. The existing live instance returns a YouTube 400 error when `continuation=` is present. [Channel metadata][a-models] selects supported tabs in Videos/Streams order, preserving the current selection on Refresh/Retry when available and falling back to Videos if no supported tab is advertised. WAN Show advertises Streams, Podcasts and Posts, so Android selects Streams. These existing public endpoints require no server patch.

## Platform-specific context

The web checkout also supplies embeddable players, developer JSON APIs, browser OpenSearch integration, server-side HTML with many no-JavaScript fallbacks, and self-hosting/administration controls. These are web/server capabilities rather than native screen parity items. Android consumes the APIs and inherits server extraction/proxy behavior; it does not need to duplicate the server.

Android already adds native conveniences: a mini-player, MediaSession/system controls, playback notification, picture-in-picture with seek/play actions, background service playback, encrypted session storage, and short-lived cached discovery/search/subscription results. The response cache is not video downloading or offline media playback. Casting and offline media management were not found as comparable implemented web features in this checkout, so they are not counted as web parity gaps.

The original checklist was a documentation-only source audit. The subsequent Android player overhaul consolidates playback settings under one in-player gear, removes the persistent fullscreen/back overlays, and adds touch seeking and Refresh buffer. Row 11 also corrects an audit omission: the old Media3 gear already exposed 0.25× speed; the new unified sheet retains it. Rows 09, 11, 13 and 20 reflect the overhaul; their broad statuses and summary counts are unchanged. Detailed statistics remain missing. Runtime validation for this revision is recorded separately in [VERIFICATION.md](VERIFICATION.md).

The DeArrow revision implements row 38 and the DeArrow portion of row 36. Titles retain their canonical original metadata, contributions use the web account's encrypted identity, and write requests are never automatically retried. The server API update needs deployment; existing native tokens must be renewed by signing in again. The DeArrow revision brought the totals to 9 implemented, 17 partial and 19 not implemented areas; the SponsorBlock revision brought the totals to 10 implemented, 17 partial and 18 not implemented; the later settings revision is reflected in the summary above. Validation and production limitations are recorded in [VERIFICATION.md](VERIFICATION.md).

The SponsorBlock revision implements row 37 and the SponsorBlock portion of row 36. It follows the local web fork's opt-in defaults, modes, colors, inheritance, dismissal and once-per-session automatic skipping. Active livestreams are excluded. Segment failures leave playback usable. Segment submission/voting is outside the web baseline. Server checks and Android build/unit validation passed; connected tests and native screenshots remain unverified. The existing production rollout remains separate.

## Source references

Links are repository-relative so this checklist works with the documented sibling-checkout layout. The listed commits identify the reviewed snapshot.

[a-ui]: android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt
[a-dearrow-ui]: android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt
[a-dearrow]: android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt
[a-player]: android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt
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
[w-search-api]: ../invidious/src/invidious/routes/api/v1/search.cr
[w-channels]: ../invidious/src/invidious/routes/channels.cr
[w-channel-ui]: ../invidious/src/invidious/views/components/channel_info.ecr
[w-watch]: ../invidious/src/invidious/views/watch.ecr
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


## Settings overhaul — 3 October 2026

Rows 03, 11, 13, 14, 16, 28, 36, 42 and 44 were updated against the current source. This adds two implemented areas (captions and guest resume) and moves homepage/navigation to partial. The 37 Android unit/API tests, debug APK builds, lint, focused server specs and disposable account/API harness passed. New Compose settings scenarios compile; they have not run because the installed emulator exits with SIGSEGV before boot. See `VERIFICATION.md` for exact validation and deployment requirements.

## Channel loading and Streams tabs — 3 October 2026

Rows 05 and 10 now include native Streams browsing and automatic selection for streams-only channels. Row 05 remains Partial and the summary counts are unchanged. All 58 Android unit/API tests, debug/instrumentation APK builds, lint and disposable channel fixture checks passed. Public live API checks returned 15 WAN Show streams and two 60-video pages of regular uploads. Four new Compose channel scenarios compile but have not run because no device is connected; native runtime/layout acceptance remains pending. See `VERIFICATION.md` for the checks and repeat commands.

## Watched/progress indicators — 4 October 2026

Row 34 now includes automatic watched indicators and saved-progress bars throughout the shared native video cards, with guest progress, account/instance isolation, background service updates and existing history-control synchronization. It remains Partial because manual mark-watched/mark-unwatched actions are intentionally outside the requested Android behavior. No new server API, token scope or database migration is needed.

All 78 Android unit/API tests, debug/instrumentation APK builds, lint and disposable watched/progress fixture HTTP checks passed. Six new Compose scenarios compile but have not run: the installed emulator exited with SIGSEGV before Android booted. Runtime, layout and screenshot acceptance remain pending; see `VERIFICATION.md`.

## Members-only visibility and channel blocking — 4 October 2026

Rows 40–41 now implement the web visibility capabilities. Blocking requires an account and shares the existing website block table. Android keeps public responses unpersonalized and filters the original loaded lists locally, retaining pagination and playlist occurrence indexes. Block changes and preference changes update lists/recommendations without stopping playback. Search visibility overrides are device-local and isolated by instance/account or guest; they do not overwrite global preferences. History and directly opened videos remain accessible.

The server adds boolean `isMember` to search/channel/feed/video/recommendation metadata, permits `show_member_videos` sparse preference patches, and exposes scoped block-list read/block/unblock endpoints. No new migration is introduced. Blocking needs the updated server and renewed native tokens; membership preferences need no new scope. Old-server/token errors explain the required update.

All 93 Android unit/API tests, debug/instrumentation APK builds and lint passed, alongside focused Crystal specs, normal/API-only executable builds, the guarded disposable account/API harness and visibility fixture HTTP checks. Six new Compose scenarios compile but remain unexecuted: the emulator exited with SIGSEGV before Android booted. Native layout, screenshots and runtime acceptance remain pending; see `VERIFICATION.md`.

[a-visibility]: android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt
[a-visibility-ui]: android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt

## Scoped search and organized history — 4 October 2026

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

## Video library actions and playback queues — 4 October 2026

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
External-playlist save/unsave, shuffle and restart restoration remain outside scope.
See VERIFICATION.md for checks and the emulator limitation.

Explicit queues now appear expanded inline beneath watch metadata and before
Up next. The collapsible header replaces both the watch action chip and player
queue icon. Standalone and implicit recommendation playback have no queue panel.
A bounded list of compact rows follows the current occurrence and marks it with
a tinted background, play marker and accessible selected state. Collapse survives
item changes, navigation and Activity recreation. Loading and retry remain inline
when video details are absent; existing paging, repeat and occurrence actions are
retained. This presentation update adds no server API or storage changes.

## Playlist subscriptions and RSS — 4 October 2026

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


## Read-only YouTube comments — 4 October 2026

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

## Channel avatar visibility — 4 October 2026

Android now displays circular avatars in channel/watch headers, subscription chips,
comments/replies and creator hearts, shared video cards (including recommendations,
history, playlists and queues), and YouTube playlist creator rows. Video titles
retain their full width; thin mode omits avatars and a channel's own upload/stream
lists suppress repeated owner images. The same author action handles image/name
navigation. Missing/loading/failed images retain a themed initial or person icon.

Existing JSON responses gain optional `authorThumbnails` from supplied metadata or
the existing migration 21 cache, with batched page/nested-entry reads and no extra
YouTube metadata requests. Images use the selected instance's `/ggpht` proxy with
query preservation, a shared native cache, and no credentials or redirects. No
additional migration, endpoint or token scope is introduced. Install a new app
build and deploy the additive server API update for full cached-list coverage.
Row 05 remains Partial for the remaining channel-page capabilities.

Validation and outstanding native device/screenshot checks are recorded in
VERIFICATION.md. No installation, deployment or release publication was performed.


## Codec-aware quality parity — 4 October 2026

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

The native account revision implements row 26 and updates rows 03, 09 and 25. Account replaces Search in the fixed bottom navigation; global search expands in the top-right and submits to the existing results screen. Settings is inside Account. The sibling server adds native registration and dedicated password-confirmed account APIs using existing account/session tables. Production requires that server update and renewed mobile sign-in; deployment and release publishing are separate. Android unit/build/lint, server builds/specs and the disposable account harness are recorded in [VERIFICATION.md](VERIFICATION.md); device acceptance remains unverified.
