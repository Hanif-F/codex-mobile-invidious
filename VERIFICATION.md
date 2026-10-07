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

## Mobivious 0.2.1 release — 3 October 2026

The release includes the channel first-page continuation fix and native
Videos/Streams tabs, with automatic Streams selection for channels without
uploads. It uses application ID `net.wingress.mobivious`, version name `0.2.1`
and version code `4`, retaining minimum SDK 26, target SDK 37 and
arm64-v8a/armeabi-v7a/x86/x86_64 support.

`scripts/build-release.sh` passed all 58 Android unit/API tests, `assembleRelease`
and `lintRelease` (zero errors, 32 existing/style/dependency warnings). APK package
inspection, archive integrity, signature verification and checksum validation
passed. The signing certificate matches the existing 0.2.0 APK; that previous
APK's SHA-256 matches GitHub's published asset digest, confirming update continuity.
The versioned APK and `.apk.sha256` are the assets for the GitHub `v0.2.1` release.
Signing material stays local and ignored.

No device is connected, so the native runtime/layout limitations recorded in the
channel revision still apply. The channel fix and Streams browsing use existing
public APIs and require no server deployment. Previously documented server
requirements for account features still apply.

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

## Watched/progress indicators — 4 October 2026

The native cards now use the existing authenticated bulk playback endpoint for
watched history and saved positions. The shared repository is owned by the
Application and updated by the playback service, including background/audio/PiP
playback. Guests use device-local resume positions only when enabled. This change
adds no manual watched-state controls or new server/API permissions.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 78 passed, including 20 new watched/progress scenarios |
| Android debug and instrumentation APK builds | Passed |
| Android debug lint | Passed; 0 errors, 33 dependency/style warnings |
| Disposable localhost watched/progress HTTP fixture | Passed |
| Python fixture syntax and diff whitespace | Passed |
| Android connected/runtime/layout/screenshots | Unverified: emulator terminated with exit 139 before Android booted; no connected devices |

Unit checks cover the bulk bearer-only API contract, malformed snapshot entries,
web percentage rounding/minimum/full-bar thresholds, separate history and progress
accessibility descriptions, unknown durations and active-live exclusion, guest
completion, account completion, coalesced reads, retained state on failures and
retry. Delayed-response cases verify account/instance isolation, newer service
writes surviving an older read while other videos still refresh, reads during
pending writes, history removal/clearing surviving older bulk snapshots,
clear-history invalidation of queued saves,
disabled-resume invalidation, history removal preserving progress, failed history
mutations retaining state and failed position writes retaining device fallback.

Local positions are now keyed by instance and account username, with a separate
guest namespace. Legacy unscoped positions are attributed to the saved session
(including an expired session) when present, otherwise to the guest namespace.
Cleared/completed positions are removed rather than stored as zero. These device
fallback values are never replayed to the server outside current playback.

The isolated fixture check used a generated media directory, an ephemeral loopback
port and a disposable fixture process that was terminated afterwards. It verified
authentication, multiple watched/progress entries, discovery/recommendation cards,
independent history deletion, position writes/deletion, clearing history and
positions together, disabled resume and injected bulk-read failure. No production
account, server or upstream content was changed.

Six new Compose scenarios compile for indicators across discovery/search/feed,
channel/playlist/history/recommendation cards, compact and thumbnail-free layouts,
light/dark screenshots, accessible descriptions, guest preferences, unknown/live
durations, history removal/clearing, background playback/completion, failed refresh
and sign-out isolation. They have **not run**. Android Emulator 37.2.12 exited with
SIGSEGV during a read-only headless SwiftShader startup with Vulkan and cameras
disabled. Native layout, screenshot, accessibility and Media3/service acceptance
remain unverified.

When a working emulator or device is available, run `scripts/test-android.sh` and
repeat `WatchedIndicatorsSmokeTest` at 320dp and 390dp widths in light/dark mode.
The scenarios save screenshots under
`/data/local/tmp/mobivious-watched-screenshots/`. The existing mobile/account API
deployment requirement still applies; this revision needs no additional server
patch, migration or token renewal.

## Production work still required

The mobile hostname, TLS alias, Docker rollout and new account APIs have **not**
been deployed on the Ubuntu server. Follow `deploy/README.md` and preserve the
existing keys, project name and volumes. Account operations in the release app
need that server patch; anonymous browsing can use the existing HTTPS instance.
Native DeArrow contributions additionally need the updated DeArrow API routes
and a renewed sign-in token. Public replacement-title reads use the existing route.
Physical POCO X6 Pro behavior, OEM background restrictions, and authenticated
account features after production rollout still need a device/server acceptance check.

## Members-only visibility and channel blocking — 4 October 2026

Rows 40–41 are implemented in source across Android and the sibling server. The
native client keeps original public responses and filters them locally using member
metadata, the shared account block list and separate device-local search overrides.
Blocking applies to discovery/search/recommendations; membership visibility also
applies to channels, subscription feeds and playlists. History and direct access
remain available. Neither preference changes nor blocking stop current playback.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 93 passed, including 15 visibility/blocking scenarios |
| Debug application and instrumentation APK builds | Passed |
| Android debug lint | Passed: zero errors, 36 existing/style/dependency warnings; no warnings in new visibility files |
| Focused Crystal visibility/search/preference specs | 34 Spectator examples and 3 standard examples passed |
| Invidious normal and API-only executable builds | Passed, using existing assets and `-Dskip_videojs_download` |
| Guarded disposable PostgreSQL account/API harness | Passed, including native/web blocking and member serialization |
| Disposable visibility fixture HTTP checks | Passed |
| Six new Compose scenarios | Compile; runtime, layout and screenshots unverified |

Unit tests cover strict member booleans and unknown/Premium fallbacks, sparse
shared preference deltas, filtering scope and combined search overrides/reset,
original-page termination and playlist occurrence retention, bearer-only account
calls, corrected `sort=views` search requests, old-server/token explanations,
confirmed offline snapshots, failed writes, refresh coalescing, delayed reads after
successful mutations, delayed mutation responses after account changes, account/
instance/guest isolation and raw public-cache re-filtering. A successful block is
saved even if the initial list refresh failed. Existing watched/progress tests pass.

The server harness exercised real auth middleware and the existing database table:
native/web shared state, account isolation, least-privilege and old-scope rejection,
browser CSRF, private/no-store headers, idempotent writes, canonical IDs, bounded
names/JSON, malformed-body rejection and unrelated preference preservation. It
also serialized members-only search/channel/feed entries, full video metadata and
recommendations from local mocks. The database was an isolated temporary PostgreSQL
container bound to localhost; no production account or upstream content was used.

Fixture HTTP checks verified raw public responses, member metadata, an entirely
hidden first search page with a visible second page, playlist occurrence IDs and
block/unblock endpoints. The six Compose scenarios cover guest settings and badges
in compact/text-only/dark layouts; hidden-page navigation and search override reset;
card/search/channel controls and retained library access; website block refresh,
manager errors and retries; direct members-only playback with uninterrupted watch
blocking and recommendation updates; and persisted account/instance/guest state.

The installed Pixel_8_Pro emulator exited with SIGSEGV (139) before Android booted
when launched without saved state using SwiftShader. No connected device was
available. These new UI scenarios have **not executed**, and native screenshots,
layout, accessibility interaction and runtime acceptance remain pending. Repeat
with a working emulator/device using `scripts/test-android.sh`; screenshots are
written under `/data/local/tmp/mobivious-visibility-screenshots/` on the device.

Deploy the sibling metadata, preference and block-list API update before using
these features against production. Blocking introduces `GET:blocked_channels` and
`POST;DELETE:blocked_channels/*` mobile scopes, so existing native sessions must
sign out and in. Members visibility uses existing preference scopes. No new database
migration, secret, production deployment or release publication was performed.

## Scoped search and organized history — 4 October 2026

Rows 06 and 33 now implement native scoped search and the website's history
organization. Channel search uses the existing public endpoint; subscription
search uses a new authenticated endpoint and the website's full-text matching
against current cached subscriptions. Organized history filters and sorts all
entries before pagination using shared website helpers. Saved release/watch dates
remain calendar dates; Android groups them against the API's account-timezone
`today`, with UTC fallback and reload on a day change during pagination.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 105 passed, including 12 new search/history scenarios |
| Debug application and instrumentation APK builds | Passed |
| Android debug lint | Passed: zero errors, 36 existing/style/dependency warnings |
| Crystal history specs | 5 examples passed, including large-page overflow regression |
| Crystal search/filter/preference specs | 51 examples passed |
| Normal and API-only server executable builds | Passed with existing assets and `-Dskip_videojs_download` |
| Guarded disposable PostgreSQL account/API harness | Passed, including new search/history and existing account/security checks |
| Disposable search/history fixture HTTP checks | Passed |
| Nine new Compose scenarios | Compile; runtime, layout and screenshots unverified |

Unit/API checks cover query encoding, bearer scope, original mixed-result page
counts, strict calendar dates, disjoint date boundaries across years/leap days,
unknown/unavailable metadata, server-calendar grouping, old detailed and ID-only
history responses, missing-route/old-token messages, and captured contexts before
and during delayed responses after an account change. Existing watched/progress
and visibility tests also pass.

The real database harness verified title/channel matching over the entire history
and subscription library before pagination, account page size, counts/end flags,
stable subscription ordering, cached member/duration metadata, unavailable history
retention, account isolation, exact token scopes, private/no-store headers,
unchanged legacy history formats and very large page numbers. It caught an
`Array#skip` Int32 subtraction overflow; shared web/native history pagination now
bounds offsets before slicing. The login-lifetime assertion uses the timestamp at
login so the expanded harness's execution time does not change its meaning.
Validation used a new PostgreSQL 14 container bound only to localhost and the
guarded `invidious_accounts_test` database; no production account or upstream
content was used. Temporary services were removed after validation.

Fixture HTTP checks verified scope/authentication, raw and entirely hidden search
pages, subsequent visible results, full-history matching, saved metadata and
legacy-array fallback. The nine Compose scenarios cover icon/IME/physical Enter
submission, pasted timestamps, channel tab restoration, subscription hidden-page
pagination despite feed-only filters, history search/groups/unknown entries,
remove/progress and clear confirmation, old-server viewing, delayed responses
after tab/account changes, and calendar-day reloads. These scenarios have **not
executed**. `adb devices` showed no connected device, and the installed Pixel_8_Pro
emulator exited with SIGSEGV (139) before boot when started without snapshots using
SwiftShader. Native keyboard interaction, layout, accessibility and screenshot
acceptance remain pending a working emulator/device.

Repeat Android validation with:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

With a working emulator/device, run `scripts/test-android.sh`. Server checks use
`crystal spec spec/history_spec.cr`, the search query/IV-filter/search-preference
specs, both normal and `-Dapi_only` builds, and the guarded database harness in
`../invidious/docs/mobile-api.md`. Use a fresh disposable database for each run.

Production use needs the sibling API update. Sign out/sign in for the new exact
`GET:subscriptions/search` token scope; history retains `GET:history`. No database
migration, new secret, signed release, production deployment or publication was
performed.

## Video library actions and playback queues — 4 October 2026

Rows 17 and 32 implement shared video actions, save/create recovery, channel-block
Undo, session audio playback and service-owned playlist/mix/temporary queues.
Watch on YouTube and Switch Invidious instance actions are intentionally excluded;
Settings → Server remains. Queues survive navigation, background audio and PiP
while the service exists, then clear on close, service stop or account/instance
change. External-playlist save/unsave, shuffle and restart restoration are outside
this change.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 121 passed, including 16 new queue/library/API scenarios |
| Debug application and instrumentation APK builds | Passed |
| Android debug lint | Passed: zero errors, 40 warnings |
| Crystal native preference specs | 3 examples passed |
| Normal and API-only server executable builds | Passed |
| Guarded disposable PostgreSQL account/API harness | Passed, including new shared boolean settings and existing account/security checks |
| Disposable queue/library fixture HTTP checks | Passed |
| Nine new Compose scenarios | Compile; runtime, native controls, layout and screenshots unverified |

Unit/API checks cover distinct duplicate occurrences, overlapping source pages,
finite versus mix tail ordering, continuation growth and no-successor errors,
Play next during initial source loading, current removal, unavailable/member
eligibility, previous/missing-page navigation, repeat page boundaries, all four
autoplay combinations, playlist/mix link indexes/timestamps, sparse preference
deltas, guest/private/mix credentials, exact occurrence deletion, create-success/
save-failure recovery and stale requests/responses. Context generations reject
delayed responses even after returning to the same account and instance.

Fixture HTTP checks used local generated media and verified all 103 original
playlist positions across overlapping windows, duplicate IDs with distinct
`indexId` values, deleting only the requested occurrence, mix continuation,
one-shot save failure followed by a retry without another creation, and shared
boolean settings preserving unrelated values. The database harness ran against a
fresh PostgreSQL 16 container bound only to localhost and the guarded
`invidious_accounts_test` database. Temporary services were removed after checking.

The new Compose scenarios cover guest sign-in-and-save, inline create/save retry,
audio mode carrying across advancement, duplicate-source playback and Repeat All,
local removal, Repeat One and owned-source deletion, background/recreated activity
state, mix links/continuation/disabled All, PiP advancement and snackbar block Undo
with context invalidation. They have **not executed**. `adb devices` showed no
connected device. The installed Pixel_8_Pro emulator exited with SIGSEGV (139)
before boot when started without snapshots using SwiftShader. Native control,
foreground/background/PiP, layout, accessibility and screenshot acceptance remain
unverified. Repeat on a working emulator/device with `scripts/test-android.sh`.

Repeat build validation with:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Server validation used `crystal spec spec/native_preferences_spec.cr`, normal and
`-Dapi_only -Dskip_videojs_download` builds, and the disposable account/API harness
documented in `../invidious/docs/mobile-api.md`. Deploy the sibling native sparse
PATCH allowlist extension before saving `continue`, `continue_autoplay` and
`video_loop` in production. Reading/playback remains available on older servers;
unsupported setting writes explain the required server update. This extension
uses existing preferences/scopes, with no migration or token renewal. No production
deployment, signed release or publication was performed.

## Visible playback and watch actions — 4 October 2026

The watch, fullscreen, PiP and browsing mini-player now share one video-surface
component connected to the existing service controller. Each attached view requests
`keepScreenOn` during ready/buffering playback in video mode while its activity is
visible (STARTED, including PiP). Pause, completion, errors, audio-only mode, stopped
activities and released surfaces clear the request. Matching media/details prevent
stale or closed playback from keeping the screen awake.

The mini-player has an 80dp minimum height, a live 112×63dp preview on the left,
thumbnail artwork for audio-only playback, and separate open/play/pause/close
actions. The watch actions row scrolls horizontally and places **DeArrow Title**
immediately after Share, opening the existing contribution sheet. The watch-page
channel SponsorBlock shortcut was removed; Settings and channel-page entries remain.

| Check | Result |
| --- | --- |
| Android unit tests | 126 passed, including 5 new playback visibility scenarios |
| Debug APK build | Passed |
| Instrumentation APK build | Passed |
| Android debug lint | Passed; 0 errors, 40 warnings; none in the new source/test files |
| Native runtime, real screen timeout, layout and screenshots | Unverified; no connected devices and both emulator startup attempts exited with SIGSEGV (139) before boot |

`PlaybackVisibilitySmokeTest` adds five compiled device scenarios: moving mini-player
frames and watch/mini surface transitions preserving controller/position/tracks/queue;
audio-only artwork and independent controls; fullscreen/PiP/pause/background/settings
wake ownership; queue advancement/completion/replay; and a 5-second system timeout
exceeded by 12 seconds of uninterrupted playback. Its fifth scenario covers 320dp
and 390dp widths, light/dark mode, long titles and enlarged text, writing screenshots
to `/data/local/tmp/mobivious-playback-visibility-screenshots/`. Device settings changed
by these tests are restored in `finally` blocks. Existing DeArrow and SponsorBlock
tests now use the new button and retained channel-page entry respectively.

Device scenarios have **not executed**. The read-only Pixel_8_Pro AVD failed both
with SwiftShader and with GPU/Vulkan disabled. `adb devices -l` listed no device
after either attempt. Run `scripts/test-android.sh` on a working emulator/device
to verify real video frames, inactivity, PiP and layouts. No production server,
release signing configuration, database or stored preference format was changed.

## Playlist subscriptions and RSS — 4 October 2026

This revision implements parity rows 31 and 45 across the Android app and sibling
server. The public release remains 0.2.1; no production deployment, migration or
release publishing was performed.

| Check | Result |
|---|---|
| Android unit/MockWebServer suite | 135 passed, including 9 playlist/RSS scenarios |
| Debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed; dependency/existing style warnings remain |
| Crystal parser/core specs | 215 passed |
| Crystal standard specs | 48 passed |
| Guarded disposable PostgreSQL account/API harness | Passed, including migration 20 and playlist/RSS checks |
| Normal and API-only Crystal executable builds | Passed |
| Disposable API fixture HTTP/XML checks | Passed |
| English web headings with retained counts | Rendered production template assertions passed |
| Native emulator runtime/screenshots | Unverified: Pixel 8 Pro emulator exits with SIGSEGV before boot |

Server checks cover preserved legacy rows and repeated migration, concurrent
idempotent subscriptions and independent accounts, source metadata/video updates,
read-only ownership, actual unlisted privacy, caller-only legacy cleanup, late
refresh after unsubscribe, native scopes/cookie CSRF/no-store headers, account
cascades, private Atom authorization, empty/populated Atom namespaces and required
entry times, preserved mix seeds, and complete OPML in both formats. The OPML
fixture has 165 subscriptions, including one without cached channel metadata.
The web assertions render My playlists (0) and Subscribed playlists (2) with the
existing count spans; the layout and controls are retained.

Android tests cover ownership parsing/legacy fallback, playlist search without
video upload/duration filters, channel continuation/sorting, dedicated subscribe
routes and mix seeds, updated details, selected-instance RSS URLs, private-link
validation, XML request types, bearer-secret exclusion, stale account reads, and
distinct old-token/missing-server explanations. Four additional Compose fixture
scenarios compile for grouping/read-only controls and owner updates, unsubscribe
without stopping playback, guest sign-in intent and retry, discovery/channel
pagination/selected-tab restoration, and RSS sheets/account-change cleanup.
They could not execute because the installed emulator crashed before boot.

Physical-device layout, external RSS apps/shares, document-picker cancellation and
file-provider grants, and live YouTube playlist/mix extraction remain runtime
acceptance checks. Source/API fixture coverage is not a claim of those checks.
Deploy migration 20 and the server APIs, install a new app build and renew native
permissions by signing out and in before acceptance. Built-in RSS reading/polling
and upload alerts remain outside this revision.

## Signed release 0.3.0 — 4 October 2026

`versionName` is 0.3.0 and `versionCode` is 5. The release script passed
`testDebugUnitTest` (135 tests), `assembleRelease` and `lintRelease`.
The APK package remains `net.wingress.mobivious`; arm64-v8a, armeabi-v7a, x86
and x86_64 are included. The verified signing certificate matches the local
0.2.1 APK, whose SHA-256 was checked against the published GitHub asset digest.
This preserves in-place update compatibility.

The `.apk.sha256` file accompanies `Mobivious-0.3.0.apk` on the v0.3.0 release
and records the final uploaded APK checksum. Existing runtime limitations above
remain: emulator/device, external RSS app and document-picker acceptance checks
were not executed for this release. The server update at `742a4d004c4e869213618dc465e65ebf66007eed`
and migration 20 must be deployed separately, followed by renewed native sign-in.
No production server deployment or database migration was performed here.


## Read-only YouTube comments — 4 October 2026

| Check | Result |
| --- | --- |
| Android unit/API tests | 146 passed, including 11 comments tests |
| Debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed: no errors; existing/dependency warnings remain; no comments-file findings |
| Disposable localhost comments API fixture | Passed |
| New native Compose scenarios | Nine compiled; not executed |
| Native runtime, layout and screenshots | Unverified: no connected device; Pixel_8_Pro exited with SIGSEGV (139) before boot |

Comments tests cover modern and legacy author thumbnail formats, optional
metadata, creator hearts, reply continuations, unknown versus zero totals,
explicit YouTube-only requests without bearer credentials, encoded opaque tokens,
and captured-context rejection after an instance change. Controller tests verify
fetch-on-open, cached close/reopen and scroll positions, pagination overlap and
cursor-preserving retry, independent reply errors/pages, duplicate-load suppression,
late sort/reply responses, context/visibility changes and repeated continuation
termination. Link tests cover native timestamps/videos/playlists/channels,
redirect resolution, external URLs and unsupported schemes.

The fixture checks exercise complete metadata and rich-text/emoji payloads,
Top/Newest ordering, overlapping main/reply pages, encoded cursors, transient
failure/retry, concurrent delayed responses, empty responses and public request
headers. These checks validate the fixture/API flow, not native rendering or live
YouTube availability.

Six new playback-fixture Compose scenarios compile for spoiler-free opening,
metadata/read-only likes, cached reopening, reply navigation and scroll restoration,
pagination retry and sorting, initial errors/empty/hidden preference states,
delayed responses across sorting/video changes, and rich timestamp seeking in
paused/playing modes plus text expansion. Three independent presentation scenarios
compile for light/dark themes, rich formatting, 200% fonts at narrow width and wide
layouts. They have **not executed**. Both emulator startup attempts used no window,
no audio and no snapshot: one used SwiftShader; the other disabled GPU/Vulkan and
both virtual cameras. Android Emulator 37.2.12 exited with code 139 before boot
in both attempts. `adb devices -l` remained empty. No screenshots were produced;
interaction, accessibility and layout acceptance remain pending.

Repeat the static checks:

```bash
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

With a working emulator/device, run `scripts/test-android.sh`. Comments screenshots
are saved by the interaction tests to
`/data/local/tmp/mobivious-comments-screenshots/`; inspect them and repeat the drawer
flow in landscape. No production deployment, app installation, release/version
change, server API change, migration or new token permission was performed.

## Channel avatar visibility — 4 October 2026

| Check | Result |
| --- | --- |
| Android unit/API tests | 156 passed, including 10 avatar tests; zero failures/errors |
| Debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed: zero errors; no findings in the new avatar files |
| Focused Crystal avatar specs | Nine passed |
| Normal and API-only Invidious executable builds | Passed |
| Production frontend/native avatar cache fixtures | Passed, including zero-added-metadata-request assertions |
| Disposable localhost avatar API/image fixture | Passed |
| New native Compose scenarios | Eight compiled; not executed |
| Native runtime, layout and screenshots | Unverified: `adb devices -l` returned no connected devices |

Android tests cover singular/array/malformed avatar metadata across videos,
channels, playlists and comments; history retention; fixed-instance URL mapping;
path/query preservation; uniform sizing; instance-specific cache keys; Unicode
initials/person fallback; thin mode and owner suppression; unchanged metadata
request count; credential-free image requests and redirect refusal.

Crystal specs verify a single unique-ID cache lookup across nested response
records, supplied-image precedence and sharing, optional cache read/write failure
isolation, invalid-image rejection, preservation of other metadata and occurrence
indexes, and unchanged query values during image sizing. The production-template
fixture uses the real optional cache services and the existing metadata spy to
verify native response enrichment/cache learning without upstream requests. It
also retains the existing web avatar request-budget and failure checks.

The HTTP fixture validates discovery, channel, watch/recommendation, subscription,
playlist, history and comment avatar payloads. Metadata requests do not fetch
images. Explicit image requests preserve queries, use public cache headers, omit
bearer/cookie credentials, and return a controlled failure when requested. These
checks use local media and do not establish live YouTube image availability.

Four new presentation scenarios cover light/dark themes, 200% fonts at narrow
width, merged author navigation, fixed avatar sizes, thin-mode suppression and
creator-heart readability. Four interaction scenarios cover browsing/channel/watch
and queue reuse, comments/replies/hearts, subscription/history/playlist cards and
headers, and thin-mode/image failures. They have compiled but have **not executed**.
Screenshots are configured under `/data/local/tmp/mobivious-avatar-screenshots/`
when run on a working device. Native layout, accessibility and interactions remain
acceptance checks; compilation is not a substitute for those checks.

Repeat Android checks:

```bash
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

Run `python3 scripts/check-avatar-fixture.py` from this repository after generating
local test media with the existing Android fixture script. With a working device,
run `scripts/test-android.sh` and inspect the avatar screenshots.

Repeat focused backend checks in the sibling Invidious checkout:

```bash
crystal spec spec/invidious/frontend/channel_avatars_spec.cr spec/invidious/jsonify/channel_avatars_spec.cr
crystal run tests/frontend/render_fixtures.cr
crystal build src/invidious.cr -o /tmp/invidious-avatars
crystal build src/invidious.cr -D api_only -o /tmp/invidious-avatars-api
```

Full cached-list coverage needs the additive server API update and the existing
migration 21 cache, plus installation of a new app build. This change adds no new
migration, endpoint, token scope or sign-in requirement. Older servers retain
available response images and placeholders. Ordinary `/ggpht` downloads can still
contact YouTube's image CDN; no additional channel/video metadata lookup is added.
No production deployment, app installation, version change or release publication
was performed.

## Action controls and contextual menus — 4 October 2026

| Check | Result |
| --- | --- |
| Android unit/API tests | 156 passed; zero failures/errors |
| Debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed: zero errors, 42 warnings; none in the new action-control helper or presentation tests |
| Native interaction/layout checks | Compiled; not executed |
| Device screenshots | Unavailable: local emulator crashed before boot |

Channel, watch, playlist, Subscriptions and History menus retain existing action
callbacks, authentication, ownership restrictions, errors, Undo and confirmations.
Queue menus distinguish local removal from deletion of a saved playlist occurrence.
Standalone commands use visible buttons; setting choices use label/value rows with
dropdown arrows, and settings navigation uses chevrons. Action groups wrap, feed
sheets scroll, and the shared controls provide button semantics and 48 dp minimum
touch targets.

Five new presentation scenarios cover menu dismissal, nested-card interactions,
entity/account changes while a menu is open, disabled selectors, and light/dark
layouts with 200% fonts at narrow and wide widths. Two new fixture scenarios cover
nested playlist subscription and confirmed playlist deletion. Existing smoke tests
now navigate the menus for editing, SponsorBlock, blocking, RSS/OPML, history
clearing and both queue removal actions. The watch blocking scenario also checks
that failed writes remain visible after the menu closes and can be retried without
changing the playing media.

These native scenarios have **not executed**. Android Emulator 37.2.12 exited with
code 139 while starting Pixel_8_Pro in read-only mode with no window, audio or
snapshots, SwiftShader graphics and disabled cameras. `adb devices -l` found no
connected devices. Runtime interactions, accessibility, layout and screenshot
acceptance remain pending; successful compilation does not confirm them.

Repeat the static checks from `android/`:

```bash
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

With a working device, run `scripts/test-android.sh` and inspect the menus, selector
rows, expanded descriptions and wrapping action groups in light/dark themes,
portrait/landscape and at 200% font size. No server API, migration, token scope,
release/version, production deployment or app installation changes were made.

## Mobivious 0.4.0 release — 4 October 2026

The signed release APK was built with version name **0.4.0** and version code **6**
using the existing personal signing key. All 156 unit/API tests passed, and release
lint completed with zero errors and 42 warnings. `apksigner verify` passed; the
signer certificate matches the published 0.3.0 APK. The local reference APK's
SHA-256 was checked against the existing GitHub release asset before comparing
certificates.

Packaged manifest checks confirm application ID `net.wingress.mobivious`, minimum
SDK 26, target SDK 37, and arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries.
The APK and checksum file are `artifacts/Mobivious-0.4.0.apk` and
`artifacts/Mobivious-0.4.0.apk.sha256`. Verified APK SHA-256:

```text
efefc227101559977cf60d00be1942f96bb31c0d7a2b58c4b80e0a261c75175f
```

This release includes the comments drawer, avatar visibility and action-control
changes since 0.3.0. Native device/UI/accessibility and screenshot acceptance
remain unverified because the local emulator crashes before boot, as recorded
above. Full avatar coverage uses the additive sibling server update and existing
migration 21 cache; see `deploy/README.md`. Publishing the APK does not deploy that
server or apply database migrations.


## Codec-aware quality and shared preference — 4 October 2026

The Android implementation follows sibling commit `4ac0170`. Quality menus show
codec/FPS names with numeric bitrates and available file sizes; presentation
retains at most four entries per resolution/rounded-FPS group, while the full
supported catalog remains available to automatic selection, fixed defaults and
restoration. DASH selection begins in the service-owned track selector before
media loading. The service owns Auto/preset/manual state independently of track
overrides and account updates. Manual choices survive refresh/retry/reconnection;
new queue occurrences start with saved defaults.

| Check | Result |
|---|---|
| Android unit/API tests | 165 passed |
| Debug APK and instrumentation APK builds | Passed |
| Android debug lint | 0 errors, 42 existing warnings |
| Crystal native preference specs | 4 passed |
| Invidious normal/API-only builds | Passed |
| Guarded disposable account/API harness | Passed |
| Real H.264/AV1 fixture generation and first-frame decoding | Passed |
| Codec fixture HTTP, missing/unsupported manifests, request logs and preference reads | Passed |
| Device playback, settings UI and screenshots | Unverified: no connected device; Pixel_8_Pro crashed with SIGSEGV (139) before boot |

Unit coverage includes the four-entry cap, stable ties, unknown bitrates/codecs,
rounded FPS groups, codec fallback, resolution-before-codec ranking, summaries for
omitted representations, explicit Auto with one preferred candidate, preserved
manual identity, captured defaults and fallback when a manual stream disappears.
Client preference checks cover normalization, round trips, sparse PATCH writes and
GET refreshes. Server checks exercise all codec values, invalid-type rejection,
web form → native GET and native PATCH → web account state, preservation of
unrelated settings and account isolation. The account harness used the empty,
guarded `invidious_accounts_test` database on a disposable localhost PostgreSQL
container, which was removed afterward.

Four new instrumentation scenarios compile: initial DASH requests and resolution/
unsupported fallback; manual/Auto refresh, retry and recreation; menu details in
portrait/fullscreen and saved defaults on queue successors; and settings order
with account writes/refreshes. Existing video-menu assertions now use codec labels.
They have not executed, so native first-segment, decoder, layout and accessibility
acceptance remains pending. The emulator attempt used no window, audio or snapshots
and SwiftShader; its log is `/tmp/mobivious-codec-emulator.log`.

Repeat Android checks with `scripts/test-android.sh` on a working device. The
fixture generator adds short real AV1/H.264 renditions with codec-specific extrema,
a manifest without the preferred codec at 360p, and AV1 renditions deliberately
outside decoder capabilities. Request paths identify each representation. Server
checks use `crystal spec spec/native_preferences_spec.cr`, normal/API-only builds,
and the guarded account harness documented in the sibling tests/database README.

Deploy the sibling native `video_codec` PATCH extension before distributing the
updated app. Existing preference scopes/storage are reused; no migration, token
renewal, version bump or production deployment was performed.


## Home selection, subscribed channels and watch actions — 4 October 2026

Popular/Trending selection is now observable state and updates before the feed
request completes. Each request captures its selected feed/region. Subscriptions
retains its video feed/search and channel chips, with a Channels button above the
scrolling content. The separate channel directory sorts names ignoring case,
filters names locally as you type, retains search/position when returning from a
channel, and has independent loading, refresh, error and retry state. Subscription
reads/writes capture account/instance contexts; list state and directory search
reset on a context change. Playlist failures cannot prevent channel loading.
The watch row keeps Playback queue and removes its blocking overflow menu and
associated action error. Channel headers, cards and settings retain blocking.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 173 passed, including 8 new subscription scenarios |
| Android debug and instrumentation APK builds | Passed |
| Android debug lint | Passed; no errors; 32 existing/style/dependency warnings; no issues in the new subscription files |
| Disposable HTTP fixture checks | Passed: authorization, refresh failure/retry, subscribe/unsubscribe, delayed snapshots, empty channels and distinct discovery results |
| Fixture Python syntax and Git whitespace checks | Passed |
| Focused connected UI tests, screenshots and layout acceptance | Unverified: emulator exited with SIGSEGV before Android booted; connected tests reported no devices |

The new unit checks cover alphabetical ordering with equal names/distinct IDs,
trimmed case-insensitive substring search by name, query/loaded-channel retention
through errors, retries, duplicate response IDs, guest isolation, superseded
refreshes, account/server/generation changes, bearer-authenticated reads and
subscription writes, and rejected stale responses/writes. The localhost fixture
uses synthetic data and was stopped after its HTTP checks.

Five new full-app Compose scenarios and two presentation scenarios compile. They
cover immediate Home highlighting during delayed/rapid requests, default-home and
Activity recreation, access after feed scrolling, local search without requests,
Back and directory-position restoration, unsubscribe, retry/empty/guest states,
late reads after sign-out, and removal of the watch menu while Playback queue still
opens. Presentation scenarios exercise 320dp/390dp light/dark layouts, larger text,
accessible channel rows and thin-mode avatars. Existing blocking acceptance now
uses channel headers while keeping the player running. These scenarios have not
run on a device in this revision; no screenshots were produced or inspected.

With a working emulator/device and the localhost fixture available, rerun
`HomeSubscriptionsSmokeTest`, `SubscriptionsPresentationTest` and
`VisibilitySmokeTest`, plus existing subscription-search/avatar/RSS scenarios.
Full-app screenshots are saved under
`/data/local/tmp/mobivious-home-subscriptions-screenshots/` by the new smoke tests.
No server code, migration, token-scope, production account or release was changed
for this revision. Existing uncommitted player/codec work was preserved.

## Inline playback queue — 4 October 2026

Explicit playlist, mix and manually assembled queues now appear expanded inline
after watch metadata and before Up next. Ordinary video playback and implicit
recommendation continuation have no queue panel. The inline header replaces both
queue shortcuts and the bottom sheet. Expansion is scoped to the service queue
token and is retained through item changes, navigation, fullscreen return and
Activity recreation. The 288dp maximum-height viewport uses occurrence keys,
compact rows and an accessible highlighted current item. Playback following only
scrolls the internal list. Loading and retry remain available when video details
are absent and while the list is collapsed.

| Check | Result |
|---|---|
| Android unit/MockWebServer tests | 174 passed; zero failures, errors or skips |
| Debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed; zero errors, 32 existing warnings; no queue UI issues |
| Git whitespace check | Passed |
| Focused connected UI tests and screenshot acceptance | Unverified: emulator startup crashed; connected tests reported no devices |

The added unit regression distinguishes active explicit queues from standalone
playback and multi-item implicit continuation, including single-item, loading and
closed sessions. Six new presentation scenarios compile and cover visibility,
duplicate-occurrence selection, bounded internal following without outer scrolling,
collapse/advance/reopen, recovery without details, paging, disabled Mix Repeat All,
48dp item actions, 320dp dark/2x text and 390dp light/1.3x text layouts. Three new
fixture-backed smoke scenarios compile and cover manual/single-item creation,
collapse across advancement/navigation/fullscreen/recreation, new-session expansion,
recommendations disabled, implicit advancement, launch-link recreation without a
session restart and queue shortcut removal. Activity recreation now reconnects to
an active service queue instead of replaying its original launch link. Existing
queue/library, watch visibility, guest watch actions and avatar-cache tests were
updated for inline access.

These instrumentation scenarios have **not run**. `adb devices -l` showed no
devices. Android Emulator 37.2.12 exited with SIGSEGV (139) before boot using the
existing Pixel_8_Pro AVD in read-only/no-snapshot mode with SwiftShader. The focused
`connectedDebugAndroidTest` task failed with `No connected devices!`. No new native
screenshots were produced or inspected; touch scrolling, large-font layouts,
accessibility interactions and actual player transitions still need device
acceptance.

With a working emulator/device, run `scripts/test-android.sh`, including
`PlaybackQueuePresentationTest`, `QueueLibrarySmokeTest`,
`HomeSubscriptionsSmokeTest`, `AvatarsSmokeTest` and `VisibilitySmokeTest`.
The new queue tests save screenshots under
`/data/local/tmp/mobivious-queue-screenshots/`. No server, wire-format, queue-command,
storage migration, production deployment or signed release changes were made.

## Native account parity, global search and flexible playback — 4 October 2026

Account replaces Search in the fixed Home/Subscriptions/Library/Account bottom
navigation. Settings is available inside Account for guests and members. Native
signup follows instance availability and CAPTCHA; account management supports
password-confirmed credential changes/deletion, opaque session metadata and
revocation, and guided/advanced scoped tokens with expiry choices and one-time
Copy. Credential changes issue an atomic replacement mobile session and preserve
the account identity/library. Password typos retain the bearer session. New
account requests and responses reject changed account/instance contexts.

The top-right search field opens without navigation or a search request. Submit
uses the existing results, filters, pagination, playlist/mix and link handling.
Back closes the field before returning from results to the originating browse
snapshot/position; contextual sign-in returns to its interrupted screen/action.
Search homepage and saved web feed preferences remain supported. Channel,
subscription and history search remain within their existing screens.

Playback geometry uses Media3 decoded dimensions and pixel aspect ratio, bound to
the current media item and refreshed on size changes/reconnection. Regular watch
height follows the ratio up to 70% of content height, or 40% with comments.
Fullscreen follows video shape; square/unknown dimensions follow device
orientation. Both PiP entry paths use a bounded ratio with exact platform endpoints.
Aspect-preserving fit, bounded mini-player and service-owned playback are retained.
Short ultrawide players put Play/Pause in the footer to avoid overlapping controls.

| Check | Result |
| --- | --- |
| Android unit/MockWebServer tests | 184 passed; zero failures, errors or skips |
| Android debug app and instrumentation APK builds | Passed |
| Android debug lint | Passed; zero errors, existing warnings only |
| Invidious normal build | Passed with `-Dskip_videojs_download` |
| Invidious API-only build | Passed with `-Dapi_only -Dskip_videojs_download` |
| Invidious specs | 224 Spectator examples and 49 standard examples passed; no failures/errors |
| Disposable PostgreSQL account harness | Passed against a fresh `invidious_accounts_test` database |
| Disposable HTTP account/shape fixture checks | Passed; ffprobe confirmed all four generated video dimensions |
| Python syntax and both repository whitespace checks | Passed |
| Connected instrumentation, native screenshots and real decoding acceptance | Unverified: emulator exited with SIGSEGV (139) before boot; connected task reported `No connected devices!` |

New API/unit coverage includes registration/CAPTCHA contracts, credential/session/
token requests, incorrect current passwords versus expired sessions, old endpoints
and scopes, delayed login/token responses, preflight context rejection, guided and
advanced scope validation, portrait/comments caps, square/anamorphic/unknown
geometry and exact PiP bounds. The account harness uses production middleware and
transactions for duplicate signup, materialized-view failure rollback, session
ownership, credential-change/login races, identity/subscription preservation,
CAPTCHA endpoint binding/expiry/replay, delegated permissions and current-session
revocation/deletion. CAPTCHA image rendering was not exercised on this host because
`rsvg-convert` is unavailable; the existing server Dockerfiles supply that runtime.

Five new fixture-backed instrumentation scenarios compile. They cover fixed tabs
and guest Settings, search cancellation/submission/query editing/Back (including
Account subpages), contextual signup, password typo/rename, browser revocation,
scoped token creation and deletion confirmation. Real-media scenarios cover inline
sizing, the comments cap, shape-aware fullscreen and manual PiP, queue transitions
between portrait and landscape, service-session retention and Activity recreation.
Existing settings, search/history, watch/library and contextual save tests were
updated for Account entry. These scenarios have **not run**; no screenshots were
produced or inspected. Automatic PiP entry and selection retention still need
runtime acceptance along with touch, keyboard, accessibility and large-font layout.

Reproduction: Android used `:app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest :app:lintDebug --offline`; server checks used
`crystal spec`, normal/API-only builds and `tests/database/accounts.cr` with
`ACCOUNT_TEST_DATABASE_URL` targeting a disposable PostgreSQL 14 container.
Run `scripts/test-android.sh` on a working emulator/device to start the localhost
fixture, generate portrait (360×640), square (480×480), landscape (640×360) and
ultrawide (960×360) AAC/H.264 MP4s, reverse port 18080 and run connected tests.
The localhost fixture and disposable database container were removed after checks.

Feature-parity rows 03, 09, 25 and 26, summary counts, API and deployment documents
were updated in their respective repositories. This addition uses existing account/
session tables and requires no new migration. Deploy the sibling server update
and renew mobile sign-in for the explicit management scopes before production use.
No production account, deployment, migration, signing version or release publishing
was changed.


## Mobivious 0.5.0 release preparation — 4 October 2026

The release version is 0.5.0 with version code 7. `scripts/build-release.sh`
passed all 184 unit/API tests, built the signed release APK and passed release
lint (zero errors, 42 warnings). `apksigner verify --print-certs` verified the APK
and confirmed the same signer certificate as the published 0.4.0 APK, preserving
in-place updates. APK metadata confirms `net.wingress.mobivious`, Android 8+
(minimum SDK 26), target SDK 37 and all four supported CPU architectures.

Release files are `artifacts/Mobivious-0.5.0.apk` and its `.apk.sha256` file.
The APK SHA-256 is
`73609c994bb2f4a61a8e61c1f80a9ddfe87fcd1a3da930a8636f6db028eeb072`.
The planned GitHub tag is `v0.5.0`; release notes include changes since 0.4.0,
server compatibility, renewed sign-in requirements and the device-validation
limitation. Native runtime acceptance remains unverified as described above.
Preparing or publishing this APK does not deploy the sibling server.

## Player gestures and presentation animations — 4 October 2026

The player now uses one saveable closed/mini/watch/fullscreen presentation state
and a shared host above the browsing scaffold. Watch and mini-player slots contain
layout/chrome; the host owns the live video surface and interpolates its bounds
while dragging. Browsing stays composed beneath the watch page. Watch scroll and
description state are retained by queue occurrence. The existing service/controller,
selection, queue and screen-awake rules remain responsible for playback.

Downward watch swipes minimize; downward fullscreen swipes return to watch first.
Mini-player upward swipes or preview/title taps restore watch. Sideways mini-player
swipes dismiss and send the existing close command after settling. Short/canceled
drags spring back without bounce. Vertical completion uses 64dp; horizontal
completion uses 35% of row width. A directional 1,000dp/second fling also completes
after 24dp. Touch slop and dominant-axis locking separate taps from drags; controls
keep their hit areas. Claiming a drag cancels pending tap/seek batches and restores
playing intent. PiP, modal interaction, touch exploration, rotation, media changes
and lifecycle interruption disable or cancel presentation drags. Unclipped anchors
and current measured callbacks prevent dismissal drift or stale size thresholds.

A visible minimize control, accessibility actions and keyboard mini-player restore
provide alternatives to dragging. Description and queue disclosures animate their
height/opacity and chevrons over 200ms. Spring/disclosure animations use Compose's
system animation-duration handling. Media3's Compose surface synchronization
workaround is enabled for the existing SurfaceView rendering path.

| Check | Result |
| --- | --- |
| Android unit/API tests | 190 passed; zero failures, errors or skips, including 6 new gesture/state tests |
| Debug APK | Passed; `android/app/build/outputs/apk/debug/app-debug.apk` |
| Instrumentation APK | Passed; 6 added real-media gesture scenarios compile |
| Debug lint | Passed; zero errors, 32 existing warnings |
| Repository whitespace check | Passed |
| Connected player tests and screenshot/layout acceptance | Unverified: emulator exited with SIGSEGV (139) before boot; connected task reported `No connected devices!` |

The added device scenarios cover watch/mini swipes retaining the same PlayerView,
controller, position, selections and queue; moving minimized frames; watch disclosure
and scroll retention; drag-following geometry; partial/canceled drags; short preview
and title drags; dismissal in both directions; accumulated-seek cancellation;
fullscreen's two-step collapse; buttons/accessibility equivalents; timeline,
comments/queue scroll priority; Activity recreation; and animation scale zero.
Existing scenarios continue to cover audio-only mode, queue advance, PiP/background,
screen wake and narrow/light/dark/large-font screenshots. These device scenarios
have **not executed**, and no new screenshots were produced or inspected.

Final compiled checks:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

The final log is `/tmp/mobivious-gestures-final-checks.log`; the failed read-only
Pixel_8_Pro attempt used no window/audio/snapshots, SwiftShader and disabled Vulkan
and cameras, with log `/tmp/mobivious-gestures-emulator.log`. On a working device,
run `scripts/test-android.sh` to supply the local fixture and real generated media.
The focused connected class is `net.wingress.mobivious.PlaybackVisibilitySmokeTest`.
No server, stored preference format, dependency version, signing/version metadata,
production deployment or release publication was changed.

## Smooth watch resizing and complete channel descriptions — 4 October 2026

The embedded player now interpolates between its aspect-ratio-fitted 70% and 40%
height limits while scrolling watch details. The first 24dp stays expanded; the
next 96dp controls resizing directly. A nested-scroll coordinator consumes only
the resizing distance and leaves the remainder for the list. Its saveable progress
is independent of viewport remeasurement, preventing short-page resize loops,
and is scoped to the current queue occurrence. Comments still use the 40% cap,
and the existing single live PlayerView/controller owns playback throughout.

Channel headers keep the three-line preview and offer Read full description for
nonblank text. A bounded Material sheet displays all existing API description
text with line breaks and selection, a fixed Close button and a scrollable body.
It is owned outside the lazy header and dismissed on channel/instance changes.

| Check | Result |
| --- | --- |
| Android unit/API tests | 197 passed; no failures, errors or skips |
| Debug APK and instrumentation APK | Compiled successfully |
| Debug lint | Passed; zero errors, existing warnings only |
| Fixture Python syntax and repository whitespace | Passed |
| Native UI, decoded playback and screenshot acceptance | Unverified: the read-only Pixel_8_Pro emulator exited with SIGSEGV (139) before boot; ADB lists no connected device |

Seven new unit scenarios cover fitted-height interpolation, smaller/unknown video
dimensions, comments and progress bounds, the 24dp/96dp thresholds, scroll
remainders, near-top reversal, short-page clamping, large fling deltas and restored
progress. New compiled device scenarios cover active-drag sizing, flings, short
pages, comments, minimization, state restoration, new occurrences and smaller
landscape video. Channel scenarios cover complete text/final-line visibility,
Close/Back/swipe dismissal, blank/short descriptions, large fonts and reading
cached metadata without additional requests. A real-media scenario checks the
same live surface, controller, position, paused/playing intent, selections and
queue during resizing/minimization. These native scenarios have **not executed**;
no screenshots were produced or inspected.

Validation used `:app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain` with the
existing JDK/SDK. The build log is `/tmp/mobivious-resize-checks.log` and the emulator
log is `/tmp/mobivious-resize-emulator.log`. Run `scripts/test-android.sh` on a working
device for fixture-backed acceptance; the focused classes are
`WatchPlayerResizePresentationTest`, `ChannelDescriptionPresentationTest`,
`ChannelSmokeTest` and `PlaybackVisibilitySmokeTest`.

No server API, migration, dependency, signing/version metadata, production
deployment or release publication was changed.

## Mobivious 0.5.1 release preparation — 4 October 2026

The release version is 0.5.1 with version code 8. `scripts/build-release.sh`
passed all 197 unit/API tests, built the signed release APK and passed release
lint (zero errors, 42 existing warnings). APK metadata confirms
`net.wingress.mobivious`, Android 8+ (minimum SDK 26), target SDK 37 and all four
supported CPU architectures. `apksigner verify --print-certs` passed and confirmed
the same signing certificate as the published 0.5.0 APK, preserving in-place
updates. The previous local APK was checked against the GitHub asset's SHA-256
before comparing certificates.

Release files are `artifacts/Mobivious-0.5.1.apk` and its `.apk.sha256` file;
`sha256sum -c` passed. The APK SHA-256 is
`2b7c8185d909e6f6ee30501b501ff29353db5dc80ef2e941834913667eb73458`.
The release tag is `v0.5.1`; its notes cover player resizing, complete channel
descriptions, player gestures and presentation/disclosure animations since 0.5.0.
The build log is `/tmp/mobivious-0.5.1-release.log`, and local verification metadata
is `artifacts/release-verification-0.5.1.json`. Native device acceptance remains
unverified as recorded above. This release requires no new server API or migration.

## Channel tabs and community posts — 5 October 2026

Channel browsing now uses the available Videos, Shorts, Streams, Podcasts,
Releases, Courses, Playlists, Posts and Channels tabs in website order. Models
normalize community/posts, retain the Videos fallback and parse optional banners,
verification, pronouns and rich descriptions. Video tabs share Newest/Oldest/Popular;
playlist sorting is separate and controls are hidden for endpoints that ignore it.
Tab/sort changes restart pagination, while search and child navigation retain
loaded content and scroll positions. Clips remain deferred.

Posts have a dedicated native page with rich text, author/publication/edit metadata,
likes, comment counts, image galleries/enlarged viewers, video/playlist attachments,
read-only polls/quizzes and unknown-attachment fallback. Post comments use a typed
target and an independent controller in a sheet, including Top/Newest, replies,
saved scroll positions, loading/empty/error states and cursor-preserving retry.
Shared/pasted/rich-text post and legacy community links resolve natively, including
missing channel IDs. Post navigation minimizes playback; launch intents are consumed
once by the retained Activity view model. Video comments retain their own state and
visibility preference.

| Check | Result |
| --- | --- |
| Android unit/API/controller tests | 213 passed; zero failures, errors or skips |
| Debug APK | Passed; `android/app/build/outputs/apk/debug/app-debug.apk` |
| Instrumentation APK | Passed; ten new Compose scenarios compile |
| Debug lint | Passed; zero errors, 34 existing dependency/style warnings |
| Disposable community fixture | Passed: nine result types, header metadata, paging, attachments, detail resolution/retry, comment sorting/replies, public requests and proxy images |
| Existing avatar fixture regression | Passed: image placement, deferred requests, cache headers, query preservation, credentials and failures |
| Python fixture syntax and repository whitespace | Passed |
| Native UI, playback continuity, intents and screenshots | Unverified: Pixel_8_Pro emulator 37.2.12 exited with SIGSEGV (139) before boot; no ADB device was connected |

New parser and MockWebServer coverage includes every tab/result kind, all video
sorts and playlist-tab contracts, optional metadata, known/unknown/missing media,
link-origin validation and post-channel resolution. Requests remain public without
bearer tokens. Controller tests cover independent video/post state, opaque cursors,
overlapping pages, repeated continuations, failed-page retry, restored pending reply
loads and delayed responses superseded by target, sorting or account/instance changes.

Seven fixture-backed scenarios in `CommunitySmokeTest` cover tab switching, shared
video sorting, scoped/global search and child restoration, post pagination/retry,
galleries/polls/quizzes, sheet/reply/Back navigation, stale responses, shared links,
playback/video-comment independence and Activity recreation both on a post and
after leaving it. Three `CommunityPresentationTest` scenarios cover narrow layouts,
large fonts, light/dark modes, thumbnail-free identity presentation, clickable rich
descriptions, missing gallery images and accessible sheet controls. These device
scenarios have **not executed**; no new screenshots were produced or inspected.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  --offline --console=plain
cd ..
python3 scripts/check-community-fixture.py
python3 scripts/check-avatar-fixture.py
git diff --check
```

The final build log is `/tmp/mobivious-community-final-checks.log`; the failed
emulator attempt is recorded in `/tmp/mobivious-community-emulator.log`. On a
working emulator/device, run `scripts/test-android.sh` to generate local media,
start the fixture, reverse its localhost port and run connected scenarios.
Both fixture checks use temporary localhost servers and shut them down on exit.

Existing sibling APIs provide posts in `comments`, related channels in
`relatedChannels`, and playlist tabs in `playlists`; post comments receive the
resolved channel ID as `ucid`. No server code, migration, token scope, dependency,
signing/version metadata, production deployment or release publication was changed.
The signed 0.5.1 release recorded above predates these working-tree changes.


## Rich video information and content links — 5 October 2026

Rows 15 and 24 now implement the accepted non-clip native scope. Watch renders
selectable rich descriptions, links/timestamps/hashtags and optional likes,
verification/subscribers, notices, genre, license, family-friendly/region metadata
and music credits. Incoming, pasted and rich-text content use one router with
native channel resolution and hashtag paging. Direct Share retains service
position, matched source occurrence and active link overrides. End boundaries
use absolute video coordinates, with service-owned clamp/pause/replay/loop.
Listen, speed and proxy carry through queue successors; other URL overrides
stay on the linked occurrence without writing saved preferences. Bare source
links keep browsing first and apply their options when Play is chosen.

| Check | Result |
| --- | --- |
| Android unit/API tests | 224 passed; zero failures, errors or skips, including 9 content-link and 2 metadata tests |
| Android debug and instrumentation APKs | Passed; seven new link smoke and three presentation scenarios compile |
| Android debug lint | Passed; zero errors, existing dependency/style warnings |
| Localhost content-link fixture | Passed: rich metadata, public channel resolution/retry, hashtag paging, local/region request tracing and older-response fallback |
| Invidious normal build | Passed with `-Dskip_videojs_download` |
| Invidious regular/scheduled video extraction specs | Three examples passed; zero failures |
| Production video serializer and frontend template fixtures | Passed: empty/custom license, rich metadata and music-credit assertions plus existing fixture regressions |
| Python fixture syntax and whitespace checks in both repositories | Passed |
| Connected native tests and screenshot acceptance | Unverified: Pixel_8_Pro emulator 37.2.12 exited with SIGSEGV (139) before boot; the focused connected task failed with `No connected devices!` |

Unit tests cover accepted/rejected origins, route aliases, YouTube index conversion,
timestamp precedence/precision/overflow, explicit false/empty overrides, bounds,
source duplicates/insertions/unresolved seeds, mix continuation, account-instance
isolation, encoded public resolution/hashtag requests and unknown optional metadata.
The production serializer assertions exercise the actual full video response and
its empty Standard YouTube/custom license semantics. No extraction request is
added by exposing the existing `Video.license` value.

Compiled device scenarios cover shared channel aliases and retry without search,
hashtag pagination with playback continuing, rich metadata and same-occurrence
seeking, end pause/replay/refresh, bounded loop/background playback, Activity
recreation and carried-only successor settings, and browse-first source links.
Presentation scenarios cover narrow/dark/200%-font metadata and region/credit
expansion, HTML link targets, plain-text timestamps/hashtags and script removal.
These device scenarios have **not executed**. No new native screenshots were
produced or inspected; real intents, Android choosers, navigation restoration,
playback bounds, PiP/background controls and layout still need device acceptance.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
cd ..
python3 scripts/check-content-links-fixture.py
cd ../invidious
CRYSTAL_CACHE_DIR=/tmp/mobivious-rich-links-crystal-cache \
  crystal build src/invidious.cr -Dskip_videojs_download -o /tmp/invidious-rich-links
CRYSTAL_CACHE_DIR=/tmp/mobivious-rich-links-crystal-cache \
  crystal spec spec/invidious/videos/regular_videos_extract_spec.cr \
  spec/invidious/videos/scheduled_live_extract_spec.cr
CRYSTAL_CACHE_DIR=/tmp/mobivious-rich-links-crystal-cache \
  FRONTEND_FIXTURES=/tmp/invidious-rich-links-fixtures \
  crystal run tests/frontend/render_fixtures.cr --error-trace
```

The Android build log is `/tmp/mobivious-rich-links-checks.log`. Server build,
extraction and serializer logs use `/tmp/mobivious-rich-links-server-*.log`;
the failed emulator startup is `/tmp/mobivious-rich-links-emulator.log` and the
focused connected-task failure is `/tmp/mobivious-rich-links-connected.log`.
On a working emulator/device, run `scripts/test-android.sh` to supply generated
media and the local fixture, including `ContentLinksSmokeTest` and
`VideoInformationPresentationTest`. The standalone fixture checker uses a
temporary localhost server and shuts it down on exit.

The sibling server adds only the optional public `license` video field. Existing
public `/api/v1/resolveurl` and `/api/v1/hashtag/:tag` contracts supply navigation;
older instances may lack them and will show the existing retry/error handling.
No migration, authentication scope, dependency, signing/version metadata,
production deployment or release publication changed. The signed 0.5.1 APK
predates these working-tree changes. License display needs the sibling update
deployed; older servers remain usable with that optional metadata hidden.

## Manual chapters — 5 October 2026

Chapters are derived from the description in the existing video details response.
There are no chapter API calls, automatic-chapter lookups, storyboard downloads,
new dependencies, server changes, authentication scopes or migrations. The web
preview investigation reproduced a relative-URL resolution bug locally and
confirmed that deployed static preview assets match the checkout; it did not
establish the cause of the reported blank image. The web player is unchanged.

| Check | Result |
| --- | --- |
| Android unit/API tests | 230 passed; zero failures, errors or skips, including six chapter tests |
| Debug and instrumentation APK builds | Passed; four chapter presentation and seven service/playback scenarios compile |
| Android debug lint | Passed; zero errors, existing dependency/style warnings |
| Localhost chapter fixture | Passed: loaded description, optional storyboard metadata, unchanged legacy responses and forbidden-preview request tracing |
| Existing localhost content-link fixture | Passed: rich video metadata, resolution/retry, hashtag pagination and request tracing |
| Native chapter scenarios and screenshots | Unverified: Android Emulator 37.2.12 exited with SIGSEGV (139) before boot; no connected device |

Unit tests cover minute/hour timestamps, bullets and separators, Unicode titles,
sorting and first-duplicate precedence, overflow/malformed/missing/out-of-duration
entries, insufficient chapters, live videos, plain-text titles, ignored automatic
chapter/storyboard fields, exact current-chapter boundaries and runtime duration
filtering. The parser preserves the web fork's two-chapter minimum and later-start
support rather than YouTube's creator publishing rules.

Compiled presentation scenarios cover revealing the active chapter on open,
timestamp selection without closing the list, manual scroll without automatic
playback scrolling, save/restore, distinct occurrences of the same video and
instance resets, long/RTL/200%-font titles, separated SponsorBlock labels,
keyboard selection and disabled seek actions. Compiled service scenarios cover
paused/playing intent, real timeline scrubbing, fullscreen sheet/Back, Activity
recreation and presentation transitions, Comments/Chapters replacement,
duplicate queue occurrences, minimization, live exclusion and PiP dismissal.
The request-count scenario asserts no added video-detail, storyboard or image
requests when opening, selecting and scrubbing. It configures the localhost
instance before Activity creation so test startup cannot browse the live server.

These eleven native scenarios have **not executed**. No new native screenshots
were produced or inspected. Layout, gestures, keyboard/TalkBack behavior,
paused/playing transitions, restoration and per-action request counts still need
device acceptance. The standalone fixture check validates the response and tracer,
not the Android UI; its two intentional forbidden-preview probes are entirely
local and are refused without any upstream access.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
cd ..
python3 scripts/check-chapters-fixture.py
python3 scripts/check-content-links-fixture.py
```

On a working emulator/device, `scripts/test-android.sh` supplies the generated
media and localhost fixture and runs the native checks, including
`ChaptersPresentationTest` and `ChaptersSmokeTest`. The new runtime scenarios
remain subject to that device verification. Row 18 is Partial; timeline thumbnail
previews are deferred to avoid YouTube image-CDN requests. The signed 0.5.1 release
predates this change; no release or deployment was performed.

## Signed release 0.5.2 — 5 October 2026

The signed 0.5.2 APK includes the channel/community, rich video information,
content-link and chapter changes since 0.5.1. Version code is 9; application ID
remains `net.wingress.mobivious`. The APK supports Android 8+ (minimum SDK 26,
target SDK 37), includes arm64-v8a/armeabi-v7a/x86/x86_64 and is not debuggable.

| Check | Result |
| --- | --- |
| Android unit/API tests | 230 passed; zero failures, errors or skips |
| Signed release build | Passed with the existing local signing key |
| Release lint | Passed; zero errors, 36 warnings |
| APK package/version/SDK/ABI metadata | Passed |
| APK signature | Passed; certificate SHA-256 matches signed 0.5.1 |
| APK checksum file | Passed with `sha256sum -c` |
| Native device acceptance | Still unverified: the previously documented emulator crash prevents boot |

APK SHA-256:
`23f0ede5d3bce29da5e6065ede7077a09d8f022c75c4556b2d6df763799ab52c`.
Signing certificate SHA-256:
`5673702abf411cf4aa9b85e2fe952a658b0611e813c9689603b47c475204ff87`.
Build log: `/tmp/mobivious-release-0.5.2-build.log`.
Local verification metadata: `artifacts/release-verification-0.5.2.json`.
The [0.5.2 release](https://github.com/Hanif-F/codex-mobile-invidious/releases/tag/v0.5.2)
provides the signed APK and its checksum file.

Reproduction:

```sh
scripts/build-release.sh
JAVA_HOME=/opt/android-studio/jbr "$HOME/Android/Sdk/build-tools/36.0.0/apksigner" \
  verify --print-certs artifacts/Mobivious-0.5.2.apk
cd artifacts
sha256sum -c Mobivious-0.5.2.apk.sha256
```

Chapters still use only the loaded description; thumbnail previews remain
deferred and the web preview is unchanged. This release does not deploy the
sibling server. Its additive license field remains a separate deployment
requirement for displaying video licenses; older instances retain optional-field
fallbacks. Historical unreleased/version statements above describe the earlier
implementation stages.

## Video-opening crash fix and signed 0.5.3 APK — 5 October 2026

The 0.5.2 chapter timestamp regex uses the JVM-only `(?U)` flag. Android's ICU
regex backend rejects that pattern. Every `VideoDetails` construction initializes
`ChapterRules`, including videos without chapters or descriptions, so the invalid
pattern can raise `ExceptionInInitializerError` during video loading. Desktop JVM
tests passed because their regex engine accepts the flag. The user's handset
stack trace was not available; the regex incompatibility was reproduced locally.

0.5.3 replaces that flag and implicit Unicode whitespace matching with the
portable class `[\p{Z}\u0009-\u000D\u0085]`. Chapter extraction, timestamps,
ordering, duplicate handling and validation remain covered by existing tests.
One new unit test covers non-breaking spaces, em spaces, tabs, NEL and Japanese
titles. Six new `ChaptersRuntimeTest` instrumentation scenarios construct video
details on Android with missing, empty and ordinary descriptions, manual chapters,
Unicode whitespace and live videos. They require no Activity, server or media
fixture.

| Check | Result |
| --- | --- |
| Android unit/API tests | 231 passed; zero failures, errors or skips |
| Debug and instrumentation APK builds | Passed; six new runtime regression tests compile |
| Debug lint | Passed; zero errors, 26 warnings; none in the changed parser or new runtime test |
| Signed release build and release lint | Passed; zero lint errors, 36 warnings |
| ICU 77 regex reproduction | Original pattern rejected with `U_REGEX_RULE_SYNTAX`; both fixed patterns compile; six representative matching cases passed |
| APK package/version/SDK/ABI metadata | Passed: `net.wingress.mobivious`, version 0.5.3/code 10, minimum SDK 26, target SDK 37, four supported ABIs, not debuggable |
| APK signature | Passed; certificate SHA-256 matches signed 0.5.2 |
| APK checksum file | Passed with `sha256sum -c` |
| Android runtime, cold launch and video-tap acceptance | Unverified: emulator exited with SIGSEGV (139) before boot; focused connected tests failed with `No connected devices!` |

The emulator attempt used the existing read-only Pixel_8_Pro AVD without snapshots,
window, audio, cameras or GPU rendering, with Vulkan disabled. Android parser tests,
`AppSmokeTest` video-opening checks and `ChaptersSmokeTest` playback checks were
selected for connected execution, but none could run without a device. The host
ICU probe is additional compatibility evidence, not Android runtime acceptance.

APK SHA-256:
`c760b1ce260f7dddcc8c94cc486dc674d6b5c2c773f6fc720a9ef5fcc62a6b37`.
Signing certificate SHA-256:
`5673702abf411cf4aa9b85e2fe952a658b0611e813c9689603b47c475204ff87`.
The existing signing key was reused. Local artifacts are
`artifacts/Mobivious-0.5.3.apk`, `artifacts/Mobivious-0.5.3.apk.sha256` and
`artifacts/release-verification-0.5.3.json`. No GitHub publication or server change
was performed.

Build logs are `/tmp/mobivious-release-0.5.3-build.log` and
`/tmp/mobivious-crash-0.5.3-debug-checks.log`; runtime-attempt logs are
`/tmp/mobivious-crash-0.5.3-emulator.log` and
`/tmp/mobivious-crash-0.5.3-connected-checks.log`. The ICU probe log is
`/tmp/mobivious-crash-0.5.3-icu-check.log`.

Reproduction:

```sh
scripts/build-release.sh
cd android
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
# This parser-only run needs a connected Android device, but no fixture server.
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.ChaptersRuntimeTest \
  --offline --console=plain
cd ..
```

With a working Android device, run `scripts/test-android.sh` to supply the localhost
fixture and execute playback scenarios. Also install the signed release as an
update to 0.5.2, force-stop and reopen it, and tap videos with and without chapters
to confirm playback without a fatal exception.

## Compact player controls and description metadata — 2026-10-05

The watch/fullscreen footer now places playback time, the clickable chapter title,
settings and fullscreen on one row below the timeline. The chapter chevron and
separate chapter row are removed. Long chapter titles use a single-line ellipsis;
fixed icon targets and a minimum chapter target take priority when space is tight.
Play/pause/replay stays centered on the video, using a smaller button for short
players. SponsorBlock scrub labels remain temporary timeline feedback.

Video metadata follows the description text and shares its existing expansion
toggle. The separate Video details heading, toggle and expansion state are removed.
Genre links, region expansion and music credits remain available.

| Check | Result |
| --- | --- |
| Android JVM unit tests | Passed: 231 tests, no failures or errors |
| Debug application and instrumentation APK builds | Passed |
| Debug lint | Passed: 0 errors, 26 warnings |
| Focused connected presentation tests | Passed: 13 tests on the user-started Pixel_8_Pro emulator, Android 16 / API 36 |
| Fixture-backed playback and content-link smoke tests | Passed: 4 tests, including real normal/fullscreen control geometry, chapter selection/scrubbing, fullscreen sheet Back handling, and description links/visibility |
| Native player layout, interactions and screenshots | Inspected normal/fullscreen playback and integrated description metadata; normal chapter title opened its panel with an ADB touch |

Four new control presentation scenarios cover footer ordering, long-title
ellipsis, RTL and large fonts, normal/fullscreen layouts, short-player centering,
chapter updates and keyboard actions, playback states, and absent chapters/live
duration labels. Two new description scenarios cover shared metadata visibility,
genre links, empty descriptions and absent metadata. Existing chapter/information
presentation and content-link smoke scenarios were updated, and a new playback
smoke scenario checks the actual player footer and center button across fullscreen.
All 17 focused device scenarios passed together with no failures, errors or skips.
Keyboard scenarios explicitly enter keyboard input mode before requesting focus.
The fullscreen geometry scenario waits for Android's landscape configuration and
player bounds before tapping the chapter title; checking fullscreen chrome alone
was insufficient to synchronize with the platform rotation.

Device playback initially uncovered an independent `CodecAwareTrackSelector`
array-bounds crash. Media3 supplies a format-support bucket for unmapped groups
after the actual renderer buckets. The selector now leaves that bucket and
non-video renderer supports unchanged, filtering only video renderer groups.
The fixture DASH playback smoke tests passed after this guard was added.

The read-only Pixel_8_Pro emulator (37.2.12) exited with SIGSEGV (139) despite a
cold boot, software graphics and disabled Vulkan. Host crash diagnostics identify
`qemu-system-x86_64-headless` as the crashing process; `emulator -accel-check`
reports KVM installed and usable. The underlying emulator crash cause is not
established. This happened before the application could be installed or run.
The user's normal GUI launch subsequently booted successfully as `emulator-5554`,
allowing APK installation, playback tests and screenshots. Android's first-time
immersive-mode tutorial was dismissed during manual fullscreen inspection. The
original headless-launch crash remains unexplained; it did not recur on this
GUI-started emulator.

Screenshots are stored locally in the ignored directory
`.tools/verification/compact-player-2026-10-05/`: `normal.png`, `fullscreen.png`
and `description.png`. Manual inspection used the emulator's native display
and default font/layout direction. Narrow widths, large fonts and RTL were
verified by the Compose presentation scenarios, rather than by manual screenshots.
No physical-device or complete instrumentation-suite run was performed.

Build, emulator and connected-test logs are
`/tmp/mobivious-compact-player-checks.log`,
`/tmp/mobivious-compact-player-emulator.log` and
`/tmp/mobivious-compact-player-connected-checks.log`. The successful final combined
unit/build/lint/device run is `/tmp/mobivious-compact-player-final-checks.log`.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.PlayerControlsPresentationTest,net.wingress.mobivious.ChaptersPresentationTest,net.wingress.mobivious.VideoInformationPresentationTest,net.wingress.mobivious.ChaptersSmokeTest#inlineFooterAndCenteredPlaybackStayInPlaceAcrossFullscreen,net.wingress.mobivious.ChaptersSmokeTest#selectingAndScrubbingPreservePlaybackAndMakeNoPreviewOrMetadataRequests,net.wingress.mobivious.ChaptersSmokeTest#fullscreenUsesChapterSheetAndBackClosesItBeforeLeavingFullscreen,net.wingress.mobivious.ContentLinksSmokeTest#richWatchInformationAndTimestampSeekKeepTheQueueOccurrence' \
  --offline --console=plain
cd ..
```

For the fixture-backed checks, first generate the local media, start
`scripts/fixture-server.py --media-dir .tools/test-media` and connect it using
`adb reverse tcp:18080 tcp:18080`, as in `scripts/test-android.sh`. Stop the
temporary fixture and remove its reverse connection after testing. The user's
emulator was left running.

## General quality review — 5 October 2026

This review used the user's already running `emulator-5554`, Android 16 / API 36.
No emulator was launched, restarted or stopped by the agent. Account mutations
used disposable localhost fixture accounts; the supplied live account was not
needed. The server checkout has no changes from this review.

Changes address existing behavior:

- HLS VOD playback could crash the service when Media3 built the logical queue
  timeline. Its VOD window exposed an epoch start time without live configuration.
  Session timeline snapshots now normalize those epoch fields while retaining
  seek geometry, periods, the manifest and actual live-window timing.
- Account/instance generations were omitted from video and queue card context
  comparisons, hiding watched/progress indicators after signing in. Cards now
  compare the complete context.
- HTTP requests now cancel their OkHttp call when a coroutine is canceled, including
  during response-body reads. Late results from an old account or instance cannot
  publish feed snapshots or offline status. Successful HTML/error pages cannot
  overwrite a valid cached feed, and mutation calls disable transport retries.
- Retrying an initial metadata failure preserves the requested autoplay or paused
  intent and the queue occurrence. Opening a playlist sends one initial request.
- Fullscreen exit commits its mode before Android rotation can cancel animation.
  PiP receives a measured source rectangle, with window updates posted after layout.
- Channel descriptions, image viewers and comment panels have 48 dp close targets.
  Navigation tabs use their visible labels for accessibility and stable test tags.
- Media-controller connection waits suspend without blocking an IO worker. Backup
  rules explicitly exclude app data from cloud backups and device transfers.
- The device-test runner checks a connected device and fixture readiness, rejects
  port conflicts, and cleans up only its own fixture/reverse mapping. Test selectors,
  lifecycle synchronization and main-thread controller reads were corrected.

| Check | Result |
| --- | --- |
| Final JVM unit/API tests | Passed: 236 tests, 0 failures/errors/skips |
| Final debug lint | Passed: 0 errors, 24 warnings; remaining warnings concern dependency updates, intentional media-service export and KTX style |
| Debug and instrumentation compilation | Passed, including the revised service-test dispatcher |
| Release APK packaging | Passed; no version bump, publication or deployment |
| Full device run during review | Executed 172 tests: 110 passed, 62 failed; this was an intermediate revision, not final acceptance |
| Subsequent focused device run | 23 of 29 passed; account navigation, HLS, playlist links, descriptions and community close targets passed |
| Retry regression and navigation/playback rerun | 9 passed, 2 failed before a Compose test-layout crash stopped instrumentation; all 5 AppSmokeTest scenarios passed, including failed initial retries with autoplay enabled and disabled |
| Live public browsing | Default-instance home feed inspected visually; no live account mutations |
| Runner/fixture syntax and diff checks | Passed |

The complete device suite is **not green**. PiP entry succeeds, but the shell-driven
return check still times out. It needs verification through Android's actual PiP
expand control. Several earlier failures were obsolete selectors, missing fixture
content or unsynchronized transitions. Watched-card selectors and account-return
setup were corrected, but those scenarios still need a complete rerun.

The recurring `performMeasureAndLayout called during measure layout` stack ran
through Compose's test frame clock. Service tests now explicitly use a
`StandardTestDispatcher` so effects queue rather than resume inline; this harness
change compiles but has not been verified on the device. Earlier speculative app
scroll changes were removed. The user-started emulator subsequently disconnected
from ADB during direct UI inspection. Host process and port checks confirmed it
had exited, and it was not relaunched. A separate host build
executor timed out under load; the final checks passed with one worker and a
768 MB Gradle heap.

Run the affected checks first after the user reopens the emulator:

```sh
export ANDROID_SERIAL=emulator-5554
scripts/test-android.sh --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.AccountNavigationSmokeTest,net.wingress.mobivious.AppSmokeTest,net.wingress.mobivious.ContentLinksSmokeTest,net.wingress.mobivious.WatchedIndicatorsSmokeTest,net.wingress.mobivious.QueueLibrarySmokeTest
```

Then run `scripts/test-android.sh` without the class filter to audit the other
remaining device failures. Intermediate XML reports, the final build log and the
public-home screenshot are saved locally in the ignored
`artifacts/quality-review-2026-10-05/` directory. These results do not establish
physical-device, production-account or final complete-suite acceptance.

Follow-up review while the emulator remained disconnected identified a library
race: a pending subscription can increment the playlist revision during the
initial account read, causing that read to be discarded and owned playlists to
remain absent. Confirmed subscribe/unsubscribe mutations now trigger a fresh
playlist-only read with the current revision. A new fixture-backed sign-in
regression checks that both owned and subscribed lists appear together; its
runtime check remains pending.

Additional smoke checks now return explicitly from Account to Home before
expecting browse content, wait for the settings root after saving SponsorBlock,
and read media IDs through playback snapshots. Settings tests also reset the
guest/account state before launch. The latest recorded failing scenarios are
listed in `artifacts/quality-review-2026-10-05/remaining-device-checks.md` to guide
the next complete run. The follow-up JVM tests, lint, debug/instrumentation builds
and release packaging also passed with the library changes included.

The fixture now records search completion even when its response raises
`BrokenPipeError` after client cancellation. A host-side check reproduced the
previous unfinished event and verified completion for successful, HTTP-error and
disconnected responses. The corresponding delayed-search device test still needs
a rerun.

## Crash-focused follow-up — 5 October 2026

The user reopened `emulator-5554`. The pending focused run finished with 44 of
51 tests passing and seven failures. Its XML is saved as
`artifacts/quality-review-2026-10-05/resumed-focused-device-run.xml`. The HLS,
pending-subscription sign-in and delayed-search cancellation regressions passed.
The recurring Compose layout exception appeared in the account recreation test,
which still used the default test dispatcher; it now uses the same explicitly
queued service-test dispatcher as the other affected classes.

The user then narrowed the goal to errors that cause crashes. Further PiP-return,
selector, history grouping and playlist navigation investigation was deferred.
A crash regression run passed **8 of 8 tests**, covering HLS VOD with captions and
audio mode, player gestures/fullscreen, decoded geometry and activity recreation,
both formerly crashing content-link cases, watched-history removal, background
queue recreation and duplicate-occurrence advancement/repeat. All 236 JVM tests
and debug lint also passed. No new Mobivious fatal exception appeared in Android's
crash buffer during this follow-up.

The confirmed app crash fix is the HLS VOD session-timeline normalization described
above. The Compose change addresses instrumentation frame-clock reentrancy; it
does not establish a separate production crash. The eight passing scenarios prove
the known crash regressions, not complete acceptance of the remaining device
suite. Reports and the successful build log are saved as
`crash-regression-device-run.xml` and `crash-regression-build.log` in the same
artifact directory. The emulator was never launched, restarted or stopped by the
agent, and live account credentials were not used.

## Signed 0.5.4 release — 5 October 2026

The 0.5.4 APK includes the player/chapter presentation, codec-selection guard,
HLS VOD session-timeline crash fix, network cancellation and account/library
reliability changes committed since 0.5.3. Version code is 11; the application ID
remains `net.wingress.mobivious`.

| Check | Result |
| --- | --- |
| JVM unit/API tests | Passed: 236 tests, 0 failures/errors/skips |
| Signed release build | Passed with the existing local signing key |
| Release lint | Passed: 0 errors, 34 warnings |
| APK metadata | Passed: version 0.5.4/code 11, minimum SDK 26, target SDK 37, not debuggable |
| CPU architectures | arm64-v8a, armeabi-v7a, x86 and x86_64 |
| APK signature | Passed; certificate SHA-256 matches published 0.5.3 |
| Previous published binary | Local 0.5.3 APK SHA-256 matches GitHub's asset digest |
| Release checksum | Passed; copied APK matches the Gradle output |

APK SHA-256:
`ef71fa790dcd314e71cebcc57d9fd694a820978cda80d67253949056b2d53743`.

Release assets are `artifacts/Mobivious-0.5.4.apk` and its `.apk.sha256` file.
The GitHub tag is `v0.5.4`; notes cover changes since 0.5.3. Build and local
verification evidence are `/tmp/mobivious-release-0.5.4-build.log` and
`artifacts/release-verification-0.5.4.json`.

The preceding crash-focused review passed eight selected device regressions.
This release build does not rerun the device suite; the complete suite still has
outstanding failures, and physical-device acceptance remains unverified. The
crash-focused follow-up above records those limits. No server deployment or live
account mutation was performed for this release.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease \
  --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m --console=plain
cd ..
JAVA_HOME=/opt/android-studio/jbr "$HOME/Android/Sdk/build-tools/36.0.0/apksigner" \
  verify --print-certs artifacts/Mobivious-0.5.4.apk
cd artifacts
sha256sum -c Mobivious-0.5.4.apk.sha256
```

## General follow-up and renewed crash priority — 5 October 2026

The general suite finished with **149 of 174 device tests passing**. The next
focused follow-up finished with **44 of 55 passing**, eleven assertion, timeout
or Compose test-hierarchy failures, and no fatal entries in its individual test
logs. Reports are `general-resumed-device-run.xml` and
`general-followup-device-run.xml` under
`artifacts/quality-review-2026-10-05/`. These runs do not establish complete device
acceptance.

Before the user narrowed the current priority to crashes, a delayed account
preferences regression reproduced unwanted navigation from Library/history to
Subscriptions. Preference completion now checks the navigation revision before
applying the default home. The history-preservation and untouched-default-home
regressions passed; the companion explicit-discovery/playback test still failed
its watch-details rendering assertion. Service activity tests consistently use
the queued Compose dispatcher, and targeted selectors/waits were tightened. The
remaining general failures are retained in `remaining-device-checks.md` and are
deferred while crash investigation takes priority. The broader general goal
remains active.

A new focused queue regression then reproduced an actual **main-thread fatal
exception in `net.wingress.mobivious.debug`**: a queued video with negative
`lengthSeconds` reached `SimpleBasePlayer.MediaItemData.Builder.setDurationUs`
through `QueueSessionPlayer.getState`. The exception can occur again in the
player listener after an initial command update, so catching the queue command
alone would not resolve it. Baseline XML, build output and logcat are preserved
as `duration-crash-before-fix*` in the same artifact directory.

Unresolved queue durations now use Media3's unknown-duration value when metadata
is zero, negative or too large to convert from seconds to microseconds. Positive
representable values retain their duration, and the active manifest timeline
retains its decoded duration. A JVM regression exercises numeric boundaries
through the actual Media3 builder; a device regression checks insertion,
continued playback and advancement with negative, overflowing, unknown and
normal metadata.

The crash-focused follow-up passed **9 of 9 device tests**, including the new
duration regression and the eight previously passing crash scenarios. All
**237 JVM tests** and debug lint passed; the complete focused build succeeded.
None of the nine individual test logs contains a fatal entry. The expected
before-fix crash remains in Android's historical crash buffer and is not a new
after-fix failure. The device XML, per-test crash audit and successful build log
are saved as `crash-duration-after-fix-device-run.xml`,
`crash-duration-after-fix-audit.json` and `crash-duration-after-fix-build.log` in
the same artifact directory. Testing used the user-started `emulator-5554`; no
emulator was launched or restarted and no live account was used. The general
goal remains active, with further work prioritized around reproducible crashes.

## Repeated recommendation crash — 5 October 2026

The next crash review reproduced another duplicate-key failure using a fixture
video response with two recommendations for `testvideo02`. The watch list uses
video IDs as its item keys, while the detail parser previously retained both
results. The before-fix device regression threw `Key "testvideo02" was already
used` during list layout.

The same fixture response was checked outside instrumentation: the debug app
launched normally with guest localhost settings and then exited with a
main-thread `AndroidRuntime` fatal exception for the duplicate key (PID 22992).
This confirms a production rendering failure, separately from the earlier
instrumentation frame-clock exception. Baseline XML, build output, test logcat
and native logcat are saved as `recommendations-crash-before-fix*` in
`artifacts/quality-review-2026-10-05/`.

Video detail parsing now retains only the first recommendation for each video
ID, preserving server order and first-result metadata. General video parsing
still retains distinct playlist occurrences. A JVM regression checks both
contracts, and the fixture-backed device regression checks rendering,
continued playback and opening the recommended video. Its first after-fix run
passed the rendering assertions and the nine other crash tests, but timed out
after tapping the card container; the test now taps the actual video title.

The completed rerun passed **10 of 10 crash-focused device regressions**, all
**238 JVM tests**, and debug lint. None of the ten per-test logs contains a fatal
entry. The native app was then launched again outside instrumentation against
the same raw response (`testvideo02`, `testvideo02`, `testvideo03`). The process
remained alive and its media session reported `PLAYING`, position 109047 ms,
speed 1.0 and no error. Native screenshot, process logcat, media-session dump and
fixture-response audit are saved as `recommendations-native-after-fix*` in the
same directory. The passing device XML, crash audit and build output are saved
as `recommendations-crash-after-fix*`.

The native check stopped only the temporary debug app and fixture and removed
its reverse mapping. The user-started emulator and installed release package
were not restarted or replaced. This is a source/debug verification; the broader
general device suite still has outstanding failures, and its goal remains
active with crash errors taking priority.

The user subsequently defined this round as achieved once the test runs were
finished. No test or fixture process remained active, and the goal was marked
complete for now. This closes the crash-focused round; the 24 broader failing
scenarios remain recorded in `remaining-device-checks.md`. Complete device-suite
acceptance is still unproven.

## Post cards and direct comments — 5 October 2026

Channel post cards now show read-only likes and a Share icon on the same row,
followed by a full-width tonal Comments button with a formatted count. The button
opens the post-comments sheet over the existing Posts feed, preserving its route,
pagination and scroll position. Card backgrounds no longer open a separate post;
Copy link and the standalone Share text action have been removed. Incoming,
pasted and rich-text post links retain the dedicated detail screen and use the
same card actions.

Feed text previews retain six lines. Outlined Read more/Show less buttons expand
and collapse the loaded rich text inside the card, with saveable per-post state
across scrolling and Activity recreation. Attachments, image viewing, selectable
text and embedded links retain their native interactions. The shared rich-text
renderer accepts an optional expansion control; existing comment presentation
keeps its current control style.

The final focused emulator run passed **22 of 22 tests** in
`CommunityPresentationTest`, `CommunitySmokeTest` and
`CommentsPresentationTest` on the already-running `emulator-5554`
(Pixel_8_Pro, Android 16). This covered direct feed comments without a post-detail
request, background-card semantics, cursor-preserving retry and feed position,
sorting and paginated replies, same-post reopening, switching targets, invalid
and stale card sources, channel/tab/search changes, delayed account/instance
responses, independent video comments/playback, shared-link recreation, native
share-chooser launch and URL payload, and long-text scrolling/restoration/links.
Presentation checks covered formatted, zero and unknown counts, independent
likes/share semantics, full-width buttons, 48 dp targets, narrow layouts, large
fonts and both themes. Native screenshots of gallery card actions, the comments
sheet, and collapsed/expanded long posts were visually inspected.

All **238 JVM tests** passed. Debug app/test APK assembly and debug lint passed;
lint reported **24 warnings and no errors**. The extended disposable community
fixture passed, including matching full long-text bodies in feed/detail responses.
An initial incremental compile could not resolve unchanged UI declarations;
recompiling with `-Pkotlin.incremental=false` passed, as did subsequent normal builds.
The account-switch regression follows the existing preference-driven return to
the home screen before re-entering Posts for the instance-switch check.

Local evidence is saved under the ignored `artifacts/post-cards-overhaul/`:
build/device logs, passing device XML, JVM totals, lint XML, and four native
screenshots in `mobivious-posts-screenshots/`. The debug APK is
`android/app/build/outputs/apk/debug/app-debug.apk` and was explicitly installed
after instrumentation cleanup as `net.wingress.mobivious.debug` for manual use.
This change required no server updates. The broader device suite was not run.

To repeat the focused runtime checks using the existing fixture runner:

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --offline \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.CommunityPresentationTest,net.wingress.mobivious.CommunitySmokeTest,net.wingress.mobivious.CommentsPresentationTest
```


## Livestream archive chat replay — 5 October 2026

Feature 23 now opens read-only chat on demand for archived videos whose optional
`liveChatReplay` flag is true. Live/upcoming videos and older responses without
the flag do not offer chat. Public replay requests omit bearer credentials and
encode opaque cursors. The ViewModel controller follows actual service position,
including pause, speed, repeat, seeks and SponsorBlock discontinuities. Positive
timing delays chat. Paging handles stable IDs, replacements/removals, sparse
pages, failed cursors and repeated-token loops; cache limits are 5,000 messages,
24 seek segments and 120 displayed rows, with 30-second prefetch and at least
800 ms between continuation requests. A stopped loop does not falsely mark
future positions as covered.

Dock size is device-local (30–70%, default 60% below / 35% beside). Normal watch
and portrait fullscreen dock below; landscape fullscreen docks beside the sole
aspect-fitting video surface. Overlay geometry is normalized within the fitted
picture, clamped on size/orientation changes, and edited with Move/Resize,
Save/Cancel, accessible movement/size actions and settings sliders. Background
opacity leaves text opaque. Defaults show timestamps and user IDs, use 100%
font scale / 75% overlay opacity, empty filters and zero timing. Manual scrolling
freezes the reading window and exposes Return to playback; rotation/recreation
and miniplayer/PiP/background restoration retain the same occurrence's state.
Comments and chapters replace chat, and a new occurrence starts closed.

Only timestamps and user/word filters are shared through dedicated sparse chat
preference PATCHes, coordinated with other account preference writes. Timing
uses the existing GET/PUT endpoint and ±3,600,000 ms range, with failed-save retry
and protection against delayed reads overwriting newer edits or form drafts.
Optional chat authentication errors retain the current playback owner and prompt
sign-in renewal. Appearance stays device-local per instance. Guest settings/timing stay local
per instance and are not copied into an account. RE2/J 1.8 provides
case-insensitive `/pattern/` tokens alongside substring word tokens and channel
ID/handle filters. New invalid filters are rejected; unsupported imported
patterns/oversized fields are reported and skipped while their saved values
remain intact, including when changing another setting. Limits are 1,024 UTF-8
bytes per field and 128 characters per token.

Validation:

- **267 JVM/API tests passed**, including 29 replay tests for optional availability,
  typed parsing, cursor encoding/credentials, synchronization, offsets, seeks,
  cache pruning/eviction, replacement ordering in frozen windows, pagination
  errors/loops, filters/imports, sparse shared
  patches, context/occurrence isolation and delayed responses.
- **15 distinct replay device scenarios passed** on the already-running
  `emulator-5554` (Pixel_8_Pro, Android 16): fourteen in the full replay run and
  the delayed timing-edit case in a subsequent three-test persistence run.
  Coverage includes on-demand loading and paused seeks, paid messages, retries,
  unavailable/live gating, both dock orientations, overlay drag/resize
  Save/Cancel and fullscreen clamping, accessible movement, 300% chat fonts and
  both themes, reading/resync and recreation, occurrence/panel transitions,
  guest/account isolation and saves, old-token scopes, invalid regex, and
  miniplayer/PiP/background suspension/restoration at the current position.
- **11 related native presentation regressions passed** across player controls,
  chapters and comments. Fullscreen Back ordering uses the Activity Back
  dispatcher because shell screenshot capture left this Android 16 AVD without
  reliable input focus for synthetic hardware Back; system Back in the modal
  settings flow was also exercised.
- Debug app and instrumentation APK assembly and debug lint passed. Lint
  reported **0 errors and 26 warnings**; no release build or publication was
  performed for this change.
- `python3 scripts/check-chat-fixture.py` passed using a disposable port and
  isolated data. Its HTTP checks cover timed/sparse chunks, replacements,
  removals, errors, authentication/scopes, sparse settings and timing isolation.
- Sibling Invidious checks passed: **6 replay specs**, **2 native scope specs**,
  and normal/API-only executable builds. Native login grants
  `PATCH:chat_preferences` and `GET;PUT:chat_timing/*`; Android exposes the matching
  permission group. Existing endpoints/tables are reused; no new migration.

Native portrait/landscape dock, overlay and light/dark large-font screenshots
were visually inspected. Evidence is saved in ignored `artifacts/chat-replay/`:
passing replay/timing/presentation XML and logs, JVM results, lint results,
server check logs and screenshots. APKs are under
`android/app/build/outputs/apk/debug/` and
`android/app/build/outputs/apk/androidTest/debug/`. The finished debug APK was installed on the connected emulator for manual use.
Existing uncommitted changes were preserved. The runner used the user-started device and cleaned up only
its own localhost fixture and ADB reverse mapping.

Compatibility/runtime limits: account sync requires deployment of the sibling
native scope update and renewed sign-in tokens. RE2 syntax excludes browser
lookaround/backreferences. Older instances without replay availability remain
usable without chat; chat failures leave playback usable. Production accounts,
real upstream archives, arbitrary desktop/multiwindow resizing, a complete
TalkBack session, and the wider device suite were not exercised. Server
deployment, release signing/publication and sending live chat remain outside
this implementation.

Repeat on an already-running device (no emulator is started by this script):

```sh
ANDROID_SERIAL=emulator-5554 scripts/test-android.sh --offline --max-workers=1 \
  -Dorg.gradle.jvmargs=-Xmx768m \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.LiveChatSmokeTest,net.wingress.mobivious.PlayerControlsPresentationTest,net.wingress.mobivious.ChaptersPresentationTest,net.wingress.mobivious.CommentsPresentationTest
python3 scripts/check-chat-fixture.py
cd ../invidious
CRYSTAL_CACHE_DIR=/tmp/mobivious-chat-crystal crystal spec spec/invidious/videos/live_chat_spec.cr spec/native_chat_scopes_spec.cr
CRYSTAL_CACHE_DIR=/tmp/mobivious-chat-crystal crystal build src/invidious.cr -Dskip_videojs_download -o /tmp/invidious-chat-replay
CRYSTAL_CACHE_DIR=/tmp/mobivious-chat-crystal crystal build src/invidious.cr -Dapi_only -Dskip_videojs_download -o /tmp/invidious-chat-replay-api
```


## Signed 0.6.0 minor release — 5 October 2026

Version is 0.6.0 (code 12), advancing the minor component from 0.5.4 and resetting
the patch component to zero. The release includes archive chat replay, community
post-card/comments improvements, queue-duration and duplicate-recommendation
crash fixes, and delayed-preference navigation protection. The application ID
remains `net.wingress.mobivious`.

| Check | Result |
| --- | --- |
| JVM unit/API tests | Passed: 267 tests, 0 failures/errors/skips |
| Signed release build | Passed with the existing local signing key |
| Release lint | Passed: 0 errors, 26 warnings |
| APK metadata | Passed: version 0.6.0/code 12, minimum SDK 26, target SDK 37, not debuggable |
| CPU architectures | arm64-v8a, armeabi-v7a, x86 and x86_64 |
| APK signature | Passed; certificate SHA-256 matches published 0.5.4 |
| Previous published binary | Local 0.5.4 APK SHA-256 matches GitHub's asset digest |
| Release checksum | Passed; copied APK matches the Gradle output |

APK SHA-256:
`7c3b92cae96ff1944a1f2f34942b694cc3b23d97e015c82c192cbd3218ec36e7`.

Assets are `artifacts/Mobivious-0.6.0.apk` and its `.apk.sha256` file, with release
notes in `artifacts/release-notes-0.6.0.md`. The release tag is `v0.6.0`. Local
evidence is `/tmp/mobivious-release-0.6.0-build.log` and
`artifacts/release-verification-0.6.0.json`, plus release signature and badging
reports in `artifacts/`.

The first incremental release compilation could not resolve unchanged shared
declarations. Full Kotlin compilation with `-Pkotlin.incremental=false` passed;
no application-source changes were needed to resolve the cache issue.

The preceding implementation checks passed 15 distinct replay device scenarios
and 11 related presentation regressions, as recorded above. Those were debug
runtime checks. No additional release-device run was performed; ADB listed no
connected devices during release preparation. The earlier complete device suite
has outstanding failures, and physical-device, production-account and real
upstream replay acceptance remain unverified.

Account chat sync requires the sibling native login-scope update and renewed
sign-in. Older instances without the availability flag remain usable without
chat. No server deployment or live account mutation was performed for this
release.

Reproduction:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease \
  --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m \
  -Pkotlin.incremental=false --console=plain
cd ..
JAVA_HOME=/opt/android-studio/jbr "$HOME/Android/Sdk/build-tools/36.0.0/apksigner" \
  verify --print-certs artifacts/Mobivious-0.6.0.apk
cd artifacts
sha256sum -c Mobivious-0.6.0.apk.sha256
```

## Native subscription sorting — 7 October 2026

The subscribed-channel directory now offers Relevance (default), Latest upload,
Most watched and A–Z. One enriched request supplies the directory and statistics;
sort changes and channel-name searches use that snapshot without requests.
Selections are saved locally per instance. Rows show the last known upload and
distinct watched-video counts: all time for Most watched, the last 90 days for
the other choices. Relevance explains the shared 90-day viewing decay and
seven-day unwatched-upload boost. The subscription video feed keeps its existing
ordering.

The sibling Invidious API preserves the ordinary array response and accepts
`include_stats=true` to add `subscriptionStats`. It reuses the database statistics
and relevance calculation from `e843ff9c853b824ab0959041ea075baa09b7c275`. Enriched
access requires both existing subscriptions and history read permissions;
responses remain private and uncached. It reads local caches without metadata
requests, history backfills, new scopes or a schema migration. Missing statistics
or a missing history permission produce an explained A–Z fallback without
replacing the saved sort. Refresh errors retain rows, statistics, search and sort.

| Check | Result |
| --- | --- |
| Android JVM unit/API tests | Passed: 272 tests, 0 failures/errors/skips |
| Debug and instrumentation builds | Passed offline |
| Debug lint | Passed: 0 errors, 27 warnings |
| Focused sorting/directory device tests | Passed: 7 tests, including 4 new sorting scenarios |
| Final subscription/search/avatar/RSS device regressions | Passed: 30 selected scenarios, 0 failures/skips |
| Crystal shared ranking specs | Passed: 7 examples |
| Guarded server database/API integration harness | Passed against disposable PostgreSQL 14 |
| Normal and API-only server builds | Passed |
| Native layout screenshots | Inspected 8 captures at 320dp/390dp in light/dark themes; dark captures use 1.4× text and thin mode |
| Fixture syntax and Git whitespace checks | Passed in both repositories |

Unit/API coverage includes all four orders, deterministic name/ID ties, unknown
uploads, filtered ordering, invalid saved keys, legacy and malformed statistics,
history-permission fallback, refresh failure retention and stale responses.
Device checks cover default Relevance, immediate local switching/search, new
activity persistence, channel navigation/restoration, unsubscribe, retry, empty
states, repeated-history writes, member-visibility refresh and sign-out isolation.
The existing history date-group device assertion now scrolls its lazy list to
the first heading before asserting visibility; data grouping is unchanged.

Server checks cover optional response compatibility, permission combinations,
account isolation, private cache headers and equality with the shared ranking
statistics. The shared tests exercise repeated watches, invalid/future dates,
account timezone boundaries, exactly 90 days, exactly seven days, member uploads,
future premieres and unknown uploads. The database harness confirms that reads
do not write history backfills or alter the website's sort cookie.

Two existing device scenarios still time out while opening playlists:
`AvatarsSmokeTest.subscriptionsHistoryAndPlaylistCreatorsUseExistingPayloads`
and `PlaylistRssSmokeTest.libraryGroupsAndReadOnlySubscriptionsFollowOwnerUpdates`.
Both failures reproduced on the unchanged Android HEAD (`c0e50bf`) in an isolated
checkout. They were excluded from the final 30-scenario run; the wider suite is
not reported as passing.

Local evidence is saved in ignored `.tools/subscription-sorting-*-device-results.xml`
(focused, final and baseline comparison reports) and
`.tools/subscription-sorting-screenshots/`. The screenshot montage is
`.tools/subscription-sorting-screenshots/montage.png`. The debug APK is
`android/app/build/outputs/apk/debug/app-debug.apk` and was installed on the
user-started `emulator-5554` for continued use. Tests used the disposable localhost
fixture; the runner cleaned up its fixture and ADB reverse mapping. The disposable
server database/container was removed after verification.

The live instance has not been updated. Deploying the sibling server changes is
required to enable ranking statistics there; until then Android falls back to
A–Z. Existing native tokens already grant the required permissions. Production
accounts, production deployment, release signing and publication were not part
of this implementation. The app version remains 0.6.0.

Repeat the core device checks on an already-running emulator:

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  --offline --console=plain --max-workers=2
cd ..
ANDROID_SERIAL=emulator-5554 JAVA_HOME=/opt/android-studio/jbr \
  scripts/test-android.sh --offline --max-workers=2 \
  -Pandroid.testInstrumentationRunnerArguments.class=net.wingress.mobivious.SubscriptionSortingSmokeTest,net.wingress.mobivious.SubscriptionsPresentationTest,net.wingress.mobivious.HomeSubscriptionsSmokeTest,net.wingress.mobivious.SearchHistorySmokeTest
cd ../invidious
CRYSTAL_CACHE_DIR=/tmp/mobivious-subscription-crystal-cache \
  crystal spec spec/invidious/frontend/subscription_manager_spec.cr
CRYSTAL_CACHE_DIR=/tmp/mobivious-subscription-crystal-cache \
  crystal build tests/database/accounts.cr -o /tmp/mobivious-subscription-accounts-tests
# Follow tests/database/README.md to supply a disposable ACCOUNT_TEST_DATABASE_URL.
```
