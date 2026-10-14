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
| [android/app/build.gradle.kts](../../android/app/build.gradle.kts) | App ID/version/SDK, build variants/signing, Java target and all runtime/test dependencies. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
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
| [android/macrobenchmark/src/main/java/net/wingress/mobivious/benchmarks/UiScrollBenchmark.kt](../../android/macrobenchmark/src/main/java/net/wingress/mobivious/benchmarks/UiScrollBenchmark.kt) | Ordinary UI account/origin setup, verified preference reads and deterministic Full-compilation timing journeys. | [TESTING](../../docs/TESTING.md) |
| [android/macrobenchmark/src/main/res/xml/fixture_network_security.xml](../../android/macrobenchmark/src/main/res/xml/fixture_network_security.xml) | Instrumentation-side loopback HTTP exception for deterministic local fixtures. | [TESTING](../../docs/TESTING.md) |
| [scripts/summarize-ui-performance.py](../../scripts/summarize-ui-performance.py) | Median per-iteration CPU/overrun percentiles, frame counts and positive-overrun fractions from raw benchmark samples. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-ui-performance.sh](../../scripts/test-ui-performance.sh) | Isolated benchmark runner, fixture/reverse ownership, source/APK provenance and raw timing/trace capture. | [TESTING](../../docs/TESTING.md) |

## Android manifests and resources

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/debug/AndroidManifest.xml](../../android/app/src/debug/AndroidManifest.xml) | Preview app label and restricted debug network security override. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [android/app/src/debug/res/xml/debug_network_security.xml](../../android/app/src/debug/res/xml/debug_network_security.xml) | Debug cleartext allowlist for localhost, 127.0.0.1 and 10.0.2.2 only. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/AndroidManifest.xml](../../android/app/src/main/AndroidManifest.xml) | Permissions, backup policy, exported components, app links, PiP and FileProvider/service declarations. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/res/drawable/ic_launcher.xml](../../android/app/src/main/res/drawable/ic_launcher.xml) | Launcher/splash vector artwork. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/drawable/ic_launcher_background.xml](../../android/app/src/main/res/drawable/ic_launcher_background.xml) | Adaptive launcher background artwork. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/drawable/ic_launcher_monochrome.xml](../../android/app/src/main/res/drawable/ic_launcher_monochrome.xml) | Monochrome themed launcher vector. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/layout/clip_preview.xml](../../android/app/src/main/res/layout/clip_preview.xml) | Separate Media3 TextureView preview with native controls for clip editing. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](../../android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) | Adaptive launcher foreground/background/monochrome composition. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values-night/colors.xml](../../android/app/src/main/res/values-night/colors.xml) | Dark launch background and dark system-bar flag. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/colors.xml](../../android/app/src/main/res/values/colors.xml) | Light launch background and light system-bar flag. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/media_colors.xml](../../android/app/src/main/res/values/media_colors.xml) | Native clip-preview footer black scrim and opaque Media3 settings popup colors; no tinted optical surfaces. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/values/styles.xml](../../android/app/src/main/res/values/styles.xml) | No-action-bar launch/splash theme, font and system-bar appearance. | [UI](../../docs/UI.md) |
| [android/app/src/main/res/xml/data_extraction_rules.xml](../../android/app/src/main/res/xml/data_extraction_rules.xml) | Explicit backup/device-transfer exclusions for app-owned data. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/res/xml/rss_paths.xml](../../android/app/src/main/res/xml/rss_paths.xml) | FileProvider exposure restricted to the RSS cache subtree. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |

## Application entry points

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/MainActivity.kt](../../android/app/src/main/java/net/wingress/mobivious/MainActivity.kt) | Activity lifecycle, incoming share/app links, fullscreen system bars and PiP actions/bounds. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/MobiviousApplication.kt](../../android/app/src/main/java/net/wingress/mobivious/MobiviousApplication.kt) | Application dependency construction, shared repository state and context reset propagation. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |

## Data and API

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/AccentPreset.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AccentPreset.kt) | Stable device accent IDs and exact light/dark role tables with blue fallback. | [SETTINGS](../../docs/SETTINGS.md) |

## Data and domain

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/data/Accounts.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Accounts.kt) | Registration/session models, guided API permissions, scope validation and expiry choices. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/AiFilter.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AiFilter.kt) | AiSList settings/decisions, versioned bounded classification cache, batching, retries and shared lookups. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/AppPreferences.kt](../../android/app/src/main/java/net/wingress/mobivious/data/AppPreferences.kt) | All shared preference defaults, JSON parsing, sparse patches/merges, homepage/caption/codec rules. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Avatars.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Avatars.kt) | Existing avatar metadata parsing, same-instance image proxy normalization, Unicode initials and redirect-free image client. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/CaptionLanguages.kt](../../android/app/src/main/java/net/wingress/mobivious/data/CaptionLanguages.kt) | Website-compatible English language choices for three caption priorities. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Chapters.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Chapters.kt) | Description timestamp chapter parsing, validity, availability and current-chapter rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Clips.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Clips.kt) | Native/upstream clip identity and bounds, title/time validation, eligibility, default ranges and storyboard VTT cues. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Comments.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Comments.kt) | Read-only comment models, target/reply pagination controller, position retention and native link mapping. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Community.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Community.kt) | Community attachments/posts, post links, owner metadata and banner/post-image proxy rules. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ContentLinks.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ContentLinks.kt) | Native/external link targets, validated URL/timestamps/indexes, nullable playback overrides/bounds and share URLs. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ContentVisibility.kt) | Member/block visibility by surface, raw-page merging, search exceptions and confirmed blocked-channel repository. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DeArrow.kt) | Title/private-identity validation and bounded, deduplicated replacement-title lookups. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DeviceProfiles.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DeviceProfiles.kt) | Stable local ownership namespaces, checked migration/transfer/purge and strict guest preference normalization. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DiscoverySearch.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DiscoverySearch.kt) | Trending categories, localized region catalog and typed mixed-search parsing/visibility/exhaustion. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DisplayFormats.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DisplayFormats.kt) | Localized compact/grouped counts, precision, size/bitrate/time/date formatting and strict non-negative numeric parsing. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/DownloadRepository.kt](../../android/app/src/main/java/net/wingress/mobivious/data/DownloadRepository.kt) | SQLite JSON journal, DownloadManager enqueue/reconciliation/retry/cancel/delete, content validation and broadcast receiver. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Downloads.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Downloads.kt) | Download catalog/selection/asset/record/status models and readiness rules. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/History.kt](../../android/app/src/main/java/net/wingress/mobivious/data/History.kt) | Organized/legacy history parsing, server-calendar grouping and scoped search input/page state. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/HttpCall.kt](../../android/app/src/main/java/net/wingress/mobivious/data/HttpCall.kt) | Cancellable asynchronous OkHttp response-body adapter and closed-response transport snapshot. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/InvidiousApi.kt](../../android/app/src/main/java/net/wingress/mobivious/data/InvidiousApi.kt) | Complete instance HTTP client, auth/error/cache semantics and consumed API endpoint methods. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/LiveChat.kt](../../android/app/src/main/java/net/wingress/mobivious/data/LiveChat.kt) | Replay chunks/settings, RE2 filters, bounded presentation-driven replay windows and timing sync. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Models.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Models.kt) | Core video/channel/playlist/account/context/stream models and defensive API JSON parsing. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/PlaybackQueue.kt](../../android/app/src/main/java/net/wingress/mobivious/data/PlaybackQueue.kt) | Occurrence/source/snapshot models, source-page merging, insertion/removal/repeat/eligibility and save/block state. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/ResponseCache.kt](../../android/app/src/main/java/net/wingress/mobivious/data/ResponseCache.kt) | Hashed device-private feed snapshots with age, size and count bounds and atomic replacement. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Rss.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Rss.kt) | Validated public/private/mix feed URL construction and RSS/OPML/Atom panel state. | [API](../../docs/API.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/SessionStore.kt](../../android/app/src/main/java/net/wingress/mobivious/data/SessionStore.kt) | Encrypted session, checked SharedPreferences, context generation and profile-local/device-wide settings access. | [STORAGE_AND_SECURITY](../../docs/STORAGE_AND_SECURITY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/SponsorBlock.kt](../../android/app/src/main/java/net/wingress/mobivious/data/SponsorBlock.kt) | Segment/settings/category/mode/color/channel models, parsing, sparse patches and validation. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/Subscriptions.kt](../../android/app/src/main/java/net/wingress/mobivious/data/Subscriptions.kt) | Directory statistics, local search/sort fallback and revision-fenced confirmed subscription mutations. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/data/WatchedState.kt](../../android/app/src/main/java/net/wingress/mobivious/data/WatchedState.kt) | Watched/progress models and indicators plus revision-fenced history/progress reconciliation and local fallback. | [LIBRARY](../../docs/LIBRARY.md) |

## File export

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/downloads/DownloadExportService.kt](../../android/app/src/main/java/net/wingress/mobivious/downloads/DownloadExportService.kt) | Foreground file copying, MP4 merging/consented conversion, output validation, progress, cleanup and interruption journal. | [LIBRARY](../../docs/LIBRARY.md) |

## Playback

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/player/CodecAwareTrackSelector.kt](../../android/app/src/main/java/net/wingress/mobivious/player/CodecAwareTrackSelector.kt) | Supported DASH codec/preset selection before initial media requests, retaining adaptive decoder checks. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/DownloadPlayback.kt](../../android/app/src/main/java/net/wingress/mobivious/player/DownloadPlayback.kt) | Local single/paired media sources and completed captions without remote metadata. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackRules.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackRules.kt) | Pure seek, explicit/resume start, completion save and bounded-link rules. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/PlaybackService.kt](../../android/app/src/main/java/net/wingress/mobivious/player/PlaybackService.kt) | ExoPlayer/MediaSession lifetime, trusted commands, history/progress ownership, SponsorBlock and PiP transport actions. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/QueueCoordinator.kt](../../android/app/src/main/java/net/wingress/mobivious/player/QueueCoordinator.kt) | Service-owned lazy resolution, source pagination, occurrence transitions, tracks, bounds, offline media, clips and successor behavior. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/QueueSessionPlayer.kt](../../android/app/src/main/java/net/wingress/mobivious/player/QueueSessionPlayer.kt) | Logical system-session queue timeline, safe durations, placeholders and forwarding seeks/repeat to coordinator. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SeekAccumulator.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SeekAccumulator.kt) | Ten-second tap accumulation with monotonic 600ms deadline and cancellation. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SessionTimeline.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SessionTimeline.kt) | Non-live window epoch normalization for safe Media3 session serialization. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/SponsorBlockEngine.kt](../../android/app/src/main/java/net/wingress/mobivious/player/SponsorBlockEngine.kt) | Overlapping automatic skips, manual offers, dismissal and playback-session segment history. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/StreamCatalog.kt](../../android/app/src/main/java/net/wingress/mobivious/player/StreamCatalog.kt) | Supported representation identity, API metadata matching, audio grouping, menu pruning and codec/quality selection. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/VideoGeometry.kt](../../android/app/src/main/java/net/wingress/mobivious/player/VideoGeometry.kt) | Decoded aspect ratio, embedded sizing, orientation and exact bounded PiP fractions. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/VideoSelection.kt](../../android/app/src/main/java/net/wingress/mobivious/player/VideoSelection.kt) | Occurrence-owned Auto/Preset/Manual video policy and reload fallback. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/player/WatchPlayerScroll.kt](../../android/app/src/main/java/net/wingress/mobivious/player/WatchPlayerScroll.kt) | Pure nested-scroll consumption rules for compact/expanded watch resizing. | [UI](../../docs/UI.md) |

## Compose UI

| File | Responsibility | Guide |
| --- | --- | --- |
| [android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AccountUi.kt) | Sign-in/register/captcha, credentials, account deletion, sessions and token forms with transient secret handling. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ActionControlsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ActionControlsUi.kt) | Palette-seeded independent popup menus, thin grouped preference rows, overflow actions and choice semantics. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AiFilterUi.kt) | Accessible classification warning thumbnails replacing artwork when configured. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AmbientPalette.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AmbientPalette.kt) | Nine-zone spatial palette extraction and one-copy rate/backoff/generation gate. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppComponents.kt) | Native opaque surface roles, Material controls, tooltips, snackbars and automatic-save feedback. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppLayout.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppLayout.kt) | Measured native top bars, section headings and accessible wrapping selection controls. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppScaffold.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppScaffold.kt) | Four fixed root destinations, labeled bottom navigation/rail and window width classes. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppTheme.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppTheme.kt) | Exact neutral light/dark Material 3 roles, six accents, standard typography, shapes and responsive spacing. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppViewModel.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppViewModel.kt) | App-facing StateFlows, browse/navigation snapshots, account/library actions, preference writes and MediaController coordination. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AppWindowLayout.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AppWindowLayout.kt) | WindowManager occluding-fold observation and contiguous usable-pane policy with physical coordinate placement. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/AvatarUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/AvatarUi.kt) | Redirect-free Coil avatar loader, fallback identity, creator/channel headers and subscription chips. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/BrowseComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/BrowseComponents.kt) | Pinned opaque browsing toolbars, sort menus, tabs and artwork components without scene recording. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChannelDescriptionUi.kt) | Adaptive creator description sheet with native rich links. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ChaptersUi.kt) | Current chapter label and retained chapter panel/sheet state with seek actions. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ClipsState.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ClipsState.kt) | Clip resolution/editor draft, validation, publication and occurrence-owned preview state. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/ClipsUi.kt) | Clip cards/details/share/delete, foreign-origin dialog, independent preview and draggable filmstrip editor. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/CommentsUi.kt) | Comment entry/drawer/replies, native rich text/emoji/link parsing, sorting and read-only presentation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/CommunityUi.kt) | Related channel/post cards, inline expansion, post details/comments, share and image gallery/viewer. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DeArrowUi.kt) | Original/replacement title controls, private identity form and reviewed proposal/vote panel. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DiscoveryUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DiscoveryUi.kt) | Searchable country choice/picker and Trending category/region controls. | [FEATURES](../../docs/FEATURES.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/DownloadsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/DownloadsUi.kt) | Track/caption selection dialog, download library/progress/details, file picker and conversion-consent UI. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LibraryActionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryActionsUi.kt) | Video Play/Listen/queue/save/block/removal menus and create-and-save destination sheet. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LibraryComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LibraryComponents.kt) | Native list actions, confirmations and responsive full-height opaque panels with inset ownership. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/LiveChatUi.kt) | Replay list, follow/older/errors, dock/overlay editor and appearance/filter/delay settings. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/MetadataUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/MetadataUi.kt) | Shared publication labels and video metadata line presentation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/MobiviousApp.kt) | Root Compose shell, browse/watch/library/settings composition, panels, dialogs and navigation/action wiring. | [ARCHITECTURE](../../docs/ARCHITECTURE.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackQueueUi.kt) | Explicit queue expansion, current occurrence following, bounded inner list, repeat and recovery controls. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaybackVideoSurface.kt) | Service-owned native video surface, screen-awake lifecycle, transparent caption/error backgrounds and slate loading shutters. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerControlsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerControlsUi.kt) | Measured transport/header/footer, full-width timeline slot and compact seek/SponsorBlock feedback placement. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerGestures.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerGestures.kt) | Presentation modes, touch-exploration observation, density-independent drag arbitration and cancellation. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerPresentation.kt) | Single-surface mini/watch/fullscreen host, animations, modality/lifecycle gates, chat and ambient integration. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlayerUi.kt) | Video controls, timeline/scrub gestures, SponsorBlock prompt, tracks/settings, speed/audio/caption/refresh controls. | [PLAYBACK](../../docs/PLAYBACK.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PlaylistUi.kt) | Playlist cards/headers, subscribe state and owned title/privacy/description editor. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/PreferenceSaveController.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/PreferenceSaveController.kt) | ViewModel-owned revisioned preference intent, coalescing, debounce, serialization, failure recovery and context isolation. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/RssUi.kt) | Feed link warning/copy/share/open and XML share/save sheet with scoped file permissions. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SearchUi.kt) | Reusable draft input with keyboard/search/physical Enter submission semantics. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SettingsUi.kt) | Colored settings environment, related opaque Material preference sections, Save/Cancel footer, server/about and category routing. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SponsorBlockUi.kt) | Global/channel skip settings, inherited modes, color editor and reset/save sheet. | [SETTINGS](../../docs/SETTINGS.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionBrowseUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionBrowseUi.kt) | Subscription/channel pinned toolbars, scoped inputs, tab and sort controls. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionControl.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionControl.kt) | Confirmed membership pill, checking/pending/error states and explicit unsubscribe menu. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/SubscriptionsUi.kt) | Searchable sorted directory, viewing/upload statistics, counts and fallback/errors. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/VideoInformationUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/VideoInformationUi.kt) | Rich video description, optional exact statistics/credits/notices, safe external intents and channel-resolution dialog. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/VisibilityUi.kt) | Members badges, guest/account block actions and blocked-channel manager. | [LIBRARY](../../docs/LIBRARY.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchActionsUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchActionsUi.kt) | Equal-width labeled watch action circles with disabled state and tooltips. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchAmbient.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchAmbient.kt) | PixelCopy sampler lifecycle, animation/power gates, color smoothing, contrast-bounded light and separate backdrop scene. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchComponents.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchComponents.kt) | Opaque watch metadata, labeled disclosure rows and native player/tool controls. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/WatchPlayerResize.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/WatchPlayerResize.kt) | Saveable occurrence resize progress and nested-scroll connection for embedded video. | [UI](../../docs/UI.md) |
| [android/app/src/main/java/net/wingress/mobivious/ui/YouUi.kt](../../android/app/src/main/java/net/wingress/mobivious/ui/YouUi.kt) | You identity/dashboard destinations and owned/subscribed playlist library sections/empty states. | [FEATURES](../../docs/FEATURES.md) |

## Scripts

| File | Responsibility | Guide |
| --- | --- | --- |
| [scripts/build-release.sh](../../scripts/build-release.sh) | Generate personal signing material only if absent, run unit/release/lint and copy versioned APK/checksum. | [DEVELOPMENT](../../docs/DEVELOPMENT.md) |
| [scripts/check-avatar-fixture.py](../../scripts/check-avatar-fixture.py) | Disposable avatar metadata/proxy fixture assertions; requires generated thumbnail media. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-chapters-fixture.py](../../scripts/check-chapters-fixture.py) | Description chapter fixtures and forbidden ordinary timeline-preview request tracing. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-chat-fixture.py](../../scripts/check-chat-fixture.py) | Replay chunk/removal/replacement/failure and dedicated authenticated filter/delay fixture checks. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-community-fixture.py](../../scripts/check-community-fixture.py) | Disposable channel/post/attachment/comment fixture contract checks; requires thumbnail media. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-content-links-fixture.py](../../scripts/check-content-links-fixture.py) | Link/channel resolution, hashtag/bounded option fixtures and request tracing checks. | [TESTING](../../docs/TESTING.md) |
| [scripts/check-documentation.py](../../scripts/check-documentation.py) | Maintained inventory, source fingerprints, generated reference catalogs and local Markdown path validation. | [TESTING](../../docs/TESTING.md) |
| [scripts/fixture-server.py](../../scripts/fixture-server.py) | Threaded local HTTP fixture, scenario controls, request tracing, synthetic account/content APIs and media/Range/failure responses. | [TESTING](../../docs/TESTING.md) |
| [scripts/generate-player-fixture.py](../../scripts/generate-player-fixture.py) | FFmpeg-generated rich tracks, codecs, aspect ratios, ambient and download media in supplied directory. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-android.sh](../../scripts/test-android.sh) | Running-device fixture/media/ADB runner for unit, connected and lint checks; owns cleanup. | [TESTING](../../docs/TESTING.md) |
| [scripts/test-download-restart.sh](../../scripts/test-download-restart.sh) | Two-process background-download persistence/offline-resume check, existing APK installation and local evidence. | [TESTING](../../docs/TESTING.md) |

## Tests and test infrastructure

Every JVM and device Kotlin file is indexed in [the test catalog](TEST_CATALOG.md), with exact declared methods and explicit Ignore annotations. The [testing guide](../TESTING.md) explains runners and acceptance boundaries.

## Existing documentation and historical evidence

| Location | Role |
| --- | --- |
| [README](../../README.md) | Installation, overview and links into the current guides. |
| [Development](../DEVELOPMENT.md) | Prerequisites, wrapper builds, signing, release publishing and upstream maintenance. |
| [UI](../UI.md) | Current opaque Material components, four-root navigation, responsive layouts and player presentation. |
| [UI overhaul specification](../ui-ux-overhaul-2026-10-13/README.md) | Approved screen contracts, design roles, migration and verification requirements. |
| [UI implementation evidence](../ui-ux-implementation-2026-10-13/README.md) | Current functional results, accessibility review, performance evidence and open acceptance gates. |
| [Liquid Glass](../LIQUID_GLASS.md) | Superseded optical design references, retained for historical context. |
| [Overhaul baseline](../liquid-glass-overhaul-2026-10-13/README.md) | Immutable pre-implementation images, provenance and approved brief. |
| [Overhaul implementation](../liquid-glass-implementation-2026-10-13/README.md) | App-wide material changes, emulator acceptance, screenshots, normal-speed recordings and frame timing. |
| [Feature parity](../../FEATURE_PARITY.md) | Dated web/native capability comparison, with exclusions and server requirements. |
| [Verification](../../VERIFICATION.md) | Chronological commands, results, device limitations and release evidence. |
| [Visual audit report](../visual-audit-2026-10-11/REPORT.md) | Dated screenshot/motion audit, EVIDENCE index, manifests, collector/grouping scripts, temporary harness/fixture patches and diagnostic logs. |
| [Visual fixes](../visual-fixes-2026-10-11/FIXES.md) | Dated fixes, evidence manifests, temporary capture harness, motion and build/test logs. |
| [Ambient verification](../ambient-mode-2026-10-11/VERIFICATION.md) | Ambient screenshots, motion, machine-readable verification and acceptance notes. |
| [Player feedback](../player-feedback-2026-10-11/README.md) | Feedback-specific screenshots and verification notes. |

Evidence images/video/GIF/JSON/text files are supporting records of their collection, not additional runtime features. Temporary `.kt.txt` capture harnesses and fixture patches are reproducibility material, not app sources.

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
