# Deploy on the Ubuntu server

The root Compose file keeps the original project name `invidious-codex-v3`, services,
Postgres 14, and `postgresdata` / `companioncache` volumes. Both hostnames serve
the same backend and database. It builds the fork in `source/`; Android never
connects directly to Postgres or Companion.

1. Back up the existing database and confirm its volume name with `docker volume ls`.
   Preserve the current deployment and secrets for rollback. Do not run two stacks
   against the same volume or run `docker compose down -v`.
2. Copy this workspace to the server (including `source/` and its `.git`, required by
   the existing Docker build). Pull app/deployment changes and the source fork
   independently if using separate Git repositories.
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
