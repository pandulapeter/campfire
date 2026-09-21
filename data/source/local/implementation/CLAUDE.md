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
holds the `@Module @ComponentScan object DataLocalSourceModule`, and every local source is a `@Single`.

- **`storage/file/FileStorage.kt`** is the only thing that differs per platform: flat file access inside the app's own
  data directory, addressed as `(StorageDirectory, file name)` — no paths, no sub-directories. `info` is what `list`
  would say about one file, and `listNames` returns unfiltered names without opening them, so that saving a song (or writing one file of an import) does not list the directory
  — on OPFS a listing opens every file for its size and date, which made an import quadratic. The Koin definition
  is a `@Single` class in each platform source set, found by the module's component scan: `AndroidFileStorage` and
  `DesktopFileStorage`, both delegating to `JvmFileStorage` over `java.io.File` (shared between `androidMain` and
  `desktopMain`, one copy each because they are separate source sets), `IosFileStorage` over `NSFileManager` and
  `OpfsFileStorage` over the browser's Origin Private File System. The Android one takes the `Context` the app shell
  hands to Koin, marked `@Provided` since no shared module declares it. Everything above this line is `commonMain`.
  - The directories are `library/songs`, `library/setlists` and `preferences` — songs and setlists sit next to each
    other so that the library exports as one archive, and the preferences sit outside it so that they do not.
    Android's backup rules in `:app:android` name `library/` and `preferences/preferences.json` by path, so moving a
    directory or renaming the preferences file means changing those two XML files as well.
  - Text is written as UTF-8 and read through `:data:model`'s `decodeLibraryText`, the same rule the import uses: UTF-8
    when the bytes are valid UTF-8, Windows-1252 when they are not (what every other Western text file dropped into the
    library folder turns out to be), and a byte order mark stripped, because editors on Windows write one. A file read
    through the fallback is written back as UTF-8 on its first save, which keeps its accents rather than replacing them.
  - Writes are atomic on the three platforms that can be (a `.campfire-<number>.tmp` temporary file of its own per write,
    named without the target so a name at the file-system limit still saves, flushed to the device
    and moved over the target on the JVM, `atomically` on iOS), so a crash in the middle of a save cannot truncate a
    song. OPFS has no such primitive. The OPFS storage
    resolves the three directory handles once and keeps them: walking down from the root is three promises, and
    nothing outside the page can remove a directory from the origin private file system.
  - Every `catch (Exception)` around a read rethrows `CancellationException` first: a scan that was cancelled is not
    a library of unreadable songs.
  - A read answers null for a file that is **not there** and for nothing else. A file that is there and cannot be
    read or written throws `LibraryStorageException` (from `:data:source:local:api`) on every platform: iOS asks
    `dataWithContentsOfFile` for its `NSError` rather than taking its nil as absence, the JVM wraps the `IOException`,
    and OPFS folds only a `NotFoundError` into null. Sync is why: a file reported as missing is planned as a deletion,
    and that deletion reaches every other device. The song scan skips such a song with a log line; a sync run stops
    and reports a storage failure.
  - iOS splits the two: the library goes to the documents directory, where the Files app can reach it, and the
    preferences to application support, where it cannot.
  - The JVM storage removes its own temporary files older than an hour on first touching each directory. On Windows it
    stores device names such as `con.cho` with a leading underscore and reports the ordinary library name back.
- **`storage/secret/SecretStore.kt`** is where the sync credentials go, and nothing else: a refresh token is a
  long-lived credential. `AndroidSecretStore` encrypts it with an AES-GCM key generated inside the Android Keystore
  (never `security-crypto`, which is deprecated) and writes the IV and ciphertext as `preferences/sync-credentials.bin`;
  a key that became unusable, or a file it cannot decrypt, is deleted and reads as no credentials, so the user
  connects again rather than being stuck. `IosSecretStore` is a Keychain generic password, readable after the first
  unlock because a background sync may need it on a locked device. Desktop and the web get `FileSecretStore`, the
  plain `preferences/sync-credentials.json` it always was: no desktop keychain is worth a native dependency per
  operating system, and the browser has none. `SyncStateLocalSourceImpl` moves a plain file left by an older version
  into the store on the first read and deletes it, which on desktop and the web is a no-op, since the store found
  that file itself. The index stays a plain file everywhere: it holds hashes and revisions, nothing secret.
- **`FileNames.kt`** owns everything about what a file is called. Every name the app writes itself is normalized
  (`LibraryFiles.normalizedName`): `tukorfurogep-arviz.cho` for a song, each half of `artist - title` folded on its
  own so the dash between them survives, and `summer_set.setlist.json` for a setlist. `uniqueName` suffixes until the
  name is free, `_2` by default — the same rule written in the alphabet every name it numbers is already in. The
  bracketed ` (2)` is left to `arrivingCollisionSuffix` and the one caller that writes a file under a name it did not
  invent: the copy sync brings down of a file changed on both sides, whose name is whatever the other device called
  it. `isNamed` reads `LibraryFiles.withoutCollisionSuffix` to answer whether a file already carries the name it would
  be given, that suffix included, which is
  what keeps "Update file name" from offering itself to a song that has made way for another one. Nothing in the
  library is ever overwritten implicitly. A rename goes through `moveFile`, which writes the new file before it
  removes the old one; a new name that differs from the old only in case is not numbered on a case-insensitive file
  system, where it is taken by the very file being renamed, and is moved through a temporary name, since writing it
  there is writing the old file and the deletion after would remove the only copy.
- **`source/`** — the four local sources. `SongLocalSourceImpl` reads the whole ChordPro family
  (`LibraryFiles.SONG_EXTENSIONS`) but writes only `.cho` — `importFileName` included, so a `.crd` that is imported
  is stored as the `.cho` it is written back as — and gets a song's title, artist, key, `{transpose}` (which
  travels with the key, since the key a list names is the one the song sounds in), tags and "has chords" from a single
  `:chordpro` `summarize` call, so that neither the file nor the text is walked twice. The scan reads a batch of
  files at a time rather than all of them at once: that is what bounds the concurrency on a library of thousands,
  and each finished batch is handed to the caller, so the song list fills up while the rest is still being read. A file that cannot be read is skipped;
  a *directory* that cannot be listed throws, because "empty library" and "your library is unreachable" must not look
  the same to the user.
- **`model/` + `mapper/`** — `SetlistDocument`, `UserPreferencesDocument` and the two-way mapping to the `:data:model`
  types. Every field of a document is defaulted, so a file written by an older version — or edited by hand, which on
  iOS and desktop the user can do — keeps whatever it does carry instead of failing to parse. A `null` is read as a
  missing field (`coerceInputValues`), in both documents. The preferences go further, since they are the one document
  the app overwrites as a whole: `UserPreferencesDocumentFormat` reads them field by field when they do not decode as
  they are — one transposition that is not a number costs that entry, not the map — and the local source copies such a
  file to `preferences.json.bad` before anything can be saved over it. A setlist that does not decode is skipped and
  left alone, as before. A setlist file naming a song twice is read as naming it once (the first mention wins) and
  written back that way, since the screens key their rows by the song's file name. No document type ever leaves this
  module.
- **`zip/`** — a dependency-free zip implementation: `ZipReader` (STORED + DEFLATE; a ZIP64 archive rejected, a ZIP64,
  encrypted or otherwise compressed entry left out),
  `ZipWriter` (STORED only — song text compresses badly enough not to be worth it), with every entry dated by its
  supplied DOS timestamp and more than 65,534 entries refused rather than written as ZIP64, `Inflater` (raw DEFLATE, RFC 1951,
  following `puff.c`) and `Crc32`. It exists because no multiplatform zip library covers wasmJs. Sizes an archive
  declares are trusted only as far as a first guess: an entry is asked about by name before it is inflated — hidden
  files and anything an import would not look inside are never read, a song over `ImportLimits.MAX_TEXT_FILE_SIZE` is
  not either, and the caller says how much the whole import may still unpack to (`ImportLimits.MAX_IMPORT_SIZE`,
  24 MiB, nested archives included). An entry that cannot be read is left out and reported by name; only an archive
  that cannot be walked at all is a `ZipException`. The buffer still starts at no more than 1 MiB whatever the central
  directory claims.

Tested with `commonTest` (zip round trips, reader rejections) and `desktopTest` (the JVM storage, what unpacking an
archive keeps, and the inflater against archives the JVM produced), run with
`./gradlew :data:source:local:implementation:desktopTest`.
