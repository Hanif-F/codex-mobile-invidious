# Baseline and preservation inventory

[Overhaul entry point](README.md) · [Screen specification](SCREENS_AND_FLOWS.md)

## Source and evidence

| Reference | Established fact | Limit |
| --- | --- | --- |
| Current source `62c91828215759c3c50f6301808b2d9accb4ed92` | Mobivious 0.10.3, version code 21; Kotlin/Compose/Media3; one app module; min API 33, target/compile API 37. | Source inspection, not a new runtime acceptance run. |
| Pre-glass `da6ef88`, release 0.9.1 / 17 | Parent of `5a265aa` (initial glass redesign). Standard top app bar, search action and bottom navigation existed. | Historical source reference; do not roll the repository back or drop later features/fixes. |
| User report | Glass UI felt inconsistent and scrolling jagged; previous UI remembered as smoother. | No causal trace or controlled before/after measurement. |
| [Previous implementation report](../liquid-glass-implementation-2026-10-13/README.md#normal-speed-motion-and-frame-timing) | Settings timing comparison and normal-speed recordings exist. Results are mixed, with physical/low-end performance explicitly unmeasured. | Emulator measurements are not phone performance acceptance; settings journeys are not feed-scrolling benchmarks. |
| [Previous baseline](../liquid-glass-overhaul-2026-10-13/README.md) | Dated screenshots and provenance for the earlier overhaul. | Captures precede current source; retain their stated source, settings and dates. |
| [Refraction record](../refraction-2026-10-13/README.md) | Documents originating-window capture and later optical changes. | Historical implementation context; no requirement to preserve optical behavior. |

The user's handset supplied diagnostic context during planning. At the user's
request, acceptance targets Android phones generally, without a model-specific
gate. Use representative devices and record their configuration when measuring.

No fresh screenshots, recordings, device tests or phone timing were collected for
this documentation package. Proposed wireframes are specifications, not captures.

## Current implementation to replace

- `LiquidNavigation` folds Popular/Trending into Discover, with a floating
  Discover/Subscriptions/You dock and separate Search circle; a floating sidebar
  begins at 840dp. Search has a bottom input above the keyboard.
- `LiquidDesign`, `GlassMaterial`, `GlassScene`, `GlassContent`,
  `GlassDialog` and `GlassWindowBackdrop` supply glass styling, recordings,
  contrast sampling and originating-window PixelCopy. Backdrop is pinned to 1.0.6.
- Browse, library, watch and action facades spread those primitives through the
  screen families. Player settings retain a separate deferred glass profile.
- Appearance supports device-local Colored backgrounds, Adaptive control colors,
  Ambient mode and Reduce transparency. Ambient defaults to true when its key is
  absent; the first two choices default false.
- Most shared preference pages maintain drafts with Save/Cancel. Some local
  settings and the Trending region save immediately. Chat filters/delay require
  Save; chat appearance is immediate.
- `MobiviousApp` owns shell, routes, overlays and watch composition.
  `AppViewModel` already holds independent tab/return snapshots, preference
  deltas, a preference-write mutex and context checks. Reuse those safeguards.

Do not assume every glass-related mechanism causes the reported slowdown.
Recordings, readbacks, recomposition, image decoding, list work and transitions
are investigation candidates. Removal of decorative work is required by the
design; a speedup must still be demonstrated.

## Screen and state inventory

Screen IDs below map directly to sections of [Screens and flows](SCREENS_AND_FLOWS.md).
Child dialogs and editor states inherit their parent plus the shared state rules.

| IDs | Existing destinations and actions | Current owners under `ui/` |
| --- | --- | --- |
| N1–N3 | Main shell, mini-player, global search/results/filters | MobiviousApp, LiquidNavigation, SearchUi, PlayerPresentation |
| B1–B2 | Popular; Trending category and searchable country picker | DiscoveryUi, MobiviousApp |
| B3–B4 | Subscription uploads, scoped search, Channels directory, sorting, RSS/OPML | SubscriptionsUi, SubscriptionBrowseUi, SubscriptionControl, RssUi |
| B5–B8 | Channel header/tabs/sorts/scoped search; description; posts/detail/comments; image gallery; hashtag results | ChannelDescriptionUi, CommunityUi, CommentsUi, MobiviousApp |
| L1–L3 | You; My/Subscribed playlists; playlist/mix detail and save/create/edit/delete | YouUi, PlaylistUi, LibraryActionsUi |
| L4 | History, scoped search, calendar groups, remove/clear | YouUi, MobiviousApp, MetadataUi |
| L5–L6 | Downloads, track selection, transfers, offline play, delete/export/conversion | DownloadsUi |
| L7–L8 | My/channel clips, clip detail/playback/delete; creation/filmstrip/preview | ClipsUi, ClipsState |
| L9 | RSS links, private links, OPML/Atom export | RssUi |
| A1–A3 | Sign-in/register/challenges; account credentials/delete; sessions/revoke/token creation | AccountUi |
| S1–S4 | Settings root; Appearance; Playback; Browsing/block list | SettingsUi, VisibilityUi |
| S5–S7 | Subscription preferences; History & library; Server/About | SettingsUi |
| S8–S10 | SponsorBlock/global/channel overrides; DeArrow identity/contributions; AI filter/status | SponsorBlockUi, DeArrowUi, AiFilterUi |
| W1–W3 | Watch information/actions; transport/settings/fullscreen/PiP; queue | PlayerUi, PlayerControlsUi, WatchActionsUi, VideoInformationUi, PlaybackQueueUi |
| W4–W6 | Comments/replies; chapters; archived replay/chat settings/overlay adjustment | CommentsUi, ChaptersUi, LiveChatUi |

All inventory entries must cover initial loading, retained-data refresh/load-more,
empty, failure/retry, offline and signed-out behavior where meaningful. Authentication
forms have submission states rather than pagination; local settings and offline
downloads remain usable without a network. See the explicit applicability rules
in the screen guide.

## Invariants carried forward

| Subsystem | Preserve |
| --- | --- |
| Playback | Service/controller ownership, one decoder through mini/watch/fullscreen, native black video backing/loading shutter, geometry, track identity, audio-only, background/PiP policy and independent clip preview lifetime. |
| Navigation | Per-tab data/position, scoped-search origins, content links, sign-in resumption, nested panel Back handling and clearing account-sensitive state on context changes. |
| Queues/playlists | Queue occurrence identity, duplicate videos, playlist occurrence actions, service continuation and existing repeat semantics. |
| Lists/data | Raw pagination before filtering, server order, opaque continuations, retry cursor, membership/block/AI rules, exact versus qualified metadata and image fallbacks. |
| Storage | Device/profile/account save boundaries, guest normalization, tokens/credentials protection, local downloads and resume surviving sign-out/server changes. |
| Features | Supported channel tabs and optional metadata, read-only rich content, filtering/skip behavior and unavailable-server fallbacks. |
| Interaction fixes | Separate control hit areas; claimed drags cannot seek/tap; Android top-edge fullscreen gesture; keyboard/footer clearance; open menus and focused edits survive theme changes. |
| Accessibility | State semantics, headings, ≥48dp targets, TalkBack alternatives to gestures, large-text wrapping and reduced motion. |

Preserve the purpose of old visual regression tests, such as clipping, readable
text and correct input ownership, while replacing glass-specific pixel expectations.
Transparent caption windows, black decoder surfaces and user-controlled chat
overlay opacity are media behaviors, not reasons to keep glass.

## Source-of-truth references

- [Features](../FEATURES.md), [Settings](../SETTINGS.md): capability and preference inventories.
- [Architecture](../ARCHITECTURE.md), [Playback](../PLAYBACK.md): state, lifecycle and queue contracts.
- [Library](../LIBRARY.md), [Storage](../STORAGE_AND_SECURITY.md), [API](../API.md): ownership, validation and compatibility.
- [Source map](../reference/SOURCE_MAP.md), [Test catalog](../reference/TEST_CATALOG.md), [Testing](../TESTING.md): exact source/test locations and existing runners.

Before implementing, read the latest versions and record the actual starting
commit. Source changes after this baseline require reconciling inventories, not
discarding newer behavior.
