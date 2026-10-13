# Documentation coverage and review boundaries

[Documentation index](../README.md) · [Source map](SOURCE_MAP.md) ·
[Test catalog](TEST_CATALOG.md) · [Review manifest](coverage.json)

The current guides describe Mobivious 0.10.2 (version code 20), reviewed against
the Liquid Glass implementation working tree over `e956aabb` on 13 October 2026.
Runtime acceptance is recorded in the [implementation record](../liquid-glass-overhaul-2026-10-13/IMPLEMENTATION.md). The review covers the maintained
Android project and its supporting scripts/deployment examples in this repository.
The sibling Invidious project is described at its integration boundary; it is not
an additional Android module.

## File coverage

| Maintained files | Count | Documentation |
| --- | ---: | --- |
| Main application Kotlin | 93 | Per-file responsibilities and owning guides in [Source map](SOURCE_MAP.md). |
| JVM test Kotlin | 34 | Every file and declared test method in [Test catalog](TEST_CATALOG.md). |
| Android device test/support Kotlin | 60 | Every suite/helper, declared method and explicit Ignore in the test catalog. |
| Build, manifests, resources, scripts, deployment and repository support | 39 | Per-file responsibilities and owning guides in the source map. |
| **Total** | **226** | Explicit entries with reviewed SHA-256 fingerprints in `coverage.json`. |

The inventory uses Git's tracked and non-ignored untracked files under `android/`,
`scripts/` and `deploy/`, plus `.env.example`, `.gitignore`, `docker-compose.yml`
and `LICENSE`. It includes wrapper scripts/JAR/configuration and the documentation
checker itself. Generated source/test catalogs are reproducible from the manifest
and Kotlin declarations. Counts describe files, not measured execution coverage.

## Behavior coverage

| System | Documented behavior and boundaries | Guide |
| --- | --- | --- |
| Application and state | Startup, ownership, navigation, snapshots, pagination, lifecycles, contexts, revisions, cancellation and failure isolation. | [Architecture](../ARCHITECTURE.md) |
| User workflows | Discovery/search, channels/posts, watch/discussions, replay chat, accounts, library, sharing, offline media and current exclusions. | [Features](../FEATURES.md) |
| Preferences | Defaults, wire keys, local/account/device scope, save behavior, validation and link/current-player precedence. | [Settings](../SETTINGS.md) |
| Networking | Consumed public/authenticated APIs, models, pagination, permissions, media transport, errors, cache and server rollout requirements. | [API](../API.md) |
| Playback | Media session/service, lazy queues, duplicate occurrences, track/codec selection, successors, links/bounds, progress, gestures, PiP, power and optional tools. | [Playback](../PLAYBACK.md) |
| Accounts and content | Credentials/tokens, membership/statistics, blocking, playlists/mixes, history, clips/preview, download journal/recovery, export/conversion, RSS, DeArrow and AI. | [Library](../LIBRARY.md) |
| Persistence and security | All stores, stable profiles/migration, session encryption, local write failures, URL/auth boundaries, components/permissions, backup and privacy. | [Storage and security](../STORAGE_AND_SECURITY.md) |
| Presentation | Responsive layout, glass scenes, player geometry/motion, ambient capture gates, native rich content, accessibility and resources. | [UI](../UI.md), [Liquid Glass](../LIQUID_GLASS.md) |
| Engineering | Toolchain/dependencies, build/sign/release, upstream maintenance, JVM/device/fixture checks, restart/conversion acceptance and documentation upkeep. | [Development](../DEVELOPMENT.md), [Testing](../TESTING.md) |
| Operations and recovery | Matching server/extensions/migrations, secrets/proxy configuration and symptom-specific recovery. | [Deployment](../../deploy/README.md), [Troubleshooting](../TROUBLESHOOTING.md) |

## Supporting and excluded material

Existing parity, verification and dated visual evidence remain historical records.
Their purpose and locations are indexed in [Source map](SOURCE_MAP.md). Images,
videos, fixture patches, capture harnesses, manifests and logs support the relevant
dated reports; they are not independent application features.

Ignored build output, caches, IDE state, local SDK paths, release artifacts and
personal secrets are explained by category in the source map. Secret contents are
not reproduced. Generated dependency sources and third-party library internals
belong to their upstream projects; the guides document their use by this app.

## Verification and freshness

Run `python3 scripts/check-documentation.py` from the repository root. It checks
inventory completeness, required descriptions/guide assignments, reviewed source
fingerprints, generated catalog contents and local Markdown file links across the
22 indexed documents. Remote URLs and Markdown section anchors are not part of
that automated check. [Testing](../TESTING.md#documentation-checks) explains the
explicit review-and-refresh workflow.

The documentation pass does not change application behavior or claim a new
Android test, emulator, production deployment or release acceptance result.
Test names identify intended checks; actual results and ignored/device-limited
cases remain in the testing guide and dated verification records. Source review
and the appropriate runtime checks remain necessary after a behavior change.
