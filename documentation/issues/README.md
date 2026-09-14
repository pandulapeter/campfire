# Pre-release review, 2026-09-14: issues still open

The rest of the review has landed. One file per issue, each a self-contained brief: what is wrong, where, why it
matters, exactly what to change, and how to verify it. Delete a file once its change has landed, and this folder with
the last one.

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.**
- New UI strings go into **both** `values/strings.xml` and `values-hu/strings.xml`, read with
  `com.pandulapeter.campfire.presentation.localization.stringResource`.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Where a plan changes documented behaviour, update the module's `CLAUDE.md` (and the root one where it is named).
- Run the unit tests after every change:

  ```
  ./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

  and `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
- Load the `commit-messages` skill before writing any commit message.
- Line numbers in these files are as of commit `cb82337f`; the code has moved a lot since, so re-locate by the quoted
  code.

## Open

| Issue | Group | Why it is still open |
|-------|-------|----------------------|
| [03](03-duplicate-setlist-entries-crash.md) | Fix before release: crash | Step 2 keys the setlist rows by position, so after a drag or a removal the rows would swap keys and slide with the wrong content. The plan needs a different key before it can be carried out; step 1 alone (deduplicating on read) is not affected. |
| [21](21-desktop-loopback-connection-timeout.md) | Sync, Dropbox provider | It was left out of the lane table, so no lane picked it up. |
