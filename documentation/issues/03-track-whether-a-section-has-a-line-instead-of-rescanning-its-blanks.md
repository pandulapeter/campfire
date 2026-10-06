# Track whether the open section has a line instead of rescanning its blank lines on every block

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
**Challenged:** amended — the test's 40 000 lines parse in about 0.5 s at HEAD, under its own 1 s bound, so it would pass without the fix; it now uses 150 000 (≈ 7 s at HEAD by the measured quadratic growth) with a 2 s bound.

## Problem

`SectionBuilder.addBlock` (ChordProParser.kt:532 at 8ee010b36) decides whether a block cuts the section by scanning
every line it holds:

```kotlin
val isCut = type != null && lines.any { it != ChordProLine.Blank }
```

Inside an explicit environment blank lines are kept in `lines` (`addContent`: `isExplicit || lineMode != null -> lines
+= ChordProLine.Blank`), and a section that holds only blanks is never flushed by `addBlock`, so every following
`{comment}`, `{highlight}`, break, `{transpose}` or recall rescans all of them. Measured with a probe test on the
desktop target, `{soc}` + N blank lines + N `{c:x}` lines:

| N | file | `parse` |
|---|------|---------|
| 10 000 | 70 KB | 69 ms |
| 20 000 | 140 KB | 122 ms |
| 40 000 | 280 KB | 495 ms |

Quadratic: a crafted file of a few MiB (within the import's 8 MiB text limit, e.g. 1 M blanks and 600 k comments)
parses for minutes, on every open of the song details screen and on every keystroke of the editor's preview. Crafted
input only; no real song has thousands of blank lines in one environment.

## Fix

In `SectionBuilder`, add `private var hasContentLine = false`:
- set it to `true` in `addContent` where a non-blank line is appended (the `lines += when (lineMode) { … }` path, i.e.
  after the `if (trimmedLine.isEmpty()) { … return }` block);
- reset it to `false` in `open` and in `close` (both already `lines.clear()`);
and replace the scan with `val isCut = type != null && hasContentLine`. `close()`'s trimming of trailing blanks never
removes a content line, so the flag stays exact; `addBlock`'s close-and-reopen goes through both and resets it.

## Tests

In `ChordProParserTest`, `blank lines before a run of comments do not slow the parse down`: `{soc}` + 150 000 blank
lines + 150 000 `{c:x}` + `{eoc}` (about 1 MB) parses within 2 seconds (`TimeSource.Monotonic`, as
`ChordProPrettifierTest.repeated lines map without searching their earlier matches again` does) and yields 150 000
comments with no section. (40 000 is not enough: at HEAD it parses in about 0.5 s, so the test would
pass without the fix; the growth is quadratic, so 150 000 takes several seconds there.) The existing parser tests pin that `isCut` still behaves the same (comments opening a
section stay `START_OF_SECTION`, cut sections still continue).

## Manual check

None needed beyond the unit test; optionally import the crafted file above and open it: the song opens at once.
