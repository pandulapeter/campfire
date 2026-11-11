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

Skipped or unreadable files are logged to the injected `Logger`.

## `FileStorage` (the only per-platform part)

- **Performance**: saving must not list the directory (`listNames`, `info`); on OPFS a listing opens every file, which
  made an import quadratic. `readTexts` is one browser call per batch on OPFS. The scan uses `listForScan` / `readScan`
  (size and date read with the text, oversized files reported and never read).
- **Directories**: Android's backup rules in `:app:android` name `library/` and `preferences/preferences.json` by path;
  moving a directory or renaming the file means changing those XML files. iOS backs up whole directories, so
  `SyncIndexLocalSourceImpl`, `SharedLinksLocalSourceImpl`, `EditorDraftLocalSourceImpl`, and `CoverArtLocalSourceImpl`
  call `keepOutOfDeviceBackup` after **every** write: the exclusion is a file attribute and the atomic write replaces
  the file.
- **Text**: written as UTF-8, read through `decodeLibraryText`; a fallback-read file is written back as UTF-8. The web
  decodes valid NUL-free UTF-8 with `TextDecoder`; bytes cross the Wasm boundary as a one-char-per-byte string (typed
  array copies are per byte).
- **Atomic writes** on JVM (own `.campfire-<n>.tmp` per write, named without the target so a name at the length limit
  still saves; flushed, then moved) and iOS (`atomically`). OPFS:
  - Without `createWritable()` (Safari < 26, all iOS 18 browsers) writes go to `app/web`'s journaled `opfs-writer.js`
    worker; a failed worker fails its waiting writes and the next write starts another.
  - With it, a new file is created behind an empty `<name>.campfire-new` marker, removed on close, by which recovery
    deletes an empty leftover (omitted for names within 12 bytes of Safari's 255-byte limit).
  - The journal is replayed per directory before its handle is first handed out and after a failed write.
    `.campfire-tmp`, `.campfire-commit`, and `.campfire-new` files are hidden from every listing.
- **Missing vs. failed**: a read returns null **only** for a file that is not there. A file that cannot be read,
  written, or deleted, or a directory that cannot be listed, throws `LibraryStorageException` everywhere, because sync
  plans a missing file as a deletion that reaches every device. iOS asks `dataWithContentsOfFile` for its `NSError` and
  re-checks `fileExistsAtPath` on nil (a directory under the name is missing, as in a listing); the JVM catches `java.nio.file.NoSuchFileException` (not Kotlin's) and wraps
  other `IOException`s; OPFS folds only `NotFoundError` into null, and drops an entry that vanishes between listing and
  stat (sync deletes while the live rescan lists).
- Every `catch (Exception)` around a read rethrows `CancellationException` first.
- **Contract**: `FileStorageContractTest` (`commonTest`), run on the desktop over `JvmFileStorage` and on the iOS
  simulator over `FoundationFileStorage` (`iosSimulatorArm64Test`, CI's `compile-ios` job); what only the JVM does stays
  in `JvmFileStorageTest`. Kotlin/Native refuses a comma in a test name, which `check_test_names.py` checks.
- **Names**: `canHoldFileName` refuses a path or nothing (`/`, `\`, `.`, `..`) everywhere, and on Windows `? : * " < >
  |`, a trailing space or dot, or a control character. Refused, never mapped: a mapping that failed to reverse would
  send a second copy of the song to every device. Windows device names (`con.cho`) are stored with a leading underscore
  and reported back unchanged.
- **JVM**: removes its own temp files older than an hour on first touching a directory. Reads via `Files.readAllBytes`,
  not `FileInputStream`, which Windows opens without delete sharing (a scan would block sync's renames). Move and delete
  retry three times on a Windows `AccessDeniedException` (anti-virus), and the app's own moves and deletions of one
  name take turns, since Windows refuses a replace while another of the same name is under way.

## `SecretStore` (sync credentials only)

- `AndroidSecretStore`: AES-GCM key in the Android Keystore (never `security-crypto`, deprecated). An invalidated key or
  a failing tag is deleted and reads as no credentials. Any other Keystore failure throws `LibraryStorageException` and
  keeps key and file (the launch reports `ConnectionFailed` with a storage reason).
- `IosSecretStore`: Keychain, `AfterFirstUnlockThisDeviceOnly` (background sync on a locked device; out of backup);
  older items are migrated on read. Any status but not-found throws. The item survives uninstall, hence the first-launch
  forgetting.
- Desktop and web: a plain file (`FileSecretStore`); `SyncCredentialsLocalSourceImpl` migrates an older version's plain
  file into the store. The sync index stays a plain file everywhere.

## `FileNames.kt`

- `uniqueName`: a name is taken where a listed file has it under any case or Unicode form (`identityKey`), on every
  platform: Android, Linux, and OPFS tell them apart, sync and the other devices do not. One listing per call, none
  where the caller passes the keys it holds (the import); a name it lacks is still asked of `exists`, which knows the
  file system's own folding.
- ` (2)` (`arrivingCollisionSuffix`) is only for sync's conflict copy, whose name another device chose.
- `isNamed` keeps "Update file name" from offering itself to a song that was numbered aside.
- `moveFile` writes the new file before removing the old. A new name differing only by case or Unicode form is not
  numbered where the file system ignores that (macOS both, Windows case) and moves through a temporary name, or the
  deletion would remove the only copy. Both functions must share one predicate for this.

## Sources

- `CoverArtLocalSourceImpl`: prune deletes only key-shaped names (64 lowercase hex): the directory also holds an
  in-progress temp file.
- `SongLocalSourceImpl` reads the ChordPro family but writes only `.cho`, `importFileName` included. Metadata comes from
  one `:libs:chordpro` `summarize` call (a file with `{key}` and `{capo}` is also parsed whole for
  `SongKeys.capoChartKeyOf`); the leading `{transpose}` travels with the key; tags are NFC-composed and merged. The scan
  publishes in batches. Unreadable or oversized files are skipped; an unlistable *directory* throws, so an unreachable
  library never looks empty.
- Documents: 16 MiB limit. `ImportCorpusTest` (desktop) scores the import against the private corpus that
  `campfire.importCorpus` names, writing its report into the corpus's `.runs/`; blank skips it.

## Documents (`model/`, `mapper/`)

No document type leaves this module.

- Every field is defaulted and `null` reads as missing (`coerceInputValues`), so old or hand-edited files keep what they
  carry; fields are only added (root `CLAUDE.md`, Stored formats).
- `UserPreferencesDocumentFormat` falls back to field-by-field reading: a bad entry costs that entry, not the map. Such
  a file is copied to `preferences.json.bad` before anything can save over it. Text size, metronome volume and tempo,
  tuner reference pitch, the tools' capo, and stored tempos and capos are clamped or dropped to range on read.
- A document with no `instrument` key is migrated once from the older tuner and chord keys (read, never written); one
  with an `instrument` key (even unknown, read as guitar with its tunings kept) never reads them.
- Setlists: invalid `tempo` / `capo` → null rather than failing the setlist; `capo` 0 is a value (removing the song's
  capo). Null tempo is omitted so untouched setlists stay byte-identical. A bad `date` costs only the date. Unknown
  members are written back after known ones; the old `priority` is declared so it is read and dropped. A setlist naming
  a song twice keeps the first mention, also in what a save returns (rows key by file name). One that does not decode is
  skipped and left alone, as is one whose `version` is a number above 1 (nothing writes the key yet). `decode` refuses a
  document past 64 levels or a 100,000-token budget before parsing.

## Other apps' libraries

`.sbpbackup`, `.sbp`, and `.osb` are in `LibraryFiles.LIBRARY_BACKUP_EXTENSIONS` so an archive holding one is looked
inside; none is registered as an Open with type.

`backup/SongbookProBackup` translates `dataFile.txt` (a version line plus one JSON document, read up to
`ImportLimits.MAX_IMPORT_SIZE`) into songs and setlists, then the ordinary import:

- Lenient: the format is unpublished and writes numbers and booleans as strings.
- Header values (folders as tags) are written only where the text does not declare them, flattened to one line with
  braces turned into parentheses.
- Songs are named by title, numbered within the batch, which is what the setlists point at. A set's per-song capo is
  kept where it differs; transpositions are not carried over.
- A backup whose document cannot be read is reported as one unread `dataFile.txt`; the rest of the archive is dropped
  unreported.

`backup/JsonSongs`, `OpenSongSong`, and `OnSongFile` turn another app's document into `.cho` songs, in an archive after
the SongbookPro check and loose through `songsIn`; input shapes are in their KDoc. JSON is bounded to the text limit, 64
levels and a token budget before parsing (`ShallowJson` through `JsonBounds`: deeper overflows the parser's stack, more
builds a tree past the heap), and 32 levels and 200,000 nodes when walked. None writes setlists; all write headers and
batch names through `ForeignSongHeader`. Beyond the KDoc:

- OpenSong: named as it came; section tags become environments or comments; `.` chord rows are set over their lyric rows
  as marked (`ChordSheetConverter.setChordRows`, nothing re-guessed); a block with no chord row is
  `ChordSheetLyrics.escaped`.
- OnSong (text-like bytes only): named by its base name; a first chord row means no header unless it is the file's own
  name; a third bare line ends the header; the body is cut at its directive lines, each run converted, kept as inline
  ChordPro, or escaped.
- ABC (`backup/AbcFile`): an `.abc` (text-like bytes only) or a sniffed text `AbcText.isAbc` accepts, which
  `ChordSheetStructure` would otherwise skip, is one `.cho` per tune, named by its title within the batch, titled by the
  file's name where it has no `T:`. A pasted or shared text (a `.txt`) never comes here: the import's plain-text branch
  asks the same sniff.

## A song is named by its header

**A file name is reproducible from its header alone** (`{artist}`, `{title}`, `{subtitle}` joining the title half) in
the editor, `importFileName`, and `ExportFileNames.kt`; `Song.canUpdateFileName` notices where that stopped being true.
