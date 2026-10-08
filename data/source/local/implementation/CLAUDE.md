<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:source:local:implementation

File-backed multiplatform implementation of `:data:source:local:api`, on all four platforms. Koin wiring: `Module.kt`
holds the `@Module @ComponentScan object DataLocalSourceModule`, and every local source is a `@Single`. A file it skips
or cannot read is written to the injected `Logger` (`:data:model`, provided by `DataRepositoryModule`).

- **`storage/file/FileStorage.kt`** is the only thing that differs per platform: flat file access inside the app's own
  data directory, addressed as `(StorageDirectory, file name)` — no paths, no sub-directories. `info` is what `list`
  would say about one file, and `listNames` returns unfiltered names without opening them, so that saving a song (or writing one file of an import) does not list the directory
  — on OPFS a listing opens every file for its size and date, which made an import quadratic. `readTexts` reads a
  batch of files with one answer per file (text, missing or failed, a failure never the batch's): the setlist scan
  reads through it, and OPFS answers the whole batch with one call into the browser rather than a handle, a file
  and a buffer awaited in turn for every name, while the other storages keep the default, their reads in parallel.
  The OPFS listing opens its files in parallel too. The song scan uses `listForScan` / `readScan` instead: it lists
  names only and reads each file's size and date with its text (a file over the size limit reported and never read),
  so the first batch waits for 64 files rather than a stat of every one of them — on the web one `getFile()` per song
  instead of two. iOS lists with its attributes prefetched and goes through the defaults, which carry them. The Koin definition
  is a `@Single` class in each platform source set, found by the module's component scan: `AndroidFileStorage` and
  `DesktopFileStorage`, both delegating to `JvmFileStorage` over `java.io.File` (one class in `jvmSharedMain`, the source
  set the `campfire-library` convention plugin gives the two JVM targets, so `JvmFileStorageTest` covers what Android
  runs too),
  `IosFileStorage` over `NSFileManager` and `OpfsFileStorage` over the browser's Origin Private File System. The Android one takes the `Context` the app shell
  hands to Koin, marked `@Provided` since no shared module declares it. Everything above this line is `commonMain`.
  - The directories are `library/songs`, `library/setlists`, `preferences` and `covers` — songs and setlists sit next
    to each other so that the library exports as one archive, and the preferences and the covers sit outside it so
    that they do not.
    Android's backup rules in `:app:android` name `library/` and `preferences/preferences.json` by path, so moving a
    directory or renaming the preferences file means changing those two XML files as well. iOS backs up both of its
    directories whole, so what must stay behind says so itself: `SyncIndexLocalSourceImpl` calls
    `keepOutOfDeviceBackup` after every write of `sync-index.json` and of the forget-pending note, and
    `EditorDraftLocalSourceImpl` after every write of `editor-draft.json` and `CoverArtLocalSourceImpl` after every cover, which `IosFileStorage` answers by setting
    `NSURLIsExcludedFromBackupKey` again — it is an attribute of the file, and the atomic write replaces the file.
    Everywhere else it does nothing.
  - Text is written as UTF-8 and read through `:data:model`'s `decodeLibraryText`, the same rule the import uses: UTF-8
    when the bytes are valid UTF-8, and otherwise Windows-1250 for a file whose bytes can only be Hungarian or Polish and
    Windows-1252 for everything else (a UTF-8 file with a few stray bytes keeps its UTF-8 and reads only those through
    the code page, see `:data:model`), and a byte order mark stripped, because editors on Windows write one. A file read
    through the fallback is written back as UTF-8 on its first save, which keeps its accents rather than replacing them.
    The web decodes valid UTF-8 without a NUL in the browser (`TextDecoder`, which gives the same string) and falls
    back to `decodeLibraryText` otherwise; bytes cross the Kotlin/Wasm boundary as a string of one character per byte,
    since the typed arrays of `kotlinx-browser` are copied one call per byte.
  - Writes are atomic on the three platforms that can be (a `.campfire-<number>.tmp` temporary file of its own per write,
    named without the target so a name at the file-system limit still saves, flushed to the device
    and moved over the target on the JVM, `atomically` on iOS), so a crash in the middle of a save cannot truncate a
    song. OPFS has no such primitive. Where the browser has no `createWritable()` (Safari before 26, every browser on
    iOS 18), a write is handed to `app/web`'s `opfs-writer.js`, a dedicated worker, since `createSyncAccessHandle()`
    exists nowhere else: it is given the directory by its path segments and the content as bytes, text encoded as
    UTF-8 on the way. One worker serves the page, started by the first write that needs it, with its replies matched
    to the writes by id; one that fails as a whole fails the writes waiting on it, and the next write starts another. Without `createWritable()`'s swap file a write is in place, so the worker keeps the previous
    content and puts it back when a write fails part of the way, and journals every write so that a worker terminated
    in the middle of one (a tab closed or reclaimed during a save) leaves a file that is whole, old or new (see
    `app/web`); the page creates nothing on that path, and the worker creates a new file only after its commit marker,
    so a first write cut short leaves no file at all or the whole new one. With `createWritable()` a new file is created
    behind an empty `<name>.campfire-new` marker of the page's own, removed once the write is closed, which the recovery
    uses to remove an empty leftover of a page closed mid-write (a name within the marker's 12 bytes of Safari's
    255-byte limit is written without one). The journal is played back for a whole directory before its handle is first
    handed out, and again after a write that failed, and its `.campfire-tmp`, `.campfire-commit` and `.campfire-new`
    files are left out of every listing, so nothing above the storage ever sees them. The OPFS storage
    resolves the directory handles once and keeps them: walking down from the root is three promises, and
    nothing outside the page can remove a directory from the origin private file system.
  - Every `catch (Exception)` around a read rethrows `CancellationException` first: a scan that was cancelled is not
    a library of unreadable songs.
  - A read answers null for a file that is **not there** and for nothing else. A file that is there and cannot be
    read, written or deleted, and a directory that is there and cannot be listed, throw `LibraryStorageException`
    (from `:data:source:local:api`) on every platform, so that sync reports a storage failure rather than an unknown
    one: iOS asks
    `dataWithContentsOfFile` for its `NSError` rather than taking its nil as absence, the JVM wraps the `IOException`,
    and OPFS folds only a `NotFoundError` into null. Sync is why: a file reported as missing is planned as a deletion,
    and that deletion reaches every other device. The song scan skips such a song with a log line; a sync run leaves
    it out on both sides, keeps its index entry for the run that can read it again, and names it among the run's
    failures — unless not one file of the library could be read, which is the storage failing, and ends the run as a
    storage failure. On OPFS an entry that disappears between the directory listing and the question
    about its size is left out of the listing rather than failing it, since a sync run deletes files while the live
    rescan is listing the same directory: that is a file no longer in the directory, not a read folded into null.
    A file removed between the existence check and the read is not there either: the JVM catches
    `java.nio.file.NoSuchFileException` (not Kotlin's class of the same name), iOS asks `fileExistsAtPath` again when
    the read comes back nil, and OPFS folds a `NotFoundError` from `getFile()` into null, as `fileInfo` already did.
  - iOS splits the two: the library goes to the documents directory, where the Files app can reach it, and the
    preferences and the covers to application support, where it cannot.
  - The JVM storage removes its own temporary files older than an hour on first touching each directory. On Windows
    (`isWindows`, which also decides the retry below) it stores device names such as `con.cho` with a leading
    underscore and reports the ordinary library name back. A name that is a path or nothing (`/`, `\`, `.`, `..`) is
    refused by `canHoldFileName` on every platform, since no storage here can address it. A name Windows cannot hold at all (`? : * " < > |`, a
    trailing space or dot, a control character) is refused by `canHoldFileName` rather than mapped: a device name is
    a dozen anchored cases whose escape can itself be escaped, while these can sit anywhere in a name and no escape
    character exists that a name could not also contain — and a name is a song's identity, so a mapping that failed
    to reverse would send a second copy of the song to every device.
  - The JVM storage reads through `Files.readAllBytes` rather than a `FileInputStream`, which Windows opens without
    sharing deletion: a scan reading sixty-four files at once would otherwise refuse the renames and deletions of a
    sync run writing at the same time. The move over the target and the delete retry three times (20, 40 and 80 ms) on
    a Windows `AccessDeniedException`, which is an anti-virus scanner holding a file that just appeared; anywhere else
    that exception is a permission, and is reported at once.
- **`storage/secret/SecretStore.kt`** is where the sync credentials go, and nothing else: a refresh token is a
  long-lived credential. `AndroidSecretStore` encrypts it with an AES-GCM key generated inside the Android Keystore
  (never `security-crypto`, which is deprecated) and writes the IV and ciphertext as `preferences/sync-credentials.bin`;
  a key the system permanently invalidated, or a file whose tag does not verify, is deleted and reads as no
  credentials, so the user connects again rather than being stuck. Any other Keystore failure (`KeyStoreException`,
  `UnrecoverableKeyException`, which some devices answer with for a moment) is thrown as a `LibraryStorageException`
  and the key and file are kept: that launch reports the connection as not readable (`ConnectionFailed` with a storage
  reason) rather than as disconnected, nothing is written over the file, and the next launch reads it again;
  `IosSecretStore` does the same for any Keychain status other than not-found. `IosSecretStore` is a Keychain generic password, readable after the first
  unlock because a background sync may need it on a locked device, and bound to the device
  (`AfterFirstUnlockThisDeviceOnly`) so that it stays out of the backup, as Android's does; an item an older version
  wrote without that is moved over when it is read. The item survives an uninstall, which is why a first launch
  forgets it (`SyncRepository.forgetStoredConnection`), and why a launch that could not goes on trying at every start. Desktop and the web get the common `FileSecretStore` (through `DesktopSecretStore` and `WebSecretStore`), the
  plain `preferences/sync-credentials.json` it always was: no desktop keychain is worth a native dependency per
  operating system, and the browser has none. `SyncCredentialsLocalSourceImpl` moves a plain file left by an older version
  into the store on the first read and deletes it, which on desktop and the web is a no-op, since the store found
  that file itself. The index stays a plain file everywhere: it holds hashes and revisions, nothing secret.
- **`FileNames.kt`** owns everything about what a file is called. Every name the app writes itself is normalized
  (`LibraryFiles.normalizedName`): `tukorfurogep-arviz.cho` for a song, each half of `artist - title` folded on its
  own so the dash between them survives, and `summer_set.setlist.json` for a setlist. `uniqueName` suffixes until the
  name is free, `_2` by default — the same rule written in the alphabet every name it numbers is already in — asking
  `exists` of the name and of `_2`, and listing the directory once only where `_2` is taken too, so that a family
  numbered far (a songbook whose songs all fall back to one name) costs one listing rather than a probe per number;
  a name the listing does not show is still asked of `exists`, which knows the file system's folding. The
  bracketed ` (2)` is left to `arrivingCollisionSuffix` and the one caller that writes a file under a name it did not
  invent: the copy sync brings down of a file changed on both sides, whose name is whatever the other device called
  it. `isNamed` reads `LibraryFiles.withoutCollisionSuffix` to answer whether a file already carries the name it would
  be given, that suffix included, ignoring case and Unicode form, which is
  what keeps "Update file name" from offering itself to a song that has made way for another one. Nothing in the
  library is ever overwritten implicitly. A rename goes through `moveFile`, which writes the new file before it
  removes the old one; a new name that differs from the old only in case or in Unicode form is not numbered on a file
  system that ignores them (macOS does both, Windows case), where it is taken by the very file being renamed, and is
  moved through a temporary name, since writing it there is writing the old file and the deletion after would remove
  the only copy. The two functions decide it with one predicate: a name one of them took for another file and the
  other for this one would be exactly that deletion.
- **`source/`** — the local sources. `CoverArtLocalSourceImpl` is the plainest of them: bytes in `covers/` under the
  key it is handed, every failure logged and answered with nothing. Its prune deletes only names shaped like a key
  (64 lowercase hex digits), since the directory also holds the temporary file of a copy being written. `SongLocalSourceImpl` reads the whole ChordPro family
  (`LibraryFiles.SONG_EXTENSIONS`) — hidden files left out, by `LibraryFiles.isSongFileName`, the rule every listing
  of the folder shares — but writes only `.cho` — `importFileName` included, so a `.crd` that is imported
  is stored as the `.cho` it is written back as — and gets a song's title, artist, key, the `{transpose}` it opens with (which
  travels with the key, since the key a list names is the one the song sounds in), tags (composed to NFC and merged, the way file names are) and "has chords" from a single
  `:chordpro` `summarize` call, so that neither the file nor the text is walked twice. The scan reads a batch of
  files at a time rather than all of them at once: that is what bounds the concurrency on a library of thousands.
  The next batch is read while the current one is parsed, so the storage and the parser never take turns, and the list is handed to the caller after the first batch and then whenever it has doubled, so that the song list
  fills up while the rest is still being read and the screen is rebuilt a handful of times rather than once per batch.
  A file that cannot be read is skipped, and so is one larger than `ImportLimits.MAX_TEXT_FILE_SIZE`, which only the
  user can have put there (the folder is the Files app's on iOS and a plain folder on the desktop); a *directory* that
  cannot be listed throws, because "empty library" and "your library is unreachable" must not look the same to the
  user.
- **`model/` + `mapper/`** — `SetlistDocument`, `UserPreferencesDocument` and the two-way mapping to the `:data:model`
  types. Every field of a document is defaulted, so a file written by an older version — or edited by hand, which on
  iOS and desktop the user can do — keeps whatever it does carry instead of failing to parse. A `null` is read as a
  missing field (`coerceInputValues`), in both documents. `seenWhatsNewVersions` defaults to an empty set for older preferences, and survives every settings save; a fresh installation records its first version without introducing it. `demoLibraryContentHashes` (what the demo files planted here held) defaults to an empty map for older preferences the same way. The preferences go further, since they are the one document
  the app overwrites as a whole: `UserPreferencesDocumentFormat` reads them field by field when they do not decode as
  they are — one transposition, tempo or capo that is not a number costs that entry, not the map, and one folded section key that
  is not text costs that key — and the local source copies such a
  file to `preferences.json.bad` before anything can be saved over it. The text size is clamped to `UserPreferences`'
  range on the way in, so a hand edit or a newer version's value never reaches the song screen as it is, and so are the metronome's volume, its tempo and the stored tempos and capos (one out of range is dropped); the metronome's ids are checked where they are read into its types, in `:presentation`. A setlist entry's `tempo` is read by a serializer of its own, like the date's: a whole number within the range is itself, anything else (`"fast"`, `96.5`, `0`) is null, since a plain `Int?` meeting one would fail the whole setlist; a null tempo is left out of the file, so a setlist nobody gave one stays byte for byte what it was. Its `capo` is read the same way, except that `0` is a value there — a capo the setlist takes off a song whose file asks for one — and null is the song's own. A setlist that does not decode is skipped and
  left alone, as before. A setlist naming a song twice is read as naming it once (the first mention wins), written
  back that way, and handed back that way from a save, since the screens key their rows by the song's file name and the
  caller caches the model the save returns rather than reading the file again. `SetlistDocumentFormat` reads and
  writes the setlist files, keeping the members the document does not know in `unknownFields` and writing them back
  after the known ones; `SetlistComparisonImpl` decodes two of them for sync, telling whether they differ only in their
  `date` (and the dropped `priority`) and what one was before a read dated it. A setlist's `date` is kept as text in the document and read as a `LocalDate` (the day of an ISO date-time too), so one that is
  not a date — or not text at all, which its own serializer reads as none — costs that date and not the setlist. Every
  setlist has a day, so one read from the library without one is given today's: `loadSetlists` does it in memory and
  says so (`ParsedSetlist.isDated`), writing nothing, and the repository saves it with that day right after the read,
  under its locks, through `loadSetlist` (`SetlistLocalSourceImpl.toDatedModel`, a write that announces nothing, so
  the next sync run carries it); one
  parsed for an import is given today's in memory, `ParsedSetlist.isDated` telling the import it was not the file's; the `priority` older versions ordered the list by is still declared,
  so that it is read and dropped rather than kept as an unknown field. No document type ever leaves this module.
- **`DocumentLocalSourceImpl` and `ArchiveLocalSourceImpl`** reach the PDF and Word readers and the zip reader and
  writer of `:data:formats` (see its `CLAUDE.md`). `DocumentLocalSourceImpl` checks the 16 MiB input limit and
  dispatches by extension; damaged, scanned, encrypted or otherwise unreadable documents return null, while cancellation
  is rethrown. `desktopTest/resources/document` holds independent-producer fixtures and adjacent ChordPro goldens,
  with their provenance and remaining producer coverage in its README, which `DocumentGoldenTest` reads end to end
  through this source.

- **`backup/`** — other apps' library backups, read by `ArchiveLocalSourceImpl` from the entries of any archive it
  unpacks (a `.zip`, a backup named by `LibraryFiles.LIBRARY_BACKUP_EXTENSIONS`, or one nested in either) and
  translated into the `.cho` songs and `*.setlist.json` setlists an archive of Campfire's own would hold, the rest of
  the archive (that app's bookkeeping) dropped unreported. `SongbookProBackup` reads SongbookPro's `dataFile.txt` — a
  version line and one JSON document of songs, sets and folders, read up to `ImportLimits.MAX_IMPORT_SIZE` rather
  than a song's limit — leniently, since the format is unpublished and writes numbers and booleans as strings as often
  as not: the key is an index from A, a deleted or textless song or set is left out, every value written into the
  header is flattened onto one line with its braces turned into parentheses, since SongbookPro's fields are free text,
  and the files are named in the batch by their titles, numbered within it, which is what the setlists point at. A
  backup recognised by its bookkeeping (`dataFile.hash`, `settings.hive`) or by the version line its document starts
  with, whose document cannot be read, is reported as one unread `dataFile.txt`, its bookkeeping dropped.

Tested with `commonTest` (the SongbookPro reader, the import budget) and `desktopTest` (the JVM storage, what unpacking an
archive keeps, the document goldens), run with
`./gradlew :data:source:local:implementation:desktopTest`.
