# 08 — Four small `:chordpro` disagreements (highlighter, repeat counts, delegate environments, an empty tab's label)

**Severity:** minor (display and round trip) · **Area:** `:chordpro` (`ChordProHighlighter`, `ChordProTabTransposer`,
`ChordProParser`, `ChordProSyntax`, `ChordProTransposer`)

**Read, not run.** Each was found by reading the code at HEAD (2065e47f); each has a unit test below that fails
before its fix. They are independent of each other and can land as four commits.

---

## A. The editor does not colour a `[chord]` the transposer moves on a non-staff tab line

### What the user sees

In the editor, a row of bracketed chords above a staff inside `{start_of_tab}`:

```
{start_of_tab}
[Am]      [C]
e|--0--1--|
{end_of_tab}
```

The `[Am]` and `[C]` are drawn as plain text, yet the editor's Transpose action (and the viewer) moves them.

### Cause

`ChordProHighlighter.kt:61-62, 70` switches bracket colouring off for the whole tab environment:

```kotlin
                    ChordProSyntax.startOfEnvironment(directive.name)?.let { isVerbatim = it == TAB_ENVIRONMENT || it in ChordProSyntax.delegateEnvironments }
                    ...
                !isVerbatim -> ChordProSyntax.brackets(line).forEach { bracket ->
```

while `ChordProTabTransposer.rewriteChordLine` (`:112-120`) renames the brackets of every tab line that is not a staff
line (`if (ChordProSyntax.hasBrackets(line)) return ChordProTransposer.rewriteLyricsLineChords(line, rename)`), and
`rewriteChordNames` (`:48-49`) does the same for the notation. The `:chordpro` rule "what counts as a chord is decided in
one place" is broken for these lines.

### The change

Track the two kinds of environment apart and colour the brackets of a tab line that is not a staff line:

```kotlin
        var isInTab = false
        var isInDelegate = false
        ...
                    ChordProSyntax.startOfEnvironment(directive.name)?.let {
                        isInTab = it == TAB_ENVIRONMENT
                        isInDelegate = it in ChordProSyntax.delegateEnvironments
                    }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let {
                        if (it == TAB_ENVIRONMENT) isInTab = false
                        if (it in ChordProSyntax.delegateEnvironments) isInDelegate = false
                    }
        ...
                // A staff line's brackets are part of the tablature, which the transposition moves by its frets; the
                // brackets of any other line of a tab are chords to it, and are coloured as chords here.
                !isInDelegate && !(isInTab && ChordProSyntax.isStaffLine(line)) -> ChordProSyntax.brackets(line).forEach { … }
```

Update the comment at `:46-47` accordingly.

### Tests

`ChordProHighlighterTest`: `the brackets above a staff are chords` — the example gives `CHORD` spans for `[Am]` and
`[C]` and none on the staff line. The existing `brackets inside a tab are left alone` (`:121-133`,
`e|--[3]--|`, a staff line) and its CR-only twin (`:176-185`) keep passing.

---

## B. A `3x` repeat count written after a staff is moved as a fret

### What the user sees

`e|--0--2--| 3x` transposed +2 in the viewer or the editor becomes `e|--2--4--| 5x`, and the count also takes part in
the octave decision of the whole environment.

### Cause

`ChordProTabTransposer.fretRanges` (`:57-75`) skips only digits written *after* the `x` (`x3`):

```kotlin
                if (line.getOrNull(index - 1)?.lowercaseChar() != REPEAT_COUNT_MARKER) {
                    ranges += index..endIndex
                }
```

### The change

A run of digits after the last bar line of the line and directly followed by an `x` is a count too:

```kotlin
    private fun fretRanges(line: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        // What stands after the last bar line is a note to the player (`x3`, `3x`, `(3x)`), not a string's frets.
        val lastBar = line.lastIndexOf('|')
        var index = 0
        while (index < line.length) {
            if (line[index].isAsciiDigit) {
                var endIndex = index
                while (endIndex + 1 < line.length && line[endIndex + 1].isAsciiDigit) {
                    endIndex++
                }
                val isCountAfter = line.getOrNull(index - 1)?.lowercaseChar() == REPEAT_COUNT_MARKER
                val isCountBefore = index > lastBar && lastBar >= 0 && line.getOrNull(endIndex + 1)?.lowercaseChar() == REPEAT_COUNT_MARKER
                if (!isCountAfter && !isCountBefore) ranges += index..endIndex
                index = endIndex + 1
            } else {
                index++
            }
        }
        return ranges
    }
```

Only after the last bar: inside the staff, `3x` is a fret followed by a dead note on the next column
(`--3x--` is not a count), which must keep moving. Update the KDoc (`:56`) and the constant's comment (`:179`).

### Tests

`ChordProTabTransposerTest`: `a repeat count written before its x is not a fret` —
`transposeText("{sot}\ne|--0--2--| 3x\n{eot}", 2)` == `"{sot}\ne|--2--4--| 3x\n{eot}"`; `| (3x)` likewise;
`e|--3x--|` inside the staff moves to `e|--5x--|`.

---

## C. Inside `abc` / `ly` / `svg` / `textblock`, `#` lines are dropped and `{ c d e }` is read as a comment

### What the user sees

A LilyPond block:

```
{start_of_ly}
\relative c' {
{ c d e }
#(set-global-staff-size 20)
}
{end_of_ly}
```

is shown with its `#(…)` line missing, and `{ c d e }` taken out of the block and drawn as a comment `d e`, cutting the
block in two. These environments are promised to be "kept verbatim" (`chordpro/CLAUDE.md`).

### Cause

`ChordProParser.parseAsWritten` (`:45-53`) drops every `#` line and matches every line as a directive before it looks
at what is open:

```kotlin
            if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
            val directive = ChordProSyntax.matchDirective(trimmedLine)
```

and `matchDirective` accepts a value separated by whitespace alone for a known name (`ChordProSyntax.kt:188-193`) —
`c` is `comment`'s short name — so `{ c d e }` is `{c: d e}`.

### The change

Inside a delegate environment only two kinds of line are directives: an `{end_of_…}` and one written with a colon
(`{comment: Chorus}`, which the existing test `the lines of an abc block are kept verbatim with no chords`,
`ChordProParserTest.kt:605-614`, relies on for a `textblock` — keep that behaviour). `#` lines are content there.
In `ChordProSyntax.kt`:

```kotlin
    /**
     * [matchDirective] for a line of an environment ChordPro hands to another program, whose own syntax is full of
     * braces and `#`: only the `{end_of_…}` that closes it and a directive written with a colon are directives there,
     * since `{ c d e }` is LilyPond and not `{c: d e}`.
     */
    fun matchDelegatedDirective(trimmedLine: String): Directive? {
        val (name, valueStart) = walkDirective(trimmedLine) ?: return null
        val hasColon = valueStart > 0 && trimmedLine[valueStart - 1] == DIRECTIVE_VALUE_SEPARATOR
        return if (endOfEnvironment(name) != null || hasColon) matchDirective(trimmedLine) else null
    }
```

Apply it in the four walkers, so that they keep agreeing about where a line ends and what it is:

- `ChordProParser.parseAsWritten`: `SectionBuilder` exposes `val isDelegated get() = lineMode == LineMode.VERBATIM`;
  the loop becomes

  ```kotlin
            if (trimmedLine.startsWith(SOURCE_COMMENT) && !section.isDelegated) return@forEach
            val directive = if (section.isDelegated) ChordProSyntax.matchDelegatedDirective(trimmedLine) else ChordProSyntax.matchDirective(trimmedLine)
  ```
- `ChordProParser.scan` (`:78-89`): with `val isDelegated = environment in ChordProSyntax.delegateEnvironments`, the
  same two changes (the `#` skip and the directive match).
- `ChordProTransposer.rewriteText` (`:228-257`): the same, on its own `environment` variable.
- `ChordProHighlighter.tokenize` (`:53-84`): with part A's `isInDelegate`, a `#` line there is not a `COMMENT` token
  and the directive is matched with `matchDelegatedDirective`.

### Tests

- `ChordProParserTest`: `a delegate block keeps its braces and hash lines` — the example is one section whose lines are
  the four inner lines verbatim; the existing textblock `{comment: Chorus}` assertion keeps passing.
- `ChordProHighlighterTest`: inside `{start_of_ly}`, `{ c d e }` and `#(x)` produce no tokens; `{end_of_ly}` is a
  `DIRECTIVE_NAME`.
- `ChordProTransposerTest`: `transposeText("{start_of_ly}\n{ key G }\n{end_of_ly}", 2)` is unchanged.

---

## D. An empty `{start_of_tab: Riff}` labels the lyrics after it, and the label is lost on a round trip

### What the user sees

```
{start_of_tab: Riff}
{end_of_tab}
[C]la la
```

The viewer heads the lyrics "Riff". Serialized and parsed again the heading is gone
(`parse(serialize(parse(x))) != parse(x)`).

### Cause

`SectionBuilder.openLineMode` (`ChordProParser.kt:267-270`) opens an implicit paragraph carrying the environment's
label; `closeLineMode` (`:272-274`) only resets the mode, so the lyrics after it land in that labelled paragraph:

```kotlin
        fun openLineMode(mode: LineMode, label: String?) {
            if (type == null) open(SectionType.Paragraph, label = label, isExplicit = false)
            lineMode = mode
        }

        fun closeLineMode() {
            lineMode = null
        }
```

The serializer only writes a paragraph's label back onto a tab or grid run it holds (`ChordProSerializer.kt:81-97`),
and this one holds none.

### The change

Fixed in the parser rather than the serializer, since the heading itself is wrong: an environment that opened the
paragraph and put nothing in it takes the paragraph away again.

```kotlin
        /** Whether the running paragraph was opened by the tab or grid environment that is open, see [closeLineMode]. */
        private var isOpenedByLineMode = false

        fun openLineMode(mode: LineMode, label: String?) {
            if (type == null) {
                open(SectionType.Paragraph, label = label, isExplicit = false)
                isOpenedByLineMode = true
            }
            lineMode = mode
        }

        /**
         * An environment that opened a paragraph of its own and wrote nothing in it leaves nothing behind: its label
         * names that environment, and a line after it would otherwise be headed by it.
         */
        fun closeLineMode() {
            lineMode = null
            if (isOpenedByLineMode && lines.all { it == ChordProLine.Blank }) close()
            isOpenedByLineMode = false
        }
```

Reset `isOpenedByLineMode` in `open` and `close` as well. `close()` of a paragraph with no lines and no `headingText`
adds nothing.

### Tests

- `ChordProParserTest`: `an empty tab environment labels nothing` — the example parses to one
  `Section(Paragraph, null, [[C]la la])`; `{sot: Riff}\n\n{eot}` parses to no blocks.
- `ChordProSerializerTest`: the example round trips.
- The existing `tab lines keep their indentation` (`{start_of_tab: Riff}` with a line) keeps its "Riff".

---

## Verification (all four)

The unit tests are the confirmation; in the app:

1. A: open the example in the editor — `[Am]` and `[C]` in the chord colour, the staff plain.
2. B: the `3x` song transposed +2 in the viewer keeps `3x`.
3. C: the LilyPond example in the viewer shows the four lines verbatim in one section.
4. D: the example shows `la la` with no heading.

## Docs

`chordpro/CLAUDE.md`:

- `model/` bullet, after "The environments ChordPro hands to another program (`abc`, `ly`, `svg`, `textblock`) are
  sections whose lines are kept verbatim as lyrics with no chords": add "— `#` lines and braces included: only the
  `{end_of_…}` that closes one and a directive written with a colon are read as directives inside it
  (`ChordProSyntax.matchDelegatedDirective`), by the parser, the summary, the transposition and the highlighter alike".
  And after "which is the only place `{start_of_tab: Riff}` can still say "Riff"": "; one that holds no line leaves no
  paragraph behind, so its label heads nothing".
- `ChordProTabTransposer` bullet: after "a line above them holding nothing but chord names (bar lines, repeats and an
  `N.C.` allowed)": add "; a repeat count after the last bar line (`x3`, `3x`) is not a fret".
- `ChordProHighlighter` bullet: add "Inside a tab it colours the brackets of the lines that are not the staff, which are
  the ones the transposition renames."

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt` (A, C)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt` (B)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt` (C)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt` (C, D)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt` (C)
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing functionally. Lands **last in the `:chordpro` part of lane A** (after 01, 03, 02, 05, 06, 04): C and D touch
the parser loop, `SectionBuilder` and `rewriteText`, which those plans also edit. D's `closeLineMode` coexists with
plan 03's `hasTabLine` reset in `openLineMode`.
