# 40 · `B7(b9)`, `B6/9`, `Bm(maj7)`, `Bmi7`, `B-`, `B7alt` and `(B)` stay `B` in German notation, and one such chord keeps a whole chord row above a tab from being transposed

**Severity:** wrong behaviour, minor (all platforms; any song using one of those spellings, with German notation on or with a tab under the chords) — plus a crash on a crafted name (JVM targets) · **Area:** `:chordpro` — `ChordProSyntax.chordNameRegex`, `ChordProNotation`, `ChordProTabTransposer.rewriteChordLine`, `ChordProTransposer.transposeChord`

## Symptom
1. German notation on. A song has `[B7]`, `[B7(b9)]`, `[B6/9]`, `[Bm(maj7)]`, `[Bmi7]`, `[B-]`, `[B7alt]` and `[(B)]`. The
   first is drawn `H7`; the other seven stay `B…`, which in that notation means B flat, so the reader plays every
   one of them a semitone low. After plan 38 the same gap shows from the other side: those seven chords of a
   German-notated file are not read into `Bb…`, and an `[H7(b9)]` does not count as the `H` that says the file is
   German-notated.
2. A tab with a row of chord names above the staff, one of which is `C6/9` or `G7(b9)`: transposed by +2, the frets
   move and the chord names do not — the whole row is taken for prose because of that one word.
3. `[(Am)]`, the usual way of writing an optional chord, is never transposed at all, in the viewer or the editor.
4. A crafted name — `[C` followed by five thousand `b9` and `]` — crashes the desktop and Android builds with a
   `StackOverflowError` where the name is tested: opening the song with German notation on, or transposing a tab
   that has it in a chord row (measured on a desktop main thread: 1000 repetitions pass, 5000 overflow; a coroutine
   worker has a smaller stack). An `Error` is not caught by any `catch (exception: Exception)` on the way.

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:29-31` (as of `29820b93`):

```kotlin
val chordNameRegex = Regex(
    "[A-H][#b♯♭]?(?:maj|min|dim|aug|sus|add|m|M|\\+|°|ø)?[0-9]*(?:(?:maj|min|dim|aug|sus|add|[#b♯♭])[0-9]*)*(?:/[A-H][#b♯♭]?)?"
)
```

No parentheses, no `/` followed by a number, no `-`, `mi`, `alt`, `Maj`, `Δ`. It is the whole-word test of
`ChordProNotation.toGerman` (`ChordProNotation.kt:36`) and of the chord rows of a tab
(`ChordProTabTransposer.kt:111`), and after plan 38 of `fromGerman`, `isGermanName` and `chordWords` as well; a name
it does not know is returned as it was, and a row with one such word in it is returned whole. The `(?:…)*` group with
an alternation inside is matched recursively by `java.util.regex`, one frame per repetition, which is the overflow.

Two neighbours of the regex have the same blind spots and have to move with it, or the new spellings would be
recognised and then rewritten wrongly: `toGerman` (and 38's `fromGerman` / `isGermanName`) and
`ChordProTransposer.transposeChord` (`ChordProTransposer.kt:23-25`) split a name at its *first* `/`
(`name.split(BASS_NOTE_SEPARATOR, limit = 2)`), so the bass of `A6/9/B` is never reached; and both look for the root
in the first character, so `(Am)` has none. The tab rows strip the parentheses themselves
(`ChordProTabTransposer.kt:107-108`), which is why `(Am)` works there and nowhere else.

## Fix
Runs after 38 and 39 and is written against their result. The regex is replaced by a walk over the name, for the
reason plan 39 gives for the other two — it costs the length of the word whatever the word is, and it cannot
overflow a stack — and because the grammar below is easier to read, and to test, as code. A Java port of the walk
was run against the old regex over five million random names: everything the regex accepted is still accepted.

The grammar, tokens taken longest first:

```
word       := '(' chord ')' | chord                      an optional chord is written in parentheses
chord      := note quality? number? tail* bass?
note       := [A-H] [#b♯♭]?
quality    := maj | Maj | min | mi | dim | aug | sus | add | m | M | + | - | ° | ø | Δ | ∆
number     := digits 'alt'?                              B7alt, but not Halt
tail       := alteration | '(' item ((',' ' '*)? item)* ')' | '/' digits
alteration := (maj | Maj | min | mi | dim | aug | sus | add | M | # | b | ♯ | ♭) digits? | '+' digits? | '-' digits
item       := digits | alteration | (no | omit) digits   C(9), C(add9), G7(#9, b13), C5(no3)
bass       := '/' note                                   and nothing after it
```

`-` needs its digits in a tail (`C7-9`) and `alt`, `no` and `omit` need theirs, because without them `E-----`,
`Halt`, `Eno` and `Ano` would be chords; `Bridge`, `Break`, `Bass`, `B-side`, `A-ha`, `Chorus` and `Coda` stay prose.
What is newly a chord and also a word is `Ami`, `Emi`, `Dmi` (through `mi`), next to the `A`, `Am`, `Emin` and `Hadd`
that always were; it only matters for a line of a tab environment made of nothing but such words.

1. New file `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt` (MPL header
   copied from a sibling):

   ```kotlin
   package com.pandulapeter.campfire.chordpro

   /**
    * What a chord name is made of, for the three places that have to know more about one than "it is written in
    * brackets": [ChordProNotation], which must not turn prose starting with a `B` into prose starting with an `H`,
    * the rows of chord names above a tab, which are told from the notes around them by every word being one, and
    * [ChordProTransposer], which has to find the two notes of a name to move them.
    *
    * It is a walk over the word and not a regular expression: a name comes out of a file, so it can be as long as
    * one, and a repeated group of alternatives is matched by recursion on the JVM, a frame for every repetition.
    */
   internal object ChordProChordNames {

       /**
        * Whether a whole word is a chord name: a note (German `H` included), an optional accidental, a quality, a
        * number, any alterations — written on, in parentheses or after a slash, as in `7b9`, `7(b9)` and `6/9` — and
        * a bass note, with or without a pair of parentheses around all of it, which marks a chord as optional.
        *
        * It is the answer to "is this a chord and not a word that happens to start with a letter". Where a token
        * is also the start of an ordinary word it has to be followed by its number (`7alt`, `no3`, `-9`), which is
        * what keeps `Halt`, `Ano` and the staff line `E-----` out. The transposer does not ask: there the brackets
        * or the grid have already said that a chord is what this is.
        */
       fun isChordName(word: String): Boolean {
           val name = unwrapped(word)
           var index = noteEnd(name, 0)
           if (index == NOT_FOUND) return false
           index = wordEnd(name, index, qualities).takeIf { it != NOT_FOUND } ?: index
           val numberEnd = digitsEnd(name, index)
           if (numberEnd > index) index = if (name.startsWith(ALTERED, numberEnd)) numberEnd + ALTERED.length else numberEnd
           while (index < name.length) {
               index = when (name[index]) {
                   GROUP_OPEN -> groupEnd(name, index + 1)
                   BASS_SEPARATOR -> {
                       val sixNineEnd = digitsEnd(name, index + 1)
                       // A bass note is the last thing a name says, so whatever follows this slash has to end it.
                       if (sixNineEnd == index + 1) return noteEnd(name, index + 1) == name.length
                       sixNineEnd
                   }

                   else -> alterationEnd(name, index)
               }
               if (index == NOT_FOUND) return false
           }
           return true
       }

       /**
        * The notes of a chord name, each with everything that is written after it: the root and, where the name has
        * one, the bass. The bass is what follows the last `/` and starts with a note letter, so the slash of a `6/9`
        * belongs to the root. Parentheses around the whole name are not part of either.
        */
       fun notes(name: String): List<String> {
           val chord = unwrapped(name)
           val separatorIndex = chord.lastIndexOf(BASS_SEPARATOR)
           val hasBass = separatorIndex >= 0 && noteEnd(chord, separatorIndex + 1) != NOT_FOUND
           return if (hasBass) listOf(chord.substring(0, separatorIndex), chord.substring(separatorIndex + 1)) else listOf(chord)
       }

       /** Applies [rewrite] to each of the [notes] of a name and puts it back together, parentheses included. */
       fun rewriteNotes(name: String, rewrite: (String) -> String): String {
           val rewritten = notes(name).joinToString(BASS_SEPARATOR.toString(), transform = rewrite)
           return if (isParenthesized(name)) "$GROUP_OPEN$rewritten$GROUP_CLOSE" else rewritten
       }

       private fun isParenthesized(name: String) = name.length > 2 && name.first() == GROUP_OPEN && name.last() == GROUP_CLOSE

       private fun unwrapped(name: String) = if (isParenthesized(name)) name.substring(1, name.length - 1) else name

       private fun noteEnd(name: String, index: Int): Int {
           val letter = name.getOrNull(index) ?: return NOT_FOUND
           if (letter !in FIRST_NOTE_LETTER..LAST_NOTE_LETTER) return NOT_FOUND
           val sign = name.getOrNull(index + 1)
           return if (sign != null && sign in ACCIDENTAL_SIGNS) index + 2 else index + 1
       }

       private fun digitsEnd(name: String, index: Int): Int {
           var end = index
           while (end < name.length && name[end] in '0'..'9') end++
           return end
       }

       private fun wordEnd(name: String, index: Int, words: List<String>) =
           words.firstOrNull { name.startsWith(it, index) }?.let { index + it.length } ?: NOT_FOUND

       private fun alterationEnd(name: String, index: Int): Int {
           val wordEnd = wordEnd(name, index, alterations)
           if (wordEnd != NOT_FOUND) return digitsEnd(name, wordEnd)
           return when (name.getOrNull(index)) {
               AUGMENTED -> digitsEnd(name, index + 1)
               // A dash with no number after it is a staff line or a hyphenated word, not a flattened fifth.
               DIMINISHED -> digitsEnd(name, index + 1).takeIf { it > index + 1 } ?: NOT_FOUND
               else -> NOT_FOUND
           }
       }

       private fun groupItemEnd(name: String, index: Int): Int {
           val numberEnd = digitsEnd(name, index)
           if (numberEnd > index) return numberEnd
           val omissionEnd = wordEnd(name, index, omissions)
           if (omissionEnd == NOT_FOUND) return alterationEnd(name, index)
           return digitsEnd(name, omissionEnd).takeIf { it > omissionEnd } ?: NOT_FOUND
       }

       /** Where the group opened just before [index] ends, its closing parenthesis included. */
       private fun groupEnd(name: String, index: Int): Int {
           var end = groupItemEnd(name, index)
           while (end != NOT_FOUND && end < name.length && name[end] != GROUP_CLOSE) {
               if (name[end] == ITEM_SEPARATOR) {
                   end++
                   while (end < name.length && name[end] == ' ') end++
               }
               end = groupItemEnd(name, end)
           }
           return if (end != NOT_FOUND && end < name.length) end + 1 else NOT_FOUND
       }

       private const val NOT_FOUND = -1
       private const val FIRST_NOTE_LETTER = 'A'
       private const val LAST_NOTE_LETTER = 'H'
       private const val ACCIDENTAL_SIGNS = "#b♯♭"
       private const val ALTERED = "alt"
       private const val AUGMENTED = '+'
       private const val DIMINISHED = '-'
       private const val GROUP_OPEN = '('
       private const val GROUP_CLOSE = ')'
       private const val ITEM_SEPARATOR = ','
       private const val BASS_SEPARATOR = '/'

       // Longest first wherever one is the beginning of another: the first that fits is the one that is taken.
       private val qualities = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "m", "M", "+", "-", "°", "ø", "Δ", "∆")
       private val alterations = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "M", "#", "b", "♯", "♭")
       private val omissions = listOf("no", "omit")
   }
   ```

   The walk never goes back: every token is taken whole or the word is refused, and no token is the beginning of a
   longer spelling of two others, so nothing the old regex reached by backtracking is lost (the fuzz above).

2. `ChordProSyntax.kt`: delete `chordNameRegex` and its KDoc (`:23-31`); in the object's KDoc nothing changes.

3. `ChordProNotation.kt` (as 38 leaves it): the three whole-word tests and the three splits go through the new object,

   ```kotlin
   fun toGerman(name: String) = if (ChordProChordNames.isChordName(name)) ChordProChordNames.rewriteNotes(name, ::noteToGerman) else name

   internal fun fromGerman(name: String) = if (ChordProChordNames.isChordName(name)) ChordProChordNames.rewriteNotes(name, ::noteFromGerman) else name

   internal fun isGermanName(name: String) = ChordProChordNames.isChordName(name) &&
       ChordProChordNames.notes(name).any { it.startsWith(GERMAN_B_NATURAL) }
   ```

   keeping their KDoc, and `private const val BASS_NOTE_SEPARATOR` goes, since nothing uses it any more.
   `noteToGerman` and `noteFromGerman` need no change: they read the letter and the sign and copy the rest, which is
   now `7(b9)` or `6/9` as readily as `m7`.

4. `ChordProTransposer.transposeChord` (`ChordProTransposer.kt:22-25`):

   ```kotlin
   /**
    * Transposes a single chord name, the bass after its `/` included and the parentheses of an optional chord kept;
    * returns the input unchanged if it is not a chord (e.g. "N.C.").
    */
   fun transposeChord(name: String, semitones: Int, preferFlats: Boolean) =
       ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
   ```

   It still does **not** ask `isChordName` — that a bracketed word starting with a note letter is moved is a closed
   decision — so the only names that come out differently are the ones in parentheses and the ones with a second
   slash. `BASS_NOTE_SEPARATOR` stays in the transposer for `prefersFlats` (plan 41 owns that function).

5. `ChordProTabTransposer.kt`, `chordWords` (38's split of `rewriteChordLine`): the parentheses are the name's own
   business now, so the class that carried them goes.

   ```kotlin
   /**
    * The chord names of a line that holds nothing but chord names and markers, or null for a line with any other
    * word on it: that one is prose.
    */
   private fun chordWords(line: String): List<MatchResult>? {
       val chordWords = mutableListOf<MatchResult>()
       wordRegex.findAll(line).forEach { match ->
           when {
               ChordProChordNames.isChordName(match.value) -> chordWords += match
               isMarker(match.value) -> Unit
               else -> return null
           }
       }
       return chordWords
   }
   ```

   and in `rewriteChordLine`: `val replacements = chordWords(line)?.map { it.range to rename(it.value) } ?: return line`.
   Delete `private class ChordWord`. All three renames of the module (`transposeChord`, `toGerman`, `fromGerman`)
   keep the parentheses, and 38's `chordNames(lines)` now hears `(H)` rather than `H`, which `isGermanName` reads
   the same. `(x2)` is still a marker: it is not a chord name, and `isMarker` is asked second.

6. `grep -rn chordNameRegex .` must find nothing outside `documentation/issues/`.

## Tests
New file `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNamesTest.kt`:

1. `every spelling of a chord is a chord name` — `isChordName` is true for `A`, `Am`, `Bb`, `B♭maj7`, `F#m7b5`, `Hsus4`,
   `C/E`, `H/F#`, `Cm7b5/Gb`, `E7#9`, `D°7`, `Eø`, `C+`, `B7(b9)`, `B6/9`, `B6/9/F#`, `Bm(maj7)`, `Bmi7`, `B-`, `B-7`,
   `B7-9`, `B7+`, `B+7`, `B7alt`, `BΔ7`, `B∆`, `CmM7`, `CMaj7`, `C(add9)`, `C(9)`, `G7(#9,b13)`, `G7(#9, b13)`,
   `G7(#9b13)`, `C5(no3)`, `Cadd9(omit5)`, `(B)`, `(Bm7/F#)`.
2. `prose and half written names are not` — false for `""`, `N.C.`, `Bridge`, `Break`, `Bass`, `Hello`, `Hold`, `Halt`,
   `Calt`, `Eno`, `Ano`, `Cno3`, `Chorus`, `Coda`, `E-----`, `E--`, `A-ha`, `B-side`, `C(`, `C()`, `C(b9`, `C(b9))`,
   `C(ridge)`, `B(,b9)`, `B(b9,)`, `(B`, `B)`, `()`, `(())`, `C/`, `C/9/`, `C/E/G`, `C/Ex`, `h`, `am`, `x2`, `(x2)`, `C7 `,
   `Tuning:`, `E|`.
3. `a name as long as a file is walked and not recursed into` — `isChordName("C" + "b9".repeat(500_000))` is true,
   the same with `"!"` appended is false, `"C" + "(".repeat(500_000)` is false, and
   `"C(" + "b9,".repeat(300_000) + "x"` is false; none of them throws.
4. `the bass is what follows the last slash and starts with a note` — `notes`: `Am` → `[Am]`, `C/B` → `[C, B]`,
   `B6/9` → `[B6/9]`, `B6/9/F#` → `[B6/9, F#]`, `(Bm7/F#)` → `[Bm7, F#]`, `C/` → `[C/]`, `N.C.` → `[N.C.]`, `""` → `[""]`.
5. `a rewritten name keeps its slash and its parentheses` — `rewriteNotes("(Bm7/F#)") { it.lowercase() } == "(bm7/f#)"`,
   `rewriteNotes("B6/9") { "<$it>" } == "<B6/9>"`.

`ChordProNotationTest.kt`:

6. `a chord is rewritten however its quality is spelled` — `toGerman`: `B7(b9)` → `H7(b9)`, `B6/9` → `H6/9`,
   `Bm(maj7)` → `Hm(maj7)`, `Bmi7` → `Hmi7`, `B-` → `H-`, `B7alt` → `H7alt`, `BΔ7` → `HΔ7`, `(B)` → `(H)`,
   `Bb7(b9)` → `B7(b9)`, `Bb6/9` → `B6/9`, `A6/9/B` → `A6/9/H`, `(Bb/B)` → `(B/H)`.
7. extend `words that are not chords are returned unchanged` with `Bass`, `B-side`, `Balt`, `B(ridge)`.
8. `German names are read however their quality is spelled` — `fromGerman`: `B7(b9)` → `Bb7(b9)`, `H7(b9)` → `B7(b9)`,
   `(H)` → `(B)`, `(B)` → `(Bb)`, `A6/9/H` → `A6/9/B`, `B6/9` → `Bb6/9`; `isGermanName` true for `(H7)`, `H7alt`, `Hmi`,
   `A6/9/H`, false for `Halt`, `B6/9`, `(B)`.
9. `the chords above a tab are rewritten whatever they are spelled like` —
   `toGerman(parse("{sot}\n  B7(b9)  Bb6/9\ne|--0-----3--|\n{eot}")).tabLines()` is
   `listOf("  H7(b9)  B6/9 ", "e|--0-----3--|")` (the shorter name gets its column back as a space, as in
   `a shorter chord name keeps the columns of a tab`).

`ChordProTransposerTest.kt`:

10. `an optional chord and a bass after a six nine are transposed` — `transposeChord(x, 2, preferFlats = false)`:
    `(Am)` → `(Bm)`, `(Am/C)` → `(Bm/D)`, `A6/9` → `B6/9`, `A6/9/E` → `B6/9/F#`, `A7(b9)` → `B7(b9)`; `(x2)`, `N.C.` and
    `""` unchanged. `transposeText("[(Am)]la [A6/9/E]la", 2, preferFlats = false) == "[(Bm)]la [B6/9/F#]la"`.

`ChordProTabTransposerTest.kt`:

11. `a row of chord names is one however its chords are spelled` —
    `transposeText("{sot}\nC6/9   G7(b9) (Am)  F-\ne|--0--3--5--7--|\n{eot}", 2, preferFlats = false)` is
    `"{sot}\nD6/9   A7(b9) (Bm)  G-\ne|--2--5--7--9--|\n{eot}"` (today the first line comes back untouched).
12. `a note to the player is still not a row of chord names` —
    `"{sot}\nAmi Dalt E-----\ne|--0--|\n{eot}"` by 2 keeps its first line (`Dalt` is not a chord, so neither is the row).

Every existing test of the module passes unchanged; `every abbreviation of no chord is a marker` and
`transposing text moves the frets of a tab and the chord names above it` (`Tuning: E A D G B E` stays prose) are the
ones that pin the rows.

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. `./gradlew :app:desktop:run`; a song `[B7]a [B7(b9)]b [B6/9]c [Bm(maj7)]d [Bmi7]e [B-]f [B7alt]g [(B)]h [Bridge]i`.
   German notation on: every chord starts with `H`, the last word still reads `Bridge`. Off: all as written.
3. Transpose +1 in the viewer and in the editor: `(B)` becomes `(C)` in both.
4. A tab with `C6/9   G7(b9)` above the staff, +2: the row reads `D6/9   A7(b9)` and still lines up with the frets.
5. A file holding `[C` + `b9` five thousand times + `]` (`python3 -c "print('[C' + 'b9'*5000 + ']la')"`) opens with
   German notation on instead of closing the app.
6. The compile checks for the other three targets (common code only).

## Docs
- `chordpro/CLAUDE.md`: in the `ChordProSyntax` bullet remove "`chordNameRegex` for "is this whole word a chord and
  not a word that starts with a letter"," and add a bullet after it: "`ChordProChordNames` — what a chord name is
  made of: `isChordName`, the whole-word answer to "is this a chord and not a word that starts with a letter" that
  the notation and the chord rows of a tab ask (a walk over the word, because a name comes out of a file and may be
  as long as one), and `notes` / `rewriteNotes`, the one rule for where the root and the bass of a name are — the
  bass follows the *last* slash and starts with a note, so `6/9` is a quality, and parentheses around the whole name
  mark an optional chord and are kept — which the transposer shares." In the `ChordProTransposer` bullet, "follows
  the bass note after `/`" stays true.
- `documentation/file-format.md`, the **Content** bullet: after "`[Chord]` markers anchored to the syllable that
  follows them" add "(a chord may spell its quality any of the usual ways — `Bm7b5`, `Bmi7`, `B-7`, `B7(b9)`, `B6/9`,
  `B7alt`, `BΔ7` — take a bass note after a slash, and stand in parentheses when it is optional)".

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt` (new)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNamesTest.kt` (new)
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposerTest.kt`
- `chordpro/CLAUDE.md`
- `documentation/file-format.md`

## Depends on
38 (its `fromGerman`, `isGermanName` and `chordWords` are rewritten here) and 39 (the same files, and the reason for
walking instead of matching is stated there); through them 37.
