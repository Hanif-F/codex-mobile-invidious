# Playback system

[Documentation index](README.md) · [Architecture](ARCHITECTURE.md)

## Service ownership and media session

`PlaybackService` is a foreground `MediaSessionService`. It creates one ExoPlayer
with a codec-aware selector, media/audio attributes, audio focus, becoming-noisy
handling and a partial wake lock. Its HTTP media source carries a Mobivious user
agent and no account cookies/bearer; cross-protocol redirects are disabled.
`QueueCoordinator` owns all loading and logical queue operations on the service
main scope. Compose connects with `MediaController` and does not own that player.

`player/PlaybackQueue.kt` owns the runtime snapshot, occurrences, repeat and
merge/successor rules, plus occurrence-aware share URLs. `data/Models.kt` retains
plain source-page/library models and `data/ContentLinks.kt` parses incoming links.
The data layer does not import player or UI types; queue wire fields are unchanged.

`QueueSessionPlayer` wraps ExoPlayer with a logical playlist while resolving only
the active item. Placeholder entries expose missing previous pages and future
source pages to system controls. The active real timeline is retained, including
live timing. `SessionTimeline` clears epoch window timing only on non-live
windows to satisfy Media3 session serialization. Unknown/overflowing durations
become `C.TIME_UNSET` rather than invalid microsecond values.

Trusted system or same-package controllers may connect. Custom app commands
require the same package. Queue commands validate server/account/generation and,
where applicable, queue token; selection also validates occurrence. SponsorBlock
commands use a separate media/token pair. Old controls cannot mutate a new session.

## Opening online content

1. The view model sends Queue Start with video/source/index/explicit time/link
   overrides or clip metadata. The coordinator pauses the previous item and
   creates a new token and context-owned snapshot.
2. It reads guest defaults or server preferences and optionally resolves a playlist
   or mix source page. A source failure can leave a known requested video playable.
   A source-only launch requires an eligible source occurrence.
3. It fetches VideoDetails, checks clip bounds/upcoming state and resolves media
   URLs against the selected origin. Stream preference is HLS, then DASH, then
   progressive fallback; a DASH manifest endpoint is synthesized when necessary.
4. Effective preferences combine defaults, link overrides and carried modes.
   Captions are configured as VTT; metadata contains occurrence identity and
   history/progress/SponsorBlock ownership flags.
5. It chooses start position, installs media, configures supported track policy,
   prepares and applies playing intent. Optional DeArrow/AI/segment work can run
   independently. The queue snapshot feeds UI and system metadata.

Start precedence includes explicit position/time, bounded-link start, then saved
resume as applicable. Saved resume within 20 seconds of the end restarts at zero.
An explicit time overrides resume. Live media retains its real live window.
Refresh buffer stops/prepares the same item, seeks to the default live position
for live content, and otherwise keeps position. Retry refetches expired stream
metadata and retains the requested playing intent and supported selections.

## Occurrences, source pages and successors

A queue entry has a unique occurrence key, video, optional source index, removed/
tail flags and optional clip/download/link playback. Source keys prefer playlist
occurrence IDs; locally inserted entries use UUIDs. The same video can appear
multiple times without collapsing positions. Removal marks an occurrence so the
currently playing item can finish while later selection skips it.

Play next inserts after the current entry. Add to queue appends a local tail.
If no live queue exists, insertion opens a paused explicit session. An implicit
standalone video does not show a queue panel merely because recommendations exist.
Explicit playlist/mix/manual queues do. Previous/next can fetch missing source
windows; page merges retain local inserts/removals and source ordering. Mix
continuations anchor new window identities and reject repeated/no-growth cursors.

Repeat Off/One/All is supported; All is unavailable for mixes. Automatic completion
of Repeat One restarts the occurrence. Finite Repeat All wraps to source start/end
as needed. Successors start automatically if `autoplay || continueAutoplay`.
Without an explicit queue, continuation to a recommendation additionally requires
`continueNext` and related videos. Unavailable/removed entries and disallowed
members-only items are ineligible. Automatic recommendations apply blocked/AI
filters; explicit user queues preserve deliberate library access.

Deleting an owned playlist occurrence calls the server with `indexId`, serializes
playlist writes and adjusts later source indexes. Removing a local queue entry
does not delete a server playlist. Queue state lives in the process/service, not
SQLite; process death is not a durable queue restore feature.

## Track selection

`StreamCatalog` combines supported Media3 tracks with unambiguous API stream
metadata. It excludes trick-play video tracks, displays resolution/fps/codec/
bitrate/size and groups audio into Original, Stable Volume, Dubbed and Other.
Repeated itags alone cannot establish audio identity; language/label/default/DRC
metadata disambiguate them. The quality menu limits each height/fps group to at
most four representative choices without pruning the selection catalog.

`VideoSelection` belongs to one occurrence and records Auto, Preset or Manual.
Auto keeps Media3 adaptation; for DASH a supported preferred codec restricts the
adaptive family. Preset selects the best supported stream at/below the requested
height, then prefers codec within that height. Best/Worst choose extremes. If no
preferred family exists, supported alternatives remain available.

Manual choices use a `StreamKey` containing representation identity and format
properties instead of retaining stale TrackGroup objects. Reload restores keys
when available; missing manual video falls back to captured defaults. The selector
applies capability/codec decisions before the first DASH video segment request.
Audio/text overrides are similarly reapplied after tracks become ready. Auto
audio clears explicit overrides and preferred language; caption Off disables text.
Online speed changes update the shared/guest default; offline speed changes do not.

## Link playback and bounded ranges

Accepted links include YouTube/youtu.be/nocookie variants, the selected instance
and the two configured Wingress hostnames. Video aliases include watch/w/v/e/
shorts/embed/live, playlists and mixes. Channel handles/custom/user names resolve
through the instance. Posts, hashtags and clips have distinct targets. Credential-
bearing/untrusted origins are rejected for native navigation; supported external
rich links use Android intents. Android auto-verified link filters cover only the
two declared hostnames; parsing more hosts does not register them all with Android.

Timestamps accept numeric seconds, decimal seconds, clock format, h/m/s/ms units
and supported query/fragment forms. YouTube playlist index is converted from one-
based; Invidious indexes stay zero-based. Link options include listen, speed,
local, autoplay, continue, continue_autoplay, loop, quality_dash, video_codec,
subtitles, region, comments, related_videos, extend_desc and save_player_pos.
Only listen/speed/local carry to successor occurrences. Saved preferences are
unchanged by link options.

Valid `end` is clamped to duration and enforced in the service. At the bound,
playback pauses without advancing, or loops to requested start when repeating.
Replaying restarts at start. Invalid end is ignored with explanatory UI state.
Sharing uses the current absolute time, source/index only for actual source
occurrences, optional end and effective share options. Rich timestamps pointing
to the current unbounded video seek the current occurrence instead of replacing it.

## Progress and history

First real online playback marks history once per transition when account history
is enabled. Failures can retry on later ticks. Position is persisted on pause,
seek/removal transitions and every 15 seconds while playing. Online position is
in seconds; ending or reaching the final 15 seconds clears it. Local fallback is
saved before account progress upload. Ownership must still match the original
context. Removing history and clearing history have separate repository rules.

Clip playback does not write ordinary full-video history/progress. Offline playback
writes `DownloadRecord.positionMs`, independent of account resume preferences;
ending/last two seconds resets that download position. Offline playback omits
recommendations/chat/storyboards and network-based tools. A completed media pair
uses `MergingMediaSource(true,true)`; only completed captions are attached.

## Chapters, discussions and replay

`ChapterRules` parses manual description lines with `m:ss` or `h:mm:ss`, optional
bullets/separators and a nonempty title. It rejects invalid seconds/minutes,
overflowing values, duplicate start times and starts at/after duration, then sorts
by start. At least two valid chapters and a known non-live duration are required;
there is no requirement that the first start be zero. The active chapter is the
last start at/before position. The chapter panel seeks the current occurrence;
it does not fetch automatic chapters or ordinary timeline-preview thumbnails.

Video and community-post comments have separate `CommentsController` instances.
Each target retains a main feed, independent reply feeds, sort and scroll positions.
Opening loads on demand; closing preserves loaded rows. Target/context changes
and sort revisions reject late results, while load-more failures retain existing
rows. Comment IDs deduplicate page merges, and a repeated continuation ends that
feed. Replies and the main feed have independent loading/error state.

`ChatReplayController` is a UI-session component and never owns play/pause.
Binding includes video, occurrence and ApiContext. Presentation visibility gates
requests; reopening selects the current playback position. Effective message time
is playback time minus saved delay. Seeking switches to a cached segment or starts
a new one, and responses from the previous generation are discarded. Only one
request runs at a time, with at least 800 ms between starts and about 30 seconds
of lookahead. Repeated cursors stop with a retryable explanation; 404 marks replay
unavailable without stopping media.

Replay keeps at most 24 segments and 5,000 cached messages, plus up to 5,000
removal IDs; each segment has a 5,000-cursor pagination guard. Removed messages
are purged, and replacement IDs update existing rows. The displayed window holds
up to 120 messages, with independent Earlier messages/Follow playback behavior.
Timing reads/writes use their own revisions and errors so a late read cannot undo
a newly saved delay. [Settings](SETTINGS.md#chat-replay-settings) describes filters
and appearance; [UI](UI.md#accessibility-and-verification) describes panel visibility.

## Gestures, presentation and power

`PlayerPresentationState` animates Closed/Mini/Watch/Fullscreen without replacing
service ownership. A single main PlayerView is moved/sized by the presentation
host and preserves decoded geometry. Axis rules reserve vertical watch/fullscreen
drags and mini horizontal dismissal; multi-touch/consumed gestures cancel safely.
Controls, timeline and panels take priority. Touch exploration uses explicit
controls/accessibility actions. The fullscreen top edge stays available to Android.

`SeekAccumulator` adds 10,000 ms per tap and commits after 600 ms idle. Opposite
direction resets the delta; accumulation captures the initial playing intent and
temporarily pauses. Competing actions, occurrence/context changes, gestures, PiP
and lifecycle transitions cancel pending work with the appropriate intent restoration.
Seeking clamps to duration, link end or clip-relative bounds.

PlayerView keeps the screen on only while a started lifecycle has active visible
video intent without error/end; audio-only and hidden surfaces release screen
wake ownership. The service's partial wake lock serves background media work.
PiP uses fitted decoded geometry clamped to Android's 1:2.39–2.39:1 bounds.
Automatic PiP requires the configured local preference and active watch playing
state. Explicit PiP uses the player command. Background disabled pauses outside
PiP; task removal stops a paused or background-disabled service. Destruction
closes the queue and releases controller/session/player resources.

## SponsorBlock integration

SponsorBlock runs in the service, evaluating at roughly 100 ms intervals. Valid
non-live seekable online videos fetch segments once per occurrence. Overlapping
Auto ranges merge to the furthest end; already-skipped Auto ranges offer manual
behavior on replay. Dismissal lasts within the range and resets after leaving it.
Markers/colors and brief skip confirmation are presentation state. Errors fetching
segments are non-fatal. Clips/downloads bypass segment work.

The clip editor uses a separate preview player with carefully scoped lifetime,
described in [Library](LIBRARY.md#clips).
