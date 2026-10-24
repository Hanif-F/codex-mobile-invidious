# API and instance compatibility

[Documentation index](README.md) · [Troubleshooting](TROUBLESHOOTING.md)

This is the Android client's consumed contract, derived from `InvidiousApi.kt`
and its models. The sibling server's `docs/mobile-api.md` is the server-side
reference when that independent checkout is available. Paths below are relative
to the selected origin. `:id` placeholders are not literal path text.

## Transport and authentication

The instance must be an origin without a path, query, fragment or credentials.
Release requires HTTPS. Debug additionally accepts HTTP localhost, 127.0.0.1 or
10.0.2.2. Query parameters use OkHttp URL encoding. The default API client uses
15-second connect and 30-second read timeouts and disables HTTP/HTTPS redirects.
Non-GET operations also disable retry-on-connection-failure to avoid automatic
mutation replay. `HttpCall.awaitBody` cancels the socket when its coroutine cancels.

Requests default to `Accept: application/json`; XML/feed methods override it.
POST/PUT/PATCH send JSON bodies (an empty object when no fields are supplied).
Valid same-instance bearer authorization also accompanies content and media
requests so private instances can enforce required sign-in. Verification uses
the separate `X-Invidious-Verification` header. Bootstrap login, registration and
verification requests omit stored bearer/verification headers and browser cookies.
Every request is fenced against a changed live context before accepting its body.

## Instance access gates

`GET /api/v1/verification` discovers Turnstile policy and checks an existing pass.
Only 404/405 mean unsupported; outages remain errors. The embedded same-origin
`/verify?client=native` page completes the challenge through the server and emits
`invidious-verification-complete`. Android validates the origin/main frame, checks
the returned pass against discovery, then encrypts it locally. It never exchanges
account cookies with the WebView. Registration discovery additionally parses
`loginRequired`, default false for older servers.

HTTP 401 `login_required` opens native sign-in. HTTP 403 `verification_required`
or `verification_expired` opens verification automatically in the foreground;
503 `verification_unavailable`
offers retry without expiring the account or serving a cached protected feed.
Successful verification retries browsing, including a failed pagination request.
Mutations, playback and downloads retain explicit Retry actions.
All transports strip credentials on external redirects, matching scheme/host/port
on each hop. Same-instance HEAD and Range requests retain their access headers.

## Public discovery, channels and discussions

| Method/path | Parameters / output |
| --- | --- |
| GET `/api/v1/popular` | Array of video rows; single discovery snapshot. |
| GET `/api/v1/trending` | `region`, `type=livestreams|gaming`; array of videos. |
| GET `/api/v1/search` | `q`, `page`, `sort=relevance|views`, `type=all|video|channel|playlist|movie|show`; `date`/`duration`/`features` only for All, Videos, Movies and Shows. Duration supports `medium`; features use a comma-separated canonical list. Ordered mixed array with raw source keys. |
| GET `/api/v1/search/suggestions` | Public `q`, `region`; `{query, suggestions: [string]}`. Optional, uncached assistance; unavailable instances retain manual search. |
| GET `/api/v1/videos/:id` | `local=true|false`, optional link `region`; VideoDetails and stream/caption metadata. |
| GET `/api/v1/channels/:id` | Channel metadata, advertised tabs, banner/avatar and optional fields. |
| GET `/api/v1/channels/:id/videos`, `/shorts`, `/streams` | Optional opaque `continuation`, non-default `sort_by`; `{videos, continuation}`. |
| GET `/api/v1/channels/:id/playlists`, `/podcasts`, `/releases`, `/courses` | Opaque continuation; only playlists sends `sort_by=last|newest|oldest`; `{playlists, continuation}`. |
| GET `/api/v1/channels/:id/channels` | Opaque continuation; `{relatedChannels, continuation}`. |
| GET `/api/v1/channels/:id/posts` | Opaque continuation; root author and community rows/attachments. |
| GET `/api/v1/channels/:id/search` | `q`, numeric `page`; video array, page heuristic ≥20 raw rows. |
| GET `/api/v1/resolveurl` | `url` for validated channel alias; accepts valid `ucid` or `browseId`. |
| GET `/api/v1/hashtag/:tag` | Numeric `page`; `{results}` videos, continuation heuristic ≥60 rows. |
| GET `/api/v1/post/:id` | Optional `ucid`; matching post extracted from response rows. |
| GET `/api/v1/post/:id/comments` | `ucid`, `sort_by=top|new`, optional continuation. |
| GET `/api/v1/comments/:id` | `source=youtube`, `sort_by=top|new`, optional continuation; comments/count/reply cursors. |
| GET `/api/v1/live_chat/:id` | `offset_ms` ≥0, optional continuation ≤4,096 UTF-8 bytes; `{messages, removedIds, continuation}`. |

Video IDs are 11 URL-safe characters. Channel IDs use `UC` plus 22 URL-safe
characters. Playlist IDs are 1–100 URL-safe characters; post/clip IDs have their
own validators. Optional numbers are validated as non-negative, finite values;
unknown counts/dates do not become invented zero/exact values. `viewCountPrecision`
and `likeCountPrecision` distinguish explicitly exact from rounded/unverified totals.

Per-video `viewCountType` distinguishes cumulative `views` from concurrent
`watching`. A non-negative `viewCount`, including zero, with precision `unknown`
is usable when a recognized type is supplied; without that type, `unknown` marks
an unavailable count. Legacy numeric counts without precision remain unverified.
Numeric counts use device-local compact formatting. Older text-only
`viewCountText` values support English labelled counts and bare numeric/K/M/B/T
counts; they never become exact totals. Placeholders, ambiguous bare zero and
untyped live counts stay hidden. No count-only metadata requests or count cache
are added. Improved playlist/history/recommendation availability depends on the
server supplying those fields; playlist aggregates and clip statistics are separate.

VideoDetails includes description/plain HTML, video metadata, dash/hls/fallback,
captions, recommended videos, adaptive/format streams, optional likes/credits,
availability/premiere/region flags, `liveChatReplay` and storyboard tracks.
Missing fields preserve compatibility. Replay requires archived, non-upcoming
content and an explicit availability flag. Comments/replies preserve their own
continuations and suppress repeated cursors. No discussion mutation API is used.

## Playlists, queues and feeds

| Method/path | Authentication / contract |
| --- | --- |
| GET `/api/v1/playlists/:id` | Guest public playlist; `index` is zero-based source position. |
| GET `/api/v1/mixes/:id` | Guest mix, optional video-ID `continuation`. |
| GET `/api/v1/auth/playlists/:id` | Signed-in playlist or mix resolution, private/ownership metadata; index or mix continuation. |
| GET `/api/v1/auth/playlists` | Account owned/saved collections; parser supports legacy ownership flags. |
| POST `/api/v1/auth/playlists` | `{title, privacy}` → created playlist ID/metadata. |
| PATCH `/api/v1/auth/playlists/:id` | `{title, privacy, description}` for owned playlist. |
| DELETE `/api/v1/auth/playlists/:id` | Delete owned playlist. |
| POST `/api/v1/auth/playlists/:id/videos` | `{videoId}` → occurrence metadata. |
| DELETE `/api/v1/auth/playlists/:id/videos/:indexId` | Delete occurrence by hexadecimal ID, not by video ID. |
| PUT/DELETE `/api/v1/auth/saved_playlists/:id` | Subscribe/unsubscribe reference; PUT may include `seedVideoId` for a mix. |
| GET `/api/v1/auth/feed` | Numeric `page`; `{notifications, videos}`. Client selects notifications-only if configured. |
| GET `/api/v1/auth/subscriptions` | `include_stats=true` for directory; rows with optional `subscriptionStats`. |
| POST/DELETE `/api/v1/auth/subscriptions/:id` | Subscribe/unsubscribe channel. |
| GET `/api/v1/auth/subscriptions/search` | `q`, `page`; subscription-scoped video search. |

The native logical queue resolves one ExoPlayer media item at a time. Finite
playlist pages use zero-based indexes through `queuePage`. Mixes follow video-ID continuations and detect
cycles/no-new-successor responses. Repeated video IDs remain distinct occurrences.

`subscriptionStats` contains nullable `latestUpload`, all-time distinct watched
count, recent watched count and a non-negative relevance score. Missing statistics
produce A–Z fallback; malformed provided statistics are an error. The exact
history-permission fallback message triggers a plain directory retry. Relevance
ranking is server-owned; the deployment/README describes its 90-day/seven-day
inputs. Android sorts returned numbers locally.

## Accounts, preferences, history and visibility

| Method/path | Contract |
| --- | --- |
| POST `/api/v1/mobile/login` | Public `{username,password}` → `{accessToken,username,expiresAt,profileId?}`. |
| GET `/api/v1/mobile/registration` | Public login/registration flags and optional captcha image/token. |
| POST `/api/v1/mobile/register` | Public username/password/passwordConfirmation/captchaAnswer/captchaToken → account response. |
| POST `/api/v1/auth/account/username`, `/password` | Current-password and new-credential form fields → replacement account/token response. |
| POST `/api/v1/auth/account/delete` | `{password}`; confirmed destructive account deletion. |
| GET `/api/v1/auth/account/sessions` | Sessions with id/type/issuedAt/expiresAt/current. |
| POST `/api/v1/auth/account/sessions/revoke` | `{id}`; revoking current session clears local authentication. |
| POST `/api/v1/auth/account/tokens` | `{password,scopes,expiresAt}` → token displayed transiently. |
| POST `/api/v1/auth/tokens/unregister` | Sign out/revoke current token; local sign-out happens even on network failure. |
| GET/PATCH `/api/v1/auth/preferences` | Full preferences / sparse changed-fields patch; GET may return `X-Invidious-Account-Profile`. |
| PATCH `/api/v1/auth/chat_preferences` | Dedicated chat timestamp/user/word preference patch → account preferences. |
| GET/PUT `/api/v1/auth/chat_timing/:id` | `{offsetMs}` for replay delay; PUT accepts ±3,600,000 ms. |
| GET `/api/v1/auth/history` | `details=true`, `organized=true`, `q`, `page`; organized entries/total/hasMore/today/timezone, or legacy flat array. |
| POST/DELETE `/api/v1/auth/history/:id` | Mark watched / remove history entry. |
| DELETE `/api/v1/auth/history` | Clear shared history (client clears local progress snapshot after confirmation). |
| GET `/api/v1/auth/playback` | `{watched: [ids], positions: {id: seconds}}`. |
| GET/PUT/DELETE `/api/v1/auth/playback/:id` | Read, `{position}` write in seconds, or clear. Missing saved position reads as zero on 404. |
| GET `/api/v1/auth/blocked_channels` | Confirmed `{authorId,author}` rows. |
| POST/DELETE `/api/v1/auth/blocked_channels/:id` | `{name}` when blocking / unblock. |

Native token permission groups are declared in `Accounts.kt`: preferences, chat,
subscriptions/feed/search, history/progress, clips, playlists, blocks, RSS, DeArrow,
token management and notification reads. Generated API tokens require an explicit
valid selection: 1–64 scopes, each ≤256 characters, using method-list/path syntax.
Expiry choices are 1/7/30/365 days or Never. A listed notification permission does
not imply Android upload notifications are implemented.

## Clips, downloads and community tools

| Method/path | Contract |
| --- | --- |
| GET `/api/v1/clips/:id` | Public `local=true`; native or upstream clip with absolute start/end seconds and source video. |
| GET `/api/v1/channels/:id/clips` | Public numeric page; 30-row page heuristic. |
| GET `/api/v1/auth/clips` | Account My Clips, numeric page. |
| POST `/api/v1/auth/clips` | `{videoId,title,startTime,endTime}` in seconds, preserving millisecond precision. |
| DELETE `/api/v1/auth/clips/:id` | Owned native clip only. |
| GET storyboard VTT URL | Public same-instance-host validation, text/vtt; shared lazy index cache for player/clip editor; same-origin image URLs and validated `#xywh` cues. |
| GET `/api/v1/videos/:id/downloads` | Public `{video,choices,allowed,reason}`; choices include key/kind/url/stream metadata. |
| GET `/api/v1/videos/:id/download?key=…` | Origin-bound catalog URL; DownloadManager for credential-free transfers, foreground OkHttp transfer for credentialed requests. |
| GET `/api/v1/sponsorblock/:id` | Public segments with id/category/start/end seconds. |
| GET `/api/v1/dearrow/:id` | Public optional title. |
| GET/PUT `/api/v1/auth/dearrow/identity` | Read readiness/configuration; import or clear `{privateId}`. |
| GET `/api/v1/auth/dearrow/:id/submissions` | Ranked title proposals with original/votes/locked/UUID. |
| POST `/api/v1/auth/dearrow/:id` | Submit/vote fields constructed by contribution action; no automatic mutation retry. |
| GET `/api/v1/ai/status` | Public list availability/staleness/count/version status. |
| GET `/api/v1/ai/channels` | Public `ids` batches ≤100, `lists=blocklist,warnlist`; channel matches/resolution plus list versions. |

Stream playback also consumes supplied HLS/DASH/media/caption URLs and, when
needed, `/api/manifest/dash/id/:id?local=…`. These are media transport, without the
account API bearer. Clip creation/editing constraints and download/export behavior
are described in [Library](LIBRARY.md).

## RSS and XML

| Method/path | Use |
| --- | --- |
| `/feed/channel/:id` | Public generated channel RSS URL. |
| `/feed/playlist/:id` | Public playlist URL; mixes include seed continuation. |
| GET `/api/v1/auth/feed/rss` | `{feedPath}` validated as same-origin `/feed/private?token=…`. |
| GET `/api/v1/auth/subscriptions/export` | `format=rss|newpipe`, Accept application/xml, OPML string. |
| GET `/api/v1/auth/playlists/:id/feed` | Private owned playlist Atom, Accept application/atom+xml. |

Private feed URLs and exported account data should be treated as access-bearing
user data. FileProvider shares only the app's RSS cache subtree with temporary
URI permission. The client does not fetch and aggregate RSS feeds for reading.

## Error and cached-results semantics

Ordinary authenticated 401 responses invalidate the session. Selected 403 expired/
unauthenticated messages do likewise. Wrong-current-password responses with
`code=invalid_password` do not sign out. Optional chat auth/scope errors retain
playback ownership and explain token renewal. Missing extensions/scopes receive
feature-specific update/sign-in guidance. 429 uses Retry-After. Other JSON error
messages are bounded to 300 characters; 5xx gets a retryable server message.

Empty or malformed JSON from the video metadata endpoint becomes a retryable
502-style client error: "The instance returned invalid video information. Try
again." Retry refetches metadata without an automatic retry loop. Successful
empty write responses retain their existing behavior. Missing storyboard previews
do not prevent playback when the server returns valid video metadata.

Only GET Popular, Trending, Search and authenticated Feed have disk snapshots.
Keys include the full URL and, for authenticated feeds, token identity. Network
IO/5xx failures may use a snapshot younger than 24 hours; 4xx errors never do.
Explicit root-feed refresh disables this fallback so failure retains the currently
displayed rows and reports Retry. Normal browsing keeps its existing cached-results
behavior; successful refresh responses still update the snapshot.
Successful cached-endpoint bodies are shape-validated before storage. Other reads
and all writes have no offline mutation queue. See [Storage](STORAGE_AND_SECURITY.md).

## Compatibility and server rollout

| Capability | Server requirement / fallback |
| --- | --- |
| Public browsing/playback | Standard compatible Invidious metadata/media APIs; optional fields can be omitted. |
| Preferred audio | Shared `preferred_audio` field and native PATCH validation from the matching server update; playback uses existing manifests/audio metadata, with manifest fallback. No migration or new scopes. |
| Seek interval | Shared integer `seek_seconds`: 5, 10, 20 or 30; default/fallback 10. Existing GET/PATCH preferences and scopes. Deploy server support before distributing Android. An older server omits the field and rejects unsupported saves through the existing update-required flow. No migration or endpoint. |
| Native login/registration/account management | Mobile/account extensions; old server explains update. New permission groups may require renewed sign-in. |
| History/progress, blocks, subscription search | Fork extensions and matching native scopes. Renew old tokens after scope additions. |
| Saved playlists/RSS | Fork APIs/scopes and migration 20 for per-account bookmarks/backfill. |
| DeArrow contributions | Fork APIs/scopes and the existing migration 13 identity table/key; this contribution update introduces no new migration or key rotation. |
| Clips | Clips extension and migration 19, plus native token/channel-tab updates; renew sign-in for old scopes. |
| Chat settings/delay | Dedicated endpoints and native token-scope update. Missing replay flag hides entry; sync failures preserve playback. |
| Downloads | Public catalog and stream download endpoints plus matching media proxy. No new native-download migration/token renewal. |
| AI filter | AI status/channel APIs and preference action support; server migration 22 supplies shared list/handle caches. Existing preference scopes suffice. |
| Subscription statistics | Matching server response extension and history-read permission; A–Z fallback retains directory. |
| Exact totals / cached Shorts metadata | Matching optional metadata update; otherwise omit unavailable exact/date/duration details. Existing migration 21 also supports the channel-avatar cache. |

These are requirements recorded by this repository, not a live capability probe
of any production instance. Install/update/migrate the sibling server separately
using [Deployment](../deploy/README.md). Public API availability does not by itself
prove the corresponding account permission or upstream content availability.

Preferred audio uses `original`, `original_stable`, or a canonical supported
language tag such as `id` or `pt-BR` in preference GET and sparse PATCH. Invidious
commit `2ea80795` supplies the web field; its native PATCH allowlist also needs
the accompanying extension. Older servers default missing values to original;
rejected account saves retain normal Retry/Revert feedback. Android builds and
local fixture tests need no deployed server update. Production account sync does.

## Original video titles

Backend commit `be5609ad` exposes `show_original_title` through preference GET and
boolean sparse PATCH, defaulting true when absent. Existing preference scopes
suffice; no renewed sign-in is required. Deploy server migration 24 before the
backend. Android requires no storage migration or direct YouTube access.

Android sends one explicit `show_original_title=true|false` query on supported
video-bearing reads: discovery/search/hashtag, channel listings, video metadata
and recommendations, history/feed, playlists/mixes, clips and download catalogs.
Public metadata endpoints do not read bearer-account preferences. A shared
context-bound preference source resolves the account choice before public reads,
uses guest storage when signed out, and rejects results from stale contexts.
Successful preference responses update that source; a delayed GET cannot replace
a newer successful PATCH. Failed initial synchronization defaults true; later
successful synchronization replaces that fallback.

The existing `title` response field contains the selected title. DeArrow and
spoiler behavior follows selection. Media/caption/storyboard URLs, comments,
DeArrow endpoints and mutations receive no added title query. Feed snapshot cache
keys include the selected query, separating both title choices. Generated public
channel/playlist RSS links include it, and private playlist Atom fetches send it.
Private subscription feed links retain their owner's server-side preference.
The server rejects invalid or duplicate query values with 400 and retains the
upstream title when an original is unavailable. Older servers may ignore the
read query; rejected preference saves retain existing update-required feedback.

## Channel title and thumbnail hiding

Account preference GET already includes `channel_spoiler_settings`. Native sparse
PATCH now accepts a map of changed canonical channel IDs to complete
`{hide_title: boolean, hide_thumbnail: boolean, reveal_after_days: integer|null}`
rules. Omitted delay means indefinite hiding; blank delays explicitly serialize
null. A null entry deletes that channel. The server owns channel names; native
patches cannot submit `name`. It merges under the existing account lock, preserves
other channels and unrelated JSON, and rejects malformed edits atomically. Existing
GET/PATCH preference scopes suffice, with no token renewal or migration.

`GET /api/v1/spoilers/publications?ids=videoId,videoId` is public and accepts 1–100
distinct 11-character video IDs. It returns
`{"publications":{"videoId":<Unix seconds or null>}}`, including null misses, with
`Cache-Control: no-store`. The server reads only existing video/channel caches
and never refreshes from YouTube. Android batches matching timed rules with missing
source dates and retains results/misses for the current content load. Missing or
older-server endpoints leave upload dates unknown and content hidden. Ordinary
public metadata retains its original values. Account saves require the companion
native API update; unsupported servers show the existing shared-settings error.


## Annotation archives and projection metadata

Public `GET /api/v1/annotations/:id` returns XML through the selected instance.
The app sends `Accept: text/xml` with instance access credentials when available, bounds the decoded body
to 2 MiB, cancels suspended/obsolete loads and disables DTD/external entities.
The sibling route preserves direct archive responses even without a redirect
header. 404 means no archive; other failures offer an independent Retry. Archives are
kept only for the current playback occurrence, and are not downloaded offline.

Video JSON adds optional top-level `projectionType`, also included in download
catalog metadata. `EQUIRECTANGULAR` selects spherical projection; `MESH` selects
YouTube EAC. Older responses can supply the existing adaptive-format field.
Absent or unknown metadata stays flat in Automatic mode; manual projection
selection remains available. The native app performs no YouTube extraction.

The native preference PATCH allowlist now accepts boolean `annotations`,
`annotations_subscribed` and `vr_mode`, using existing preference scopes and
storage. Deploy the sibling server addition before relying on account saves;
no migration or token renewal is needed. Older instances retain playback and
show their existing save errors with Retry/Revert.

Search feature values, in canonical order, are `live,4k,hd,subtitles,commons,360,vr180,3d,hdr,location,purchased` (one comma-separated query value without spaces
or newlines). Shows is a legacy upstream filter and can return no results.
