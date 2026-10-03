# Invidious web → Mobivious Android feature checklist

Reviewed on **3 October 2026** against the local source checkouts:

- Web: `../invidious`, reviewed commit `a21a5513` plus the expanded native settings API changes in the working tree — includes this fork's custom features.
- Android: this repository, reviewed commit `13d2695`, app version `0.1.1`, plus the settings overhaul in the working tree.

**The status column describes implementation in the Android app compared with the web version.** The web feature is the baseline; a server endpoint by itself does not count as an Android feature unless the app uses it and provides the relevant interaction.

- **Implemented:** the listed core capability has an Android implementation.
- **Partial:** some parts exist; the last two columns identify what exists and what is missing.
- **Not implemented:** no corresponding Android flow was found in the reviewed source.

This is a source audit, not a new runtime or production acceptance test. Features can depend on instance configuration, upstream content availability, and device capabilities. Existing validation is recorded in [VERIFICATION.md](VERIFICATION.md). That file reports the production mobile sign-in/account API rollout as still pending; account features below are implemented in source, but need the server patch deployed before production use. No live deployment was checked for this audit.

**Summary: 45 broad feature areas — 12 implemented, 16 partial, 17 not implemented.** These counts describe the grouping below, not a weighted completion percentage.

## Discovery and channels

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 01 | Anonymous viewing through Invidious, without a Google account or official YouTube API | Implemented | Public browsing/playback through Invidious and Companion; no client-side YouTube extraction. | — |
| 02 | Popular and trending discovery, with regional/category selection | Partial | Popular and trending tabs; editable trending region. | Trending category selector, such as Music/Gaming/Movies. |
| 03 | Custom homepage and configurable feed navigation | Partial | Default homepage selection across existing Popular, Trending, Search, Subscriptions and Playlists destinations; shared feed-menu priorities order native navigation and Home discovery chips. Guests fall back to public browsing for account-only homepages. | Full web menu visibility/removal: native core navigation destinations remain reachable and Popular/Trending share Home. |
| 04 | Search across videos, channels and playlists, with filters | Partial | Video search, pagination, upload-date filter, short/long duration filters, pasted video links. | Channel/playlist result types, medium-duration and feature filters such as live/HD/4K/subtitles. **Sorting is not wired correctly:** Android sends `sort_by`; this fork reads `sort`. See the integration notes below. |
| 05 | Channel browsing and specialized tabs | Partial | Name, subscriber count, truncated plain-text description, paginated Videos, subscribe/unsubscribe. | Shorts, Streams, Podcasts, Releases, Courses, Playlists, Clips, Posts and related Channels tabs; channel sorting; banner/avatar/verification/pronoun presentation and expandable rich description. Tabs on the web depend on channel availability. |
| 06 | Search within a channel or the subscription library | Not implemented | — | Dedicated channel search and authenticated subscription search flows. Android's general video search is not scoped to the currently open channel. |
| 07 | Community posts and post comments | Not implemented | — | Community/post pages, media/polls and their read-only comment threads. |
| 08 | Hashtag browsing | Not implemented | — | Hashtag result pages and navigable hashtag links. |

Evidence: [web routing][w-routing], [web search filters][w-search], [web channel routes][w-channels], [web channel header][w-channel-ui]; [Android browsing/navigation][a-ui], [Android view model][a-vm], [Android API][a-api].

## Watching and player controls

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 09 | Standard video playback | Implemented | Media3 player, adaptive DASH/HLS and fallback streams, a shared fading controller for embedded/fullscreen playback, play/pause, timeline scrubbing, double-tap ±10-second seeking and buffering/error retry display. | — |
| 10 | Livestream playback | Implemented | HLS playback path and LIVE labels; a playable live video can be opened directly. | —; dedicated Streams browsing is tracked in row 05, and chat replay in row 23. |
| 11 | Quality, audio-track and speed controls | Partial | Auto or resolution ceiling from 144p–2160p in app settings (360p–2160p in the player gear); a unified in-player settings sheet with available audio-track selection (including unlabeled tracks), captions and speed 0.25×–2×; active-selection display and shared account speed/quality defaults (per-instance local defaults for guests). | Web's richer representation selection with codec/FPS/bitrate details, audio bitrate tiers, stable-volume/original/dubbed-track distinction and additional web quality choices. Android's quality option limits maximum height rather than selecting one exact representation. |
| 12 | Audio-only listening and background playback | Implemented | Disable video tracks, background playback setting and service-owned playback. | — |
| 13 | Captions/subtitles | Implemented | Load VTT captions, select available language, turn subtitles on/off through the unified player gear; available-track selection and active-selection display. Three saved language priorities select the first available caption when opening a video; preferences are shared with the account or saved locally for guests. | — for core captions; runtime acceptance of the new defaults remains unverified. |
| 14 | Remember playback position and resume | Implemented | Signed-in account positions shared with the website, local fallback, periodic saves, completion reset and explicit timestamp precedence. Guests can enable device-local resume; changing the preference updates active service-owned playback too. | — for core resume; runtime acceptance of guest resume remains unverified. |
| 15 | Video information and rich descriptions | Partial | Title, author, views, publication text and expandable plain-text description. | Rendered description links, clickable timestamps/hashtags, richer web metadata/actions such as likes and external source links. |
| 16 | Related/recommended videos | Implemented | Recommended video list on the watch screen; manual selection, channel navigation and a shared visibility preference. | —; automatic advancement is tracked separately. |
| 17 | Automatic next-video playback, playlist queue and repeat | Not implemented | — | Next-video/continue settings, sequential playlist/mix playback, queue panel and loop control. Opening a playlist item loads one media item and loses playlist playback context. |
| 18 | Chapters and timeline thumbnail previews | Not implemented | — | Chapter navigation/markers and storyboard previews while seeking. |
| 19 | Searchable transcripts | Not implemented | — | Transcript panel, language choice, text search and timestamp navigation. Captions alone do not provide this flow. |
| 20 | Playback diagnostics and buffer recovery | Partial | Playback errors and Retry in both embedded/fullscreen modes; Retry reloads stream details and resumes at the current position. A dedicated Refresh buffer action reloads the current source while retaining paused/playing state, speed, quality, captions and audio-only mode; live playback returns to the live edge. | Web's detailed playback statistics/copy action. |
| 21 | Legacy annotations and VR/360° viewing | Not implemented | — | Annotation overlays/toggles and specialized VR projection controls. |
| 22 | Read-only YouTube and Reddit comments | Partial | Top-level YouTube comments, author/text/date/like count, continuation pagination and retry. | Reply-thread navigation, sorting, Reddit source selection and rich links/content. Neither client provides posting or liking YouTube comments through this feature. |
| 23 | Livestream archive chat replay | Not implemented | — | Replay synchronized to playback; docked/overlay chat; timestamp/font/size/opacity controls; user/word filters and saved timing offsets. This fork implements replay, not sending live chat messages. |
| 24 | Sharing and opening content links | Partial | Android share sheet for the current video with timestamp; receive shared/pasted YouTube and configured-instance video links, including Shorts/embed/live video forms. | Channel, playlist, mix, post and clip link navigation; preserving playlist context and broader web URL parameters. Automatic app-link registration only covers `/watch` on the two bundled instance hosts. An embed URL can identify a video, but Android does not host a web embed player. |

Evidence: [web watch page][w-watch], [web player component][w-player], [web player logic][w-player-js], [web stream controls][w-streams], [web playlist queue][w-watch-js], [web comments API][w-videos], [web comments UI][w-comments]; [Android watch UI][a-ui], [Android player controls/settings][a-player], [Android playback setup][a-vm], [Android playback service][a-service], [Android link parsing][a-models], [activity/PiP][a-activity], [manifest][a-manifest].

## Accounts, subscriptions and library

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 25 | Account sign-in and sign-out | Implemented | Native username/password login, encrypted saved bearer session, expiry handling, token revocation on logout and account-scoped library requests. | —; requires the fork's mobile login endpoint. |
| 26 | Account registration and account/session management | Not implemented | — | Signup, username/password changes, account deletion, API token authorization/listing/revocation management. Revoking the app's own token at logout is covered in row 25. |
| 27 | Channel subscriptions and subscription feed | Implemented | Subscribe/unsubscribe from channel/watch pages, subscribed channel chips, paginated subscription feed sharing the web account. | — |
| 28 | Subscription-feed filtering and sorting | Partial | The shared feed API applies account settings. A dedicated settings screen edits latest-only/unwatched-only, page size, sort and pending-notifications-only. Feed/history requests respect server page sizes; notifications-only shows no ordinary videos when notifications are empty. | Separated notification/feed presentation and Android alerts. |
| 29 | New-upload/livestream notifications | Not implemented | Pending notification videos returned by the feed API are included in the ordinary subscription list; this is not an alert implementation. | Upload notification subscription/stream handling, notification count/badge and Android alerts for new uploads/livestreams. The playback notification is a separate feature. |
| 30 | Personal Invidious playlist management | Implemented | List/create/edit/delete playlists; public/unlisted/private privacy; edit title/description; add the watched video; remove items; paginated playlist contents. | — for core management; shortcuts, external playlists, sharing and queue behavior are tracked in rows 17, 24, 31, 32 and 36. |
| 31 | Public/YouTube playlists, saved external playlists and mixes | Partial | The library API can return external playlists already saved on the web; the common playlist API can read their contents when reached through Library. | Discover/open arbitrary public or YouTube playlist links without signing in, save/unsave external playlists in Android, and browse/play mixes. The app does not follow the playlist endpoint's redirect to a mix. |
| 32 | Video-card/context-menu library actions | Partial | Save from the watch page; remove items in playlist/history lists; open a video's channel. | Save directly from browse/search/feed cards, inline create-and-save, quick audio/source/instance actions and undo feedback available in the web context-menu flows. |
| 33 | Watch history and history organization | Partial | Record watched videos, paginated history, keep unavailable entries, remove one entry, clear all with confirmation and shared enable/disable setting. | Search/filter history, timezone-aware date groups and fuller archived metadata presentation. |
| 34 | Watched/progress indicators and manual watched state | Not implemented | Automatic history recording exists (row 33). | Thumbnail progress bars/watched indicators and explicit mark-watched/mark-unwatched actions from browsing cards. Removing a history entry exists, but there is no general watched-state action. |
| 35 | Data import/export and migration | Not implemented | — | Invidious data import/export; YouTube subscription/playlist/history imports; NewPipe/FreeTube imports and subscription exports/OPML. The native login token also lacks export/import scopes. |
| 36 | Shared account preferences | Partial | Dedicated Settings and submenu screens; shared history/resume, playback defaults (autoplay, audio only, proxy, speed, resolution ceiling, caption priorities), color mode/density/thumbnails, homepage/feed order, region, comments/recommendations/description visibility, feed filters/sort/page size, default playlist, DeArrow and SponsorBlock. Sparse patches preserve unrelated server values; refresh on sign-in, settings opening and foreground return. | Preferences for capabilities outside the implemented native scope, including web theme registry/randomization, interface locale, annotations, VR, chat replay and next-video queues. Background/PiP stay device-local. The expanded shared settings require the sibling settings API update. |

Evidence: [web account][w-account], [web authenticated APIs][w-auth], [web feed rules][w-users], [web playlist routes][w-playlists], [web common playlist/mix API][w-playlist-api], [web history][w-history], [web watched indicators][w-indicator], [web data control][w-data], [web preferences][w-prefs]; [Android account/library UI][a-ui], [Android API][a-api], [Android session/local settings][a-store], [mobile API contract][w-mobile].

## Enhancements, appearance and instances

| ID | Feature in the web version | Android status | Implemented in Android | Missing from Android |
| --- | --- | --- | --- | --- |
| 37 | SponsorBlock | Implemented | Instance-proxied segments; all eight web categories and four modes; colored timeline ranges and scrub labels; manual Skip/Dismiss with remaining time; service-owned auto skipping in background/audio/PiP; overlapping-range merging and manual replay after an auto skip; custom colors; shared account settings and inherited per-channel overrides with a native manager. Guests save global settings per instance. | — for the web capability; shared settings require the SponsorBlock preference API update. Native runtime/layout acceptance is unverified because the emulator crashes before boot; see VERIFICATION.md. |
| 38 | DeArrow titles and contributions | Implemented | Optional replacement titles throughout video lists, watch/mini-player and system playback metadata; accessible original-title toggle; native suggestion/voting sheet with all four guideline acknowledgements, locked/original vote restrictions, refresh and preserved failed drafts; shared account settings and encrypted contribution identity, including private-ID import. Guests keep per-instance local title settings. | — for the web capability; requires the native DeArrow API server update and an updated sign-in token. This fork does not implement thumbnail replacement. |
| 39 | Clips | Not implemented | — | Resolve/play YouTube or native clips; create/preview/share bounded native clips; My Clips/channel Clips lists, loop control and deletion. Existing web native clips are public, immutable, 5–120 seconds; active-live clipping/editing/clip embeds are excluded there too. |
| 40 | Channel blocking | Not implemented | — | Block/unblock/manage channels and personalized filtering of browse/search/recommendation content. Most Android public reads do not send account authorization, so web account filtering should not be assumed to carry over. |
| 41 | Members-only content visibility controls | Not implemented | — | Member-video filtering preferences and per-search visibility override. Android does not model member status or expose these controls; this is visibility filtering, not membership authentication/access. |
| 42 | Appearance and layout preferences | Partial | Shared manual light/dark/system mode, compact video-list density and thumbnail-free thin mode; dedicated Appearance settings screen. | Registered Modern Neon/Diary themes, scheduled random theme selection and alternative player styles. |
| 43 | Interface localization | Not implemented | — | Translated app interface and language selector. Android's user-facing strings are hardcoded in English; caption/audio language selection is a separate capability. |
| 44 | Instance selection/switching and proxy preference | Partial | Dedicated server screen for an HTTPS instance; fresh installs default to `https://invidious.wingress.net`; clear local session/cache/player when switching. A shared proxy preference is used by video and manifest requests. | Instance discovery/automatic redirection and per-content switch-instance flow. Switching servers does not migrate account data. |
| 45 | RSS subscriptions | Not implemented | — | Open/share channel, playlist and private subscription RSS feeds from Android. These routes already exist on the server. |

Evidence: [web preferences][w-prefs], [web player/SponsorBlock integration][w-player], [web SponsorBlock behavior][w-sponsor-js], [web SponsorBlock data/settings][w-sponsor], [DeArrow implementation][w-dearrow], [DeArrow contribution contract][w-dearrow-doc], [native clip contract][w-clips], [web blocking][w-blocked], [web theme registry][w-themes], [web routing/RSS][w-routing]; [Android settings/appearance][a-ui], [Android SponsorBlock sheets][a-sponsor-ui], [Android SponsorBlock settings/data][a-sponsor], [Android SponsorBlock skip engine][a-sponsor-engine], [Android DeArrow UI][a-dearrow-ui], [Android title resolver][a-dearrow], [Android API][a-api], [Android models][a-models], [Android instance/session switching][a-vm].

## Integration details affecting the checklist

1. **Search sorting mismatch:** [Android API][a-api] sends `sort_by` with `relevance`, `rating`, `upload_date` or `view_count`. [This fork's search parser][w-search] reads `sort` and supports `relevance` or `views`. The [search route][w-search-api] passes query parameters directly to that parser, with no `sort_by` translation. The menu exists, but selecting a different sort leaves the default relevance sort. Date and duration are separate, correctly named parameters. This finding was established from source, not a live result-order test.
2. **External playlists are only partly reachable:** [authenticated playlist listing][w-auth] includes account-stored playlists; Android reads them through its authenticated playlist method. It has no public playlist/deep-link flow or external-playlist subscription method, and exposes edit/delete controls without distinguishing an external saved playlist from an owned native playlist. The server limits what those mutations can do. Mix IDs redirect in [the common API][w-playlist-api], while Android's HTTP client disables redirects and has no mix API method.
3. **Feed settings now have native controls:** [shared server feed rules][w-users] are applied by the authenticated feed endpoint. Android no longer overrides account page size with 30. Pending-notifications-only filters the returned notifications even when the web endpoint falls back to ordinary feed videos; latest-only and notifications-only lists do not expose redundant pagination. Other feeds still merge notifications and ordinary items.
4. **Shared settings require the expanded PATCH API:** [the mobile PATCH endpoint][w-mobile] now validates supported native booleans, playback defaults, captions, appearance, browsing and feed values in addition to SponsorBlock. It merges changed fields under the account lock and preserves unknown preferences. The existing preference token scopes suffice; no migration or token renewal is added. Guests save native preferences per instance. Background playback and PiP remain device settings. Unsupported web capabilities have no native switches.

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
