# Testing policy

Use [the testing guide](docs/TESTING.md#choosing-checks) for commands and acceptance
boundaries. Apply this policy to the Android repository; run server checks when
the task changes server code or its contracts.

- During development, run tests for the changed behavior and its immediate
  dependencies. Use Gradle `--tests` for JVM cases. For device cases, use
  `scripts/test-android.sh --device-only` with an explicit class/method selector.
- After the final production-code edits, run the full JVM suite and debug lint
  once. Passing checks remain valid until a subsequent change affects their inputs;
  rerun only the affected checks after fixes. Retain Gradle's incremental outputs.
- Run focused device cases for UI, playback, lifecycle, storage and Android
  integration changes. Widen coverage across affected consumers for shared
  navigation, preferences, authentication, playback-service, manifest or dependency
  changes. Include relevant failure/recovery cases and existing regression tests.
- Documentation-only changes need documentation checks. Script/fixture changes
  need relevant workflow/contract checks and affected device consumers when their
  contract changes. Test-only changes need the changed tests. Add Android builds,
  lint or unrelated suites only when the change affects what they verify.
- Build APKs when packaging or installation needs verification; connected Gradle
  tasks already build their required APKs. Keep release checks intact. Run
  performance benchmarks for performance work or explicitly requested measurements.
- Add tests for meaningful behavior and regressions at the lowest suitable layer;
  extend existing coverage where practical. Trivial edits and assertions that
  merely mirror implementation do not require new tests. Preserve existing tests
  unless removal has a demonstrated obsolete or duplicate purpose.
- Report exact checks, results and material gaps. Keep known failures and ignored
  cases explicit; focused success does not establish full-device-suite acceptance.
  If a needed device is unavailable, report its checks as pending.
