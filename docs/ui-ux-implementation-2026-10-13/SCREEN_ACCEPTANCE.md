# Screen acceptance audit

[Implementation report](README.md) · [Required flows](../ui-ux-overhaul-2026-10-13/SCREENS_AND_FLOWS.md)

This inventory covers all 39 required IDs. The implementation report records
actual functional results and rendered/input review; performance acceptance was
explicitly deferred to the user on 14 October 2026. The class mapping below is
supported by the method-level aggregate and its raw result references. Shared
state coverage uses applicable domain/device cases, rather than requiring every
network state for purely local controls or the full Cartesian product.

| ID | Screen / contract | Current owners | Verification families |
| --- | --- | --- | --- |
| N1 | Four roots, per-root stack/position, reselect, bar/rail | AppScaffold, AppViewModel, MobiviousApp | NavigationHubSmokeTest, ResponsiveShellSmokeTest, AppWindowPresentationTest |
| N2 | Opaque full-width mini-player, IME, transport/gestures | MobiviousApp, PlayerPresentation | PlaybackVisibilitySmokeTest, AppUiSmokeTest |
| N3 | Top search, scoped return, immediate filters and summaries | SearchUi, MobiviousApp, AppViewModel | DiscoverySearchSmokeTest, VisibilitySmokeTest, SearchHistorySmokeTest |
| B1 | Popular, density/artwork/visibility/pagination | BrowseComponents, MobiviousApp | AppUiSmokeTest, VisibilitySmokeTest, AiFilterSmokeTest |
| B2 | Trending category/country, confirmed/requested choice | DiscoveryUi, AppViewModel | DiscoverySearchSmokeTest |
| B3 | Creator shortcuts, scoped search, persistent Channels access | SubscriptionBrowseUi, SubscriptionControl | HomeSubscriptionsSmokeTest, SubscriptionJourneyTest |
| B4 | Local channel directory, sorting, counts and recovery | SubscriptionsUi | SubscriptionSortingSmokeTest, SubscriptionPresentationTest |
| B5 | Banner/identity before channel tabs/search/results | MobiviousApp, AvatarUi, SubscriptionBrowseUi | ChannelSmokeTest, AvatarsSmokeTest, SubscriptionPresentationTest |
| B6 | Channel information and rich links | ChannelDescriptionUi | ChannelDescriptionPresentationTest, ContentLinksSmokeTest |
| B7 | Posts/detail/comments/gallery/polls | CommunityUi, CommentsUi | CommunitySmokeTest, CommunityPresentationTest |
| B8 | Hashtag and linked browse results | AppViewModel, MobiviousApp | ContentLinksSmokeTest, AppSmokeTest |
| L1 | Identity, guest sign-in, continuous library rows | YouUi | LibraryPresentationTest, LibraryJourneyTest, AccountNavigationSmokeTest |
| L2 | Owned/subscribed playlists and mixes | YouUi, PlaylistUi | NavigationHubSmokeTest, PlaylistRssSmokeTest |
| L3 | Detail/save/create/edit/delete, explicit submit/discard | PlaylistUi, LibraryActionsUi | QueueLibrarySmokeTest, AppSmokeTest |
| L4 | History/search/calendar/remove/clear/progress | MobiviousApp, MetadataUi | SearchHistorySmokeTest, WatchedIndicatorsSmokeTest |
| L5 | Local downloads and opaque details sheet | DownloadsUi | DownloadsSmokeTest, DownloadsPersistenceSmokeTest |
| L6 | Explicit selection/export/conversion and cancellation | DownloadsUi, DownloadExportService | DownloadsSmokeTest; inherited conversion Ignore remains explicit |
| L7 | My/channel clips, playback/detail/delete | ClipsUi | ClipsSmokeTest, SecondaryScreensJourneyTest |
| L8 | Explicit clip editor, preview/filmstrip/discard | ClipsUi, ClipsState | ClipsSmokeTest |
| L9 | RSS/private links/OPML/Atom | RssUi | PlaylistRssSmokeTest, SecondaryScreensJourneyTest |
| S1 | Settings groups and links to outstanding edits | SettingsUi, MobiviousApp | SettingsSmokeTest, SecondaryScreensJourneyTest |
| S2 | Twelve palettes, scopes, ambient migration, no optical flags | AppTheme, AccentPreset, SettingsUi, SessionStore | AppThemeTest, AppThemePresentationTest, AppWindowPresentationTest |
| S3 | Playback defaults, captions, local playback/PiP choices | SettingsUi, AppViewModel | SettingsSmokeTest, StreamControlsSmokeTest |
| S4 | Browsing/region/block list | SettingsUi, VisibilityUi, DiscoveryUi | VisibilitySmokeTest, DiscoverySearchSmokeTest |
| S5 | Subscription preferences and validation | SettingsUi, AppViewModel | SettingsSmokeTest, HomeSubscriptionsSmokeTest |
| S6 | History/resume/default playlist and confirmed side effects | SettingsUi, AppViewModel | SettingsSmokeTest, WatchedIndicatorsSmokeTest |
| S7 | Explicit server Connect and About | SettingsUi | AppUiSmokeTest, SecondaryScreensJourneyTest |
| S8 | SponsorBlock modes/colors/sparse channel overrides | SponsorBlockUi | SponsorBlockSmokeTest, DeviceProfilesSmokeTest |
| S9 | DeArrow preferences, explicit identity/contributions | DeArrowUi | DeArrowSmokeTest |
| S10 | AI filter actions/status/scopes/exceptions | AiFilterUi | AiFilterSmokeTest |
| A1 | Sign-in/register/CAPTCHA/autofill/validation | AccountUi | AccountNavigationSmokeTest, VisualFixesSmokeTest, ResponsiveShellSmokeTest |
| A2 | Explicit credentials/delete and context isolation | AccountUi, AppViewModel | AccountNavigationSmokeTest, DeviceProfilesSmokeTest |
| A3 | Sessions/revoke/token creation and sensitive fields | AccountUi | AccountNavigationSmokeTest |
| W1 | Opaque information, primary/supporting panes, recommendations | MobiviousApp, VideoInformationUi, WatchActionsUi | AppUiSmokeTest, VideoInformationPresentationTest, DeArrowSmokeTest |
| W2 | Single decoder, transport/tracks/gestures/fullscreen/PiP | PlayerUi, PlayerControlsUi, PlayerPresentation | PlaybackVisibilitySmokeTest, StreamControlsSmokeTest, PlayerControlsPresentationTest |
| W3 | Occurrence-safe full-height/supporting-pane queue | PlaybackQueueUi | QueueLibrarySmokeTest, PlaybackQueuePresentationTest, AiFilterSmokeTest |
| W4 | Comments/replies, mutual exclusion and retained scroll | CommentsUi | CommentsSmokeTest, WatchCommentsPresentationTest |
| W5 | Chapters, current chapter, exact seek and occurrence lifetime | ChaptersUi | ChaptersSmokeTest, ChaptersRuntimeTest, ChaptersPresentationTest |
| W6 | Replay, automatic preferences/delay, explicit geometry | LiveChatUi, AppViewModel | LiveChatSmokeTest, PreferenceSaveControllerTest |

The final [implementation report](README.md) records build/JVM/lint results,
ordered Android reruns, actual TalkBack/keyboard/RTL/software/reduced-motion
checks, download process-restart evidence and reviewed rendered/input artifacts.
The conversion Ignore in L6 remains an explicitly recorded emulator limitation;
no successful conversion is claimed. The user's performance deferral covers the
absolute deadline target and comparable current/pre-glass/new measurements.
