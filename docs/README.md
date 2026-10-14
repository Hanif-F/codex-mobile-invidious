# Mobivious documentation

This is the current documentation for the Android app in `androidInvidious`.
The current guides describe the opaque Material 3 implementation in this working
tree. Version remains **0.10.3**, code **21**; this task does not publish a release.
The [approved overhaul](ui-ux-overhaul-2026-10-13/README.md) defines acceptance.
The [implementation report](ui-ux-implementation-2026-10-13/README.md) records
completed work, failed/passing checks, evidence and acceptance boundaries. Its
hardware scope is emulator verification; performance acceptance is deferred to
the user by explicit instruction.

## Start here

| Reader / task | Guide |
| --- | --- |
| Use the app or understand its capabilities | [Features and user workflows](FEATURES.md) |
| Understand components, ownership and data flow | [Architecture](ARCHITECTURE.md) |
| Find every setting, its default and save scope | [Settings reference](SETTINGS.md) |
| Understand requests and instance requirements | [API and compatibility](API.md) |
| Work on playback, queues, tracks or gestures | [Playback system](PLAYBACK.md) |
| Work on accounts, subscriptions, history, playlists, clips or downloads | [Library and account systems](LIBRARY.md) |
| Understand persistence, isolation and privacy | [Storage and security](STORAGE_AND_SECURITY.md) |
| Review the UI/UX overhaul contracts and results | [Specification](ui-ux-overhaul-2026-10-13/README.md), [implementation evidence](ui-ux-implementation-2026-10-13/README.md) |
| Understand current layouts, accessibility, theme or ambient mode | [UI system](UI.md) |
| Review historical Liquid Glass work | [Implementation and acceptance](liquid-glass-implementation-2026-10-13/README.md), [superseded brief](LIQUID_GLASS_OVERHAUL_PLAN.md), [baseline](liquid-glass-overhaul-2026-10-13/README.md) |
| Review historical refraction and originating-window overlays | [Refraction implementation and verification](refraction-2026-10-13/README.md) |
| Build, sign or release the APK | [Development guide](DEVELOPMENT.md) |
| Run or extend checks | [Testing guide](TESTING.md), [test catalog](reference/TEST_CATALOG.md) |
| Diagnose a failure | [Troubleshooting](TROUBLESHOOTING.md) |
| Locate an implementation, resource or script | [Source map](reference/SOURCE_MAP.md) |
| Audit documentation completeness and freshness | [Coverage](reference/COVERAGE.md) |
| Operate the sibling Invidious server | [Deployment guide](../deploy/README.md) |

## Scope and terminology

**Mobivious** is the product name; `net.wingress.mobivious` is the release package.
**Instance** means the selected Invidious HTTPS server. **Account** means an
Invidious account on that instance, not a Google/YouTube account. A **profile**
owns local saves for one account or guest on one instance. A **queue occurrence**
is one position in a playback queue; two occurrences may contain the same video.

The `app` application module uses Kotlin, Compose and Media3; a separate
`macrobenchmark` test module measures its isolated benchmark variant.
The separate `../invidious` checkout performs extraction, exposes the mobile APIs
and connects to Companion/Postgres. Android never directly accesses those services.

These guides cover all maintained Kotlin application files, Android resources
and manifests, build configuration, scripts, test files and deployment examples.
The source map gives each file a responsibility and a guide; the test catalog
lists the actual test methods. Generated build output, local secrets and historical
screenshots/logs are documented by category, rather than treated as application code.

## Existing records

[LIQUID_GLASS.md](LIQUID_GLASS.md) preserves the previous visual design rationale.
[LIQUID_GLASS_OVERHAUL_PLAN.md](LIQUID_GLASS_OVERHAUL_PLAN.md) is the superseded
overhaul brief, with dated baseline evidence. The [implementation report](liquid-glass-implementation-2026-10-13/README.md) records the completed changes, emulator checks and validation limits.
Those records retain their historical results; future design follows the
[opaque Android overhaul](ui-ux-overhaul-2026-10-13/README.md).
[FEATURE_PARITY.md](../FEATURE_PARITY.md) is a dated web/native comparison, and
[VERIFICATION.md](../VERIFICATION.md) contains chronological test and release
evidence. Their old versions, counts and deployment statements belong to their
stated dates. Use the current source and this index for today's behavior.

Dated evidence collections are indexed in [the source map](reference/SOURCE_MAP.md).
Emulator acceptance is recorded explicitly in the implementation report; production
deployment and release acceptance are separate. To maintain the docs, follow [Testing → documentation checks](TESTING.md#documentation-checks).
