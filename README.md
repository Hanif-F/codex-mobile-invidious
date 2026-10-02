# Mobivious

A native Android client for your Invidious instance, built with Kotlin, Compose
and Media3. Invidious and Companion own all YouTube extraction and stream
resolution. The app uses their JSON APIs and server-proxied DASH/HLS streams.

`android/` contains the client. `source/` is your independent Invidious Git checkout;
its small mobile API patch is documented in `source/docs/mobile-api.md`. Keep its
origin/upstream remotes. Commit server changes inside that repository; commit app
and deployment changes here. No remote pushes or production deployment are automatic.

## Build

Open `android/` in Android Studio. Install SDK platform 37, Build Tools 36.0.0 and
use JDK 17 or newer (the installed Android Studio JBR works). Gradle and library
versions are pinned. Local SDK settings, signing material and build output are ignored.

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
installation. The default app URL is `https://mobivious.wingress.net`; set another
HTTPS address through Settings → Server while the new hostname is being configured.

## Features

Home popular/trending, filtered search and shared video links; channel browsing;
native account sign-in; subscription feed and subscribe/unsubscribe; playlists and
watch history; descriptions, captions, read-only comments and recommendations.
The service-owned player supports adaptive streams, seeking, speed/quality/audio
selection, background playback, system media controls, mini-player, fullscreen,
audio only and picture in picture. Explicit URL timestamps override saved resume
positions. History and resume settings are shared with the website.

One consistent appearance follows system light/dark mode. SponsorBlock, DeArrow,
chat replay, clips, blocking, downloads, casting and upload notifications are
outside this first release. Discovery uses Invidious popular/trending feeds.

The native sign-in/history/settings extensions must be deployed before account
features work against production. An APK alone does not update the Ubuntu server.

## Upstream maintenance

Merge upstream into `source/` as you do now, run its account/API checks, rebuild the
Docker image and pull Companion updates. The client contains no YouTube scraping
or signature deciphering. Upstream fixes can be adopted without rebuilding the APK
while API contracts remain compatible; changes to those contracts may require a
small client update. The server's existing custom extraction features remain your
fork's responsibility.

Invidious retains its AGPL-3.0 license and attribution. Mobivious is AGPL-3.0-only;
the root LICENSE mirrors Invidious's license. Include corresponding source when
distributing modified binaries as required by that license.
