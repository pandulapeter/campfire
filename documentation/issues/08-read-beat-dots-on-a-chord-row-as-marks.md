# Read beat dots (`.`, `...`, `…`) on a chord row as marks, so a dotted chart's bar lines and counts are not lyrics

**Kind:** bug · **Severity:** low · **Platforms:** all
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProRowMarks.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserRowMarksTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt` (only if a pinned
output changes),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProHighlighterTest.kt`,
`chordpro/CLAUDE.md`

**Decision (taken by the user, 2026-10-26): the dots count as marks;** parentheses stay lyrics. **Lane R**, alone.

**Challenged:** amended — pinned the edge cases a player would write (dots glued to a sung word, dots glued between two
chords, a lone chord over dots or an ellipsis, `..`/`....`) as tests; stated the intended lyrics-only and PDF
consequence and where it comes from; corrected the converter quote (the file spells the ellipsis `\u2026`).

## Problem

A chord row's marks are a fixed set plus repeat counts (`syntax/ChordProRowMarks.kt`):

```kotlin
private val signs = setOf("|", "||", "|:", ":|", ":|:", "%", "%%", "/", "-", "N.C.", "NC")

fun isMark(word: String) = word in signs || repeatCount.matches(word)
```

and one word that is not a mark refuses the whole row (`marksOf`: `if (!isMark(...) || …) return null`). Beat dots —
`[G] . . . | [C] . . . x2`, a common way of writing four beats a bar — are not in the set, so the row is no chord row:
`. . . |`, `. . . x2` stay lyrics under the chords, the bar line and the count included, drawn as a lyric line, shown in
lyrics-only mode as a line of dots and bars, and spoken by a screen reader as text.

The converter already treats them as part of a chord row (`convert/ChordSheetConverter.kt`):

```kotlin
private fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "-", "/", "%", "N.C.", "NC", "(", ")", "...", "\u2026", ".") || ChordProRowMarks.repeatCount.matches(value)
```

and writes such a row as `[G] . . . | [C] . . . x2` (`bracketed`: chords bracketed, furniture as is) — which the parser then
reads as lyrics. A pasted dotted chart therefore converts into exactly the case above.

## Fix

1. Add `"."`, `"..."`, and `"\u2026"` (`…`, spelled as the converter spells it) to `signs`. Exactly these three, as the
   converter's furniture has them: `..` and `....` stay words, so a row holding one stays lyrics, in the converter and
   the parser alike. Nothing else in the parser changes: `marksOf` and `marksIn` work
   on words, the marks become `Chord(isAnnotation = true, isMark = true)` as every mark does, and the text is left blank
   where they stood.
2. Update the class KDoc's list of marks ("…bar lines and repeat signs (`|`, `|:`, `%`, `/`), beat dots (`.`, `...`,
   `…`), and a no chord…") and keep its last paragraph's rule (words, and parentheses such as `(let ring)`, are never
   marks). Do not add `(` or `)`: the converter's furniture keeps them for its own row detection; in a file, a row
   holding a bare `(` stays lyrics, as decided.
3. Check, without expecting changes, every consumer of `isMark`, and say in the commit's verification which held:
   - the highlighter (`edit/ChordProHighlighter.kt`, `ChordProRowMarks.marksIn(line)`) colours the dots as annotations;
   - the serializer round trip (`commonTest` `ChordProSerializer`: a mark is written back as its bare text);
   - presentation: `ui/songLayout/LyricsPadding.kt` (a mark is never a row's start), `ui/songView/SongLineWithChords.kt`
     (`withChordsInline` writes a mark bare: the screen reader / copy text reads `[G] . . . | [C] . . . x2`),
     `ui/print/PrintRows.kt` (marks follow `showChords`) — all generic over `isMark`;
   - transposition and notation conversion rewrite bracketed text only (`ChordProChordRewriter` also skips annotations in
     the model), so dots are untouched; scales (`scales/SongParts.kt`), chord lists (`chords/ChordProChords.kt`),
     `ChordProParser`'s chord summary, and `ChordProSong.hasChords` skip annotations.
   - **Intended consequence, not a regression:** a dotted row now has blank text, so lyrics-only mode drops it
     (`ui/songView/model/SongRenderSections.kt`, `prepareForDisplay`: a blank-text `Lyrics` line goes with the chords)
     and a PDF without chords drops it (`PrintRows.lyricsRows`: chords hidden and text blank → no rows), exactly as a
     `[G] [C] x4` row already is. That covers `[Am]. . .` and `[G] ...` written as a "spoken pause": they read as a
     chord over beat dots, which is what the user decided. A row that holds any word keeps everything as lyrics, so a
     sung line with dots (`[G]Oh... [C]yeah...`, `[G]Hello . . .`) never loses a character.
   - `ChordSheetConverter`: its output text does not change (it already writes the dots bare); only how the parser
     reads that output does. Run `ChordSheetConverterTest`; if a test pins the *parsed* lines of a dotted chart, update
     it to the marks.
4. `chordpro/CLAUDE.md`, the row marks line: "**A chord row's repeat counts, bar lines, and beat dots are marks**".

## Tests

In `ChordProParserRowMarksTest`:

- `beat dots are marks between the chords`: `line("[G] . . . | [C] . . . x2")` has a blank text and chords
  `G . . . | C . . . x2` (names), every one but G and C `isMark && isAnnotation`; `assertRoundTrip` on it; the same for
  `[G] ... [C] …`.
- `a lone chord over dots or an ellipsis is a chord row`: `line("[Am]. . .")` is `Am` then three `.` marks with a blank
  text, and `line("[G]…")` is `chord(0, "G"), mark(0, "…")` (a mark sharing its offset with a chord comes after it, as
  `[D]x4` does); `assertRoundTrip` on both.
- Extend `a sung line, a lone count, a backing line, and a word with a chord inside keep their text` with
  `"[G]Hello . . ."`, `"[G]Oh... [C]yeah..."`, `"[G] [C] (. . .)"` (`(.` and `.)` are words), `"[G]...[C]..."` (one
  word `......` with a chord inside it, as `[G]x[C]4`), `"[G] .... [C] .."` (not one of the three), and a lone
  `". . ."` (no bracket): all keep their text, no marks.
- `ChordProHighlighterTest`: beside its row-marks case (it has one with `x4`), the dotted row's dots, bar line, and count are
  coloured as annotations.

`./gradlew :chordpro:desktopTest :presentation:desktopTest spotlessCheck`.
