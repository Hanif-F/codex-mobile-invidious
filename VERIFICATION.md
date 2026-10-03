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
| Android unit tests | 7 passed |
| Android emulator integration | 2 passed |
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
recreated during the test. Server specs need local loopback networking for proxy
tests. The normal build downloads the existing web player's dependencies; an
interrupted download may leave empty asset directories that need to be removed
before retrying the existing dependency script.

## Relocated server checkout

Compose configuration passed with the default `../invidious` checkout, an explicit
absolute path, and a custom checkout path containing spaces. The Dockerfile and
both SQL initialization mount sources exist in each case. Compared with the
previous Compose configuration, only the three source paths changed; the project
name, database, volumes, account settings and service configuration are identical.

All nine mobile API patch files match the previously tested server commit byte
for byte, including the documentation exception. The original `source/.git` remains
at `644443ef` as a recovery copy. Android source and build/test scripts are unchanged;
both shell scripts passed syntax checks. No server image or APK rebuild was needed
for this location change.

## Production work still required

The mobile hostname, TLS alias, Docker rollout and new account APIs have **not**
been deployed on the Ubuntu server. Follow `deploy/README.md` and preserve the
existing keys, project name and volumes. Account operations in the release app
need that server patch; anonymous browsing can use the existing HTTPS instance.
Physical POCO X6 Pro behavior, OEM background restrictions, and authenticated
account features after production rollout still need a device/server acceptance check.
