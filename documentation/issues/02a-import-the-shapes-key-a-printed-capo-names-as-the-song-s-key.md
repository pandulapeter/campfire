<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Import the key a Campfire PDF names in "Capo: 2 (Em shapes)" as the song's `{key}`, so a re-imported capo chart is never read in the sounding key

**Challenged:** amended — `CAPO_SHAPES` only where the fret reads (an unreadable one stays a lyric as today); the key cut
from the original text, not the lowercased one; the false "third-party sheets are untouched" replaced by what changes
for them and a test pinning it; 06's German round trip gains the key.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`

## Problem

Under the default chord names (shapes), the PDF prints a capo song's key as it sounds and the shapes' key after the
capo: `PrintLayout.playingDetails`

```kotlin
entry.shapesKey != null -> labels.capoWithShapes("$capo", entry.shapesKey)
```

gives `Key: F♯m   Capo: 2 (Em shapes)` over the Em shapes. The importer reads only the capo's digits:
`ChordSheetConverter.capo`

```kotlin
private fun capo(value: String): String = Regex("[0-9]+").find(value)?.value
```

so the song comes back as `{key: F#m}{capo: 2}` over the shapes, a capo chart keyed by the sounding key, which only
`SongKeys`' heuristic (`Harmony.capoKey`, which needs the shapes' tonic to be the first-ranked root) can tell from a
chart in F♯m. Wherever the heuristic misses, the re-imported song is read in F♯m: the header, cards, Transposition
control, degrees, and scale suggestions name F♯m as the chords' key and G♯m as what sounds.

Reproduction (probed at HEAD with `PdfRoundTripTest`'s pipeline): a file `{key: Em}{capo: 2}` over
`[C]Hello [G]world [D]and [Em]more`, exported with the default settings (printed `Key: F♯m   Capo: 2 (Em shapes)`, in
every language: `(Em-Griffe)`, `(diteggiature di Em)`, …) and imported back gives `{key: F#m}{capo: 2}`, and
`SongKeys.ofChords` of it is `F#m` (`capoChartKeyOf` null: the ranked roots are C, E, G). The original file said Em. The
two print modes also import different keys for one song (shapes print keeps the sounding key, the sounding print, read
as a capo chart, moves it down to the shapes'), which `PdfRoundTripTest`'s capo test concedes: "which SongKeys may read
either way".

`ChordSheetConverterTest` pins the current reading on purpose (`a capo marked as sounding is read as the capo chart it
was printed from`: `Key: F#m   Capo: 2 (Em shapes)` → `{key: F#m}`), from the 51st review's plan, which left the
key's reading to `SongKeys`; that reading is what fails, so the assertion changes with this fix.

## Fix

Read the shapes marker the way `soundingCapoMarkers` reads the sounding one, and write the key it names:

1. Add the localized forms of `song_details_capo_shapes` (`%1$s (%2$s shapes)`) as prefix/suffix pairs around the key
   inside the parentheses, lowercase, beside `soundingCapoMarkers` (with a KDoc naming the string): en `"" / " shapes"`,
   hu `"" / " fogások"`, ro `"poziții de " / ""`, de `"" / "-Griffe"`, es `"formas de " / ""`, fr `"doigtés de " / ""`,
   it `"diteggiature di " / ""`, he `"צורות " / ""` (escape non-ASCII as the file does). A function
   `shapesKeyOf(value: String): String?` takes the parenthesised text (`parentheses.find(value)`), trimmed, finds a pair
   whose prefix and suffix its **lowercased** form starts and ends with, and cuts the **original** text by those lengths
   (so `Em-Griffe` gives `Em`, not `em`, which is no chord), trimmed; it returns that rest only where
   `isChordOrLatinName(ascii(rest))`.
2. In `metadata()`, return a new name `CAPO_SHAPES` (a `const` beside `CAPO_SOUNDING`) with the raw value (after
   `capoPreposition`) **only where `shapesKeyOf` finds a key and `capo(value)` is not blank**; otherwise the line is read
   as today (`"capo"` to `capo(value)`, blank where no fret reads, which keeps `Capo: II (G shapes)`'s line the lyric it
   is today). Accept it in `labelledRow` where `capo` and `CAPO_SOUNDING` are (`"capo", CAPO_SOUNDING, CAPO_SHAPES ->
   isHeader && value.isNotEmpty()`).
3. In `extractHeader`, for `CAPO_SHAPES` write `{capo: ${capo(value)}}` and remember
   `headerValue(ascii(shapesKeyOf(value)!!))`; once the loop is done, replace the header's `{key: …}` entry with that
   key, or add one before the `{capo: …}` entry where no row named a key. The key goes through the same `ascii` and
   `headerValue` as the printed key, so German and Latin names are handled exactly as the `Key:` value already is.
4. Docs: `convert/CLAUDE.md`'s playing details bullet gains "a capo marked with its shapes' key
   (`song_details_capo_shapes`, in any of the app's languages, from any sheet) writes that key as the song's `{key}`";
   `ui/print/CLAUDE.md`'s playing row sentence ("2 (Em shapes)" …) says the import reads that key back as the song's.

This needs no heuristic: the export already wrote down what `SongKeys` decided. A file that named the sounding key over
its shapes (`{key: F#m}{capo: 2}` over Em shapes, which `SongKeys` reads as Em) comes back as `{key: Em}{capo: 2}`:
the same reading (Em's scale holds C, G, D, and Em; D minor's, the alternative, holds only C), in the form the chords
are written in.

**Other sheets.** Like the sounding marker, this reads any text, not only a flagged export: a copy of a Campfire PDF
pasted as plain text is the same sheet. So another sheet that writes exactly these words changes too:
`Key: A` / `Capo: 2 (G shapes)` over G shapes, today `{key: A}{capo: 2}` (left to `SongKeys`), becomes
`{key: G}{capo: 2}`, which is what that sheet says. Nothing else changes: a parenthesis that matches no pair
(`Capo: 2 (optional)`, `Capo 2 (play in G)`) and a capo whose fret does not read stay exactly as today, and
`convertFragment` keeps metadata lines as text as before.

Land before plan 12 (both edit `extractHeader`) and before 06 (whose German round trip then also gets the key back);
independent of 02b.

## Tests

- `ChordSheetConverterTest`: change the pinned assertion to
  `convert("Song\nSomeone\nKey: F#m   Capo: 2 (Em shapes)\nVerse 1\nEm    C\nHello world")` containing
  `{key: Em}\n{capo: 2}\n` and `[Em]Hello [C]world`; add a loop over the eight languages' capo labels and markers (as
  `a sounding capo is told in every language the app prints it in` does) asserting `{key: Em}` and `{capo: 2}`, plus
  `Tonalidad: Fa#m   Capo: 2 (formas de Mim)` giving the Latin key as the `Key:` value would be, and
  `Capo: 2 (optional)` still giving no key; `Key: A\nCapo: 2 (G shapes)` over a G-shapes verse giving `{key: G}` (the
  other-sheet change, pinned); `Capo: II (G shapes)` staying a lyric line with no `{capo` and no `{key`; and
  `Kapodaster: 2 (Em-Griffe)` giving `{key: Em}` (case kept). Fails before the fix (`{key: F#m}`).
- `PdfRoundTripTest`: a new test printing `{title: Song}\n{key: F#m}\n{capo: 2}` over
  `[C]Hello [G]world [D]and [Em]more` with `shapesKey = "Em"` in every `values*` folder and 1 to 4 columns, asserting
  `metadata.key == "Em"`, `capo == 2`, and `SongKeys.ofChords(imported) == "Em"`. Fails before the fix (F#m). Update the
  capo test's comment ("which SongKeys may read either way") now that the shapes print brings the shapes' key back.
