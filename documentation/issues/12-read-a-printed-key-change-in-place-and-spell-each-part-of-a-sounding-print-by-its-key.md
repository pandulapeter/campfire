<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Import a key change the PDF prints mid-song as a `{key}` where it stands, and spell each part of a sounding capo print by its own key

**Challenged:** amended — later key rows become changes only in Campfire's export (another sheet's second key row stays
in the header as today), and one naming the key in force writes nothing; the sounding print is cut before every key
change step 1 writes, inside a section too (the old "outside an environment" left a key change printed between a
section's lines spelled by the key before); a key change too wide for its row now prints through `detailRows`, marked,
and is joined back anywhere in the song, where it came back as a lyric `D♭`; the first converter test is an export
sheet.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

A song changing key with a `{transpose}` prints the new key over the part it starts (`PrintRows.keyChangeRows`:
`Key: D♭`). The import has no place for it:

- `extractHeader` writes every key row into the header (`name == ChordProVocabulary.KEY -> header += …`), where tempo
  and time rows after the first become changes in place (`name in timingNames && !declared.add(name) -> changes[index]
  = …`). Corpus `09-capo-transpose.cho` (`{key: G}{capo: 5}{transpose: 2}` … `{transpose: -1}` before the chorus)
  comes back with `{key: E}{capo: 5}{key: Db}` in its header: a second header key, which the parser reads past.
- A key row is metadata only among the first 15 rows (`index < 15 && …`); `labelledRow(…, isHeader = false)` refuses a
  key. Probed: the same key change after a 20-line verse comes back as a lyric `Key: A♭` at the end of the verse, sign
  and all, in a plain print and in a sounding one (`Key: B`, not even moved down by the capo).
- The sounding print (Preferences → Chord names → as they sound) is imported as the capo chart it came from,
  `asCapoChart`, one `ChordProTransposer.transposeText(…, -capo, capo = -capo)` over the whole song, spelled by the
  header key moved down. `09-capo-transpose`'s chorus is printed sounding as `F♯ C♯` under `Key: F♯` and comes back as
  `[C#]After the [G#]change` where the file reads `[Db]…[Ab]` (C♯ major, seven sharps, for D♭ major): the header's E
  wants sharps, the chorus's own key flats. A plain `{key: Db}{capo: 1}` keeps its flats, having one key throughout.

The pitch is right everywhere; what is lost is the key change's place, a stray lyric past row 15, and the chorus's
spelling.

## Fix

In `ChordSheetConverter`:

1. In Campfire's export, key rows after the first are changes, as tempo and time rows are: in `extractHeader`,
   `name == ChordProVocabulary.KEY && lines[index].isCampfire && !declared.add(name) -> changes[index] = …
   "{key: ${headerValue(ascii(value))}}"`, so `writeBody` writes it where it stands (before the section it heads, as
   `flushChanges` does, or between a section's lines where the print set it there). A key equal to the one in force (the
   last written, header or change) writes nothing. A body `{key}` is the file's own mid-song key change
   (`chordpro/CLAUDE.md`: never the header's), kept by Prettify and read past by the model, which takes its chords as
   written. Another sheet's key rows go into the header as today (a second one in the first 15 rows is more likely a
   repetition or a second notation than a change).
2. In a Campfire export, a row that is a lone key value is metadata anywhere in the song: in `classify`,
   `line.isCampfire && labelledRow(line.text, isHeader = true)?.singleOrNull()?.first == ChordProVocabulary.KEY` joins
   the `Kind.METADATA` condition (another producer's "Key: …" past row 15 stays as now; the export prints a key alone on
   its row only as a change). `joinWrappedDetails` joins such a row's marked rest anywhere too: where `line.isCampfire`
   and the joined text is that lone key, it is read with `isHeader = true` (today a key past row 15 is refused, so its
   wrapped value stays a lyric).
3. `asCapoChart` moves each stretch by its own key: split the converted text before every body `{key: …}` line (the
   ones step 1 writes; none is ever inside a tab or grid, since a metadata row closes them before its change is
   flushed, while one inside a verse or chorus is cut through safely, its lines being lyrics either way), and run
   `transposeText(stretch, -capo, capo = -capo)` on each. `transposeText` reads a stretch's opening `{key}` as its key,
   so each stretch is spelled by `prefersFlats` for its own key moved down without any further call, the first by the
   header's, as today. Join the stretches back unchanged otherwise.
4. `convert/CLAUDE.md`: the playing details bullet gains "a key printed further down is the key change it stands for,
   written where it stands, and a sounding capo chart is moved part by part, each spelled by its own key".

In `PrintRows` (print):

5. `keyChangeRows` prints its value through `detailRows(listOf(labels.labelled(labels.key, it)), width)` instead of
   `wrapped`, so a key change too wide for its row (`Tonalitate: D♭` at size 20 in four columns, 25 mm margins) marks
   its rest as the opening details row does, rather than leaving `D♭` an unmarked row the import takes for a lyric.
   `ui/print/CLAUDE.md`'s "A value too wide for a row of its own wraps" sentence names the key changes too.

With plan 02a, a shapes print's key changes stay in the shapes' frame (`ChordProChordRewriter` renames a `{transpose}`'s
key with the chords) while its heading names the sounding key and the shapes' one; the import writes the shapes' key in
the header (02a) and the changes as printed, so both are in the frame of the chords under them.

Plans 02a and 06 also edit `extractHeader` and `joinWrappedDetails`; land them first.

## Tests

- `ChordSheetConverterTest`: a Campfire export sheet (built as the `isCampfireExport = true` cases build theirs) of the
  rows `Song`, `Someone`, `Key: A   Capo: 5 (chords as they sound)`, `Verse 1`, `A     E` (bold), `Hello world`,
  `Key: F♯`, `Chorus`, `F♯    C♯` (bold), `After the change`, asserting the header holds one `{key: E}`, a `{key: Db}`
  stands before `{start_of_chorus`, and the chorus reads `[Db]After the [Ab]change`; the same with `Key: A` again where
  `Key: F♯` is, asserting no body `{key`; a sheet with 20 lyric rows before a `Key: A♭` row asserting `{key: Ab}` in
  place and no `Key:` lyric, and the same with the row wrapped (`Key:` / `⁠A♭`, the second marked) giving the same;
  and the plain text `Song\nSomeone\nKey: A\nKey: G\nVerse 1\nA     E\nHello world` keeping both keys in its
  header as today (no body `{key`). Fails before the fix.
- `PdfRoundTripTest`: `09-capo-transpose.cho` printed sounding (`songRenderer.transposedSong(text, 5, …)` with
  `areChordsSounding = true`) comes back with the chorus `[Db]After the [Ab]change`, compared through `summary`; and a
  song with a `{transpose}` before its chorus printed in every `values*` folder at `PrintSettings(columns = 4,
  fontSize = 20, marginMm = 25)` comes back with its `{key}` change before the chorus and no lyric but its own.
