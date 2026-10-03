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
