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
  `.onsong`) or an ABC file (`.abc`, or text that reads as ABC) is those songs (an OpenSong set its setlist), before a setlist is tried or an unknown
  name skipped.
- A file under an unknown name whose bytes read as text (`isPlainTextByContent`) is a song only with
  `ChordSheetStructure.hasSongStructure`, else reported skipped. In a bytes-only archive such an entry, and a `.txt`,
  count toward the gate only with structure (a `.txt` also when too large), so an e-book or a licence stays one skipped
  file. One known only by its bytes that holds nothing importable is reported as the one unsupported file it was, since
  OpenDocument files, e-books, and spreadsheets are zips too; a bare `.json` does not count (inside another app's
  container it is that app's configuration).
- Each song is named by `SongRepository.importFileName` from its own header. The arrival name is only a fallback
  *title* (and a match for a library file of exactly that name with the same text: exports carry library names).
- The planner raises one conflict per library file and leaves numbering to the write. A song arriving as a numbered
  sibling of its header's name that the library lists (`x_2.cho`) is held against that file, so a backup restored with
  Replace goes back onto the files it came from; a hand-named loose `x_2.cho` then asks too. Recorded names are the
  library's own spellings (case / Unicode form), so no setlist points at a name the song list lacks.
- Songs compare by text, setlists by fields, never by stored document. An undated setlist file is compared without
  date or countdown (parsing gives it today; a replacement keeps the library's), so re-importing the demo setlist finds
  it unchanged. Setlist entries compare as they will be written, resolved first among songs of the same picked file
  (`SongEntry.origin`), since names are unique only within an archive.
- A setlist from another app's set (OpenSong) names some songs only by title (`ImportedFile.setlistSongTitles`): before
  planning, `SetlistSongTitles` points each such entry the batch does not resolve at a batch song of that title (its
  own picked file's first), else a library song's (identity-folded, one not taken by the setlist first), else leaves it
  missing. The library is read only for such a setlist.
- An archive (`ImportedFile.isArchive`) reaches the preparation staged (`ImportedFile.staged`): the intake receives it,
  and `ImportWork` stages one a caller read whole. It goes to the streaming unpack, its song texts within the songs'
  budget shared by the batch, its pictures and attachments staged within the disk budget; the in-memory budgets bound
  only the picture links; the phase counts the bytes the unpacks read of the staged archives (`UnpackingProgress`), a
  nested archive's size joining the total as it is reached. The received archive stays with its request for Try again.
  The in-memory unpack (`unpack(ByteArray)`) is reached only by the tests, as the streaming one's reference; an unstaged
  archive with nothing read is skipped without it. An archive that cannot be unpacked is skipped, unless for lack of space (`isOutOfSpace`)
  or past 4 GB without ZIP64 (`WrappedArchiveException`), which end the preparation.
- **Pictures** (`IncomingImages`): a song's `src` values are rewritten to the library names of the pictures it brings
  when it is planned, so the review, the duplicate check, and the write all see the text the song will have; that is
  the one change an import makes to a song's text. A picture missing, too large, or refused leaves its directive as it
  arrived. A file chosen on its own that is a picture by its bytes is `ImportPlan.imageFiles`, never a song; one left unread
  for its size is oversized.
- **Attachments** go the same way (`ImportedImage.isAttachment`, `SongImage.isAttachment`): read from the archive only
  for a song naming them (a content-named attachment is never read as a document of its own, nor is any entry a song
  takes), judged by size and extension (`libraryNameOfAttachment`), their lines' files rewritten, stored by
  `addAttachment`. One missing, too large, or of a refused type keeps its line as it arrived, so sync can bring the file
  later; the review counts brought and left-out attachments apart from pictures. A file a song both shows and attaches
  is read once, as a picture.
- **Pictures by address** (`IncomingPictureLinks`, only while `isPictureLinkDownloadEnabled`): every `https` `src` of
  the batch's song files is downloaded once as it is prepared, four at a time, within the picture limit and what is
  left of the batch's picture budget, never re-encoded, then planned and rewritten as an archive's pictures are;
  written only on Import. One that brings nothing is left out unreported, its directive kept.
- Plain text (a `.txt`, so every paste and share) is first asked `AbcText.isAbc`: ABC is one song per tune, since the
  converter would read its note groups (`[CEG]`) as chords; ChordPro holding an ABC block is no ABC. ChordPro
  extensions never go through the converter. No readable text is `unreadableDocumentFileNames`, not unsupported. All
  incoming songs are prettified after conversion, splitting, and notation normalization. No origin info survives in
  the library.

### Applying

- A plan is held against the library as prepared. Only two things are re-checked at write time: setlists are
  re-planned on the names songs actually got (`ImportPlanner.replanSetlists`), and an identical song whose library file
  sync deleted meanwhile is written after all. A replacement is not re-checked: the user confirmed it by name.
- Everything is written inside one `ImageRepository.withImagesKept`, each song's pictures just before it and the songs
  adopted before the block ends, so no prune falls between a picture and its song. Inside it, before anything is
  written, every staged file the plan names must still be there whole (`ImportStagingRepository.isIntact`), else
  `StagedFileMissingException` with nothing written. A staged file is moved in by the first song bringing it; a move that
  fails is a write failure like any other, the files moved before it left for the prune. Once the staged files moved in
  pass `MAX_STAGED_BYTES_PER_HOLD` (256 MiB) the block is let go of after the song, the songs written so far adopted
  first, and taken again: on the web a move writes the file (about 130 MB a second on a desktop browser), and sync's
  deletions and Save wait for the block. An import of files in memory stays one block. A picture whose planned name a
  different one took meanwhile is stored under a longer one, which the written text names. An identical song stores its
  pictures too: a library restored without them (Android's backup leaves them out) gets them back.
- Songs before setlists. A disregarded duplicate maps to the library copy; a renamed collision (`x_2.cho`) makes the
  setlist naming it a different setlist, numbered rather than disregarded. A setlist that becomes a conflict only at
  re-plan is written numbered (no choice was offered), unless unticked.
- An unticked entry is left alone with its repeats; a new song left out is still pointed at by its batch's setlists
  under its arrival name (shown as missing). Identical entries are disregarded whatever the choices say.
- The result lists songs naming files the batch left out (`songsWithFilesLeftOut`, the review's counts); what the
  batch wrote is never also a duplicate.
- A write failure stops before any dependent setlist. What was written is adopted in one change (`adoptImported`, also
  after failure or cancellation), from the models built, not by rescanning.
- `ApplyImportUseCaseImpl` writes the plan and then its records (the demo's names, each shared link's) whole, even for a
  cancelled caller. A link's review starts on Replace only for names that link wrote here (`SharedLinkChoices.kt` in
  `:data:model`), never for the recipient's own songs.
