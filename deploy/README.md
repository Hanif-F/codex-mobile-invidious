# Deploy on the Ubuntu server

The root Compose file keeps the original project name `invidious-codex-v3`, services,
Postgres 14, and `postgresdata` / `companioncache` volumes. Both hostnames serve
the same backend and database. It builds the sibling fork in `../invidious` by
default; Android never connects directly to Postgres or Companion.

The native download addition requires the sibling server routes
`GET /api/v1/videos/:id/downloads` and `GET /api/v1/videos/:id/download?key=…`.
Deploy both before distributing the new APK. They use the existing media/Companion
proxy and enforce disabled downloads, restricted videos and finite-track
availability. Exact keys distinguish audio languages and stable-volume variants
sharing an itag; unavailable selections fail without substitution. This addition
requires no database migration, token scope, sign-in renewal or new keys.
Android stores downloads locally and exports paired files using Media3 Transformer
1.9.3 with explicit conversion consent. Older instances show an update-required
message. See the sibling `docs/mobile-api.md` for the contract and verify both
allowed and disabled-download responses after deployment.

Keep the two Git projects side by side on both this device and the Ubuntu server:

```text
projects/
├── androidInvidious/       # This project; run Docker Compose here
│   ├── docker-compose.yml
│   └── .env
└── invidious/             # Server fork, including its .git directory
```

For a different server checkout location, set `INVIDIOUS_SOURCE_DIR` in this
project's `.env`. Relative paths are resolved against `docker-compose.yml`;
absolute paths such as `/srv/invidious` also work. The build context and both SQL
initialization mounts use this same setting. The old local `source/.git` is a
recovery copy only and does not need to be copied to the server.

1. Back up the existing database and confirm its volume name with `docker volume ls`.
   Preserve the current deployment and secrets for rollback. Do not run two stacks
   against the same volume or run `docker compose down -v`.
2. Copy or clone both projects to the server in the layout above. Include the
   server checkout's `.git` directory, required by the existing Docker build.
   Pull app/deployment changes and the source fork independently in their own
   repositories. Run the commands below from `androidInvidious/`.
3. Copy `.env.example` to `.env`, chmod it 600 and replace placeholders with the
   **existing** Companion, HMAC and DeArrow keys. Retain the existing database password.
   Rotating these values can invalidate sessions or encrypted identities.
4. Point `mobivious.wingress.net` at the server, and add it to the existing reverse
   proxy and TLS certificate. Examples for NGINX and Caddy are included. Keep
   `invidious.wingress.net` working. Ports 3000, 5432 and 8282 need no public exposure.
5. Stop the old Compose services without deleting volumes; from this workspace run:

```sh
docker compose --env-file .env config --quiet
docker compose pull companion invidious-db
docker compose build invidious
docker compose up -d invidious-db
docker compose run --rm --no-deps invidious /invidious/invidious --migrate
docker compose up -d
docker compose ps
curl -f https://mobivious.wingress.net/api/v1/stats
```

The playlist/RSS update adds **migration 20**. Back up the database, keep all old
Invidious instances stopped, and run the new executable’s `--migrate` against the
existing database before restarting the app service, as shown above. Migration 20
creates `saved_playlists` keyed by account and source ID, caches source metadata
and optional mix seed, and backfills legacy external saves without deleting their
rows. A repeated migration is safe. Account deletion cascades bookmarks;
unsubscribe cleans only the caller’s bookmark and caller-owned legacy save.

Fresh installations use the added SQL initialization file. Table integrity checks
can create the table but do not replace migration backfill on an existing database.
Both normal and API-only builds expose RSS routes. Existing native tokens need
renewal: sign out and sign in after deployment for saved-playlist writes, secret RSS
link access and subscription OPML export. Private Atom uses playlist-read scopes.
Verify independent saves from two accounts, live owner updates, private access,
My playlists/Subscribed playlists counts, and Android read-only controls before
accepting the rollout. No production migration, deployment or release publishing
was performed as part of this source change.

If the host reverse proxy reaches Invidious through Docker's bridge gateway, set
`AUTH_TRUSTED_PROXY_CIDR` to that gateway IP `/32` (inspect the Compose network).
Trust only the actual proxy. The default remains conservative; incorrect proxy
configuration shares an IP throttle between clients rather than trusting arbitrary
forwarded addresses.

Install the signed APK, select the mobile hostname and sign in with your existing
account. Check playback, subscriptions, a private playlist, and history from both
app and website. DNS and this production deployment were not performed locally.

Rollback uses the preserved previous source/image and the same volumes/secrets.
Never delete the database volume to fix a failed startup.

## Native DeArrow support

Deploy the updated sibling Invidious source to enable bearer-authenticated title contributions and private-ID import. The preference PATCH allowlist now includes `dearrow_enabled` and `dearrow_show_original`. Reuse the existing migration 13 identity table and persistent DeArrow identity key; this update adds no migration and requires no key rotation. See `../invidious/docs/dearrow-contributions.md` and `../invidious/docs/mobile-api.md` for the endpoint contract.

After the server update, sign out and sign in again in Mobivious to obtain the added DeArrow token permissions. Old tokens retain their previous permissions. Public replacement-title reads continue to work without contribution storage. Missing API endpoints, unavailable storage and old-token permissions have separate app messages. Neither building the APK nor this source change deploys the server automatically.

## Native SponsorBlock support

Deploy the updated sibling Invidious preference PATCH implementation to synchronize
SponsorBlock enablement, category modes/colors and channel overrides with the web
account. Segment reads use the existing public `/api/v1/sponsorblock/:id` proxy.
The API preserves unrelated preferences/categories/channels and validates sparse
updates; see `../invidious/docs/mobile-api.md`. Existing native preference tokens
remain valid: this change adds no scopes, database migration or key requirements.
Guest global settings remain local to the selected instance. No production rollout
or signed APK release was performed for this revision.

## Scoped search and organized history

Deploy the updated sibling Invidious source for authenticated subscription search
and organized history. Channel search uses the existing public channel endpoint.
The new subscription endpoint reads the account's current cached subscription
library; organized history shares website matching/date ordering and local archive
metadata. Both use authenticated private/no-store responses; the legacy history
formats remain compatible. See `../invidious/docs/mobile-api.md` for the contract.

Sign out and sign in after the server update to obtain the exact
`GET:subscriptions/search` native scope. History uses the existing `GET:history`
scope. This update adds no database migration, secret, or key rotation. On older
servers the app retains basic history viewing and explains why history search or
subscription search needs an update. Building the APK does not deploy the server.

## Channel avatar API coverage

Deploy the additive avatar response update from the sibling Invidious checkout
and install a new Android build for avatars across the native app. The server
reuses the existing **migration 21** `channel_avatars` cache; ensure that existing
migration has run on installations still using an earlier schema. No additional
migration, endpoint, token scope or sign-out/sign-in is required by this update.

Response-provided images retain priority; cache misses and cache failures use
placeholders without fetching channel/video metadata. Images use the existing
fixed-host `/ggpht` proxy, which now preserves query parameters. Normal image
requests may still reach YouTube's image CDN. Older servers remain usable with
available response images/placeholders. Native runtime and screenshot acceptance
are pending; see `../VERIFICATION.md` for validation and repeat commands.


## Shared preferred video codec

Deploy the sibling Invidious native preferences PATCH extension accepting
`video_codec` (`auto`, `av1`, `h264`) before distributing an Android build with the
codec setting. The web commit already stores this preference on the account;
the extension enables sparse Android writes to that same value. Existing GET/PATCH
preference scopes and storage are reused, with no migration or sign-in renewal.
Older servers can still play video, but unsupported shared saves show the existing
settings API update error. Guest codec settings remain local per instance.
Building the APK does not deploy the server.

## Native account management and flexible playback

Build and deploy the sibling server update before using native registration and
account management. Both normal and API-only builds include the new routes listed
in `../invidious/docs/mobile-api.md`. Preserve the existing HMAC key and account
schema; this addition requires **no new database migration**. Existing migrations
needed by earlier features still apply. Sign out and sign in on Android after the
server update to receive the explicit account-management scopes. Existing login
remains available on servers without the new endpoints; Android explains missing
routes or old token permissions.

Keep login/registration and CAPTCHA configuration consistent with the website.
CAPTCHA image rendering uses the existing `rsvg-convert` runtime dependency
already installed by the server Dockerfiles. Verify availability/challenge refresh,
signup, password typos without logout, credential replacement, browser/token
listing and revocation, selected token permissions/expiry, and account deletion
using disposable accounts. The account harness exercises these transactions and
CAPTCHA replay before rollout; see the sibling `tests/database/README.md`.

The Android update puts Settings inside Account and Search in the top-right;
it also sizes playback from decoded dimensions. Test portrait/square/landscape/
ultrawide media, comments, fullscreen, PiP and queue transitions on a real device
before runtime acceptance. Local connected tests are unverified because the
emulator crashes before boot. No production deployment or release publishing was
performed for this change.

## Native AI channel filter

Deploy the matching sibling server AI API update and run `--migrate` before server
startup on existing installations. Migration **22** supplies `ai_slist_snapshots`
and `channel_handles`; fresh installs already include them. This native extension
adds no migration or token scopes, and existing native sessions remain valid.

Android uses public `/api/v1/ai/status` and bounded `/api/v1/ai/channels` queries;
the existing preference PATCH endpoint accepts the master switch and eight action
fields. Lists and channel resolution stay on the instance. Verify guest/account
settings, warning thumbnails and recommendation autoplay with the fixture checks
in `VERIFICATION.md`. Old instances keep browsing and playback available and show
an update-required message in AI settings. Downloads remain independent.
