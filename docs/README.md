# Mobivious documentation

This is the current documentation for the Android app in `androidInvidious`.
The current guides describe the opaque Material 3 implementation in this working
tree. Current release version is **0.12.4**, code **28**.
The [testing guide](TESTING.md) defines repeatable validation; the
[cleanup report](CLEANUP.md) records the latest audit results and known failures.
The [feature parity checklist](../FEATURE_PARITY.md) retains its 9 October
baseline and records later feature updates with evidence in
[VERIFICATION.md](../VERIFICATION.md).

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
| Understand current layouts, accessibility, theme or player controls | [UI system](UI.md) |
| Review the repository cleanup and its validation | [Cleanup report](CLEANUP.md) |
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
lists the actual test methods. Generated builds, captures and local secrets stay
in ignored directories and are documented by category.

## Evidence and maintenance

Current source and these guides define supported behavior. Record the source,
device and command with each validation result; old passing totals do not certify
a new build. Emulator, production deployment, performance and release acceptance
have separate checks.
To maintain the docs, follow [Testing → documentation checks](TESTING.md#documentation-checks).
