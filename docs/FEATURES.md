# Features and user workflows

[Documentation index](README.md) · [Settings reference](SETTINGS.md)

## Install and choose an instance

Install the release APK linked from the [project README](../README.md) on Android
13 or newer. Debug builds install separately as **Mobivious Preview**. Open
**You → Settings gear → Server**, enter an HTTPS instance origin and Connect.
The default is `https://invidious.wingress.net`. Changing servers signs you out
and closes playback. Existing device downloads remain in the library.

Guest browsing, streaming, local resume, blocking, optional viewing tools and
downloads do not require an account. Sign in with an **Invidious** account on the
selected instance to access its subscriptions, playlists, clips and shared history.
Native account flows and several enhancements need the fork's server extensions;
[API compatibility](API.md#compatibility-and-server-rollout) describes them.

## Navigation and discovery

Phones use four direct labeled destinations: **Popular, Trending, Subscriptions,
You**. A rail replaces bottom navigation from 600dp window width. Each root keeps
its stack and scroll position; reselecting a root scrolls to the top without a
refresh. Search opens a transient editor and returns to its origin. The
full-width opaque mini-player sits immediately above navigation.

Every search-icon tap starts with a blank, focused field. The editor shows local
recent searches and live instance suggestions instead of result filters. Tap a
suggestion/recent query to search immediately, or its arrow to edit it. Recent
queries can be removed individually or cleared with confirmation. Browsing settings
can hide/pause recents and disable live suggestions independently.

Search accepts words or a supported URL. Submit through the keyboard, physical
Enter or search action. Results preserve the server's ordering and may contain
videos, channels, playlists and mixes. Select All, Videos, Channels or Playlists
& mixes. Sort choices are Relevance and Views where supported; date/duration
filters apply to All/Videos. Search visibility can override members-only display
and include blocked channels. Closing search returns to its originating page.
Hidden results do not prevent loading subsequent raw pages. Tap the query to edit
it with its filters retained, or New search to start blank. New submissions reset
type/sort/date/duration; persistent visibility preferences are retained. Canceling
the editor leaves the previous page/results/position unchanged. Large video
thumbnails, circular-avatar channel cards and stacked playlist/mix covers make
mixed result types visually distinct without regrouping relevance order.

Channels open in the app. Advertised tabs can include Videos, Shorts, Streams,
Podcasts, Releases, Courses, Playlists, Clips, Posts and Channels. Unsupported or
absent tabs are omitted; a Videos fallback is used when none are advertised.
Community/live aliases normalize to Posts/Streams. Auto-generated channels do
not expose the generic Playlists tab. Video tabs support Newest/Oldest/Popular;
the age-gated fallback uses Newest. Generic playlist tabs support their own sort.
Scoped channel search preserves the selected tab for returning from results.

Creator identity can show a banner, avatar, verified badge, subscribers, handle,
pronouns, inventory and a rich description. Missing optional metadata stays hidden.
Descriptions and other rich text support native content links. Avatar images use
the instance proxy and fall back to a Unicode initial or generic icon.

## Watch and listen

Open a video to enter the expanded watch page. Tap to show controls, use Play/Pause
or Replay, drag the timeline or double-tap either side to seek by the selected
interval: 5, 10 (default), 20 or 30 video seconds, at every playback speed.
Repeated seek taps accumulate until a 600 ms quiet interval. The current chapter
and fullscreen controls sit above the full-width timeline. Loading, buffering,
errors and Retry remain actionable within the controls.

Dragging the timeline pauses playback and shows scene previews across the player,
using the sharpest available storyboard images. Releasing seeks once and restores
the previous playing/paused state; cancelling restores it without seeking. Tap
seeking shows the same previews, with cached frames following every tap and new
image-sheet downloads limited to one every 250 ms across tap bursts.
Saved clips use source-video images while retaining clip-relative times. Images
fit without stretching, and controls remain above them. Missing previews leave
seeking usable. Hidden thumbnails, offline/audio-only/live playback, miniplayer
and PiP do not request previews.

Player settings offer supported Quality, Audio and Caption tracks, speed from
0.25× to 2×, Audio only, Picture in picture and Refresh buffer.
The Captions menu and Settings → Playback share a caption appearance editor with
a native preview, text size, font, text/background/window colors and opacity,
edge styles and reset. Styling saves per profile and instance on this device,
applies immediately, and does not change the selected caption language.
Quality can be adaptive Auto or a specific stream. Audio groups distinguish
Original Audio, Stable Volume, Dubbed Audio and other tracks when metadata permits.
Options reflect decoder-supported tracks; unavailable choices are disabled.
Global playback defaults apply when opening later videos. Online speed changes
also update the saved speed default; most live track selections belong to the
current occurrence. [Playback](PLAYBACK.md) explains exact selection rules.

Swipe the expanded player down to minimize, and swipe the mini-player up or tap
it to restore. Swipe the mini-player sideways to close playback. A downward
fullscreen swipe first returns to the watch page. Visible controls and accessible
actions provide equivalent operations when touch exploration disables gestures.
The decoder and queue survive these presentation changes and activity recreation.

The video keeps its decoded aspect ratio, including portrait, square and ultrawide
content. Portrait watch scrolling can reduce the player's height as details move
up; scrolling back near the top expands it. Fullscreen hides system bars; the top
edge remains available for Android's reveal gesture.

Playback can continue in the background, with system media controls. PiP supports
back/forward by the selected seek interval and play/pause. Automatic PiP depends on the
device feature, local PiP setting and active watch/play state. Disabling background
playback pauses when leaving outside PiP. Audio only disables video decoding tracks.

## Watch information and discussions

The expanded page has Save, Download, Share and More actions. More includes Create
clip and DeArrow title contributions. Description expands inline and includes
rich links, clickable timestamps and available statistics/details: views, likes,
publication date, category, license, visibility, premiere/upcoming state, region
availability, family suitability and music credits. Only explicitly exact server
counts gain exact-total presentation; rounded or unverified counts stay qualified.

Manual chapters are derived from timestamp lines in the supplied description.
At least two valid chapters within a known non-live duration are required.
The chapter panel seeks without replacing the queue occurrence. Automatic
chapters remain unsupported; seek previews are independent of manual chapters.

Video comments load on demand when the entry is opened. Choose Top or Newest,
load additional comments, open replies and return to retained positions. Rows
may show verification, creator/pinned/member badges, hearts, likes, dates and
rich links. Comments are read-only; there is no post, like or moderation action.
The Show YouTube comments preference controls the video entry, not community post
comments. Closing a drawer retains its loaded session state.

Community Posts support single images, galleries, video/playlist attachments,
polls and quizzes. Long feed posts expand inline; a standalone detail page and
post share action retain channel context. The image viewer remains navigable
when an image fails. Post comments use their own controller and are read-only.
Poll/quiz choices are displayed, not submitted. Unknown attachments have a readable
unavailable state rather than discarding the post.

## Archived chat replay

Archived livestreams with a server `liveChatReplay` flag offer the player chat
button. This is read-only replay, not live posting. Messages follow playback and
seeks; scrolling away offers Earlier messages and Follow playback. Closing or
minimizing suspends replay requests while playback continues.

Use the chat menu for settings and overlay adjustment. Choose a docked panel or
movable/resizable overlay; Done saves overlay geometry and Cancel discards the
adjustment. Appearance, timestamps, user/word filters and per-video delay save
automatically, with Retry/Revert for failed changes. Users can be channel IDs or
`@handles`, and words can include `/RE2 patterns/`, separated by whitespace.
Positive delay shows messages later; allowed delay is ±3,600 seconds. Invalid
new regex is rejected; unsupported imported regex is reported and skipped.
Signed-in filters/timestamps/delay sync with the server; guest saves remain local.

## Subscriptions and library

**Subscriptions** shows account uploads with creator shortcuts, refresh, scoped
video search, a Channels directory and RSS/OPML actions. Feed settings control
page size, order, latest-per-channel, unwatched-only and pending-notifications-only.
The last option filters account feed content; it does not enable Android alerts.

**Channels** searches the loaded subscription names locally and sorts by Relevance,
Latest upload, Most watched or A–Z. The server supplies viewing/upload statistics;
missing statistics or history permission fall back to A–Z with an explanation.
Subscribe/Subscribed controls show pending state, preserve confirmed membership
on failure and offer explicit Unsubscribe. Guest subscribe intent can resume after
sign-in; canceling authentication clears channel intent.

**You** shows account/instance identity and links to Playlists, My Clips, History
and Downloads. Guests see all destinations, with sign-in prompts for account
pages and immediate access to Downloads. Account management is in Settings.

Playlists separates My playlists and Subscribed. Create an owned playlist with
public/private/unlisted privacy; edit its title/privacy/description or delete with
confirmation. Save a video to an owned playlist or create one from the save panel.
The default playlist is ordered first. Subscribe to external playlists/mixes to
keep their reference in the library; this does not make them editable copies.
Play, Listen, Play next and Add to queue feed the service queue. Removing a queue
entry and deleting a saved playlist occurrence are distinct actions.

History supports scoped title/channel search, server-calendar groups (Today,
Yesterday, Last 7 days, Last 30 days, Older), removal of individual entries and
confirmed clearing. Older flat history remains readable with organized search
disabled. Watched badges and progress bars reflect shared history and optional
resume positions. Removing a history entry preserves its saved position; clearing
history removes both. Guests can save positions locally, but have no shared history.

RSS support provides channel/public-playlist/mix links, a private subscription
feed link, OPML (`rss` or `newpipe`) and private owned playlist Atom exports.
Files can be shared or saved through Android. Private feed URLs contain access
tokens and are labeled accordingly. There is no in-app RSS reader.

## Clips and downloads

**Create clip:** open an eligible recorded video, choose More → Create clip,
sign in if necessary, give it a 1–140 Unicode-code-point title and choose 5–120
seconds within the video. Use tenth-second fields, filmstrip handles or the
independent preview, then publish. My Clips and channel Clips show public moments.
Clip playback uses a relative timeline and initially loops. Share/copy its link,
continue the full video at the same scene, or delete a clip you own. Native clips
belong to their original instance; foreign-instance links offer a browser action.
Active livestream clipping and editing published clips are not implemented.

**Download:** choose a separate video track, audio track or both, plus optional
captions. At least one media track is required. Entries show progress, details,
cancel, retry, play, Save to files and delete. Transfers permit metered and roaming
connections. Repeating Download creates another entry; Retry repairs the same
entry without redownloading completed assets. Active/upcoming streams and
server-restricted media are unavailable for download.

Saved media and artwork play offline through the usual player. Downloads remain
available after sign-out or server changes and have independent resume positions.
Deleting an entry deletes its app-specific files. Clearing app storage or
uninstalling removes the local library. Save to files creates a user-owned copy.
Single tracks/captions copy unchanged; a paired download first attempts an MP4
merge. If needed, **Convert and export** explicitly approves conversion of affected
tracks to H.264/AAC. Device capability failures are reported. See
[Library](LIBRARY.md) for recovery and export details.

## Personalization and community tools

Light, Dark or System appearance, Comfortable/Compact lists, hidden thumbnails,
default homepage, playback defaults and browsing filters are configurable.
Six device-wide accent presets accompany exact light/dark neutral Material 3
surfaces. Preferences save automatically, with field-level pending/error feedback,
Retry and Revert. Legacy Colored backgrounds and Adaptive control colors have no
interface effect. Watch actions use filled circles with labels below; portrait
transport stays centered and visible player controls use a 25% black dim layer.

Channel blocking hides discovery and recommendation content and, unless overridden,
search results. It preserves deliberate library/channel access. A brief Undo is
available after blocking. Guest blocks remain local; account blocks sync.

SponsorBlock optionally marks or skips community segments, with category colors,
Auto/Manual/Marker/Disabled modes and channel overrides. DeArrow optionally replaces
titles, retains an original-title control and permits authenticated title proposals
and votes after review. AI channel filtering uses instance-provided AiSList
Blocklist/Warnlist classifications, with independent actions per surface. Library
surfaces allow warnings only; downloads keep their saved artwork. Optional data
failures leave ordinary playback usable. [Settings](SETTINGS.md) records all defaults.

## Deliberate limits

Casting and Android upload alerts are not implemented. Clip-editor filmstrips
and full-player seek previews share storyboard loading support. The
client does not add YouTube account access, membership entitlements, uploads,
comment writing, poll voting or a local RSS reader. Queue shuffle and durable
queue restoration after process death are not implemented. Compatibility on
other instances varies; an APK update does not update the server.

## Hide a channel’s video titles and thumbnails

Use **Settings → Channel title and thumbnail settings**, or a channel/video
menu shortcut, to hide either or both for one channel. Optionally reveal uploads
after a number of days. Rules save automatically, share with the website when
signed in, and stay isolated per instance for guests. A hidden thumbnail reveals
on the first tap; titles have an explicit reveal cycle, including DeArrow when
enabled. Playback remains available. See [the settings reference](SETTINGS.md#channel-title-and-thumbnail-settings)
for expiry, unknown-date handling, offline downloads and reveal lifetime.
