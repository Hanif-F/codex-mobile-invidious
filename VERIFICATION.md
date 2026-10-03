# Verification — 3 October 2026

Local verification used the Pixel 8 Pro emulator, Android 16 / API 36. The Android
app targets API 37 and supports API 26+. No physical phone was needed.

| Check | Result |
|---|---|
| Invidious normal build, Crystal 1.21.1 | Passed |
| Invidious API-only build | Passed |
| Crystal parser/core specs | 215 passed |
| Crystal standard specs | 38 passed |
| Account and mobile API database harness | Passed |
| Android unit tests | 8 passed |
| Android emulator integration | 4 passed; player UI scenario also passed in dark mode |
| Android lint | No errors; dependency update/style warnings remain |
| Docker Compose validation | Passed with dummy keys |
| Docker server image build | Passed |
| Built Docker image HTTP health | 200 |
| Built Docker image invalid native login | Generic 401, no-store headers |
| Existing live instance public API compatibility | 40 popular entries; video/DASH/caption responses verified |
| Personal release APK build and signature verification | Passed |
| Signed release browsing against the existing HTTPS instance | Passed |
| Signed release live YouTube playback through Invidious/Companion | Playing, buffered, no player error |

The database harness verifies legacy credentials, malformed/oversized sign-in,
generic authentication errors, shared throttling, disabled login, token signature
and database expiry, least privilege, logout, concurrent credential changes,
unknown preference preservation, position clearing, unavailable history entries
and large-page pagination. Its database is disposable and guarded by name.

Emulator tests use a localhost-only API fixture and generated video; they verify
native sign-in, real DASH/HLS decoding, seeking and timestamp precedence, history
and position writes, speed, captions, audio only, playlist creation/editing,
mini-player, PiP entry/return and playback continuing in the background. They use
a custom activity host so Android's previously pinned task is cleaned up explicitly.

## Android player overhaul — 3 October 2026

The updated debug build passed `testDebugUnitTest`, `assembleDebug`, `lintDebug`
and all four fixture-backed `connectedDebugAndroidTest` scenarios on the Pixel 8 Pro
emulator. Lint has no errors and no warnings in the new player UI file; existing
project dependency/style warnings remain. The complete player UI scenario was also
run separately with Android dark mode enabled and passed.

The new checks cover the entire controller fading, tap-to-show/hide, double-tap
±10-second seeking and bounds, accessible seek actions, timeline scrubbing, removal
of the external Player chip/back overlay, gear settings and active values after
reopening, 0.25× speed, quality ceilings, captions, unlabeled audio-track selection,
landscape menu scrolling, submenu/sheet/fullscreen Back navigation, and injected
error/Retry interactions in both embedded and fullscreen layouts. The other
scenarios retain real DASH/HLS decoding, account/history/position writes,
mini-player, playlist actions, background playback and PiP entry/return; PiP is now
entered through the unified gear menu in the account scenario.

Refresh buffer verification observes fresh media requests and ready/playing state
from the real Media3 player. It preserves the paused position, playing intent,
1.5× speed, 720p ceiling, caption preference, selected audio-track override and
audio-only mode. The source implements live-edge refresh, but the generated
fixtures are VOD and do not provide a real moving live-window acceptance test.

Portrait and fullscreen player/settings screenshots were inspected in light and
dark mode. The timeline uses a thin track and round handle; the settings header
stays fixed while the landscape list scrolls. QA screenshots are generated under
`/data/local/tmp/mobivious-player-screenshots/` on the emulator so Android test
package cleanup does not erase them. Accessibility actions were exercised;
physical-phone and TalkBack/OEM behavior still need device acceptance checks.

The source audit updates in `FEATURE_PARITY.md` cover rows 09, 11, 13 and 20; its
broad counts are unchanged. No server tests or production rollout were performed
as part of this player revision. The earlier server/release results above remain
historical checks of the original build, rather than a new signed-release test.

## DeArrow titles and contributions — 3 October 2026

The native DeArrow implementation and matching sibling server API were checked
with local fixtures and mocked upstream writes. No real titles or votes were
submitted, and no production deployment or signed release was performed.

| Check | Result |
|---|---|
| Android debug app and instrumentation APK build | Passed |
| Android unit/MockWebServer tests | 17 passed, including 9 DeArrow scenarios |
| Android debug lint | Passed; dependency and existing code-style warnings remain |
| Crystal DeArrow specs | 13 passed |
| Guarded disposable PostgreSQL account/API harness | Passed, including native DeArrow checks |
| Rendered web fixtures | Passed |
| Focused Chromium DeArrow browser tests | 11 passed, including web widths 320px/390px/1440px |
| Invidious normal and API-only executable builds | Passed |
| Native emulator instrumentation/runtime/screenshots | Unverified: emulator startup crashes before Android boots |

Unit tests cover opt-in defaults, title/identity validation, upstream proposal
order, sparse preference PATCH requests, deduplicated lookups with at most four
concurrent requests, null/error fallbacks, stale-response isolation, bearer-only
authenticated calls, JSON booleans, old-server/token explanations, a timeout with
exactly one write attempt, and identity-import redirect refusal. The database
harness exercises real auth middleware, scope and CSRF checks, encrypted identity
import/blank preservation, shared web/native identity use, server-resolved votes,
locked/stale rejection, bounded JSON, guideline confirmation and preservation of
unrelated preferences. Upstream transports are injected mocks.

Three new Compose scenarios compile alongside the four existing emulator tests.
They cover guest settings/original-title controls, shared titles across lists and
the mini-player, playback metadata without resetting position/speed/quality or
duplicating history, voting restrictions, all four acknowledgements, failed draft
preservation, identity import/clearing and logout isolation. These scenarios have
**not run successfully in this revision**. Android Emulator 37.2.12 repeatedly
terminated with SIGSEGV during startup, including a fresh disposable AVD and
alternative graphics/CPU configurations. Narrow native layouts, keyboard behavior,
light/dark screenshots, accessibility interaction and the Media3 metadata update
still need runtime acceptance. The connected-test task could not execute because
there were no connected devices. Earlier player results above are historical.

Once a working emulator is available, run `scripts/test-android.sh`. Repeat the
DeArrow scenarios at 320dp and 390dp widths in light/dark mode, with the keyboard
visible; the contribution test records screenshots under
`/data/local/tmp/mobivious-dearrow-screenshots/`. The app defaults to DeArrow off
and original-title controls on. Signed-in flags use the account, while guests use
per-instance local settings.

Production contributions require deployment of the sibling API update, the
existing migration 13 identity table/key, and sign-out/sign-in to obtain the
added token permissions. This revision adds no schema migration. Public trusted
title reads remain independent of contribution storage.

## SponsorBlock parity — 3 October 2026

SponsorBlock was implemented against the local web fork's categories, colors,
settings inheritance and playback behavior. Public segment data uses the configured
Invidious instance. No production rollout, live account mutation, upstream
contribution or signed release was performed.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 30 passed, including 13 SponsorBlock scenarios |
| Android debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed; no errors; existing dependency/style warnings remain |
| Crystal parser/core specs | 215 passed |
| Crystal standard specs | 41 passed, including 11 SponsorBlock scenarios |
| Guarded disposable PostgreSQL account/API harness | Passed, including SponsorBlock preference checks |
| Rendered web fixtures and focused Chromium SponsorBlock regressions | Passed; 10 browser scenarios |
| Invidious normal and API-only executable builds | Passed |
| Android connected/runtime/layout checks | Unverified: emulator startup crashes; no connected devices |

Unit checks cover opt-in defaults, category/color normalization, malformed ranges,
deduplication, canonical channel input, nullable inheritance, sparse category/channel
patches, overlapping/touching auto ranges, manual replay, earliest-ending manual
prompts, dismissal/re-entry, marker/disabled modes, duration bounds, session reset,
state serialization, credential-free public requests and stale account contexts.
The database harness uses real authentication middleware and account transactions:
shared web/native values, unrelated/unknown preference preservation, independent
channels, resets, invalid and oversized JSON, CSRF, existing token scopes, lookup
failure atomicity and concurrent deltas all passed. Browser tests exercise the
existing web baseline with generated videos and intercepted requests. An initial
browser run lacked its synthetic video fixtures; generating the documented media
resolved the playback timeouts and all ten scenarios passed.

Five new Compose scenarios compile. They cover guest/color settings, manual
Skip/Dismiss, replay and merged automatic skipping, fullscreen/buffer refresh,
shared channel overrides and failed drafts, background/audio/PiP service behavior,
stale commands, failed segment reads and active-live exclusion. They have **not run
successfully**: Android Emulator 37.2.12 exited with SIGSEGV before Android booted
in both read-only configurations attempted, including Vulkan/camera-disabled
software graphics. `connectedDebugAndroidTest` reported no connected devices.
Native 320dp/390dp layouts, keyboard and accessibility interactions, light/dark
screenshots, and actual Media3/MediaSession integration remain unverified. Existing
player acceptance results above are historical, not new SponsorBlock runtime checks.

Once a working emulator or device is connected, run `scripts/test-android.sh`.
Repeat SponsorBlock checks at 320dp and 390dp in light/dark mode and landscape, with
the keyboard visible. The new scenarios save screenshots under
`/data/local/tmp/mobivious-sponsorblock-screenshots/`. The localhost fixture now uses
a canonical channel ID and models category-map merges and individual channel resets.

Shared SponsorBlock writes require deploying the preference PATCH extension in the
sibling repository. It introduces no migration, key or additional token permissions;
existing native preference tokens suffice. SponsorBlock defaults off with manual
modes. Guests save global settings per instance, while channel overrides require
sign-in. Active livestreams do not fetch segments. An older preference API receives
an update explanation without discarding the settings draft.

## Dedicated settings screens and supported web preferences — 3 October 2026

Settings now uses a full screen with dedicated Playback, Appearance, Browsing,
Subscriptions, History & library, SponsorBlock, DeArrow, Server and About screens.
SponsorBlock channel submenus use the full-screen container when opened from App
Settings; the player retains its sheet. Regular category drafts and the current
settings route survive Activity recreation. Back returns to the settings index and
then the previous browse/watch screen. Failed shared saves retain the draft and
show an inline error. Background/PiP settings save immediately on the device.

The native preference model and sibling PATCH API now cover supported playback,
caption, appearance, browsing, feed and library values. Sparse patches preserve
unknown account settings and SponsorBlock map entries. Guest preferences are saved
per instance, including local resume. Feed/history requests no longer override
account page size with 30. The active playback service accepts history/resume
changes, and default speed/quality writes are serialized. Fresh installs use
`https://invidious.wingress.net`; explicit saved addresses are retained.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 37 passed, including 7 new preference scenarios |
| Android debug and instrumentation APK builds | Passed |
| Android debug lint | Passed; no errors; 32 existing/style/dependency warnings |
| Focused native-preference and SponsorBlock Crystal specs | 14 passed |
| Guarded disposable PostgreSQL account/API harness | Passed, including expanded native preference checks |
| Normal and API-only Invidious compiler checks (`--no-codegen`) | Passed |
| Android runtime, screenshots and layout acceptance | Unverified: emulator exited with SIGSEGV before Android booted |

Unit/API checks cover shared preference parsing/round trips, sparse writes,
concurrent local merge preservation, language priority/availability, navigation
fallbacks, malformed value defaults, server page-size requests, notification-only
filtering and the stream proxy parameter. The database harness verifies the actual
account transaction and middleware: expanded typed values, shared reads, invalid
patch atomicity, unrelated/unknown preservation and browser CSRF checks. It used a
fresh `invidious_accounts_test` database in a temporary PostgreSQL container, which
was removed after the run. No production accounts or services were changed.

Three new Compose scenarios compile for screen/back navigation, draft recreation,
guest autoplay/audio/resume defaults and failed shared-save retries. Existing
DeArrow and SponsorBlock tests were adapted to the full-screen settings entry.
They have not run in this revision: Android Emulator 37.2.12 again terminated with
exit 139 during startup in read-only, headless software-graphics mode with Vulkan
and cameras disabled. Native narrow/landscape layout, keyboard, light/dark and
accessibility acceptance remain pending a working emulator or device. Run
`scripts/test-android.sh` when one is available.

The expanded shared preference API must be deployed from the sibling Invidious
checkout before the newly added account settings can be saved on production. It
requires no migration, new key or token scope. Guests can use the local settings
without that API update. No release APK was signed or production deployment made.

## Exact stream controls and accumulated tap seeking — 3 October 2026

Quality now offers Auto and exact supported video representations, including codec,
FPS, bitrate tiers and available file sizes. Saved defaults match the web's Auto,
Best, 4320p–144p and Worst choices. In-player quality choices apply only to the
current video. Audio choices group original, stable-volume and dubbed variants;
unknown and unlabeled tracks remain selectable. Matching uses representation
identity and language/label metadata rather than audio itags alone. Refresh,
same-video retry and controller reconnection preserve available selections.

Double tap starts a ±10-second batch, subsequent single taps add 10 seconds, and
an opposite tap replaces the accumulated total with 10 seconds in that direction.
The player pauses at the starting position, seeks once after 600 ms of inactivity,
and restores its previous playing or paused intent. Pending state belongs to the
ViewModel and survives fullscreen/recreation. Competing seeks, settings/navigation,
errors, media/account changes and PiP/background entry cancel the batch; accessibility
and PiP seek actions retain immediate behavior.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 52 passed, including 15 new stream/accumulator scenarios |
| Android debug and instrumentation APK builds | Passed |
| Android debug lint | Passed; zero errors, 32 existing/style/dependency warnings |
| Focused audio serialization and native preference Crystal specs | 6 passed |
| Normal and API-only Invidious compiler checks (`--no-codegen`) | Passed |
| Generated rich fixture and localhost API/media checks | Passed; four video representations and four audio variants with accessible initialization segments |
| Python syntax, shell syntax and diff whitespace checks | Passed |
| Android connected/runtime/layout checks | Unverified: emulator crashed before boot; connected tests reported no devices |

Unit tests cover optional/string-valued format metadata, duplicate audio itags,
language/stable-volume disambiguation, codec/FPS/bitrate details and tiers, unsupported
tracks, exact override versus adaptive selected flags, ranked quality defaults,
reload identity matching, ambiguous/vanished representations, original/dubbed role
precedence, unlabeled audio, timing/deadline renewal, direction reset, bounded
targets, cancellation, stale media and previous playing intent. Server tests verify
additive audio metadata, false default flags, omission of unrelated upstream fields,
and shared stable-volume detection from flags, labels and encoded URLs.

Five new Compose scenarios compile for real exact video/audio selection, unchanged
saved defaults, captions/speed, refresh/retry/recreation, ranked defaults, sequential
single-tap accumulation and reversal, pause/resume intent, cancellation, fullscreen
and PiP. Existing player/DeArrow assertions now inspect exact representation
overrides, and retry checks preserve paused intent. These scenarios have **not run**: Android Emulator 37.2.12 exited with
SIGSEGV (139) during a read-only headless startup with software graphics, Vulkan
and cameras disabled. No physical device was connected. Native gesture timing,
320dp/390dp and landscape layouts, light/dark screenshots, accessibility and actual
Media3/MediaSession acceptance remain pending. Existing historical runtime results
above do not validate this revision.

Run `scripts/test-android.sh` with a working emulator/device. The script generates
the rich media alongside the existing DASH/HLS fixtures, retaining older scenarios.
Repeat the stream scenarios at narrow widths and in light/dark mode; menu captures
are written to `/data/local/tmp/mobivious-stream-screenshots/`. Generated stable/dubbed
variants use synthetic audio and metadata to test track selection, not upstream
language translation or dynamic-range processing. Real moving live-window seeking
still needs separate device acceptance.

Reliable audio enrichment requires deploying the sibling video's additive
`audioTrack`/`isDrc` API fields. Older instances fall back to manifest metadata and
generic labels; all options remain limited to supported manifest tracks. This API
change needs no database migration, new key, token scope or sign-in renewal. No
production rollout or signed release was performed.

## Mobivious 0.2.0 release — 3 October 2026

The release APK uses application ID `net.wingress.mobivious`, version name `0.2.0`
and version code `3`. Package inspection confirmed minimum SDK 26, target SDK 37
and arm64-v8a/armeabi-v7a/x86/x86_64 support. The existing signing key was reused;
the verified APK certificate matches the published 0.1.1 asset. Its local copy's
SHA-256 matches GitHub's asset digest, establishing signing continuity against the
published release rather than an unrelated local build.

`scripts/build-release.sh` passed all 52 Android unit/MockWebServer tests,
`assembleRelease` and `lintRelease`. APK signature verification, package/version
inspection and the generated checksum passed. The versioned APK and matching
`.apk.sha256` are prepared for the GitHub `v0.2.0` release; signing material remains
local and ignored. Native runtime/layout acceptance limitations in the preceding
section still apply. Building and publishing this APK does not deploy the sibling
Invidious server APIs or change production services.

## Channel loading and Streams tabs — 3 October 2026

Android now omits empty/blank continuation parameters on the first Videos or
Streams request and preserves nonblank tokens through URL encoding. Channel
metadata supplies the available tabs; Videos is preferred when advertised,
otherwise Streams is selected, with Videos as the compatibility fallback. Switching
tabs cancels the previous load, resets the list/token and starts the selected
endpoint. Refresh/Retry preserve a still-available selection, and stale metadata or
pages cannot replace a newer channel route or tab.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 58 passed, including 6 new channel scenarios; no failures or skips |
| Android debug and instrumentation APK builds | Passed |
| Android debug lint | Passed; zero errors, 32 existing/style/dependency warnings; none reference the channel changes |
| Disposable channel fixture HTTP checks | Passed: separate pagination, exact token decoding, blank-token failure, wrong-tab token rejection, one-shot failure, streams-only metadata and concurrent delayed metadata |
| Existing live instance public channel APIs | Passed: WAN Show Streams returned 15 videos; Just For Laughs Gags Videos returned 60 videos on each of two pages |
| Native channel runtime/layout acceptance | Unverified: `adb devices` lists no connected emulator or device |

Unit/API checks cover advertised and malformed tab metadata, stable tab ordering,
uploads/streams-only and missing-metadata defaults, retaining or replacing a selected
tab after metadata changes, omitted initial/default/whitespace tokens on both
endpoints, exact opaque tokens containing reserved characters and Unicode,
credential-free public reads, parsed videos/live flags, end-of-list responses and
temporary errors followed by successful retries.

Four new `ChannelSmokeTest` Compose scenarios compile. They cover tapping a video's
channel, Videos/Streams switching and independent pagination, automatic Streams
selection, Refresh/Retry retention and fallback when a tab disappears, delayed
pages after switching or leaving a channel, and delayed metadata after navigation.
The stricter fixture rejects `continuation=` instead of silently accepting it.
These native scenarios have **not run**. With a working device connected, run
`scripts/test-android.sh`; narrow/landscape layouts, light/dark appearance and
accessibility acceptance remain pending device verification.

Read-only checks on `https://invidious.wingress.net` reproduced the original failure:
`/api/v1/channels/:id/videos?continuation=` returned HTTP 500 containing the reported
upstream YouTube 400 message, while the same request without the parameter and the
channel's web page returned 200. WAN Show (`UCVlfe3MRSCZSeIWByrWHhkg`) advertises
Streams, Podcasts and Posts, with no regular uploads; its Streams endpoint returned
15 videos. Just For Laughs Gags (`UCpsSadsgX_Qk9i6i_bJoUwQ`) confirmed normal uploads
and follow-up pagination. These are API checks, not acceptance of the Android UI
against production. The feature uses existing server APIs and needs no server
deployment, migration or new token scope. No signed release or production rollout
was performed.

## Repeat Android checks

Start an emulator in Android Studio, install FFmpeg and Python 3, then:

```sh
export ANDROID_SERIAL=emulator-5554  # use the serial shown by adb devices
scripts/test-android.sh
```

The script generates its own 120-second video, starts the fixture on localhost
port 18080, forwards that port through ADB, and runs tests plus lint. No YouTube
requests or production accounts are used. Stop any earlier fixture using this port
before running the script. Generated media and signing material are ignored by Git.

## Repeat server checks

Initialize the existing mocks submodule from upstream if your fork's relative
submodule URL points to a repository you do not have:

```sh
git -C ../invidious -c submodule.mocks.url=https://github.com/iv-org/mocks.git \
  submodule update --init mocks
cd ../invidious
shards install
crystal spec
```

These commands use the default sibling checkout layout; adjust the path for a
custom server location. The account harness instructions are in
`../invidious/docs/mobile-api.md`. Run it only
against an empty disposable `invidious_accounts_test` database. Its schema is
created during the test; start with an empty schema for every run. Server specs need local loopback networking for proxy
tests. The normal build downloads the existing web player's dependencies; an
interrupted download may leave empty asset directories that need to be removed
before retrying the existing dependency script.

## Relocated server checkout

Compose configuration passed with the default `../invidious` checkout, an explicit
absolute path, and a custom checkout path containing spaces. The Dockerfile and
both SQL initialization mount sources exist in each case. Compared with the
previous Compose configuration, only the three source paths changed; the project
name, database, volumes, account settings and service configuration are identical.

At the time of relocation, all nine mobile API patch files matched the previously
tested server commit byte for byte, including the documentation exception. The
original `source/.git` remained at `644443ef` as a recovery copy. Android source and
build/test scripts were unchanged and both shell scripts passed syntax checks.
No server image or APK rebuild was needed for that location change. The subsequent
DeArrow source changes and validation are described above.

## Production work still required

The mobile hostname, TLS alias, Docker rollout and new account APIs have **not**
been deployed on the Ubuntu server. Follow `deploy/README.md` and preserve the
existing keys, project name and volumes. Account operations in the release app
need that server patch; anonymous browsing can use the existing HTTPS instance.
Native DeArrow contributions additionally need the updated DeArrow API routes
and a renewed sign-in token. Public replacement-title reads use the existing route.
Physical POCO X6 Pro behavior, OEM background restrictions, and authenticated
account features after production rollout still need a device/server acceptance check.
