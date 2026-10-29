<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: importing sheet music from MusicXML

**Status:** not started. Written against the code on 2026-10-29 (90efe86fe). `formats/` is
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/`, `backup/` is
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/`.

The import learns [MusicXML](https://www.w3.org/2021/06/musicxml40/), the format every notation app exports
(MuseScore, Sibelius, Dorico, Finale, Notion, Flat, Noteflight, and scanner apps such as PlayScore). A score becomes one
song: its notes kept as an `{start_of_abc}` block that the existing renderer draws, and, where the score has lyrics,
a chord sheet of its verses with the score's chord symbols placed over their syllables.

## Why not PDF

Reading notes from a PDF is optical music recognition. A scanned page needs an OMR engine: Audiveris is JVM-only,
AGPL (which would pull the whole app under AGPL and off the App Store), and needs Tesseract; the machine-learning
readers are Python with large models. None of them runs on iOS or wasm, and a cloud service breaks "documents are
converted locally, never uploaded". A PDF exported from a notation app draws its noteheads as music-font glyphs, but
its stems, beams, slurs, and staff lines are bare paths, and rebuilding rhythm and voices from them is most of OMR
again, differently for every engraver. Whoever has such a PDF can usually export MusicXML from the app that made it,
or get it from the same site; step 6 tells them so.

## Decisions

- **Written from the specification, not ported.** `xml2abc` (LGPL) and `music21` are reading material for the edge
  cases only; no code is copied, so no new dependency is shipped or credited. The MusicXML DTDs and schema are not
  bundled either: the reader is lenient and reads what it knows.
- **MusicXML goes to ABC, and the ABC is the source.** The song keeps the score as an ABC block exactly as `.abc`
  imports do (`chordpro/abc/CLAUDE.md`): editing it later regenerates nothing, and transposition, notation names, and
  print already work for it. No second notation renderer and no MusicXML in the library.
- **Lyrics make the chord sheet.** MusicXML ties every `<harmony>` and every `<lyric>` syllable to a note, which ABC's
  `w:` alignment hides from `AbcToChordPro` (it reads chord symbols as a grid and leaves `w:` in the block). So a score
  with lyrics gets real verses: each `<lyric number>` is one verse, its syllables joined into words (`syllabic`
  begin/middle/end), each chord symbol placed before the syllable of the note it sounds on (or the next one with a
  syllable). A score with no lyrics gets `AbcToChordPro`'s chord grid as an `.abc` does, by converting the generated ABC.
- **One song per score.** A `.mxl` holds one score; `opus` files (a collection of scores) are read as one song per
  score they reference inside the same archive.
- **Every part is kept**, each as an ABC voice (`V:`), a part on two staves (a piano) as two voices. The song is the
  whole score; trimming a part is an edit of the block.
- **Written pitch, as the score shows it.** A transposing part (`<transpose>`) keeps its written notes, and its `V:` line says
  how far it sounds from them in whichever form abc2svg reads for that (settled in step 3); the song's `{key}` is the
  concert key.
- **Where it lives.** `formats/musicxml/` (beside the PDF and Word readers, reusing `parseXml` and the zip reader)
  reads a score into a small model and writes it as ABC; it knows nothing of ChordPro. `backup/MusicXmlFile.kt` makes
  the song, using `:chordpro` for directives, escaping, and the grid, the way `AbcFile` does.
- **Open with**, like `.abc`: Campfire is offered for `.musicxml` and `.mxl` but never made their default, since it
  is no notation editor.

## Where it hooks in today

- `formats/document/XmlPullParser.kt`: `parseXml`, bounded at 8 MB and 600,000 events. It refuses any `<!`
  declaration, and every MusicXML file starts with a `<!DOCTYPE score-partwise PUBLIC …>`.
- `formats/zip/ZipReader.kt`: for `.mxl` (a zip with `META-INF/container.xml` naming the root file).
- `ArchiveLocalSourceImpl.kt` (`data/source/local/implementation/.../source/`): `unpack` treats any zip by its bytes
  as an archive, so a `.mxl` today is unpacked and its `.xml` handed to `OpenSongSong`, which fails; `foreignSongs`
  routes by extension and first character.
- `backup/AbcFile.kt`, `BatchNames`, `ForeignSongHeader.uniqueName`: naming and the shape of a foreign reader.
- `chordpro/abc/AbcToChordPro.kt`: the grid for a score without lyrics.
- `LibraryFiles` (`data/model`): `FOREIGN_SONG_EXTENSIONS`, `IMPORTABLE_EXTENSIONS`, `isArchiveFileName`;
  `ImportLimits.MAX_TEXT_FILE_SIZE` (8 MB) already covers an uncompressed score.
- `PrepareImportUseCaseImpl` (`domain/implementation/.../importing/`): pastes and shares of plain text; MusicXML is never
  pasted, so nothing changes there.
- Registration: `app/android/src/main/AndroidManifest.xml` (the `.abc` filters), `app/ios/iosApp/iosApp/Info.plist`
  (imported UTIs `com.recordare.musicxml` and `com.recordare.musicxml.uncompressed`, as `org.abcnotation.abc` is), the
  share extension's `Info.plist` and `ShareInboxWriter.swift`, and the desktop and web file pickers.

## Steps

### 1. The XML reader takes a DOCTYPE

`XmlPullParser` skips a `<!DOCTYPE …>` with no internal subset and keeps refusing one with a `[` (where entities would
be declared), so nothing is expanded. Tests: MusicXML's public DOCTYPE, a DOCTYPE with an internal subset refused, a
DOCTYPE after the root refused. Word and OpenSong reading are unchanged.

### 2. The score model and its reader (`formats/musicxml/`, pure, `commonTest`)

`MusicXmlScore`: title (`work-title`, else `movement-title`, else the first large `<credit-words>`), composer and
lyricist (`<creator type>`), parts (name, staves, transposition), and measures per part: attributes (divisions, key
with mode, time, clefs), directions (tempo from `<sound tempo>` or `<metronome>`), barlines (repeats, endings), and
events in time order per voice: notes (pitch or rest or unpitched, duration, dots, chord member, tie, tuplet ratio,
grace, voice, staff, fermata), harmonies (root, alter, kind, bass), and lyrics (number, syllabic, text, extend).

- `score-timewise` is pivoted to partwise; `<backup>` and `<forward>` are resolved into each voice's own timeline.
- A `.mxl` is read through `container.xml`'s first `rootfile`, or the first `.musicxml`/`.xml` outside `META-INF`.
- Malformed input never throws past the reader: a file that is no score returns null, and a measure the reader cannot
  make sense of is kept as a rest of the measure's length, so one bad measure does not lose the tune.

Tests: small hand-written scores per feature, plus three real exports (MuseScore 4, Dorico, and Finale or Sibelius)
of a short score written for the tests, so that their licence is ours.

### 3. ABC writer (`formats/musicxml/MusicXmlToAbc.kt`, pure)

- Header: `X:1`, `T:`, `C:`, `M:`, `L:` (the shortest common unit of the score, 1/8 at least), `Q:`, `%%score` for
  the voices and their staff braces, `V:` per voice with `name=`, `clef=`, and the part's transposition, then `K:` with mode.
- Notes: pitch with octave marks; accidentals written where the key signature and the measure so far (ABC's carry
  rule, per octave as abc2svg reads it) would otherwise sound a different pitch, and kept where the score shows a
  cautionary one; lengths as fractions of `L:`; dots folded into lengths; tuplets as `(p:q:r`; chords `[…]`; ties `-`;
  grace notes `{…}`; rests `z` and whole-measure rests `Z`; beams from `<beam>` (notes of one beam written together,
  others apart).
- Chord symbols `"Gm7"` before their note, spelled from `<kind>` (a table of MusicXML's 33 kinds to the suffixes
  `:chordpro`'s parser reads, `text=` used where the score gives its own), with `/bass`.
- Bars: `|`, `||`, `|]`, repeats `|:` `:|` `::`, endings `[1` `[2`; a pickup measure (`implicit="yes"`) as a short
  first bar; mid-tune key, time, and clef changes as inline fields.
- Lyrics as `w:` lines per voice and verse (`-` between syllables, `_` for an extend, `*` for a note without one), so
  the notation shows them as the score did.
- Dropped on purpose: layout (`<print>`, system breaks beyond one line per four bars or the score's own breaks),
  dynamics, articulations other than fermata and staccato, slurs that cross voices, and anything a voice holds that
  ABC cannot say; the tune still plays and reads right without them.

Tests: per feature, as ABC text; and, where the `:abc` module's desktop tests can run the engine, every fixture
rendered by abc2svg without an error.

### 4. The song (`backup/MusicXmlFile.kt`)

- With lyrics: `{title}`, `{artist}` (the composer; the lyricist goes in `{lyricist}`), `{key}`, `{time}`,
  `{tempo}`, then one `{start_of_verse}` per lyric number with the chords placed over syllables (each line one
  system of the score, else four bars), then the ABC block. Repeated sections are not unrolled: a verse is what the
  score prints as lyric line *n*.
- Without lyrics: the generated ABC through `AbcToChordPro`, the same song an `.abc` export of the score would give.
- Named as `AbcFile` names, by title or the file's name, unique within the batch.

Tests: a lead sheet with two verses and chords, a choral score (lyrics in several parts: verses come from the top
part with lyrics), an instrumental score (the grid), and a score with no chord symbols (verses with no chords).

### 5. Routing and registration

- `LibraryFiles`: `MUSICXML_EXTENSIONS = listOf(".musicxml", ".mxl")`, added to `IMPORTABLE_EXTENSIONS`.
- `ArchiveLocalSourceImpl`: a `.mxl`, or any zip holding `META-INF/container.xml`, is read as a score before the
  archive unpack; a `.musicxml`, or an `.xml` whose root is `score-partwise` or `score-timewise`, before
  `OpenSongSong`.
- Android, iOS (app and share extension), desktop, and web: the extensions and types beside `.abc`'s, offered and
  never default. The comments that list the formats there name MusicXML too.

### 6. A notation PDF says what to do instead

`PdfTextExtractor` already sees the fonts: a PDF whose text is mostly a music font's glyphs (Bravura, Leland,
Petaluma, Emmentaler, Opus, Maestro, Finale Maestro) and holds no lyrics or chords to convert is reported in the
review as sheet music, with one sentence: export MusicXML from the app that made it and import that. One string in
every app language.

### 7. Documentation

- `formats/musicxml/CLAUDE.md`: what is read, what is dropped, and why ABC is the source.
- Root `CLAUDE.md`: MusicXML in the opening list of imports and in the product rule on other apps' songs
  (`data/formats`); `data/formats/CLAUDE.md` and `backup/`'s docs.
- At release time: the website's import page, the store listings, and what's new.

## Size

About 8 small files in `formats/musicxml/` and one in `backup/`, plus one change each to `XmlPullParser`,
`ArchiveLocalSourceImpl`, `LibraryFiles`, and the four platforms' registrations. All of it is pure and tested in
`commonTest`. The bulk and the risk is step 3: accidentals, voices, and tuplets are where generated ABC goes wrong,
and the real exports are what catch it.
