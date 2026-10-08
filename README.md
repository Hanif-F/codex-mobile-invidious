# Mobivious

A native Android client for Invidious. Browse YouTube through your chosen
instance, then watch or listen with a player built for Android.

Mobivious uses Invidious and Companion for video extraction and stream
resolution. You can browse and play public content without signing in; an
Invidious account adds subscriptions, playlists and shared watch history.

## Get started

**[Download the latest APK](https://github.com/Hanif-F/codex-mobile-invidious/releases/latest)**

Requires **Android 8.0 or newer**. Release APKs include all supported CPU
architectures, so there is no separate device-specific download.

1. Download and install the APK. Allow your browser or file manager to install
   apps when Android prompts you.
2. Open the **Settings gear → Server** to choose an HTTPS Invidious instance.
   The default is [invidious.wingress.net](https://invidious.wingress.net).
3. Start browsing, or sign in to your Invidious account on the selected instance
   for account features. Registration is available when the instance allows it.

See [instance compatibility](#instance-compatibility) below for server requirements.

### Verify a download (optional)

Download the matching `.apk.sha256` file alongside the APK. With both files in
the same directory, run:

```sh
sha256sum -c Mobivious-*.apk.sha256
```

An `OK` result confirms the APK matches the supplied checksum.

## Features

### Browse and discover

- Separate Popular and Trending tabs, filtered search, and channel browsing.
- Available channel tabs for videos, Shorts, streams, podcasts, releases,
  courses, playlists, clips, community posts and related channels.
- Native community posts with images, galleries, attachments and read-only
  polls, quizzes and comments.
- Open shared or pasted video, clip, playlist, channel, post and hashtag links.

### Watch and listen

- Adaptive streaming with quality, codec, audio-track, speed and caption controls.
- Background playback, audio-only mode, system media controls, fullscreen,
  picture in picture and a mini-player while browsing.
- Playlists, mixes, Play next and Add to queue, with previous/next and repeat.
- Rich descriptions, clickable timestamps, manual description chapters,
  read-only video comments and recommendations.

### Accounts and library

- Sign in, create an account, manage credentials, sessions and API tokens.
- Channel subscriptions, a subscription feed and a searchable channel directory
  sorted by Relevance, Latest upload, Most watched or A–Z, with upload and viewing
  details. The choice is saved on this device for each instance.
- A **You** hub for your identity, owned/subscribed playlists, clips, and searchable
  watch history, with account management in **Settings → Account**.
- Watched indicators and saved playback positions.
- RSS links, subscription OPML exports and private playlist Atom exports.
- **Clips:** create public 5–120 second moments with a title, precise timestamps,
  draggable filmstrip and independent preview. Find My Clips in **You**, or browse
  public clips on a channel's Clips tab. Watch with a
  clip-relative timeline, loop, share or copy the permalink, continue the full
  video at the same scene, and delete your own clips.

### Make it yours

- Light, dark or system appearance; compact lists and optional hidden thumbnails.
- Choose your launch homepage; retain your place across the fixed Popular, Trending,
  Subscriptions, and You tabs.
- Playback defaults, browsing and feed preferences, and channel blocking.
- **SponsorBlock:** optional segment skipping, category controls and channel overrides.
- **DeArrow:** optional community titles, an original-title toggle and title contributions.
- Supported preferences shared with the website when signed in; guest preferences
  saved locally for each instance.

## Instance compatibility

Public browsing and playback use Invidious APIs. **Account features and some
enhancements require this fork's mobile server extensions**; support varies on
other or older instances. Optional metadata is hidden when it is unavailable.

If a feature reports a missing API or permission, the instance may need an update.
After server updates that add token permissions, sign out and sign in again.
Installing an APK does not update the server.

Native clips require this fork's Clips API and existing migration 19. Deploy the
matching native token-scope and channel-tab updates, then sign out and sign in
again to renew existing tokens. Native clips stay on their originating instance;
foreign-instance links offer a browser action. Existing YouTube clip links are
resolved through the selected instance. Active livestream clipping and editing
published clips are outside this feature.

Channel sorting statistics require the matching server API update. Older servers
keep the channel directory available in A–Z order. Relevance uses viewing habits
from the last 90 days, boosted by unwatched uploads from the last seven days;
Most watched uses all-time distinct video counts. Existing native tokens already
have the required subscriptions and history permissions.

For instance owners, the [deployment guide](deploy/README.md) covers server setup,
migrations and token renewal. The [feature checklist](FEATURE_PARITY.md) records
individual capabilities and their dependencies.

Archived livestreams with `liveChatReplay` support offer on-demand chat replay
through the player chat button or watch-page entry. Replay follows playback and
seeks, with compact docked and movable/resizable overlay modes. The side panel
can use 10–70% of the screen. Use the chat menu to open settings or choose
**Adjust overlay**; attached handles move and resize the panel, with Done and
Cancel controls. Appearance saves immediately; **Save** applies timestamps,
user/word/RE2 regex filters, and a per-video chat delay. Positive delay values
show messages later. Signed-in timestamps, filters and delays sync with
Invidious, while appearance stays on the device. Guests save settings per instance.
Deploy the sibling native token-scope update and renew sign-in for account chat
sync. RE2 excludes lookaround/backreferences; unsupported imported patterns are
reported and skipped. Older instances without the availability flag remain usable
without chat. Chat is read-only.

### Current limitations

Clips, downloads, casting, upload notifications and timeline
thumbnail previews are not implemented. Comments, polls and quizzes are read-only;
RSS support provides links and exports rather than an in-app feed reader.

Native interaction and playback checks run on an already started emulator. See the
dated [verification record](VERIFICATION.md) for completed checks, remaining device
acceptance and reproduction instructions.

## Development and documentation

Built with Kotlin, Jetpack Compose and Media3. Start with the developer guide
whether you are contributing directly or working with an AI coding agent.

Debug builds appear as **Mobivious Preview** and install separately from the
release app for local review.

- [Developer guide](docs/DEVELOPMENT.md) — repository orientation, prerequisites,
  builds, tests, signing, releases and upstream maintenance.
- [Deployment guide](deploy/README.md) — install and update the server.
- [Feature checklist](FEATURE_PARITY.md) — detailed coverage compared with the web app.
- [Verification record](VERIFICATION.md) — dated results and repeatable checks.

## License

Mobivious is licensed under [AGPL-3.0-only](LICENSE). Invidious retains its
AGPL-3.0 license and attribution. Include corresponding source when distributing
modified binaries as required by the license.
