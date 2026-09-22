# 22 · Grid lines: the left-margin label is transposed as a chord, and only the first chord of a `C~G` cell moves

**Severity:** wrong behaviour (all platforms. Any grid written the way the spec's own example is — margin labels, `~` cells, `:|:` and voltas; the editor's Transpose writes the damage into the file) · **Area:** `:chordpro` (`ChordProSyntax.parseGridTokens` / `isBar`, `ChordProTransposer`, `ChordProParser.scan`)

## Symptom
The spec's grid example:
```
{start_of_grid}
A    || G7 . | % . | %% . | . . |
     |: C7 . | %  . :|: G7 . | % . :| repeat 4 times
Coda | D7 . | Eb7 | C~A | G7 . | % . |.
{end_of_grid}
```
Transpose +2:
- the viewer draws the margin labels in chord colour as `B` and `Doda`; the editor's Transpose writes `B    ||` and
  `Doda |` into the file;
- the cell `C~A` becomes `D~A`: its second chord does not move;
- `:|:`, voltas (`|1`, `:|2`, `:|2>`) and the `/` beat mark are drawn in chord colour.

## Cause
`parseGridTokens` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:326-340`)
makes only the words *after the last bar* text; every word before the first bar becomes `GridToken.Chord`, and
`isBar` knows five spellings. `transposeGridLine` (`ChordProTransposer.kt:324-340`) repeats that classification on
its own. A rename goes through `transposeNote` (`:299-305`), which rewrites the first letter of any word whose first
letter is a note, so `Coda` becomes `Doda` and `C~A` becomes `D~A`.

The spec (chordpro.org, grid directives): "Everything before the first bar line will be put in the left margin, and
everything following the last bar symbol will be put in the right margin"; bars are `|`, `||`, `|.`, `|:`, `:|`,
`:|:`, `|1`, `:|2` and `:|2>`; "Multiple chords can be put in a single cell by separating the chord names with a
`~`"; `/` marks a chord position.

## Fix
1. `ChordProSyntax.kt`, replace `parseGridTokens` and `isBar` (`:325-340`):

   ```kotlin
   /**
    * Splits a grid line into tokens. ChordPro puts whatever comes before the first bar line in the left margin and
    * whatever follows the last one in the right margin, so on a line that has a bar both are text: a margin label
    * such as `A` or `Coda` names a part of the song, and taking it for a chord would transpose it. A `/` marks where a
    * chord is played and is not one either.
    */
   fun parseGridTokens(trimmedLine: String): List<GridToken> {
       val words = trimmedLine.split(whitespaceRegex).filter { it.isNotEmpty() }
       val firstBarIndex = words.indexOfFirst { isBar(it) }
       val lastBarIndex = words.indexOfLast { isBar(it) }
       return words.mapIndexed { index, word ->
           when {
               firstBarIndex >= 0 && (index < firstBarIndex || index > lastBarIndex) -> GridToken.Text(word)
               isBar(word) -> GridToken.Bar(word)
               word == "." -> GridToken.Beat
               word == "%" || word == "%%" -> GridToken.Repeat(word)
               word == "/" -> GridToken.Text(word)
               else -> GridToken.Chord(word)
           }
       }
   }

   /** The bar lines of a grid, the repeats and the voltas (`|1`, `:|2`, `:|2>`) included. */
   fun isBar(word: String) = word in barLines || voltaRegex.matches(word)

   private val barLines = setOf("|", "||", "|.", "|:", ":|", ":|:")
   private val voltaRegex = Regex(":?\\|\\d+>?")

   /**
    * The chords of one grid cell: ChordPro writes several chords into a cell by joining them with a `~`, and each
    * of them is a chord of its own to transpose or respell.
    */
   fun cellChords(cell: String) = cell.split(GRID_CELL_CHORD_SEPARATOR)

   const val GRID_CELL_CHORD_SEPARATOR = "~"
   ```

   (Keep the declaration order of the file: the two private vals may go next to `whitespaceRegex` at the top.)

2. `ChordProTransposer.kt`:
   - `rewriteLine` (`:290-294`), the grid branch:
     ```kotlin
     if (token is GridToken.Chord) {
         GridToken.Chord(ChordProSyntax.cellChords(token.name).joinToString(ChordProSyntax.GRID_CELL_CHORD_SEPARATOR, transform = rename))
     } else {
         token
     }
     ```
   - `writtenChordNames` (`:74`): `is ChordProLine.Grid -> yieldAll(line.tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProSyntax.cellChords(it.name) })`.
   - `transposeGridLine` (`:324-340`): classify with the parser's own rule instead of its own copy of it. The words
     of `tokenRegex` (`\S+`) and of `parseGridTokens` (split on `\s+`, empties dropped) are the same words in the same
     order:
     ```kotlin
     private fun transposeGridLine(rawLine: String, trimmedLine: String, rename: (String) -> String): String {
         val matches = tokenRegex.findAll(trimmedLine).toList()
         val tokens = ChordProSyntax.parseGridTokens(trimmedLine)
         val body = buildString {
             var consumedUntil = 0
             matches.forEachIndexed { index, match ->
                 append(trimmedLine, consumedUntil, match.range.first)
                 append(
                     if (tokens[index] is GridToken.Chord) {
                         ChordProSyntax.cellChords(match.value).joinToString(ChordProSyntax.GRID_CELL_CHORD_SEPARATOR, transform = rename)
                     } else {
                         match.value
                     }
                 )
                 consumedUntil = match.range.last + 1
             }
             append(trimmedLine, consumedUntil, trimmedLine.length)
         }
         return rawLine.replaceTrimmedPart(trimmedLine, body)
     }
     ```
     Then delete the now unused `BEAT`, `REPEAT` and `DOUBLE_REPEAT` constants (grep first).

3. `ChordProParser.kt`, `writtenChordNames` (`:111`): `GRID -> ChordProSyntax.parseGridTokens(trimmedLine).filterIsInstance<GridToken.Chord>().flatMap { ChordProSyntax.cellChords(it.name) }`.

4. `model/ChordProLine.kt`, the `GridToken` comments: `Bar` — `// "|", "||", "|.", "|:", ":|", ":|:", voltas such as "|1" and ":|2>"`;
   `Chord` — `// "Am", or "C~G" for several chords in one cell`; `Text` — `// a margin label before the first bar,
   a comment after the last one, or a "/" chord position`.

`ChordProNotation` needs nothing: it goes through `rewriteChords` / `writtenChordNames`. The viewer
(`SongLyrics.kt:659-678`) draws `Text` in the lyrics colour and keeps token order, so a margin label stays at the
start of its row.

## Tests
`ChordProParserTest.kt`:
- new `the words before the first bar of a grid line are its margin label`: `A    || G7 . |` →
  `[Text("A"), Bar("||"), Chord("G7"), Beat, Bar("|")]`; `Coda | D7 |.` → `[Text("Coda"), Bar("|"), Chord("D7"), Bar("|.")]`.
- new `repeats voltas and chord positions are not chords`: `|: C7 / :|: G7 . :|2> D |` →
  `[Bar("|:"), Chord("C7"), Text("/"), Bar(":|:"), Chord("G7"), Beat, Bar(":|2>"), Chord("D"), Bar("|")]`, and
  `|1 C :|2 D |` starts with `Bar("|1")` and has `Bar(":|2")`.
- `Am C` (no bar at all) is still `[Chord("Am"), Chord("C")]`; the existing `text after the last bar…` test stands.

`ChordProTransposerTest.kt`, new `a grid keeps its margin labels and moves every chord of a cell`:
```kotlin
val text = "{start_of_grid}\nA    || G7 . | C~A . |\nCoda | D7 |.\n{end_of_grid}"
assertEquals(
    "{start_of_grid}\nA    || A7 . | D~B . |\nCoda | E7 |.\n{end_of_grid}",
    ChordProTransposer.transposeText(text, 2, preferFlats = false),
)
```
and on the model: `transpose(parse(text), 2, preferFlats = false)` has `Chord("D~B")` and `Text("Coda")`.

`ChordProNotationTest.kt`: `toGerman` of a song with the grid cell `B~Bb` gives `H~B`, and a song whose only `H` is
in a cell `C~H` is detected as German (`isGermanNotated(parseAsWritten("{sog}\n| C~H |\n{eog}"))`).

## Verify
Paste the spec example above into a song: the margin labels are drawn in the lyrics colour, `:|:` and the voltas in
the bar colour; Transpose +2 in the viewer and in the editor moves `G7`→`A7` and `C~A`→`D~B` and leaves `A` and
`Coda`. `./gradlew :chordpro:desktopTest`.

## Docs
`chordpro/CLAUDE.md`, the `model/` bullet, after "`{start_of_tab}` and `{start_of_grid}` switch the kind of
`ChordProLine` that is read until they close": add a sentence at the end of the bullet: "A grid line keeps what
comes before its first bar and after its last one as text, the margins ChordPro puts labels and comments in, and
a cell may hold several chords joined with `~`, each transposed on its own."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProLine.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/CLAUDE.md`

## Depends on
None. 19, 21, 24 edit the same files; run them one after another.
