# Pre-release review, second pass, 2026-09-21: issues and plans

One file per issue, numbered by priority within each group. Every file is a self-contained brief for an agent (or a
person): what the user sees, where the cause is, exactly what to change, the tests to add, how to verify it, which
docs become untrue, which files it touches and which plans must land first. Delete a file once its change has landed.

This is the second review. The first one (2026-09-14, 69 issues) landed between `d40f9754` and `7e7d893c`; nothing in
here repeats it, but five plans finish a fix it left incomplete: 02 (`bb45f5a8`), 09 (`ab5c54ae`), 10 (`0aaa713e`),
48 (`f19e27eb`) and 55 (`7e7d893c`). Every finding was reported by an area reviewer, re-verified in the code by the
plan's writer, and — for most of the 01–15 group — checked by hand a third time. Line numbers are as of commit
`29820b93`; re-locate by the quoted code where they have drifted.

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`
  (or, once plan 63 has landed, with its `textResource` wherever user text goes into the sentence).
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Where a plan changes documented behaviour, update the `CLAUDE.md` files and `documentation/*.md` it names in its
  **Docs** section.
- Run the unit tests after every change:

  ```
  ./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

  Plan 02 gives `:domain:implementation` its first `commonTest` source set; once it has landed, add
  `:domain:implementation:desktopTest` to that command here, in the root `CLAUDE.md` and in `EXECUTION.md`.
  Compile every target the change touches, at minimum `:app:desktop:run` for a manual check and
  `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  for the rest.
- Do not commit unless asked. Load the `commit-messages` skill before writing any commit message.
- A plan that turns out to be wrong or impossible is **not improvised around**: leave its file in place and say why.

## Decisions already taken (do not re-open)

From the first review: an emptied remote folder stops the run and asks; `[Chorus]`-style bracketed words keep being
transposed; credentials live behind the Android Keystore and the iOS Keychain, and in a plain file on desktop and the
web; the demo library is planted only on a first run; sync never merges, a conflict lands as a ` (2)` copy, an edit
beats a deletion, a rename is a deletion and a new file; the UI is untested.

Taken by the user on 2026-09-21 for this review:

| Plan | Question | Decision |
|------|----------|----------|
| 25 | Android backup of the library | `library/` and `preferences.json` travel in **both** cloud backup and device transfer; `sync-credentials.bin` and `sync-index.json` stay excluded |
| 26 | Titles in non-Latin scripts all filed as `untitled` | **Keep the letters**: any Unicode letter or digit survives, lowercased; Latin text normalizes exactly as today; the cap counts UTF-8 bytes |
| 38 | `B` in a file written in German/Hungarian notation | **Detect per song**: an `H` root anywhere makes the song German-notated (`B` = B-flat); no marker directive; the editor writes back in the file's notation |
| 49 | Two copies of the app on one library | Desktop: **lock file, hand the file arguments to the running instance, exit**. Web: **Web Lock**, a second tab gets an "already open" page with Retry |
| 12 | Safari before 26 has no `createWritable()` | **Web Worker fallback** with `createSyncAccessHandle()`; and on every browser a failed write no longer leaves an empty file |
| 52 | Text shared to Campfire on Android | **Imported as one song** through the ordinary import, `EXTRA_SUBJECT` standing in as the file name |

## What the plan writers decided that is worth a second look

These are inside the plans, each with its reasoning. None needs an answer before execution, but they change
behaviour a user can see, so they are listed rather than buried:

- **41** changes how accidentals are spelled for songs that *do* declare a key, too: one answer per key (F♯ major and
  G♯ minor in sharps, C major's accidentals in flats), which is what makes +n then −n the identity.
- **52** does not inject a `{title}` into shared text. A pasted chord sheet without one is listed under its
  normalized file name (`my_song`), exactly like any other title-less import. Text holding nothing but links is
  reported as skipped.
- **46** puts a vanished file back under its **own** name rather than saving the draft as a new song: that name is
  what the setlists and the saved transposition point at.
- **47** makes a required update **wait** while the editor holds unsaved text, rather than asking about it.
- **16** keeps a run in which some files failed a `Success` (files did move), names the first three failures in
  Settings, and does **not** move "last synced". A full Dropbox ends the run with its own message.
- **10** writes the conflict copy locally first and takes it back only when the service said no; uploading the copy
  first was rejected (it needs a name free on both sides and a remote clean-up that can itself fail).
- **01** deletes an already-downloaded foreign file locally only when it is provably a copy (index hash and remote
  revision still match); anything else is left where it is. Worth a line in the release notes: Dropbox keeps deleted
  files for 30 days, for anybody the current build already bit.
- **25** needs two things done **outside this repository**: the privacy policy and the Play *Data safety* form, since
  Android's Auto Backup uploads the library to the user's own Google backup. Above 25 MB Auto Backup uploads
  nothing at all.
- **62C** (`isScrollInProgress` read per row) is confirmed and deliberately **not** fixed: every alternative shifts a
  row for a frame. The plan only documents it.
- **40** found a crash the review had missed: the existing `chordNameRegex` throws `StackOverflowError` on the JVM for
  a crafted chord name, which is one more reason it is replaced by a hand-written walk.
- **63** and **02** each add a sentence to `.claude/skills/code-style/SKILL.md`. That is the project's own skill
  file; review those two hunks like any other.
- **42** carries a `**Status:**` line: the missing-glyph symptom on the web could not be reproduced without a browser.
  The second half of the plan (a song flipping between `B♭` and `Bb` when transposed) is platform-independent and
  stands on its own.

## Groups

| Range | Group |
|-------|-------|
| 01–15 | Fix before release: data loss and crashes |
| 16–24 | Sync behaviour and the Dropbox provider |
| 25–31 | Local storage and file naming |
| 32–36 | Domain, repositories, data flow |
| 37–42 | ChordPro parser, transposer, notation |
| 43–53 | ViewModel, navigation, platform shells |
| 54–63 | Compose UI: two more crashes, then performance on low-end devices |

## Index

| # | Issue | Severity | Lane |
|---|-------|----------|------|
| 01 | Sync deletes the user's own non-song files from their Dropbox folder, and downloads files of any size | data loss | A |
| 02 | Two imported songs wanting one file name raise a bogus question, and "Replace" or "Skip" loses one | data loss | C |
| 03 | Sorting the songs by title crashes the start screen on every launch (two `Symbols` headers) | crash | D |
| 04 | Android: a file opened with Campfire is imported again on every rotation | wrong behaviour | E |
| 05 | "Save" in the unsaved-changes dialog closes the editor before the write has succeeded | data loss | E |
| 06 | With the device's storage full, a sync run crashes the app — on every launch | crash | A |
| 07 | Stopping a sync run is reported as a network failure, and the run's clean-up is skipped | wrong behaviour | A |
| 08 | After a sync run that fails part-way, the lists and the open songs show the library as it was before | data loss path | A |
| 09 | A second conflict on the same song overwrites the conflict copy | data loss | A |
| 10 | Resolving a sync conflict can lose the other device's version when the copy cannot be written | data loss | A |
| 11 | Android: connecting Dropbox silently fails when the app was killed while the user was in the browser | wrong behaviour | A |
| 12 | The web app cannot save on Safari before 26, and a failed write leaves an empty file on every browser | data loss | C |
| 13 | Web: reloading or closing the tab discards unsaved editor text without asking | data loss | F |
| 14 | Android: pressing Home in the editor can kill the app and lose the unsaved text | crash | E |
| 15 | A large file picked, opened, shared or dropped kills the app; one bad entry fails a whole archive | crash | Z |
| 16 | A sync run in which files failed says "Last synced successfully" | wrong behaviour | A |
| 17 | Reopening the app on Android while a sync is going makes it look interrupted and stops its notification | wrong behaviour | A |
| 18 | Leaving at once after opening can latch sync "Disconnected"; reconnecting overwrites the stored tokens | wrong behaviour | A |
| 19 | On a slow connection Settings offers "Connect to Dropbox" to a connected user | minor | A |
| 20 | Deleted files come back after the account's name is first read or its e-mail changes | minor | A |
| 21 | Web: Back from the consent page leaves Settings on "Connecting…", and Cancel does nothing | minor | A |
| 22 | Connecting Dropbox on a device that cannot write its credentials crashes the app | crash | A |
| 23 | A first sync of a large library rescans the whole library back to back | performance | A |
| 24 | Five small sync fixes | minor | A |
| 25 | Android: a new or restored phone brings Campfire back with an empty library | data loss | C |
| 26 | Every title not written in Latin letters is filed as `untitled`; the N-th file of a name costs N calls | wrong behaviour | C |
| 27 | Hidden files in the library folder (`._song.cho`) are listed as songs and uploaded by sync | wrong behaviour | Z |
| 28 | A UTF-16 song file shows as garbage, and the first change writes the garbage back | wrong behaviour | C |
| 29 | Long names cannot be saved on desktop and Android, crashed writes leave files, Windows cannot store "Con" | wrong behaviour | C |
| 30 | One bad field in `preferences.json` resets everything; quick changes can be saved in the wrong order | data loss | D |
| 31 | Exported archives are dated 1979, and more than 65,534 entries would be written truncated | minor | C |
| 32 | Leaving during the first library scan leaves half the library in place as if it were all of it | wrong behaviour | D |
| 33 | Saving a setlist's title or description writes back the songs the dialog was opened with | data loss | D |
| 34 | Importing a large songbook freezes the window while the import is planned | performance | C |
| 35 | Setlists, songs and tags that tie in the sort order trade places whenever one is changed | minor | D |
| 36 | A "Create" activated twice lists one file twice and crashes the list | crash | D |
| 37 | A comment or a page break inside a tab or a grid turns the rest of it into lyrics | wrong behaviour | B |
| 38 | A song written in German notation has its `B` chords read as B natural | wrong behaviour | B |
| 39 | A crafted or corrupt line hangs the library scan, the import and the editor | performance | B |
| 40 | `B7(b9)`, `B6/9`, `Bm(maj7)`… stay `B` in German notation; a crafted chord name overflows the stack | wrong behaviour / crash | B |
| 41 | Transposing a key-less song up and back down respells it | minor | B |
| 42 | `♯` and `♭` from a song file reach the screen as written | minor | B |
| 43 | Web: dropping a folder kills drag and drop, and can freeze the app | crash | F |
| 44 | macOS: "Open with Campfire" starts the app and imports nothing | wrong behaviour | F |
| 45 | Desktop: tapping a link closes the app where AWT's `Desktop` is not supported | crash | E |
| 46 | An editor whose file disappears turns into an endless spinner and drops the draft | data loss | E |
| 47 | Android: a required update covers an editor with unsaved text | data loss | E |
| 48 | Android: a picker result that comes back to a restarted process is dropped; an export leaves an empty file | wrong behaviour | E |
| 49 | Two copies of Campfire can run on one library and quietly undo each other | wrong behaviour | F |
| 50 | Rotating during a Play update download brings the dialog back; a late Play answer can crash | minor | E |
| 51 | iOS: opened files pile up in `Inbox`, an unreadable one is ignored silently, the read blocks the UI | minor | F |
| 52 | Android: Campfire is in the share sheet for every piece of text, and does nothing with it | minor | E |
| 53 | Web: an unsupported browser is told to check its connection; one failed icon holds the loading screen forever | minor | F |
| 54 | A song with one very tall section crashes the song screen every time it is opened | crash | G |
| 55 | Closing a sheet as an import's question appears drops it, and no import works until restart | wrong behaviour | E |
| 56 | On a phone every line of a song is laid out twice | performance | G |
| 57 | Every chorded line measures the same strings again with a text measurer of its own | performance | G |
| 58 | Every song row starts a flow collection, a lifecycle observer and an animation it never uses | performance | G |
| 59 | The editor tokenizes the whole song again every time the caret moves | performance | E |
| 60 | Every frame of the keyboard animation recomposes the whole app | performance | G |
| 61 | Resizing with "Read across columns" stalls on songs of many short sections | minor | G |
| 62 | Small per-frame work in the fast scroller, the app bar and the list rows | minor | G |
| 63 | User text containing `%` comes out mangled wherever the app puts it into a sentence | minor | G |

## Working in parallel

How to actually run it — worktrees, subagents, commits and merges — is in [EXECUTION.md](EXECUTION.md).

The lanes were cut from the plans' own **Touches** sections: two plans that edit the same source file are in one
lane, serial in the order given, except for the small, named touch points below. Lane **Z** is not parallel at all:
plan 15 edits every way a file gets into the app (four file pickers, the Android activity, the iOS import, the
desktop drop, the zip reader, the import use cases, the ViewModel), and plan 27 builds on it and on 01, so the two
run **on `master`, after every other lane has been merged**.

| Lane | Scope | Order | Why it is one lane |
|------|-------|-------|--------------------|
| A | Sync engine, sync repository, Dropbox provider, authenticators | 01 → 06 → 08 → 09 → 10 → 16 → 17 → 23 → 24 → 07 → 11 → 18 → 19 → 20 → 21 → 22 | `SyncRepositoryImpl.kt` (12 plans), `SyncEngine.kt`, `DropboxSyncProvider.kt`; 06 builds the `SyncRepositoryImplTest` harness that 08, 16, 17, 21, 22 and 24 extend; 08 states the final shape of the run's clean-up once |
| B | `:chordpro` | 37 → 38 → 39 → 40 → 41 → 42 | every plan edits `ChordProTransposer.kt` or its neighbours and is written against the result of the one before |
| C | Import planning, naming, storage actuals, zip writer, Android backup | 02 → 34 → 28 → 26 → 29 → 31 → 12 → 25 | `PrepareImportUseCaseImpl.kt` (02, 34), `LibraryFiles.kt` / `FileNames.kt` (02, 26), both `JvmFileStorage.kt` copies (26, 29), `FileStorage.wasmJs.kt` (26, 12) |
| D | Sorting and sections, setlist and song repositories, the repository base class | 03 → 35 → 33 → 36 → 30 → 32 | `GetScreenDataUseCaseImpl.kt` (03, 35), `SetlistRepositoryImpl.kt` (33, 36), `BaseLocalDataRepository.kt` (30, 32) |
| E | Editor, ViewModel, Android shell, Play updates | 04 → 05 → 14 → 46 → 47 → 50 → 55 → 48 → 52 → 59 → 45 | `CampfireViewModel.kt`, `SongEditorScreen.kt`, `CampfireApp.kt`, `CampfireActivity.kt` (04 creates `AndroidFileImport.kt` for 52), `AppUpdate.android.kt` (47, 50) |
| F | Desktop, web and iOS shells | 44 → 49 → 53 → 13 → 43 → 51 | 44 creates `OpenedFiles` for 49; 49 puts the `TEXTS` table and the scripted start into `index.html` for 53 |
| G | Song rendering and list performance | 56 → 57 → 54 → 61 → 63 → 58 → 60 → 62 | `SongLyrics.kt` (56, 57, 54, 61, 63), `ListItems.kt` (63, 58), `SetlistsScreen.kt` (58, 60) |
| Z | Size caps on every way in, then hidden files | 15 → 27 | runs on `master` after A–G are merged; see above |

Merge order: **B, C, F, A, D, E, G**, then run **Z**. B, C and F touch nothing another lane touches (Z aside). E
carries most of the `CampfireViewModel.kt` edits and G edits screens that D and E have been in, so those two go last.

Cross-lane touch points to expect at merge time (small, distinct hunks):

- `CampfireViewModel.kt`: A (21: `cancelSyncConnection` and the constructor; 22: `connectSyncProvider`), D (03: the
  section grouping moves out; 33: the setlist edit call), E (everything else).
- `Dialogs.kt`: D (33, 35, 36: the setlist dialogs, the song picker's comparator, the two create confirmations), E
  (05, 55: the unsaved-changes dialog, the sheets' dismissal), G (63: formatted strings).
- `CampfireApp.kt`: E (14, 46, 47, 45), G (60: the keyboard inset).
- `CampfireActivity.kt`: A (11: `handle(intent)` relative to `setContent`), E (04, 52). 04 and 11 each say how they
  fit together in either order.
- `AndroidManifest.xml`: A (11) and E (52) change a comment; C (25) changes the backup attributes.
- `SongsScreen.kt`: D (03: header keys), G (60: content padding). `SyncSettings.kt`: A (16), G (63).
- `strings.xml` (both languages): A (16), C (02), E (14, 46, 45) — additions only; keep every side's keys.
- Root `CLAUDE.md` and the module `CLAUDE.md` files: many plans add or correct a sentence; keep every side's.

Two cross-lane dependencies that are not file conflicts:

- **27 needs 01** (`LibraryFileKind.matches` as a member in `:data:model`), which is why it is in lane Z.
- **01's `MAXIMUM_REMOTE_FILE_SIZE`** is its own constant until plan 15 creates `ImportLimits`; when 15 lands in lane
  Z, point the one at the other, as both plans say.

Not reproduced by running, only by reading — the plans say what to watch for when verifying: 09 and 10 (need two real
installations and a Dropbox account), 11 (a low-memory device or `adb shell am kill` behind the browser), 21 and 42
(a browser), 50 (an internal testing track on Play), 25 (`bmgr`), 12 (Safari 18).
