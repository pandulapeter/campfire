<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# domain/implementation/importing

### Preparing

- A loose file goes through `ArchiveRepository.songsIn` first: another app's document of songs (JSON, OpenSong,
  `.onsong`) or an ABC file (`.abc`, or text that reads as ABC) is those songs, before a setlist is tried or an unknown
  name skipped.
- A file under an unknown name whose bytes read as text (`isPlainTextByContent`) is a song only with
  `ChordSheetStructure.hasSongStructure`, else reported skipped. In a bytes-only archive such an entry, and a `.txt`,
  count toward the gate only with structure (a `.txt` also when too large), so an e-book or a licence stays one skipped
  file. One known only by its bytes that holds nothing importable is reported as the one unsupported file it was, since
  OpenDocument files, e-books, and spreadsheets are zips too; a bare `.json` does not count (inside another app's
  container it is that app's configuration).
- Each song is named by `SongRepository.importFileName` from its own header. The arrival name is only a fallback
  *title* (and a match for a library file of exactly that name with the same text: exports carry library names,
  possibly from an older naming rule).
- The planner raises one conflict per library name and leaves numbering to the write. Recorded names are the library's
  own spellings (case / Unicode form), so no setlist points at a name the song list lacks.
- Songs compare by text, setlists by fields, never by stored document. An undated setlist file is compared without
  date or countdown (parsing gives it today; a replacement keeps the library's), so re-importing the demo setlist finds
  it unchanged. Setlist entries compare as they will be written, resolved first among songs of the same picked file
  (`SongEntry.origin`), since names are unique only within an archive.
- Archives share one size budget.
- Plain text (a `.txt`, so every paste and share) is first asked `AbcText.isAbc`: ABC is one song per tune, since the
  converter would read its note groups (`[CEG]`) as chords; ChordPro holding an ABC block is no ABC. ChordPro
  extensions never go through the converter. No readable text is `unreadableDocumentFileNames`, not unsupported. All
  incoming songs are prettified after conversion, splitting, and notation normalization. No origin info survives in
  the library.

### Applying

- A plan is held against the library as prepared (the review may stay open indefinitely). Only two things are
  re-checked at write time: setlists are re-planned on the names songs actually got (`ImportPlanner.replanSetlists`),
  and an identical song whose library file sync deleted meanwhile is written after all. A replacement is not
  re-checked: the user confirmed it by name.
- Songs before setlists. A disregarded duplicate maps to the library copy; a renamed collision (`x_2.cho`) makes the
  setlist naming it a different setlist, numbered rather than disregarded. A setlist that becomes a conflict only at
  re-plan is written numbered (no choice was offered), unless unticked.
- An unticked entry is left alone with its repeats; a new song left out is still pointed at by its batch's setlists
  under its arrival name (shown as missing). Identical entries are disregarded whatever the choices say.
- A write failure stops before any dependent setlist. What was written is adopted in one change (`adoptImported`, also
  after failure or cancellation), from the models built, not by rescanning.
- `ApplyImportUseCaseImpl` writes the plan and then its records (the demo's names, each shared link's) whole, even for a
  cancelled caller. A link's review starts on Replace only for names that link wrote here (`SharedLinkChoices.kt` in
  `:data:model`), never for the recipient's own songs.
