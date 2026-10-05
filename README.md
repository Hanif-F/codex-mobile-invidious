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
2. Open **Account → Settings → Server** to choose an HTTPS Invidious instance.
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

- Popular and trending feeds, filtered search, and channel browsing.
- Available channel tabs for videos, Shorts, streams, podcasts, releases,
  courses, playlists, community posts and related channels.
- Native community posts with images, galleries, attachments and read-only
  polls, quizzes and comments.
- Open shared or pasted video, playlist, channel, post and hashtag links.

### Watch and listen

- Adaptive streaming with quality, codec, audio-track, speed and caption controls.
- Background playback, audio-only mode, system media controls, fullscreen,
  picture in picture and a mini-player while browsing.
- Playlists, mixes, Play next and Add to queue, with previous/next and repeat.
- Rich descriptions, clickable timestamps, manual description chapters,
  read-only video comments and recommendations.

### Accounts and library

- Sign in, create an account, manage credentials, sessions and API tokens.
- Channel subscriptions, a subscription feed and a searchable channel directory.
- Owned and subscribed playlists, searchable watch history, watched indicators
  and saved playback positions.
- RSS links, subscription OPML exports and private playlist Atom exports.

### Make it yours

- Light, dark or system appearance; compact lists and optional hidden thumbnails.
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

For instance owners, the [deployment guide](deploy/README.md) covers server setup,
migrations and token renewal. The [feature checklist](FEATURE_PARITY.md) records
individual capabilities and their dependencies.

Archived livestreams with `liveChatReplay` support offer on-demand chat replay
through the player chat button or watch-page entry. Replay follows playback and
seeks, with docked and movable/resizable overlay modes. Chat settings provide
appearance controls, user/word/RE2 regex filters, and a per-video timing offset;
positive timing delays chat. Signed-in timestamps, filters and offsets sync with
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

- [Developer guide](docs/DEVELOPMENT.md) — repository orientation, prerequisites,
  builds, tests, signing, releases and upstream maintenance.
- [Deployment guide](deploy/README.md) — install and update the server.
- [Feature checklist](FEATURE_PARITY.md) — detailed coverage compared with the web app.
- [Verification record](VERIFICATION.md) — dated results and repeatable checks.

## License

Mobivious is licensed under [AGPL-3.0-only](LICENSE). Invidious retains its
AGPL-3.0 license and attribution. Include corresponding source when distributing
modified binaries as required by the license.
