# Select the header's value instead of inserting a tempo or time change above the body's first line of the song

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — a `{chorus}` recall now counts as a line of the song (it is a section on the page, which plan 20's "first `RenderSection.Lines`" also counts), so the two plans agree on where the song starts; one test added.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeader.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeaderTest.kt`, `chordpro/CLAUDE.md`

## Problem

The editor's Tempo and Time signature shortcuts (`EditorToolbar.kt`'s `insertIntoHeader`, which passes
`caretOffset = minOf(selection.start, selection.end)`) call `ChordProHeader.insertChangeable`. Where the header already
names a value and the caret is in the body, it writes `{tempo: }` at the start of the caret's line
(`ChordProHeader.kt:158-164` at 1c52e5347):

```kotlin
val caretLine = lineStarts.indexOfLast { it <= caretOffset }
if (bodyStart >= lines.size || caretLine < bodyStart) {
    val valueStart = headerLineStart + headerLine.lastIndexOf(value, headerLine.lastIndexOf('}'))
    return Insertion(offset = valueStart, text = "", caretOffset = valueStart, selectionEnd = valueStart + value.length)
}
// A caret past the final line break is on a line of its own already, the one after the last.
val offset = if (caretOffset >= text.length && ChordProSyntax.endsWithLineBreak(text)) text.length else lineStarts[caretLine]
return Insertion(offset = offset, text = "$prefix$suffix${ChordProSyntax.lineSeparatorOf(text)}", caretOffset = offset + prefix.length)
```

`ChordProSyntax.bodyStartIndex` is the index of the first line that starts the body — the first lyric line itself, or
the first environment directive. Two placements go wrong (verified with a probe at 1c52e5347):

1. **Caret on the line `bodyStart` itself** (`caretLine == bodyStart`). The new line is inserted *before* it, so it is
   still part of the header; the parser reads a second header `{tempo}` past, and the value the user types does
   nothing. `{title: X}\n{tempo: 120}\n\n[C]The first line` with the caret on `[C]` and `90` typed gives
   `{title: X}\n{tempo: 120}\n\n{tempo: 90}\n[C]The first line`: no `Timing`, `metadata.tempo` still `120`. The same
   happens with the caret on a `{start_of_verse}` that opens the body: `{tempo: 90}` lands above it, in the header.
2. **Caret on the first lyric line inside an opening environment.** `…\n{start_of_verse}\n[C]The first line\n…` with
   the caret on `[C]` gives `{start_of_verse}\n{tempo: 90}\n[C]The first line`, which parses as
   `[Timing(tempo=90, time=null), Section(Verse…)]` — a change before any line of the song has been played. It does
   what was typed, but it is a change from the song's own value at the very start, which is the song's own value
   written in the wrong place: the header still says `120`, the click and the page show a change line and (until the
   presentation lane's own fix) a forced page for nothing.

In both cases nothing of the song stands between the header and the caret, so there is nothing a change could be
"from".

## Fix

Options:

- **A (recommended):** treat a caret *at or before the body's first content line* like a caret in the header: select
  the header line's value, so typing replaces the song's own value. The first content line is the first line at or
  after `bodyStart` that is not blank, not a source comment (`#`), and either not a directive
  (`ChordProSyntax.matchDirective(trimmed) == null`: lyrics, a line of tablature or of a grid) or a `{chorus}` recall,
  which is a part of the song that is sung and which the song details screen draws as a section of its own (a
  `RenderSection.Lines`, the same thing plan 20 counts as the song's first line — so a change after a leading recall
  is a change on both sides, and one before the first line is the song's own value on both sides). Comments,
  `{transpose}`, breaks and environment openers are not lines. `lines.size` where there is none (which also covers the
  existing "no body yet" case, so `bodyStart >= lines.size` folds into it). Concretely, replace
  `if (bodyStart >= lines.size || caretLine < bodyStart)` with
  `if (caretLine <= firstContentLine)` where

  ```kotlin
  // Nothing of the song is played before its first line, so a change there would be the song's own value written
  // in the wrong place. A recall is a part of the song, as a line of lyrics is.
  val firstContentLine = (bodyStart until lines.size).firstOrNull { index ->
      val trimmed = lines[index].trim()
      if (trimmed.isEmpty() || trimmed.startsWith('#')) return@firstOrNull false
      val directive = ChordProSyntax.matchDirective(trimmed) ?: return@firstOrNull true
      directive.name == "chorus"
  } ?: lines.size
  ```

  (`matchDirective` lowercases the name already, so `{Chorus}` is caught too.)

  (`#` being the source comment `bodyStartIndex` skips, whose constant is private to `ChordProSyntax`). A caret past the final line break of a song with content stays an insertion, as today (the existing test with `caret = it.length` on a text ending in a line break must keep passing; the caret's line there is past every content line).
- B: insert *after* the caret's line when `caretLine == bodyStart`. Fixes case 1 only, and still writes a change
  before the first line in case 2 and whenever the body opens with a directive.

Update the KDoc of `insertChangeable` to match option A: the third bullet becomes "a header that names a value and a
caret below the body's first line of the song (a line of lyrics, tablature or a grid, or a `{chorus}` recall) get a line of their own at the start
of the caret's line…", and the fourth "a header that names a value and a caret in the header or anywhere up to and
including that first line, or a song with no such line yet, get nothing written and the header line's value selected,
since a change before anything is played is the song's own value". Update the `insertChangeable` sentence in
`chordpro/CLAUDE.md` (the `ChordProHeader` entry: "at the start of the caret's line where the header names a value
and the caret is in the body") the same way: "…and the caret is below the body's first line of the song".

## Tests

In `ChordProHeaderTest` (the file's private `insertChangeable` helper returns the text and the selection):

- `{title: T}\n{tempo: 120}\n\n[C]The first line` with the caret at `indexOf("[C]")` and at the end of that line returns
  the text unchanged with `120` selected.
- `{tempo: 120}\n\n{start_of_verse}\n[C]a\n[G]b\n{end_of_verse}` with the caret on `{start_of_verse}` and on `[C]a`
  selects `120`; with the caret on `[G]b` inserts `{tempo: }\n` before it.
- `{tempo: 120}\n\n{chorus}\n[C]a` with the caret on `[C]a` inserts `{tempo: }\n` before it (a recall is a line of the
  song); with the caret on `{chorus}` it selects `120`. `{tempo: 120}\n\n{c: Slowly}\n[C]a` with the caret on `[C]a`
  selects `120` (a comment is not).
- The existing tests `a changeable directive is written above the caret's line in the body` and
  `…with the caret in the header selects the header's value` keep passing.
- A round trip: for each of those texts and carets, wherever `insertChangeable` returned a non-empty `text`, splice
  in `90` at the caret and assert `ChordProParser.parse(result).blocks` contains a `ChordProBlock.Timing` with tempo
  `90` and that the first block of the song is not a `Timing`.

## Manual check

In the editor, on a song with `{tempo: 120}` in its header, put the caret on the first lyric line and tap Tempo in
the Shortcuts: the header's `120` is selected. Type `90` and open the preview: it says 90 and shows no change line.
With the caret on the second lyric line, Tempo inserts a line above it and the preview shows a change line there.
