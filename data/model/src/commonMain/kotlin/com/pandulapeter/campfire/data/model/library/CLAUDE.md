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

### Identity and names

- **A song's or setlist's identity is its file name**, extension included, not a generated id.
- The extension lists (`SONG_EXTENSIONS`, `IMPORTABLE_EXTENSIONS`, `LIBRARY_BACKUP_EXTENSIONS`) **must agree with the
  import rules, the storage layer, and the three OS registrations** (Android manifest, iOS plist, desktop Gradle file).
  Importable-only types are never claimed system wide; legacy `.doc` is listed only to report it unreadable. `.osb`
  and `FOREIGN_SONG_EXTENSIONS` (`.onsong`, read with the text limit) are the exceptions to the iOS share extension:
  they have no system type there to name, so on iOS they come in through the in-app picker.
- `isSongFileName` / `isSetlistFileName` (and `LibraryFileKind.matches`) are the one answer to which files are the
  library, asked by the scan, the local sync listing, and the remote listing: right extension and not hidden
  (`._song.cho` is macOS's AppleDouble companion).
- `normalizedName` (rules in the root CLAUDE.md) **must stay idempotent**: an exported name is normalized again on its
  way back in. NFC comes first, before folding and lowercasing (it is what composes non-Latin marks). Letters outside
  the BMP are kept as surrogate pairs and never cut; symbols, emoji, and planes 14–16 become separators. A Latin letter
  `Accents.kt`'s `withoutAccent` table does not know becomes a separator, so extend the table; it lives here because two
  modules share it.
- `songBaseName` is the one song naming rule (artist, dash, title, each normalized; the title alone without an
  artist): the local source, the PDF export, and the demo library test use it.
- `identityKey` / `isSameLibraryName` are the one rule for whether two names are one file (NFC, then
  `uppercaseChar().lowercaseChar()` per code point, matching the JVM's `equals(ignoreCase = true)`); asked by storage
  renames, sync matching, and the import (`LibraryNameIdentityJvmTest` holds it against the JVM).
- `withoutCollisionSuffix` reads both the app's `_2` names and sync's ` (2)` conflict copies.

### Text

`decodeLibraryText` is how every library file and every incoming file becomes text, so a file reads the same however it
arrived: UTF-8; mostly-UTF-8 files have stray bytes read through a code page; otherwise Windows-1250 only where the
bytes can only be Hungarian or Polish and no Western-only letter appears, else Windows-1252. Czech, Slovak, and Romanian
stay on 1252 on purpose (their bytes overlap Western ones; guessing would misread correct files). A leading BOM is
dropped (a mid-file one is `ChordProSplitter`'s).
