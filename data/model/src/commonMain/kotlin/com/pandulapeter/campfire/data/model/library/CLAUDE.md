<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/model/library

- **A song's or setlist's identity is its file name**, extension included, never a generated id.
- The extension lists (`SONG_EXTENSIONS`, `IMPORTABLE_EXTENSIONS`, `LIBRARY_BACKUP_EXTENSIONS`) **must agree with the
  import rules, the storage layer, and the three OS registrations** (Android manifest, iOS plist, desktop Gradle file).
  Importable-only types are never claimed system wide; legacy `.doc` is listed only to report it unreadable. `.osb` and
  `FOREIGN_SONG_EXTENSIONS` (`.onsong`, read with the text limit) have no iOS system type, so on iOS they come only
  through the in-app picker, never the share extension.
- `isSongFileName` / `isSetlistFileName` (and `LibraryFileKind.matches`) are the one answer to which files are the
  library, for the scan and both sync listings: right extension and not hidden (`._song.cho` is macOS's AppleDouble).
- `normalizedName` **must stay idempotent**: an exported name is normalized again on its way back in. NFC first (it
  composes non-Latin marks). Letters outside the BMP are kept as surrogate pairs, never cut; symbols, emoji, and planes
  14–16 become separators, as does a Latin letter `Accents.kt`'s `withoutAccent` table lacks, so extend the table.
- `songBaseName` is the one song naming rule (artist, dash, title; the title alone without an artist).
- `identityKey` / `isSameLibraryName` are the one rule for whether two names are one file (NFC, then
  `uppercaseChar().lowercaseChar()` per code point, matching the JVM's `equals(ignoreCase = true)`,
  `LibraryNameIdentityJvmTest`).
- `withoutCollisionSuffix` reads both the app's `_2` names and sync's ` (2)` conflict copies.

## Text

`decodeLibraryText` is how every library and incoming file becomes text, so a file reads the same however it arrived:
UTF-8; mostly-UTF-8 files have stray bytes read through a code page; otherwise Windows-1250 only where the bytes can
only be Hungarian or Polish and no Western-only letter appears, else Windows-1252. Czech, Slovak, and Romanian stay on
1252 on purpose (their bytes overlap Western ones; guessing would misread correct files). A leading BOM is dropped (a
mid-file one is `ChordProSplitter`'s).
