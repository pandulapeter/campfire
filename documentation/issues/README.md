# Sixteenth review — a general review from a blank slate

Reviewed commit: **8ee010b36** on `master` (2026-10-05/06). The working tree carried one uncommitted change that is
the user's own and not part of the sweep: `documentation/TO_DO.md`. Nothing in these plans touches it.

Angle: a general review with no area presumed solid — nine read-only area reviewers (`:chordpro` and the models;
local storage; sync and the network; `:domain` and the metronome engine; the view model and navigation; song details
and the metronome UI; the list, editor and settings screens; dialogs, print and the platform UI code; the shells and
CI) and one live run of the desktop app with about 3 000 songs and 40 setlists. Every finding was then verified
against HEAD by a writer per lane (probe tests for the pure logic, deleted afterwards), and every fix was challenged
by agents that had not written it.

45 plans were written, 44 remain after the decisions (plan 19 dropped by D3): 2 high, 8 medium, 34 low. Nothing has been executed.

## Headlines

- **01 (high, crash)** — a small file of `{chorus}` recalls multiplies into millions of lines: 14 KB expands to a
  million recalled lines, 28–29 KB runs `parse` or `transpose` out of memory. Reachable by any import or sync.
- **30 (high, crash)** — an import batch that carries one song twice lists it twice under "Already in the library";
  opening either row hands the song pager one key twice and Compose throws. (17 is the data-side half.)
- **10 (medium, data loss)** — the synced-overrides step deletes another device's transposition, tempo or capo for a
  song this run has not downloaded yet; a first run uploads an empty `songs` object.
- **31 (medium, data loss)** — Choose songs writes the sheet's whole list on every tick, dropping entries a sync run
  brought into the setlist while the sheet was open. (33 is the same shape for Edit setlist, low.)
- **60, 61 (medium, release pipeline)** — a slip in a release description's instruction comment is silently taken as
  absent (every store submits, priority 0); a release with an empty notes block ships the commit log to Play and
  fails the Apple and Windows jobs after their uploads.
- **11 (medium, desktop)** — the desktop click's feed thread ignores a short `SourceDataLine.write` and spins when
  the audio device goes away.
- **02 (medium)** — Prettify (so every import) splits a legacy `{c: Chorus}` section at a comment or break inside it,
  and a later `{chorus}` repeats only half the chorus.
- **32 (medium, ux)** — the editor's preview shifts what is typed by the library's transposition override.
- **18 (low, but it spreads)** — on the web an interrupted first write leaves an empty file, which the next sync run
  uploads under the song's name, keeping the real song only as ` (2)` on every device.

## Index

| # | Title | Severity | Lane |
|---|---|---|---|
| 01 | Bound what `{chorus}` recalls may expand to, in the parser, so a small file cannot multiply into millions of lines *(amended)* | high | A |
| 02 | Keep Prettify from inserting a blank line that splits a running heading section or paragraph at a comment or a break *(amended)* | medium | A |
| 03 | Track whether the open section has a line instead of rescanning its blank lines on every block *(amended)* | low | A |
| 04 | Read the first tempo and time signature the song can use, and never a negative capo, as the highlighter already does | low | A |
| 05 | Transpose the chords in a tab or grid environment's label on the model, as the text transposition does | low | A |
| 06 | Keep letters outside the Basic Multilingual Plane in normalized names instead of turning them into separators *(amended)* | low | A |
| 10 | Keep the synced preferences of a song that reached the cloud folder after the run listed it | medium | B |
| 11 | Stop the desktop click as a lost output when the audio line stops taking data | medium | B |
| 12 | Record the revision a sync download actually fetched, not the one the listing named | low | B |
| 13 | Read and publish a sync refresh under the library file lock, so it cannot put back what a save or deletion replaced | low | B |
| 14 | Keep a refused connection shown as failed when the reconnect started from it is cancelled *(amended)* | low | B |
| 15 | Point the batch's setlists at the library's spelling of a song name whose conflict was answered with Skip | low | B |
| 16 | Compare an incoming setlist with its entries deduplicated, the way it is written | low | B |
| 17 | List each library file once in an import's result | low | B |
| 18 | Never leave an empty file behind a new file's interrupted first write on the web *(amended)* | low | B |
| 20 | Say in the repository contracts that the sync repository refreshes what a run changed, and that an import ends with `adoptImported` | low | B |
| 21 | Correct two stale KDocs of the local storage: `JvmFileStorage`'s reason for two copies and `loadSongs`' progress cadence | low | B |
| 22 | Give the zip writer's real reason for storing its entries uncompressed *(amended)* | low | B |
| 23 | Fold directive short forms and spacing when an import compares two songs *(amended)* | low | B |
| 30 | Never hand the song pager a file name twice, so that opening a duplicate from the import screen cannot crash | high | D |
| 31 | Write one song in or out per tick of Choose songs, so a tick never reverts what a sync run brought into the setlist | medium | D |
| 32 | Show the text as written in the editor's preview, without the library's transposition *(amended)* | medium | D |
| 33 | Write only the setlist details the Edit setlist sheet changed, and say so when the setlist is gone | low | D |
| 34 | Keep the first run's welcome sheet off an import question, so its Open settings cannot cancel the import | low | D |
| 35 | Refuse deleting the library while an import is running or waiting for its question *(amended)* | low | D |
| 36 | Write everything still waiting for its debounce — text size, metronome settings, export options, tempo and capo overrides — before the view model or the desktop process goes *(amended)* | low | D |
| 37 | Settle the stored editor draft before the desktop process ends, so a discarded text cannot come back | low | D |
| 38 | Hand an unreadable dropped or opened desktop file to the import empty, so it is reported as skipped | low | D |
| 39 | Fall back to the exact name the save dialog returned when the name with the extension added cannot be written *(amended)* | low | D |
| 40 | Close the export screen from the view model once its file is saved, rather than by an event nobody may hear | low | D |
| 41 | Refuse to select a tab whose feature is switched off, even from its item while it shrinks away *(amended)* | low | D |
| 42 | Cross-fade the song details title block only when the song is another one *(amended)* | low | D |
| 43 | Read the time signature of a song being renamed in the metronome panel, as the click already does | low | D |
| 44 | Glide the song's sections while the window's height keeps changing, as they already do for its width *(amended)* | low | D |
| 45 | Keep the editor's caret and selection next to their text after an edit made from a sheet or by Save *(amended)* | low | D |
| 46 | Change a search's open state and its text in one snapshot, so reopening never applies the previous query | low | D |
| 47 | Pass the keyboard-following padding down rather than reading it while composing, on three screens *(amended)* | low | D |
| 48 | Make the close that follows a sheet's action final, so a second tap during the slide cannot keep the sheet open *(amended)* | low | D |
| 60 | Stop the release on an instruction comment the description parser cannot read, instead of taking it as absent | medium | E |
| 61 | Give a release with no notes one "What's new" for every store before any build starts, instead of failing the Apple and Windows jobs after their uploads | medium | E |
| 62 | Read `campfire.log` in the Windows start check, as the Linux and macOS legs read theirs | low | E |
| 63 | Hold the first launcher icon switch while a click is playing, so locking the screen does not close the task under it | low | E |
| 64 | Retry the store scripts' GET requests on a transient failure and give every request a timeout *(amended)* | low | E |
| 65 | Correct three stale statements: the build properties list, the web launch test's file name, and what the Android INTERNET permission is for | low | E |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|---|---|---|---|
| A | `:chordpro`, `:data:model` | 01, 02, 03, 04, 05, 06 | `chordpro/**` except the files lane B's 23 names; `data/model/**`; for 06 only, `data/source/local/implementation/src/commonTest/.../FileNamesTest.kt` |
| B | storage, sync, `:domain`, metronome engine | 10, 11, 12, 13, 14, 15, 16, 17, 23, 18, 20, 21, 22 | `data/repository/**`, `data/source/**`, `domain/**`, `metronome/**`, `app/web/src/wasmJsMain/resources/opfs-writer.js`, `app/web/tests/opfs-writer.test.cjs`; for 23 only, `chordpro/.../ChordProSplitter.kt`, `ChordProSyntax.kt` and their tests |
| D | `:presentation` | 30, 48, 31, 33, 35, 34, 36, 37, 40, 41, 32, 45, 42, 43, 44, 46, 47, 38, 39 | `presentation/**` |
| E | shells, CI, docs | 60, 61, 62, 63, 64, 65 | `app/**` except lane B's two web files, `.github/**`, `.claude/skills/prepare-release/SKILL.md` (60, 61) |

Order notes: 01–04 edit different functions of `ChordProParser.kt`; 15 → 17 change the same lines of
`ImportFilesUseCaseImpl` (keep both), 23 appends to the test 16 extends; 18 before 19; 60 before 61 (same `read()`);
in lane D, 30 first (the crash), 48 before the other `Dialogs.kt` plans (it changes the shared sheet they sit in),
37 before 36 (both add a first step to `settleSynchronizationBeforeExit`: 37's draft settle, `metronome.stop()`, 36's
two writes, then the sync wait), 32 before 45 (`SongEditorScreen.kt`), 38 before 39 (`FilePicker.desktop.kt`).

**Merge order: A, B, E, D.** A first because B's 23 builds on `:chordpro` and both edit `chordpro/CLAUDE.md`; E
before D so the root `CLAUDE.md` sentences of 61, 62 and 65 are in before D's 32; D last, since it touches the most
shared UI files and is the longest lane.

### Shared files

- **Root `CLAUDE.md`** — 10 and 23 (lane B), 32 (lane D), 61, 62, 65 (lane E), each one quoted sentence or
  paragraph. A lane edits only the sentence its plan quotes; a cherry-pick conflict is resolved as a word-level
  three-way merge keeping every lane's sentence.
- **`chordpro/CLAUDE.md`** — 01, 02, 04 (lane A) and 23 (lane B), different paragraphs.
- **`data/repository/implementation/CLAUDE.md`** — 10, 12, 13, 14, different paragraphs (one lane, serial).
- **`data/source/local/implementation/CLAUDE.md`, `app/web/CLAUDE.md`** — 18, 22 (lane B). Lane E does not
  touch `app/web/CLAUDE.md`.
- **`data/model/.../ImportResult.kt`** — 17 (lane B) changes one KDoc line; lane A's 06 edits `LibraryFiles.kt` only.
- **`strings.xml` (both languages)** — lane D only.
- **`presentation/CLAUDE.md`** — lane D only.

## Decisions

Taken by the user on 2026-10-06:

- **D1 — plan 32, what the editor's preview shows. Answer: A.** (A, recommended) the text as written: the file's own
  `{transpose}` stays, the reader's library override goes, so field, stepper and preview agree; (B) the text as it
  will be read where the editor was opened from, which needs the setlist added to the editor destination.
- **D2 — plan 61, a release with no notes. Answer: B.** (B, the plan's default) every store gets one fixed line before any
  build starts — note that "Bug fixes and improvements." is the vague note the prepare-release skill otherwise
  forbids; (A) `prepare` stops the release and the skill always writes a bullet instead of an empty block.
- **D3 — plan 19, journal names on Safari before 26 / iOS 18. Answer: drop — plan 19 is removed from the folder and lane B.** (drop, recommended by the challenger) it needs both
  halves of a name near the 120-byte cap on a shrinking set of browsers, and changes the journal format of the web's
  most crash-sensitive code; (keep) land it after 18.

Defaults taken without asking (say so if any should change):

- 01: the recall budget is a weight (8 per line + characters + chord lengths), allowance 64 000 + twice the song's
  own weight; a 40-line chorus recalled 20 times is about 49 000. A recall past it is drawn as its heading alone.
- 02: library files an earlier import stored with the split keep it, so re-importing their original source asks keep
  both / replace — accepted, like the earlier re-import conflicts under older rules.
- 04: `ChordProMetadataFields.set` is brought in line (the plan's optional step) only if trivial; otherwise left.
- 06: songs with letters outside the BMP in their titles will show **Update file name**; nothing is migrated.
- 12: `download` returns the existing `RemoteDocument`. 13: the whole refresh runs under `LibraryFileLock` (a Save
  can wait for one refresh, up to a second or so after a first sync of a large web library). 17: a repeat of a song
  this import wrote is left out of "Already in the library". 22: docs only, the zip stays uncompressed. 23: only the
  comparison key folds short forms; Prettify does not rewrite them.
- 34: the welcome is skipped for good when an import screen is up on the first run. 35: an import arriving during a
  library deletion waits and then imports into the emptied library. 39: landed as a harmless fallback, with a
  TestFlight check owed. 41: the optional step 2 is taken, with the colour override. 42: cover and title edits
  animate in place.
- The 0.5–0.8 s text-size step on a 2 000-line song is recorded as a measurement, not planned.

## The challenge

Ran on all 45 plans (five challengers, none the author of what it read). **20 amended, none dropped by the challengers**; 19 was then
recommended for dropping (D3). What it changed:

- 01 — the cap counted lines only; one 5 000-chord line recalled 1 000 times still ran out of memory. Now a weight.
- 02 — `ChordSheetConverter` relied on the blank line Prettify inserted before `Interlude`/`Instrumental`; it now
  writes it itself.
- 03 — the test passed without the fix (40 000 lines parse in 0.5 s); now 150 000 lines against a 2 s bound.
- 06 — cased scripts outside the BMP: the name depends on `lowercase()` agreeing across JVM, Native and Wasm; one
  iOS test run and a fallback table added.
- 14 — "failed" is restored only while the provider still holds credentials.
- 18 — no marker file for names over 243 bytes (APFS's 255-byte limit on Safari 26+); recovery restructured.
- 19 — old journals told apart by "32 hex digits" alone; decoupled from 18.
- 22 — a third stale statement (`ArchiveLocalSourceImpl`'s KDoc) added.
- 23 — `longNames` must be declared below the maps it reads (object initialisation order).
- 32 — the file's own `{transpose}` stays applied.
- 35 — the reverse order added: an import queued during a deletion waits for it; a second deletion is refused.
- 36 — the writes are also awaited in the desktop's pre-exit hook, the `onCleared` launch is guarded, and the waiting
  tempo and capo overrides (lost on every macOS Cmd+Q today) go through the same step.
- 39 — the fallback only when the extended file was never created.
- 41 — a leaving item keeps its colours instead of dimming in one frame.
- 42 — cover and title edits animate in place, since the block cross-fade no longer does it.
- 44 — the metronome panel opening is a height burst too and now glides.
- 45 — offsets map line by line; the single-stretch mapping threw the caret many lines down.
- 47 — `SettingsPage` in `SettingsLayout.kt` reads the padding too.
- 48 — the scrim and Back are not turned off by `sheetGesturesEnabled`; the fallback now lets Material's own hide
  finish instead of dismissing in one frame.
- 64 — dropped connections arrive unwrapped by urllib; the retry covers them, and `github_token()` gets a timeout.

## Measurements (live run, desktop, isolated data directory)

| What | Result |
|---|---|
| Cold start, empty data directory | 1.55 s to the list; demo library planted, welcome shown once |
| Second launch | 1.10 s; neither again |
| Import of 2 990 songs + 40 setlists | 14.4 s; duplicates, collisions and setlist entries all right |
| Startup with 2 952 songs (and with 6 963) | 1.29–1.54 s (1.29 s); 418 MB RSS after start |
| Search, filter chips, paging a 60-song setlist | no UI stall of 60 ms or more |
| 124 KB / 2 000-line song | 428 ms to open; 0.5–0.8 s per text-size step; 1.5 GB peak |
| 60-song setlist PDF | 98 pages in 2.0 s, 7.7 MB; 1.30 GB peak RSS |
| Library export / re-import | 0.5 s, 5.04 MB stored zip / 0.67 s, all "already there" |
| `campfire.log` | no app exception in any phase |

Not covered by the live run: setlist reordering by drag, the date picker, quitting with unsaved editor text.

## Manual checks owed

- 01: open a crafted 3 000 × 3 000 `{chorus}` file on Android — no freeze, two recalls then headings; transpose and PDF-export it; demo songs' recalls still whole.
- 02: import a Campfire 3 `{c: Chorus}` file with `{c: x2}` inside and a later `{chorus}` — the recall repeats the whole chorus; Prettify adds no blank before `{c: x2}`.
- 03: none beyond the unit test (optionally open the crafted blank/comment file).
- 04: header `{tempo: Moderato}` + `{tempo: 96}` — first line red, stepper reads 96.
- 05: `{start_of_tab: Riff [G]}` inside a verse, transpose +2 on song details — fold reads `Riff [A]`.
- 06: a song titled in Adlam or with 𠀀 gets a file name carrying those letters; export/import round trip recognises it.
- 10: two devices; create a song with a capo on B while A runs a long sync; after both run again the capo survives on both.
- 11: Linux `.deb`, click through a USB headset, unplug: the click stops with "output failed", CPU and memory settle; repeat on Windows.
- 12: two quick saves of one song on B during A's sync, then edit it on A: no "(2)" copy.
- 13: during a first sync, tag one arrived song and delete another: neither change is undone.
- 14: revoke Dropbox access, Sync now, Connect and close the consent page: still "authorization refused".
- 15: on a Mac, a hand-capitalised `Beatles-Yesterday.cho` + a zip with a different one and a setlist, answer Skip: the setlist shows the library's song.
- 16: two same-title template songs in a setlist, export zip, delete, import twice: second import asks nothing.
- 17: a zip holding one library song twice: the report lists it once and pages normally.
- 18: Chrome and Safari 18: close the tab mid-import and mid-first-sync: no empty songs, no ` (2)` copies.
- 20, 21: none (docs).
- 30: import a song plus an identical copy, open either "Already in the library" row from the report — no crash.
- 31: two devices; a song added to a setlist on device 2 survives a tick in device 1's open Choose songs after a sync.
- 32: library transposition +2, open the editor — preview, field and stepper name the same key.
- 33: two devices; another device's description survives a title-only Edit setlist; Save on a deleted setlist shows the failure message (also Duplicate).
- 34: fresh install opened with a file conflicting with a demo file — no welcome over the import question.
- 35: drop a big folder with the delete-library sheet up — Delete stays disabled until the import is over.
- 36: Android, change metronome volume / pinch text, Back Back — the value persists.
- 37: desktop, store a draft by switching windows, quit with Discard, relaunch — no "unsaved changes restored".
- 38: desktop, drop a `chmod 000` `.cho` — reported as skipped.
- 39: sandboxed Mac build, save an export with the extension deleted — saved, not "Export failed".
- 40: Android, rotate inside the system save screen of a ChordPro export — the export screen closes after saving.
- 41: switch Metronome/Setlists off and tap its shrinking nav item — stays on Settings.
- 42: add a tag / set a tempo on an open song — the app bar title does not dim; paging still cross-fades.
- 43: Update file name on a 3/4 song with the metronome panel up — beat row stays at three.
- 44: desktop, drag the bottom window edge on a multi-row song — rows glide; landscape phone bar collapse — no springing, flings rest on dividers.
- 45: desktop German notation, `[Bb]` above the caret + Ctrl+S — caret stays; add a tag from the menu — caret stays in its verse.
- 46: large library, search, close, scroll, reopen — no jump to top, no flash of old results; close restores position.
- 47: report screen search keyboard — no per-frame recomposition; setlist description still collapses on a short window.
- 60: none beyond the unit tests (optionally run the script on a slipped body with `RELEASE_BODY`).
- 61: next small release with an empty block — B: every store shows the default line; A: `prepare` stops.
- 62: dispatch `publish-windows.yml` with `submit` off; the step prints a log without exceptions and passes.
- 63: fresh install, pick a color in the welcome sheet, play a click, lock the screen — click keeps playing; icon switches at the next real leave.
- 64: watch the next Apple/Windows runs for an "asking again" line on a failed poll.
- 65: none.
- 22: none (docs).
- 23: import a song, then a copy written with `{t:}`, `{soc}/{eoc}`: "already there", no question, no `_2`.
- 48: double-click Create on New song / New setlist / Delete library (and Save on tags): created once, sheet always
  slides away; X then press-and-hold on the sliding sheet still keeps it up.
- 36 (amended): desktop, step a song's tempo or capo and quit at once with Cmd+Q — the override is there on relaunch.
- 44 (amended): open and close the song's metronome panel on a multi-row song — the rows glide.
- 06 (amended): a Dropbox sync of an Adlam-titled song between an Apple device and Android/desktop — one file name.

## Checked and found solid

- `ChordProSyntax.splitLines` / `lineStartOffsets` / `joinLines`: every line ending, trailing break restored; text editors round-trip unrelated bytes.
- `walkDirective`: colon/whitespace separation, negated selectors, selector suffixes dropped consistently everywhere.
- `bodyStartIndex` agrees with the parser's `Transposition.startBody`.
- `ChordProMetadataFields.set`: effective line rules match the parser; spellings kept; empty-line keeping.
- Tags, languages, cover art, links editors: line-preserving, no brace/line-break injection.
- `ChordProHeader.insert` and `DeclaredMetadataCache` (CRLF, change spans).
- `ChordProSummaryCache` rescans whenever an edit can change a line's kind.
- `ChordProChordNames` on slash chords, `C6/9`, bass digits, double accidentals, parenthesized chords, `[Break]`-style words.
- Text vs model transposition agree on tab runs cut by comments/recalls/`{transpose}`; German detection.
- `ChordProNotation.convertText` STANDARD↔GERMAN round trip incl. lowercase minors and `{define}`.
- `ChordProTabTransposer` octave fitting; `ChordProTabWrapper` termination and budgets.
- `ChordSheetConverter`: no backtracking regexes, bounded per-line work.
- `ChordProSplitter`: mid-file BOMs, `{ns}` in delegated environments.
- `decodeLibraryText` encoding detection; `normalizedToNfc` actuals on all four platforms.
- `ChordProSerializer` is test-only, so its costs and lossy output affect nothing shipped.
- `SyncPlanner`: every local/remote/index combination, edit-beats-deletion both ways, Resolve for two new files with no index.
- Deletion guard arithmetic, one direction at a time; KEEP_AND_UPLOAD / KEEP_AND_DOWNLOAD drop the right index entries (and base preferences).
- `SyncEngine.download` re-read under the lock before the write; `deleteLocally` check-and-delete under one lock.
- `resolve`/`resolveWith`: copy before upload, copy name free on both sides, `discardCopy` only on a definite refusal.
- Index crash safety (a lagging index repaired by Resolve+same content or Forget).
- `withoutForeignEntries`, name folding onto listings, `tooLarge`/`unreadable`/`unstorable` exclusions (and that `unstorable` reaches `keptFileNames` via `summary.failed`).
- Run state machine, debounce, Stop/Sync now, disconnect, library deletion's DELETE_REMOTELY run; the run-in-progress marker.
- `SyncedPreferencesDocument` merge/localDocument/withSongsSpelled/applyTo, unknown fields, newer format, unreadable document, conflict retry, `writtenBySync`.
- `BaseLocalDataRepository` read/re-read/replay, partial publishing, `writeData`/`transformAndWriteData`.
- Dropbox provider: pagination, retries and `retry_after`, 401 refresh, timeouts, `delete_batch`, write modes, `insufficient_space`.
- `SyncCredentialsStore`, `completePendingAuthorization`, owed forgetting; the four authenticators.
- Cover art repository/local/remote sources, MusicBrainz limiter and iTunes search, User-Agent.
- `JvmFileStorage` atomic writes, reads, Windows names; `IosFileStorage`; Android backup allow-lists.
- `OpfsFileStorage` NotFoundError folding and listing filters; `opfs-writer.js` journal for an existing file.
- `uniqueName`/`moveFile`/`isNamed`; user preferences decoding with `.bad` copy; setlist document format; song scan batching; sync state source; Android/iOS secret stores; editor draft; cover prune; zip reader/writer and PDF/Word extractors' bounds.
- `ImportPlanner.planSongs`, `ImportFilesUseCaseImpl` replace guards and failure handling, `PrepareImportUseCaseImpl` budgets, `followSongReferences`, `saveSong`'s `expectedText` guard and lock order, `DeleteLibraryUseCaseImpl`, export use cases, `GetScreenDataUseCaseImpl`.
- `MetronomeSequencer`, `ClickMixer`, `ClickSynthesizer`, `ClickStream`, `MetronomeImpl` confinement and session ids, `TapTempo`, `TimeSignature.parse`; Android, iOS and web outputs.
- `updateBackStack`: metronome stop on leaving its screen, reorder mode cleared, retained editor field dropped, pager maps pruned, import-report-left detection, persisted stack truncation.
- Restored back stack: ImportReport filtered out, empty stack → Songs, `SongDetails.id` encoded with `@EncodeDefault`.
- Import queue: demo decision ordering, one batch at a time, cancel of preparation vs scope, NonCancellable writing, Review/Importing/Finished report-left semantics, `importReportRequest` guard.
- `openImportedSong` / `selectTopLevelDestination` / `restoreNavigationState` never cover or replace an editor with unsaved text; `navigateOnLaunch` waits for draft recovery.
- Editor draft lifecycle: drop only when no editor of the file remains, recovery skips equal drafts, stored in file notation, save-and-leave takes the pending exit first.
- `editSongText` single mutex with compare-and-write; tag/link/metadata edits keep values another device added.
- Tempo/capo pending overrides: debounce, reset cancels waiting write, settled only when the store agrees, rescued in `onCleared`.
- Metronome collector: rename window via `songsBeingRenamed`, context move restarts only for a different song; export dialog stops the click.
- Dialog-closing collector for songs that disappear; `_underlyingSongInfo` parent/child across CoverArtSearch ↔ RemoveSongCoverArt.
- `launchFileTransfer` lazy start + identity check; PDF export cancelled when its screen stops being the dialog.
- What's new gating and recording; first-run preferences written in the demo planting's tail.
- `SongSearchIndex` reuse; `songGroupsFor`/`rankSongs` tag-only matches last; heavy derivations on Default.
- `CampfireTheme` cross fade; `AppUpdateGate` waits for unsaved text, sync and a playing click; launch screen latch.
- Web `BrowserHistory`/`BrowserRoutes`: depth stamping, forward-instead-of-push, multi-step Back, validated Forward.
- Strings: every key/plural in both languages with matching arguments; no `%%`; `textResource`/`withTexts` safe for `%`/`$`.
- Songs list: `SongSectionIndex`, filter rows keep their anchor, invisible filters ignored by the domain filter.
- Setlists reorder mode exits, drag order identity checks, deduplicated entries → unique row keys, archived rows read-only.
- Setlist header subtitle (countdown, total duration with `+`), sync section stages, Settings tab pager ⇄ `settingsTab`.
- Import report stage cross-fade, Review → ConfirmImportReplace guarded by `pendingImport`, row keys unique per section.
- Editor: initial vs retained draft, `EditorFieldSaver` cut-off, `FollowFileWhileUntouched`, revert via undo, Ctrl/Cmd+S excluding Alt, token cache.
- Components: `OverflowMenuState`, `ActionsMenu`, `FastScroller`, `ListTopFade`, `ScrollToTopWhenChanged`, `SearchBackHandler`, `CampfireBottomSheet` guards.
- Song details: `MetronomeContext` follows `targetPage`; `metronomePatternOf` and `timeSignatureOrDefault` fall back to 4/4 consistently; beat levels fall back on wrong length.
- Tempo/capo effective values and bounds; Tap segment survives a tap-set tempo; panel show/hide rules; performance-mode metronome placement.
- Pager key = fileName (with plan 30), identity-checked `pageScrollStates`, `hasShownLyrics`, initial-page settling.
- `PageStepper`, `stepOnTap`, `songKeyboardShortcuts`; `RowSnapping` strict progress and anchoring; `SectionSizesPool` keys; `flowLikeAMagazine` `!!` safe.
- `rememberSongLyricsModel`, `StepProgressIndicator`, `FontScaleAccumulator`; Metronome tab layout at 360×640 and 2× font; accessibility labels.
- `PrintDeflater`, `PrintPdfWriter` (xref, lengths, fonts, ToUnicode, ActualText), `PrintRenderer.selectableText`, `wrapPrintText`, `layoutPrintDocument`.
- Export screen: deferred Save, Cancel ring, refusal once off screen, flush on close, zip subset manifest.
- Android `AndroidFilePicker` (launchers per composition, orphaned results after process death), `AppUpdate.android.kt`, platform actuals (keyboard, haptics, language names, calendar locale, persistence).
- Web `FilePicker` (cancel/focus fallback, URL revoke, folder drop), desktop `FilePicker` (EDT dialogs, IO off it, `openUrl` fallback).
- Sheets: `LocalIsSheetClosing`, single confirmation on create/delete library, nested sheets, `dismissSheet` identity, song dialogs closed when the file leaves, Song defaults writes only changed fields, date picker UTC round trip, duration mask.
- Cover search: previous job cancelled, selection cleared on new search or failed tile, Save disabled for the current cover, Remove via confirmation.
- Android intents handled once, `onNewIntent` for `singleTask`, redirect before `setContent`, unreadable streams skipped.
- Android sync service: start in `onPause`, dismiss in `onResume`, stop/dismiss split, 2 s hand-over, `onTimeout`, `dataSync` type.
- Android metronome service: `mediaPlayback` type, followed only once in the foreground, `onTaskRemoved` with `stopWithTask="false"`.
- Android backup rules are allow-lists of `library/` and `preferences/preferences.json`.
- Manifest aliases and `AppIconSwitcher` names agree; enable-before-disable; `appliedTarget` recorded after a full switch.
- R8/ProGuard: Ktor engines named, Koin compiler-plugin wired, `heldLock` and JBR keeps, packaged JDK modules cover imports.
- Desktop single instance: OS lock, endpoint rewrite, constant-time token, 1 MiB cap, closing wait above quit grace.
- Desktop: macOS open-file/quit handlers, log rotation at line starts (and flushed per write), MAS entitlements, MSIX family name.
- iOS: `onOpenURL` buffered channel, security-scoped reads, inbox handling, background task, alternate icon names, version phase.
- Web launch: page stored last, digest before `cache.put`, failed update falls back, reload guarded per build per tab, `forgetOtherBuilds` guarded.
- Service worker scope, routing and `<base>` agreement, `build.json`/worker never cached.
- `finishWebDistribution` idempotent; OPFS writer journal and metronome timer worker (Node tests: 15 pass).
- Gradle: every `campfire.*` via `project.property`, `local.properties` as UTF-8, `isMacAppStoreBuild` matches CI's task.
- Workflows: secrets via `env:`, backslash doubling, tag/version and build-number checks, sync-key guards, per-workflow permissions, scripts from `workflow_sha`.
- `app_store_signing.py`: certificate matched by modulus, 60-day renewal, 409 handled, shared key never revoked. Python tests: 19 pass.
- First run plants the demo once and shows the welcome once; second launch neither.
- Big import (2 990 songs): in-batch exact duplicates disregarded, different files numbered, setlists re-pointed, German
  keys converted, broken setlist logged, unsupported/empty files reported; report search works.
- Re-import of a library export: everything "already there", snackbar.
- Conflict paths: Cancel, Skip, Keep both, Replace (with confirmation), Escape on the question.
- SIGTERM mid-import: no truncated files, re-import adds only the rest.
- Search, tag/language filters, countdown and duration subtitles; paging a 60-song setlist.
- Metronome: song panel and tab start/stop, tempo override per setlist entry, paging moves the click, feature switch off.
- Library vs setlist overrides kept separately; editor unsaved-changes question, double-click Save saves once; Update file
  name; triple-click Delete deletes once.
- Song defaults validation; Manage tags/links writes; setlist create/archive/read-only song.
- Feature switches and Read only mode; Hungarian; 360×640 and 1500×900 windows; 40 rapid tab clicks and 15 open/back
  pairs consistent.
- Performance: cold start 1.5 s, 1.3–1.5 s with ~3 000–7 000 songs, no ≥ 60 ms stalls scrolling/searching/filtering;
  60-song setlist PDF (98 pages) saved in 2 s.

## Dropped after verification

- Plan 19 (web journal names), dropped by the user's decision D3 after the challenge; its finding (the worker's journal
  names can exceed the file-name limit for the longest names on Safari before 26 / iOS 18) is accepted as a known limit.

- Sub-claim of "Choose songs writes the sheet's whole list…": that the seed's `.distinct()` drops a song a setlist names
  twice. The model never holds a repeated entry (`SetlistMappers.kt` `distinctBy { it.file }`, `SetlistLocalSourceImpl`
  deduplicates on write), so there is nothing to drop. The main finding stands (plan 31).
- No whole finding was dropped. The five "unconfirmed" ones were kept with the evidence found:
  export-saved event (SharedFlow drops an emission with no subscriber; subscriber starts a frame after `shown` is set —
  plan 40); Discard then quit (nothing orders the draft delete before `exitProcess(0)` from `application()` — plan 37);
  search re-open (MutableStateFlow seen at once vs snapshotFlow after the main-thread apply; the stale emission is
  deterministic, its visibility is timing — plan 46); caret after a sheet (the Save respelling path is certain — plan
  45); Mac App Store save (App Sandbox grants only the chosen URL, no `NSIsRelatedItemType` declared — plan 39, fix is
  a fallback that is harmless everywhere else).
- **A new release holds the web launch for the whole download even though a complete kept build is there** — documented,
  deliberate design: `app/web/CLAUDE.md` says "A download gives up after fifteen seconds without a byte rather than after
  a total time", and the root `CLAUDE.md` describes the launch as "another build is downloaded, checked … stored with its
  page last and loaded once", with a determinate progress bar for that download. The "three seconds" are only for the
  `build.json` question. Starting the kept build while the next one downloads in the background would be a design change
  (a running build N writing build N+1 into the cache, `forgetOtherBuilds` gated on it); the user can ask for it, but it
  is not a defect against what is promised.
- Long song text-size steps freeze for 0.5–0.8 s (LayoutBudget): not planned by instruction — recorded as a measurement
  only (428 ms open, 528/763/804 ms per text-size step, 1.5 GB footprint peak on a 2 000-line song).
