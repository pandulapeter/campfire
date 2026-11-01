# Mark a printed capo over chords named as they sound, and import it as the shapes the capo plays

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — `transposeText` moves no Latin chord or key (probe: `{key: Fa#m}[Fa#m]…[Re]…` came back
unchanged at -2), and the converter's output is still in the notation the PDF printed it in (the import brings it to the
standard one only afterwards, `PrepareImportUseCaseImpl`), so a Spanish, French, Italian, or Romanian reader printing in
Latin would still get the song two semitones high; the move now runs on the text brought into the standard notation
first. Added: where the sounding marker is told apart (before `capo()` keeps only the digits), a Hebrew fallback as plan
05's, a short marker so a narrow column never wraps it, Latin and German cases in the tests, and how 5.2.x reads it.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDocument.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/PrintLabelsResource.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu`, `values-ro`, `values-de`,
`values-es`, `values-fr`, `values-it`, `values-he` (`strings.xml` each),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposer.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposerTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Runs after plan 05 in lane P (it relies on every language's capo label being read back).

## Problem

Since fd1c29082 the PDF prints the capo even where the chords are named as they sound (the keyboard always; a fretted
instrument with Settings → Preferences → Chord names = Sounding). `ExportController.kt`:

```kotlin
// The pages name the chords as the screen does: what sounds, the capo played counted in, or the shapes fingered
// above it ([shapeNames]); the diagrams are the shapes played above the capo, none on the keyboard, which still
// prints the capo, since it raises the pitch for every instrument.
...
val printed = named?.withTempo(tempo)?.withCapo(capo)
```

At aca41acce the keyboard printed no capo (`if (playsWithoutCapo) it.withoutCapo() else it.withCapo(capo)`); keeping it
is the author's deliberate choice. But the row says nothing about how the chords under it are named.
`PrintLayout.playingDetails`:

```kotlin
song.metadata.capo?.takeIf { options.showKey && it != 0 }?.let { capo ->
    // Where the chords are the shapes fingered above it, the key before it being the one that sounds, it says so.
    labels.labelled(labels.capo, entry.shapesKey?.let { labels.capoWithShapes("$capo", it) } ?: "$capo")
},
```

With shapes the row reads `Key: F#m   Capo: 2 (Em shapes)` over Em chords; with sounding names it reads
`Key: F#m   Capo: 2` over F#m chords. Campfire's own PDF import (`ChordSheetConverter.capo` keeps only the digits) turns
both into `{key: F#m}{capo: 2}`, and a Campfire file's chords under a `{capo}` are the shapes played above it
(`SongKeys`' KDoc, `chordpro/scales/SongKeys.kt`: `{key: G}{capo: 5}` over D shapes). `SongKeys.capoChartKeyOf` finds
the written key holds the chords, so the song is read as F#m shapes over capo 2: it sounds G#m, a whole step above
what was printed. The shapes case round-trips correctly (checked in en, hu, es, de). Anyone who prints with sounding
names, or on the keyboard, and imports the PDF (a bandmate, another device) gets a song in the wrong key, with nothing
on screen to say so.

## Fix

**Option (a), recommended (README decision D1):** say on the page that the chords sound as written, and import that
as the capo chart it was printed from.

1. **The print.** A new string `print_capo_sounding`, `%1$s (chords as they sound)` in `values/strings.xml`, translated
   in all seven others in the words each already uses for Chord names → Sounding (`settings_chord_names_sounding`,
   `chord_names_sounding_description`), with the capo as `%1$s`. Keep each translation no longer than that language's
   `song_details_capo_shapes` with a three-letter key: `detailRows` breaks the row between values, but a single value
   wider than a narrow column wraps inside itself, and the import then reads the label's value
   cut and the rest as a lyric. Read it in `rememberPrintLabels` as
   `plainTextResource(Res.string.print_capo_sounding, LABELLED_VALUE)` into a new `PrintLabels.capoSounding`
   (default `"$LABELLED_VALUE (chords as they sound)"`, KDoc as `capoWithShapes`'s, and add it to the `remember` keys),
   with `fun capoSounding(capo: String)`. In `playingDetails`, the capo value is `capoWithShapes` where
   `entry.shapesKey` is set, `capoSounding` where the chords are named as they sound, and the bare number only where
   neither holds (shapes whose key is unknown: `ShapeNames.shapesKey` null, which the import already reads right).
   Whether the chords sound is what `ExportController` already knows: `shapeNamesOf` (`ui/playing/ShapeNames.kt`)
   returns null exactly "where its chords are named as they sound, under no capo, a reader naming what sounds, or on
   the keyboard", so with a printed capo, `shapeNames == null` means sounding names. `PrintSong` cannot tell today: add
   `areChordsSounding: Boolean = false` beside `shapesKey` in `PrintDocument.kt` (KDoc it with the `shapesKey` one),
   set in `ExportController` as `areChordsSounding = shapeNames == null`, next to `shapesKey = shapeNames?.shapesKey`.
2. **The import.** In `ChordSheetConverter`, a capo value carrying the sounding marker is read as a sounding capo:
   the song keeps `{capo: N}`, and its chords are moved down by N to the shapes played above it. The marker is the
   parenthesis of `print_capo_sounding` in each of the eight languages (hard-coded beside the label tables, the way
   plan 05 adds the labels; plan 01's round-trip test below keeps them in step with `strings.xml`). A value of
   another site (`Capo: 2nd fret`, `Capo 2 (optional)`) is unchanged.
   - Tell the marker apart where the capo value is still whole: `metadata(text)` and `labelledRow` pass the value
     through `capo()`, which keeps only the digits, so the marker is lost by `extractHeader`. Read it there (for
     example a separate name such as `CAPO_SOUNDING`, beside `TRANSPOSITION` and `NO_CAPO`, that `extractHeader` writes
     as `{capo: N}` and returns N to `convertSong`), and keep `labelledRow`'s `"capo" -> isHeader && value.isNotEmpty()`
     accepting it.
   - **Bring the text into the standard notation before moving it.** `convertSong`'s output keeps the chord names and
     the key as the PDF printed them, in the reader's notation (`ExportController` renders the song with
     `notatedSong(…, spelling, …)`; the converter keeps Latin names, `isChordOrLatinName`), and only the import's
     `ChordProNotation.convertText(part, STANDARD, STANDARD)` (`PrepareImportUseCaseImpl`) brings them into the standard
     notation, later. `ChordProTransposer.transposeText` reads German but not Latin: `transposeChord` returns a name
     that is no standard chord unchanged, so at 286971fe4 `transposeText("{key: Fa#m}\n[Fa#m]Hello [Re]world", -2)`
     returns it as it was (challenge probe), while the `{capo: 2}` stays: the bug again, for every reader in Latin. So
     move `ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.STANDARD)` of the converted song
     (it returns a standard text unchanged, and the import's own call is then a no-op on it). Nashville and Roman
     prints need nothing more: their numbers are not read back as chords (as today), and only the `{key}` moves, which
     is still the right capo chart.
   - Move the chords with `ChordProTransposer.transposeText(standardText, -N, capo = -N)` on the converted song's text,
     after `convertSong` builds it and before `normalize`. `transposeText` has no `capo` today; add one mirroring `transpose`'s (`@param capo How
     many of [semitones] a tab's frets are not moved by`): its `rewriteTab` moves frets by `semitones - capo`, while
     every chord name, a tab's included, and the `{key}` move by `semitones`. Reason: a PDF printed with sounding
     names prints the tabs counted from the capo (`ExportController` passes `capo = tabCapo` to `transposedSong`), so
     their frets must stay as printed while their chord names move. Default `capo = 0` keeps every existing caller.
   - Result: `{key: Em}{capo: 2}` over Em shapes, the ordinary capo chart; it sounds F#m, exactly what was printed,
     and the capo the reader played with survives the trip, as it does for a shapes print.
3. **An older Campfire (5.2.x) reading a new PDF** keeps only the digits (`capo()`), so it imports a sounding print
   as today: the capo over the sounding chords, a whole step high. No worse than now, and the row now tells a human
   reader how the chords are named.
4. **Why not the alternatives.** (a1) Importing the sounding marker as no capo also sounds right, but drops the capo
   the song was written for and would need the tabs' frets raised by N, since they were printed counted from it.
   (b) Dropping the capo from sounding-named prints reverses the author's choice in fd1c29082 ("it raises the pitch
   for every instrument") and leaves the bare number ambiguous to a human reader; rejected.
5. Docs: in `ui/print/CLAUDE.md`, after `"2 (Em shapes)" where the chords are named by the shapes,
   PrintLabels.capoWithShapes`, add the sounding label (`PrintLabels.capoSounding`) and that the import reads it back
   as shapes over the same capo. In the convert `CLAUDE.md`, one line: a capo marked as sounding is imported as the
   capo chart (chords moved down by the capo in the standard notation, tab frets kept).

## Tests

- `ChordSheetConverterTest`: `Key: F#m   Capo: 2 (chords as they sound)` over `F#m D` chords and a tab line comes back
  as `{key: Em}`, `{capo: 2}`, `[Em]…[C]…`, tab frets unchanged; `Key: F#m   Capo: 2 (Em shapes)` over Em chords is
  unchanged (`{key: F#m}{capo: 2}`, Em chords); `Capo: 2 (optional)` over F#m stays F#m with capo 2. One case per
  language for the marker (hard-coded strings, as plan 05's test). One case in Latin
  (`Tonalidad: Fa#m   Capo: 2 (<es marker>)` over `Fa#m Re` → `{key: Em}`, `[Em]`, `[C]`) and one in German with an
  `H` among the chords (`Kapodaster: 2 (<de marker>)` alone in the row, over `H E` → `[A]`, `[D]`, capo 2).
- `ChordProTransposerTest`: `transposeText` with `capo` moves a
  tab's chord names but not its frets; with `capo = 0` it behaves as before.
- `PdfRoundTripTest` (desktop, the real PDF writer and extractor): for each of the eight languages, with that
  language's labels built from its `strings.xml` (as plan 05's case does), print a capo-2 song both ways — the shapes
  naming (`shapesKey = "Em"`, Em chords) and the sounding naming (F#m chords, sounding flag set) — import each, and
  assert that both imports sound the same: `SongKeys.ofChords(imported)` moved up by `imported.metadata.capo` names
  F#m, and the capo is 2. Run the sounding print once more with the Spanish labels and the Latin notation, and once
  with the German labels and notation. If the Hebrew marker does not come back through the right-to-left extraction,
  keep the Hebrew case in `ChordSheetConverterTest` (text as the extractor returns it) and say so in the lane report,
  as plan 05 does.
- `PrintLayoutTest`: the playing row reads `Capo: 2 (chords as they sound)` for a sounding entry, `Capo: 2 (Em shapes)`
  for a shapes entry, and `Capo: 2` for neither.
