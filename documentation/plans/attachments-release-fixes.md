<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: fixes for pictures, attachments, and their sync before the release

**Status:** ready to execute. Written against `76309ad0b` on 2026-11-17, from a stability check of everything since
tag `5.4.0` (mutation testing, a generated crash-point and round-trip harness, a 5.4.0-beside-HEAD trace, live runs on
the Android emulator, in Chrome, and against a real Dropbox folder). It lands **before**
[streaming-archive-import.md](streaming-archive-import.md), whose steps 1 and 6 it carries out (lane M).

The orchestrator is started with *"Follow `documentation/plans/attachments-release-fixes.md`."* Paths: `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, `S/` is
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/`, `zip/` is
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/`, `IRI` is
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/ImageRepositoryImpl.kt`.
Line numbers are those of `76309ad0b`; find the code by the names, since earlier items move lines.

## What was found

| # | Finding | Severity | Item |
|---|---|---|---|
| 1 | Android: saving a second 50 MB attachment dies with `OutOfMemoryError` (whole-file SHA-256 on the main thread, a whole copy in `Sha256.pad`) | crash | M1–M4 |
| 2 | Android: exporting a library holding one 50 MB attachment dies with `OutOfMemoryError` (`ZipWriter` deflates into a doubling buffer) | crash | M5 |
| 3 | A 2 s main-thread stall on every 50 MB attach, the work bar never shown | janky, ANR risk | M2 |
| 4 | Messages sent while a bottom sheet is open are drawn under it (too large, wrong type, disk full, nothing opens this file) | wrong result | U1 |
| 5 | The finished import report says "Nothing was left out." when pictures or attachments were | wrong result | U2 |
| 6 | A song deleted here and revived by a 5.4.0 device's offline edit comes back naming a file that exists nowhere | data loss, narrow | S7 |
| 7 | A shared link's song keeps the lines of attachments its zip leaves out | wrong result | S8 |
| 8 | Restoring a backup with Replace numbers a sibling (`twice_3.cho`) instead of replacing `twice_2.cho` | wrong result | I1 |
| 9 | A song is uploaded before the files it names, so a killed run leaves the folder naming a missing file | low | S1 |
| 10 | A damaged folder copy is downloaded whole again on every run; no last-synced time while any file fails | low | S2, S3 |
| 11 | A deleted song's files stay in the folder until the device syncs for another reason | low | S6 |
| 12 | Android: an attachment picked while the process was killed is dropped without a word | low | U3 |
| 13 | Android: Campfire offers itself for opening its own attachments | low | U4 |
| 14 | A file accepted as "up to 50 MB" is listed as "52 MB" | cosmetic | M8 |
| 15 | A full disk says only "Something went wrong" | low | M7 |
| 16 | Two exports of one name in flight overwrite each other, and discarding one deletes the other | low | M6 |
| 17 | Setlists written by an import are also reported as duplicates; songs are not | cosmetic | U2 |
| 18 | `../x.png` in an archive resolves to the archive's root instead of missing | cosmetic | I2 |
| 19 | Sync log lines say "picture" for an attachment and count a repair upload as a download | cosmetic | S4 |
| 20 | Guards with no test of their own (mutation survivors) | test gap | S5, S8, M5 |
| 21 | Web: the Import picker went dead until reload once, after a pick made while What's New was opening | unconfirmed | U5 |
| 22 | Comments and a `CLAUDE.md` claim versions before pictures skip `images/` unread; 5.4.0 reads attachments there | false doc | S9 |

## Decisions already taken (the owner, 2026-11-17): do not re-ask

- **Finding 6 is fixed by restoring the file from Dropbox's deleted-file history** (S7), not by a grace period: content
  decides in sync, never a clock.
- **Shared links rebuilt in turn by a 5.4.0 device and a current one are left as they are**, only documented (S9). No
  yielding, no legacy fingerprint.
- **A shared copy's song drops the attachment lines of files its zip leaves out** (S8).
- **Identical songs or setlists arriving twice in one batch stay one file** (intended, `importing/CLAUDE.md`); only their
  reporting is made consistent (U2).
- **No migration and no desktop heap cap.** The desktop's 1.5 GB RSS with three 50 MiB files in flight is heap growth,
  not a failure. Sync's and shared copies' whole-file transfers stay (one large file at a time); M4 only removes their
  needless copies.
- **The attachment limit stays 50 MiB in every stored and synced path.** Only what a *picker* takes becomes decimal (M8),
  so no file a library already holds is ever refused.

## Rules for every lane

These repeat what bit earlier sweeps; the lane prompt carries them verbatim.

- Read the root `CLAUDE.md` and the nearest `CLAUDE.md` before touching a package. Invoke the `code-style` skill before
  the first edit of any `.kt`, `.kts`, or `strings.xml`, and the `commit-messages` skill before the first commit: one
  line, one sentence, no trailers, no new branch. Commit each item once it compiles and its tests pass; never push.
- Stage by explicit path, never `git add -A`. Scratch files go to `<scratchpad>/lane-<x>/` only; nothing temporary is
  committed (no generators, dumps, or review aids).
- **At most two Gradle lanes at a time on this machine** (24 GB; the Kotlin daemon runs out of memory). One Gradle
  invocation at a time per lane, the owning module's tests only (`./gradlew :<module>:desktopTest --tests '<class>'`);
  the full suite is the orchestrator's, after each merge. `DesktopSyncAuthenticatorTest` binds port 53682 and flakes
  when two lanes run it at once: rerun it alone before blaming a change.
- Shared code is JVM-free: a JVM-only call in `commonMain` passes `desktopTest` and breaks iOS and the web. The
  orchestrator compiles all four platforms after the last merge (see Finish).
- Tests check behaviour through the public seam, with few fakes; a failing test is fixed, never `@Ignore`d. Every item
  below names its test; "reproduction" tests from the stability check are in
  `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/67e66913-7dba-4e23-9a4e-e59bf8e75a01/scratchpad/`
  (`confirm/`, `harness/`) if that folder still exists; they are aids to read, not files to commit as they are.
- Stored documents (preferences, sync index, shared links) only gain fields, each with an `upgrade-<version>` fixture
  taken from a worktree at the last tag. **No item in this plan needs a stored-format change; if one seems to, stop and
  report instead.**
- Strings live in all eleven `strings.xml` files (`values`, `-hu`, `-ro`, `-de`, `-es`, `-fr`, `-it`, `-he`, `-pt`,
  `-pl`, `-cs`), counted sentences as `<plurals>`, Oxford comma in English lists. Add new strings next to the ones they
  relate to, so two lanes' additions do not land on the same lines.
- Every user-caused change is animated; nothing is fixed by a delay or a wait gate. No UI screenshots or app launches
  per iteration: compile and run the tests; the live checks are the orchestrator's (Finish).
- A `CLAUDE.md` line the change makes false is corrected in the same commit; nothing else in the docs is rewritten.
- If an item's premise turns out false in the code, do not force it: leave it, say why in the lane report, move on.

## Lanes

Four lanes, each in a detached worktree cut from the start commit (`git worktree add --detach ../Campfire-lane-<x>
START`), each a `general-purpose` subagent, items in the order listed. **Run M and S first (two Gradle lanes), then U
and I as they finish.** Merge order: **M, S, I, U** (`git cherry-pick <base>..<lane HEAD>` in the main checkout), the
tests of the touched modules after each, conflicts resolved keeping both sides' intent.

| Lane | Area | Files it owns | Items |
|---|---|---|---|
| M | memory, storage, export | `data/model/.../hashing`, `data/source/remote/api/.../hashing`, `IRI`, `data/formats/.../zip`, `data/source/local/implementation` (storage, export files, not `ArchiveImagePaths`), `domain/.../export/ExportedArchive.kt` and `CreateExportFileUseCaseImpl.kt`, the platform pickers' read code, `S/run/ImageTransfers.kt` **only for M4's read trims**, `DropboxDownloads.kt`, `ui/components/FormattedSize.kt`, `ImportLimits.kt` | M1–M8 |
| S | sync, shared copies | `S/` (after M4 merged for `ImageTransfers.kt`: see below), `data/source/remote` (`SyncFolder`, Dropbox provider), `libs/chordpro/.../edit/ChordProAttachments.kt`, `domain/.../export/ExportedImages.kt` and `CreateShareLinkUseCaseImpl.kt` | S1–S9 |
| I | import planning | `domain/.../importing/ImportPlanner.kt`, `ArchiveImagePaths.kt` | I1–I2 |
| U | messages, sheets, Android intake | `ui/messages`, `ui/dialogs/sheet`, `ui/screens/importReport`, `ui/state/importing/ImportOutcome.kt`, `data/model/.../importing/ImportResult.kt`, `domain/.../importing/ImportFilesUseCaseImpl.kt` (`result()` only), `presentation/src/androidMain`, `app/android` manifest, `presentation/src/wasmJsMain` picker | U1–U5 |

Files two lanes touch, and how:

- `S/run/ImageTransfers.kt`: lane M trims reads in `bringDown` and `sendUp` (M4); lane S changes failure memory and log
  text (S2, S4). **Lane S starts its `ImageTransfers.kt` items (S2, S4) only after M4 is merged**: the orchestrator
  tells lane S when, and lane S then cherry-picks nothing itself but rebases its worktree onto the main checkout's
  `HEAD` (`git rebase --onto <main HEAD> <old base>` in the detached worktree) before S2. S1, S5–S9 do not wait.
- `strings.xml` (all eleven): M7, U1–U3 add strings. Different neighbourhoods; a cherry-pick conflict keeps both.
- `ui/messages/Messages.kt`: U1 splits it; M7 adds one message mapping. Lane M adds the `Message.StorageFull` case and
  its text branch and nothing else there; lane U merges last and keeps it.

---

## Lane M: memory, storage, export

The two crashes share one cause: a library file is held whole, copied whole again to hash it, on the main thread, and
deflated into a third buffer. The lane removes the copies in the order that makes each step testable.

### M1. Incremental SHA-256 in `:data:model` (streaming plan step 1)

Carry out step 1 of the streaming plan exactly: move `Sha256` from
`data/source/remote/api/.../hashing/Sha256.kt` to `:data:model` (`data/model/.../hashing/`) verbatim in a commit of its
own with the imports updated, then add `Sha256.Digest` (`update(bytes, offset, length)`, `finish()`, `finishToHex()`).

- `Digest` holds `IntArray(8)` state, a 64-byte block buffer, its fill, and the total length as a `Long`. `update`
  compresses whole 64-byte blocks **straight from the caller's array at an offset** and buffers only the remainder;
  `finish` pads in a 64 or 128-byte tail. Re-implement `Sha256.hash(bytes)` as one digest over the array, which deletes
  `pad()` and its whole-input copy (`Sha256.kt:91–101`).
- `Digest` is not `suspend`. Callers hashing more than 256 KiB loop in 256 KiB chunks with `yield()` between them, on a
  computation dispatcher: add `suspend fun Sha256.hashToHexYielding(bytes)` (or the name the code style prefers) beside
  it for that, and use it in `LibraryImages.contentNames` and `localContentHash`.
- `dropboxContentHash` (`data/source/remote/implementation/.../crypto/ContentHash.kt:21–32`) hashes each 4 MB block
  through a `Digest` over offsets instead of `copyOfRange`.
- Tests (`:data:model` `commonTest`): the NIST vectors (empty, `abc`, the 448-bit and 896-bit messages), any chunking
  equal to the one-shot hash (random boundaries from a fixed seed; inputs of 55, 56, 63, 64, 65, and 119–129 bytes), a
  1,000,000 × `a` vector. Every existing sync and repository test passes untouched: content names and fingerprints must
  not change by one bit.
- Pitfall: the move changes imports in `:data:source:remote:api`, sync, repository, and domain. Check that
  `:data:model` may be seen from every one of them (it is the bottom of the graph) and that no `explicitApi()` module
  re-exports the old package.
- In the streaming plan, mark step 1 as landed by this item (one line under its heading).

### M2. The repository never hashes on its caller's thread

`IRI.store` (`:218–231`) runs `examine` on `environment.computation` but `LibraryImages.contentNames(bytes, …)` (`:223`)
and `otherBytesUnder`'s `contentEquals` (`:239–241`) on the caller's dispatcher, which is `Dispatchers.Main.immediate`
for the editor's Attach, the Attachments sheet, and Save (`viewModelScope`). That is the 2 s stall and the reason the
400 ms work bar (`ui/pictures/PictureWork.kt:38–44`) cannot appear: its `delay` cannot resume while the main thread
hashes.

- Wrap examine, the content names, and the comparison in **one** `withContext(environment.computation)` inside `store`,
  keeping the critical section (`keeping`) exactly as it is. Do the same wherever `IRI` hashes or compares file-sized
  arrays (`nameFor` `:128`, `libraryNameOf*`, `moveDraftImages` until M3 replaces it).
- `otherBytesUnder` compares sizes first and only then bytes.
- Track Save's file move in `PictureWork` (it is not tracked today: `EditorSession.kt:391–394`, and
  `SaveSongAttachmentsUseCaseImpl`'s caller), so a slow Save shows the bar.
- Test (`data/repository/implementation` `commonTest`): with a `StandardTestDispatcher` standing in for the main
  dispatcher and a distinct computation dispatcher in the environment, storing a file runs no hashing on the caller's
  dispatcher (assert through a `Sha256`-free seam: a dispatcher that fails the test when a block of more than a few
  milliseconds of work is dispatched to it is too clever; instead inject the content-naming function the repository
  already takes or can take, and assert the thread/dispatcher it was called on). A UI test in `:tools:screenshots` is not
  needed.
- Pitfall: `store` is called under `withContext(NonCancellable)` (`:137`); `withContext(computation)` inside it stays
  non-cancellable, which is intended.

### M3. Save moves a draft file instead of reading, hashing, and writing it again (streaming plan step 6)

Today Save (`SaveWithDraftImagesUseCaseImpl.kt:16–31`, `SaveSongAttachmentsUseCaseImpl.kt:19–33` →
`IRI.moveDraftImages` `:193–204`) reads every draft file whole, re-examines and re-hashes it, reads a same-named
library file whole to compare, and writes a second copy. `FileStorage` has no move and no random read.

1. **Carry out step 6 of the streaming plan as amended there** (`StorageDirectory.IMPORTS`, `openSource`, `moveInto`,
   `availableSpace`, the contract tests on desktop and the iOS simulator, the web's `wasmJsTest` contract test and its CI
   step, the worker's `stage` request). Mark step 6 in that plan as landed by this item.
2. Rewrite `moveDraftImages` on top of it. A draft name is already the content name computed at intake:
   - verify it by streaming a `Digest` over `openSource` in 256 KiB chunks (`isLibraryFileNamedFor`), never a whole read;
   - the library holds that name with the same size and the same streamed hash: nothing to write;
   - the library holds it with other bytes: take the next content name, as `store` does;
   - otherwise put the file into `library/files` **without holding it whole**.
3. **Pitfall, decides the shape of step 2: the draft copy must stay while an editor is open.** `loadImage` falls back to
   the draft shelf (`IRI:105`), `clearDraftImages` (`:161–166`) tidies it only when the last editor closes, and the
   editor's Undo can bring back a line whose library file a later Save pruned. So Save **copies** by streaming (source
   → a sink under a temporary name in the destination's volume → `moveInto` the final name), and the draft copy is left
   for the tidy. Write the test first: add a picture, Save, remove its line, Save (the library prunes it), Undo, Save
   again: the picture is in the library and shown. Only if that test passes with a plain `moveInto` of the draft file
   may the copy be replaced by a move.
4. iOS: `DRAFT_IMAGES` is under Application Support and `FILES` under Documents (`IosFileStorage.kt:37–44`), one
   container, so a rename works; a file written into the draft shelf carries the backup exclusion
   (`ImageLocalSourceImpl.writeImage:40`). A **picture** arriving in `library/files` must have it cleared (library
   pictures belong in the iOS backup), an **attachment** keeps it. Cover both in `FileStorageContractTest` on the
   simulator or in a test of `ImageLocalSource`.
5. Web: no rename exists; one file at a time is read and written with the existing atomic `writeBytes`.
6. Tests: every existing `ImageRepositoryImplTest` and `SaveWithDraftImagesUseCaseImplTest` case unchanged; a 50 MiB
   draft file saved under a JVM capped well below three copies of it (see M5's test task) completes.

### M4. One copy, not four, in the pickers and in sync's transfers

- Android `readAtMost` (`presentation/src/androidMain/.../ui/platform/FilePicker.android.kt:462–471`): with a known
  size, allocate `ByteArray(size)` and read fully, then read one more byte to detect a longer stream; with an unknown
  size, collect 1 MiB chunks in a list and join once. No doubling `ByteArrayOutputStream` (114 MB peak for 50 MB today).
- Web picker (`FilePicker.wasmJs.kt:62–79, 227–232`): cross into Wasm with the chunked Latin-1 bridge of
  `OpfsBytes.kt:35–63`, not `Int8Array.toByteArray()` (one interop call per byte). Share the function (move it where
  both source sets see it) rather than duplicating it.
- `DropboxDownloads.boundedBody` (`:108–112`): when the size is declared, read into `ByteArray(declared)` with
  `readFully` and check for the end of the body; keep the bounded buffer only for an undeclared size.
- `ImageTransfers.bringDown` (`S/run/ImageTransfers.kt:102–135`): do not hold `local` across the download, and do not
  read `current` whole again under the lock (`:118`): re-check existence and size, and hash only when they differ from
  what was listed. `sendUp` (`:141–142`) hashes with the yielding digest. **Every check that exists today must still
  hold**: the mutation run showed tests kill "integrity check dropped" (S10), "damaged local copy uploaded" (S11), and
  "re-check dropped" (S07); run `ImageSyncTest`, `SyncPlannerImageTest`, and `SyncSimulationTest` after each edit.
- No streaming of sync transfers, no API change to `SyncFolder` here.

### M5. `ZipWriter` never deflates a large entry into a growing buffer

`zip/ZipWriter.kt:40–118` deflates each whole entry into `deflated` (`ByteArrayBuilder`, doubling), and only then
decides stored or deflated (`:63`). A 50 MiB MP3 costs 50 (entry) + 32 → 64 (builder growth) MiB.

- For an entry over 1 MiB: deflate its first 64 KiB as a trial. If that does not shrink by at least 3%, write the entry
  **stored**, without deflating it. If it shrinks, deflate the whole entry through a `DeflateOutput` that **gives up as
  soon as the output reaches the input's size** (then store), into a builder pre-sized to the input so it never
  doubles past it.
- After any entry over 1 MiB, replace `deflated` (and let go of `Deflater.input`, `zip/Deflater.kt:26`) so the export
  does not keep its peak capacity until the end.
- Write the data to the sink in slices of at most 1 MiB (`:82` writes one call per entry; `OpfsFileSink.write`
  converts each call whole into a `Uint8Array`).
- **Entries of 1 MiB or less are written exactly as today, bit for bit**: the streaming plan's step 4 records the
  writer's output hashes for its existing tests at its start, and shared copies (`SharedCopySync`) hash their zip. A
  large entry may change method; a shared copy holding one rebuilds once, which the next build number causes anyway
  (`SharedCopySync.kt:313`).
- **Never write a stored entry with a data descriptor** (Java's `ZipInputStream` refuses it); this design needs no
  descriptor at all, since CRC and sizes are known before the header.
- Also fix the latent off-by-one the pre-mortem found: `checkedOffset` refuses with `>` where the 32-bit field's marker
  value itself (`0xFFFFFFFF`) must already be refused (`ZipWriter.kt:120`). Test it with a fake `ZipSink` whose
  `position` starts just below the limit (mutation survivor F04).
- Tests (`:data:formats` `commonTest`): a small-entry archive byte-identical to one recorded before the change;
  incompressible 2 MiB entry stored, compressible 2 MiB entry deflated, an entry whose head shrinks but whose whole does
  not ends stored; every archive read back by `ZipReader` and by `java.util.zip` in the JVM test; `ZipFuzzTest` and
  `ZipRoundTripTest` unchanged.
- **The memory proof**: a Gradle test task of its own in `:data:source:local:implementation` (or where the export's
  real sources are wired) with `maxHeapSize = "128m"`: a library with two 50 MiB incompressible attachments is exported
  through `ArchiveLocalSourceImpl.pack` into a file sink, and a 50 MiB draft is saved (M3). The streaming plan's step 16
  reuses this task. Add it to `desktopTest`'s dependencies so CI and the pre-commit hook run it, unless it takes more
  than about 30 seconds: then make it part of `.github/workflows/tests.yml` only.

### M6. Exports in flight never share a file

`ExportFileLocalSourceImpl.create(name)` (`:65–68`) replaces an existing file of that name and `discard` (`:85–92`)
deletes by `file.name`. Make `exportedArchive` (`domain/.../export/ExportedArchive.kt:31`) and
`CreateExportFileUseCaseImpl.kt:21` use `createUnique` (`:70–73`, already under the `naming` mutex), and have `discard`
delete the **stored** name (carry it in `ExportedContent.TemporaryFile`, or take the path's last segment), while
`ExportedFile.name` stays the name save dialogs and share sheets show. Test: two exports of one name held at once keep
their own bytes; discarding the first leaves the second (`ExportFileLocalSource`'s test, and the use case tests).
Pitfall: platforms that share the temp file directly would show `name_2.zip` in that rare case; that is accepted.

### M7. A full disk says so

No path tells out-of-space apart: the JVM wraps the `IOException` (`JvmFileStorage.kt:280–296`, `JvmFileSink.kt:54–55`),
iOS drops the `NSError` (`FoundationFileStorage.kt:143–145`), the web wraps a message naming `QuotaExceededError`
(`OpfsFileStorage.kt:247–259`, `OpfsFileSink.kt:58`).

- `:data:model`: `OutOfSpaceException` and `Throwable.isOutOfSpace()` walking the cause chain. Set it as the cause at
  each wrap point: JVM and Android by `ENOSPC` (`ErrnoException` on Android) or the platform's message ("No space left
  on device", Windows "There is not enough space on the disk"); iOS by using `writeToFile:options:error:` and checking
  `NSFileWriteOutOfSpaceError` or POSIX `ENOSPC`, and `errno == ENOSPC` in `PosixFileSink.kt:68`; the web by
  `QuotaExceededError`.
- Presentation: one `Message.StorageFull` ("There is not enough free space on this device." in every language), chosen
  instead of `OperationFailed` / `SaveFailed` in `MessageSink.launchLibraryChange` (`ui/messages/MessageSink.kt:51–61`),
  `AttachmentIntake.kt:47–49`, the editor's save failure (`EditorSession.kt:385`), and `ExportSaver`. Sync's
  `toFailureReason` (`S/scheduling/SyncRunner.kt:363–367`) gains a local-full reason with its own sentence.
- The streaming plan's `ImportFailure.NO_SPACE` (its step 13) reuses the predicate; say so there in one line.
- Tests: a `FileStorage` fake throwing the exception through each path yields the message; the JVM mapping by a real
  `IOException("No space left on device")`.
- Pitfall: the Attachments sheet closes on a failed Save and loses the picks (seen live). Keep the sheet open when the
  write fails, with the message shown in it (lane U's U1 makes it visible).

### M8. What the pickers call 50 MB is 50 MB

`formattedSize` counts in 1000s on purpose (`ui/components/FormattedSize.kt:64`), the limits are MiB
(`ImportLimits.kt:57`), the strings say "50 MB". Add `MAX_PICKED_ATTACHMENT_SIZE = 50_000_000` beside
`MAX_ATTACHMENT_FILE_SIZE` and use it **only** where a file the user picks or drops as a new attachment is taken
(`ui/platform/PickedAttachment.kt:29–35` and the size check its refusal message comes from). Sync
(`SyncFileLimits`), archive reads, shared copies, and `IRI.store` keep the MiB limit, so a file between 50,000,000 and
52,428,800 bytes that a library already holds stays accepted everywhere. Tests: 50,000,000 bytes picked is taken,
50,000,001 refused with the existing sentence; a 52,428,800-byte file still syncs and imports (existing tests).
Pictures need nothing: one over its limit is shrunk, not refused.

### Lane M's last step: the retained 50 MB (time-boxed, 45 minutes)

The live run saw about 50 MB still reachable minutes after a Save of a 50 MB attachment (heap 19 → 68 MB). Reading
found no field holding it; the candidates are a sync run's upload body, a suspended coroutine frame, or uncollected
large objects. After M1–M5, on the Medium_Phone emulator (recipe in the memory files `android-emulator-verification.md`
and `smallest-supported-screen.md`; the debug build; never the owner's emulator): attach a 50 MB file, Save, wait 30
s, `am dumpheap`, and find the path to GC roots of any `byte[]` of that size (`hprof-conv`, then a short script or
`jhat`-like tool in the scratchpad). Sync is not connected in that build, so an array still reachable is the app's: fix
it at the holder. Nothing reachable: record that in the lane report and stop.

---

## Lane S: sync and shared copies

Read `data/sync/implementation/CLAUDE.md` and `run/CLAUDE.md` first. The sync simulation (`SyncSimulationTest`,
`commonTest/.../SyncSimulation.kt`) runs after every item; a change that needs its expectations changed is described in
the lane report.

### S1. A song's files are uploaded before the song

`S/run/SyncEngine.kt:518–526` orders Resolve 0, Download 1, Upload 2, DeleteLocal 3, DeleteRemote 4, Forget 5; within
group 2 a song and its files race (`:258–267`, six at a time, large files one at a time through `largeFilePermit`). Give
an Upload of a `FILE` key its own group before the other uploads. Pitfalls: a failed file upload must not hold back the
song's upload (the group completes, failures recorded, as today); a 50 MB file now delays the songs of that run, which
is the point; the fallback upload in `ImageTransfers.deleteLocally` (`:66`) still comes later and stays. Test
(`ImageSyncTest`): a run killed after the first group (use the simulation's fault hooks, `SyncSimulation.kt:73`) never
leaves the folder holding a song that names a file the uploading device held and the folder lacks.

### S2. A damaged folder copy is not downloaded again while it has not changed (after M4 is merged)

`ImageTransfers.kt:115–116` downloads, finds the copy is not what its name says, and `failed()` (`:161–164`) forgets
that, so every run and every change-feed wake downloads the whole file again. Remember, **in memory** for the life of
the process, the `(name, contentHash)` pairs found damaged; skip the download while the listing shows that same content
hash, still reporting the file as failed; forget an entry when the listing shows another hash or the name is gone. No
index field (no stored-format change). Test (`ImageSyncTest`): three runs against an unchanged damaged copy download it
once; a repaired copy is downloaded on the next run.

### S3. A last-synced time while a file fails

`S/scheduling/SyncRunner.kt:186–193` records `lastSyncedAt` only when `summary.isComplete` (no failed file), so a
device with one damaged file in its folder shows no last-synced time, ever. Record the time whenever a run **completed**
(it was not stopped, interrupted, or failed as a whole), whatever its per-file failures; the failed files keep being
listed beside it (`SyncSettings.kt:253–294`). Leave `isComplete`'s other uses as they are. Test: a completed run with a
failed file sets the time; a stopped or failed run does not.

### S4. Log lines that say what happened (after M4 is merged)

- `ImageTransfers.kt:93, 116, 143`: "picture" → "file" (it is an attachment as often).
- `S/run/SyncPlanDescription.kt:21` counts planned downloads before `bringDown` may turn one into a repair upload
  (`ImageTransfers.kt:109–113`). Keep the plan line, and make the run's closing line (`SyncSummary.uploaded` /
  `downloaded`) the one that counts what was done; check that it already does, and only fix the wording if not.

### S5. Tests for the guards that have none (mutation survivors)

Each was shown to survive a planted bug; write the test, then plant the bug once to see it fail, then revert.

- **The editor's held names** (`ImageTransfers.kt:61`, `namedNow()?.plus(held)`): the existing test "a picture only the
  editor's unsaved text names is kept by sync" never reaches the guard because its second device runs once. New
  `ImageSyncTest` case: B deletes the song and the picture and runs **twice** (so the picture leaves the folder), A's
  editor holds the picture (`Device.heldPictures`), A runs: the picture stays on A and goes back up.
- **Repair of a damaged folder copy** (`:109`): a file present on both sides, unknown to the index, the folder's copy
  with other bytes: the intact local copy is uploaded over it. (Live: it works.)
- **An unreadable song keeps every pending deletion** (`:61`, `namedNow` null) and **the listing reader yields
  `imageReferences = null`** for an unreadable or oversized song (`LocalListingReader`, `isEverySongRead`): a song made
  unreadable after the listing; and an unreadable song in the listing plans no file deletion on either side.
- **Changed since the listing** (`:73`): a local file whose bytes change between the listing and the deletion pass is
  sent back up, not deleted.
- **The planner's own rule** (`SyncPlanner.kt` `withImagesKept`): the folder lacks a file the index knows and a local
  song names → `Upload` (`SyncPlannerImageTest`).

### S6. A deleted song's files leave the folder without waiting for another reason to sync

`S/run/SyncPlanner.kt:86–112` holds back `DeleteRemote` of files while the song set changes (`mayDelete`, `:93`), and
nothing schedules the run that would then delete them. Let the plan report that it held deletions back, carry that
through `Result.Completed`, and have `SyncRunner` ask the scheduler for **one** follow-up run after
`AUTOMATIC_RUN_DELAY`. Loop guard: a follow-up run never asks for another (a flag through `startRun`), and none is asked
after a stopped run or one waiting for an answer about deletions. Test (`SyncRunner`'s or the scheduler's tests): a run
that deleted a song is followed by exactly one more, which deletes its files; that one is followed by none.

### S7. A file no device holds any more is restored from the folder's history

The confirmed loss: device A deletes song S, the only song naming file P; A's later run deletes P from the folder; a
5.4.0 device (which knows nothing of files) then uploads its offline edit of S. S comes back everywhere naming P, which
no device and no folder holds. The reproduction is `confirm/ConfirmPictureLossTest.kt` in the scratchpad.

- `SyncFolder` (`data/source/remote/api/.../SyncFolder.kt:39–172`) gains `restoreDeleted(kind, name): Boolean` ("bring
  back the last content this folder held under that name; false when it holds none"). Dropbox: `files/list_revisions`
  (mode `path`) on `/files/<name>`, take the newest revision that is a file, `files/restore` it. Pitfalls from Dropbox's
  own answers: restoring the top revision of a deleted file can answer `invalid_revision` while the one below works, so
  try the revisions in order, at most three; `missing_scope` or any other refusal is "false", logged once, never a
  failed run (`DropboxErrors.kt:25–35` shows how `missing_scope` is recognised). The app console grants
  `files.content.read` and `files.content.write` (`data/source/remote/implementation/CLAUDE.md:32–33`); whether an
  app-folder token may list revisions of a deleted path is the one thing only a live call shows (Finish).
- The planner already has what it needs: `imageReferences` and both listings' `FILE` keys (`SyncEngine.kt:156`). In
  `plan()`, the names in `kept` with no local key, no remote key, and no index entry are **missing everywhere**; report
  them beside the operations (they are not operations: no key exists for them).
- The engine asks `restoreDeleted` for each (bounded: at most 20 a run, large ones one at a time), and when any was
  restored asks for the same single follow-up run as S6, which downloads them through the ordinary path, whose integrity
  check (`isIntact`) is what guards against a wrong revision: a restored copy that is not what its name says is a
  damaged copy like any other (S2), and is not asked for again in this process.
- A song downloaded in this pass enters `imageReferences` only on the next run, so the restore happens on the run
  after the song arrives; S6's follow-up flag must also be raised when a pass downloaded or resolved a song (one run,
  same loop guard).
- The test fakes of `SyncFolder` (the simulation's cloud included) get a deleted-file history, so the simulation models
  it: a file deleted from the folder can be restored for the rest of the run of seeds.
- Tests: the scratchpad reproduction, now ending with P on A and in the folder; a name with no history changes nothing
  and is asked once per process; a restore that brings back wrong bytes is treated as damaged; the control (a current
  device holding P) still passes.
- `run/CLAUDE.md` (the lines near 55 that state the loss as a limit) is rewritten in this commit: what is restored, from
  where, for how long (the provider's history, about 30 days on Dropbox), and that a file older than that stays lost.
- The Google Drive plan's provider, when it comes, answers `false` until it implements the same.

### S8. A shared copy carries no line for an attachment it leaves out

- `libs/chordpro/.../edit/ChordProAttachments.kt`: add `withoutAttachments(text, sources)`, removing every line
  `referencedSources` (`:31`) reads for those sources, commented-out ones included (`setAttachments` `:41` leaves those).
  `explicitApi()` applies. While there, pin the dedup with a test: text declaring one attachment on two lines keeps
  exactly one after `setAttachments` (mutation survivor C07; eight shapes are in `confirm/ConfirmDuplicateAttachmentsTest.kt`).
- Sync: `S/shared/SharedCopySync.kt` packs raw texts (`:230, 235`) and decides the budget after (`:248, 262`); strip the
  left-out sources from the texts before `contents.pack` (`:277`).
- Export's first copy: `domain/.../export/ExportedImages.kt:60–64` returns the text unchanged and decides lazily after
  the songs were written (`:84–92`). The decision must come **before** a song's text is written: decide from the songs'
  texts and `loadLibraryFileSizes()` first, then write stripped texts. This applies to shared copies only
  (`isSharedCopy`, `CreateShareLinkUseCaseImpl.kt:51–52`); a library, song, or setlist export keeps every line.
- **The two must produce the same bytes** (`SharedCopySync.kt:228–229`), or every share uploads once more after its
  creation: one function decides and strips for both. The fingerprint needs no change (`inputs`, `:270–276`, already
  covers what is read and what is left out).
- Tests: `SharedCopySyncTest` (`:84, 95, 393, 415`) and the export use case tests: the copy's song has no line for a
  left-out file and keeps the lines of the carried ones; the copy made by export equals the copy sync would build.

### S9. Documentation of what stays

- `domain/.../export/ExportedImages.kt:28` and `domain/.../export/CLAUDE.md:19` say versions before pictures skip
  `images/` unread. True for pictures, audio, and video; **5.4.0 reads `.pdf` and `.docx` there as documents, `.txt` as
  songs, and `.abc` as tunes**, so its review may offer songs made from attachments (nothing is written without Import).
  Say that. `ImportLimits.kt:91–92` gains the same half-sentence about attachments.
- `S/shared/CLAUDE.md`: a 5.4.0 device and a current one rebuild a shared copy whose songs name files in turn (the two
  fingerprint it differently; 5.4.0's copy has no files), one upload per run of each until both are current. Decided:
  left as it is.
- The stored-format rule's letter: the shared links document gained `leftOutAttachments` without an `upgrade-5.4.0`
  shared-links fixture (reading the older document is covered by `SharedLinksUpgradeFrom520Test`). Add the fixture from
  a worktree at tag `5.4.0` and a test reading it, as the root `CLAUDE.md` asks.

---

## Lane I: import planning

### I1. A backup restores onto the files it came from

Stress run: the library holds `twice.cho` and `twice_2.cho` (one header, different texts), both edited after an
export; importing the export with Replace restores `twice.cho`, leaves `twice_2.cho` edited, and writes `twice_3.cho`.
`ImportPlanner.planSongs` (`domain/.../importing/ImportPlanner.kt:97–167`) names both arrivals `twice.cho` by their
header, `takenFileName` is always the unnumbered file (`:358`), and `hasConflict` is one Boolean per family (`:381`).

- When an arrival's own file name (`sourceFileName`) is a numbered sibling of its header name
  (`withoutCollisionSuffix(identityKey(base(source))) == identityKey(base(fileName))`, `LibraryFiles.kt:332, 391`) **and
  the library lists that very name** (`isSameLibraryName`): equal text → IDENTICAL to it; different text → CONFLICTING
  against it with `replacesFileName` set to it (or NEW when it is in `kept`). The write already honours
  `replacesFileName` (`ImportFilesUseCaseImpl.kt:146–163`); Keep both still numbers afresh.
- `SongFamily.hasConflict` becomes the set of library names already asked about, so each library file is asked about
  once.
- Nothing is renamed: the rule "a song is named by its own header; renames only when asked or lossless" holds.
- Tests (`ImportPlannerTest`, beside `:169`, `:354`): the stress case ends with both files restored and no third; an
  arrival named `x_2.cho` whose header name the library holds only unnumbered is planned as today; equal text is
  identical. Expectations near `:190–240` (setlists pointing at numbered songs) may change to IDENTICAL: check each
  against the rule, do not bend the rule to them.
- Accepted risk, say it in the KDoc: a loose file a user named `x_2.cho` that differs from the library's `x_2.cho` now
  asks Replace instead of arriving as a new numbered file.
- Update `importing/CLAUDE.md` ("one conflict per library name") and the planner's KDoc (`:21–30`).
- **Land this before the streaming plan's step 11** (the differential test compares plans; both paths share the
  planner, so it is unaffected as long as this is in first).

### I2. `..` above an archive's root is no file

`ArchiveImagePaths` (`data/source/local/implementation/.../ArchiveImagePaths.kt`, `normalized`): a song naming
`../x.png` from the archive's root resolves to `x.png` there. It cannot leave the archive, but it should be missing.
Refuse a path that climbs above the root; test `resolve("song.cho", "../x.png")` is null and
`resolve("songs/a.cho", "../images/x.png")` still resolves (mutation survivor L03).

---

## Lane U: messages, sheets, and Android intake

### U1. A message is shown where the user is looking

`ui/messages/Messages.kt:116–164` hosts one `SnackbarHost` at the root (`ui/CampfireOverlays.kt:76–82`); every bottom
sheet is M3 `ModalBottomSheet` in a window of its own (`ui/dialogs/sheet/CampfireBottomSheet.kt:132`), so a snackbar
sent while a sheet is open plays out under it. Senders reachable with a sheet open include the Attachments sheet's
refusals, About the song's "nothing opens this file" and link failures, the Playing sheet, the setlist picker, and
every background sender (import results, sync, shares).

- Split `Messages` into the **driver** (the queue's `LaunchedEffect`, the actions, one hoisted `SnackbarHostState`,
  composed once at the root as today) and a **presenter** (`SnackbarHost(hostState)`).
- A registry of open sheets (a `CompositionLocal` holding a small state list; `CampfireBottomSheet` registers in a
  `DisposableEffect`). The root presents while the registry is empty; otherwise only the **topmost** sheet presents,
  in a `Box` over its content column, above the bottom content padding and inside the `imePadding` column, outside the
  scrolling content. Nothing joins the sheet's pinned height (the 330 dp budget is untouched); in a short window
  (`SHORT_WINDOW_HEIGHT`) it still overlays rather than pushing.
- A sheet stops presenting when it starts closing (`LocalIsSheetClosing`), so a message sent as a sheet closes (the
  metadata sheets write as they close) appears at the root. A snackbar already showing is not replayed when the
  presenter changes: the host state is one, and only the place that draws it moves. If that makes M3 run its enter
  animation again, keep a showing snackbar where it started until it ends.
- Each message is shown exactly once; `MessageSinkTest` passes unchanged; actions (import Details, the clipboard link
  offer) still work from inside a sheet.
- Tests: a UI test in `:tools:screenshots` (see `tools/screenshots/CLAUDE.md`; the repo has none for messages yet):
  with the Attachments sheet open, a refusal's text is on screen in the sheet's window; with two sheets stacked
  (Attachments over About the song) only the top one shows it; with none, the root shows it.
- With M7 merged first, the Attachments sheet stays open on a failed write and shows the reason.

### U2. The finished import report agrees with the review

The review row counts left-out pictures and attachments from `entry.images` with `name == null`
(`ui/screens/importReport/…/ImportReviewSections.kt:115, 117`, plurals `import_review_images_left_out` /
`import_review_attachments_left_out`), but `ImportResult` (`data/model/.../importing/ImportResult.kt`) has no field for
them, `ImportFilesUseCaseImpl.result()` (`:283–297`) drops them, and both `isClean`
(`ui/screens/importReport/finished/ImportResultItems.kt:99–101`, `ui/state/importing/ImportOutcome.kt:38–40`) ignore
them. So the report says "Nothing was left out." and the snackbar counts only songs and setlists.

- `ImportResult` gains the songs written (or found identical) whose files were left out, each with its count of
  pictures and of attachments. Filled in `ImportFilesUseCaseImpl` from the same `entry.images`. `ImportResult` is not a
  stored document.
- Both `isClean` count it; the report (`finished/ImportReportSections.kt`) gains a section listing those songs with the
  review's plural as their subtitle; the snackbar's counts (`Messages.kt`, `importCounts`) gain a line. Reuse the
  existing plurals; add a section heading in every language.
- Whatever follows renames in the result (`followingLibraryFileNames`) follows the new list too.
- No reason sentence about "the size one import can take": the streaming plan removes that budget; what remains
  (missing in the archive, too large for a song, unreadable) the review already tells per song.
- In the same function, **setlists**: `result()` subtracts written songs from `duplicateFileNames` (`:294`) but not
  written setlists (`:250`), so a setlist is reported both as imported and as a duplicate, against `ImportResult`'s
  KDoc (`:20–24`). Subtract them as songs are.
- Tests: `ImportFilesUseCaseImplTest`, `ImportOutcomeTest`, `ImportReportSectionsTest`.

### U3. Android: an attachment picked while the process was gone is kept

`presentation/src/androidMain/.../ui/platform/FilePicker.android.kt`: a picture picked for a view model that is gone
goes to `_orphanedPictures` (`:319–326`) and lands in the restored editor or the missing picture it was for
(`androidMain/.../ui/OrphanedPicturesEffect.kt`, `ui/pictures/OrphanedPicturesPlace.kt`, `PendingMissingImagePick`); an
attachment is logged and dropped (`:327–331`, and `pickAttachment`'s KDoc `:180–183` says so on purpose).

- Read the URI at once in `onAttachmentPicked` (its grant lasts only for this Activity) with the code `pickAttachment`
  uses, into an orphaned-attachments channel.
- Extend the place decision: an open Attachments sheet (`DialogType.SongAttachments` in `dialogHost.openDialogs`,
  restored by `SavedDialog.kt:93`) takes it through `SongAttachmentEditing.pick(target)`; else an editor on top takes
  it through `EditorImages.attach`; else, and in read only mode, it is let go **with a message** asking to pick the
  file again.
- Pitfall: the restored sheet may not be in `openDialogs` yet when the orphan arrives; the channel is buffered and the
  effect must wait for the saved dialogs' restoration before deciding, never a delay.
- Tests: extend `OrphanedPicturesPlaceTest`. Correct `presentation/src/androidMain/CLAUDE.md:32–37` (it says the Image
  button is that screen's only picker).

### U4. Android: Campfire does not offer itself for its own attachments

`FileOpener.android.kt` starts a plain `ACTION_VIEW`; the `.CampfireIntentActivity` alias matches octet-stream, plain
text, `.abc`, and pictures (`AndroidManifest.xml:110–221`), so the chooser lists Campfire for `.gp5`, `.txt`, `.abc`,
and pictures, and choosing it says "Nothing to import".

- Query the activities for the intent, drop this package's; none left → the existing "nothing opens this" message; one
  → set it as the component; several → `Intent.createChooser` with `EXTRA_EXCLUDE_COMPONENTS` naming the **alias**
  component (`<package>.CampfireIntentActivity`), not the main activity.
- Pitfall: Android 11's package visibility. Without a `<queries>` entry for `ACTION_VIEW` the query returns almost
  nothing and every attachment would read as unopenable. Add the `<queries>` intent the manifest needs (check for an
  existing one first), and keep the plain `startActivity` as the fallback when the query is empty, so the fix can only
  remove Campfire from a list, never an app.
- iOS, desktop, and the web need nothing (Quick Look, the system's default app, the browser).
- Verified on the emulator at Finish; no unit test can show a chooser.

### U5. Web: the Import picker cannot go dead (time-boxed, 45 minutes)

Seen once in Chrome: Import was pressed while What's New was logged open but not drawn; the log shows "Import started"
and "Import finished" at once, and afterwards every Import pick did nothing until a reload. The strongest candidate:
`ExportController.launchFileTransfer` (`ui/…/ExportController.kt:144–152`) ignores presses while `fileTransferJob` is
active, and the web's `openFileInput` promise (`presentation/src/wasmJsMain/.../FilePicker.wasmJs.kt:179–205`) never
settles when `input.click()` is refused for want of a user activation (no `change`, `cancel`, or focus event follows).

- Try to reproduce in Chrome with the recipe in the memory file `web-target-verification.md`. Reproduced: fix the cause.
- Either way, make the promise unable to hang: when `navigator.userActivation` exists and is not active at the click,
  settle with nothing picked at once. No timer. Node or wasm test where the picker's JavaScript allows one.
- Not reproduced and nothing found: say so in the lane report; the guard stays.

---

## Finish (the orchestrator)

1. After the last merge: `./gradlew spotlessCheck desktopTest`, `./gradlew -p gradle :build-logic:test`,
   `./gradlew :tools:screenshots:test`, the iOS simulator storage tests, the web's storage contract test, then **compile
   every platform** (`:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
   `:app:web:wasmJsBrowserDistribution` on its own if a combined build runs out of memory, `:app:desktop:run` is not
   needed). A break is fixed by one more commit.
2. **The Android live check that found the crashes, repeated** on the Medium_Phone emulator (360 × 640 dp, 192 MB heap;
   memory files `android-emulator-verification.md`, `smallest-supported-screen.md`), `logcat` watched for
   `OutOfMemoryError`, ANRs, and skipped frames: attach a 50,000,000-byte file from the sheet and Save; in the same
   process attach a second one and a small one from the editor and Save; export the library as an archive; open a `.gp5`
   and a PDF attachment (U4); pick one byte over the limit in the sheet (U1: the sentence is visible); kill the process
   with the attachment picker open and pick (U3). Record the peak heap of each step in the report. **This check is the
   acceptance of findings 1–3; the plan is not done while any step dies or skips more than a few frames.**
3. **One live Dropbox call for S7**, which needs the owner: whether the app-folder token may list and restore a deleted
   file's revisions. Recipe and rules in the memory file `sync-live-testing.md` (a test home, never the real
   installation; the owner clicks the consent; snapshot first, restore after, `zz livetest` names). Upload a small test
   file under `files/`, delete it, run the restore through a test home, compare bytes. If Dropbox refuses, S7 stays in
   (it answers "false" and changes nothing) and the limit in `run/CLAUDE.md` is worded as still standing; tell the owner.
4. Remove the lane worktrees and any empty `.claude/worktrees` folders. Update the streaming plan's status line (steps 1
   and 6 landed, the writer's large-entry rule). Delete this plan and its roadmap entry in the last commit.
5. Report: one line per item (`<item>: <hash> "<message>"`, what verified it), what was not done and why, the live
   checks' numbers, and anything a lane found that this plan did not name.

## Not in this plan

- Streaming sync transfers or shared copies to and from files (an API redesign; one large file at a time stays the bound).
- A desktop heap cap.
- Safari and iOS runs of the web's write path: the streaming plan's release check covers them on real browsers.
- Chrome emptying an origin's storage after its disk filled (seen once in a headless profile on a full test volume,
  not reproduced, the browser's doing): the streaming plan's quota handling and its release check on a nearly full
  device are where it is looked at again.
- The cross-version shared-link rebuilds (decided: left as they are).
