# 26 · ABC, LilyPond, SVG and text blocks are read as lyrics, and the editor's Transpose rewrites their brackets

**Severity:** minor (all platforms. Only ChordPro 6 files with `{start_of_abc}`, `{start_of_ly}`, `{start_of_svg}` or `{start_of_textblock}`; rare, but the editor's Transpose silently corrupts the notation in them) · **Area:** `:chordpro` (`ChordProParser`, `ChordProTransposer.rewriteText`, `ChordProHighlighter`)

## Symptom
A song holds an ABC block:
```
{start_of_abc}
X:1
K:G
[CEG]2 [A2B] |
{end_of_abc}
```
- The song screen draws the ABC source as lyrics with `CEG` and `A2B` raised as chords, under the heading "Abc".
- The song list reports it as having chords.
- Transpose +2 in the editor writes `[DEG]2 [B2B]` into the file: music notation the user never asked to change.

## Cause
Every `start_of_x` other than tab and grid opens `SectionType.Custom(x)` and its lines go through `parseLyrics`
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:131-132`, `:323-327`). The
text transposition rewrites every line outside tab and grid as lyrics (`ChordProTransposer.kt:231-233`:
`else -> lines[index] = rewriteLyricsLineChords(rawLine, rename)`), `scan`'s `writtenChordNames` (`ChordProParser.kt:109-113`)
counts their brackets, and the highlighter colours them (`ChordProHighlighter.kt:65`).

## Fix
Keep the lines of these environments verbatim: a section as today, but lines that carry no chords.

1. `ChordProSyntax.kt`:
   ```kotlin
   /**
    * The environments ChordPro hands to another program — ABC and LilyPond notation, SVG, a block of preformatted
    * text. Their lines are that program's input rather than lyrics: `[CEG]` is an ABC chord of three notes, and moving
    * it as a ChordPro chord would corrupt music nobody asked to change. They are kept line for line and nothing in
    * them is a chord.
    */
   val delegateEnvironments = setOf("abc", "ly", "svg", "textblock")
   ```

2. `ChordProParser.kt`:
   - `LineMode` (`:197`): `private enum class LineMode { TAB, GRID, VERBATIM }`.
   - `handleDirective`, the start branch (`:131-133`):
     ```kotlin
     section.close()
     section.open(sectionType(environment), ChordProSyntax.label(directive.value), isExplicit = true)
     if (environment.lowercase() in ChordProSyntax.delegateEnvironments) section.openLineMode(LineMode.VERBATIM, label = null)
     return
     ```
     `lineMode(environment)` stays null for them, so `{end_of_abc}` closes the section as it does today.
   - `SectionBuilder.addContent` (`:323-327`): `LineMode.VERBATIM -> ChordProLine.Lyrics(text = rawLine, chords = emptyList())`.
   - `isInLineMode`'s KDoc: "Whether a `{start_of_tab}`, a `{start_of_grid}` or one of the verbatim environments is
     open, which says how lines are read and not what section they are in." (Being in line mode also keeps a
     `{comment}` inside the block from being taken for a Campfire 3 heading, which is right here too.)
   - `writtenChordNames` (`:109-113`): add `in ChordProSyntax.delegateEnvironments -> emptyList()` before `else`.
     `scan`'s environment is already lowercased.

3. `ChordProTransposer.kt`, `rewriteText` (`:231-233`): add a branch before `else`:
   ```kotlin
   environment in ChordProSyntax.delegateEnvironments -> Unit
   ```

4. `ChordProHighlighter.kt`, `tokenize` (`:46-72`): rename `isInsideTab` to `isVerbatim` and extend the comment
   ("Chords are not chords inside a tab or an environment handed to another program: the brackets there are part of
   the tablature or of the notation, and the viewer leaves them alone too."):
   ```kotlin
   ChordProSyntax.startOfEnvironment(directive.name)?.let { isVerbatim = it == TAB_ENVIRONMENT || it in ChordProSyntax.delegateEnvironments }
   ChordProSyntax.endOfEnvironment(directive.name)?.let { if (it == TAB_ENVIRONMENT || it in ChordProSyntax.delegateEnvironments) isVerbatim = false }
   ```

The serializer needs nothing: the section is written back inside `{start_of_abc}` / `{end_of_abc}` and its lyrics
lines with no chords are their text, brackets included, which the parser reads verbatim again. The heading the
viewer shows for `Custom("abc")` ("Abc") is left as it is.

## Tests
`ChordProParserTest.kt`, new `the lines of an abc block are kept verbatim with no chords`:
```kotlin
val section = ChordProParser.parse("{start_of_abc}\nX:1\n[CEG]2 [A2B] |\n{end_of_abc}").blocks.single() as ChordProBlock.Section
assertEquals(SectionType.Custom("abc"), section.type)
assertEquals(listOf(ChordProLine.Lyrics("X:1", emptyList()), ChordProLine.Lyrics("[CEG]2 [A2B] |", emptyList())), section.lines)
```
and `summarize("{start_of_ly}\n[c e g]\n{end_of_ly}").hasChords` is false; `{start_of_textblock}` with a
`{comment: Chorus}` line inside keeps the comment a comment.

`ChordProTransposerTest.kt`, new `an abc block is left alone by the transposition`:
`"{start_of_abc}\n[CEG]2\n{end_of_abc}\n[C]la"` transposed by 2 is `"{start_of_abc}\n[CEG]2\n{end_of_abc}\n[D]la"`,
in the text and (the lyrics chord) on the model.

`ChordProSerializerTest.kt`: `parse(serialize(parse(x))) == parse(x)` for the abc song above.

`ChordProHighlighterTest.kt`: `spans("{start_of_abc}\n[CEG]\n{end_of_abc}\n[C]")` has exactly one `CHORD` span, `[C]`.

## Verify
Paste the ABC block into a song: the viewer shows it as plain lines with no chords; Transpose in the editor leaves
it byte for byte and moves the chords around it. `./gradlew :chordpro:desktopTest`.

## Docs
`chordpro/CLAUDE.md`, the `model/` bullet, after the sentences about `{start_of_tab}` and `{start_of_grid}`: "The
environments ChordPro hands to another program (`abc`, `ly`, `svg`, `textblock`) are sections whose lines are kept
verbatim as lyrics with no chords, so the transposition, the chord detection of the library scan and the
highlighter all leave them alone."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/` (`ChordProParserTest`, `ChordProTransposerTest`, `ChordProSerializerTest`, `ChordProHighlighterTest`)
- `chordpro/CLAUDE.md`

## Depends on
None. Edits `rewriteText` and the highlighter alongside 19, 22, 24: run them one after another.
