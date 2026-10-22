# Source map

[Documentation index](../README.md) · [Coverage](COVERAGE.md) · [Test catalog](TEST_CATALOG.md)

Generated from the reviewed descriptions in [coverage.json](coverage.json). Each maintained implementation, resource, build, script and deployment file has an owning guide. Test files and their methods are listed separately below.

## Deployment and repository support

| File | Responsibility | Guide |
| --- | --- | --- |
| [.env.example](../../.env.example) | Documented non-secret shape for source path, database/Companion/HMAC/DeArrow/proxy settings. | [README](../../deploy/README.md) |
| [.gitignore](../../.gitignore) | Excludes local SDK/signing/deployment secrets, caches, builds, generated media and release artifacts. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [LICENSE](../../LICENSE) | AGPL-3.0-only project license text; README carries attribution/distribution guidance. | [LICENSE](../../LICENSE) |
| [deploy/Caddyfile.example](../../deploy/Caddyfile.example) | Two-hostname HTTPS reverse-proxy example with streaming-friendly flushing. | [README](../../deploy/README.md) |
| [deploy/README.md](../../deploy/README.md) | Independent server installation/update, migrations, scopes, secrets, rollback and compatibility workflow. | [README](../../deploy/README.md) |
| [deploy/nginx.conf.example](../../deploy/nginx.conf.example) | Two-hostname TLS reverse-proxy example, forwarded headers, disabled buffering and stream timeout. | [README](../../deploy/README.md) |
| [docker-compose.yml](../../docker-compose.yml) | Sibling server build, loopback Invidious binding, Companion, Postgres 14, healthchecks, existing volumes and environment configuration. | [README](../../deploy/README.md) |

## Build configuration

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/build.gradle.kts](../../android/app/build.gradle.kts) | App ID/version/SDK, signing/build variants, release/benchmark R8 and resource shrinking, Java target and runtime/test dependencies. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/app/proguard-rules.pro](../../android/app/proguard-rules.pro) | App-specific R8 keep-rule location; Android defaults and dependency consumer rules handle framework and library entry points. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/build.gradle.kts](../../android/build.gradle.kts) | Pinned Android Gradle and Compose compiler plugins. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/gradle.properties](../../android/gradle.properties) | Gradle memory/encoding, AndroidX, resource and Kotlin style flags. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/gradle/wrapper/gradle-wrapper.jar](../../android/gradle/wrapper/gradle-wrapper.jar) | Checked-in Gradle wrapper bootstrap binary. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/gradle/wrapper/gradle-wrapper.properties](../../android/gradle/wrapper/gradle-wrapper.properties) | Gradle 9.4.1 distribution URL/checksum and wrapper download/storage settings. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/gradlew](../../android/gradlew) | Unix Gradle wrapper launcher. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/gradlew.bat](../../android/gradlew.bat) | Windows Gradle wrapper launcher. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/settings.gradle.kts](../../android/settings.gradle.kts) | Repository policy, project name and sole app module. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |

## Performance verification

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/benchmark/AndroidManifest.xml](../../android/app/src/benchmark/AndroidManifest.xml) | Isolated non-debuggable profileable application variant for Macrobenchmark. | [TESTING](../../docs/TESTING.md) |
| [android/app/src/benchmark/res/xml/benchmark_network_security.xml](../../android/app/src/benchmark/res/xml/benchmark_network_security.xml) | Benchmark-only loopback fixture HTTP; ordinary production origins retain HTTPS policy. | [TESTING](../../docs/TESTING.md) |
| [android/macrobenchmark/build.gradle.kts](../../android/macrobenchmark/build.gradle.kts) | Self-instrumenting release-like Macrobenchmark module, ten-iteration test dependencies and isolated target. | [TESTING](../../docs/TESTING.md) |
| [android/macrobenchmark/src/main/AndroidManifest.xml](../../android/macrobenchmark/src/main/AndroidManifest.xml) | Benchmark instrumentation permissions, target visibility and fixture network policy. | [TESTING](../../docs/TESTING.md) |
| [android/macrobenchmark/src/main/java/net/wingress/mobivious/benchmarks/UiScrollBenchmark.kt](../../android/macrobenchmark/src/main/java/net/wingress/mobivious/benchmarks/UiScrollBenchmark.kt) | Eighteen current-UI performance journeys with ordinary-control setup, automatic-save recovery and measured gesture provenance. | [TESTING](../../docs/TESTING.md) |
| [android/macrobenchmark/src/main/res/xml/fixture_network_security.xml](../../android/macrobenchmark/src/main/res/xml/fixture_network_security.xml) | Instrumentation-side loopback HTTP exception for deterministic local fixtures. | [TESTING](../../docs/TESTING.md) |
| [scripts/summarize-ui-performance.py](../../scripts/summarize-ui-performance.py) | Median per-iteration CPU/overrun percentiles, frame counts and positive-overrun fractions from raw benchmark samples. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-ui-performance.sh](../../scripts/test-ui-performance.sh) | Runs current isolated benchmark journeys or recordings with fixture ownership, source/APK provenance and gesture/video validation. | [TESTING](../../docs/TESTING.md) |

## Android manifests and resources

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/debug/AndroidManifest.xml](../../android/app/src/debug/AndroidManifest.xml) | Preview app label and restricted debug network security override. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/app/src/debug/res/xml/debug_network_security.xml](../../android/app/src/debug/res/xml/debug_network_security.xml) | Debug cleartext allowlist for localhost, 127.0.0.1 and 10.0.2.2 only. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/AndroidManifest.xml](../../android/app/src/main/AndroidManifest.xml) | Permissions, app/service/provider entry points, shared text, verified Wingress links and user-selected YouTube content filters. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/res/drawable/ic_launcher.xml](../../android/app/src/main/res/drawable/ic_launcher.xml) | Flat cyan M and pale-dot foreground shared by the launcher and system splash. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/drawable/ic_launcher_background.xml](../../android/app/src/main/res/drawable/ic_launcher_background.xml) | Solid charcoal full-bleed adaptive launcher background. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/drawable/ic_launcher_monochrome.xml](../../android/app/src/main/res/drawable/ic_launcher_monochrome.xml) | M-and-dot silhouette for system-themed launcher icons. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/layout/clip_preview.xml](../../android/app/src/main/res/layout/clip_preview.xml) | Separate Media3 TextureView preview with native controls for clip editing. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/res/mipmap-anydpi/ic_launcher.xml](../../android/app/src/main/res/mipmap-anydpi/ic_launcher.xml) | Adaptive launcher foreground/background/monochrome composition. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values-night/colors.xml](../../android/app/src/main/res/values-night/colors.xml) | Dark launch background and dark system-bar flag. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/colors.xml](../../android/app/src/main/res/values/colors.xml) | Light launch background, fixed charcoal icon backing and light system-bar flag. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/media_colors.xml](../../android/app/src/main/res/values/media_colors.xml) | Native clip-preview footer scrim, transparent picture overlay and opaque Media3 settings popup colors. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/styles.xml](../../android/app/src/main/res/values/styles.xml) | Window theme with light/dark launch background, branded splash icon and charcoal icon backing. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/xml/data_extraction_rules.xml](../../android/app/src/main/res/xml/data_extraction_rules.xml) | Explicit backup/device-transfer exclusions for app-owned data. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/res/xml/rss_paths.xml](../../android/app/src/main/res/xml/rss_paths.xml) | FileProvider exposure restricted to the RSS cache subtree. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |

## Application entry points

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/MainActivity.kt](../../android/app/src/main/java/net/wingress/mobivious/MainActivity.kt) | Activity lifecycle, incoming share/app links, fullscreen system bars and PiP actions/bounds. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/MobiviousApplication.kt](../../android/app/src/main/java/net/wingress/mobivious/MobiviousApplication.kt) | Process-wide repositories, instance-access controller, context-fenced image loaders and playback state wiring. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DownloadReceiver.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DownloadReceiver.kt) | Manifest-bound system download-completion/notification adapter; preserves component identity and reconciles only the app journal. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |

## Data and API

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/AccentPreset.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AccentPreset.kt) | Stable device accent IDs and exact light/dark role tables with blue fallback. | [SETTINGS](../../docs/SETTINGS.md) |

## Data and domain

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/Accounts.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Accounts.kt) | Registration/session models, guided API permissions, scope validation and expiry choices. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/AiFilter.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AiFilter.kt) | AiSList settings/decisions, versioned bounded classification cache, batching, retries and shared lookups. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Annotations.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Annotations.kt) | Bounded archived-annotation XML parsing, occurrence-scoped loading/cancellation, timing and dismissal state. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/AppPreferences.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AppPreferences.kt) | All shared preference defaults, JSON parsing, sparse patches/merges, homepage/caption/codec rules; integrates channel title/thumbnail rules. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/AudioPreferences.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AudioPreferences.kt) | Shared preferred-audio language catalog, canonical normalization and labeled settings choices. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Avatars.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Avatars.kt) | Existing avatar metadata parsing, same-instance image proxy normalization, Unicode initials and redirect-free image client. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/CaptionLanguages.kt](../../android/app/src/main/java/net/wingress/mobivious/data/CaptionLanguages.kt) | Website-compatible English language choices for three caption priorities. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ChannelSpoilers.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ChannelSpoilers.kt) | Channel rules, publication precision, expiry and context-bound batched lookup/reveal state. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Chapters.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Chapters.kt) | Description timestamp chapter parsing, validity, availability and current-chapter rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Clips.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Clips.kt) | Native/upstream clip identity and bounds, title/time validation, eligibility and default ranges. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Comments.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Comments.kt) | Read-only comment models, target/reply pagination controller, position retention and native link mapping. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Community.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Community.kt) | Community attachments/posts, post links, owner metadata and banner/post-image proxy rules. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ContentLinks.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ContentLinks.kt) | Native/external link targets, validated URL/timestamps/indexes, nullable playback overrides/bounds and share URLs. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt) | Member/block visibility by surface, raw-page merging, search exceptions and confirmed blocked-channel repository. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt) | Title/private-identity validation and bounded, deduplicated replacement-title lookups. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DeviceProfiles.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DeviceProfiles.kt) | Stable local ownership namespaces, checked migration/transfer/purge and strict guest preference normalization. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DiscoverySearch.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DiscoverySearch.kt) | Trending categories, localized region catalog and typed mixed-search parsing/visibility/exhaustion. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DisplayFormats.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DisplayFormats.kt) | Localized compact/grouped counts, precision, size/bitrate/time/date formatting and strict non-negative numeric parsing. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DownloadRepository.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DownloadRepository.kt) | SQLite download journal, system and credentialed transfers, per-attempt ownership, retry and offline validation. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Downloads.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Downloads.kt) | Download catalog/selection/asset/record/status models and readiness rules. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/History.kt](../../android/app/src/main/java/net/wingress/mobivious/data/History.kt) | Organized/legacy history parsing, server-calendar grouping and scoped search input/page state. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/HttpCall.kt](../../android/app/src/main/java/net/wingress/mobivious/data/HttpCall.kt) | Cancellable asynchronous OkHttp response-body adapter and closed-response transport snapshot. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/InstanceAccess.kt](../../android/app/src/main/java/net/wingress/mobivious/data/InstanceAccess.kt) | Instance access discovery, typed gate failures, origin-bound credential transport and native pass validation. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/InvidiousApi.kt](../../android/app/src/main/java/net/wingress/mobivious/data/InvidiousApi.kt) | Complete instance HTTP client, auth/error/cache semantics and consumed API endpoint methods. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/LiveChat.kt](../../android/app/src/main/java/net/wingress/mobivious/data/LiveChat.kt) | Replay chunks/settings, RE2 filters, bounded presentation-driven replay windows and timing sync. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Models.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Models.kt) | API/domain models and parsers, source queue pages, library action state, stream/caption metadata and validated video links; integrates channel title/thumbnail rules. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/OriginalTitlePreferences.kt](../../android/app/src/main/java/net/wingress/mobivious/data/OriginalTitlePreferences.kt) | Context-bound original-title preference resolution shared by public browsing and background playback, with stale-read protection and a video-read query allowlist. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ResponseCache.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ResponseCache.kt) | Hashed device-private feed snapshots with age, size and count bounds and atomic replacement. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Rss.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Rss.kt) | Validated public/private/mix feed URL construction and RSS/OPML/Atom panel state. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/SessionStore.kt](../../android/app/src/main/java/net/wingress/mobivious/data/SessionStore.kt) | Keystore-encrypted account and per-instance verification storage plus local profile preference ownership. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/SponsorBlock.kt](../../android/app/src/main/java/net/wingress/mobivious/data/SponsorBlock.kt) | Segment/settings/category/mode/color/channel models, parsing, sparse patches and validation. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Storyboards.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Storyboards.kt) | Storyboard track selection, same-origin VTT sprite cues, frame lookup, clip offsets, crop bounds and bounded shared index cache. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Subscriptions.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Subscriptions.kt) | Directory statistics, local search/sort fallback and revision-fenced confirmed subscription mutations. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/WatchedState.kt](../../android/app/src/main/java/net/wingress/mobivious/data/WatchedState.kt) | Watched/progress models and indicators plus revision-fenced history/progress reconciliation and local fallback. | [LIBRARY](../../docs/LIBRARY.md) |

## Data and account settings

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/CaptionAppearance.kt](../../android/app/src/main/java/net/wingress/mobivious/data/CaptionAppearance.kt) | Validated profile-local caption appearance, supported choices, defaults and JSON persistence. | [SETTINGS](../../docs/SETTINGS.md) |

## Data and persistence

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/SearchAssistance.kt](../../android/app/src/main/java/net/wingress/mobivious/data/SearchAssistance.kt) | Profile-local search assistance settings, bounded recent queries and cancellable new/edit search entry state. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |

## File export

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/downloads/DownloadExportService.kt](../../android/app/src/main/java/net/wingress/mobivious/downloads/DownloadExportService.kt) | Foreground file copying, MP4 merging/consented conversion, output validation, progress, cleanup and interruption journal. | [LIBRARY](../../docs/LIBRARY.md) |

## Android foundation

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/downloads/ProtectedDownloadService.kt](../../android/app/src/main/java/net/wingress/mobivious/downloads/ProtectedDownloadService.kt) | Non-exported foreground owner for credentialed download transfers and interruption handling. | [LIBRARY](../../docs/LIBRARY.md) |

## Playback

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/player/AudioSelection.kt](../../android/app/src/main/java/net/wingress/mobivious/player/AudioSelection.kt) | Occurrence-owned preferred/manual audio policy, logical identity restoration and original/language/Stable Volume fallback. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/BufferRecovery.kt](../../android/app/src/main/java/net/wingress/mobivious/player/BufferRecovery.kt) | Bounded automatic buffer recovery, terminal error eligibility, stable-progress budget reset and sanitized diagnostics. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/CodecAwareTrackSelector.kt](../../android/app/src/main/java/net/wingress/mobivious/player/CodecAwareTrackSelector.kt) | Supported DASH codec/preset selection before initial media requests, retaining adaptive decoder checks. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/DownloadPlayback.kt](../../android/app/src/main/java/net/wingress/mobivious/player/DownloadPlayback.kt) | Local single/paired media sources and completed captions without remote metadata. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackQueue.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackQueue.kt) | Canonical playback runtime snapshot, occurrence identity, share URLs, repeat and source merge/successor rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackRules.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackRules.kt) | Pure seek, explicit/resume start, completion save and bounded-link rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackService.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackService.kt) | ExoPlayer/MediaSession lifetime, trusted commands, history/progress ownership, SponsorBlock and PiP transport actions. Integrates session playback statistics. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackStatistics.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackStatistics.kt) | Typed statistics snapshots, allowlisted JSON, session overlay state, load accounting and visible observation/history rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackStatisticsCollector.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackStatisticsCollector.kt) | Service-owned Media3 decoder/load observation, source isolation, active-format metrics and suspended one-second sampling. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/QueueCoordinator.kt](../../android/app/src/main/java/net/wingress/mobivious/player/QueueCoordinator.kt) | Service-owned lazy resolution, source pagination, occurrence transitions, tracks, bounds, offline media, clips and successor behavior; integrates channel title/thumbnail rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/QueueSessionPlayer.kt](../../android/app/src/main/java/net/wingress/mobivious/player/QueueSessionPlayer.kt) | Logical queue media-session timeline, bounded relative seeks, occurrence selection and queue next/previous commands. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SeekAccumulator.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SeekAccumulator.kt) | Captured configurable tap steps, direction changes, clamped targets, playback intent and 600 ms batch commitment. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SeekPreviewController.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SeekPreviewController.kt) | Seek preview lifecycle, stale-response rejection, cache-first image selection and tap-only 250 ms download throttling. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SessionTimeline.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SessionTimeline.kt) | Non-live window epoch normalization for safe Media3 session serialization. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SponsorBlockEngine.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SponsorBlockEngine.kt) | Overlapping automatic skips, manual offers, dismissal and playback-session segment history. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/StreamCatalog.kt](../../android/app/src/main/java/net/wingress/mobivious/player/StreamCatalog.kt) | Supported representation identity, API metadata matching, audio grouping, menu pruning and codec/quality selection. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/VideoGeometry.kt](../../android/app/src/main/java/net/wingress/mobivious/player/VideoGeometry.kt) | Decoded aspect ratio, embedded sizing, orientation and exact bounded PiP fractions. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/VideoSelection.kt](../../android/app/src/main/java/net/wingress/mobivious/player/VideoSelection.kt) | Occurrence-owned Auto/Preset/Manual video policy and reload fallback. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/WatchPlayerScroll.kt](../../android/app/src/main/java/net/wingress/mobivious/player/WatchPlayerScroll.kt) | Height-distance scroll consumption with top-region gating, bounds and unused movement handed to the watch details list. | [UI](../../docs/UI.md) |

## Playback and media sessions

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/player/CaptionLoadFailure.kt](../../android/app/src/main/java/net/wingress/mobivious/player/CaptionLoadFailure.kt) | Occurrence and track scoped subtitle loading failure, independent of cue gaps. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/Viewing.kt](../../android/app/src/main/java/net/wingress/mobivious/player/Viewing.kt) | Projection resolution, occurrence viewing state, camera bounds and tested spherical/EAC/sensor mapping. | [PLAYBACK](../../docs/PLAYBACK.md) |

## Compose UI

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt) | Sign-in/register/captcha, credentials, account deletion, sessions and token forms with transient secret handling. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ActionControlsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ActionControlsUi.kt) | Opaque dropdowns, icon-bearing action items/dividers, overflow lifecycle, action rows and selection fields. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt) | Accessible classification warning thumbnails replacing artwork when configured. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AnnotationsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AnnotationsUi.kt) | Native archived annotation overlays, link/dismiss actions and annotation/viewing mode player settings. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppComponents.kt) | Native opaque surface roles, Material foregrounds, icons, sections and automatic-save feedback. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppLayout.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppLayout.kt) | Measured native top bars, section headings and accessible wrapping selection controls. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppScaffold.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppScaffold.kt) | Four fixed root destinations, labeled bottom navigation/rail and window width classes. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppTheme.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppTheme.kt) | Canonical six-accent light/dark Material palettes, shapes, typography and window insets. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppViewModel.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppViewModel.kt) | App-facing StateFlows, browse/navigation snapshots, account/library actions, preference writes and MediaController coordination. Integrates session playback statistics. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppWindowLayout.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppWindowLayout.kt) | WindowManager occluding-fold observation and contiguous usable-pane policy with physical coordinate placement. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AvatarUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AvatarUi.kt) | Redirect-free Coil avatar loader, fallback identity, creator/channel headers and subscription chips. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/BrowseComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/BrowseComponents.kt) | Pinned opaque browsing toolbars, sort menus, tabs and artwork components. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt) | Adaptive creator description sheet with native rich links. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChannelSpoilerSettingsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChannelSpoilerSettingsUi.kt) | Configured-channel editor, sparse automatic saves, validation and retry/revert feedback. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChannelSpoilerUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChannelSpoilerUi.kt) | Per-occurrence neutral artwork masking and passive masked title presentation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt) | Current chapter label and retained chapter panel/sheet state with seek actions. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ClipsState.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ClipsState.kt) | Clip resolution/editor draft, validation, publication and occurrence-owned preview state. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt) | Clip cards/details/share/delete, foreign-origin dialog, independent preview and draggable filmstrip editor. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt) | Comment entry/drawer/replies, native rich text/emoji/link parsing, sorting and read-only presentation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt) | Related channel/post cards, inline expansion, post details/comments, share and image gallery/viewer. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt) | Original/replacement title controls, private identity form and reviewed proposal/vote panel; integrates channel title/thumbnail rules. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DiscoveryUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DiscoveryUi.kt) | Searchable country choice/picker and Trending category/region controls. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DownloadsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DownloadsUi.kt) | Download picker and library/export tools with shared friendly primary/supporting track labels and icon-bearing actions. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/InstanceAccessUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/InstanceAccessUi.kt) | Coalesced access prompts and an origin-restricted WebView verification dialog with explicit retry. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LibraryActionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryActionsUi.kt) | Grouped icon-bearing video actions with channel-page display settings, plus playlist save/create tools. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LibraryComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryComponents.kt) | Native list actions, confirmations and responsive full-height opaque panels with inset ownership. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt) | Replay list, follow/older/errors, dock/overlay editor and appearance/filter/delay settings. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/MediaListLayout.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/MediaListLayout.kt) | Shared responsive media-row artwork sizing, compact density, narrow/large-text stacking and text-only layout. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/MetadataUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/MetadataUi.kt) | Shared publication labels and video metadata line presentation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt) | Root Compose shell, browse/watch/library/settings composition, panels, dialogs and navigation/action wiring. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/OpeningLinksUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/OpeningLinksUi.kt) | Android-owned YouTube domain-selection status, setup guidance and installed-package Open by default shortcut with app-details fallback. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PanelComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PanelComponents.kt) | Shared flat panel surfaces, pinned accessible headers and complete modal-sheet height limits. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt) | Explicit queue expansion, current occurrence following, bounded inner list, repeat and recovery controls. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaybackStatisticsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackStatisticsUi.kt) | Compact/detailed statistics overlay, bounded graphs, pinned playback/copy controls, gesture isolation and selectable copy failure recovery. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt) | Service-owned native video surface, screen-awake lifecycle, transparent caption/error backgrounds and slate loading shutters. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerControlsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerControlsUi.kt) | Centered portrait transport, responsive footer and secondary-control popup, fullscreen controls and the shared 25% dim layer. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerGestures.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerGestures.kt) | Presentation modes, touch-exploration observation, density-independent drag arbitration and cancellation. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt) | Single-surface mini/watch/fullscreen host, animations, modality/lifecycle gates and chat integration. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt) | Player controls, gestures, supported track selection and icon-bearing settings with VR metadata gating and expandable secondary actions. Integrates session playback statistics. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt) | Playlist cards/headers, subscribe state and owned title/privacy/description editor. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PreferenceSaveController.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PreferenceSaveController.kt) | ViewModel-owned revisioned preference intent, coalescing, debounce, serialization, failure recovery and context isolation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ProjectionVideoSurface.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ProjectionVideoSurface.kt) | Service decoder surface projected with GLES, native captions, relative device motion and direct lifecycle cleanup. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt) | Feed link warning/copy/share/open and XML share/save sheet with scoped file permissions. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SearchResultCards.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SearchResultCards.kt) | Distinct circular-avatar channel cards and stacked playlist/mix search covers. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt) | Shared search input, transient suggestions/history editor and results toolbar with removable filters. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt) | Settings category routing, automatic per-field preference feedback, server confirmation and About content; integrates channel title/thumbnail rules. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt) | Automatic global/channel skip settings, inherited modes, color validation, channel loading and reset controls. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/StoryboardImages.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/StoryboardImages.kt) | Shared bounded original-sheet image caches, validated sprite cropping and aspect-fit full-player seek preview rendering. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionBrowseUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionBrowseUi.kt) | Subscription/channel toolbars, scoped inputs, independent channel tabs, adaptive sort pills/dropdown and expanding inline channel search. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionControl.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionControl.kt) | Confirmed membership control, checking/pending/error states and explicit unsubscribe menu. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt) | Searchable sorted directory, viewing/upload statistics, counts and fallback/errors. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/VideoInformationUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/VideoInformationUi.kt) | Rich video description, optional exact statistics/credits/notices, safe external intents and channel-resolution dialog. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt) | Members badges, guest/account block actions and blocked-channel manager. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchActionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchActionsUi.kt) | Equal-width filled circular watch action buttons, wrapping labels below icons, disabled state and tooltips. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchComponents.kt) | Opaque watch metadata, labeled disclosure rows and native player/tool controls. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchPlayerResize.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchPlayerResize.kt) | Normalized occurrence-scoped resize progress, legacy restoration and nested scrolling against the current player height range. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/YouUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/YouUi.kt) | You identity/dashboard destinations and owned/subscribed playlist library sections/empty states. | [FEATURES](../../docs/FEATURES.md) |

## Native user interface

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/ui/CaptionAppearanceUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/CaptionAppearanceUi.kt) | Shared caption editor, native preview and SubtitleView appearance adapter. | [PLAYBACK](../../docs/PLAYBACK.md) |

## Scripts

| File | Responsibility | Guide |
| --- | --- | --- |
| [scripts/build-release.sh](../../scripts/build-release.sh) | Checked signing identity; unit/release/lint builds, immutable R8 mapping/report archives keyed by version and APK SHA-256, and APK/checksum outputs. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [scripts/check-avatar-fixture.py](../../scripts/check-avatar-fixture.py) | Disposable avatar metadata/proxy fixture assertions; requires generated thumbnail media. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-chapters-fixture.py](../../scripts/check-chapters-fixture.py) | Description chapter fixtures and forbidden ordinary timeline-preview request tracing. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-chat-fixture.py](../../scripts/check-chat-fixture.py) | Replay chunk/removal/replacement/failure and dedicated authenticated filter/delay fixture checks. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-community-fixture.py](../../scripts/check-community-fixture.py) | Disposable channel/post/attachment/comment fixture contract checks; requires thumbnail media. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-content-links-fixture.py](../../scripts/check-content-links-fixture.py) | Link/channel resolution, hashtag/bounded option fixtures and request tracing checks. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-documentation.py](../../scripts/check-documentation.py) | Reviewed maintained-file/Markdown inventory, fingerprints, all local links and generated source/test/coverage catalogs. | [TESTING](../../docs/TESTING.md) |
| [scripts/fixture-server.py](../../scripts/fixture-server.py) | Threaded local HTTP fixture, scenario controls, request tracing, synthetic account/content APIs and media/Range/failure responses. | [TESTING](../../docs/TESTING.md) |
| [scripts/fixture_support.py](../../scripts/fixture_support.py) | Standalone contract-check fixture lifecycle, early startup diagnostics and bounded process teardown. | [TESTING](../../docs/TESTING.md) |
| [scripts/generate-player-fixture.py](../../scripts/generate-player-fixture.py) | Canonical base audiovisual/DASH/HLS/caption/artwork fixtures plus codec, geometry and separate download tracks. | [TESTING](../../docs/TESTING.md) |
| [scripts/lib/android-fixture.sh](../../scripts/lib/android-fixture.sh) | Shared connected-device selection, local fixture readiness and owned-process/reverse-mapping teardown for Android runners. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-android.sh](../../scripts/test-android.sh) | Running-device fixture/media/ADB runner with focused device-only selection or combined JVM/device/lint checks; owns cleanup. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-download-restart.sh](../../scripts/test-download-restart.sh) | Two-phase force-stop checks for system and protected downloads, export cleanup and offline recovery. | [TESTING](../../docs/TESTING.md) |
| [scripts/tests/test_documentation.py](../../scripts/tests/test_documentation.py) | Disposable Git-inventory regression checks for new/removed sources, Markdown review, fingerprints, dated links and generated coverage. | [TESTING](../../docs/TESTING.md) |
| [scripts/tests/test_instance_access_fixture.py](../../scripts/tests/test_instance_access_fixture.py) | Local fixture HTTP access-gate precedence, bootstrap, expiry, policy and outage contract checks. | [TESTING](../../docs/TESTING.md) |
| [scripts/tests/test_workflows.py](../../scripts/tests/test_workflows.py) | Disposable regression checks for test selection/argument forwarding, fixture ownership, device/port conflicts, signals, release signing and R8 archive safeguards. | [TESTING](../../docs/TESTING.md) |

## Tests and test infrastructure

Every JVM and device Kotlin file is indexed in [the test catalog](TEST_CATALOG.md), with exact declared methods and explicit Ignore annotations. The [testing guide](../TESTING.md) explains runners and acceptance boundaries.

## Current documentation

| Location | Role |
| --- | --- |
| [README](../../README.md) | Installation, overview and links into the current guides. |
| [Documentation index](../README.md) | Current subsystem guides and terminology. |
| [Development](../DEVELOPMENT.md) | Prerequisites, builds, signing, releases and upstream maintenance. |
| [UI](../UI.md) | Opaque Material components, navigation, responsive layouts and player presentation. |
| [Features](../FEATURES.md) and [API](../API.md) | Supported capabilities and server contracts. |
| [Testing](../TESTING.md) | Repeatable checks and acceptance boundaries. |
| [Cleanup](../CLEANUP.md) | Latest repository audit changes, validation and known baseline failures. |

## Local and generated files

| Location | Role / handling |
| --- | --- |
| `android/local.properties` | Local Android SDK path, ignored. |
| `android/signing.properties`, `android/keystore/` | Personal release signing secrets; preserve securely for future updates, never commit. |
| `.env` | Real deployment secrets/settings; `.env.example` is the committed shape. |
| `android/.gradle/`, `.kotlin/`, `.idea/`, module `build/` | Tool caches, IDE state and generated builds/reports. |
| `.tools/` | Generated fixture media and disposable local verification output. |
| `artifacts/` | Release APKs/checksums/release notes from the build workflow. |
| `.git/` | Repository metadata, not application code. |

Ignored/generated/private files are excluded from fingerprints. Their contents and secrets are not reproduced in documentation.
