# Documentation coverage and review boundaries

[Documentation index](../README.md) · [Source map](SOURCE_MAP.md) · [Test catalog](TEST_CATALOG.md) · [Review manifest](coverage.json)

The current guides describe Mobivious **0.12.5** (version code **29**). This report is generated from the current Gradle configuration and reviewed inventory; counts describe files, not measured execution coverage.

## File coverage

| Maintained files | Count |
| --- | ---: |
| Main application Kotlin | 121 |
| JVM test Kotlin | 46 |
| Android device test/support Kotlin | 80 |
| Build, benchmark, resources, scripts, deployment and repository support | 54 |
| **Total** | **301** |

Every maintained file has a reviewed SHA-256 fingerprint, responsibility and owning guide in `coverage.json`. The source map indexes implementations; the test catalog lists every declared JVM/device test and explicit Ignore.

## Review scope

The inventory includes tracked and non-ignored untracked files under `android/`, `scripts/` and `deploy/`, plus `.env.example`, `.gitignore`, `docker-compose.yml` and `LICENSE`. Locally deleted files are excluded. The sibling Invidious checkout is reviewed at its integration boundary; it is not an Android module.

The [architecture](../ARCHITECTURE.md), [features](../FEATURES.md), [settings](../SETTINGS.md), [API](../API.md), [playback](../PLAYBACK.md), [library](../LIBRARY.md), [storage/security](../STORAGE_AND_SECURITY.md), [UI](../UI.md), [development](../DEVELOPMENT.md) and [testing](../TESTING.md) guides own current behavior. Generated logs and captures belong under ignored `.tools/`, with source and device provenance.

Ignored builds, caches, IDE files, SDK paths, fixture media and release artifacts are explained by category in the source map. Local secrets and third-party library internals are excluded from fingerprints; secret values are never reproduced in documentation.

## Verification and freshness

`python3 scripts/check-documentation.py` checks inventory completeness, guide assignments, reviewed fingerprints and all three generated catalogs. It checks local file links in **all 21 repository Markdown documents**, including the cleanup report. Remote URLs and Markdown section anchors are outside this local check.

After reviewing changed sources and updating their guides, run `python3 scripts/check-documentation.py --refresh`, then the checker without refresh. New sources and documents require explicit inventory registration first. [Testing](../TESTING.md#documentation-checks) explains the review workflow.

Fingerprints and catalogs establish inventory freshness; semantic accuracy, emulator/device behavior, production compatibility, performance and release acceptance require their relevant checks. Test names and previous passing totals do not certify a current run.
