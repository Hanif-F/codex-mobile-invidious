# Deploy on the Ubuntu server

The root Compose file keeps the original project name `invidious-codex-v3`, services,
Postgres 14, and `postgresdata` / `companioncache` volumes. Both hostnames serve
the same backend and database. It builds the sibling fork in `../invidious` by
default; Android never connects directly to Postgres or Companion.

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
docker compose up -d
docker compose ps
curl -f https://mobivious.wingress.net/api/v1/stats
```

The mobile patch adds no schema migrations. The fork already has account-schema
migrations; your existing deployed version must have those, as it did during local
analysis. New installations initialize from the existing SQL files. If updating from
an older fork, follow that fork's migration instructions before starting it.

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
