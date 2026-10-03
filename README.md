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
release_version=0.1.1
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

Home popular/trending, filtered search and shared video links; channel browsing;
native account sign-in; subscription feed and subscribe/unsubscribe; playlists and
watch history; descriptions, captions, read-only comments and recommendations.
The service-owned player supports adaptive streams, seeking, speed/quality/audio
selection, background playback, system media controls, mini-player, fullscreen,
audio only and picture in picture. The embedded and fullscreen player share fading
controls, double-tap ten-second seeking, and one gear menu for quality, audio,
captions, speed, audio only, picture in picture and Refresh buffer. Explicit URL timestamps override saved resume
positions. History and resume settings are shared with the website.

Optional DeArrow titles appear throughout browsing and playback, with an original-title toggle. A native watch-page sheet supports title suggestions, all four guideline acknowledgements, voting and refresh. Signed-in settings and the encrypted contribution identity are shared with the website; Settings also supports private-ID import. Guests keep local title settings per instance. Contributions require the server API update and a fresh sign-in token.

SponsorBlock matches this fork's eight categories, automatic/manual skipping,
marker-only and disabled modes, custom colors and per-channel inheritance. Colored
timeline ranges and scrub labels complement Skip/Dismiss prompts. The playback
service handles skipping during background, audio-only and PiP playback, and offers
manual controls when replaying an automatically skipped segment. App Settings opens
a full SponsorBlock screen with channel submenus. The player gear opens a settings
sheet; watch/channel screens link to the channel editor. Signed-in settings are shared with the website through the updated
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
Chat replay, clips, blocking, downloads, casting and upload notifications are
outside this first release. Discovery uses Invidious popular/trending feeds.

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
