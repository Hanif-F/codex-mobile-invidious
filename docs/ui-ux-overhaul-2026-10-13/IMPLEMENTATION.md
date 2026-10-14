# Implementation packages and migration

[Overhaul entry point](README.md) · [Screens](SCREENS_AND_FLOWS.md) ·
[Verification](VERIFICATION.md)

**Future application work.** None of the code, variants, helpers or benchmark
commands proposed here exist merely because this documentation is complete.
Start from current source, preserve unrelated changes, and implement packages in
order. An agent owns a complete screen family, including its secondary overlays.

## Integration order and acceptance

| Package | Work and source ownership | Required acceptance before integration |
| --- | --- | --- |
| P0 Baseline and measurement harness | Record actual starting commit; inventory current journeys/tests. Add isolated benchmark variant/module/runner described below; capture current-glass and comparable pre-glass journeys before replacing rendering. | Harness runs on a physical device, produces trace/JSON with provenance, and leaves release user data untouched. Missing devices/old-build incompatibility are explicit measurement gaps, not passing results. |
| P1 Shared foundations | Introduce `ui/AppTheme.kt`, `ui/AppComponents.kt`, shared width/inset policy and device accent preference. Replace `LiquidDesign` and shared control internals with opaque Material controls; implement exact palettes. | Both appearances × six accents pass enabled-color contrast checks; standard components, ≥48dp targets and software drawing work without glass recording/readback. Theme changes preserve open menus/focused fields. |
| P2 Navigation and search | Introduce `ui/AppScaffold.kt`; migrate `MobiviousApp`, `LiquidNavigation`, `SearchUi`, tab/return state in `AppViewModel` and navigation test helpers. Implement N1–N3 with four destinations/top search/solid mini slot. | All root stacks/positions, reselect, launch preferences, search/link/scoped return, IME and rail resizing work; playback survives. Search is not a root tab. |
| P3 Browsing and library | Migrate Discovery, Subscriptions, channel/community/rich information, You, playlists/history/downloads/clips/RSS and shared metadata/actions. Replace Browse/Library glass facades. Implement B1–B8/L1–L9. | Each applicable shared state and action works; raw pagination/duplicates/filtering remain correct; guests reach downloads offline; all child editors/menus are opaque. |
| P4 Settings, accounts and editors | Add `ui/PreferenceSaveController.kt`; integrate with `AppViewModel` write ownership and `SessionStore`. Migrate Settings, Account, SponsorBlock, DeArrow, AI filter and editor components. Implement S1–S10/A1–A3 and save behavior used by W6. | No page-level preference Save/Cancel remains; concurrency/failure/context tests below pass. Entity/security operations stay explicit; keyboard/autofill/large-text/validation remain usable. |
| P5 Watch and player | Migrate PlayerUi/PlayerControls/WatchActions/WatchGlass/queue/comments/chapters/chat and MiniPlayer; preserve PlayerPresentationHost, PlaybackVideoSurface and service ownership. Decouple WatchAmbient/AmbientPalette from glass. Implement W1–W6. | One decoder and occurrence across mini/watch/fullscreen/PiP; all tools, gestures, captions, tracks, offline media and explicit editors work. Ambient off has no capture loop; on has bounded independent sampling. |
| P6 Removal and integrated acceptance | Delete unused glass/recording/capture abstractions, Backdrop dependency, obsolete tests/preferences UI and stale names. Update current guides, source/test catalogs and new implementation evidence. | No runtime glass imports, optical shaders, backdrop-color readbacks or originating-window captures remain. Build/lint/unit/device and general-phone performance gates are recorded with honest gaps. |

P0 establishes measurement tooling; P1→P6 is the UI integration order. Continue
unaffected code work if a physical device is unavailable, but physical performance
acceptance remains pending. Do not gate every reversible edit on user permission.
Do not install on a user's release package or publish a release as an incidental
verification step.

Temporary adapters can keep intermediate packages compiling. They must render
solid controls with no glass source/recording dependency, and P6 removes them.
Do not perform an unrelated architectural rewrite of the service, API or databases.

## Internal interfaces and persistence

These are internal application contracts, not new server endpoints:

| Interface | Required contract |
| --- | --- |
| `AccentPreset` | Stable IDs `blue/teal/green/violet/amber/rose`; unknown/absent → Blue; exposes fixed light/dark roles from DESIGN_SYSTEM. |
| `SessionStore.accentPreset` | Observable device-wide value plus persistence setter, consistent with existing immediate device choices; store as string key `appearance.accentPreset`. Publish only after successful persistence. |
| Shell destinations | Popular, Trending, Subscriptions, You; preserve existing internal tab strings so saved homepage values and domain rules remain compatible. Search stays a transient route. |
| Preference save controller | Per-context field/group state: confirmed value, requested value, monotonically increasing revision, pending/error/invalid status and Retry. Owned by ViewModel, independent of a screen's composition lifetime. |
| Save sinks | Device/profile persistence, existing preference PATCH, existing chat PATCH and per-video delay API; reuse validators, guest normalization, deltas and context checks. |

No changes to server wire names, token permissions, database schema or existing
preference storage scopes. Keep shared `dark_mode/ui_density/thin_mode`, profile
choices and device choices in their current owners. Accent is never included in
account JSON or copied per account/instance.

## Automatic preference saving

### Input and feedback

1. A toggle or choice requests an immediate write. Sliders request one on release.
   Text/numeric preferences request a write after 500ms quiet, or immediately on
   IME Done/focus loss when valid. Retain invalid text with a field error; never
   transmit a partial number, URL or invalid regex.
2. The requesting control shows the requested value and pending state; other
   consumers keep the confirmed value until success. This prevents an unsuccessful
   save from changing playback/filtering or deleting resume positions.
3. Group atomic values together: caption priorities, chat filter lists and
   channel override tuples. Derive their validity with existing validators.
   Unchanged unsupported imported fields stay untouched.
4. Local writes commit immediately. Account writes happen asynchronously and do
   not block the whole screen or navigation. Show unobtrusive Saving while
   pending and inline failure/Retry afterward; no per-toggle success snackbar.
5. On failure preserve the requested value as visibly unsaved, alongside the
   last confirmed summary where needed. Retry sends that intent; a new user edit
   replaces the failed intent. Do not silently persist offline account changes
   as guest settings or claim they will sync after process death.

### Ordering and isolation

- All shared preference writers, including region selection, player speed,
  SponsorBlock and chat preferences/delay, participate in the same ordered
  ViewModel save pipeline. Use the existing `preferenceWrites` mutex at the
  actual sink; do not reacquire it recursively in an outer controller.
- Capture an immutable field/group intent with its context, baseline and revision
  at the time of the edit. Send only that group's delta. Never build a patch from
  an entire stale settings-page snapshot at dispatch time.
- Serialize requests per active context, coalescing unsent edits to the same key
  to their newest value. Keep independent-field edits. One in-flight response
  updates the confirmed account snapshot; overlay newer pending field intents
  when displaying controls so an older response cannot replace them.
- Protect completion with context generation and revision. A late result from
  another account/instance/video may not mutate the new screen or its error state.
  Context changes cancel unsent work and discard sensitive requested values.
- Existing browse invalidation and playback-default side effects run once per
  successful confirmed change. Suppress old `Settings saved` messages for
  automatic saves; explicit entity-operation feedback remains.

### Navigation, lifecycle and exceptional actions

On leaving a preference page, flush valid debounce-pending values into the
ViewModel queue; persistence outlives composition while that context is active.
Invalid fields keep their last confirmed setting and are not sent; announce that
the invalid edit was not saved. Expose failed writes and invalid unsaved edits
through a Settings root status row linking to the affected page. On returning,
restore the requested value/error until retried or replaced. A failed-write row also offers Revert to
saved, which clears that intent without issuing a PATCH.

An activity recreation keeps pending/failed state in the ViewModel. Persist only
confirmed data; after process death, restore confirmed preferences and make no
promise that an unsaved intent survived. On backgrounding, flush valid text edits
while the process remains alive; no new background synchronization service.

Changing account/server never carries old intents to the new context. If pending
or failed account writes exist, the explicit switch/sign-out flow explains that
unconfirmed changes will be discarded; offer Stay or Continue. Local accent and
ambient choices remain device-wide.

Authentication, credential changes, server Connect, token creation/revocation,
account deletion, playlist/clip creation/editing, DeArrow identity/contributions,
download track submission/export/conversion, and chat geometry Done/Cancel remain
explicit operations. The preference-save controller must not autosubmit them.

## Appearance and navigation migration

| Existing/new state | Required handling |
| --- | --- |
| Missing/invalid `appearance.accentPreset` | Resolve Blue; explicit selection writes its stable ID immediately. No account/profile migration. |
| Absent `appearance.ambientMode` | Change read default to false; do not seed true during startup. |
| Explicit ambient true/false | Preserve exactly across upgrade, recreation and account/server changes. All existing lifecycle/power/accessibility gates still apply. |
| `appearance.coloredBackgrounds`, `appearance.adaptiveControlColors` | Remove their UI/runtime consumers. Leave dormant stored keys untouched for this release; never reinterpret them as accent/ambient choices. |
| `accessibility.reduceTransparency` | Retain key/value and device scope; it only suppresses optional ambient lighting. Revise explanatory copy; remove glass fallback dependencies. |
| Color mode/density/thumbnail preferences | Preserve wire values, scope and defaults. No preference reset on upgrade. |
| Popular/Trending snapshots | Promote both to direct shell destinations, preserving their existing independent data/positions. Remove Discover grouping and `lastDiscover` shell state. |
| Existing default-home / `feed_menu` | Keep current validation and guest fallback; fixed native tab order does not mirror website `feed_menu`. |

Migration requires no sign-out, clearing app data, destructive profile conversion
or server rollout. Device-write failures follow existing persistence rollback
and error semantics.

## Rendering and cleanup boundaries

Use a shared `MaterialTheme` plus native opaque Surface, Scaffold, NavigationBar,
NavigationRail, app bars, fields, sheets, dialogs and menus. Preserve local
semantics/test tags and native PlayerView/SurfaceView synchronization.

Remove `GlassMaterial/GlassScene/GlassContent/GlassWindowBackdrop/GlassDialog`,
old Liquid/navigation optical helpers and facades once all callers migrate.
Remove `io.github.kyant0:backdrop` and obsolete imports/resources/attribution
references when no longer used. Ordinary menus/dialogs must never PixelCopy their
origin window. UI alpha/ripple and native video/chat/caption compositing remain.

Keep `WatchAmbient/AmbientPalette` only for opt-in video lighting, with no glass
scenes, contrast statistics or foreground sampling. Preserve one in-flight copy,
250ms rate limit, backoff/disposal rules and lifecycle gating. SponsorBlock's
service evaluation and active chat/playback timers are functional work, not
decorative loops to delete.

For scrolling, use lazy containers, stable occurrence keys/content types,
size-bounded Coil requests and remembered immutable derived values. Do not
reparse rich text, sort full collections, allocate full-frame bitmaps or observe
playback-position state at the entire feed root on every frame. Profile before
changing functional data logic; no blanket performance claims from code deletion.

## Planned measurement tooling

P0 adds a separate `android/macrobenchmark` module using `com.android.test`
and `androidx.benchmark:benchmark-macro-junit4:1.5.0`, the
[stable release verified for this specification](https://developer.android.com/jetpack/androidx/releases/benchmark).
Keep existing app/tool versions; pin any additional test dependencies explicitly.
Use namespace `net.wingress.mobivious.benchmarks`.

Add an app `benchmark` build type initialized from release, non-debuggable,
profileable and locally debug-signed, with application ID suffix `.benchmark`.
Match release optimization settings on every comparison; do not turn on R8 only
for the redesigned sample. Production remains `net.wingress.mobivious`.

The fixture uses host port 18080 and ADB reverse. A benchmark-only network config
permits loopback HTTP, with an explicit build flag used at origin-validation
call sites; release stays HTTPS-only and the flag false. Do not use
`BuildConfig.DEBUG=true` to fake a release-like benchmark. Setup uses ordinary
UI actions to select the fixture origin and fixture account outside timed blocks;
no exported production seeding endpoint or real credentials.

Add `scripts/test-ui-performance.sh` to own fixture preparation/readiness,
exclusive port/reverse mapping, teardown and evidence copying as the existing
device runner does. It targets only the benchmark package and never clears the
release app. Support `--list` (no device mutation), `--label <label>` and
`--journey <id|all>`; use `ANDROID_SERIAL` for explicit device selection and
reject ambiguity. Test class `UiScrollBenchmark` implements the journeys in
VERIFICATION. Report outputs to
`.tools/ui-ux-overhaul/performance/<label>/<device>/<run>/`.

For historical comparison use isolated temporary checkouts and the same harness,
variant settings, fixtures and common journeys. Port only the minimal measurement
adapter; record its diff/hash. Never replace current source with old source to
obtain a baseline, and never use performance-only feature removal in one sample.

## Agent handoff and rollout

Each package reports source commit, changes, accepted screen IDs, exact checks,
results/ignored cases and evidence paths. Review rendered screens and normal-speed
interaction recordings; static screenshots cannot certify gestures or smoothness.
Integrate one shared design, with no shipping mixed-style milestone.

After P6, update current UI/settings/features/playback/storage/test descriptions
and reviewed fingerprints to match actual implementation. Add new source/test
coverage entries before refreshing catalogs. Preserve dated evidence and old
counts. Release/versioning/distribution follows the existing development guide
only when separately requested; documentation completion does not authorize it.
