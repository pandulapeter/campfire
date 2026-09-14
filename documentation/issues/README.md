# Pre-release review, 2026-09-14: issues and plans

One file per issue, numbered by priority within each group. Every file is a self-contained brief for an agent (or a
person): what is wrong, where, why it matters, exactly what to change, and how to verify it. Delete a file once its
change has landed.

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Where a plan changes documented behaviour, update the module's `CLAUDE.md` (and the root one where it is named).
- Run the unit tests after every change:

  ```
  ./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

  and a compile of every target that the change touches, at minimum `:app:desktop:run` for a manual check and
  `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  for the rest.
- Do not commit unless asked. Load the `commit-messages` skill before writing any commit message.
- Line numbers in these files are as of commit `cb82337f`; re-locate by the quoted code if they have drifted.

## Groups

| Range | Group |
|-------|-------|
| 01–10 | Fix before release: data loss, crashes, dropped input |
| 11–23 | Sync behaviour and the Dropbox provider |
| 24–30 | Dropbox provider, small |
| 31–38 | Local storage and file naming |
| 39–45 | Domain, repositories, data flow |
| 46–49 | ChordPro parser, transposer, serializer |
| 50–69 | ViewModel, navigation, Compose UI, platform shells |

## Decisions already taken (do not re-open)

- An emptied remote folder stops the run and asks (11), rather than deleting or refusing silently.
- `[Chorus]`-style bracketed words keep being transposed as chords; no plan file for that.
- Credentials move behind the Android Keystore and the iOS Keychain (20); desktop and web keep the plain file.
- The demo library's first-run condition is closed by writing the preferences on every first launch (42).
- Plan files live here, committed.

## Working in parallel

How to actually run it — worktrees, subagents, commits and merges — is in [EXECUTION.md](EXECUTION.md).

The plans are independent except where they edit the same file, so run one agent per lane (a git worktree each),
serial **inside** a lane in the order given, and merge lanes as they finish. Run the unit-test command after every
merge. Merge G last: it carries most of the `CampfireViewModel.kt` edits.

| Lane | Scope | Order | Shared files that make it one lane |
|------|-------|-------|------------------------------------|
| A | Sync engine and repository | 01 → 12 → 14 → 15 → 30 → 11 → 13 → 22 → 23 → 07 | `SyncEngine.kt`, `SyncRepositoryImpl.kt`, `SyncState.kt`, `SyncSettings.kt`; 01 builds the fake-provider test harness 11 and 12 reuse |
| B | Dropbox provider and credentials | 19 → 16 → 29 → 18 → 17 → 24 → 26 → 25 → 28 → 27 → 20 | `DropboxSyncProvider.kt`, `SyncCredentialsStore.kt`, `SyncStateLocalSourceImpl.kt`; 18 introduces the forced refresh 17 uses |
| C | ChordPro, then the import comparison | 05 → 46 → 47 → 48 → 49 → 06 | the `:chordpro` editors and tests; 06 depends on the line helper from 05 |
| D | Local storage and naming | 08 → 36 → 34 → 33 → 31 → 32 → 35 → 38 → 37 | the zip package, the four `FileStorage` actuals, `LibraryFiles.kt`, `FileNames.kt` |
| E | Domain and repositories | 39 → 40 → 41 → 43 → 44 → 50 | the use cases, `BaseLocalDataRepository.kt`, `SetlistRepositoryImpl.kt` |
| F | Compose screens and web shell | 03 → 04 → 55 → 52 → 59 → 56 → 57 → 60 → 63 → 58 → 62 → 64 → 65 → 66 → 61 → 67 → 69 | `Dialogs.kt`, `SongDetailsScreen.kt`, `SongLyrics.kt`, `SetlistsScreen.kt` |
| G | ViewModel, navigation, Android shell | 02 → 51 → 45 → 42 → 53 → 54 → 09 → 10 → 68 | `CampfireViewModel.kt`, `CampfireDestination.kt`; 51 uses 02's guard, 68 uses 10's serializable destinations |

Cross-lane touch points to expect at merge time (small, distinct hunks):

- `CampfireViewModel.kt`: E (50, setlist functions), F (61, one property), G (everything else).
- `PrepareImportUseCaseImpl.kt`: C (06, the comparison) and D (32, the decoder).
- `FileStorage.ios.kt`: A (07, `readData`) and D (32, `readText`).
- `SyncRepositoryImpl.toFailureReason`: A (07) only, but B's exception types feed it.

The 01–10 set is deliberately spread across lanes, so starting all seven at once gets the release blockers done
first in each area rather than serially.
