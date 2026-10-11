# Troubleshooting

[Documentation index](README.md) · [API compatibility](API.md#compatibility-and-server-rollout)

| Symptom | Meaning and recovery |
| --- | --- |
| Cannot connect to an instance | Check HTTPS origin, network and server availability. Paths, credentials, query and fragment are invalid instance addresses. HTTP is limited to debug loopback fixtures. |
| “Offline · showing saved results” | An eligible feed/search request used a recent snapshot. Refresh when online. It does not mean a streamed video has been saved offline. |
| Sign-in/registration/account management needs an update | Instance lacks native mobile/account endpoints. Deploy matching server source; installing an APK alone cannot add them. |
| Feature says invalid scope / renew sign-in | Old token lacks an added permission. Update server first, then sign out/in to issue a new native token. |
| Chat sync fails while playback works | Dedicated chat auth/settings errors intentionally preserve playback ownership. Renew token or retry delay/filter save. |
| Wrong current password | Account operation failed; this alone does not revoke the active bearer. Correct the field and retry. |
| Too many attempts | 429; follow the displayed Retry-After interval. Server proxy/IP throttling configuration may matter. |
| Feed/list is empty after filtering | Hidden rows can still have more raw pages. Check members, block/AI and account feed settings; load more. |
| Directory sorts only A–Z | Server statistics or history-read permission unavailable. The saved preferred sort remains; update server/token as indicated. |
| History search disabled | Legacy flat history response. Basic viewing remains; matching organized-history server support enables scoped search/date groups. |
| Approximate counts / missing Shorts date | Optional exact-precision or cached metadata was omitted. Do not infer missing values from a different field. |
| Upcoming video has no playable stream | Wait until available and Retry. Upcoming streams are not converted into regular playback/downloads. |
| Decoder/stream playback error | Retry refreshes stream metadata; Refresh buffer re-prepares current media. Check device-supported tracks, instance/Companion/upstream health. |
| Preferred codec/quality is unavailable | Selection uses supported manifest representations and falls back. Defaults cannot add an unsupported decoder or missing stream. |
| Leaving the app pauses | Check profile-local Background playback and PiP settings/device support. Audio only is a separate mode. |
| Queue missing next page | Use source Retry/Load more. Repeated playlist/mix cursors stop intentionally; existing video playback can remain available. |
| Clips API error | Deploy Clips/token update and migration 19 as required. Renew old scopes. Native clips remain with their original origin. |
| Download catalog says restricted/unavailable | Instance enforces restrictions and finite-track availability. Live/upcoming sources cannot download. |
| Download Waiting/Failed | Read system reason/track detail, check connectivity/storage and Retry. Completed assets are kept. Retry retains original selected URLs; unavailable exact selections are not substituted. |
| Offline playback fails | Missing/incomplete local files or decoder support. Reconcile/Retry the download or create a new one if its old source selection expired. |
| Paired Save to files needs conversion | MP4 muxing cannot keep the source tracks unchanged. Explicit Convert and export permits affected H.264/AAC conversion; Decline leaves source downloads intact. |
| Export fails resolution/frame-rate check | Device cannot produce the requested unchanged geometry. Save individual tracks or use a capable device; the app does not silently downscale. |
| Export Interrupted | Process stopped during pending export. Partial output/temp file is cleaned; choose Save to files again. |
| Local save fails | Checked preferences/database/storage operation failed. Retry after correcting device storage availability; do not assume UI changes persisted. |
| Ambient light absent | Check toggle, Reduce transparency, system animation scale, power-save mode, hardware acceleration and expanded video visibility. Paused/no-ready-frame/mini/PiP/audio-only states may intentionally have no sampling. |
| SponsorBlock/DeArrow/AI data absent | Optional server/community data may be missing or disabled. Check enabled settings/list status/API support. Ordinary playback remains available. |
| Shared link does not open automatically | Manifest app-link registration covers declared Wingress hosts only. Use Android Share or paste supported URLs into Search; arbitrary instance/YouTube parsing does not mean Android auto-verifies all those hosts. |

For development failures, use [Development](DEVELOPMENT.md) and
[Testing](TESTING.md). A device test compiled successfully is not a passed runtime
test. Dated logs/screenshots in [VERIFICATION.md](../VERIFICATION.md) describe their
specific source/date/device, not a blanket acceptance guarantee.
