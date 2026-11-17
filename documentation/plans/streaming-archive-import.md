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

**Status:** final, ready to implement: the fixes plan for pictures and attachments has landed (2026-11-17), carrying
out steps 1 and 6 here, and `ZipWriter` now stores an entry over 1 MiB whose first 64 KiB do not shrink by 3% and gives
up deflating one whose output reaches its size (entries of 1 MiB or less are written as before, bit for bit). The owner's answers (ZIP64
read and written, no fallback, the web's contract test in CI, an archive past 4 GB without ZIP64 refused with its own
sentence) are folded in, and so are the corrections of a pre-mortem on 2026-11-17: the reader's ZIP64 rules follow what
real tools write, the import's work belongs to the app rather than a view model, the free-space rule cannot refuse a
small import, iOS stages outside `Caches`, and step 0's spikes settle what reading could not. Written against the code
on 2026-11-17 (76853db36). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, `zip/` is
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/`, `local/` is
`data/source/local/implementation/src/`.

**This is critical for the next release. Safety and reliability come first: it has to work right the first time it
ships, on every platform, with no room for error, and it must not introduce any issue for users.** Every step below is
judged by that before anything else: a step that cannot be shown safe on every platform does not land, and the plan
is not done until every platform's release check (step 17) has passed.

A zip archive of any size (a library export, another app's backup, or a shared link's zip, ZIP64 included) is imported
without ever being held in memory. The archive is copied into an app-private staging folder as it arrives, read from
there through its central directory, and every picture and attachment its songs name is inflated straight into a
staging file named by its content. Only the song texts and per-file metadata stay in memory for the review; Import
moves the staged files into `library/files` instead of writing bytes. What limits an import afterwards is disk space,
the web's storage quota, and time, each checked up front and reported cleanly, never an allocation the process does
not survive.

## Where it stands

- `ImportLimits.kt` (`:data:model`) caps everything by memory: `MAX_ARCHIVE_SIZE` (88 MB), `MAX_IMPORT_SIZE` (24 MiB of
  songs), `MAX_IMPORT_IMAGES_SIZE` (64 MiB), and `MAX_IMPORT_ATTACHMENTS_SIZE` (50 MiB), because an Android heap is about
  192 MB and running out of it cannot be caught. `ImportBudget` reads every picked file whole within those.
- `ZipReader.read(archive: ByteArray, …)` walks the central directory of an archive in memory; `Inflater.inflate`
  returns each entry as a `ByteArray` (`MAX_DECLARED_ENTRY_SIZE` 50 MiB). The reader's safety checks are the ones to
  keep (all but the ZIP64 refusal, which step 3 replaces with reading it): ZIP64 refused, encrypted and unknown methods
  left out, declared sizes charged before reading, inflation held to the declared size, compressed bytes charged once
  and never more than the archive holds (overlapping entries), CRC checked, names decoded as UTF-8 or CP437,
  `EntryPolicy` (`Read`, `Sniff`, `Skip`) decided before inflating. `DocxTextExtractor` reads Word documents through it
  too.
- `ZipWriter.write` refuses an archive past 4 GiB or 65,534 entries rather than writing ZIP64, so a large library
  cannot be exported at all today.
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
  `IosFileImport.openUrl`, and `IosShareInbox`); the iOS `LibraryDropTarget` (`presentation/src/iosMain`) and
  `IosPhotoPicker` instead read a provider's `loadFileRepresentationForTypeIdentifier` file, which exists only until
  its completion handler returns. Downloads (`ImportDownloadRemoteSourceImpl`,
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
  unpacking. What stays in memory is bounded: the central directories being walked (at most
  `MAX_CENTRAL_DIRECTORY_SIZE` across every one held at once, an outer archive's and the nested ones it is walking, kept
  compactly: offsets, sizes, and CRCs in primitive arrays, names as the directory's bytes), one read buffer and one
  64 KiB inflate window, the song texts within the unchanged `MAX_IMPORT_SIZE`, and one library file at a time on the
  web's move. A large-archive test under a 128 MiB heap proves it, at the entry-count cap too (step 16).
- **Hangs.** Every loop that reads, inflates, copies, or hashes yields at least every 256 KiB, so cancellation is
  prompt and the web's single thread keeps painting. Progress is determinate in bytes. The reader stays linear in the
  archive's size: the central directory is read once, each entry's data at most once per walk, and the existing
  `assertTakesTimeInProportion` checks extend to the streaming paths, ZIP64 archives included.
- **A hostile archive escaping paths.** Entry names never become paths: staged files are named by session, request, and
  content hash (`[0-9a-f]` and a known extension), never by an entry's name, and `FileStorage` refuses a separator or
  `..` anyway. Hidden entries are skipped as today.
- **A hostile archive's 64-bit numbers.** Every size, offset, and count is a `Long` checked before use: a ZIP64 value
  with its top bit set (negative as a `Long`) is refused, every sum (an offset plus a size, a running total) is added
  with an overflow-checked helper, every offset must lie within the file and before the structure that follows it, and
  a count is checked against `MAX_ARCHIVE_ENTRIES` before anything is allocated for it. A malformed ZIP64 record is a
  corrupt archive, refused like any other.
- **A hostile archive exhausting disk.** The declared sizes of every entry that will be staged are added up before
  anything is inflated and must fit the disk budget (free space minus `MIN_FREE_SPACE`, at most
  `MAX_IMPORT_STAGED_SIZE`); inflation is held to each declared size as today, so the declared total is a hard bound.
  The budget is one ledger for the whole app: every request's received archive, its nested archives, and its staged
  files reserve from it as they are announced and give back as they are deleted, so two requests measured at different
  moments (a share arriving during a review) cannot both fit and then fill the disk together.
  Overlapping entries stay refused, per-file caps stay (8 MB a picture, 50 MB an attachment), and the copy of the
  picked archive is checked against free space before it starts and as it runs.
- **A regression for archives that work today.** There is no fallback: once step 15 lands, the streaming path is the
  only one production runs, so it must be proven equal before then. The in-memory path stays in the code, unchanged,
  only as the reference for the differential test (step 11), which runs every archive the tests build, every committed
  fixture, every fuzz mutant of each, and the private import corpus through both and requires identical results. It
  must pass in full, the corpus included, before the switch lands, and the switch does not ship until the release
  check (step 17) has passed on every platform. The old path is removed before the release ships (step 18).

## Decisions

- **An import that downloads or unpacks survives leaving the app, on every platform** (the owner's decision): its
  work belongs to the app, not to a screen's view model (step 14, first), with a
  `dataSync` foreground service on Android as sync has, a continued processing task on iOS 26 and later (background time
  before it), a quit that asks first on desktop, and a closing tab that warns on the web (step 14).
- **No fallback to the in-memory path** (the owner's decision). The switch (step 15) makes the streaming path the only
  one in production. What stands in for a fallback is the gate before it: the differential test passing over every
  fixture, fuzz mutant, and the private corpus before the switch lands, and the release check passing on every platform
  before the switch ships.
- **ZIP64 is read, and written when needed** (the owner's decision). The streaming reader reads the ZIP64 end of
  central directory record and its locator, the extended information extra field (`0x0001`) in local and central
  headers, and data descriptors with 8-byte sizes, so archives over 4 GB and with more than 65,535 entries import, with
  every existing check applied to the 64-bit values (step 3). `ZipWriter` writes ZIP64 records only when an export
  passes 4 GiB or 65,534 entries, so a large library export imports back and every smaller export stays byte-identical
  to today's (step 4). Split (multi-disk) archives stay refused as corrupt.
  What real tools write decides how strict the reader is (step 3): the 64-bit values win over stale 32-bit ones, the
  ZIP64 structures' disk fields are not checked, and local headers' ZIP64 fields are never validated. An archive past
  4 GB written **without** ZIP64 (macOS Archive Utility, some Windows tools) is refused with a sentence of its own and
  never recovered (the owner's decision, 2026-11-17).
- **The web's storage contract test runs in CI** (the owner's decision): `wasmJsBrowserTest` in headless Chrome is a
  step of `.github/workflows/tests.yml`, beside the desktop and iOS simulator storage tests (step 6).
- **Copy first, always, on every platform.** Whatever arrives as an archive (by name, or by its first bytes being a
  zip's, the rule `ImportBudget` follows today) is copied into `imports/` by streaming before it is read: Android a
  content URI's `InputStream`, iOS a security-scoped, coordinated URL read with POSIX `fread` in 1 MiB chunks (never
  `NSFileHandle`, whose errors are ObjC exceptions Kotlin cannot catch), desktop `Files.newInputStream` (not `FileInputStream`, which locks the file on Windows while it is open), the web
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
  Word reader, the in-memory corner cases) and `FileStorage.openSource(directory, name)` behind it.
  `ZipReader.open(source)` finds the end of central directory in the last 64 KiB + 22 bytes, then the ZIP64 locator
  just before it where there is one, reads the central directory (refused over `MAX_CENTRAL_DIRECTORY_SIZE` or
  `MAX_ARCHIVE_ENTRIES`), and keeps every current check.
- **The inflater streams with puff's structure intact.** A second entry point inflates from a `ZipSource` range into an
  `EntrySink`, with a 64 KiB output buffer that slides to keep the last 32 KiB of history. `bits()` and `decode()` stay
  plain functions over an input buffer; only the block loops are `suspend`, topping the input up to the longest symbol
  (and the output buffer down) between symbols, so no suspend call is paid per bit. Declared sizes are held as today,
  CRC-32 and SHA-256 are updated per flushed chunk, and a mismatch throws `ZipException` after which the sink abandons its
  file. `Inflater.inflate(ByteArray)` stays untouched (the PDF reader uses it); a fuzz test requires the two to agree
  byte for byte or fail alike.
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
  `moveItemAtPath` (staging in `Library/Application Support/imports`; the library's files are under `Documents`, the
  same container and volume, which the contract test proves on the simulator by moving between the two), then the
  backup exclusion kept for an attachment and **cleared** for a picture (a staged file carries it, a library picture
  belongs in the backup); the web reads the staged file and writes it with the
  existing atomic `writeBytes` (one file of at most 50 MB at a time, which 84bddce6a made fit a phone's browser), then
  deletes it. A name the library already holds with the same bytes is not moved; the staged copy is deleted.
- **Nested archives are staged too**, inflated into `imports/` and walked through the same reader (ZIP64 included),
  within `MAX_DEPTH` 3, counting against the disk budget instead of the songs' budget. A SongbookPro `dataFile.txt` and
  every song text stay in memory within `MAX_IMPORT_SIZE`.
- **The limits after this plan** (`ImportLimits`, KDoc rewritten with the new reasoning):
  - `MAX_STAGED_ARCHIVE_SIZE`: 16 GiB, the same bound on time as `MAX_IMPORT_STAGED_SIZE` (the format no longer limits
    it). Larger is refused up front as too large, and an export that large is flagged as not importable (step 15).
  - `MAX_CENTRAL_DIRECTORY_SIZE`: 32 MiB, across every central directory held at once (an outer archive's and the
    nested ones being walked), so nesting cannot multiply it. A record is 46 bytes plus its name and extra fields. A Campfire export's
    are short (`images/<hash>.<ext>`; a song's name with its folder is at most about 250 bytes, usually a fifth of
    that), and other tools add about 28 bytes of timestamps to each, so 32 MiB holds `MAX_ARCHIVE_ENTRIES` records
    averaging 120 bytes of name and extras, and fewer of longer ones. The two caps are independent, an archive is
    refused by whichever it passes first, and step 16 proves each on its own with names of realistic length.
  - `MAX_ARCHIVE_ENTRIES`: 200,000 entries in one archive, and across one request's archives and the nested ones in
    them, about three times the format's old ceiling: far more than any library export or backup the importer serves,
    while the compact directory of that many stays a small part of a phone's heap (proven in step 16). Checked against
    the declared count (the 16-bit or the ZIP64 one) before anything is allocated, and again as records are read, since
    a declared count may lie.
  - Per entry: a picture 8 MB and an attachment 50 MB as today; a nested archive bounded by the disk budget and
    `MAX_STAGED_ARCHIVE_SIZE`; a song text within the text limits below.
  - `MAX_IMPORT_SIZE` (24 MiB of songs, setlists, and documents in memory), `MAX_TEXT_FILE_SIZE`,
    `MAX_DOCUMENT_FILE_SIZE`, `MAX_IMAGE_FILE_SIZE` (8 MB a picture), and `MAX_ATTACHMENT_FILE_SIZE` (50 MB) unchanged:
    they bound what is held or examined whole, and single files keep their limits.
  - `MAX_IMPORT_IMAGES_SIZE` and `MAX_IMPORT_ATTACHMENTS_SIZE` stop bounding archives. Pictures and attachments share
    the disk budget: free space minus `MIN_FREE_SPACE` (on Android, iOS, and the desktop a quarter of what
    the import needs, at most 512 MiB, so a large import never takes the system to its own low-storage state and a
    small one is not refused on a phone with a few hundred MB free; 32 MiB of the origin's quota on the web). What an
    import needs is honest in the `NO_SPACE` sentence: the archive's copy plus what it unpacks, about twice the
    archive, where the in-memory path needed only the unpacked files; a received archive is deleted as soon as its
    unpack has finished, so the review holds only the staged files, and at most
    `MAX_IMPORT_STAGED_SIZE` (16 GiB) to bound the time one import can take. `MAX_IMPORT_IMAGES_SIZE` stays for picture
    links downloaded into memory (`IncomingPictureLinks`).
  - `MAX_ARCHIVE_SIZE` (88 MB) stays only as the selection-wide budget for files read whole into memory
    (`ImportBudget`).
- **Free space** is asked through a new `FileStorage.availableSpace(directory)`: `File.usableSpace` on the JVM,
  `NSURLVolumeAvailableCapacityForImportantUsageKey` on iOS, `navigator.storage.estimate()` (quota − usage) on the web. The figure is approximate everywhere and deliberately
  imprecise in Safari, so it is only the early answer; a write refused for space (`OutOfSpaceException` from the
  fixes plan, `QuotaExceededError` on the web) is the real one and ends in the same `NO_SPACE`.
  Checked before the copy (with the size the platform reports; Android may report none, then as it runs), before
  unpacking (declared sizes), and a write that fails anyway is the same clean failure.
- **A new failure, `NO_SPACE`**, in `ImportFailure`: one sentence naming how much space the import needs, Try again
  offered. `TOO_LARGE` now names 16 GB. Both in every language. A malformed ZIP64 record or a split archive is a
  damaged archive and gets the existing sentence for one. The one other new failure is `WRAPPED_ARCHIVE` (step 3), an
  archive past 4 GB written without ZIP64, with its own sentence in every language.
- **Progress is determinate in bytes.** `ImportProgress` gains `completedBytes` and `totalBytes`; `RECEIVING` (the copy)
  and `UNPACKING` report them, the Loading stage says "Unpacking 340 MB of 1.2 GB", and `WorkProgressBar` fills for
  those phases as it does for writing. Downloads report bytes against `Content-Length` where the host sends one.
- **Downloads stream into staging.** `ImportDownloadRemoteSource.download` takes a sink (`suspend (ByteArray, Int, Int)
  -> Unit`, as `ExportSink` does), so `:data:source:remote:implementation` still never touches `FileStorage`; the body is
  read from `bodyAsChannel()` in chunks within `MAX_STAGED_ARCHIVE_SIZE`. `downloadPicture` stays in memory. A staged download has no total time
  limit (the web's 10-minute `BROWSER_REQUEST_TIMEOUT_MILLIS`, `HttpClientConfiguration.kt:43, 86`, would end a large
  one however well it runs), only a stall limit: no byte for 60 seconds. A shared link's zip comes without
  `Content-Length`, so its progress is the bytes received, with no total.

## Steps

Each step compiles, passes `desktopTest`, `spotlessCheck`, the iOS simulator storage tests, the web's storage contract
test (from step 6 on), and the UI tests, and is committed on its own. **Steps 1–13 change nothing a user sees**, except
that from step 4 on an export past 4 GB or 65,534 entries is written (as ZIP64) where it was refused: the in-memory path
stays what production runs until step 15 switches it, so any of them can ship in a release on its own. Each step's
"Safety" line names what it must prove.

### 0. Spikes, before step 7

Three things the plan rests on cannot be known by reading. Each is about half an hour of throwaway code (scratchpad
only, never committed), and its outcome is written into the step it decides before that step starts.

- **Android, a content URI's lifetime** (decides steps 10 and 14): an `ACTION_VIEW` from Gmail or Drive with a 1 GB
  zip; start copying its stream; then press Back from the root, and separately swipe the task away. Does a stream
  already open keep reading after its grant is gone, for a file-backed and for a pipe-backed provider? Does passing
  the URI to a service with `FLAG_GRANT_READ_URI_PERMISSION` keep it readable?
  **Outcome (2026-11-17, API 37.1 emulator; file-, pipe-, MediaStore-, and DocumentsUI-backed URIs; Gmail and Drive
  untested, no account):** the activity's grant ends at `onDestroy` (Back or swipe) and nothing reopens it, but a stream
  already open keeps reading to the end while the process lives. Without a foreground service a swipe kills the process
  at once and Back lets the cached-app freezer stop the copy within about 10 s; with one, every copy finished. Passing
  the URI to the service with the grant flag works but is not needed. A provider process dying while
  `openInputStream`'s stable reference is held kills Campfire too (even in front); an unstable client
  (`acquireUnstableContentProviderClient(uri).openFile`) survives it, but a pipe then ends early as a clean EOF.
- **iOS, background work** (decides step 14): `BGContinuedProcessingTaskRequest` on iOS 26 with an exact identifier
  registered at launch against a wildcard one (registering one identifier twice crashes the app); and what happens
  when `beginBackgroundTask`'s time expires during a write that does not stop: does returning from the handler at once
  let the app be suspended rather than killed?
  **Outcome (2026-11-17, iOS 27.2 simulator):** registering one identifier twice throws
  `NSInternalInconsistencyException`, and so does submitting one never registered; registering a `.*` pattern itself
  returns false. What works is one plist entry, `<bundle>.import.*`, and a fresh concrete `<bundle>.import.<n>`
  registered just before each submit, in front (continued tasks are exempt from registering at launch). The simulator
  refuses every submit ("BGTaskScheduler is not available on this platform"), so the system's progress UI, its
  cancel, and running on are left to a device in step 17. `beginBackgroundTask` gives about 30 s; ending the task
  synchronously in the expiration handler while a write still runs lets the app be suspended (the write resumes on
  return), while not ending it gets the app killed about 5 s later (RunningBoard `0x2182BAD2`, not `0x8badf00d`).
- **Web, a hidden tab and the quota** (decides steps 6, 10, and 14): Chrome with Energy Saver on, a hidden tab copying
  into OPFS and inflating in Wasm for six minutes: is it frozen, do `delay`s stall? Safari before 26: a 2 GB `File`
  posted to the worker and written through a sync access handle near the quota: where does `QuotaExceededError` land?

### 1. SHA-256 in `:data:model`, incremental

**Landed** by item M1 of the fixes plan (`Sha256.Digest`, `Sha256.hashToHexYielding`).

Move `Sha256` from `:data:source:remote:api/hashing` to `:data:model/hashing` verbatim (its own commit, imports
updated), then add `Sha256.Digest` with `update(bytes, offset, length)` and `finishToHex()`. Tests: the digest over any
chunking equals `Sha256.hash` (random chunk boundaries from a fixed seed, empty input, 55/56/64-byte boundaries).
Safety: sync's fingerprints and content names are unchanged (existing sync tests pass untouched).

### 2. `ZipSource` and the reader over it

**Landed** (37fe8217c): `ZipReader.open(source, maxCentralDirectorySize, maxEntries)`, `ZipDirectory.walk(…, sinkOf)`; the caps are parameters so step 8 passes what remains of a request's budget. A source's read failure ends the walk as a storage failure (`ZipException` is internal).

- `zip/ZipSource.kt` and `ByteArrayZipSource`; `ZipReader.open(source): ZipDirectory` and a `suspend` walk with the same
  `EntryPolicy` / `sniffedLimitOf` callbacks, reading entries through `EntrySink`s (`ByteArrayEntrySink` for texts).
  The old `ZipReader.read(ByteArray)` is left exactly as it is.
- All current checks, plus `MAX_CENTRAL_DIRECTORY_SIZE` and `MAX_ARCHIVE_ENTRIES`, the directory kept compactly (see
  the safety section), the local header read once per entry through the source, and data descriptors (bit 3) handled
  as today: the central directory's sizes and CRC are the truth. Every size and offset is a `Long` from here on, added
  through one overflow-checked helper, so step 3 only widens where the numbers come from.
- Tests (`commonTest`): every `ZipReaderTest` case run through both readers with identical results; stored, deflated,
  data descriptors, UTF-8 and CP437 names, backslash separators, ZIP64 refused (until step 3), encrypted and unknown
  methods left out, overlapping entries, declared sizes past the archive, truncated at every byte of a small archive,
  comment of 65,535 bytes, central directory past the cap, a declared entry count past the cap refused before
  allocating. `ZipFuzzTest`'s mutants through both: the same entries or the same failure.
Safety: no regression in what an archive yields; hostile layouts refused as before.

### 3. Reading ZIP64

The streaming reader (only; the old `ZipReader.read(ByteArray)` keeps refusing ZIP64, as the differential test's
reference) reads every ZIP64 structure, with every existing check applied to the 64-bit values:

- **The ZIP64 end of central directory locator and record.** When the end of central directory record has a field at
  its marker (`0xFFFF` entries, `0xFFFFFFFF` size or offset), or a locator signature sits in the 20 bytes before it,
  the locator is read: its record offset within the file and before the locator. The record is read there: its
  signature, its declared size (at least the fixed 44 bytes, ending before the locator; the extensible data sector is
  skipped, never read), and the central directory's offset plus size (overflow-checked) at or before the record.
  **The 64-bit values win**: where the record gives a value it is the value, whether or not the 32-bit field is at its
  marker, and a 32-bit field that disagrees is not a reason to refuse (real writers leave stale ones). **The disk
  fields of the locator and of the record are not checked**: Windows Explorer and OneDrive write wrong ones in
  ordinary one-file archives. What refuses a split archive is the classic end record's own "number of this disk" or
  "disk where the central directory starts" being other than 0. The entry count is checked against
  `MAX_ARCHIVE_ENTRIES` and the directory's size against `MAX_CENTRAL_DIRECTORY_SIZE` before anything is allocated.
- **The extended information extra field (`0x0001`) in central headers**: it holds, in the format's fixed order, the
  uncompressed size, compressed size, local header offset, and disk number, each present only when its 32-bit field is
  at its marker. A marker without its value (the field missing or too short) is corrupt; values for fields not at
  their marker are ignored, as the format says; the disk number's value is ignored. Every extra field is walked within
  the header's declared extra length, each block's length within what is left, as the reader walks them today.
- **Local headers' ZIP64 fields are never read or validated**, only skipped by the declared extra length: Java's and
  Android's `ZipOutputStream` and Go's `archive/zip` write local headers without the field or with both sizes whatever
  the markers say. The central directory stays the truth, as today, where a local header's sizes are never read at all
  (`ZipReader.kt:217`).
- **Data descriptors are never read and never charged**: the overlap accounting charges compressed sizes only, as
  today (`ZipReader.kt:118, 131, 148`), and the central directory gives sizes and CRC. So a descriptor's width (12, 16,
  20, or 24 bytes) never matters, and no width is guessed from a local header.
- **An archive past 4 GB written without ZIP64 is refused with a sentence of its own, never recovered** (the owner's
  decision). macOS Archive Utility and `ditto`, and some Windows tools, write such archives with sizes and offsets
  wrapped around at 4 GiB and no ZIP64 records. Recognised as: the file is longer than `0xFFFFFFFF` bytes and has no
  ZIP64 locator. It is `ImportFailure.WRAPPED_ARCHIVE` (step 13), not the damaged-archive failure: the sentence says
  the archive is larger than 4 GB and was written by a tool that damages archives of that size, and to zip it in
  smaller parts or with another tool.
- **Real writers first.** Before any of the reader's ZIP64 code, in a commit of its own, the fixtures: small ZIP64
  archives from Java's `ZipOutputStream` (streamed, so bit 3 with ZIP64), Go's `archive/zip`, 7-Zip (one with more than
  65,535 entries), Info-ZIP `zip -fz`, and Python's `zipfile` with `force_zip64=True`, each beside the same content
  zipped without ZIP64; and sparse descriptions (headers only, through the sparse `ZipSource` below) of an Explorer or
  OneDrive archive past 4 GB (a locator saying zero disks, stale 32-bit fields beside the 64-bit ones) and of a wrapped
  Archive Utility one. Expected: every ZIP64 one reads as its twin, the wrapped one is refused as `WRAPPED_ARCHIVE`.
  What each tool really writes past 65,535 entries is recorded in the test's KDoc from these files, not assumed.
- **The safety checks at 64 bits.** Values with the top bit set are refused; every offset plus size is overflow-checked
  and must end within the file (before the central directory for entry data); declared sizes are charged before
  reading and held during inflation as today, now as `Long`s; compressed bytes are still charged once and never more
  than the archive holds; per-entry caps (pictures, attachments, texts) apply to the 64-bit sizes unchanged, so ZIP64
  never widens what one entry may be.
- Tests (`commonTest`, with the fixtures under `data/formats/src/commonTest/resources`): archives written by real tools,
  each small enough to commit, or generated in the test where not: Info-ZIP `zip -fz` (ZIP64 forced on small entries),
  Python's `zipfile` with `force_zip64=True` (entries, and the archive through its ZIP64 end records), macOS Archive
  Utility and Windows Explorer archives where available (one past 65,535 entries, which they write as ZIP64 at a few
  MB), each beside the same content zipped without ZIP64, which must read identically. The commands that made them are
  in the test's KDoc; the generators themselves are never committed. Archives built in the test: over 65,535 entries;
  offsets and sizes past 4 GiB described by a sparse `ZipSource` (a source that serves the headers and data at their
  far offsets without the bytes in between, so the over-4-GB layout is read in CI in milliseconds); mismatched 32 and
  64-bit fields (a 32-bit value not at its marker that disagrees with the record, read by the record's value; a marker with no extra field, an
  extra field too short for its markers, values for fields not at their marker); a locator pointing past the file,
  into the central directory, or at itself; a ZIP64 record whose size runs into the locator; an entry count of 2^63
  and of `MAX_ARCHIVE_ENTRIES` + 1; offsets with the top bit set; 8-byte data descriptors with and without their
  signature. `ZipFuzzTest` gains mutants of every ZIP64 fixture, with the mutator taught the ZIP64 structures so
  their fields are damaged on purpose: the same rule as today, a `ZipException` or the right entries, never an
  `Error` or a read past the budget, with `assertTakesTimeInProportion` on each.
Safety: no archive that reads today reads differently (step 2's parity cases pass untouched); a hostile ZIP64 archive
is refused at every field.

### 4. `ZipWriter` writes ZIP64 when an export needs it

`ZipWriter.write` writes ZIP64 only when an archive passes what the 32-bit format holds, and never otherwise:

- An entry whose local header offset is at or past `0xFFFFFFFF` (the marker's own value cannot be written as an
  offset; the 32-bit writer's guard is corrected for the same by the fixes plan's M5) gets the extended information extra field in its central
  header with that offset (sizes never need it: each entry is a `ByteArray`, and the local header is written after the
  entry's sizes are known, so no local extra field and no data descriptor are needed). The version needed to extract
  becomes 45 for that entry only.
- An archive whose central directory starts or ends at or past `0xFFFFFFFF`, or holds 65,535 entries or more, ends with a
  ZIP64 end of central directory record and locator before the end of central directory record, whose fields at their
  limits are set to their markers. The 65,534-entry and 4 GiB refusals are removed; a name longer than 65,535 bytes is
  still refused.
- Tests (`commonTest`): every existing `ZipWriterStreamingTest` and `ZipRoundTripTest` archive is byte-identical to
  what the writer wrote before this step (their bytes hashed and compared against values recorded from the code at the
  start of this step, which is after the fixes plan's M5 changed how an entry over 1 MiB is stored or deflated); exactly 65,534 entries writes no ZIP64 record, 65,535 does; an archive past 4 GiB written into
  a counting sink that keeps only the headers (so CI never writes 4 GB), whose ZIP64 records are read back by step 3's
  reader through a sparse `ZipSource`; small ZIP64 archives produced by the writer read by Python's `zipfile` and
  Info-ZIP `unzip -t` when the machine has them (skipped, not failed, where not).
- `data/formats/CLAUDE.md` (`zip/`) changes in this step: ZIP64 read by the streaming reader and written past the
  format's limits.
Safety: every export under 4 GB and 65,535 entries stays byte-identical; a large export becomes possible and is read
back by every ZIP64 reader the tests can reach.

### 5. The streaming inflater

**Landed** (b7ffaff9d), before step 2, whose walk inflates through it.

The second `Inflater` entry point described in the decisions, writing to an `EntrySink` with CRC-32 and SHA-256 updated
per chunk, yielding every 256 KiB. Tests: random and real-world streams (every fixture, `Deflater`'s output, stored
blocks, dynamic and fixed blocks, distances of 32,768, a stream ending exactly on a buffer boundary) inflate to the
same bytes as `Inflater.inflate`; malformed streams fail alike; a stream producing more or less than declared fails
before the sink closes; cancellation mid-entry abandons the sink; `assertTakesTimeInProportion` on a highly repetitive
stream. Safety: bounded memory per entry, no behaviour change for the PDF reader (untouched).

### 6. `FileStorage`: a staging folder, random reads, moves, and free space

**Landed** by item M3 of the fixes plan, with `copyInto` beside `moveInto` (a copy by the system, renamed over the
target) for the editor's Save; the web's storage moved under `WebFileStorage` over `OpfsFileStorage(rootSegments)`, so
its contract test has a folder of its own per test.

- `StorageDirectory.IMPORTS` → `imports/`: Android under `filesDir` (never `cacheDir`, which the system may clear while
  the app runs), iOS under `Library/Application Support` with the backup exclusion set on the folder (never
  `Caches`, which the system may purge while the app is suspended with a review waiting, taking the staged files with
  it), desktop and web beside `exports/`. Not in `app/android/src/main/res/xml`'s backup rules, which are an
  allow-list.
- `openSource(directory, name): FileSource` (random reads at 64-bit positions; JVM `FileChannel` positional reads,
  opened with `StandardOpenOption.READ` only, iOS `pread` with a 64-bit `off_t`, web `getFile()` then
  `slice(...).arrayBuffer()` across the boundary in `LATIN1_CHUNK_SIZE` chunks: new code, since `OpfsBytes.kt` reads a
  file whole today and only crosses the boundary in chunks; a `Long` position crosses into JavaScript as a `BigInt` and
  is converted to a Number first, and the `File` snapshot is taken once per source, not per read),
  `openSink(IMPORTS, …)` (already written for `EXPORTS`), `moveInto(from, name, to, name)` (atomic where the platform
  can, see the decisions), and `availableSpace(directory)`.
- Contract tests in `FileStorageContractTest`: reads at every offset, a read past the end, a source of a file deleted
  meanwhile, a move over nothing and over an existing file, a move of a missing file, available space positive. Run on
  the desktop and the iOS simulator. The web gets a `wasmJsTest` source set in `:data:source:local:implementation` that
  runs the same contract test over `OpfsFileStorage` in headless Chrome (`wasmJsBrowserTest`), added to
  `.github/workflows/tests.yml` in this step as its own step, so every PR and every run before `publish-all.yml` checks
  the web's storage as it does the desktop's and iOS's; `opfs-writer.js` gains a `stage` request (a `File` posted to
  the worker, written with a sync access handle in slices) with Node tests beside the existing ones.
Safety: the library's directories behave exactly as before (the existing contract tests pass untouched).

### 7. The staging sink and the session

**Landed** (e190478f8, ea839387a): an unpacked file is written as `<session>-<request>-<n>.part` and renamed to its hash on finish (on the web a rename rewrites the file, one extra copy of at most 50 MB); a received archive is not hashed (`StagedFile.sha256Hex` is nullable); `MIN_FREE_SPACE` is `ImportLimits.minFreeSpaceFor(needed, isQuota)`.

`local/commonMain/.../staging/ImportStaging`: names (`<session>-<request>-<n>.zip` for a received archive,
`<session>-<request>-<sha256>.<ext>` for an unpacked file), a sink that writes, hashes, and counts against a budget and
deletes its file when abandoned, `deleteRequest(request)`, and `deleteOtherSessions()`. A `ClearImportStagingUseCase`
runs at launch beside `ClearExportFilesUseCase`. Tests (desktop): a partial file never survives a failure, a
cancellation, or a budget overrun; other sessions' files go at launch, this session's stay; disk full (a `FileStorage`
fake throwing on write past a size) fails cleanly with nothing left behind.

### 8. The streaming unpack, beside the old one

- `ArchiveLocalSource.unpack(archive: StagedFile, …)` beside the `ByteArray` one: `ArchiveLocalSourceImpl` and
  `ArchiveSongImages` walk a `FileSource` with the new reader, song texts into memory as today, pictures and attachments
  into staging sinks, nested archives staged and walked. The policies (`limitOf`, `sniffedLimitOf`, hidden entries,
  content-named attachments skipped, SongbookPro, other apps' documents) are shared code with the old path, extracted
  verbatim first in a commit of their own so the two cannot drift.
- The disk budget is checked from declared sizes before the picture and attachment walks start; the request-wide
  `MAX_ARCHIVE_ENTRIES` and the held directories' `MAX_CENTRAL_DIRECTORY_SIZE` are counted across nested archives.
- Tests: `ArchiveLocalSourceTest` and `ArchiveImagesTest` run against both unpacks (parameterised), with staged bytes
  equal to the old bytes; nested archives that together pass the entry or directory cap refused cleanly.

### 9. Staged content through the plan and the write

`StagedFile`, `IncomingContent`, the `ImportedFile` / `ImportedImage` / `ImportPlan.SongImage` changes,
`ImageRepository`'s and `LibraryImageWriter`'s overloads with the chunked comparison, `ImageLocalSource.moveStagedFile`,
and `IncomingImages.planned` / `store` handling both kinds. `PrepareImportUseCaseImpl` routes a staged archive to the
new unpack; nothing produces one yet. Tests (`domain/implementation`): `PrepareImportUseCaseImplTest`,
`ImportFilesUseCaseImplTest`, and `ApplyImportUseCaseImplTest` run their archive cases through both kinds with
identical plans and libraries; a move failing midway leaves the songs unwritten and the prune finds the moved files.
Before its first write, Import checks that every staged file its plan names still exists with its size; one missing
fails the import cleanly with Try again and nothing written (tested by deleting a staged file between the plan and the
write). `withImagesKept` holds sync's file writes for as long as it is held (`data/sync/implementation/Module.kt:70`):
measure a multi-GB Import, and take it per song (the files just before the song naming them, which is all the rule
needs) if a sync run would otherwise stall for minutes.

### 10. Intake: receiving into staging

- A pure decision in `:data:model` (`ImportIntake.decide(name, size, head)`: read whole within a limit, stage, or leave
  unread), replacing the archive branch of `ImportBudget.read`, tested against every `ImportLimitsTest` case.
- Android: `toImportedFiles` opens each URI once, peeks the head, and either reads as today or hands an `IncomingFile`
  whose copy (the stream into `ImportStaging`) starts at once on `Dispatchers.IO`. The copy itself is
  `jvmSharedMain` code taking an `InputStream`, so the desktop tests cover it; Android supplies only
  `acquireUnstableContentProviderClient(uri).openFile(uri, "r")` (never `openInputStream`, whose stable reference
  takes Campfire down with a dying provider: step 0), and a copy shorter than `OpenableColumns.SIZE`, where the provider
  gives one, fails as unreadable (a dead pipe reads as a clean end; without a size, the reader's missing end record
  refuses the truncated copy).
- Desktop: `readAsImportedFiles` the same over `Files.newInputStream` (never `FileInputStream`: on Windows it opens
  the file without delete sharing, so the user's file would be locked for the length of the copy), closed as soon as
  the copy ends.
- iOS: nothing waits behind a copy. `IosFileImport.openUrl` queues the request first, so Loading shows at once, and
  copies in the request's own job, not on `inboxScope` (`IosFileImport.kt:58`, `limitedParallelism(1)`, which a file
  iCloud has not downloaded yet would block for its whole download, with later opens and the share inbox's drains
  behind it); deleting a `Documents/Inbox` file (`cleanImportInbox`) stays serialized after its move. The copy runs
  inside the coordinated read and the security-scoped access (both must outlive it), off the main thread. A
  `Documents/Inbox` copy, a picker copy (`asCopy`), and the share inbox's file are **moved**, not copied (a move within
  the volume costs no disk; the share inbox's folder is still deleted once the import settles). The iOS
  `LibraryDropTarget` and `IosPhotoPicker` get a provider's temporary file that exists only inside its completion
  handler (`loadFileRepresentationForTypeIdentifier`): it is moved into `imports/` inside the handler, a rename, never
  a copy there. The share extension's `maxArchiveSize` (24 MiB today, `ShareInboxWriter.swift`) rises to
  `MAX_STAGED_ARCHIVE_SIZE` only together with a free-space check before it copies: its copy into the App Group inbox
  is the one copy an extension cannot avoid, and the app then moves it. The inbox bound stops counting archives
  against its 64 MB.
- Web: the picked or dropped `File` is piped into OPFS in JavaScript, never crossing into Wasm.
- Behind `IncomingFile` nothing is queued differently: a request with a receiving file shows Loading at once.
- Not yet switched on: the intake still reads archives whole until step 15, behind one `ImportIntake` constant.

### 11. The differential test

`ArchivePathParityTest` (desktop, `:data:source:local:implementation`, with the domain's planner wired over real
sources): every archive the tests build, the committed fixtures (`upgrade-*`, the backup tests' archives, the ZIP64
fixtures), the fuzz mutants of each, and every archive-like file (`.zip`, `.sbpbackup`, `.sbp`, `.osb`, and zips by
their bytes) under `campfire.importCorpus` when set, run through the in-memory path and the streaming path. Required:
identical `ImportPlan`s (songs, setlists, every problem, skipped, oversized, and unreadable names, content names) and
identical library files after Import. The allowed differences, each checked rather than skipped:

- An archive that hit the old picture, attachment, or nested archive budgets may bring more files in the new path; the
  test then requires that every other field is identical and that each difference is a file the old path reported
  `TOO_LARGE` for its budget.
- An archive the old reader refused as ZIP64 is read by the new path; its result must equal the new and the old path's
  result on the same content without ZIP64 (step 3's fixture pairs, and each corpus ZIP64 archive rewritten without it
  in the test where it fits under 4 GB and 65,535 entries).

Runs in CI with the committed set. **It is the gate for the switch**: step 15 does not land until it passes in full
against every fixture, every fuzz mutant, and the private corpus (by hand, the corpus being outside git), and it is run
against the corpus again before the release.

### 12. Downloads into staging

`ImportDownloadRemoteSource.download` with a sink and a byte progress callback; `ImportDownloadRepositoryImpl` stages
into `imports/`; `SharedLinkDownload` and `ImportWork.prepare` carry a `StagedFile`, so Try again reads the same
staged copy again. Tests (`:data:source:remote:implementation` desktop, with Ktor's mock engine): a body streamed in
chunks, cut short, larger than announced, larger than the cap, cancelled midway, each leaving no staged file. On the web,
check that Ktor's wasmJs engine reads the body as a stream (`body.getReader()`); where it buffers, the download is a
`fetch` piped into OPFS in a `js()` block instead. The staged download carries no total timeout, only the stall limit
of the decisions; a mock-engine test stalls a body and sees that limit end it with no staged file left.

### 13. Progress, Cancel, and the new failures in the UI

`OutOfSpaceException` and `Throwable.isOutOfSpace()` (`:data:model`, from the fixes plan's M7) are the predicate `NO_SPACE`
reuses: every platform's storage sets it as the cause of a write refused for space.

`ImportProgress` bytes, `RECEIVING`, the Loading stage's byte text (`ImportProgressContent`, tested), `WorkProgressBar`
filling for those phases (`workProgress`, tested), `ImportFailure.NO_SPACE` and `TOO_LARGE` naming 16 GB in every
language (`values`, `-hu`, `-ro`, `-de`, `-es`, `-fr`, `-it`, `-he`, `-pt`, `-pl`, `-cs`), with plurals where counted.
A malformed ZIP64 record or a split archive reaches the existing damaged-archive sentence; no new one is added for it.
Cancel during `RECEIVING` and `UNPACKING` deletes the request's staging before the screen leaves. UI tests in
`:tools:screenshots` (`importReport/ImportReviewTest`, `songDetails/ImportPreviewTest`): determinate progress during a
staged unpack, Cancel mid-unpack returning to where the user was with `imports/` empty, the no-space failure and its Try
again, and every existing import UI test passing unchanged.

### 14. Leaving the app never loses an import: a foreground service and its equivalents

**First commit landed** (28ce45130): the holder is `ui/state/importing/ImportWork` over an `ApplicationScope`, with `state` (`ImportWorkState`) and `cancel()`; it runs the launch's staging cleanup once per process. The platform parts below remain.

Every import that downloads (a shared link) or unpacks an archive keeps the app working while that work runs, on every
platform, the way a sync run already does, so leaving the app never loses an import halfway, whatever its size. Today's
in-memory path is covered too from this step on, so this is proven before step 15 switches the intake.

**The work belongs to the app.** Today `ImportController` prepares in `viewModelScope`
(`CampfireViewModel.kt:505–506`), so Back from the root or a swipe from recents on Android clears the view model and
cancels the preparation, and a service would keep an empty process alive; only the Import write is `NonCancellable`.
First in this step, in a commit of its own: receiving, downloading, unpacking, and writing run in an app-scoped holder
(a Koin `@Single` with its own scope, as a sync run belongs to the app: `data/sync/implementation/CLAUDE.md`), which
keeps the request queue, the job in flight, and the prepared plan. `ImportController` becomes its view, and a new view
model attaches to whatever is running or waiting: a preparation that finished while nobody watched opens its review
when the app is next in front. The session of step 7 is the process. The holder exposes one import-work state
(receiving, downloading, unpacking, writing, or idle, with byte progress) that each platform watches, and one cancel
that behaves exactly as the screen's Cancel, its staging deleted (step 13) under `NonCancellable`. Cancel is offered
while receiving, downloading, and unpacking. **The Import write cannot be cancelled** (it is `NonCancellable`, so a
library is never left between a song and its files), so no platform's cancel or expiry waits for it: an expiry handler
ends its background task at once and lets the write finish or the process be suspended. The review waiting for the
user is not work: nothing is held while it sits on screen or in the background. Tests: the holder outliving a cleared
view model with its job still running and its plan delivered to the next one (`commonTest`), the state's transitions,
and each platform's start/stop decisions as plain logic where they can be, like the sync starters'.

- **Android: a `dataSync` foreground service**, exactly as `CampfireSyncService` (`app/android/CLAUDE.md`).
  `CampfireImportService` beside it does no importing; it only keeps the process alive, watches the import-work state,
  and stops itself, after `startForeground`, once that reads idle, with the same short grace as sync so a download
  followed by its unpack keeps one notification. Started and stopped by an `ImportServiceStarter` as `SyncServiceStarter`
  does (`onPause`, not on rotation; at once for work starting after that; stopped in `onResume`). The notification names
  the phase and its byte progress (at most twice a second, as `SyncNotificationScheduler`), with a Cancel action; its
  strings arrive in the intent, in every language. Android 15 gives `dataSync` six hours a day across both services:
  `onTimeout` cancels the import (the screen showing why, with Try again) and stops the service. An import and a sync run
  at once never block each other and keep a notification each. A content URI's grant ends with the activity's task: its
  stream is opened in the activity at once by the request's copy job (step 10), and the
  URI is not passed on to the service (step 0: the open stream keeps reading; the service only holds the process,
  which it must, or a swipe kills the copy and Back lets the freezer stop it). The manifest already declares
  `FOREGROUND_SERVICE_DATA_SYNC`; the Play Console's `dataSync` declaration gains the import as a use.
- **iOS: a continued processing task, with background time as the fallback.** On iOS 26 and later the import, which
  always starts from a user's action in front, submits a `BGContinuedProcessingTaskRequest` (identifier in the Info
  plist's `BGTaskSchedulerPermittedIdentifiers`) and reports the byte progress to it, so the system shows its own
  progress and lets the user cancel it, which cancels the import; the task ends when the state reads idle, and its
  expiration cancels the import cleanly. Before iOS 26 (the app supports 15.3) it holds a `beginBackgroundTask` for the
  work as `IosSyncNotifier` does for a run; when iOS ends that time the import is cancelled cleanly and the screen offers
  Try again on return. Either way a kill loses only the preparation. Identifiers as step 0 found: one
  `$(PRODUCT_BUNDLE_IDENTIFIER).import.*` entry, a fresh `….import.<n>` registered per import, never reused in a
  process, a failed submit (the simulator always) falling back to `beginBackgroundTask`; `progress` in bytes updated
  steadily (a stalled task may be expired), `setTaskCompleted` when the state reads idle. Every expiration handler only
  asks the import to cancel and returns, `endBackgroundTask` called synchronously inside it; the cancel's cleanup may run
  only after a suspension, on return to the front, and must be safe there. Lives in
  `app/ios` beside `IosSyncNotifier`, which `app/ios/CLAUDE.md` gains a line about.
- **Desktop: quitting asks first.** The process keeps running in the background by itself, so nothing holds it; but
  closing the window or quitting (including Cmd+Q, through the same kept quit request the editor's unsaved text uses in
  `CampfireDesktopApplication.kt`) while an import works asks whether to stop it, and stopping cancels it cleanly before
  the app exits. A logout or shutdown that asked is answered once the cancel has deleted the staging.
- **Web: closing the tab warns.** While an import works, `beforeunload` warns as it does for the editor's unsaved text
  (`CampfireWebApp.kt`); a hidden tab keeps importing as far as the browser lets it: nothing in the import waits
  on the frame clock (a hidden tab stops `requestAnimationFrame`) or on `delay` and `withTimeout` (a hidden tab's
  timers run once a minute after five), the OPFS worker's progress still arrives, and the Loading stage says to keep
  the tab open. Chrome may still freeze or discard a hidden tab that works for minutes (Energy Saver); that is a
  stated limit, which step 0's spike measures, not something the app can prevent. A closed tab loses only
  the preparation; the next launch empties the staging.
- **Safety:** none of these touches the library or staging; a kill with or without them loses only the preparation
  (staging cleaned at the next launch, step 7), never a library file (the Import write moves files atomically, step 9). A kill
  during the Import write leaves what was written so far (songs with their files, the setlists not yet), as today but
  for longer with large files: importing the same archive again completes it without duplicates (step 16).
- **Release check (step 17):** leave the app during a download, an unpack, and an Import write on every platform:
  Android on the 360 × 640 dp emulator and a device (Cancel from the notification, one alongside a sync); iOS 26 and an
  iOS before 26 (the system's progress and its cancel; the fallback's clean cancel on a long import); desktop quitting
  with Cmd+Q and the window's close; web a hidden tab finishing and a closed tab warning.

### 15. Switch: every archive streams

The intake stages archives and `PrepareImportUseCaseImpl` unpacks staged archives only: from this step on the streaming
path is the only one production runs, with no fallback to the in-memory one, whose code stays only as the differential
test's reference (step 18 removes it). **This step lands only once step 11 has passed in full, the private corpus
included, and it does not ship until step 17 has passed on every platform.** Export's `isTooLargeToImport` and
`export_too_large_to_import` change to the new limits (an export over 16 GB, with more than `MAX_ARCHIVE_ENTRIES`
entries, or with more song text than `MAX_IMPORT_SIZE`), in every language; an export past 4 GB or 65,534 entries is
ZIP64 (step 4) and imports back, so it is no longer flagged for that. `ImportLimits`' KDoc, `data/formats/CLAUDE.md`
(`zip/`), `data/source/local/implementation/CLAUDE.md` (`IMPORTS`, staging), `domain/implementation/.../importing/CLAUDE.md`,
`ui/screens/importReport/CLAUDE.md`, `ui/progress/CLAUDE.md`, `app/ios/CLAUDE.md` (share extension limits), and the root
`CLAUDE.md`'s library layout (`imports/<name>`, emptied on launch) are updated in this step.

### 16. The large-archive test

`LargeArchiveImportTest` (desktop, `:data:source:local:implementation`): an archive generated on the fly into a temp
folder with `ZipWriter`'s streaming `write` (never committed): thousands of songs naming stored and deflated pictures and
attachments of pseudo-random bytes from a fixed seed, nested archives (one of them ZIP64), and data descriptors. It is
received, unpacked, planned, and imported in a JVM capped at 128 MiB of heap (its own Gradle test task,
`maxHeapSize`), asserting the library's file hashes, an empty `imports/` afterwards, and that peak disk use stayed within
what the ledger reserved (the received archive, each nested archive while it is walked and deleted once it was, and
the staged files): about twice a plain archive, more for one that nests. The size is the `campfire.largeArchiveTest` property (default in `gradle.properties`):

- **In CI**: a 600 MB archive; a ZIP64 archive of 70,000 small entries (past the 16-bit count, a few MB); one of
  `MAX_ARCHIVE_ENTRIES` entries, read under the 128 MiB heap to prove the compact directory's bound; one of
  `MAX_ARCHIVE_ENTRIES` + 1, refused cleanly before allocating.
- **By hand before the release** (`-Pcampfire.largeArchiveTest=5g`): a 5 GB archive, ZIP64 by size, written by
  `ZipWriter` and imported back whole.
- **Export, then import**: a library exported through the export use case and imported into an empty library through
  the import use cases must come back file for file (songs, setlists, pictures, and attachments by hash). In CI with a
  library past 65,535 files (so the export is ZIP64 by count); by hand with the 5 GB size, so the export is ZIP64 by
  size.
- A variant cancels at random points and one fills a size-limited fake storage, both requiring no leftovers and an
  untouched library.
- **No regression in space**: an 80 MB archive that the in-memory path imports is imported with free space of twice
  its size plus the margin, and refused with `NO_SPACE` naming the amount with less.
- **A killed write, repeated**: the Import write stopped after a random song, a fresh start over the same library, the
  same archive imported again: the library equals a clean import's, with no numbered duplicates.
- **A staged file gone before Import** fails cleanly with nothing written.
- **The web's time**: the 70,000-entry archive unpacked by the wasm target in headless Chrome within a stated budget
  (each local header read once, the `File` snapshot reused), so three walks of promises do not turn into minutes.

### 17. Release check by hand, on every platform

Not done until every line passes on every platform, with the same archives each time: a library export of the owner's
library with pictures and attachments, a shared link opened from a message, a SongbookPro backup and an OnSong or
OpenSong archive, a generated > 1 GB archive, a generated > 4 GB (ZIP64) archive written by `ZipWriter` and one zipped
past 4 GB by the system's own tool (macOS Archive Utility, Windows Explorer) where the platform can (it either imports,
when the tool wrote ZIP64, or is refused with the wrapped-archive sentence, when it did not: never the damaged-archive
sentence and never a wrong import), a library export
past 4 GB exported and imported back, a small archive with ZIP64 forced (`zip -fz`), a corrupt one (truncated, and one
with a damaged entry), Cancel during receiving and during unpacking, and an import on a nearly full device. After
each: the review matches the same archive on the previous release (where the previous release could read it; a ZIP64
one matches its content zipped without ZIP64), the library holds what it should, `imports/` is empty after the import
and after the next launch, and the app stays responsive. The switch (step 15) ships only once this has passed on every
platform.

- **Android**: the small-heap emulator at 360 × 640 dp (Medium_Phone AVD, 720 × 1280 at density 320), picked, opened
  with, shared from Files and Gmail, dropped from another app in split screen; the process killed during receiving and
  relaunched; low disk by filling the emulator's storage.
- **iOS**: the simulator and a device; picked, opened in place from iCloud Drive (not downloaded yet), AirDrop, the share
  extension, a drop on iPad; the app sent to the background during unpacking.
- **Desktop**: macOS, Windows, and Linux as available; picked, dropped, opened with while running and cold; on Windows,
  the source file renamed and deleted during the copy and during the review (it must not be locked).
- **Every platform**: leaving the app during an import, as step 14 lists.
- **Web**: Chrome and Safari (Safari 26 and an older one without `createWritable()`), picked and dropped; the tab
  closed during unpacking and reopened; `navigator.storage.estimate()` near its quota.

### 18. Remove the old path, before the release ships

Nothing in production runs the in-memory path after step 15, so it stays only as the differential test's reference
until step 17 has passed on every platform, and is removed then, before the release ships (not the release after):
`ZipReader.read(ByteArray)`, the `ByteArray` unpack, the `ImportIntake` switch, and every test that only fed the old
path. `DocxTextExtractor`, the old reader's other caller, moves first, in a commit of its own, to the new reader over a
`ByteArrayZipSource` (it is already `suspend`), with the Word tests and their fuzz test passing unchanged. The
differential test becomes the parameterised tests' new-path half. The full test suite and step 16's CI runs pass after
the removal, and the release is built from that commit. Then delete this plan.

## Not in this plan

- **Sync's whole-file transfers** (Dropbox uploads and downloads of library files, held in memory one at a time).
- **Picking or opening a single attachment** (50 MB in memory, `MAX_ATTACHMENT_FILE_SIZE`) and single pictures: the
  fixes plan removed the extra copies and the hashing on the main thread; the one array stays.
- **PDF and Word conversion**, which keep `MAX_DOCUMENT_FILE_SIZE` and the in-memory `Inflater.inflate` (a Word
  document moves to the new zip reader in step 18 but is still read whole, within its limit).
- **Shared copies** (`SharedCopySync`), still packed in memory within `MAX_SHARED_COPY_SIZE` and
  `MAX_SHARED_ATTACHMENTS_SIZE`.
- **Larger song budgets**: `MAX_IMPORT_SIZE` stays 24 MiB of text; a library of more songs still imports in parts.
- **Dropped folders**, walked file by file as today (an archive inside one is staged like any other).
- **Split (multi-disk) archives and ZIP64's encryption and central directory compression**, refused as damaged.
