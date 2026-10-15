# Storage, isolation and security

[Documentation index](README.md) · [Settings](SETTINGS.md)

## Persistence inventory

Paths describe Android sandbox locations, not host repository paths.

| Store | Contents and lifetime |
| --- | --- |
| SharedPreferences `mobivious` | Selected server, encrypted current session, local profile values, migration version, device-wide accent settings; dormant legacy colored-background/adaptive-control keys. |
| AndroidKeyStore alias `mobivious.session` | AES key for session encryption; the key is not a checked-in file. |
| `device.v2.<SHA-256>.…` preference keys | Account/guest + normalized instance-owned background/PiP, guest preferences, category/sort, chat appearance/delay, resume positions, search assistance/recents and visibility snapshots. |
| `cacheDir/feeds/` | Selected successful feed/search response snapshots, hashed keys, bounded count/age/size. |
| SQLite `downloads.db`, schema version 1 | Table `downloads(id TEXT PRIMARY KEY, record TEXT NOT NULL)` storing JSON download journals. Upgrade hook currently has no migration logic. |
| `getExternalFilesDir(null)/downloads/<UUID>/` | App-specific video/audio/caption/thumbnail/avatar assets; selected tracks plus unique attempt filenames. |
| SharedPreferences `download-export` | Pending export id/asset/destination marker; startup recovery reports Interrupted. |
| `cacheDir/export-<UUID>.mp4` | Temporary paired-media export; removed on success/error/cancel/recovery. |
| `cacheDir/rss/` | XML share files exposed through the RSS FileProvider. |
| Coil caches / process lookup state | Image loading and in-memory title/classification/controller state; not durable account navigation. |
| User-selected document URI | Exported file managed by its provider/user; independent of the original app download. |

## Stable local profiles

`DeviceProfile.of(ApiContext)` uses normalized server origin plus owner:
`guest`, `account:<64-lowercase-hex profileId>` or legacy `legacy:<username>`.
The JSON pair is SHA-256 hashed into a key prefix. Bearer tokens and request
generations do not define stable local ownership. Hashing namespaces keys; it is
not encryption of preference contents.

`DeviceProfiles.transfer` moves only the old owner's keys when stable identity is
confirmed or credentials are replaced, preserves existing destination values and
removes transferred source keys atomically with session changes. Known stable
identities cannot be rebound to a different profile from a response. A successful
preferences GET can confirm an older token through `X-Invidious-Account-Profile`.
Stale-context confirmation is rejected.

Guest and signed-in saves stay separate. Logging out/expiring authentication leaves
account-local saves available for that identity's later sign-in and exposes guest
saves instead. Switching servers loads the destination's guest profile. Deleting
an account purges only its current local profile. Downloads are intentionally
device-owned and are visible to guests and different account/instance selections.

## Legacy migration and local writes

Migration version 2 consolidates old scalar speed/height/region and standalone
DeArrow/SponsorBlock preferences into bounded guest defaults. Already-owner-scoped
positions/visibility stay with their legacy account; unowned background/PiP/chat/
sort values become guest data. It normalizes primitive types and discards malformed
positions, IDs, impossible delays and unsupported choices.

Unscoped old progress is assigned only if the stored session establishes ownership
or no session existed. A corrupt encrypted session leaves unknown old progress
untouched instead of exposing it to guests. Migration and profile transfers use
checked SharedPreferences commits. On failed commit the readable in-memory values
are restored and a storage error is surfaced; state flows do not pretend the save
succeeded. Guest normalization disables watch history/account-only filters and
default playlists, bounds captions and channel overrides and validates chat filters.

## Session and credential handling

Current Account JSON (token, username, expiry, server, optional profile ID) is
encrypted with AndroidKeyStore AES/GCM/NoPadding. The stored Base64 consists of
the generated IV followed by authenticated ciphertext; decryption uses a 12-byte
IV and 128-bit GCM tag. Undecryptable sessions are ignored; expired sessions do not
become live accounts. Session preferences remain within the app sandbox.

Passwords/captcha answers/new token values are transient form values. Private
DeArrow identity is imported to the server, which owns its encrypted identity
storage; Android persists readiness status only in process state. Ordinary local
preferences, SQLite JSON, media files and cached feeds are not encrypted by this
application. Do not describe the whole local library as encrypted because the
current bearer is encrypted.

Manifest backup is disabled (`allowBackup=false`, `fullBackupContent=false`), and
data-extraction rules exclude root/files/databases/preferences/external/device
stores for cloud backup and device transfer. Uninstall/clear app data removes
app-owned preferences/database/media; manually exported files are separate.

## Network and URL boundaries

Release instance selection permits only HTTPS origins without credentials/path/
query/fragment. Debug cleartext configuration is restricted to localhost,
127.0.0.1 and 10.0.2.2. API redirects are disabled, and mutations disable automatic
connection retry. Authenticated calls validate server match/expiry and captured
context. Public API calls and ExoPlayer/DownloadManager media requests do not
attach the Invidious bearer.

Avatar/GGPHT images normalize supported sources to the selected instance proxy;
the avatar client follows no redirects and sends no account credentials. Banner/
post image proxy rules preserve their selected sizes. Download URLs must exactly
match the originating scheme/host/port and contain no credentials. Download file
paths validate UUID directory and safe asset filename characters.

Link parsing accepts supported YouTube hosts, the configured origin and declared
Wingress hosts, validates IDs and timestamps, and distinguishes external links.
Foreign native clips are represented as origin-owned browser actions. Rich text
uses Compose's native representation; arbitrary HTML/JavaScript is not executed
in a WebView. The app has no WebView browsing session containing account cookies.
Private RSS links must be same-origin `/feed/private` with a token parameter.

## Exported Android components and permissions

| Component / permission | Purpose and boundary |
| --- | --- |
| MainActivity, exported/singleTask | Launcher, text/plain SEND and HTTPS VIEW links. Validates parsed content before native routing. |
| PlaybackService, exported/mediaPlayback | Media3 session for trusted system/same-package controls. Custom app commands additionally require package/context/token checks. |
| DownloadReceiver, exported | System DownloadManager broadcasts; treats them as hints and reads its own journal/system rows rather than trusting supplied IDs/paths. Notification click opens Downloads. |
| DownloadExportService, not exported | App-started foreground dataSync/mediaProcessing export. |
| FileProvider, not exported | `${applicationId}.rss` authority; cache RSS subtree only, grants temporary URI permissions. |
| INTERNET / ACCESS_NETWORK_STATE | API/media/network operation declarations. |
| FOREGROUND_SERVICE and mediaPlayback/dataSync/mediaProcessing types | Ongoing playback and exports. |
| POST_NOTIFICATIONS | Notification capability declared; no upload-alert feature follows from the permission. |
| WAKE_LOCK | Media playback wake ownership; visible PlayerView separately controls screen wake. |

There is no broad storage permission; app media uses app-specific storage and
user exports use the Storage Access Framework. The HTTPS intent query allows
browser resolution. Auto-verification declares invidious.wingress.net and
mobivious.wingress.net, not every YouTube/custom-instance domain the parser accepts.

## Cached results and privacy limits

ResponseCache hashes the full URL/session key as the filename; the response body
is ordinary sandboxed text. It keeps at most 50 newest files, rejects writes over
2,000,000 characters, and reads only snapshots younger than 24 hours. Temporary
write/rename avoids publishing a partly written snapshot. Public results use a
public key; authenticated feed keys include bearer identity. Sign-out/server switch
clears the cache. No offline mutation replay exists.

Server APIs necessarily receive searches, video/channel IDs, account actions and
enabled optional-tool requests. Proxy-stream preference controls media routing;
server settings/upstream behavior determine its remaining network path. Android
does not independently contact public DeArrow/SponsorBlock/AiSList APIs for these
features. Clipboard/share/file-picker actions disclose only what the user chooses.

Repository secrets are local `.env`, `android/signing.properties`, keystore files
and SDK settings; they are ignored by Git. The coverage map documents their
roles without reading or reproducing their values. Signing identity must be
preserved for compatible APK updates; see [Development](DEVELOPMENT.md).

## Appearance migration and pending preference intent

An unknown or absent `appearance.accentPreset` uses `blue`. No account setting
is written by device appearance migration. Legacy optical flags
remain stored but have no rendering consumers.

Unconfirmed preference requests live in the ViewModel, survive configuration
recreation and page navigation, and are isolated by account/instance generation.
They are not a process-death journal. Failed persistence retains confirmed durable
values and the requested edit with Retry/Revert. Sign-out/server changes warn
before discarding unconfirmed intent. Sensitive identity/account operations retain
explicit submission.

## Channel hiding rules

Guest `channel_spoiler_settings` live in the existing instance-scoped preference
profile. Signed-in rules are confirmed account preferences, not imported guest
settings. Unconfirmed edits and publication lookups retain their API context;
late responses from a previous account/server cannot change presentation.
Reveal state is ephemeral and scoped to displayed/playing occurrences. Raw titles,
descriptions, downloaded metadata and files remain intact: these are presentation
preferences, not encryption or metadata access restrictions.

## Search assistance

Each existing device profile owns `search.assistance` (remember/live-suggestion
booleans, both default true) and `search.recent` (up to 20 submitted text queries).
Recent terms are trimmed, deduplicated case-insensitively, and moved to the front
when reused. URL-like terms and recognized pasted content links are excluded.
Search history is local and independent of server watch history. Turning saving
off hides recents and pauses future writes without clearing them; confirmed Clear
all removes saved terms even while hidden. These new keys need no legacy migration
and participate in existing profile transfer and deletion.

Live suggestions send nonblank, non-URL drafts and the configured region to the
selected instance after 250ms without input. They carry no bearer token, are not
persistently cached and are canceled when superseded or the editor closes.
Draft/profile revisions fence responses; an optional lookup or local-history
write failure cannot prevent manual searching.
