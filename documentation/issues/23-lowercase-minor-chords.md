# 23 · Lowercase minor chords (`[a]`, `[d]`, `[h]`) are never transposed, and a lowercase `h` does not mark a song as German

**Severity:** wrong behaviour (all platforms. Central European charts that write minors as lowercase roots, a convention of the app's main audience; how common it is in users' files is open. In such a song the uppercase chords move and the lowercase ones stay, in the viewer and, through the editor's Transpose, in the file) · **Area:** `:chordpro` (`ChordProChordNames`, `ChordProNotation`, `ChordProTransposer.transposeText`, `ChordProParser.scan`)

## Decision (taken by the user on 2026-09-22)
**A. A lowercase root reads as the minor chord of that root.** `a` is `Am`, `h7` is `Hm7`, `f#` is `F#m`; a
lowercase `h` is an `H` root and marks the song as German like an uppercase one. The viewer draws them spelled out
(`Am`, and `Hm` with the German preference on), since the model is in the app's one notation; the editor's Transpose
keeps the file's own lowercase spelling (`a` +2 is `h` in a German song, `b` in an English one). Only a whole bracket
that is such a chord counts, so `[fine]` or `[da capo]` are untouched. The per-song German detection itself (an `H`
root, no marker) is not re-opened.

## Symptom
1. A German-notated song (it has an `H`): `[C]… [a]… [F]… [G]… [H7]… [e]`. Transpose +2 in the viewer: `D … a … G
   … A … C#7 … e`. The minors `a` and `e` did not move. The editor's Transpose writes the same into the file.
2. A song whose only `H` is a lowercase minor (`[D]… [h]… [G]… [A]`) is not taken for German: its `h` is not
   understood at all, and a `B` in it would be read as B natural.

## Cause
- `ChordProChordNames.noteEnd` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt:51-54`)
  accepts only `'A'..'H'`, so `isChordName("a")` is false, which `ChordProNotation.isGermanName` / `fromGerman`
  (`ChordProNotation.kt:37-63`) and the tab chord-line detection rely on.
- `transposeNote` (`ChordProTransposer.kt:299-305`) looks up `noteIndices[part[0]]`, which has only uppercase keys,
  and returns the part unchanged otherwise.

## Fix
1. `ChordProChordNames.kt`, two functions:

   ```kotlin
   /**
    * [word] as the minor chord a lowercase root stands for in Central European charts — `a` is `Am`, `h7` is `Hm7`,
    * `f#` is `F#m`, `(e)` is `(Em)` — or null for anything else. A suffix that starts with an `m` is not taken, since
    * `am` would say minor twice and `amaj7` would be neither. The bass note after `/` is a note and not a chord, so it
    * is written in capitals as everywhere else.
    */
   fun lowercaseMinorExpanded(word: String): String? {
       val name = unwrapped(word)
       val root = name.firstOrNull()?.takeIf { it in 'a'..'h' } ?: return null
       val noteLength = if (name.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
       if (name.startsWith("m", noteLength)) return null
       val expanded = root.uppercaseChar() + name.substring(1, noteLength) + "m" + name.substring(noteLength)
       if (!isChordName(expanded)) return null
       return if (isParenthesized(word)) "($expanded)" else expanded
   }

   /** The other way: [name], a minor chord, written with a lowercase root and no `m`. */
   fun lowercaseMinorFolded(name: String): String {
       val chord = unwrapped(name)
       val noteLength = if (chord.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
       val folded = chord[0].lowercaseChar() + chord.substring(1, noteLength) + chord.substring(noteLength).removePrefix("m")
       return if (isParenthesized(name)) "($folded)" else folded
   }
   ```

2. `ChordProNotation.kt`:
   - A lowercase `h` is an `H` root:
     ```kotlin
     internal fun isGermanName(name: String) = (ChordProChordNames.lowercaseMinorExpanded(name) ?: name).let { chord ->
         ChordProChordNames.isChordName(chord) && ChordProChordNames.notes(chord).any { it.startsWith(GERMAN_B_NATURAL) }
     }
     ```
     (Its KDoc: "…that uses German notation's `H`, at its root or bass, a lowercase `h` minor included.")
   - `normalized` (`:77-87`) expands them before anything else:
     ```kotlin
     internal fun normalized(song: ChordProSong): ChordProSong {
         val names = ChordProTransposer.writtenChordNames(song).toList()
         val german = names.any(::isGermanName)
         val hasLowercaseMinors = names.any { ChordProChordNames.lowercaseMinorExpanded(it) != null }
         if (!german && !hasLowercaseMinors && names.none { SHARP_SIGN in it || FLAT_SIGN in it }) return song
         val rename = { name: String ->
             val expanded = ChordProChordNames.lowercaseMinorExpanded(name) ?: name
             withAsciiAccidentals(if (german) fromGerman(expanded) else expanded)
         }
         ...
     ```
     The expansion happens first, so a German `b` (B♭ minor) becomes `Bm` and then `Bbm`.
   - New, for the text transposition's decisions:
     ```kotlin
     /** [song] with its lowercase minor chords spelled out, and nothing else changed. */
     internal fun withLowercaseMinorsExpanded(song: ChordProSong) = ChordProTransposer.rewriteChords(
         song = song,
         rewriteTabLines = { lines -> lines },
         rename = { name -> ChordProChordNames.lowercaseMinorExpanded(name) ?: name },
     )
     ```

3. `ChordProTransposer.kt`, `transposeText` (`:117-132`):
   ```kotlin
   val written = ChordProNotation.withLowercaseMinorsExpanded(ChordProParser.parseAsWritten(text))
   ```
   (the rest of the decisions — German or not, flats or not, stays German — then see the chords spelled out), and
   wrap both renames handed to `rewriteText` so a lowercase chord comes back lowercase:
   ```kotlin
   /**
    * [rename] for a chord as the file writes it: a lowercase minor is spelled out for it and folded back afterwards,
    * so that the file keeps its own convention.
    */
   private fun keepingLowercaseMinors(rename: (String) -> String) = { name: String ->
       ChordProChordNames.lowercaseMinorExpanded(name)?.let { ChordProChordNames.lowercaseMinorFolded(rename(it)) } ?: rename(name)
   }
   ```
   i.e. `rewriteText(text, semitones, keepingLowercaseMinors(transposeName))` and
   `rewriteText(text, semitones, keepingLowercaseMinors { name -> … })`. The tab path is unaffected: its chord-name
   rows are found with `isChordName`, which does not change.

4. `ChordProParser.kt`, `scan` (`:90-101`): look for the notation on a line with either letter, and expand the key:
   ```kotlin
   val isLookingForNotation = !isGermanNotated && (GERMAN_LETTER in rawLine || GERMAN_LETTER.lowercaseChar() in rawLine)
   ```
   ```kotlin
   val key = declared.key?.let { written -> ChordProChordNames.lowercaseMinorExpanded(written) ?: written }?.let { key -> … }
   ```
   with `isGermanKey` computed on the expanded key. (The lowercase check makes `scan` parse more lines of an English
   song until it finds its first chord, which `scan` does anyway for `hasChords`; it is linear.)

## Tests
`ChordProChordNamesTest.kt`, new `a lowercase root is a minor chord`:
`lowercaseMinorExpanded` gives `Am` for `a`, `Hm7` for `h7`, `F#m` for `f#`, `(Em)` for `(e)`, `Em/G` for `e/G`,
and null for `am`, `amaj7`, `add`, `fine`, `A`, `N.C.`; `lowercaseMinorFolded("C#m7") == "c#7"`,
`lowercaseMinorFolded("(Bbm)") == "(bb)"`.

`ChordProNotationTest.kt`:
- `a lowercase h marks a song as German`: `isGermanNotated(parseAsWritten("[D]a [h]b"))` is true.
- `lowercase minors are read spelled out`: `parse("[H]a [h]b [a]c [b]d").chordNames() == ["B", "Bm", "Am", "Bbm"]`,
  and `parse("[F]a [d]b").chordNames() == ["F", "Dm"]` (English).

`ChordProTransposerTest.kt`:
- `lowercase minors are transposed and keep their spelling in the text`:
  `transposeText("[H]a [h]b [a]c", 2, preferFlats = false) == "[C#]a [c#]b [h]c"`;
  `transposeText("[C]a [a]b [G]c", 2, preferFlats = false) == "[D]a [b]b [A]c"`; and transposing either result back by -2
  (`preferFlats = false`) gives the original text.
- `lowercase minors are transposed on the model`: `transpose(parse("[C]a [a]b"), 2, preferFlats = false)` has
  `D` and `Bm`.
- `a lowercase word in brackets is not a chord`: `transposeText("[fine]", 2) == "[fine]"`.

## Verify
A German song with `[a]`, `[e]`, `[h]` minors: the viewer shows `Am`, `Em`, `Hm` (German preference on) and moves
them with the rest on transposition; the editor's Transpose keeps them lowercase. `./gradlew :chordpro:desktopTest`.

## Docs
`chordpro/CLAUDE.md`, the `ChordProNotation` bullet, after "a German chord chart writes `B` for what an English one
calls `Bb`, but never `Ais`.)": "Those charts often write a minor chord as its root in lowercase (`a` for `Am`, `h`
for `Hm`), and that is read as the minor chord it stands for, in either notation; a lowercase `h` marks a song as
German like an uppercase one. The model spells them out; the editor's transposition keeps the file's lowercase."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/` (`ChordProChordNamesTest`, `ChordProNotationTest`, `ChordProTransposerTest`)
- `chordpro/CLAUDE.md`

## Depends on
None. 22 (grid cells) composes with it: `cellChords` hands each part to the same renames.
