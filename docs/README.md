# Mobivious documentation

This is the current documentation for the Android app in `androidInvidious`.
The UI guides and inventories were reviewed against the Liquid Glass implementation
working tree over `e956aabb` (Mobivious **0.10.2**, version code **20**) on
**13 October 2026**. It describes source behavior; it does not assert
that a production server is updated or that every device scenario passes.

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
| Work on layouts, accessibility, glass or ambient mode | [UI system](UI.md) |
| Review the app-wide Liquid Glass overhaul | [Implementation and acceptance](liquid-glass-implementation-2026-10-13/README.md), [brief](LIQUID_GLASS_OVERHAUL_PLAN.md), [baseline](liquid-glass-overhaul-2026-10-13/README.md) |
| Review clear refraction and originating-window overlays | [Refraction implementation and verification](refraction-2026-10-13/README.md) |
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

The app is one Android Gradle module, written in Kotlin with Compose and Media3.
The separate `../invidious` checkout performs extraction, exposes the mobile APIs
and connects to Companion/Postgres. Android never directly accesses those services.

These guides cover all maintained Kotlin application files, Android resources
and manifests, build configuration, scripts, test files and deployment examples.
The source map gives each file a responsibility and a guide; the test catalog
lists the actual test methods. Generated build output, local secrets and historical
screenshots/logs are documented by category, rather than treated as application code.

## Existing records

[LIQUID_GLASS.md](LIQUID_GLASS.md) provides the extended visual design rationale.
[LIQUID_GLASS_OVERHAUL_PLAN.md](LIQUID_GLASS_OVERHAUL_PLAN.md) is the approved
overhaul brief, with dated baseline evidence. The [implementation report](liquid-glass-implementation-2026-10-13/README.md) records the completed changes, emulator checks and validation limits.
[FEATURE_PARITY.md](../FEATURE_PARITY.md) is a dated web/native comparison, and
[VERIFICATION.md](../VERIFICATION.md) contains chronological test and release
evidence. Their old versions, counts and deployment statements belong to their
stated dates. Use the current source and this index for today's behavior.

Dated evidence collections are indexed in [the source map](reference/SOURCE_MAP.md).
Emulator acceptance is recorded explicitly in the implementation report; production
deployment and release acceptance are separate. To maintain the docs, follow [Testing → documentation checks](TESTING.md#documentation-checks).
