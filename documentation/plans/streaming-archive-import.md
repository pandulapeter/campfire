<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: streaming archive import

**Status:** not started. Written against the code on 2026-11-17 (76853db36). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, `zip/` is
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/`, `local/` is
`data/source/local/implementation/src/`.

**This is critical for the next release. Safety and reliability come first: it has to work right the first time it
ships, on every platform, with no room for error, and it must not introduce any issue for users.** Every step below is
judged by that before anything else: a step that cannot be shown safe on every platform does not land, and the plan
is not done until every platform's release check (step 14) has passed.

A zip archive of any size (a library export, another app's backup, or a shared link's zip) is imported without ever
being held in memory. The archive is copied into an app-private staging folder as it arrives, read from there through
its central directory, and every picture and attachment its songs name is inflated straight into a staging file named
by its content. Only the song texts and per-file metadata stay in memory for the review; Import moves the staged files
into `library/files` instead of writing bytes. What limits an import afterwards is disk space, the web's storage quota,
and time, each checked up front and reported cleanly, never an allocation the process does not survive.

## Where it stands

- `ImportLimits.kt` (`:data:model`) caps everything by memory: `MAX_ARCHIVE_SIZE` (88 MB), `MAX_IMPORT_SIZE` (24 MiB of
  songs), `MAX_IMPORT_IMAGES_SIZE` (64 MiB), and `MAX_IMPORT_ATTACHMENTS_SIZE` (50 MiB), because an Android heap is about
  192 MB and running out of it cannot be caught. `ImportBudget` reads every picked file whole within those.
- `ZipReader.read(archive: ByteArray, …)` walks the central directory of an archive in memory; `Inflater.inflate`
  returns each entry as a `ByteArray` (`MAX_DECLARED_ENTRY_SIZE` 50 MiB). The reader's safety checks are the ones to
  keep: ZIP64 refused, encrypted and unknown methods left out, declared sizes charged before reading, inflation held to
  the declared size, compressed bytes charged once and never more than the archive holds (overlapping entries), CRC
  checked, names decoded as UTF-8 or CP437, `EntryPolicy` (`Read`, `Sniff`, `Skip`) decided before inflating.
- `ArchiveLocalSourceImpl.unpack` (`local/commonMain/.../source/`) reads the songs, recurses into nested archives up to
  `MAX_DEPTH` 3 (in memory, within the songs' budget), translates SongbookPro backups and other apps' documents, and
  hands `ArchiveSongImages.read` the archive again for two more walks that read the pictures and attachments songs name.
  `ImportedFile.images` carries them as `ImportedImage.bytes`.
- `PrepareImportUseCaseImpl` (`domain/implementation/.../importing/`) triages, unpacks, and plans; `IncomingImages.planned`
  asks `ImageRepository.libraryNameOf(bytes)` / `libraryNameOfAttachment` (SHA-256 of the whole array, then
  `otherBytesUnder`, which reads a library file of that name whole to compare), and `ImportPlan.SongImage.bytes` holds
  every picture and attachment until `ApplyImportUseCaseImpl` writes them through `LibraryImageWriter.addImage` /
  `addAttachment` inside `withImagesKept`.
- The intake reads whole files before anything is queued: Android `List<Uri>.toImportedFiles` (`FilePicker.android.kt`,
  used by the picker, `LibraryDropTarget`, and `AndroidFileImport`), desktop `readAsImportedFiles`
  (`FilePicker.desktop.kt`: picker, drop, "Open with", the single-instance hand-over), web `toImportedFile`
  (`FilePicker.wasmJs.kt`: picker and drop), iOS `NSURL.readImportedFile` (`IosFilePicker.kt`, used by the picker,
  `IosFileImport.openUrl`, `IosShareInbox`, and the iOS `LibraryDropTarget`). Downloads (`ImportDownloadRemoteSourceImpl`,
  for addresses and shared links) read the body into memory within `MAX_ARCHIVE_SIZE`.
- The export side already streams: `ZipWriter.write(entries, sink)`, `ZipSink`, `FileStorage.openSink` with
  `JvmFileSink`, `PosixFileSink` (iOS), and `OpfsFileSink`; the `EXPORTS` folder is emptied on launch.
- `FileStorage` has no random-access read, no move, and no free-space query. Its contract is tested by
  `FileStorageContractTest` on the desktop and on the iOS simulator; Android and the web have no Kotlin tests, the web's
  `opfs-writer.js` has Node tests.

## Safety and reliability

What must never happen, and what prevents it. Each step names which of these it touches, and its tests prove them.

- **Data loss in the library.** Nothing in the library is written before Import, as today. Import only adds files:
  staged files are moved in under content names (never over a different file of the same name, which `nameFor` already
  steps past), songs and setlists are written exactly as today, and a failure stops before any dependent setlist.
- **A partially imported library.** Unchanged rules: pictures and attachments are moved in just before the song naming
  them, inside `withImagesKept`, so a failure or a killed process leaves only files no song names, which the prune
  removes; what was written is adopted in one change (`adoptImported`). A cancelled or failed preparation has written
  nothing to the library at all.
- **A half-written library file.** A staged file reaches `library/files` only by an atomic rename on the JVM and iOS
  (staging lives on the same volume as the library), and on the web through the existing atomic `writeBytes` (journaled
  worker or `.campfire-new` marker), never by writing into the library in place. A staged file is only eligible once
  its sink closed cleanly with its size, CRC, and SHA-256 verified; its name is that SHA-256, computed over exactly the
  bytes written.
- **Staging leftovers filling the disk.** One staging folder, `imports/`, out of every backup and sync. Every staged
  name carries the session that wrote it; launch deletes every name of another session, so a crash, a killed process,
  or a closed tab costs nothing past the next launch, with no race against a cold start's "Open with" file. A request
  deletes its own files when it settles (imported, cancelled, failed and dismissed, or replaced by a retry), and an
  abandoned sink deletes its partial file, as an export's does.
- **Running out of memory.** No code path holds an archive, a nested archive, a picture, or an attachment whole while
  unpacking. What stays in memory is bounded: the central directory (at most `MAX_CENTRAL_DIRECTORY_SIZE`), one
  read buffer and one 64 KiB inflate window, the song texts within the unchanged `MAX_IMPORT_SIZE`, and one library file
  at a time on the web's move. A large-archive test under a 128 MiB heap proves it (step 13).
- **Hangs.** Every loop that reads, inflates, copies, or hashes yields at least every 256 KiB, so cancellation is
  prompt and the web's single thread keeps painting. Progress is determinate in bytes. The reader stays linear in the
  archive's size: the central directory is read once, each entry's data at most once per walk, and the existing
  `assertTakesTimeInProportion` checks extend to the streaming paths.
- **A hostile archive escaping paths.** Entry names never become paths: staged files are named by session, request, and
  content hash (`[0-9a-f]` and a known extension), never by an entry's name, and `FileStorage` refuses a separator or
  `..` anyway. Hidden entries are skipped as today.
- **A hostile archive exhausting disk.** The declared sizes of every entry that will be staged are added up before
  anything is inflated and must fit the disk budget (free space minus `MIN_FREE_SPACE`, at most
  `MAX_IMPORT_STAGED_SIZE`); inflation is held to each declared size as today, so the declared total is a hard bound.
  Overlapping entries stay refused, per-file caps stay (8 MB a picture, 50 MB an attachment), and the copy of the
  picked archive is checked against free space before it starts and as it runs.
- **A regression for archives that work today.** The in-memory path stays in the code, unchanged, until the last step.
  A differential test runs every archive the tests build, every fixture, and the private import corpus through both
  and requires identical results (step 9). For the next release, an archive within the old limits whose streaming
  preparation fails for any reason but cancellation, a damaged archive, or no space falls back to the in-memory path
  and logs it, so no user who could import an archive yesterday is worse off (step 12).

## Decisions

- **Copy first, always, on every platform.** Whatever arrives as an archive (by name, or by its first bytes being a
  zip's, the rule `ImportBudget` follows today) is copied into `imports/` by streaming before it is read: Android a
  content URI's `InputStream`, iOS a security-scoped, coordinated URL read with POSIX `fread` in 1 MiB chunks (never
  `NSFileHandle`, whose errors are ObjC exceptions Kotlin cannot catch), desktop a `FileInputStream`, the web
  `File.stream()` piped into an OPFS writable (the worker on Safari before 26). One code path reads every archive; the
  user's file is never held open during a review (Windows locks an open file against deletion and renaming), and an
  iCloud or content-provider file cannot change under the reader. Files that are not archives (songs, setlists,
  documents, single pictures) are read into memory exactly as today.
- **The copy starts at once and belongs to the request.** `ImportSource.Files` carries `IncomingFile`s whose staging is
  a job started when the file arrives (so an Android URI grant, an iOS `Documents/Inbox` copy, or a web `File` is
  consumed while it is valid), and the request's preparation awaits it as its first phase, `RECEIVING`, determinate in
  bytes and cancelled by the Loading stage's Cancel as the rest of the preparation is. A queued request's copy runs
  while it waits.
- **Random access through the central directory.** `zip/ZipSource` is "read N bytes at offset X" (`size`,
  `suspend fun read(position, buffer, offset, length)`), with a `ByteArray` implementation (tests, the old path, the
  in-memory corner cases) and `FileStorage.openSource(directory, name)` behind it. `ZipReader.open(source)` finds the end
  of central directory in the last 64 KiB + 22 bytes, reads the central directory (refused over
  `MAX_CENTRAL_DIRECTORY_SIZE`, 16 MiB, which holds 65,535 entries of long names), and keeps every current check.
- **The inflater streams with puff's structure intact.** A second entry point inflates from a `ZipSource` range into an
  `EntrySink`, with a 64 KiB output buffer that slides to keep the last 32 KiB of history. `bits()` and `decode()` stay
  plain functions over an input buffer; only the block loops are `suspend`, topping the input up to the longest symbol
  (and the output buffer down) between symbols, so no suspend call is paid per bit. Declared sizes are held as today,
  CRC-32 and SHA-256 are updated per flushed chunk, and a mismatch throws `ZipException` after which the sink abandons its
  file. `Inflater.inflate(ByteArray)` stays untouched (the PDF reader and the old path use it); a fuzz test requires the
  two to agree byte for byte or fail alike.
- **Stored entries copy, deflated entries inflate**, both through the same sink, so a 50 MB recording stored in a
  library export costs one buffer.
- **SHA-256 moves to `:data:model`** (`data/model/.../hashing/Sha256`), gaining an incremental `Sha256.Digest`
  (`update`, `finish`), so the staging sink in `:data:source:local:implementation` names a file as it writes it.
  `:data:source:remote:api`, sync, and `LibraryImages` import it from there.
- **Staged content is a type of its own.** `:data:model` gets `StagedFile(name, size, sha256Hex)` and
  `IncomingContent` (`Bytes(ByteArray)` or `Staged(StagedFile)`); `ImportedFile` gains `staged: StagedFile?` for a
  picked archive (its `bytes` empty), and `ImportedImage.bytes` and `ImportPlan.SongImage.bytes` become `content:
  IncomingContent?`. In-memory content (a picture link's download, a single picked picture, the demo library) stays
  `Bytes`, so nothing that works on bytes today changes.
- **Pictures are examined by their head.** `LibraryImages.examine` reads only `ImageInfo.MAX_HEADER_BYTES` and the size
  already, so a staged picture is judged from its first 64 KiB and its size; an attachment by its size and extension as
  today. `ImageRepository.libraryNameOf` / `libraryNameOfAttachment` and `LibraryImageWriter.addImage` / `addAttachment`
  gain `IncomingContent` overloads; `otherBytesUnder` compares sizes, then the two files chunk by chunk, never reading
  either whole.
- **Import moves, never rewrites.** `ImageLocalSource` gains `moveStagedFile(staged, shelf, name)`: the JVM
  `Files.move` with `ATOMIC_MOVE` (staging is under the same `filesDir` / data directory as the library), iOS
  `moveItemAtPath` (staging in `Library/Caches/imports` on the same volume as `Application Support`), then
  `keepOutOfDeviceBackup` for an attachment as `writeImage` does; the web reads the staged file and writes it with the
  existing atomic `writeBytes` (one file of at most 50 MB at a time, which 84bddce6a made fit a phone's browser), then
  deletes it. A name the library already holds with the same bytes is not moved; the staged copy is deleted.
- **Nested archives are staged too**, inflated into `imports/` and walked through the same reader, within `MAX_DEPTH`
  3, counting against the disk budget instead of the songs' budget. A SongbookPro `dataFile.txt` and every song text
  stay in memory within `MAX_IMPORT_SIZE`.
- **The limits after this plan** (`ImportLimits`, KDoc rewritten with the new reasoning):
  - `MAX_STAGED_ARCHIVE_SIZE`: 4 GiB − 1, what a zip without ZIP64 can be (and what `ZipWriter` writes at most), so
    every Campfire export imports back whole. Larger is refused up front as too large.
  - `MAX_CENTRAL_DIRECTORY_SIZE`: 16 MiB, above.
  - `MAX_IMPORT_SIZE` (24 MiB of songs, setlists, and documents in memory), `MAX_TEXT_FILE_SIZE`,
    `MAX_DOCUMENT_FILE_SIZE`, `MAX_IMAGE_FILE_SIZE` (8 MB a picture), and `MAX_ATTACHMENT_FILE_SIZE` (50 MB) unchanged:
    they bound what is held or examined whole, and single files keep their limits.
  - `MAX_IMPORT_IMAGES_SIZE` and `MAX_IMPORT_ATTACHMENTS_SIZE` stop bounding archives. Pictures and attachments share
    the disk budget: free space minus `MIN_FREE_SPACE` (512 MiB on Android, iOS, and the desktop, so the system never
    reaches its own low-storage state because of an import; 32 MiB of the origin's quota on the web), and at most
    `MAX_IMPORT_STAGED_SIZE` (16 GiB) to bound the time one import can take. `MAX_IMPORT_IMAGES_SIZE` stays for picture
    links downloaded into memory (`IncomingPictureLinks`).
  - `MAX_ARCHIVE_SIZE` (88 MB) stays only as the selection-wide budget for files read whole into memory (`ImportBudget`)
    and, for one release, as the fallback's ceiling.
  - The 65,535-entry ceiling is the format's; nothing more is needed while the central directory is capped.
- **Free space** is asked through a new `FileStorage.availableSpace(directory)`: `File.usableSpace` on the JVM,
  `NSURLVolumeAvailableCapacityForImportantUsageKey` on iOS, `navigator.storage.estimate()` (quota − usage) on the web.
  Checked before the copy (with the size the platform reports; Android may report none, then as it runs), before
  unpacking (declared sizes), and a write that fails anyway is the same clean failure.
- **A new failure, `NO_SPACE`**, in `ImportFailure`: one sentence naming how much space the import needs, Try again
  offered. `TOO_LARGE` now names 4 GB. Both in every language.
- **ZIP64 stays refused**, now with its own sentence ("saved in a format for archives over 4 GB"), so the user learns
  why rather than reading "unreadable". See the open questions.
- **Progress is determinate in bytes.** `ImportProgress` gains `completedBytes` and `totalBytes`; `RECEIVING` (the copy)
  and `UNPACKING` report them, the Loading stage says "Unpacking 340 MB of 1.2 GB", and `WorkProgressBar` fills for
  those phases as it does for writing. Downloads report bytes against `Content-Length` where the host sends one.
- **Downloads stream into staging.** `ImportDownloadRemoteSource.download` takes a sink (`suspend (ByteArray, Int, Int)
  -> Unit`, as `ExportSink` does), so `:data:source:remote:implementation` still never touches `FileStorage`; the body is
  read from `bodyAsChannel()` in chunks within `MAX_STAGED_ARCHIVE_SIZE`. `downloadPicture` stays in memory.

## Steps

Each step compiles, passes `desktopTest`, `spotlessCheck`, the iOS simulator storage tests, and the UI tests, and is
committed on its own. **Steps 1–11 change nothing a user sees**: the in-memory path stays what production runs until
step 12 switches it, so any of them can ship in a release on its own. Each step's "Safety" line names what it must
prove.

### 1. SHA-256 in `:data:model`, incremental

Move `Sha256` from `:data:source:remote:api/hashing` to `:data:model/hashing` verbatim (its own commit, imports
updated), then add `Sha256.Digest` with `update(bytes, offset, length)` and `finishToHex()`. Tests: the digest over any
chunking equals `Sha256.hash` (random chunk boundaries from a fixed seed, empty input, 55/56/64-byte boundaries).
Safety: sync's fingerprints and content names are unchanged (existing sync tests pass untouched).

### 2. `ZipSource` and the reader over it

- `zip/ZipSource.kt` and `ByteArrayZipSource`; `ZipReader.open(source): ZipDirectory` and a `suspend` walk with the same
  `EntryPolicy` / `sniffedLimitOf` callbacks, reading entries through `EntrySink`s (`ByteArrayEntrySink` for texts).
  The old `ZipReader.read(ByteArray)` is left exactly as it is.
- All current checks, plus `MAX_CENTRAL_DIRECTORY_SIZE`, the local header read once per entry through the source, and
  data descriptors (bit 3) handled as today: the central directory's sizes and CRC are the truth.
- Tests (`commonTest`): every `ZipReaderTest` case run through both readers with identical results; stored, deflated,
  data descriptors, UTF-8 and CP437 names, backslash separators, ZIP64 refused, encrypted and unknown methods left out,
  overlapping entries, declared sizes past the archive, truncated at every byte of a small archive, comment of 65,535
  bytes, central directory past the cap. `ZipFuzzTest`'s mutants through both: the same entries or the same failure.
Safety: no regression in what an archive yields; hostile layouts refused as before.

### 3. The streaming inflater

The second `Inflater` entry point described in the decisions, writing to an `EntrySink` with CRC-32 and SHA-256 updated
per chunk, yielding every 256 KiB. Tests: random and real-world streams (every fixture, `Deflater`'s output, stored
blocks, dynamic and fixed blocks, distances of 32,768, a stream ending exactly on a buffer boundary) inflate to the
same bytes as `Inflater.inflate`; malformed streams fail alike; a stream producing more or less than declared fails
before the sink closes; cancellation mid-entry abandons the sink; `assertTakesTimeInProportion` on a highly repetitive
stream. Safety: bounded memory per entry, no behaviour change for the PDF reader (untouched).

### 4. `FileStorage`: a staging folder, random reads, moves, and free space

- `StorageDirectory.IMPORTS` → `imports/`: Android under `filesDir` (never `cacheDir`, which the system may clear while
  the app runs), iOS under `Library/Caches` (excluded from backup by location, cleared by the system only while the app
  is not running), desktop and web beside `exports/`. Not in `app/android/src/main/res/xml`'s backup rules, which are an
  allow-list.
- `openSource(directory, name): FileSource` (random reads; JVM `FileChannel` positional reads, opened with
  `StandardOpenOption.READ` only, iOS `pread`, web `getFile()` then `slice(...).arrayBuffer()` across the boundary in
  `LATIN1_CHUNK_SIZE` chunks as `OpfsBytes.kt` does), `openSink(IMPORTS, …)` (already written for `EXPORTS`),
  `moveInto(from, name, to, name)` (atomic where the platform can, see the decisions), and `availableSpace(directory)`.
- Contract tests in `FileStorageContractTest`: reads at every offset, a read past the end, a source of a file deleted
  meanwhile, a move over nothing and over an existing file, a move of a missing file, available space positive. Run on
  the desktop and the iOS simulator. The web gets a `wasmJsTest` source set in `:data:source:local:implementation` that
  runs the same contract test over `OpfsFileStorage` in headless Chrome (`wasmJsBrowserTest`), added to `tests.yml`;
  `opfs-writer.js` gains a `stage` request (a `File` posted to the worker, written with a sync access handle in slices)
  with Node tests beside the existing ones.
Safety: the library's directories behave exactly as before (the existing contract tests pass untouched).

### 5. The staging sink and the session

`local/commonMain/.../staging/ImportStaging`: names (`<session>-<request>-<n>.zip` for a received archive,
`<session>-<request>-<sha256>.<ext>` for an unpacked file), a sink that writes, hashes, and counts against a budget and
deletes its file when abandoned, `deleteRequest(request)`, and `deleteOtherSessions()`. A `ClearImportStagingUseCase`
runs at launch beside `ClearExportFilesUseCase`. Tests (desktop): a partial file never survives a failure, a
cancellation, or a budget overrun; other sessions' files go at launch, this session's stay; disk full (a `FileStorage`
fake throwing on write past a size) fails cleanly with nothing left behind.

### 6. The streaming unpack, beside the old one

- `ArchiveLocalSource.unpack(archive: StagedFile, …)` beside the `ByteArray` one: `ArchiveLocalSourceImpl` and
  `ArchiveSongImages` walk a `FileSource` with the new reader, song texts into memory as today, pictures and attachments
  into staging sinks, nested archives staged and walked. The policies (`limitOf`, `sniffedLimitOf`, hidden entries,
  content-named attachments skipped, SongbookPro, other apps' documents) are shared code with the old path, extracted
  verbatim first in a commit of their own so the two cannot drift.
- The disk budget is checked from declared sizes before the picture and attachment walks start.
- Tests: `ArchiveLocalSourceTest` and `ArchiveImagesTest` run against both unpacks (parameterised), with staged bytes
  equal to the old bytes.

### 7. Staged content through the plan and the write

`StagedFile`, `IncomingContent`, the `ImportedFile` / `ImportedImage` / `ImportPlan.SongImage` changes,
`ImageRepository`'s and `LibraryImageWriter`'s overloads with the chunked comparison, `ImageLocalSource.moveStagedFile`,
and `IncomingImages.planned` / `store` handling both kinds. `PrepareImportUseCaseImpl` routes a staged archive to the
new unpack; nothing produces one yet. Tests (`domain/implementation`): `PrepareImportUseCaseImplTest`,
`ImportFilesUseCaseImplTest`, and `ApplyImportUseCaseImplTest` run their archive cases through both kinds with
identical plans and libraries; a move failing midway leaves the songs unwritten and the prune finds the moved files.

### 8. Intake: receiving into staging

- A pure decision in `:data:model` (`ImportIntake.decide(name, size, head)`: read whole within a limit, stage, or leave
  unread), replacing the archive branch of `ImportBudget.read`, tested against every `ImportLimitsTest` case.
- Android: `toImportedFiles` opens each URI once, peeks the head, and either reads as today or hands an `IncomingFile`
  whose copy (the stream into `ImportStaging`) starts at once on `Dispatchers.IO`. The copy itself is
  `jvmSharedMain` code taking an `InputStream`, so the desktop tests cover it; Android supplies only
  `contentResolver.openInputStream`.
- Desktop: `readAsImportedFiles` the same over `FileInputStream`, closed as soon as the copy ends.
- iOS: `readImportedFile` copies inside the coordinated read and the security-scoped access (both must outlive the
  copy, so the copy runs inside the block, off the main thread); a `Documents/Inbox` copy and a picker copy
  (`asCopy`) are moved instead of copied; the share inbox's file is copied, since its folder is deleted only once the
  import settles. The share extension's `ShareLimits` raise the archive size to `MAX_STAGED_ARCHIVE_SIZE` (it copies,
  never loads) and the inbox bound stops counting archives against its 64 MB.
- Web: the picked or dropped `File` is piped into OPFS in JavaScript, never crossing into Wasm.
- Behind `IncomingFile` nothing is queued differently: a request with a receiving file shows Loading at once.
- Not yet switched on: the intake still reads archives whole until step 12, behind one `ImportIntake` constant.

### 9. The differential test

`ArchivePathParityTest` (desktop, `:data:source:local:implementation`, with the domain's planner wired over real
sources): every archive the tests build, the committed fixtures (`upgrade-*`, the backup tests' archives), the fuzz
mutants of each, and every archive-like file (`.zip`, `.sbpbackup`, `.sbp`, `.osb`, and zips by their bytes) under
`campfire.importCorpus` when set, run through the in-memory path and the streaming path. Required: identical
`ImportPlan`s (songs, setlists, every problem, skipped, oversized, and unreadable names, content names) and identical
library files after Import. The one allowed difference: an archive that hit the old picture, attachment, or nested
archive budgets may bring more files in the new path, and the test then requires that every other field is identical
and that each difference is a file the old path reported `TOO_LARGE` for its budget. Runs in CI with the committed set
and by hand against the corpus before step 12 and before the release.

### 10. Downloads into staging

`ImportDownloadRemoteSource.download` with a sink and a byte progress callback; `ImportDownloadRepositoryImpl` stages
into `imports/`; `SharedLinkDownload` and `ImportController.prepare` carry a `StagedFile`, so Try again reads the same
staged copy again. Tests (`:data:source:remote:implementation` desktop, with Ktor's mock engine): a body streamed in
chunks, cut short, larger than announced, larger than the cap, cancelled midway, each leaving no staged file. On the web,
check that Ktor's wasmJs engine reads the body as a stream (`body.getReader()`); where it buffers, the download is a
`fetch` piped into OPFS in a `js()` block instead.

### 11. Progress, Cancel, and the new failures in the UI

`ImportProgress` bytes, `RECEIVING`, the Loading stage's byte text (`ImportProgressContent`, tested), `WorkProgressBar`
filling for those phases (`workProgress`, tested), `ImportFailure.NO_SPACE` and the ZIP64 and 4 GB sentences in every
language (`values`, `-hu`, `-ro`, `-de`, `-es`, `-fr`, `-it`, `-he`, `-pt`, `-pl`, `-cs`), with plurals where counted.
Cancel during `RECEIVING` and `UNPACKING` deletes the request's staging before the screen leaves. UI tests in
`:tools:screenshots` (`importReport/ImportReviewTest`, `songDetails/ImportPreviewTest`): determinate progress during a
staged unpack, Cancel mid-unpack returning to where the user was with `imports/` empty, the no-space failure and its Try
again, and every existing import UI test passing unchanged.

### 12. Switch: every archive streams, with a fallback for one release

The intake stages archives and `PrepareImportUseCaseImpl` unpacks staged archives only. For this release, a staged
archive within the old `MAX_ARCHIVE_SIZE` whose streaming preparation throws anything but a cancellation, a
`ZipException` the old reader would also throw, or `NO_SPACE` is read into memory and prepared through the old path,
with one log line naming the exception, so nobody who could import an archive yesterday is worse off. Export's
`isTooLargeToImport` and `export_too_large_to_import` change to the new limits (an export over 4 GB, or with more song
text than `MAX_IMPORT_SIZE`), in every language. `ImportLimits`' KDoc, `data/formats/CLAUDE.md` (`zip/`),
`data/source/local/implementation/CLAUDE.md` (`IMPORTS`, staging), `domain/implementation/.../importing/CLAUDE.md`,
`ui/screens/importReport/CLAUDE.md`, `ui/progress/CLAUDE.md`, `app/ios/CLAUDE.md` (share extension limits), and the root
`CLAUDE.md`'s library layout (`imports/<name>`, emptied on launch) are updated in this step.

### 13. The large-archive test

`LargeArchiveImportTest` (desktop, `:data:source:local:implementation`): an archive generated on the fly into a temp
folder with `ZipWriter`'s streaming `write` (never committed): thousands of songs naming stored and deflated pictures and
attachments of pseudo-random bytes from a fixed seed, nested archives, and data descriptors. It is received, unpacked,
planned, and imported in a JVM capped at 128 MiB of heap (its own Gradle test task, `maxHeapSize`), asserting the
library's file hashes, an empty `imports/` afterwards, and that peak disk use stayed under twice the archive. CI runs
a 600 MB archive; `-Pcampfire.largeArchiveTest=4g` runs a 3.9 GB one by hand before the release. A variant cancels at
random points and one fills a size-limited fake storage, both requiring no leftovers and an untouched library.

### 14. Release check by hand, on every platform

Not done until every line passes on every platform, with the same archives each time: a library export of the owner's
library with pictures and attachments, a shared link opened from a message, a SongbookPro backup and an OnSong or
OpenSong archive, a generated > 1 GB archive, a corrupt one (truncated, and one with a damaged entry), a ZIP64 one,
Cancel during receiving and during unpacking, and an import on a nearly full device. After each: the review matches the
same archive on the previous release, the library holds what it should, `imports/` is empty after the import and after
the next launch, and the app stays responsive.

- **Android**: the small-heap emulator at 360 × 640 dp (Medium_Phone AVD, 720 × 1280 at density 320), picked, opened
  with, shared from Files and Gmail, dropped from another app in split screen; the process killed during receiving and
  relaunched; low disk by filling the emulator's storage.
- **iOS**: the simulator and a device; picked, opened in place from iCloud Drive (not downloaded yet), AirDrop, the share
  extension, a drop on iPad; the app sent to the background during unpacking.
- **Desktop**: macOS, Windows, and Linux as available; picked, dropped, opened with while running and cold; on Windows,
  the source file renamed and deleted during the review (it must not be locked).
- **Web**: Chrome and Safari (Safari 26 and an older one without `createWritable()`), picked and dropped; the tab
  closed during unpacking and reopened; `navigator.storage.estimate()` near its quota.

### 15. Remove the old path (the release after)

Once the release has shipped and step 14 passed on every platform: remove the fallback, `ZipReader.read(ByteArray)`,
the `ByteArray` unpack, the `ImportIntake` switch, and every test that only fed the old path; the differential test
becomes the parameterised tests' new-path half. Then delete this plan.

## Open questions for the owner

- **ZIP64**: refuse with a clear sentence (this plan), or read it? Reading adds the extra field and the ZIP64 end
  records to a reader that must stay small; macOS's Archive Utility and Windows write ZIP64 only past 4 GB or 65,535
  entries, but some backup tools write it always. Worth a look at whether any app the importer serves does.
- **Android imports over 1 GB**: an in-process import as today (a process the system kills in the background loses the
  preparation, never the library), or a foreground service with a notification while receiving and unpacking?
- **The fallback in step 12**: keep it for one release as planned, or ship without it once step 14 passes?
- **The web test in CI**: headless Chrome through `wasmJsBrowserTest` adds a browser to the CI image's work; fine, or
  keep the web's contract test by hand?

## Not in this plan

- **Sync's whole-file transfers** (Dropbox uploads and downloads of library files, held in memory one at a time).
- **Picking or opening a single attachment** (50 MB in memory, `MAX_ATTACHMENT_FILE_SIZE`) and single pictures.
- **PDF and Word conversion**, which keep `MAX_DOCUMENT_FILE_SIZE` and the in-memory `Inflater.inflate`.
- **Shared copies** (`SharedCopySync`), still packed in memory within `MAX_SHARED_COPY_SIZE` and
  `MAX_SHARED_ATTACHMENTS_SIZE`.
- **Larger song budgets**: `MAX_IMPORT_SIZE` stays 24 MiB of text; a library of more songs still imports in parts.
- **Dropped folders**, walked file by file as today (an archive inside one is staged like any other).
