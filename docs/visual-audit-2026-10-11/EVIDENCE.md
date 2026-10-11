# Evidence guide

Start with [REPORT.md](REPORT.md) for findings, reproduction steps, source locations, coverage and test outcomes.

- `screenshots/`: 440 full-resolution screenshots. `evidence-manifest.txt` is the complete path inventory.
- `details/`: close-ups of findings. Full originals are linked from the report.
- `contact-sheets/`: overview images used during inspection. `category-*`, `final-panels-*` and `conditional-final-*` supplement the groups named after their capture directories.
- `icons/`: diagnostic circle/square/rounded-square renders of the packaged adaptive icon layers. The actual launcher capture is in `screenshots/003-launcher-circle.png`.
- `motion/watch-realtime.mp4`: ordinary device input, approximately 30 seconds, captured at 672 × 1496. Use it to assess watch/mini transitions. Time-labeled frame sequences and the GIF are derived from this recording.
- `logs/`: complete instrumentation output, including initial capture errors and reruns. See the report for outcomes and the unresolved AI interaction timeout.

Fixture color bars, synthetic channel/video text, the registration clock and local account data are intentional test content. They are not visual defects.

`capture-harness.kt.txt` preserves the temporary instrumentation capture source. It uses this repository's existing Android test utilities and the disposable API in `scripts/fixture-server.py` on port 18080. Conditional registration capture also uses a second copy on port 18081 with `captcha-fixture.patch` applied and `fixtures/captcha.png` in its media directory. The patch only adds a CAPTCHA payload to the existing registration response. The services are stopped after inspection; they must be started again and ADB reverse mappings restored before rerunning captures.

`collect-evidence.py` and `group-evidence.py` preserve the collection/contact-sheet helpers. They run from the repository root and require Python/Pillow; collection additionally requires ADB and the on-device start marker. They are audit utilities, not application source changes.

Compose instrumentation can freeze or skip animation frames. The report's animation finding therefore uses the separate normal-input recording. The invalid large-text CAPTCHA focus capture, which accidentally tapped navigation, was excluded; the focused-field defect was independently reproduced using ordinary input.
