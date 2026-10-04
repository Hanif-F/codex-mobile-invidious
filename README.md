# Mobivious

A native Android client for your Invidious instance, built with Kotlin, Compose
and Media3. Invidious and Companion own all YouTube extraction and stream
resolution. The app uses their JSON APIs and server-proxied DASH/HLS streams.

`android/` contains the client. The sibling `../invidious/` directory is your
independent Invidious Git checkout; its small mobile API patch is documented in
`../invidious/docs/mobile-api.md`. Keep its origin/upstream remotes. Commit server
changes inside that repository; commit app
and deployment changes here. No remote pushes or production deployment are automatic.
Docker uses `../invidious` by default; set `INVIDIOUS_SOURCE_DIR` in `.env` for a
different checkout location. Android build and test scripts do not depend on it.
The old ignored `source/.git` is retained as a recovery copy of the original server
commits and is no longer used for builds.

## Build

Open `android/` in Android Studio. Install SDK platform 37, Build Tools 36.0.0 and
use JDK 17 or newer (the installed Android Studio JBR works). Gradle and library
versions are pinned. Local SDK settings, signing material and build output are ignored.
Validation results and repeatable emulator checks are recorded in `VERIFICATION.md`.

```sh
cd android
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest assembleDebug
```

Android 8+ is supported. Debug builds additionally permit HTTP to localhost and
the emulator's `10.0.2.2` for testing. Release builds require HTTPS.

The signed personal release is produced with `scripts/build-release.sh`; preserve
the ignored `android/keystore/` and `android/signing.properties` securely because
future APK updates must use the same signing key. See `deploy/README.md` for server
installation. The default app URL is `https://invidious.wingress.net`; set another
HTTPS address through Settings → Server. Previously saved instance addresses are preserved.

## APK releases

Download the signed APK from the
[latest GitHub release](https://github.com/Hanif-F/codex-mobile-invidious/releases/latest).
Release APKs include all supported CPU architectures and work on Android 8+.
The matching `.apk.sha256` file lets you check the download with `sha256sum -c`.

Releases are built and published manually from this computer. Signing keys stay
local. For each release, update `versionName` and increment `versionCode` in
`android/app/build.gradle.kts`, then run from the repository root:

```sh
# Match versionName for this release.
release_version=0.3.0
scripts/build-release.sh

# Confirm the APK's signature before publishing.
JAVA_HOME=/opt/android-studio/jbr "$HOME/Android/Sdk/build-tools/36.0.0/apksigner" \
  verify --print-certs "artifacts/Mobivious-$release_version.apk"

# Commit only the intended release changes, then publish that exact commit.
git add android/app/build.gradle.kts scripts/build-release.sh README.md
git commit -m "Prepare Mobivious $release_version release"
git push origin HEAD:main
git tag -a "v$release_version" -m "Mobivious $release_version"
git push origin "v$release_version"

# Write a short changelog and any server requirements into artifacts/release-notes.md.
gh release create "v$release_version" \
  "artifacts/Mobivious-$release_version.apk" \
  "artifacts/Mobivious-$release_version.apk.sha256" \
  --repo Hanif-F/codex-mobile-invidious --verify-tag --latest \
  --title "Mobivious $release_version" --notes-file artifacts/release-notes.md
```

The build script runs unit tests and release lint, then writes the versioned APK
and checksum into `artifacts/`. Preserve the existing signing key for every update.
Account features require the server extensions described in `deploy/README.md`.

## Features

Home popular/trending, filtered search and shared video links; channel browsing with
paginated Videos/Streams/Playlists tabs and automatic Streams selection for channels without uploads;
native account sign-in; subscription feed and subscribe/unsubscribe; playlists and
watch history; descriptions, captions, read-only comments and recommendations.
Channel pages and Subscriptions each have their own search field. Channel search
keeps the selected Videos/Streams/Playlists tab available; clearing search restores it.
Subscription search matches titles/channels across the cached subscription library.
History supports title/channel search, account-timezone date groups and saved
release/watch metadata, including unavailable videos. Search icons, the keyboard
Search action and physical Enter submit through the same handler; general Search
also retains pasted-link timestamp handling. Subscription search and organized
history require the sibling API update. After deployment, sign out and sign in for
the new subscription-search token scope; history needs no new scope or migration.
Channel avatars appear in channel/watch headers, subscription channels, comments,
video author rows, recommendations, history, queues and YouTube playlist creators.
Missing or failed images retain a themed initial or person icon. Thin mode omits
avatars, and a channel's own upload/stream lists avoid repeating its image.
Avatar images use only the selected instance's existing `/ggpht` proxy, with a
shared image cache and no credentials or redirects. No extra YouTube channel/video
metadata requests are added. Cached listings gain optional avatar URLs through the
sibling API update and existing migration 21; no new token scope is needed.
Older servers remain usable with available response images and placeholders.

The service-owned player supports adaptive streams, seeking, speed/quality/audio
selection, background playback, system media controls, mini-player, fullscreen,
audio only and picture in picture. The embedded and fullscreen player share fading
controls, accumulated double-tap seeking, and one gear menu for quality, audio,
captions, speed, audio only, picture in picture and Refresh buffer. Explicit URL timestamps override saved resume
positions. History and resume settings are shared with the website.

The taller browsing mini-player shows live video on the left and preserves playback
when opening or leaving the watch screen. Visible video playback and buffering keep
the screen awake in the watch player, fullscreen, mini-player and PiP; paused,
audio-only and hidden playback allow the normal screen timeout.

The quality menu selects exact supported representations with codec/FPS/bitrate
details. Audio choices distinguish original, stable-volume and dubbed streams
where metadata is available. Saved quality defaults include Auto, Best, 4320p
through 144p and Worst; player choices apply to the current video. Double tap
starts a ten-second skip, further taps add ten seconds, and opposite taps reset
the direction. Playback pauses until a single jump 600 ms after the last tap,
then restores its previous playing or paused state. Reliable audio labels use the
additive video API metadata update; older instances retain manifest-based choices.

Optional DeArrow titles appear throughout browsing and playback, with an original-title toggle. The DeArrow Title button beside Share opens a native watch-page sheet for title suggestions, all four guideline acknowledgements, voting and refresh. Signed-in settings and the encrypted contribution identity are shared with the website; Settings also supports private-ID import. Guests keep local title settings per instance. Contributions require the server API update and a fresh sign-in token.

SponsorBlock matches this fork's eight categories, automatic/manual skipping,
marker-only and disabled modes, custom colors and per-channel inheritance. Colored
timeline ranges and scrub labels complement Skip/Dismiss prompts. The playback
service handles skipping during background, audio-only and PiP playback, and offers
manual controls when replaying an automatically skipped segment. App Settings opens
a full SponsorBlock screen with channel submenus. The player gear opens a settings
sheet; the channel header's three-dot menu opens the channel editor. Signed-in settings are shared with the website through the updated
preference API; guests save global settings locally per instance. SponsorBlock is
off by default, with manual modes. Active livestreams are excluded. Native runtime
and layout checks remain unverified because the installed emulator crashes before
Android boots; see `VERIFICATION.md`.

Settings has its own screen, with dedicated Playback, Appearance, Browsing, Subscriptions,
History & library, SponsorBlock, DeArrow, Server and About screens. Supported web
preferences sync with the account using sparse updates. Guests save preferences per
instance, including local resume. Playback defaults include autoplay, audio only,
proxy streams, speed, resolution ceiling and three caption-language priorities.
Appearance supports light/dark/system, compact lists and hidden thumbnails. Browsing
includes homepage/navigation priorities, region and video-page visibility; feeds
include page size, sorting and filters. The default playlist appears first in Save.
Background playback and PiP save immediately on the device. The expanded shared
settings need the sibling settings PATCH API deployed; no migration or new scopes
are required. Native runtime/layout checks remain pending a working emulator.
Chat replay, clips, downloads, casting and upload notifications are
outside this first release. Discovery uses Invidious popular/trending feeds.

Browsing settings also controls members-only visibility, off by default. Signed-in
users share this preference with the website; guests save it per instance. Search
has a separate saved visibility override with Use browsing default, plus Include
blocked channels for signed-in users. Members only labels appear on visible cards
and watch metadata. These controls filter lists; they do not provide membership
access. History and direct links remain available.

Channel blocking requires sign-in and shares the website’s block list. Block or
unblock from video-card actions, watch pages and channel headers, or use Settings →
Browsing → Blocked channels. Blocks hide discovery, search and recommendations;
subscriptions, playlists, history and direct channel/video access remain available.
Confirmed block lists are saved per account/instance for offline filtering. Neither
blocking nor visibility changes interrupt playback. Deploy the sibling metadata,
preference and blocking API update; sign out and sign in again for the new blocking
scopes. Native runtime/layout acceptance remains pending a working emulator.

Video actions now include Save to playlist, inline Create and save, Audio mode,
Play next, Add to queue, channel navigation and Block/Unblock with Undo. Save retains
the selected video through sign-in and retries a failed add without recreating its
playlist. Watch on YouTube and Switch Invidious instance actions are excluded;
Settings → Server still configures the app's instance.

Frequent actions use visible buttons and labeled chips. Channel menus group RSS,
blocking and SponsorBlock settings; playlist menus group RSS, editing and deletion.
Subscriptions has an RSS/OPML menu, and History has a Clear watch history menu.
The watch action row includes Playback queue and a blocking menu. Queue item menus
distinguish Remove from queue from Remove from playlist. Settings selectors show
their label and current value, and descriptions expand through an arrow row.

Public playlist/mix links open for guests; watch links retain list, occurrence index
and timestamp. The service owns sequential playback, paging and dynamic mix
continuation, including background audio and PiP. Open Playback queue from the
watch page or player controls to select items, navigate previous/next, remove local
queue entries, delete an owned playlist occurrence separately, or select repeat
Off/One/All. All applies to finite queues. Queues last until playback closes, the
service stops, or the account/instance changes. Shared next-recommendation,
autoplay-next and single-video-loop defaults require the sibling native preference
PATCH allowlist update, using existing scopes and no migration. Runtime repeat
selection is session-only. Native runtime/layout acceptance remains unverified
because the installed emulator crashes before boot; see VERIFICATION.md.

The native sign-in/history/settings extensions must be deployed before account
features work against production. An APK alone does not update the Ubuntu server.

## Upstream maintenance

Merge upstream into the sibling `invidious` repository as you do now, run its
account/API checks, rebuild the Docker image and pull Companion updates.
The client contains no YouTube scraping
or signature deciphering. Upstream fixes can be adopted without rebuilding the APK
while API contracts remain compatible; changes to those contracts may require a
small client update. The server's existing custom extraction features remain your
fork's responsibility.

Invidious retains its AGPL-3.0 license and attribution. Mobivious is AGPL-3.0-only;
the root LICENSE mirrors Invidious's license. Include corresponding source when
distributing modified binaries as required by that license.

## Playlist subscriptions and RSS

Library separates **My playlists** from **Subscribed playlists**, with counts and
source labels. Only owned playlists can be edited or used as saving/default
destinations. Subscribe to YouTube playlists, mixes, or another user’s
public/unlisted Invidious playlist; opening or refreshing fetches owner updates.
Unsubscribe removes your subscription while playback continues. Search defaults to
Videos and also offers Playlists & mixes; channel playlists support continuation
pagination and Last added/Newest/Oldest sorting.

RSS sheets offer Open, Copy and Share for feed links. The subscription-feed link
grants access to its holder and stays in memory; the native bearer token is never
shared. Private owned playlists export authenticated Atom snapshots via Open, Save
file or Share. Subscription OPML exports support both Invidious and YouTube feeds.
This adds feed/export access, without RSS reading, polling or upload alerts.

The sibling server requires **migration 20** before rollout. Renew native tokens by
signing out and in after the API update. See [deployment instructions](deploy/README.md),
[API contract](../invidious/docs/mobile-api.md) and [verification](VERIFICATION.md).
Release 0.3.0 includes these changes; the older 0.2.1 APK predates this implementation.
