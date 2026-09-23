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
    directory or renaming the preferences file means changing those two XML files as well. iOS backs up both of its
    directories whole, so what must stay behind says so itself: `SyncStateLocalSourceImpl` calls
    `keepOutOfDeviceBackup` after every write of `sync-index.json`, which `IosFileStorage` answers by setting
    `NSURLIsExcludedFromBackupKey` again — it is an attribute of the file, and the atomic write replaces the file.
    Everywhere else it does nothing.
  - Text is written as UTF-8 and read through `:data:model`'s `decodeLibraryText`, the same rule the import uses: UTF-8
    when the bytes are valid UTF-8, and otherwise Windows-1250 for a file whose bytes can only be Hungarian or Polish and
    Windows-1252 for everything else (a UTF-8 file with a few stray bytes keeps its UTF-8 and reads only those through
    the code page, see `:data:model`), and a byte order mark stripped, because editors on Windows write one. A file read
    through the fallback is written back as UTF-8 on its first save, which keeps its accents rather than replacing them.
  - Writes are atomic on the three platforms that can be (a `.campfire-<number>.tmp` temporary file of its own per write,
    named without the target so a name at the file-system limit still saves, flushed to the device
    and moved over the target on the JVM, `atomically` on iOS), so a crash in the middle of a save cannot truncate a
    song. OPFS has no such primitive. Where the browser has no `createWritable()` (Safari before 26, every browser on
    iOS 18), a write is handed to `app/web`'s `opfs-writer.js`, a dedicated worker, since `createSyncAccessHandle()`
    exists nowhere else: it is given the directory by its path segments and the content as bytes, text encoded as
    UTF-8 on the way. The OPFS storage
    resolves the three directory handles once and keeps them: walking down from the root is three promises, and
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
    preferences to application support, where it cannot.
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
  and the key and file are kept: that launch starts disconnected and the next one reads the same file again. `IosSecretStore` is a Keychain generic password, readable after the first
  unlock because a background sync may need it on a locked device, and bound to the device
  (`AfterFirstUnlockThisDeviceOnly`) so that it stays out of the backup, as Android's does; an item an older version
  wrote without that is moved over when it is read. The item survives an uninstall, which is why a first launch
  forgets it (`SyncRepository.forgetStoredConnection`). Desktop and the web get `FileSecretStore`, the
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
  be given, that suffix included, ignoring case and Unicode form, which is
  what keeps "Update file name" from offering itself to a song that has made way for another one. Nothing in the
  library is ever overwritten implicitly. A rename goes through `moveFile`, which writes the new file before it
  removes the old one; a new name that differs from the old only in case or in Unicode form is not numbered on a file
  system that ignores them (macOS does both, Windows case), where it is taken by the very file being renamed, and is
  moved through a temporary name, since writing it there is writing the old file and the deletion after would remove
  the only copy. The two functions decide it with one predicate: a name one of them took for another file and the
  other for this one would be exactly that deletion.
- **`source/`** — the four local sources. `SongLocalSourceImpl` reads the whole ChordPro family
  (`LibraryFiles.SONG_EXTENSIONS`) — hidden files left out, by `LibraryFiles.isSongFileName`, the rule every listing
  of the folder shares — but writes only `.cho` — `importFileName` included, so a `.crd` that is imported
  is stored as the `.cho` it is written back as — and gets a song's title, artist, key, `{transpose}` (which
  travels with the key, since the key a list names is the one the song sounds in), tags and "has chords" from a single
  `:chordpro` `summarize` call, so that neither the file nor the text is walked twice. The scan reads a batch of
  files at a time rather than all of them at once: that is what bounds the concurrency on a library of thousands,
  and the list is handed to the caller after the first batch and then whenever it has doubled, so that the song list
  fills up while the rest is still being read and the screen is rebuilt a handful of times rather than once per batch.
  A file that cannot be read is skipped, and so is one larger than `ImportLimits.MAX_TEXT_FILE_SIZE`, which only the
  user can have put there (the folder is the Files app's on iOS and a plain folder on the desktop); a *directory* that
  cannot be listed throws, because "empty library" and "your library is unreachable" must not look the same to the
  user.
- **`model/` + `mapper/`** — `SetlistDocument`, `UserPreferencesDocument` and the two-way mapping to the `:data:model`
  types. Every field of a document is defaulted, so a file written by an older version — or edited by hand, which on
  iOS and desktop the user can do — keeps whatever it does carry instead of failing to parse. A `null` is read as a
  missing field (`coerceInputValues`), in both documents. The preferences go further, since they are the one document
  the app overwrites as a whole: `UserPreferencesDocumentFormat` reads them field by field when they do not decode as
  they are — one transposition that is not a number costs that entry, not the map — and the local source copies such a
  file to `preferences.json.bad` before anything can be saved over it. A setlist that does not decode is skipped and
  left alone, as before. A setlist naming a song twice is read as naming it once (the first mention wins), written
  back that way, and handed back that way from a save, since the screens key their rows by the song's file name and the
  caller caches the model the save returns rather than reading the file again. `SetlistDocumentFormat` reads and
  writes the setlist files, keeping the members the document does not know in `unknownFields` and writing them back
  after the known ones. No document type ever leaves this module.
- **`zip/`** — a dependency-free zip implementation: `ZipReader` (STORED + DEFLATE; a ZIP64 archive rejected, a ZIP64,
  encrypted or otherwise compressed entry left out),
  `ZipWriter` (STORED only — song text compresses badly enough not to be worth it), with every entry dated by its
  supplied DOS timestamp and more than 65,534 entries refused rather than written as ZIP64, `Inflater` (raw DEFLATE, RFC 1951,
  following `puff.c`) and `Crc32`. It exists because no multiplatform zip library covers wasmJs. Sizes an archive
  declares are trusted only as far as a first guess: an entry is asked about by name before it is inflated — hidden
  files and anything an import would not look inside are never read, a song over `ImportLimits.MAX_TEXT_FILE_SIZE` is
  not either, and the caller says how much the whole import may still unpack to (`ImportLimits.MAX_IMPORT_SIZE`,
  24 MiB, nested archives included) — charged before an entry is read, so a damaged entry costs what it declared, and
  an inflating entry is stopped at the size it declared rather than at the 24 MiB any entry may reach; the entries'
  compressed sizes may not add up to more than the archive holds, which is what a central directory pointing many
  entries at the same bytes (a zip bomb) runs into. An entry that cannot be read is left out and reported by name; only an archive
  that cannot be walked at all is a `ZipException`. The buffer still starts at no more than 1 MiB whatever the central
  directory claims. Entry names are UTF-8 where they say so or are valid UTF-8 (macOS does not say so), and code page
  437 otherwise, as the format specifies. A backslash separates paths as well as a slash, since Windows PowerShell 5.1
  writes one.

Tested with `commonTest` (zip round trips, reader rejections) and `desktopTest` (the JVM storage, what unpacking an
archive keeps, and the inflater against archives the JVM produced), run with
`./gradlew :data:source:local:implementation:desktopTest`.
