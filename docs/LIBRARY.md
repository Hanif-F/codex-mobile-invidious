# Accounts and library systems

[Documentation index](README.md) · [API](API.md) · [Storage](STORAGE_AND_SECURITY.md)

## Accounts and authentication

Native login/register capture the selected ApiContext before sending credentials.
Registration reads instance flags and optional captcha challenge; its answer/token
are submitted with password confirmation. Accepted responses provide bearer,
username, expiry and optional stable profile ID. Late responses from an old
context are discarded. Successful authentication restores the originating screen
and supported pending actions after account-scoped collectors reset state.

Settings → Account contains sign-in/out, username/password changes, sessions,
token creation and account deletion. Credential changes send current `password`
plus `username` or `newPassword`/`passwordConfirmation`, then replace the session
without losing compatible profile saves. Wrong current password is an inline
failure rather than a sign-out. Sessions show type, issue/expiry/current status;
revoking the current session clears local authentication. Account deletion requires
password and confirmation, purges that local profile and signs out. Downloads
remain device-owned. Logout attempts revocation and clears local authentication
even if the network request fails.

Token creation combines guided permission groups and optional advanced scopes,
requires password and chosen expiry, and displays the issued token for copy.
Passwords, captcha answers, imported private IDs and newly issued tokens are
transient form state; persistent session storage is covered in [Storage](STORAGE_AND_SECURITY.md).
Account operations expose busy state and disable conflicting actions.

## Subscriptions, sorting and visibility

`SubscriptionsController` owns confirmed membership, local name search, statistics,
sort and per-channel pending/errors. A membership change publishes only after
server confirmation, invalidates reads started earlier and refreshes statistics.
Multiple taps cannot send duplicate writes for the same channel. Returning from
sign-in can complete a guest subscribe intent only for its original instance;
canceled channel authentication clears that intent.

The upload feed and channel directory are separate views. Feed search is a server
search across subscribed content, independent of feed-only filters. Directory
name filtering/sorting is local. Latest uses nullable upload timestamp; Most
watched uses all-time distinct count; Relevance uses the server score. Name and
ID break ties. Missing stats/history permission fall back to A–Z; a failed refresh
retains loaded rows. History/member-preference changes refresh loaded statistics.

`BlockedRepository` stores confirmed blocks, uses local snapshots for fallback
and rejects stale reads/writes. Guest changes never call account block APIs.
Discovery/recommendations filter blocks; search does unless Include blocked is
set. Channel/subscription/playlist/history access remains deliberate. Membership
visibility filters all ordinary video lists except history, with a nullable search
override. Neither filter grants access or automatically stops an already-open video.
Pagination and merge always use raw source rows before visibility filters.

## Playlists, mixes and saving

My playlists and Subscribed have independent session positions. Ownership and
saved-reference state are separate flags. Public/private/unlisted owned playlists
support title/privacy/description changes and confirmed deletion. External saves
are read-only live references, not snapshots or new ownership. Mixes carry a seed
video for continuation and feed-link creation.

The Save sheet fetches only owned destinations and puts the configured default
first. Create and save is a two-stage operation: if creation succeeds and adding
the video fails, retry uses the already-created playlist. This prevents accidental
duplicate playlists on retry. Pending guest save/subscription state is rebound
only on the same instance after sign-in. Account/instance changes fence older
playlist reads and actions.

Source playback preserves duplicate video occurrences. Deleting from an owned
playlist requires its hexadecimal occurrence `indexId`; removing from the local
queue marks only the queue occurrence. See [Playback](PLAYBACK.md) for source-page
loading and repeat behavior. Queue reordering/shuffle are not implemented.

## History, progress and formatting

Organized history is server-owned: entries include `release_date` and
`latest_watched`, plus server `today`, `timezone`, `total` and `hasMore`. Groups
compare calendar dates against that server day. If a later page reports a new day,
the client reloads the first page instead of mixing calendar groups. Legacy arrays
retain basic viewing, including unavailable IDs, with organized search disabled.

`WatchedRepository` maintains watched IDs, optional positions and history revision.
Reads merge untouched entries while retaining newer confirmed writes; clear and
per-video generations stop old writes resurrecting removed history. Position writes
keep local fallback even when the server upload fails. Guests have progress only
when enabled, not shared watch history. Removing one history entry leaves progress;
clear removes both. Badges/progress bars hide unknown percentages for live/unknown
duration; known progress has a small visible floor and near-complete fill.

`DisplayFormats` centralizes localized compact audience counts, grouped inventories,
bytes/bitrates, clock times and dates. Unknown/negative/fractional/overflow numbers
are rejected rather than silently truncated. Publication timestamps outside the
YouTube-era range or more than five minutes in the future are hidden. Exact expanded
counts require explicit `exact` precision; legacy values are not promoted to exact.
Shorts dates/durations appear only if supplied by the server's existing response.

## Clips

`Clip` stores absolute source start/end in milliseconds; Media3 exposes a clipped,
relative playback window. Native IDs have `IVCL` plus 32 URL-safe characters;
upstream clip IDs use the broader validator. Native durations must be 5–120 seconds,
titles 1–140 Unicode code points. Creating requires a signed-in account and an
eligible finite recorded source: no active live/upcoming/members-only/unavailable
source or future premiere. Server validation remains authoritative.

Opening the editor pauses main playback and remembers its occurrence/context and
playing intent. It defaults to up to 30 seconds centered near the current scene.
Precise tenth-second fields and filmstrip handles modify a draft. Optional VTT
storyboards choose a track near 160px; `#xywh` sprite cues are parsed defensively.
Missing storyboards do not prevent timestamp editing. Clip preview has its own
ExoPlayer/TextureView and is released with its composition; it never replaces the
service queue. Closing restores main playing intent only for the same occurrence
and context. Publishing keeps failures editable and invalidates saved clip pages
after success.

Clip playback initially repeats one, disables ordinary full-video history/progress
and SponsorBlock, and validates the source still contains the bounds. Watch full
video converts relative current position to `clip.startMs + player.position` and
retains playing intent. Native ownership requires current origin plus creator
username; only owners can delete, with confirmation. A foreign native clip offers
Open in browser rather than silently changing instances. Clips are server records,
not offline media files. Published editing and active livestream clipping are absent.

## Downloads and recovery

`DownloadRecord` has UUID, originating server, original video JSON, creation time,
selected assets and independent `positionMs`. Assets store exact choice metadata,
filename, system transfer ID, state, bytes/total and error. States are Queued,
Running, Waiting, Complete, Failed and Cancelled. At least one media selection is
required; optional captions and same-origin artwork are additional assets.

Download choices and saved details use friendly codec labels shared with playback,
with separate primary track and supporting language/bitrate/size text. Raw format
metadata, exact selection keys and stored downloads are preserved. Missing codec
information says Unknown codec; an unfamiliar identifier remains visible.

Creation checks instance verification policy and journals the record in SQLite
before starting work. Credential-free downloads use DownloadManager;
credentialed downloads use ProtectedDownloadService with per-hop OkHttp credential
selection. Same-origin redirects retain credentials; external redirects strip them.
Managed attempts keep their owning API context in memory, never in the journal.
Changing identity cancels their active calls and fails queued work for explicit
Retry. Process death similarly marks unfinished managed assets retryable;
completed tracks remain available. Existing records default to DownloadManager.
Each attempt uses a new filename so asynchronous removal of an old transfer cannot
delete its retry. Transfers allow metered/roaming connections. The repository
reconciles on startup, roughly each second while active, and download broadcasts.
If death occurs between enqueue and saving the system ID, it can recover the ID
by matching the journaled local URI. Completed assets require a nonempty file and
matching known size. First validation checks VTT headers or expected-only video/
audio tracks with MediaExtractor. Missing system rows/files become retryable failures.

Retry skips completed/in-flight assets and repairs failed/cancelled selections;
it uses recorded catalog URLs rather than automatically substituting a different
track. Cancel removes incomplete transfers/files while retaining completed assets.
Delete cancels associated export/current offline playback and deletes only that
record and its directory. Repeating Download creates a separate UUID record.
The library is independent of account/instance profiles and survives sign-out.

A record becomes playable when every selected video/audio asset is Complete;
caption/artwork failure need not block media. Offline details use only completed
local artwork/captions, remove remote streams/recommendations/chat/storyboards and
keep original saved title metadata. Local video/audio are merged for playback.
No network metadata lookup is required to play a saved record.

Tap a playable entry's artwork, title, status or padding to open offline playback.
The trailing three-dot menu contains Details, Cancel/Retry when applicable, Save
to files and Delete. Incomplete entries retain their progress and menu actions
while playback is disabled. Details still opens the local panel, and spoiler
artwork/title reveals keep their own actions without starting playback.

## Save to files / export

Android CreateDocument supplies a `content://` destination. The foreground export
service owns one active request and a notification with progress/cancel/open.
Single media tracks or selected VTT files copy unchanged. A video/audio pair is
trimmed to their common duration and first transmuxed to one MP4. Unsupported
codecs or specific mux failures enter **Needs conversion**, bound to exactly that
record/asset/destination. Only explicit Convert and export may restart conversion.

Conversion changes affected tracks to H.264/AAC, with encoder fallback disabled.
Output validation requires both tracks, unchanged resolution, compatible known
frame rate and duration agreement within one second. Unsupported capabilities
fail rather than silently lowering resolution. Converted quality/bitrate can change.
The final copy checks its byte count. Cancel/error cleans partial destination and
temporary cache MP4. If a provider cannot delete, cleanup attempts truncation.

Export journal `download-export` retains pending request identity. After process
death, startup reports Interrupted, clears temporary/partial output and asks the
user to Save to files again; it does not silently resume transformation. API 35+
uses mediaProcessing foreground type; earlier supported devices use dataSync.
Timeout cancels work. User-exported copies have their own lifecycle and are not
removed with the app's original download entry.

## RSS and optional community metadata

RSS/OPML/Atom panels either expose validated feed URLs or fetch XML on demand.
Account/instance changes dismiss them and discard pending responses. Private
owned playlists export Atom instead of publishing a private feed URL. File sharing
uses the restricted RSS FileProvider directory; saving uses a document URI.

DeArrow title reads deduplicate with four concurrent slots and cache even missing
results until clear/invalidate. Contributions require ready server identity,
valid single-line titles ≤110 code points and all four review acknowledgements.
Submit sends action/title/confirmed; votes send action/original/uuid. Locked proposals
cannot be downvoted. An uncertain write is not automatically replayed; refresh
before retry. Accepted writes and failed post-write refresh are reported separately.

AI lookups batch up to 100 channels with four concurrent slots, five-minute entry
expiry and up to 10,000 channel identities. Page preparation waits at most two
seconds without canceling shared requests. Unresolved entries get one five-second
retry when lists are available; they remain visible. List version changes invalidate
older classification state. Known matches may continue to warn when refresh fails.
Settings distinguish hiding surfaces from warning-only library surfaces; saved
downloads are unchanged. Classifications are community-maintained and may be wrong.
