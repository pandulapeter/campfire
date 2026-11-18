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
  Importable-only types are never claimed system wide; legacy `.doc` is listed only to report it unreadable. `.onsong`
  has no iOS system type, so on iOS it comes only through the in-app picker, never the share extension; the other
  apps' backups (`LIBRARY_BACKUP_EXTENSIONS`) and OpenSong's `.osts` (`FOREIGN_SETLIST_EXTENSIONS`, read with the text
  limit) are iOS imported types, so the share extension takes them. JustChords' `.justchords`
  (`FOREIGN_JSON_EXTENSIONS`, read with the text limit and as JSON by its name) is one app's format, unlike JSON in
  general: an iOS imported type and document type, so iOS offers Campfire for it in Open with as well.
- `isSongFileName` / `isSetlistFileName` (and `LibraryFileKind.matches`) are the one answer to which files are the
  library, for the scan and both sync listings: right extension and not hidden (`._song.cho` is macOS's AppleDouble).
- `normalizedName` **must stay idempotent**: an exported name is normalized again on its way back in. NFC first (it
  composes non-Latin marks). Letters outside the BMP are kept as surrogate pairs, never cut; symbols, emoji, and planes
  14–16 become separators, as does a Latin letter `Accents.kt`'s `withoutAccent` table lacks, so extend the table.
- `isLibraryFileName` is the one rule for `library/files` (`LibraryFileKind.FILE`): 16 to 64 lowercase hex digits of
  the content's SHA-256 and a picture's extension (`isLibraryImageName`) or an attachment's (`isLibraryAttachmentName`,
  `ATTACHMENT_EXTENSIONS`, an allow-list with nothing that runs), never normalized; anything else there is left alone.
  `isLibraryFileNamedFor` tells a file that is what its name says from a damaged one.
  `IMAGE_FILE_EXTENSIONS` are the names a picture arrives under on its own (refused formats included, `.svg` not, being
  text the folder walks would read). Only PNG and JPEG are offered to the OS, and never as its default: Android's Open
  with and Share filters, iOS's document types (`Alternate`) and share extension, the Mac's document types
  (`Alternate`). Not Windows (an MSI or MSIX association would take or nag for the default), Linux, or the web, which
  registers no file types.
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
