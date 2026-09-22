# Pre-release review, fourth pass, 2026-09-22: fresh code, unfinished fixes and untaken angles

One file per issue, numbered by priority within each group. Every file is a self-contained brief for an agent (or a
person): what the user sees, where the cause is, exactly what to change, the tests to add, how to verify it, which
docs become untrue, which files it touches and which plans must land first. Delete a file once its change has landed.

This is the fourth review. The first (2026-09-14, 69 issues) landed between `d40f9754` and `7e7d893c`, the second
(2026-09-21, 63 issues) between `9ed6fc7f` and `c997bd85`, the third (2026-09-22, 48 issues) between `693f567b` and
`f34ae47a`. This one was taken at `2821f023` and aimed at three things the others could not have covered: the code
none of them saw (`aa52ae8f`, the web back stack, and `2821f023`, the library size in Settings), the earlier fixes
themselves (a fix can be incomplete or bring a bug of its own), and the angles not taken yet: the sync API contract,
ChordPro round trips against the spec, the view model as a state machine, accessibility and localization, store
policy, the release pipeline, and the docs held against the code. Seven area reviewers wrote the findings; verifiers
then re-traced every one against the code and the library sources of the versions in `gradle/libs.versions.toml`,
corrected or rewrote fixes where needed, and rejected what did not hold. Line numbers are as of commit `2821f023`;
re-locate by the quoted code where they have drifted.

Fresh code: **43** and **44** are in `aa52ae8f`'s browser history, **45** is `2821f023`'s library size, and **49** is
a sentence `aa52ae8f` reworded without noticing it had gone stale in `48fb1920`. Three plans finish or repair an
earlier fix: **04** (`4a9fa7f8` matched remote names that differ only by case, but not the index, which a case-only
"Update file name" from `02642c75` then trips over), **11** (`f19b92f5` made a backed-out consent page read as a
failed connection on Android and iOS) and **20** (`c3df90cb` folded decomposed accents but left most Latin letters out
of the table).

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`, or
  with `textResource` wherever user text goes into the sentence.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Monkey-tapping is answered with **state**, not time: a guard that asks whether the thing is already happening, a
  menu that knows it has closed, a key that knows it has not been released. No debounce windows, and no animation or
  visible wait added to cover a race (see the "no incidental animations" rule the earlier reviews followed).
- Where a plan changes documented behaviour, update the `CLAUDE.md` files and `documentation/*.md` it names in its
  **Docs** section.
- Run the unit tests after every change:

  ```
  ./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

  Compile every target the change touches, at minimum `:app:desktop:run` for a manual check and
  `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  for the rest.
- Do not commit unless asked. Load the `commit-messages` skill before writing any commit message.
- A plan that turns out to be wrong or impossible is **not improvised around**: leave its file in place and say why.

## Decisions already taken (do not re-open)

From the earlier reviews: an emptied remote folder stops the run and asks; `[Chorus]`-style bracketed words keep
being transposed; credentials live behind the Android Keystore and the iOS Keychain, and in a plain file on desktop
and the web; the demo library is planted only on a first run; sync never merges, a conflict lands as a ` (2)` copy,
an edit beats a deletion, a rename is a deletion and a new file; Android backup carries the library and the
preferences; non-Latin names keep their letters; German notation is detected per song (by an `H` root, with no marker
directive); one desktop instance per library, one tab per origin on the web; the UI is untested; the Setlists tab
always shows the setlists, whatever the library holds; a library over the import's cap is exported anyway, and
explained afterwards.

Taken by the user on 2026-09-22 for this review:

| Plan | Question | Decision |
|------|----------|----------|
| 06 | How much to guess about the code page of a song file that is not UTF-8 | **Narrow Windows-1250 detection.** Read as Central European only when the bytes point at Hungarian (`ő ű` together with `á í`) or Polish (`ą ł ż ź` right after a letter) and nothing typical of a Western language vetoes it; Windows-1252 otherwise. Czech, Slovak and Romanian keep today's reading |
| 16 | Which extensions the Windows installer claims | **`.cho`, `.chopro` and `.chordpro`**, which no other application uses; `.crd`, `.chord` and `.pro` are still imported by a drop, the in-app import or Open with. macOS is fixed as the plan says |
| 23 | Whether a lowercase root (`[a]`, `[h7]`) is a chord | **Yes, the minor chord of that root.** The viewer draws it spelled out (`Am`), the editor's Transpose keeps the file's lowercase spelling, and a lowercase `h` marks the song as German like an uppercase one |
| 27 | What an environment with a selector (`{start_of_chorus-guitar}`) is | **The environment it selects**: drawn as a chorus and recalled by `{chorus}`, since the app has no instrument or voice to match against. A negated selector reads as the directive it is on |
| 38 | The Hungarian name of the bridge section | **"Átkötés"**, in both the editor's section button and the song screen's heading (neither "Bridge" nor "Átvezetés", the two words used today) |
| 39 | How the in-app language list names its languages | **Each in its own language**: "English" and "Magyar" in both UI languages; only "System" is translated |
| 52 | A German-notated song that never needs an `H` | **Document the limit only.** The per-song `H` rule stays as decided; `chordpro/CLAUDE.md` says what such a song is read as and how to write it so that it reads right |

## What the plan writers decided that is worth a second look

- **45** removes the listing rather than taming it: every `Song` and `Setlist` carries the size of its file from the
  scan or the write that produced it, and Settings adds them up in memory. `LibraryRepository`,
  `LibraryRepositoryImpl`, `GetLibrarySizeUseCase` and its implementation are deleted. Two things change on purpose:
  the figure now counts only the files the library shows (a song over the 8 MiB cap or one that cannot be read is no
  longer in it, as it is not in the song count), and the size row is always there with the counts, since it can no
  longer fail on its own. Every later plan that constructs a `Song` or a `Setlist` adds the new `size` argument.
- **20** adds a rule to `ImportPlanner`: an incoming song (or setlist) whose text is identical to the library file
  under the **exact name it arrived with** is that file, even where its header now derives a different name. That is
  what keeps the larger accent table, and every library file named before normalization, from being copied twice by
  an export and a re-import. Nothing in the library is renamed; files whose derived name changes offer "Update file
  name" once.
- **21** does the same by design: songs whose `{meta: title …}` or `{meta: artist …}` are read for the first time get
  a real title on the next scan, and "Update file name" is offered once for them. No automatic rename.
- **41** changes the reviewer's approach. Instead of queueing the demo and having the queue wait, the demo is planted
  directly, outside the import queue, and the queue's consumer waits for the first-run decision: the user's file is
  already in the channel ahead of anything queued later, so a gate alone would not put the demo first.
- **09** declares a single required-reason category, `NSPrivacyAccessedAPICategoryFileTimestamp` (`C617.1`,
  `3B52.1`), found by reading the symbols of the linked app, and says in the plan why nothing else is declared.
- **13** matches each extension with `android:pathSuffix` instead of the reviewer's `pathAdvancedPattern`.
- **32** adds a string of its own, `song_editor_edit` ("Edit" / "Szöveg"), for the editor's Edit segment instead of
  the shared `edit` ("Szerkesztés"), which is what lets both segments fit on a 360dp phone in Hungarian.
- **18** fixes the project rather than the docs: `project.pbxproj` reads `$(TEAM_ID)` and `$(BUNDLE_ID)` from
  `Config.xcconfig`, as the docs already say (resolved values checked unchanged with `xcodebuild -showBuildSettings`).
  Picking a team in Xcode's Signing tab writes a literal back over it, which `app/ios/CLAUDE.md` is to say.
- **30** answers reordering without a drag with **Move up** / **Move down** entries in the row's menu as well as
  accessibility custom actions, because custom actions alone reach no screen reader on desktop or the web.
- **01** turns a run Dropbox refuses into `ConnectionFailed`, whose Connect keeps the index, instead of a failure
  whose only way on is Disconnect, which throws the index away.
- **05** carries a setlist file's unknown members through the model as opaque JSON text, at the document and the
  entry level, since the import, rename and replace paths all rebuild the file from the model.
- **12** makes `IosSyncNotifier` watch the sync state itself, one per process, beginning and ending the background
  task with the run and posting only while the app is in the background.

Rejected at verification, and why (so that nobody files them again): re-normalizing the library for the search after
every song edit is real but costs a millisecond or two per library change, not per keystroke, and the suggested
`flowOn` buys nothing on the web and would let the search answer from the previous library for a frame. The iOS build
needs no system boot time category in its privacy manifest: Kotlin 2.4.20's `TimeSource.Monotonic` calls
`clock_gettime`, not `mach_absolute_time`, and skia's one reference to the latter is dropped by the linker. The
reviewer's `android:pathAdvancedPattern=".*\\.cho"` would match nothing, since the advanced pattern never backtracks
(13 uses `pathSuffix`). A second, harmonic signal for German notation (a plain `B` in a flat key) would re-open the
per-song `H` rule; 52 documents the limit instead.

## Groups

| Range | Group |
|-------|-------|
| 01–08 | Data loss, and features that silently stop working |
| 09–18 | Store blockers and platform shells |
| 19–29 | ChordPro reading and writing, file names and archives |
| 30–42 | Screens, the view model, accessibility and localization |
| 43–44 | Web |
| 45 | Performance |
| 46–54 | Documentation the code contradicts |

## Index

| # | Issue | Severity | Lane |
|---|-------|----------|------|
| 01 | When Dropbox refuses a run, Settings offers only Disconnect, and disconnecting brings deleted songs back | wrong behaviour | A |
| 02 | Android: unsaved editor text is lost on rotation after the song's file disappeared | data loss | C |
| 03 | A sync index that fails to read once is taken for none: deletions come back, conflict copies, the index is overwritten | wrong behaviour | A |
| 04 | A song renamed only by case comes back when deleted; another device's edit to it becomes a conflict copy | wrong behaviour | A |
| 05 | A setlist file written by a newer Campfire (or by hand) loses its extra fields the first time this version changes it | data loss | A |
| 06 | Old Hungarian and Polish files that are not UTF-8 show `õ û` for `ő ű`, and the first save writes it into the file | wrong behaviour | A |
| 07 | Play's "what's new" loses its last bullets well under 500 characters, most of all the Hungarian one | store listing | E |
| 08 | A wipe guard that stops a run on its second pass leaves the lists unread | minor | A |
| 09 | iOS: no privacy manifest, so App Store Connect refuses the first upload | store/policy | E |
| 10 | iOS: every uploaded build stops at "Missing Compliance" | store/policy | E |
| 11 | Android, iOS: backing out of the Dropbox consent page shows "The connection did not go through." | wrong behaviour | A |
| 12 | iOS: the sync notification is never seen, and the background task does not follow the run | wrong behaviour | E |
| 13 | Android: "Open with Campfire" is not offered for a ChordPro file whose name or folder has another dot | wrong behaviour | E |
| 14 | Android: the required-update screen drops away on every rotation until Play answers again | wrong behaviour | C |
| 15 | iOS: the bundle declares English only, so a Hungarian iPhone gets the system sheets in English | minor | E |
| 16 | Desktop: the installers claim ChordPro's shared extensions, and on macOS every kind of file | minor | E |
| 17 | Android: an import in progress is abandoned half way when the activity finishes | minor | C |
| 18 | iOS: the team id and bundle id in `Config.xcconfig` are ignored by the project | docs / build config | E |
| 19 | Directives written with a space instead of a colon, the spec's own examples included, show up as lyrics | wrong behaviour | B |
| 20 | Polish, Turkish, Vietnamese and other Latin letters become gaps in file names and escape an accent-free search | wrong behaviour | A |
| 21 | `{meta: title …}`, `{meta: artist …}` and `{meta: key …}` are ignored | wrong behaviour | B |
| 22 | Grids: the margin label is transposed as a chord, and only the first chord of a `C~G` cell moves | wrong behaviour | B |
| 23 | Lowercase minor chords are never transposed, and a lowercase `h` does not mark a song as German | wrong behaviour | B |
| 24 | The editor's Transpose rewrites the user's `{key}` spelling and the spaces inside chord brackets | minor | B |
| 25 | With a key change, the list shows the song's last key, not its first | minor | B |
| 26 | ABC, LilyPond, SVG and text blocks are read as lyrics, and Transpose rewrites their brackets | minor | B |
| 27 | `{start_of_chorus-guitar}` becomes a custom section, and a negated selector shows as lyrics | minor | B |
| 28 | `{highlight}` comments are silently dropped | minor | B |
| 29 | Archives from older Windows tools show garbled file names, and backslash paths are not stripped | minor | A |
| 30 | A setlist cannot be reordered with a screen reader or a keyboard | accessibility | D |
| 31 | "Imported 1 songs and 0 setlists": counted English sentences that are not plurals | minor | D |
| 32 | The editor's Edit / Preview choice is cut off on phones, badly in Hungarian | minor | C |
| 33 | A chord grid wider than the column loses its last bars without any sign | wrong behaviour | D |
| 34 | On wide windows a screen reader reads a song's columns interleaved | accessibility | D |
| 35 | A setlist's song numbers break onto two lines from 100 songs up, or from 10 at a large font | minor | D |
| 36 | Snackbar messages appear behind the on-screen keyboard, the editor's "could not save" included | minor | C |
| 37 | Chords and tablature are invisible to screen readers | accessibility | D |
| 38 | Hungarian: a wrong article before some numbers, and two names for the bridge | minor | D |
| 39 | The language setting names each language in the current language | minor | D |
| 40 | An "Update file name" that moves the file but fails on a setlist closes the song and says nothing changed | minor | C |
| 41 | A first launch through "Open with" or Share imports the demo after the user's file, not before | minor | C |
| 42 | A long tag in the song header hides its own remove button | minor | D |
| 43 | Web: Forward after paging a setlist loses the setlist, and every further Forward repeats it | minor | C |
| 44 | Web: Forward into a song of a setlist pages through the setlist as it was | minor | C |
| 45 | The library size lists the whole library from disk on every change, from app start on | performance | A |
| 46 | The docs describe the sync wipe guard differently from the code | docs | A |
| 47 | Root `CLAUDE.md`: the iOS version is said to live in the Xcode project; it comes from `gradle.properties` | docs | E |
| 48 | Root `CLAUDE.md`: the web distribution is said to be precompressed; precompression is off | docs | E |
| 49 | `app/web/CLAUDE.md`: the Dropbox redirect URI is documented for github.io; the page sends pandulapeter.com | docs | E |
| 50 | `file-format.md`: the setlist example names a file the app never writes, and leaves out the description | docs | B |
| 51 | The Microsoft Store guide says the Windows build has never run | docs | E |
| 52 | A German-notated song with no `H` is read as English (documented as a limit) | docs (decided) | B |
| 53 | `presentation/CLAUDE.md` overstates how `visibleDialog` is written | docs | C |
| 54 | `app/desktop/CLAUDE.md` misplaces when the macOS open-file handler is registered | docs | E |

## Working in parallel

How to actually run it — worktrees, subagents, commits and merges — is in [EXECUTION.md](EXECUTION.md).

The lanes were cut from the plans' own **Touches** and **Depends on** sections: two plans that edit the same source
file are in one lane, serial in the order given, except for the small, named touch points below.

| Lane | Scope | Order | Why it is one lane |
|------|-------|-------|--------------------|
| A | Sync repository and engine, the data model, local sources, import, zip | 01 → 03 → 08 → 04 → 11 → 05 → 45 → 06 → 20 → 29 → 46 | `SyncRepositoryImpl.kt` (01, 03, 08), `SyncRepositoryImplTest.kt` (01, 03, 08, 11), `Setlist.kt`, `SetlistMappers.kt` and `SetlistLocalSourceImpl.kt` (05, 45), `ImportPlanner.kt` (05, 20), `data/repository/implementation/CLAUDE.md` (01, 03, 04, 08, 45), `data/model/CLAUDE.md` (05, 45, 06, 20), `data/source/local/implementation/CLAUDE.md` (05, 06, 29); 01 → 03 → 08 edit one function in turn |
| B | `:chordpro` and the file format page | 19 → 24 → 21 → 25 → 22 → 26 → 27 → 28 → 23 → 52 → 50 | `ChordProSyntax.kt` (19, 21, 22, 26, 27, 28), `ChordProParser.kt` (21, 25, 22, 26, 28, 23), `ChordProTransposer.kt` (24, 21, 22, 26, 23), `ChordProHighlighter.kt` (19, 26), `chordpro/CLAUDE.md`, `documentation/file-format.md` (19, 21, 28, 50); 21 needs 24's `transposeKeyLine`, 27 needs 19's `walkDirective` and uses 26's `delegateEnvironments` |
| C | View model, app root, the editor, web navigation | 02 → 14 → 17 → 41 → 36 → 40 → 32 → 43 → 44 → 53 | `CampfireViewModel.kt` (02, 17, 41, 40), `SongEditorScreen.kt` (02, 32), `CampfireApp.kt` (36, 40), `BrowserRoutes.kt` (43, 44), `presentation/CLAUDE.md` (all); 14 needs part 2 of 02 |
| D | Screens, accessibility, strings | 30 → 31 → 33 → 34 → 35 → 37 → 38 → 39 → 42 | `SongLyrics.kt` (33, 34, 37), `values-hu/strings.xml` (30, 31, 38, 39), `values/strings.xml` (30, 31, 39) |
| E | iOS, Android and desktop shells, store and release, the build docs | 07 → 09 → 10 → 15 → 18 → 12 → 13 → 16 → 54 → 47 → 48 → 49 → 51 | `project.pbxproj` (09, 15, 18), `Info.plist` (10, 15), `app/ios/CLAUDE.md` (09, 10, 12, 15, 18), `app/desktop/CLAUDE.md` (16, 54), root `CLAUDE.md` (47, 48) |

Merge order: **B, E, A, C, D.** B touches nothing outside `:chordpro` but one page of documentation; E touches only
the platform shells and docs; A reaches the view model and the settings screen through 45; C carries the view model;
D edits strings and screens that A and C have been in, so it goes last.

Cross-lane touch points to expect at merge time (small, distinct hunks):

- `CampfireViewModel.kt`: A (45: `librarySummary`, `loadLibrarySize` removed, the KDoc above `emptyPlaceholder`
  merged), C (02, 17, 41, 40).
- `SettingsScreen.kt`: A (45: the size row), D (39: a comment only).
- `Song` and `Setlist` gain a `size` parameter in 45 (lane A): a plan of lane C or D that constructs either, or a
  test helper that does, adds it when its lane is rebased.
- `strings.xml` (both languages): C (32, 40 add keys), D (30 adds keys; 31, 38, 39 change existing lines) —
  additions only across lanes; keep every side's keys.
- `documentation/file-format.md`: A (05: the setlist section), B (19, 21, 28, 50).
- `domain/implementation/CLAUDE.md`: A (20), C (40). The `:domain` modules themselves: A (45 deletes
  `GetLibrarySizeUseCase`), C (40 changes `RenameSongFileUseCase`) — different files.
- `presentation/CLAUDE.md`: C (almost every plan), D (30, 33, 34, 35, 37), E (15). Root `CLAUDE.md`: A (20, 46),
  E (47, 48). The module `CLAUDE.md` files: many plans add or correct a sentence; keep every side's.

Not reproduced by running, only by reading and by the library sources — the plans say what to watch for when
verifying: 01, 03, 04 and 08 (a Dropbox account, and for 04 and 08 a second installation), 11 (an Android or iOS
device and a Dropbox account), 02, 17 and 36 (an Android device or emulator: rotation, Back during an import on
Android 9–11, the on-screen keyboard), 14 (a Play-installed build on an internal testing track with a priority 4–5
release), 13 (an Android file manager handing over a dotted name), 09, 10 and 18 (Xcode and an App Store Connect or
TestFlight upload), 12 and 15 (an iOS device, the latter set to Hungarian), 30, 34 and 37 (TalkBack or VoiceOver),
16 (installing the DMG and the MSI), 07 (a run of `release.yml` / `android-publish.yml`), 43 and 44 (the web build in
a browser, with its Back and Forward buttons).
