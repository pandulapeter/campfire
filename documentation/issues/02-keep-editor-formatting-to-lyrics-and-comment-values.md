<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep the editor's Bold, Italic, and Underline to lyrics and comment values, never tab, grid, delegated, or other directive lines

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — a plain `{c}` / `{comment}` the parser reads as a Campfire 3 heading is now null (wrapping
its value, `{c: <b>Chorus</b>}`, made it a comment, and the multi-line path would have done that to every heading of
such a song on Ctrl+A); the value's offsets now add the line's start and its leading whitespace (`directiveValueStart`
counts into the trimmed line); the multi-line path is now taken for a `\r` too, or several touched ranges, not only
for `'\n' in selected`, which missed a bare-CR file's breaks; the caret rule for an empty range is spelled out; tests added
for a selector name, a delegated `{c note}` / `{c: note}`, a legacy heading, leading whitespace, and bare CR.
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProMarkupLines.kt` (new),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProLiteralLines.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProMarkupLinesTest.kt` (new),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorFormatting.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorFormattingTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`

## Problem

`formattingToggle` in `EditorFormatting.kt` decides where tags may go by looking at the first character of a line:

```kotlin
private fun String.isFormattable() = trim().let { it.isNotEmpty() && !it.startsWith('{') && !it.startsWith('#') }
```

That check is used only when the selection spans several lines. The single-line path (the `isAroundSelection` /
`isEnclosedIn` / `else` branches) does no check at all. Two things go wrong:

**(a) Literal lines get tags.** The parser keeps the lines of a tab, a grid, and an environment handed to another
program (ABC, LilyPond, SVG, `textblock`) as written (chordpro/CLAUDE.md: "Delegated environments, tabs, grids, and
other directives have no markup"). Ctrl+A then Ctrl+B over a song turns `e|---0---|` into `<b>e|---0---|</b>`, which is
no longer a staff line, and a grid's `| C . . . |` row into text cells. The same happens to one selected word inside a
tab line through the single-line path.

**(b) Directives get tags.** On one line, a selection inside `{title: Foo}` gives `{title: <b>Foo</b>}`: the title holds
literal tags, and so does the file name a new song's Save builds from it. Selecting a whole `{c: note}` line gives
`<b>{c: note}</b>`, which the parser no longer reads as a directive, so the comment becomes a lyric. Comments may carry
markup by design (root CLAUDE.md; chordpro/CLAUDE.md: "A comment keeps its markup in its text"), so a selection inside a
comment's value must keep toggling.

The parser's rule for "which lines are literal" already exists, but only per offset:

```kotlin
public fun isLiteralAt(text: String, offset: Int): Boolean {
    val prefix = text.substring(0, offset.coerceIn(0, text.length))
    val lineIndex = ChordProLines.splitLines("$prefix.").lastIndex
    val line = ChordProLineScanner.scan(text).elementAtOrNull(lineIndex) ?: return false
```

Each call copies and rescans the text, so calling it per line of a Ctrl+A selection over a long song (the editor allows
far more than 50,000 characters) is quadratic. `ChordProLineScanner` and `ChordProDirectives` are internal to
`:chordpro`, so `:presentation` cannot do the walk itself.

## Fix

One public `:chordpro` function answers both questions in one scan: **where in each line markup is read**.

### Commit 1 — literal lines (finding a)

1. New `public object ChordProMarkupLines` in `chordpro/.../edit/ChordProMarkupLines.kt` (explicit API, KDoc'd as a
   contract, the license header copied from a sibling):

   ```kotlin
   /**
    * For every line of [text], split as the parser splits it, the offsets into [text] that markup written on that line is
    * read in, or null where the parser reads none: ...
    */
   public fun markupRanges(text: String): List<IntRange?>
   ```

   One `ChordProLineScanner.scan(text)` walk zipped with `ChordProLines.lineStartOffsets(text)`, per `ScannedLine`:
   - a directive line (`line.directive != null`, which inside a delegated environment is only a colon directive, so
     `{c note}` in ABC is literal text and `{c: note}` a comment) whose name is a comment the parser keeps markup in
     (`comment`, `c`, `comment_italic`, `ci`, `comment_box`, `cb`, `highlight`, the names `ChordProParser` turns into
     `ChordProBlock.Comment`; a selector name such as `c-guitar` is dropped by the parser and is not one): the value's
     offsets, from `ChordProDirectives.directiveValueStart(line.trimmed)` to the last non-whitespace character before
     the closing brace, trimmed. Those indices count into `line.trimmed`, so the text offset is the line's start plus
     `raw.length - raw.trimStart().length` (`trim` and `trimStart` both use `Char.isWhitespace`) plus the index. An
     empty value (`{c: }`) is an empty range (`at until at`) at the closing brace so a caret there still gets a pair;
     `{c}` with no value at all is null. Decide this before the literal check, since a `{c: note}` inside a tab is
     still a comment;
   - **except** a plain `comment` / `c` with no environment open before it (`line.environment == null`) whose value
     `ChordProParser.isLegacyHeading` accepts: that is a Campfire 3 heading opening a section, and the parser reads the
     heading from the value's first letters (`legacyHeading`), so `{c: <b>Chorus</b>}` would become a plain comment and
     the song lose a section. Null. (The scanner's `environment == null` is the parser's
     `!section.isExplicit && !section.isInLineMode` or stricter: after `{sov}{sot}…{eot}` the scanner says null while
     the parser's verse is still explicit, so such a comment is left alone when it could have been formatted, never the
     other way round.);
   - any other directive, a source comment (`isSourceComment`), and a literal line: null;
   - every other line (lyrics, a blank line included): the whole line without its break, leading and trailing
     whitespace included (the editor trims what it wraps itself).

   Put the literal rule in one internal place both functions read, e.g. an `internal val ScannedLine.isLiteral` in
   `ChordProLiteralLines.kt` (`isDelegated || environment == TAB || environment == GRID`), and have `isLiteralAt` use it.
   `isLiteralAt` keeps its signature and its single call from `PastedLines.kt`.
2. `formattingToggle`: compute `ChordProMarkupLines.markupRanges(text)` once and choose the path by line breaks of
   either kind and by the ranges, not by `'\n' in selected` alone (a file with bare CR line endings, an old Mac export
   the line splitter supports and the editor keeps byte for byte, has no `\n` between its lines; `'\n' in selected`
   would send a selection of several such lines down the single-line path). A range *touches* the selection when its intersection with `start until end`, trimmed,
   is not empty; with a caret (`start == end`), when `range.first <= start <= range.last + 1` (so an empty range
   `at until at` holds a caret at `at`, and a caret at a line's end is on that line). A line break (`\n` or `\r`) in
   the selection, or several touched ranges: the multi-line path, which keeps whitespace outside the tags as now
   (`Hello \n{title: x}` gives `<b>Hello</b> `); otherwise the single-line path (step 3, then commit 2).
   Multi-line: replace `isFormattable` with the touched ranges, intersect each with the selection, and apply the existing `withCore` wrapping / unwrapping and
   the "all formatted → take off, else format the rest" rule to that intersection only, rebuilding the text from
   `start` to `end` from the back so earlier offsets stay valid; the result is still one replacement of `start` to
   `end` (one undo step) and the selection still covers it. A comment line inside the selection then has its value
   formatted, never its braces (it used to be skipped).
3. Single-line path, this commit only: when `ChordProLiteralLines.isLiteralAt(text, start)`, return a no-op
   (`FormattingToggle(start, end, selected, start, end)`). One call, one scan.

### Commit 2 — directives (finding b)

Replace step 3 with the ranges: the one range the selection touches (step 2's rule). Then:
- no range touched (a selection or caret on any non-comment directive, a legacy heading, a `#` line, a literal line,
  or only on a comment's name or braces, `{ti|tle}`): no-op;
- otherwise clip the selection to that range and run the existing three branches on the clipped selection. The
  `isAroundSelection` check must only match tags that lie inside the range.

Options for a selection covering a comment's braces: **wrap only the value (recommended)**, which matches what the
multi-line path now does and what the user meant; or leave it alone, which makes Ctrl+B on a whole selected comment
line do nothing for no visible reason.

Docs: the songEditor `CLAUDE.md` Bold/Italic/Underline bullet becomes "markup goes only where the parser reads it
(`ChordProMarkupLines`): a lyric line, or a comment's value; a selection is clipped to that, and a tab, grid, delegated,
other directive, Campfire 3 heading (`{c: Verse 1}` with no environment open), or `#` line is left alone".
`chordpro/.../edit/CLAUDE.md` gets one line: `ChordProMarkupLines` is where markup is read, line by line, in one walk, by the parser's rule; `isLiteralAt` shares its literal-line rule.

## Tests

`./gradlew :chordpro:desktopTest` (commit 1), new `ChordProMarkupLinesTest`, reusing the song of
`ChordProLiteralLinesTest`:
- a lyric line's range is the whole line (`"[C]Hello"` → its offsets), a blank line's is empty-but-present;
- grid, tab, and ABC lines, `{title: Song}`, `{sog}`, and a `# note` line are null;
- `{c: Play <b>slowly</b>}` and `{highlight: x}` give exactly the value's offsets; `{c: }` an empty range at the
  brace; `{c}` null; `{c: note}` inside a tab gives its value;
- `  {c: note}` (leading whitespace) gives the value's offsets in the text, not in the trimmed line;
- `{c-guitar: x}` is null; inside `{start_of_abc}`, `{c note}` is null and `{c: note}` gives its value;
- `{c: Chorus}` with no environment open is null (a Campfire 3 heading), the same inside `{start_of_verse}` gives its
  value, and `{ci: Chorus}` gives its value anywhere;
- `\r\n` and bare `\r` line endings give the same ranges shifted by the extra characters;
- `markupRanges` over a 60,000-character song returns as many entries as `splitLines` (no timing assertion).

`./gradlew :presentation:desktopTest`, `EditorFormattingTest` (with the existing `|…|` helper):
- commit 1: `|{start_of_tab}\ne|--0--|\n{end_of_tab}\nWords|` formats only `Words`; a grid row is left alone; a
  multi-line selection over `{c: note}` gives `{c: <b>note</b>}`, over `{c: Verse 1}` with no environment open leaves
  it alone; a word selected inside a tab line is left alone; `|One\rTwo|` (bare CR) formats both lines;
- commit 2: `{title: |Foo|}` is unchanged; `|{c: note}|` gives `{c: <b>|note|</b>}`; `{c: |note|}` and the existing
  comment toggle still work; `|# note|` is unchanged; a caret in `{c: |}` writes an empty pair; the existing tests still
  pass unchanged.
