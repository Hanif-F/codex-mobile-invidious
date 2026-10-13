# Settings reference

[Documentation index](README.md) · [Storage ownership](STORAGE_AND_SECURITY.md)

Open **You → Settings gear**. Most category pages edit a draft and require Save;
Cancel leaves saved settings intact. Failed saves keep the draft for retry.
Untouched drafts follow refreshed website preferences. Changed fields are patched
against the baseline, rather than replacing unrelated settings. Context checks
prevent saving a draft into a different account or instance.

**Shared** below means website/account settings when signed in and local
instance-specific guest settings where supported. **Profile-local** means saved
for the current account or guest on this device/instance. **Device-wide** means
independent of account and instance. Playback defaults normally affect the next
opened occurrence; current player controls are described separately below.

## Playback

| UI / model | Wire field | Default | Behavior / scope |
| --- | --- | --- | --- |
| Autoplay opened videos / `autoplay` | `autoplay` | true | Start an opened video. Shared. |
| Next recommendation / `continueNext` | `continue` | false | Standalone completion may select an eligible recommendation when related videos are enabled. Shared. |
| Autoplay next video / `continueAutoplay` | `continue_autoplay` | true | Successors play automatically when this OR opened-video autoplay is true. Shared. |
| Loop / `videoLoop` | `video_loop` | false | Initial Repeat One; clips initially repeat independently. Shared. |
| Audio only / `listen` | `listen` | false | Disable video tracks when opening media. Shared. |
| Proxy streams / `local` | `local` | true | Ask the instance to proxy streams. Shared. |
| Speed | `speed` | 1 | 0.25–2 inclusive; UI offers quarter steps. Shared. |
| Quality / `qualityDash` | `quality_dash` | `auto` | Auto, Best, Worst, 4320/2160/1440/1080/720/480/360/240/144p. Shared. |
| Codec / `videoCodec` | `video_codec` | `auto` | Auto, AV1 (`av1`), H.264 (`h264`). Preference applies to DASH. Shared. |
| Caption priority | `captions` | three empty strings | First available language among three priorities; empty means None. Accepts web English names or language tags. Shared. |
| Background playback | local `background` | true | Profile-local, saves immediately. |
| Picture in picture | local `pip` | true | Profile-local, saves immediately; device must support PiP. |

Invalid/non-finite saved speed becomes 1. Unsupported codec becomes Auto. A fixed
resolution selects the best supported representation at or below that height;
codec preference is resolved within that height. Auto remains bandwidth adaptive.
Defaults do not create tracks missing from the manifest or unsupported by the device.

## Appearance and browsing

| UI / model | Wire or local key | Default | Behavior / scope |
| --- | --- | --- | --- |
| Color mode / `darkMode` | `dark_mode` | empty / System | Empty, `light`, `dark`. Shared. |
| Colored backgrounds | `appearance.coloredBackgrounds` | false | Device-wide, immediate; enables blue/teal/purple page gradients. Off uses neutral light/dark page surfaces. |
| Adaptive control colors | `appearance.adaptiveControlColors` | false | Device-wide, immediate; lets glass controls switch light/dark style based on the backdrop. Off follows Color mode while retaining contrast tint. |
| List density / `uiDensity` | `ui_density` | `balanced` | Comfortable (`balanced`) or `compact`. Shared. |
| Hide thumbnails / `thinMode` | `thin_mode` | false | Text-focused video lists, also suppresses ordinary creator artwork surfaces. Shared. |
| Ambient mode | `appearance.ambientMode` | true | Device-wide, immediate; player toggle shares the same value. |
| Reduce transparency | `accessibility.reduceTransparency` | false | Device-wide, immediate opaque material fallback; ambient lighting is paused. |
| Show members-only / `showMemberVideos` | `show_member_videos` | false | Visibility only; grants no access. Shared. |
| Homepage / `defaultHome` | `default_home` | `Popular` | Empty=Search, Popular, Trending, Subscriptions, Playlists (You library). Guests choose Search/Popular/Trending. |
| Region | `region` | `US` | Two uppercase letters; picker uses the fixed content-region catalog. Shared. |
| Related videos | `related_videos` | true | Watch recommendations and standalone continuation eligibility. Shared. |
| Expanded description / `extendDescription` | `extend_desc` | false | Initial watch disclosure state. Shared. |
| Comments | `comments` | `["youtube", ""]` | UI toggles the YouTube entry; preserved other website values do not create a native Reddit comment implementation. Shared. |
| Trending category | local `discovery.category` | `livestreams` | `livestreams` or `gaming`; profile-local, immediate. |
| Search members override | local search `showMembers` | null | null inherits current shared preference; true/false explicitly override it. Profile-local. |
| Search include blocked | local search `includeBlocked` | false | Profile-local search visibility exception. |

Colored backgrounds and Adaptive control colors appear directly after Color mode.
Both start off when their device keys are absent, including existing installations.
They save without pressing Save and remain independent of account/instance changes.
Colored backgrounds does not change thumbnails, banners, artwork washes, accent
colors or the separate Ambient mode preference. Media-overlay controls retain
their consistently light treatment over video.

`feed_menu` is retained in the account JSON for website compatibility (default
Popular, Trending, Subscriptions, Playlists). It does not reconfigure the native
fixed Discover/Subscriptions/You/Search shell. Unknown homepage values resolve
to Popular. Launching signed-out into account-only destinations falls back safely.

## Subscriptions and history/library

| Setting | Wire / local key | Default | Behavior |
| --- | --- | --- | --- |
| Videos per page | `max_results` | 40 | Validated 1–1500; menu offers 10/20/30/40/60/100/200/500/1000/1500 plus the current value. Also governs history. Account feed controls require sign-in. |
| Feed sorting | `sort` | `published` | Published, title or channel name, each forward/reverse. |
| Latest per channel | `latest_only` | false | Account feed filter. |
| Unwatched only | `unseen_only` | false | Account feed filter. |
| Pending notifications only | `notifications_only` | false | Account feed selection, not Android upload notifications. |
| Directory sorting | local `subscriptions.sort` | `relevance` | Relevance, Latest, Most watched, Alphabetical. Profile-local; effective A–Z when statistics are unavailable. |
| Watch history | `watch_history` | true for accounts | Guests normalize to false. Marks playback in shared history. |
| Remember playback position | `save_player_pos` | false | Shared for accounts, local for guests. Turning it off clears local resume saves. |
| Default playlist | `default_playlist` | null / empty | Account-owned playlist ordered first in the save sheet. Guest value is empty. |

Server feed preference fields are sent through preferences rather than duplicated
as every feed request's query. Directory sorting is a separate local choice and
does not change the upload feed's order. A fallback does not erase the saved
directory sort. Guests normalize account-only filters to false.

## SponsorBlock

`sponsorblock_enabled` defaults to **false**. Every category initially uses
**Manual skip**. `sponsorblock_modes` and `sponsorblock_colors` are maps keyed by
category; colors are six-digit `#RRGGBB`. Modes are `auto`, `manual`, `marker`
and `disabled`.

| Category key | Label | Default color |
| --- | --- | --- |
| `sponsor` | Sponsor | `#4caf50` |
| `selfpromo` | Self-Promotion | `#ffeb3b` |
| `interaction` | Interaction | `#e91e63` |
| `intro` | Intro | `#00bcd4` |
| `outro` | Outro | `#2196f3` |
| `preview` | Recap | `#3f51b5` |
| `music_offtopic` | Music Offtopic | `#ff9800` |
| `filler` | Filler | `#9c27b0` |

`sponsorblock_channel_overrides` maps valid UC channel IDs to nullable enabled
overrides and category-mode overrides. Null/inherit falls back to the global
choice; reset removes the override. Account patches omit local channel names;
guest saves include bounded display names. Sparse map patches preserve unchanged
keys and use null for deleted channel entries. Guests retain at most 1,000 channel
overrides. The player uses effective settings for its current channel.

## DeArrow and AI filter

| Setting | Default | Details |
| --- | --- | --- |
| `dearrow_enabled` | false | Community title substitution; failed lookups retain original titles. |
| `dearrow_show_original` | true | Original-title affordance. |
| DeArrow private identity | server-managed | Signed-in import/clear; empty clears, otherwise 30–256 letters/digits/underscore/hyphen. Not stored as an Android preference. |
| `ai_filter_enabled` | false | Master switch pauses actions without deleting choices. |
| `ai_<list>_<group>_action` | `off` | Eight fields: blocklist/warnlist × feeds/search/recommendations/other_pages. |

AI actions are `off`, `hide`, `replace_thumbnail`. `other_pages` accepts only Off
or Replace thumbnails; it covers subscriptions, channel/scoped search, history,
playlists/mixes, clips and queues. Downloads remain independent. Hide wins across
active lists on hiding-capable surfaces; replacement prefers Blocklist when both
match. Legacy boolean choices are normalized. These are community classifications
served by the instance, not an on-device AI detector.

The AI settings page also shows list availability/staleness, channel counts and download dates, and
offers Refresh plus expandable technical details and source/attribution links.
These describe data supplied by the selected server; an unavailable list does
not turn an unresolved channel into a match.

## Chat replay settings

| Shared/guest field | Default | Limits |
| --- | --- | --- |
| `chat_show_timestamps` | true | Boolean. |
| `chat_user_blacklist` | empty | Whitespace-delimited channel IDs/handles; 1,024 UTF-8 bytes maximum. |
| `chat_word_blacklist` | empty | Literal substrings or `/regex/`; 1,024 UTF-8 bytes maximum. |
| Per-video delay / `offsetMs` | 0 | −3,600,000 to +3,600,000 milliseconds. Positive is later. |

Filter tokens are at most 128 characters. Matching is case-insensitive; regex uses
RE2/J (no lookaround/backreferences). Changed filters are validated before save.
Unchanged unsupported imported filters can remain while another field is saved.
Chat preferences use a dedicated PATCH endpoint; delay uses its own per-video API.

Appearance is profile-local and immediate: overlay=false, hide user IDs=true,
font scale=100% (25–300), opacity=75% (0–100), below fraction=.60 (.30–.70), beside
fraction=.35 (.10–.70), overlay x=.56/y=.05/width=.40/height=.75. Width/height clamp
to .10–1 and x/y clamp to the screen. Overlay-adjust drafts require Done;
Cancel discards that geometry draft.

## Server, account and About

Server edits the selected origin, initially `https://invidious.wingress.net`.
Connect validates an HTTPS origin (debug additionally permits loopback HTTP),
signs out the old account, clears its active playback/navigation and loads the
destination guest profile. Local downloads remain available. See
[Storage](STORAGE_AND_SECURITY.md#network-and-url-boundaries) for URL validation.

Account and its child pages provide authentication, credentials, sessions, API
tokens and deletion rather than preference fields; see [Library](LIBRARY.md#accounts-and-authentication).
Blocked channels opens the confirmed block-list manager. About displays the
build version, Invidious/Companion attribution, Backdrop source/license, and
community-data attribution/license links. It has no update installer or setting
that changes server capabilities.

## Current player overrides and precedence

For a new online occurrence, defaults are combined in this order: base preferences,
current link options, then carried session options (listen/speed/local). A null
link option inherits; false and empty subtitle/comment lists are explicit values.
These link options never patch saved account defaults. Ordinary quality/audio/
caption menu selections belong to the occurrence and are restored by stable
representation identity after reload when still available. Online speed changes
also update the saved default. Offline playback does not rewrite online defaults.

Server/account/local save boundaries and legacy migration are specified in
[Storage and security](STORAGE_AND_SECURITY.md); playback application is detailed
in [Playback](PLAYBACK.md).
