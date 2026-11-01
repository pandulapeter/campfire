# Read back the PDF's playing details row in every language the app prints it in

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — a colon-labelled line alone (`metadata(text)`, the first 15 lines) takes any value today
(probe: `Time: something odd` became `{time: something odd}`), so the new time and tempo words would turn a lyric such as
`Metro: …` into metadata; Fix 1 now gives them the value check the foreign key labels already have, and the tests cover it.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`
(read-only: `presentation/src/commonMain/composeResources/values*/strings.xml`)

Found while verifying plan 01; it is older than the reviewed range (the labels below predate aca41acce), but plan 01's
round-trip test runs through it, so it lands first, in lane P.

## Problem

The PDF heads each song with one row of values three spaces apart (`PrintLayout.playingDetails`), each label in the
app's language: `song_editor_insert_key`, `song_details_transposition`, `song_editor_insert_capo`,
`song_editor_insert_tempo`, and `print_time`, joined by `print_label_value` (`%1$s: %2$s`, French `%1$s : %2$s`). The
importer reads that row only when it knows every label in it (`labelledRow`: `entries.size == parts.size`), and its
tables hold English, Hungarian, and some site words only (`ChordSheetConverter.kt`, bottom of the object):

```kotlin
private val keyLabels = setOf(
    "key", "key of", "hangnem", "tom", "tono", "tonalidad", "tonart", "tonalité", "tonalità", "tonacja", "toonsoort",
)
private val capoLabels = setOf("capo", "capotraste", "cejilla", "kapodaster", "capodastre", "kapo")
private val metadata = Regex(
    "^(Capo|Capotraste|Cejilla|Kapodaster|Capodastre|Kapo|Key|Hangnem|Tom|Tono|Tonalidad|Tonart|Tonalité|Tonalità|Tonacja|Toonsoort|" +
        "Tempo|Tempó|BPM|Time|Ütemmutató|Artist|Előadó|Song|Title|Album|Year|Composer|Lyricist|words (?:and|&) music by|by)" +
        "\\s*[:;]\\s*(.+)$",
    RegexOption.IGNORE_CASE,
)
private val transposition = Regex("^(Transposition|Transzponálás)\\s*:\\s*([+-]?[0-9]+)$", RegexOption.IGNORE_CASE)
```

Missing, from the app's own `strings.xml`:

| | key | capo | transposition | tempo | time |
|---|---|---|---|---|---|
| ro | Tonalitate | Capodastru | Transpunere | Tempo | Măsură |
| it | (Tonalità ok) | Capotasto | Trasposizione | Tempo | Metro |
| he | טונליות | קאפו | טרנספוזיציה | טמפו | משקל |
| es | ok | ok | Transposición | ok | Compás |
| de | ok | ok | ok | ok | Taktart |
| fr | ok | ok | ok | ok | Mesure |

A probe test at 286971fe4 (converter on the PDF's row as text, `Song\nSomeone\n<row>\nVerse 1\nEm C\nHello world…`)
gave `{key: F#m}{capo: 2}` for the English and French rows, but kept these as a line of text, with no `{key}` and no
`{capo}`:

```
Tonalitate: F#m   Capodastru: 2 (poziții de Em)
Tonalità: F#m   Capotasto: 2 (diteggiature di Em)
טונליות: F#m   קאפו: 2 (צורות Em)
Tonalidad: F#m   Capo: 2 (formas de Em)   Compás: 3/4
```

So someone using the app in Romanian, Italian, or Hebrew who prints a capo song and imports the PDF elsewhere (or
shares it with a Campfire user) gets the shapes with no capo: the song sounds two semitones low. In Spanish, German, and
French any song with a time signature (in Spanish also one with a transposition) loses its key, capo, tempo, and time the same way, and the
row stays in the song as a stray lyric. The section labels already work in every language (the convert `CLAUDE.md`:
"An export's label opens the kind its leading heading word names, in every language the app prints labels in"); the
playing details row was never brought along.

## Fix

1. Add every app language's labels from the table above to the label tables, lowercase in the sets and as written
   (Unicode escapes for non-ASCII, as the file does) in the regexes: `keyLabels` + `metadata` + `unlabelledMetadata`
   for key (`tonalitate`, `טונליות`), `capoLabels` + both regexes for capo (`capodastru`, `capotasto`, `קאפו`),
   `transposition` for transposition (`Transpunere|Transposición|Trasposizione|טרנספוזיציה`), and the label
   `when` in `metadata(text)` that maps `"tempó", "bpm" -> "tempo"` and `"ütemmutató" -> "time"`
   for tempo (`טמפו`) and time (`măsură`, `taktart`, `compás`, `mesure`, `metro`, `משקל`).
   - Keep the stricter rules that guard lyrics: a label without a colon (`unlabelledMetadata`) only for words that
     cannot start a sung line. Add the new key and capo words there, but **not** `Metro`, `Mesure`, `Compás`, or
     `Măsură` (all ordinary words; "Metro" opens lyrics). The colon-labelled row (`labelledRow`, `metadata`) is where
     the new time and tempo words belong.
   - **Give the new time and tempo words a value check in `metadata(text)` too.** `labelledRow` checks a row's values
     (`"time" -> timeSignature.matches(value)`), but a colon-labelled line on its own goes through `metadata(text)`,
     whose colon branch takes any value: `classify` reads it as metadata in the first 15 lines
     (`index < 15 && metadata(line.text)?.second?.isNotBlank()`), so at 286971fe4 `Time: something odd` becomes
     `{time: something odd}` (challenge probe). For `Time` that is old behaviour and stays; for the new words it would turn
     `Metro: …`, `Mesure : …`, or `Compás: …` at the top of a sheet into a directive. Do what the `"key"` branch already
     does for the foreign key labels: in the value `when`, `"time" -> value.takeIf { label in setOf("time", "\u00fctemmutat\u00f3") || timeSignature.matches(value) }.orEmpty()`,
     and for `tempo` keep the number and return blank where a new label (`טמפו`) has none. A blank value leaves the
     line as text (see `metadata`'s KDoc), and `labelledRow` already refuses a row holding one.
   - Hebrew has no case, so `IGNORE_CASE` changes nothing for it; the words are matched as the PDF's text extractor
     returns them (logical order, see the convert `CLAUDE.md` on right-to-left rows).
2. No new braces in any regex. If one is needed, escape it as `\\{`/`\\}` outside a count, since
   `.github/scripts/check_regexes.py` (pre-commit) refuses a brace only the JVM accepts.
3. In the convert `CLAUDE.md`, next to the line on section labels, say that the playing details row
   (key, transposition, capo, tempo, time) is read in every language the app prints it in, from the same labels as
   `strings.xml`.

## Tests

- `ChordSheetConverterTest`: one test over the eight languages' rows as the PDF prints them, each
  `"<key>: F#m   <transposition>: +2   <capo>: 2   <tempo>: 96 BPM   <time>: 3/4"` (French with `" : "`), asserting
  `{key: F#m}`, `{capo: 2}`, `{tempo: 96}`, `{time: 3/4}` in the header and the row gone from the body. Hard-code the
  labels in this test (`:chordpro` cannot read `:presentation`'s resources) and keep negative cases: `Metro dopo
  metro` and `Mesure par mesure` as a lyric stay lyrics, and so do `Metro: dopo metro` and `Mesure : par mesure` as
  the second line of a song (inside the first 15 lines, where a colon label alone is read as metadata), while
  `Măsură: 3/4` alone there is read as `{time: 3/4}`.
- `PdfRoundTripTest` (`presentation/src/desktopTest`, the real PDF writer and text extractor): print one capo song
  with key, transposition, capo, tempo, and time with each language's `PrintLabels` built from that language's
  `strings.xml` (read with `DocumentBuilderFactory`, as `PluralItemsTest` reads `src/commonMain/composeResources`),
  and assert the imported song's key, capo, tempo, and time. This is what keeps the converter's tables in step with a
  later translation change. If the Hebrew row does not come back because of its right-to-left extraction order, keep
  the Hebrew case in `ChordSheetConverterTest` (text as the extractor returns it) and say so in the lane report;
  do not change the extractor in this plan.
