# Settings reference

[Documentation index](README.md) · [Storage ownership](STORAGE_AND_SECURITY.md)

Preferences save automatically from **You → Settings**.
Choices save immediately, sliders on release, and valid text after 500ms, blur,
Done, page exit or backgrounding. Fields retain pending, invalid or failed input
with Retry/Revert; the Settings root links to changes needing attention. Sparse
patches preserve unrelated website fields. A ViewModel-owned revisioned pipeline
serializes writers and rejects stale account/instance context. Account/security,
identity import, destructive operations and entity editors retain explicit submit
buttons. [Testing](TESTING.md) describes the relevant checks.

**Shared** below means website/account settings when signed in and local
instance-specific guest settings where supported. **Profile-local** means saved
for the current account or guest on this device/instance. **Device-wide** means
independent of account and instance. Playback defaults normally affect the next
opened occurrence; current player controls are described separately below.

## Instance access

Instance access prompts offer native sign-in or an embedded verification page.
Cancel/Later leaves Server settings and local Downloads reachable; retry the
failed action to reopen the prompt. Verification passes are instance-specific and
independent of the account. CAPTCHA, registration, required-login and Turnstile
administration remain on the instance website.

## Opening links

**Settings → Connection & app → Opening links** shows how many of the eight
supported YouTube domains Android currently opens in this installed Mobivious
variant. The count refreshes when returning from Android settings; these choices
belong to Android and are independent of the active account or instance.

1. In Android app settings for YouTube, turn off **Open supported links**. Do the
   same for YouTube Music if it handles `music.youtube.com`.
2. Select **Open Android link settings** in Mobivious, enable **Open supported
   links**, and select the YouTube addresses under **Add link** or **Supported web
   addresses**. Android's exact labels vary by device.

The shortcut opens this installed package's Open by default page, falling back to
app details if the direct page is unavailable. Ordinary supported video, playlist,
channel, post, hashtag and clip URLs can then open in Mobivious. Homepage/search
URLs, apps explicitly launching the YouTube package, and browser navigation that
stays inside its own page are outside this routing. YouTube domains cannot be
verified as Mobivious-owned websites; selection is manual. See
[Android's domain association documentation](https://developer.android.com/training/app-links/verify-applinks).

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
| Seek interval / `seekSeconds` | `seek_seconds` | 10 | 5, 10, 20 or 30 video seconds. Shared; applies to the next seek without reopening playback. |
| Quality / `qualityDash` | `quality_dash` | `auto` | Auto, Best, Worst, 4320/2160/1440/1080/720/480/360/240/144p. Shared. |
| Codec / `videoCodec` | `video_codec` | `auto` | Auto, AV1 (`av1`), H.264 (`h264`). Preference applies to DASH. Shared. |
| Preferred audio / `preferredAudio` | `preferred_audio` | `original` | Original Audio, Original Audio (Stable Volume), or a supported language/region tag. Shared. |
| Legacy annotations / `annotations` | `annotations` | false | Show archived overlays. Shared; current occurrence/menu and link overrides take precedence. |
| Annotations on subscriptions / `annotationsSubscribed` | `annotations_subscribed` | false | Enable overlays for subscribed channels once the current account directory is loaded. Shared. |
| Automatic 360° viewing / `vrMode` | `vr_mode` | true | Automatic uses supplied equirectangular/EAC metadata. Shared. |
| Caption priority | `captions` | three empty strings | First available language among three priorities; empty means None. Accepts web English names or language tags. Shared. |
| Caption appearance | local `caption.appearance` | device font/color/edge, 100% size and text opacity, transparent backgrounds | Profile-local editor in Playback settings and the player's Captions menu; size, eight colors, three opacities, seven font families and five edge styles. Changes apply immediately, including offline playback. Reset restores defaults; failed saves offer Retry/Revert. |
| Background playback | local `background` | true | Profile-local, saves immediately. |
| Picture in picture | local `pip` | true | Profile-local, saves immediately; device must support PiP. |

Invalid/non-finite saved speed becomes 1. Unsupported codec becomes Auto. A fixed
resolution selects the best supported representation at or below that height;
codec preference is resolved within that height. Auto remains bandwidth adaptive.
Defaults do not create tracks missing from the manifest or unsupported by the device.

Invalid or absent `seek_seconds` values become 10. Accounts use existing shared
preferences; guests save on this device per instance. The service receives the
interval through Queue Settings for media/PiP controls. A tap batch captures its
interval until the 600 ms commitment; the next batch uses refreshed settings.
Web account changes arrive on the existing refresh schedule, without live push.

Preferred audio selects the highest-bitrate supported matching track. General
languages can match regional tracks; regional preferences require an exact match.
Missing choices fall back to regular original audio, or retain the playable
default when original identity is unknown. Playback defaults apply to the next
occurrence. In-player manual audio lasts for the current occurrence, including
refresh/retry. The Audio menu displays and checks the active track directly;
there is no Audio Auto option. Guests save per instance.
Progressive sources retain embedded audio; downloads keep their saved tracks.

## Appearance and browsing

| UI / model | Wire or local key | Default | Behavior / scope |
| --- | --- | --- | --- |
| Color mode / `darkMode` | `dark_mode` | empty / System | Empty, `light`, `dark`. Shared. |
| Accent preset | `appearance.accentPreset` | `blue` | Device-wide; blue, teal, green, violet, amber, rose. Unknown IDs use blue. |
| List density / `uiDensity` | `ui_density` | `balanced` | Comfortable (`balanced`) or `compact`. Shared. |
| Hide thumbnails / `thinMode` | `thin_mode` | false | Text-focused video lists, also suppresses ordinary creator artwork surfaces. Shared. |
| Show members-only / `showMemberVideos` | `show_member_videos` | false | Visibility only; grants no access. Shared. |
| Homepage / `defaultHome` | `default_home` | `Popular` | Empty=Search, Popular, Trending, Subscriptions, Playlists (You library). Guests choose Search/Popular/Trending. |
| Region | `region` | `US` | Two uppercase letters; picker uses the fixed content-region catalog. Shared. |
| Related videos | `related_videos` | true | Watch recommendations and standalone continuation eligibility. Shared. |
| Expanded description / `extendDescription` | `extend_desc` | false | Initial watch disclosure state. Shared. |
| Comments | `comments` | `["youtube", ""]` | UI toggles the YouTube entry; preserved other website values do not create a native Reddit comment implementation. Shared. |
| Trending category | local `discovery.category` | `livestreams` | `livestreams` or `gaming`; profile-local, immediate. |
| Search members override | local search `showMembers` | null | null inherits current shared preference; true/false explicitly override it. Profile-local. |
| Search include blocked | local search `includeBlocked` | false | Profile-local search visibility exception. |

Accent selection does not change neutral surface roles or server Color mode.
Legacy Colored backgrounds and Adaptive control colors keys
remain dormant; neither changes the new interface. Player controls retain white
foregrounds over one 25% black dim layer while controls are visible.

`feed_menu` is retained in the account JSON for website compatibility (default
Popular, Trending, Subscriptions, Playlists). It does not reconfigure the native
fixed Popular/Trending/Subscriptions/You shell. Unknown homepage values resolve
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

**Show original title** (`show_original_title`, default **true**) is under
Browsing → Video pages. It prefers non-translated titles supplied by the server;
missing or unavailable originals keep the upstream title. Accounts share the
choice with the website; guests save it for their current instance on this device.
It is independent of DeArrow: community titles can still replace the selected
title, and spoiler rules can still hide it. Custom clip and playlist names and
existing downloaded metadata are preserved.

Changes apply when content is refreshed or reopened. Existing player titles remain
until metadata is reloaded (Retry/reopen), preserving playback state when the setting
is saved. A buffer-only refresh reuses its loaded metadata. Reopened navigation snapshots fetch the new choice; loading another
browsing page after a change restarts at page one to avoid mixing title choices.
Account failures use the usual Retry/Revert feedback and retain the confirmed
choice until a save succeeds.

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
build version, Invidious/Companion attribution and community-data attribution/license
links. It has no update installer or setting
that changes server capabilities.

## Current player overrides and precedence

For a new online occurrence, defaults are combined in this order: base preferences,
current link options, then carried session options (listen/speed/local). A null
link option inherits; false and empty subtitle/comment lists are explicit values.
These link options never patch saved account defaults. Annotation and automatic
360° defaults apply to the active video unless a link or menu overrides them.
Player settings → More → Annotations offers a per-occurrence override and Use playback default.
More also contains Audio only, Picture in picture, Refresh buffer and Playback statistics.
Statistics is a session toggle: its compact/expanded overlay survives queue changes
and recreation, temporarily hides in mini-player/PiP/background/settings, and resets
its measurements for each source. Details includes Copy diagnostics; no preference
or account setting is saved. Viewing mode
appears only for supported VR video metadata, including while using Flat mode.
Viewing mode, device motion (initially off), yaw/pitch and dismissed annotations
are session state; they survive Activity recreation and reset on occurrence changes.
`annotations` overrides `iv_load_policy` (`1` on, `3` off); `vr_mode` is a boolean
link override. These options remain local to their linked occurrence. Ordinary quality/audio/
caption menu selections belong to the occurrence and are restored by stable
representation identity after reload when still available. Online speed changes
also update the saved default. Offline playback does not rewrite online defaults.

Server/account/local save boundaries and legacy migration are specified in
[Storage and security](STORAGE_AND_SECURITY.md); playback application is detailed
in [Playback](PLAYBACK.md).

## Channel title and thumbnail settings

Open **You → Settings → Preferences → Channel title and thumbnail settings**,
or the shortcut in a channel or video action menu. Select a channel by
canonical `UC…` ID or `/channel/UC…` URL. **Hide title** and **Hide thumbnail** save
immediately; **Reveal after this many days since upload** saves valid text after
500ms. Blank means indefinite hiding. Invalid delays remain editable with Revert;
failed saves offer Retry/Revert. Reset or disabling both switches removes the rule.
There are at most 1,000 configured channels; delays are integers 1–2,147,483,647.

Signed-in rules use `channel_spoiler_settings` and are shared with the website.
Guest rules stay on this device for the selected instance and are never copied
into an account. Rules apply to browsing, history, playlists/mixes, recommendations,
queues, clips, downloads, watch/mini-player titles and static artwork. Media-session
notifications and passive action/dialog/accessibility labels respect hiding.
Transfer notifications use the generic “Mobivious download” title.

Tap a hidden thumbnail once to reveal the original image, then again to open it.
The blue title control cycles Hidden → DeArrow → Original → Hidden, or Hidden →
Original → Hidden when no replacement is available. Hidden titles stay hidden
while a requested replacement loads; the cycle remains available when “Show
original titles using the DeArrow icon” is disabled. Independent displayed copies
reveal separately and reset on navigation/refresh. Watch and mini-player share the
current playback occurrence’s reveal state with media-session metadata.

Upload age uses source publication metadata, with midnight UTC for date-only
values and exact timestamps otherwise. Missing dates can use the instance’s
batched cache-only publication endpoint; errors and misses stay unknown. Unknown
uploads stay hidden and are labeled “upload date unknown”. Clips use their source
video’s upload age. Downloads use locally stored dates without new network
lookups. Expiry is checked on loading/refresh and rule changes, without a timer
changing an open screen. Account-rule loading uses safe placeholders; read failures
can be retried in this settings page. Rules change presentation, preserving raw
metadata, playback, downloaded files and descriptions. An active thumbnail rule
wins over an AI warning image, and explicit reveal shows the original image.

### Search assistance on this device

Browsing includes Save recent searches and Live search suggestions, both enabled
by default and scoped to the active account/guest and instance. Disabling recents
hides them and pauses saving; Clear all removes saved terms after confirmation.
Live suggestions use the selected instance and region. These controls are local
and do not modify shared account watch history or website preferences.
