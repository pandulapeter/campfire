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

File-backed multiplatform implementation of `:data:source:local:api`, on all four platforms. Koin wiring in `Module.kt`
(`dataLocalSourceModule`).

- **`storage/file/FileStorage.kt`** is the only thing that differs per platform: flat file access inside the app's own
  data directory, addressed as `(StorageDirectory, file name)` — no paths, no sub-directories. `info` is what `list`
  would say about one file, so that saving a song (or writing one file of an import) does not list the directory
  — on OPFS a listing opens every file for its size and date, which made an import quadratic. `expect fun
  Scope.createFileStorage()` has four actuals: `JvmFileStorage` over `java.io.File` (shared between `androidMain` and
  `desktopMain`, one copy each because they are separate source sets), `IosFileStorage` over `NSFileManager` and
  `OpfsFileStorage` over the browser's Origin Private File System. Everything above this line is `commonMain`.
  - The directories are `library/songs`, `library/setlists` and `preferences` — songs and setlists sit next to each
    other so that the library exports as one archive, and the preferences sit outside it so that they do not.
  - Text is UTF-8 both ways, and a byte order mark is stripped while reading, because editors on Windows write one.
  - Writes are atomic on the three platforms that can be (a temporary file moved over the target on the JVM, `atomically`
    on iOS), so a crash in the middle of a save cannot truncate a song. OPFS has no such primitive. The OPFS storage
    resolves the three directory handles once and keeps them: walking down from the root is three promises, and
    nothing outside the page can remove a directory from the origin private file system.
  - Every `catch (Exception)` around a read rethrows `CancellationException` first: a scan that was cancelled is not
    a library of unreadable songs.
  - iOS splits the two: the library goes to the documents directory, where the Files app can reach it, and the
    preferences to application support, where it cannot.
- **`FileNames.kt`** owns everything about what a file is called. Every name the app writes itself is normalized
  (`LibraryFiles.normalizedName`): `tukorfurogep-arviz.cho` for a song, each half of `artist - title` folded on its
  own so the dash between them survives, and `summer_set.setlist.json` for a setlist. `uniqueName` suffixes until the
  name is free — ` (2)`, ` (3)`… for an imported file that keeps the name it arrived with, `_2` for a normalized one,
  which is the same rule written in the alphabet the name is already in. `isNamed` answers whether a file already
  carries the name it would be given, that suffix included, which is what keeps "Update file name" from offering
  itself to a song that has made way for another one. Nothing in the library is ever overwritten implicitly.
- **`source/`** — the four local sources. `SongLocalSourceImpl` reads the whole ChordPro family
  (`LibraryFiles.SONG_EXTENSIONS`) but writes only `.cho`, and gets a song's title, artist, key, `{transpose}` (which
  travels with the key, since the key a list names is the one the song sounds in), tags and "has chords" from a single
  `:chordpro` `summarize` call, so that neither the file nor the text is walked twice. The scan reads a batch of
  files at a time rather than all of them at once: that is what bounds the concurrency on a library of thousands,
  and each finished batch is handed to the caller, so the song list fills up while the rest is still being read. A file that cannot be read is skipped;
  a *directory* that cannot be listed throws, because "empty library" and "your library is unreachable" must not look
  the same to the user.
- **`model/` + `mapper/`** — `SetlistDocument`, `UserPreferencesDocument` and the two-way mapping to the
  `:data:model` types. Every field of a document is defaulted, so a file written by an older version — or edited by
  hand, which on iOS and desktop the user can do — keeps whatever it does carry instead of failing to parse. No
  document type ever leaves this module.
- **`zip/`** — a dependency-free zip implementation: `ZipReader` (STORED + DEFLATE, ZIP64 and encryption rejected),
  `ZipWriter` (STORED only — song text compresses badly enough not to be worth it), `Inflater` (raw DEFLATE, RFC 1951,
  following `puff.c`) and `Crc32`. It exists because no multiplatform zip library covers wasmJs.

Tested with `commonTest` (zip round trips, reader rejections) and `desktopTest` (the JVM storage, and the inflater
against archives the JVM produced), run with `./gradlew :data:source:local:implementation:desktopTest`.
