# 12 — Never cut a wrapped tab row through a character

## What the user sees

A tab that is too wide for the screen is cut into rows. The columns it is cut at are decided by the staff lines —
after a bar line, or on a column every string is silent on — and then *every* line of the system is cut at that
same column, including the lines written above the staff: chord names, a `Riff 1`, a line of lyrics.

Those lines are the user's own text, and a character there may be written with more than one `Char`. When the cut
falls inside one, both rows show a replacement glyph — one at the end of the row above and one at the start of the
row below — and it happens on **every** row of the tab, so a wide tab is a column of boxes down both edges.

Verified at HEAD, wrapping at 10 columns a system whose text line is `x` followed by six U+1D11E (𝄞, a surrogate
pair each):

```
row 1 text:  x 𝄞 𝄞 𝄞 𝄞 \uD834        <- lone high surrogate
row 2 text:      \uDD1E 𝄞             <- lone low surrogate
```

And the same for a combining mark, wrapping at 12 a text line of eleven `a`s then `e` + U+0301:

```
row 1 text:  aaaaaaaaaae              <- the acute is gone
row 2 text:  ··́bbbb                  <- the acute lands on the padding space
```

Anything the user pastes can carry these: an emoji or a musical symbol (surrogate pairs), or a decomposed accent
(`é` typed on iOS as `e` + U+0301, which is exactly how a title arrives decomposed — `:data:model` already handles
that case for file names). The staff lines themselves are ASCII, so the cut column is always safe for them; it is
only the prose above them that is torn.

## Cause

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt:84-98`, verified at HEAD
`984861e4`:

```kotlin
        while (start < length) {
            // The first row keeps the lines' own beginnings, every later one starts with the repeated string names.
            val contentStart = if (start == 0) prefixWidth else start
            val capacity = (maxColumns - prefixWidth).coerceAtLeast(MIN_CAPACITY)
            val end = if (length - contentStart <= capacity) length else cutColumn(lines, isStaffLine, barColumns, contentStart, contentStart + capacity)
            val rowLines = lines.mapIndexedNotNull { index, line ->
                val content = if (start < line.length) line.substring(start, minOf(end, line.length)) else ""
```

`end` comes from `cutColumn` (`:172-180`), which looks only at bar lines (`barColumns`, `:152-164`) and at "quiet"
columns (`isQuietColumn`, `:183-184`, a dash on a staff or a space elsewhere). Both are decided from the staff,
and neither knows anything about the characters a text line is made of. `line.substring(start, minOf(end,
line.length))` then cuts at that `Char` index whatever sits there.

The module already treats a tab's columns as `Char` counts on purpose — "Nothing in it is a measurement: it is
asked for a number of characters, and the viewer works that out from its font" (`chordpro/CLAUDE.md`). That is
right for the staff and wrong for the one operation that splits a string.

## The change

Give the wrapper one notion of "where this particular line may be cut", and put the same boundary on both sides of
every cut, so nothing is lost and nothing is duplicated.

### 1. A boundary per line, computed from the shared column

Add to `ChordProTabWrapper`:

```kotlin
    /**
     * Where [line] may be cut for a row that the staff under it wants to end at [column].
     *
     * A tab is cut at columns the staff decides, and the staff is dashes, bars and digits. A line written above it
     * is somebody's own text, where a character may be written with more than one `Char` — an astral symbol as a
     * surrogate pair, a letter and the mark that accents it — and cutting through one draws a replacement glyph at
     * the end of one row and another at the start of the next. So the column is moved back off the middle of a
     * character. It is moved back for both sides of the cut, since the next row starts where this one ended, which
     * is what keeps the character in exactly one row.
     */
    private fun cutBoundary(line: String, column: Int): Int {
        var index = column.coerceAtMost(line.length)
        var moved = 0
        while (index > 0 && index < line.length && moved < MAX_CLUSTER && line.isInsideCharacter(index)) {
            index--
            moved++
        }
        return index
    }

    /** Whether the character at [index] belongs to the one in front of it rather than starting one of its own. */
    private fun String.isInsideCharacter(index: Int) =
        (this[index].isLowSurrogate() && this[index - 1].isHighSurrogate()) || this[index].isCombiningMark()

    /**
     * The combining marks a pasted title or chord name carries: the Latin block a decomposed accent is written
     * with, and the general categories every other script's marks fall in. `:data:model` has its own, narrower
     * answer for folding accents out of a file name; this module depends on nothing, so it carries its own.
     */
    private fun Char.isCombiningMark() = this in '̀'..'ͯ' ||
            category == CharCategory.NON_SPACING_MARK ||
            category == CharCategory.COMBINING_SPACING_MARK ||
            category == CharCategory.ENCLOSING_MARK

    // A crafted line of nothing but combining marks would otherwise make the walk-back as long as the line, once
    // per row; past this many the cut is made where the staff asked and one glyph is drawn wrong.
    private const val MAX_CLUSTER = 16
```

`Char.category` is common Kotlin stdlib and resolves on all four targets — `'́'.category` is
`NON_SPACING_MARK` (verified on the JVM); the explicit `'̀'..'ͯ'` range in front of it is the cheap
answer for the case that actually happens and keeps the behaviour identical to `:data:model`'s
`Char.isCombiningMark()` for Latin text.

### 2. Use it on both sides of every cut

`ChordProTabWrapper.kt:89-93`:

```kotlin
            val rowLines = lines.mapIndexedNotNull { index, line ->
                // The same boundary function on both ends: this row stops where the next one starts, so a character
                // the shared column falls inside travels whole into the row whose column has passed it.
                val from = cutBoundary(line, start)
                val to = cutBoundary(line, end)
                val content = if (from < to) line.substring(from, to) else ""
                val row = if (start == 0) content else (if (isStaffLine[index]) prefixes[index].padStart(prefixWidth) else " ".repeat(prefixWidth)) + content
                row.trimEnd().takeIf { isStaffLine[index] || it.isNotBlank() }
            }
```

`start` and `end` stay the shared **nominal** columns and the loop's `start = end` (`:97`) is unchanged, so the
walk always advances and cannot loop. `cutBoundary` is monotonic in `column` and clamped to `line.length`, so
`from <= to` always; where a cluster spans a whole row for one line, that line contributes `""` to that row and
the whole cluster appears in the row whose nominal end has passed it. Nothing is dropped.

For a staff line — ASCII throughout — `cutBoundary(line, column)` is `minOf(column, line.length)`, which is
exactly what the old expression computed, so every existing wrapping test is unaffected by construction. Say that
in the comment: it is the reason this is safe to apply to all lines rather than only to the ones above the staff.

### 3. Leave the rest alone

`cutColumn`, `barColumns`, `isQuietColumn`, `systems` and `staffPrefix` all keep working in `Char` columns. That
is deliberate and stays: a tab *is* a grid of `Char` columns, the staff is what defines it, and the viewer turns
the count into a width from its font. This plan only stops the one operation that slices a string from slicing it
in the middle of a character. Do **not** try to make the wrapper work in code points throughout — the columns
would then no longer be the columns the staff is written in.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapperTest.kt`, next to
`a quiet cut never falls inside a chord name` (line 107):

1. `a row is never cut through a surrogate pair` — the system from **What the user sees**
   (`"x" + "𝄞".repeat(6)` above two staff lines, `maxColumns = 10`). Assert that no row line contains a
   lone surrogate: `rows.flatten().none { row -> row.any { it.isSurrogate() && … } }`, and, more readably, that
   every row line equals what you expect character for character. Write the expectation out with escapes so the
   test says what it means.
2. `a row is never cut between a letter and its accent` — the eleven-`a` case above at `maxColumns = 12`: the
   second row must not start with U+0301, and the first must end with `é`.
3. `nothing is lost or repeated by moving a cut back` — the strongest form, and the one that catches an off-by-one
   in `cutBoundary`: for each of the two fixtures, concatenate the text line's contribution to every row (dropping
   the repeated string-name padding, which only staff lines get) and assert it equals the original line with its
   trailing spaces trimmed the way the wrapper trims them.
4. `a staff line is cut exactly where it always was` — a plain ASCII tab wrapped before and after the change gives
   the same rows. The existing suite covers this; add one explicit case only if the diff makes it unclear.
5. `a line of nothing but combining marks does not stall the wrap` — a text line of 200 U+0301 above a staff,
   wrapped at 20: assert the call returns, that the number of rows matches the staff's own cutting, and that the
   marks all appear somewhere in the output (the `MAX_CLUSTER` guard).

## Verification

```
./gradlew :chordpro:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual (`./gradlew :app:desktop:run`, then narrow the window until the tab wraps):

1. Import a song with a `{start_of_tab}` environment wide enough to wrap, whose line above the staff holds an
   emoji or a `𝄞` and a decomposed `é`.
2. Narrow the window through several widths. No row may show a `�` at either edge, at any width.
3. Check the same on the web build (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), where the text is laid out
   by a different engine.

## Docs

`chordpro/CLAUDE.md`, the `ChordProTabWrapper` bullet (lines 122-136) — this sentence is the one the change
qualifies:

> Nothing in it is a measurement: it
>   is asked for a number of characters, and the viewer works that out from its font.

It stays true of the columns, and now needs the exception next to it: a line above the staff is cut at the nearest
place that is not inside a character, so a surrogate pair or a letter and its combining mark travel into one row
whole. Put it next to the sentence about a line above the staff being left out of the rows it has nothing to say
in, which is the other rule about those lines.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapperTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing. Plan 11 also edits `ChordProTabWrapper` (`staffPrefix`), in a different function — land 11 first or
rebase.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all, so it cannot reuse `:data:model`'s
  `Char.isCombiningMark()` and carries its own.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` is part of the change.
