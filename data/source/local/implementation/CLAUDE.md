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

File-backed implementation of `:data:source:local:api` on all four platforms. Skipped or unreadable files are logged to
the injected `Logger`.

## `FileStorage` (the only per-platform part)

Flat files addressed as `(StorageDirectory, name)`, no paths. `JvmFileStorage` (`jvmSharedMain`, behind
`AndroidFileStorage` and `DesktopFileStorage`), `IosFileStorage`, and `OpfsFileStorage`.

- **Performance**: saving must not list the directory (`listNames`, `info`); on OPFS a listing opens every file, which
  made an import quadratic. `readTexts` is one answer per file and one browser call per batch on OPFS. The song scan uses
  `listForScan` / `readScan` (size and date read with the text, oversized files reported and never read).
- **Directories**: `library/songs`, `library/setlists`, `preferences`, `covers`. Android's backup rules in `:app:android`
  name `library/` and `preferences/preferences.json` by path: moving a directory or renaming the file means changing
  those XML files. iOS backs up whole directories, so `SyncIndexLocalSourceImpl` (index, forget-pending note),
  `EditorDraftLocalSourceImpl`, and `CoverArtLocalSourceImpl` call `keepOutOfDeviceBackup` after **every** write: the
  exclusion is a file attribute and the atomic write replaces the file.
- **Text**: written as UTF-8, read through `:data:model`'s `decodeLibraryText` (UTF-8, else Windows-1250/1252, BOM
  stripped); a fallback-read file is written back as UTF-8. The web decodes valid NUL-free UTF-8 with `TextDecoder`, else
  `decodeLibraryText`; bytes cross the Wasm boundary as a one-char-per-byte string (typed array copies are per byte).
- **Atomic writes** on JVM (own `.campfire-<n>.tmp` per write, named without the target so a name at the length limit
  still saves; flushed, then moved) and iOS (`atomically`). OPFS:
  - Without `createWritable()` (Safari < 26, all iOS 18 browsers) writes go to `app/web`'s journaled `opfs-writer.js`
    worker (`createSyncAccessHandle()`, see `app/web`); a failed worker fails its waiting writes and the next write
    starts another.
  - With `createWritable()`, a new file is created behind an empty `<name>.campfire-new` marker, removed on close, used
    by recovery to delete an empty leftover (omitted for names within 12 bytes of Safari's 255-byte limit).
  - The journal is replayed per directory before its handle is first handed out and after a failed write.
    `.campfire-tmp`, `.campfire-commit`, and `.campfire-new` files are hidden from every listing. Directory handles are
    resolved once and cached.
- **Missing vs. failed**: a read returns null **only** for a file that is not there. A file that cannot be read,
  written, or deleted, or a directory that cannot be listed, throws `LibraryStorageException` on every platform,
  because sync plans a missing file as a deletion that reaches every device. iOS asks `dataWithContentsOfFile` for its
  `NSError` and re-checks `fileExistsAtPath` on nil; the JVM catches `java.nio.file.NoSuchFileException` (not Kotlin's)
  and wraps other `IOException`s; OPFS folds only `NotFoundError` into null, and drops an entry that vanishes between
  listing and stat (sync deletes while the live rescan lists).
- Every `catch (Exception)` around a read rethrows `CancellationException` first.
- **Names**: `canHoldFileName` refuses a path or nothing (`/`, `\`, `.`, `..`) everywhere, and on Windows
  `? : * " < > |`, a trailing space or dot, or a control character. These are refused, never mapped: a mapping that
  failed to reverse would send a second copy of the song to every device. Windows device names (`con.cho`) are stored
  with a leading underscore and reported back unchanged.
- **JVM**: removes its own temp files older than an hour on first touching a directory. Reads via `Files.readAllBytes`,
  not `FileInputStream`, which Windows opens without delete sharing (a 64-file scan would block sync's renames). Move and
  delete retry three times on a Windows `AccessDeniedException` (anti-virus); elsewhere it is reported at once.

## `SecretStore` (sync credentials only)

- `AndroidSecretStore`: AES-GCM key in the Android Keystore (never `security-crypto`, deprecated), file
  `preferences/sync-credentials.bin`. An invalidated key or a tag that fails is deleted and reads as no credentials.
  Any other Keystore failure throws `LibraryStorageException` and keeps key and file (the launch reports
  `ConnectionFailed` with a storage reason; nothing overwrites the file).
- `IosSecretStore`: Keychain generic password, `AfterFirstUnlockThisDeviceOnly` (background sync on a locked device; out
  of backup); older items are migrated on read. Any status but not-found throws like Android. The item survives
  uninstall, hence the first-launch forgetting.
- Desktop and web: plain `preferences/sync-credentials.json` via `FileSecretStore`. `SyncCredentialsLocalSourceImpl`
  migrates a plain file left by an older version into the store. The sync index stays a plain file everywhere.

## `FileNames.kt`

- `uniqueName` suffixes `_2`, `_3`…. A name is taken where a listed file has it under any case or Unicode form
  (`identityKey`), on every platform: Android, Linux, and OPFS tell them apart, sync and the other devices do not. One
  listing per call, none where the caller passes the keys it holds (the import); a name it lacks is still asked of
  `exists`, which knows the file system's own folding.
- ` (2)` (`arrivingCollisionSuffix`) is only for sync's conflict copy, whose name another device chose.
- `isNamed` (via `LibraryFiles.withoutCollisionSuffix`, ignoring case and Unicode form) keeps "Update file name" from
  offering itself to a song that was numbered aside.
- `moveFile` writes the new file before removing the old. A new name differing only by case or Unicode form is not
  numbered where the file system ignores that (macOS both, Windows case) and moves through a temporary name, or the
  deletion would remove the only copy. Both functions must share one predicate for this.

## Sources

- `CoverArtLocalSourceImpl`: bytes in `covers/`, failures logged and answered with nothing. Prune deletes only
  key-shaped names (64 lowercase hex), since the directory also holds an in-progress temp file.
- `SongLocalSourceImpl`: reads the ChordPro family (`LibraryFiles.SONG_EXTENSIONS`, hidden files excluded via
  `isSongFileName`) but writes only `.cho`, `importFileName` included. Metadata comes from one `:chordpro` `summarize`
  call; the leading `{transpose}` travels with the key; tags are NFC-composed and merged. The scan reads in batches
  (bounding concurrency), reads the next batch while parsing, and publishes after the first batch and then each time
  the list doubles. Unreadable or oversized (`ImportLimits.MAX_TEXT_FILE_SIZE`) files are skipped; an unlistable
  *directory* throws, so an unreachable library never looks empty.
- `DocumentLocalSourceImpl` / `ArchiveLocalSourceImpl` wrap `:data:formats`. Documents: 16 MiB limit, null for
  unreadable input, cancellation rethrown. `desktopTest/resources/document` holds third-party fixtures with goldens
  (provenance in its README).

## Documents (`model/`, `mapper/`)

No document type leaves this module.

- Every field is defaulted and `null` reads as missing (`coerceInputValues`), so old or hand-edited files keep what
  they carry.
- `UserPreferencesDocumentFormat` falls back to field-by-field reading: a bad entry costs that entry, not the map. Such
  a file is copied to `preferences.json.bad` before anything can save over it. Text size, metronome volume and tempo,
  tuner reference pitch, and stored tempos and capos are clamped or dropped to range on read.
- A document with no `instrument` key is migrated once from the older tuner and chord keys, which are read, never
  written; a document with an `instrument` key (even an unknown one, read as guitar with its tunings kept) never reads
  them.
- Setlists: `tempo` and `capo` have their own serializers (invalid → null rather than failing the setlist; `capo` 0 is a
  value, removing the song's capo). Null tempo is omitted so untouched setlists stay byte-identical. A bad `date`
  costs only the date. Unknown members are kept in
  `unknownFields` and written back after known ones; the old `priority` is declared so it is read and dropped. A
  setlist naming a song twice keeps the first mention, also in what a save returns (screens key rows by file name).
  A setlist that does not decode is skipped and left alone.
- An undated setlist gets today in memory (`ParsedSetlist.isDated`, no write); the repository then saves it via
  `loadSetlist` / `SetlistLocalSourceImpl.toDatedModel`, a write that announces nothing, so the next sync carries it.
  `SetlistComparisonImpl` serves sync (see `:data:source:local:api`).

## Other apps' libraries

SongbookPro's `.sbpbackup` / `.sbp` are named in `LibraryFiles.LIBRARY_BACKUP_EXTENSIONS` so an archive holding one
is looked inside; none is registered as an Open with type, and no platform's picker filters by name. Other names are
recognised by their bytes (`ImportBudget` in `:data:model`, `:domain:implementation`'s `importing`), and so is an
entry inside an archive under any name but a document's: one under an unknown name, or a text name over
`MAX_TEXT_FILE_SIZE`, is sniffed (`EntryPolicy.Sniff`) and read only when its first bytes are a zip's.

`backup/SongbookProBackup` translates `dataFile.txt` (a version line plus one JSON document, read up to
`ImportLimits.MAX_IMPORT_SIZE`) into `.cho` songs and `*.setlist.json` setlists, then the ordinary import:

- Lenient: the format is unpublished and writes numbers and booleans as strings.
- Header values (folders as tags) are written only where the text does not declare them, flattened to one line with
  braces turned into parentheses.
- Songs are named by title, numbered within the batch, which is what the setlists point at. A set's per-song capo is
  kept where it differs. Transpositions are not carried over.
- A recognised backup whose document cannot be read is reported as one unread `dataFile.txt`; the rest of the archive
  is dropped unreported.

`backup/JsonSongs` reads any other JSON document (a `.json` that is no setlist, or a sniffed text starting with `{` or
`[`), inside an archive after the SongbookPro check and loose through `songsIn`: an object is a song only with a
one-line title and a content with `ChordSheetStructure`, or four lines under `lyrics` (a notes export's `{title,
content}` is not). Bounded to the text limit, 32 levels, and 200,000 nodes; no setlists. Both readers write their
headers and batch names through `ForeignSongHeader`.

## A song is named by its header

**A file name is reproducible from its header alone** (`{artist}`, `{title}`, `{subtitle}` joining the title half) in
the editor, `importFileName`, and `ExportFileNames.kt`; `Song.canUpdateFileName` notices where that stopped being true.
Entries in an exported archive keep their library names, since setlists point at songs by file name.
