# 38 · A song written in German notation has its `B` chords read as B natural: shown as `H`, transposed a semitone off

**Severity:** wrong behaviour (all platforms; every song book written the Hungarian / German way, which is the app's home audience) · **Area:** `:chordpro` — `ChordProNotation`, `ChordProParser`, `ChordProTransposer`, `ChordProTabTransposer` · **Decision:** detect per song — a song with an `H` root anywhere in its chords or `{key}` is German-notated (`B` = B flat, `H` = B natural); everything else is English as today; the editor's transpose writes back in the notation the file is in. No marker directive.

## Symptom
A Hungarian user imports their own song book, written the way chord sheets are written there: `[H7]` for B7 and
`[B]` for B flat.

1. With **German notation** on in Settings, `[B]` is taken for an English B natural and drawn as **`H`**; `[H7]` stays
   `H7`. Every B flat chord of the song now reads as B natural.
2. With the setting off, `[F] [B] [C]` transposed by +2 becomes `G C# D` instead of `G C D`, in the viewer and in the
   editor, and the editor's action writes that into the file.
3. `{key: B}` of such a file is treated as B major (sharps) rather than B flat major (flats), in the song rows, in
   the details header and in the spelling the transposition picks.

## Cause
Both letters are pitch class 11 and nothing knows which notation a file is written in
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:277-286`, as of `29820b93`):

```kotlin
private val noteIndices = mapOf(
    …
    'B' to 11,
    'H' to 11, // German notation.
)
```

`ChordProNotation.noteToGerman` (`ChordProNotation.kt:51-61`) turns every `B` without a flat sign into `H`, and
`ChordProTransposer.prefersFlats` (`:144-153`) looks the key's letter up in the same table. The test
`a song already written in German notation stays as it is` only covers `H`, `Hm7` and `Hb`; the `B` of a German song
is not covered and does not stay as it is. The file says which notation it is in, though: `H` does not exist in the
English one.

## Fix

### The design, and why this one
**The parser normalizes.** A German-notated file is detected once, as it is parsed, and its chord names are rewritten
into the app's own (English) notation before the model leaves `ChordProParser`: `H` → `B`, `B` → `Bb`. Everything
downstream keeps working on one notation and **no call site outside `:chordpro` changes**: `renderSong` transposes
the normalized model and `ChordProNotation.toGerman(song)` then turns it back into `B` / `H` for a reader who asked
for German display, or leaves it `Bb` / `B` for one who did not; `renderKey` — the key in the song rows of the Songs
and Setlists screens, the key in the details header and in `SongDisplayControls` — works from `Song.key`, which
comes from `ChordProParser.summarize`, which normalizes the key the same way; `prefersFlats` sees `{key: B}` of a
German file as `Bb` and answers flats; the tab chord rows and the grids go through the same `rewriteChords` walk;
the highlighter colours brackets and never reads a name. The alternative — a flag carried on `ChordProSong` and
`Song`, with the transposer and the notation both consulting it — touches `data:model`, the song mapper,
`CampfireViewModel.renderSong` / `renderKey` and `ConvertChordProNotationUseCaseImpl`, and needs a `toEnglish`
display step next to `toGerman`. A flag on the model would also break `parse(serialize(parse(x))) == parse(x)` for a
German file (the serialized text is English, so the second parse would not set it), so the model carries none: the
one caller that needs to know, `transposeText`, asks the detection function itself.

**What counts as a chord for detection** is the whole-word test the notation already uses for the same reason,
`ChordProSyntax.chordNameRegex`, with an `H` as the root or as the bass after `/`. Not the transposer's own
recognition, which is "anything in brackets that starts with a note letter" (the closed decision that `[Chorus]`
keeps being transposed): by that rule `[Hello]` or `[Hold]` would flip a whole English song. Annotations
(`[*H7]`), `#` lines and staff lines (`H|--0--|`, the German name of the B string) never count; chords of grids and
of the rows of chord names above a tab do, by the same walk that would rewrite them. A lowercase `h` does not count:
no part of this module reads a lowercase letter as a note (`[am]`, `[h]` are drawn as written and never transposed),
so a file written that way would be left half moved whichever way it was detected.

**The text path** (`transposeText`, the only thing that writes) works per chord name as
`toGerman(transpose(fromGerman(name)))`, so it reads and writes the file's own notation with the same three
functions the viewer's pipeline is made of, and the two cannot disagree. One guard: **when the transposed song
would hold no `H` any more, it is written in the English spelling instead** (`Bb`, which both notations read the
same way). Without the guard `[H7] [E] [A]` + 1 would be written `[C7] [F] [B]` — a file with a `B` and no `H`, which
the next parse (the editor transposes the current text on every tap) reads as English, so − 1 would give `A#` where
the song had `A`. With it the file reads `[C7] [F] [Bb]` and − 1 gives `[B7] [E] [A]`: the music is intact, and only
the letter of the notation is lost, which is the price of there being no marker. Wherever a B natural survives the
move, + n then − n returns the original text.

### Steps
1. `ChordProNotation.kt` — add, all `internal` (nothing outside the module calls them):

   ```kotlin
   /**
    * Whether [name] is a chord only German notation can have written: a real chord name with `H` as its root or as
    * its bass. It is the whole word that is tested and not its first letter, so that a `[Hello]` or a `[Hold]`
    * somebody put in brackets does not decide what every `B` of the song means.
    */
   internal fun isGermanName(name: String) = ChordProSyntax.chordNameRegex.matches(name) &&
       name.split(BASS_NOTE_SEPARATOR, limit = 2).any { it.startsWith(GERMAN_B_NATURAL) }

   /**
    * Whether a song is written in German notation, which it says by using an `H` anywhere: in its key, over its
    * lyrics, in a grid or in a row of chord names above a tab. `H` does not exist in the English notation, so one
    * of them is enough; a German-notated song that never needs one cannot be told from an English song and is read
    * as that. [song] has to be the song as the file spells it, see [ChordProParser.parseAsWritten].
    */
   internal fun isGermanNotated(song: ChordProSong) = ChordProTransposer.writtenChordNames(song).any(::isGermanName)

   /**
    * Reads one chord name of a German-notated file into the notation the rest of the app works in: `H` becomes `B`
    * and `B` becomes `Bb`, bass note included. `Bb` stays what it is, since both notations read it as B flat and a
    * file that mixes them means exactly that, and so does `Hb`. A word that is not a chord is returned as it was.
    */
   internal fun fromGerman(name: String): String {
       if (!ChordProSyntax.chordNameRegex.matches(name)) return name
       return name.split(BASS_NOTE_SEPARATOR, limit = 2).joinToString(BASS_NOTE_SEPARATOR, transform = ::noteFromGerman)
   }

   /** The same for a whole song as the file spells it, the rows of chord names above its tabs included. */
   internal fun fromGerman(song: ChordProSong) = ChordProTransposer.rewriteChords(
       song = song,
       rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines) { name -> fromGerman(name) } },
       rename = { name -> fromGerman(name) },
   )

   /** [song] in the app's own notation, whichever of the two its file is written in. */
   internal fun normalized(song: ChordProSong) = if (isGermanNotated(song)) fromGerman(song) else song

   private fun noteFromGerman(note: String) = when (note.firstOrNull()) {
       'H' -> "B" + note.substring(1) // H -> B, and Hb -> Bb with it.
       // A sign after the letter means the author spelled the note out, and `Bb` is B flat in both notations.
       'B' -> if (note.getOrNull(1) in accidentalSigns) note else "Bb" + note.substring(1)
       else -> note
   }

   private const val GERMAN_B_NATURAL = "H"
   private val accidentalSigns = setOf('#', 'b', '♯', '♭')
   ```

   (and `in accidentalSigns` in `noteFromGerman`). Rewrite
   the class KDoc: its last paragraph ("It is a spelling and nothing else … a song can be read as `H` and still be
   edited and shared as `B`") stays true for `toGerman`; add a paragraph saying that the other direction is how a
   file *written* in German notation comes in, that the parser applies it, and that the only thing written back in
   German is the editor's transposition of such a file. In the KDoc of `toGerman(song)` replace "the transposition
   works in the notation the file is written in" with "the transposition works in the app's own notation, which the
   parser has already brought the song into".

2. `ChordProTransposer.kt` — the read-only twin of `rewriteChords`, next to it:

   ```kotlin
   /**
    * Every name [rewriteChords] would hand to its `rename`, without rewriting anything: the key, the chords over the
    * lyrics, the chords of the grids and the rows of chord names above the tabs. Never an annotation, never a staff.
    */
   internal fun writtenChordNames(song: ChordProSong): Sequence<String> = sequence {
       song.metadata.key?.let { yield(it) }
       song.blocks.asSequence().filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.forEach { line ->
           when (line) {
               is ChordProLine.Lyrics -> yieldAll(line.chords.filter { !it.isAnnotation }.map { it.name })
               is ChordProLine.Grid -> yieldAll(line.tokens.filterIsInstance<GridToken.Chord>().map { it.name })
               is ChordProLine.Tab -> yieldAll(ChordProTabTransposer.chordNames(listOf(line.text)))
               ChordProLine.Blank -> Unit
           }
       }
   }
   ```

   Leave the private `chordNames(song)` that `prefersFlats` counts accidentals with as it is (plan 41 owns it).

3. `ChordProTabTransposer.kt`:
   - `rewriteChordLine` (`:101-121`) calls `rename` word by word and only then finds out that a later word makes
     the line prose (`H is the open string` would hand `H` to the rename before `is` returns the line untouched).
     That is harmless while the result is thrown away, and wrong for a caller that listens. Split the recognition
     from the rewriting, so that `rename` is only ever called for a line that really is a row of chord names:

     ```kotlin
     private class ChordWord(val range: IntRange, val name: String, val isParenthesized: Boolean)

     /**
      * The chord names of a line that holds nothing but chord names and markers, each with where it stands and
      * whether it is written in parentheses, or null for a line with any other word on it: that one is prose.
      */
     private fun chordWords(line: String): List<ChordWord>? {
         val chordWords = mutableListOf<ChordWord>()
         wordRegex.findAll(line).forEach { match ->
             val word = match.value
             val isParenthesized = word.length > 2 && word.startsWith('(') && word.endsWith(')')
             val name = if (isParenthesized) word.substring(1, word.length - 1) else word
             when {
                 ChordProSyntax.chordNameRegex.matches(name) -> chordWords += ChordWord(match.range, name, isParenthesized)
                 isMarker(word) -> Unit
                 else -> return null
             }
         }
         return chordWords
     }
     ```

     and the tail of `rewriteChordLine`, after the bracket branch, becomes

     ```kotlin
     val replacements = chordWords(line)?.map { word ->
         val renamedName = rename(word.name)
         word.range to if (word.isParenthesized) "($renamedName)" else renamedName
     } ?: return line
     return if (replacements.isEmpty()) line else replaceKeepingColumns(line, replacements, filler = ' ')
     ```

   - add the read-only twin of `rewriteChordNames`, so that detection recognises a row of chord names by exactly
     the rule that would rewrite it (every word a chord or a marker; staff lines, directives and prose left out):

     ```kotlin
     /**
      * The chord names [rewriteChordNames] would rewrite in [lines], in order. It listens to the rewrite rather than
      * repeating its rules, which works because a name is only ever handed over once its whole line has been
      * recognised as a row of chord names.
      */
     fun chordNames(lines: List<String>): List<String> = buildList {
         rewriteChordNames(lines) { name -> name.also(::add) }
     }
     ```

   - change `transpose` to take the rename instead of the spelling, because the text of a German-notated file
     renames differently from the model: `fun transpose(lines: List<String>, semitones: Int, rename: (String) -> String)`,
     its body calling `rewriteChordLine(line, rename)`. KDoc: "`rename` is what a chord name written above the staff
     becomes; the frets move by [semitones] whatever it does."

4. `ChordProTransposer.kt`, the two paths:
   - model path — only the tab call changes shape:

     ```kotlin
     private fun transpose(song: ChordProSong, semitones: Int, preferFlats: Boolean): ChordProSong {
         val rename = { name: String -> transposeChord(name, semitones, preferFlats) }
         return rewriteChords(
             song = song,
             rewriteTabLines = { lines -> ChordProTabTransposer.transpose(lines, semitones, rename) },
             rename = rename,
         )
     }
     ```

   - text path — replace `transposeText` (`:106-109`) and turn the private overload (`:111-141`) into a walk that
     takes the rename:

     ```kotlin
     fun transposeText(text: String, semitones: Int, preferFlats: Boolean? = null): String {
         if (semitones == 0 && preferFlats == null) return text
         val written = ChordProParser.parseAsWritten(text)
         val isGermanNotated = ChordProNotation.isGermanNotated(written)
         val song = if (isGermanNotated) ChordProNotation.fromGerman(written) else written
         val flats = preferFlats ?: prefersFlats(song, semitones)
         val transposeName = { name: String -> transposeChord(name, semitones, flats) }
         if (!isGermanNotated) return rewriteText(text, semitones, transposeName)
         // A German-notated file is written back in its own notation, but only while the result still says that it
         // is one. With no B natural left in it there is no `H` either, and a `B` written for the B flat would be
         // read as B natural the next time the file is parsed - so that result is spelled `Bb`, which both
         // notations read the same way.
         val staysGermanNotated = writtenChordNames(song).any { name -> ChordProNotation.isGermanName(ChordProNotation.toGerman(transposeName(name))) }
         return rewriteText(text, semitones) { name ->
             val transposedName = transposeName(ChordProNotation.fromGerman(name))
             if (staysGermanNotated) ChordProNotation.toGerman(transposedName) else transposedName
         }
     }

     private fun rewriteText(text: String, semitones: Int, rename: (String) -> String): String
     ```

     `rewriteText` is today's private `transposeText` with `semitones, preferFlats` replaced by `semitones, rename`
     throughout: `lines.transposeTab(tabLineIndices, semitones, rename)`, `transposeGridLine(rawLine, trimmedLine, rename)`
     (its `transposeChord(word, …)` becomes `rename(word)`), `transposeKeyLine(rawLine, trimmedLine, directive.value, rename)`
     (`rename(key)`), and `rewriteLyricsLineChords(rawLine, rename)` in place of `transposeLyricsLine`, which then has
     no caller left — delete it. Update the KDoc of the public `transposeText`: "… keeping all formatting, and the
     notation the file is written in, see [ChordProNotation.isGermanNotated]."
   - `noteIndices` keeps its `'H' to 11`: `transposeChord` is public and a bracketed word that is not a chord name
     (`[Hello]`) is still moved by the first-letter rule. Change the comment to a KDoc-less statement of that:
     `// Only a word that is not a chord name still gets here with an H: the parser has read the rest into B.`

5. `ChordProParser.kt`:
   - `parse` becomes two functions; the body of today's `parse` moves into the second one unchanged:

     ```kotlin
     /** The song in the notation the app works in, whichever one its file is written in: see [ChordProNotation.isGermanNotated]. */
     fun parse(text: String) = ChordProNotation.normalized(parseAsWritten(text))

     /**
      * The song with every chord spelled the way its file spells it. It is what the notation is detected on and what
      * [ChordProTransposer.transposeText] works from, since that one writes the file back; nothing that draws a song
      * wants it.
      */
     internal fun parseAsWritten(text: String): ChordProSong
     ```

   - `scan` detects while it walks, and normalizes the key. The notation is looked for whatever
     `shouldDetectChords` says, so that `parseMetadata(x) == parse(x).metadata` keeps holding; it costs a character
     search per line, since only a line with an `H` in it can say anything:

     ```kotlin
     private fun scan(text: String, shouldDetectChords: Boolean): ChordProSummary {
         val metadata = MetadataBuilder()
         var hasChords = false
         var isGermanNotated = false
         var environment: String? = null
         ChordProSyntax.splitLines(text).forEach { rawLine ->
             val trimmedLine = rawLine.trim()
             if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
             val directive = if (trimmedLine.startsWith(DIRECTIVE_START)) ChordProSyntax.matchDirective(trimmedLine) else null
             if (directive != null) {
                 …unchanged…
                 return@forEach
             }
             val isLookingForChords = shouldDetectChords && !hasChords
             // Only an `H` can say that a file is German-notated, and most lines have none.
             val isLookingForNotation = !isGermanNotated && GERMAN_LETTER in rawLine
             if (!isLookingForChords && !isLookingForNotation) return@forEach
             val names = writtenChordNames(rawLine, trimmedLine, environment)
             if (isLookingForChords && environment != TAB) hasChords = names.isNotEmpty()
             if (isLookingForNotation) isGermanNotated = names.any(ChordProNotation::isGermanName)
         }
         val declared = metadata.build()
         val isGermanKey = declared.key?.let(ChordProNotation::isGermanName) == true
         return ChordProSummary(
             metadata = if (isGermanNotated || isGermanKey) declared.copy(key = declared.key?.let(ChordProNotation::fromGerman)) else declared,
             hasChords = hasChords,
         )
     }

     /** The names one line of the body hands to a chord rewrite, by the environment it stands in. */
     private fun writtenChordNames(rawLine: String, trimmedLine: String, environment: String?) = when (environment) {
         TAB -> ChordProTabTransposer.chordNames(listOf(rawLine))
         GRID -> ChordProSyntax.parseGridTokens(trimmedLine).filterIsInstance<GridToken.Chord>().map { it.name }
         else -> parseLyrics(rawLine).chords.filter { !it.isAnnotation }.map { it.name }
     }
     ```

     with `private const val GERMAN_LETTER = 'H'`. Update the `@param shouldDetectChords` KDoc: the body is now
     skipped once a chord has been found *and* either the notation is known or the line has no `H`.

6. Do **not** change: `ChordProSerializer` (a normalized model holds no `H`, so its round trip holds as it is);
   `ChordProHighlighter`; `prefersFlats` (it is handed normalized songs); anything in `:domain`, `:data` or
   `:presentation`. The editor's transpose stepper shows `summary.metadata.key`, which is now the normalized key —
   `Bb` for a German `{key: B}` — the same key the lists show; that is what its comment says it is for.

## Tests
`ChordProNotationTest.kt`:
1. `a name written in German notation is read into the app's own` — `fromGerman`: `H`→`B`, `Hm7`→`Bm7`, `H7/A`→`B7/A`,
   `B`→`Bb`, `Bm`→`Bbm`, `B7`→`Bb7`, `Bmaj7`→`Bbmaj7`, `C/H`→`C/B`, `F/B`→`F/Bb`, `B/H`→`Bb/B`, `Hb`→`Bb`, `H#`→`B#`;
   unchanged: `Bb`, `B♭`, `Bbm7`, `B#`, `Am`, `F#m7/C#`.
2. `words that are not chords are not read as German either` — `fromGerman` returns `Hello`, `Hold`, `Bridge`,
   `N.C.`, `""` unchanged.
3. `reading and writing German notation are inverses` — for `H`, `Hm7`, `B`, `Bm`, `B7/H`, `F/B`, `C#m`:
   `toGerman(fromGerman(x)) == x`.
4. `only a real chord with an H in it is a German name` — `isGermanName` true for `H`, `Hm`, `H7`, `Hsus4`, `Hb`,
   `A/H`, `H/F#`; false for `B`, `Bb`, `C/E`, `Hello`, `Hold`, `h`, `hm`, `N.C.`, `""`.
5. `a song says it is German notated with an H anywhere` — `isGermanNotated(ChordProParser.parseAsWritten(x))` true
   for `"[H7]la [E]la"`, `"[Am]la [C/H]la"`, `"{key: Hm}\n[Am]la"`, `"{sog}\n| H . | E . |\n{eog}"`,
   `"{sot}\n  H     E\ne|--0--3--|\n{eot}"`, `"{sot}\nE A D G H E\n{eot}"`; false for `"[B]la [E]la"`,
   `"[Hello]la [B]lo"`, `"[*H7]la [B]lo"`, `"# [H7]\n[B]la"`, `"[h]la [B]lo"`,
   `"{sot}\nHold the last note\nH|--0--|\n{eot}"`.
6. `a German notated song reads as written in German notation and in English without it` —
   `val song = ChordProParser.parse("{key: B}\n\n[B]a [H7]b [Bb]c")`: `song.metadata.key == "Bb"`,
   `song.chordNames() == listOf("Bb", "B7", "Bb")`; `toGerman(song)`: key `"B"`, names `listOf("B", "H7", "B")`.
   The existing cases stay untouched and green; `a song already written in German notation stays as it is` is about
   single names and still holds.

`ChordProParserTest.kt`:
7. `a song written in German notation is parsed into the app's own` —
   `parse("{key: H}\n[H7]a [B]b [F/B]c\n{sog}\n| H . | B . |\n{eog}")`: key `"B"`, lyrics chords `B7`, `Bb`, `F/Bb`,
   grid chords `B`, `Bb`.
8. `a song with no H in it is read as it always was` — `parse("{key: B}\n[B]a [E]b")`: key `"B"`, chords `B`, `E`;
   and `parse("[Hello]a [B]b")` keeps `Hello` and `B`.
9. `the chord names above a German tab keep its columns` —
   `parse("{sot}\n  B     H\ne|--0--3--|\n{eot}")` has tab lines `listOf("  Bb    B", "e|--0--3--|")` (the wider name
   takes one of the spaces after it), and `"{sot}\n  H     B\n…"` gives `"  B     Bb"` (nothing after it, so the line grows).
10. `summarize reads the key of a German notated song into the app's notation` — `summarize("{key: H}\n[E]la").metadata.key == "B"`;
    `summarize("{key: B}\n\n[E]a [H7]b").metadata.key == "Bb"`; `summarize("[E]a [H7]b\n{key: B}").metadata.key == "Bb"`
    (the key after the chords); `summarize("{key: B}\n[E]a").metadata.key == "B"`;
    `summarize("{key: B}\n[Hello]a").metadata.key == "B"` with `hasChords == true`; and for
    `text = "{key: B}\n{sot}\n  H\ne|--0--|\n{eot}"`: key `"Bb"`, `hasChords == false`.
11. extend `parseMetadata matches the metadata of a full parse` with a second text, `"{key: B}\n[H7]a"`.

`ChordProTransposerTest.kt`:
12. `a German notated song is transposed in its own notation and comes back as it was` —
    `text = "{key: Dm}\n[Dm]a [B]b [C/H]c [A7]d"`; `up = transposeText(text, 1)` is
    `"{key: Ebm}\n[Ebm]a [H]b [Db/C]c [B7]d"`; `transposeText(up, -1) == text`.
13. `the key of a German notated song is read the German way` — `transposeText("{key: B}\n[B]a [F/A]b [H7]c", 2)` is
    `"{key: C}\n[C]a [G/H]b [C#7]c"` (today: `{key: Db}`, `Db`, `G/B`, `Db7`).
14. `a German notated song that is left with no H spells its B flat out` —
    `transposeText("[H7]a [E]b [A]c", 1, preferFlats = true)` is `"[C7]a [F]b [Bb]c"`, and transposing that by −1
    with `preferFlats = true` gives `"[B7]a [E]b [A]c"`; `transposeText("{key: H}\n[H]a [E]b", -1)` is
    `"{key: Bb}\n[Bb]a [Eb]b"`.
15. `the chord names above a German tab are transposed in its notation` —
    `transposeText("{sot}\n  H     B\ne|--0--3--|\n{eot}", 1, preferFlats = true)` is `"{sot}\n  C     H\ne|--1--4--|\n{eot}"`.
16. `the model of a German notated song is transposed from what it means` —
    `transpose(parse("[H7]a [B]b"), 2).chordNames() == listOf("Db7", "C")`.
17. `an English song is transposed as it always was` — `transposeText("[B]a [E]b", 1, preferFlats = false) == "[C]a [F]b"`.
    `german notation is understood` stays (`transposeChord("H", 1)` is still `C`).

## Verify
1. `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest`.
2. `./gradlew :app:desktop:run`; write a song `{key: B}` / `[B]Boci [F]boci [H7]tarka`. With German notation **off**
   the viewer shows `Bb F B7` and the row in the Songs list shows the key `Bb`; **on**, `B F H7` and `B`.
3. Transpose + 2 in the viewer: `C G C#7` with the key `C`, whichever way the setting is (today: `Db G Db7`).
4. In the editor, tap transpose up once, then down once: the text is `[B] [F] [H7]` again, byte for byte.
5. A song `[H7] [E] [A]`, editor up once: `[C7] [F] [A#]` or `[Bb]`, never a bare `[B]`; down once: `[B7] [E] [A]`.
6. An English song with `[B]` and a `[Hello]` behaves exactly as before.
7. The compile checks for the other three targets (the change is common code only).

## Docs
- `chordpro/CLAUDE.md`: in the `ChordProParser` bullet add that `parse` and `summarize` hand out the app's own
  notation, reading a file that uses an `H` anywhere as German-notated (`parseAsWritten` is the song as spelled).
  `ChordProTransposer` bullet: replace "understands German `H`" with "and writes a German-notated file back in its
  own notation — unless the result would hold no `H`, in which case its B flats are spelled `Bb`, so that the file
  is never one the next parse misreads". `ChordProNotation` bullet: it is now both directions; replace "It runs
  after the transposition, never before, because the transposition works in the notation the file is written in.
  Nothing that goes back to disk passes through it: … has no counterpart here on purpose." with: `toGerman` runs
  after the transposition, on the way to the screen, as the reader's preference; `fromGerman` runs in the parser, as
  what the file says about itself; and the one thing that goes back to disk through both is `transposeText` on a
  German-notated file.
- `domain/api/CLAUDE.md`, `ConvertChordProNotationUseCase`: "a file is always written in the app's own notation"
  becomes "a file is written in the notation it is already in, which the text transposition works out for itself".
- `documentation/file-format.md`, a new bullet under "Campfire understands the core of the format": chord names are
  read in English notation unless the song uses an `H` anywhere — root or bass of a chord, in a grid, in a row of
  chord names above a tab, or in `{key}` — in which case it is German-notated: `H` is B natural, `B` is B flat
  (`Bb` and `Hb` too), and the editor's transposition writes it back that way. State the two known limits: a
  German-notated song that uses `B` but never `H` cannot be told from an English one and is read as English (write
  the B flat as `Bb`, which both notations read alike); and a transposition that leaves no B natural in the song is
  written with `Bb` for the same reason. And: only capital letters are note names, so `[h]` or `[am]` is drawn as
  written and never transposed.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/CLAUDE.md`
- `domain/api/CLAUDE.md`
- `documentation/file-format.md`

## Depends on
37 (same source files, and its fix is what makes `parse` and `scan` agree on which lines are a grid or a tab).
