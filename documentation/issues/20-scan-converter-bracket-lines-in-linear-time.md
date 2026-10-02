# Stop the converter's chord-bracket detection being quadratic on a line of many opening brackets
**Challenged:** amended — the 100_000-character "belt" is dropped (the plain `ChordProSyntax.brackets` fix converts a 100_000-`[` line in 37 ms, measured, and a cap would silently change the answer for legitimate long lines), and the behaviour statement is corrected: it is not identical for an unclosed or nested opener.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (imports of PDF, Word, text)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`

## Problem
`isChordPro()` runs two private regexes per line: `brackets = Regex("\\[([^\\]]+)\\]")` and
`attachedChord = Regex("\\[([^\\]\\s]+)\\][A-Za-z…]")`. On a line of many `[` with no `]` each start scans to the end of
the line, so the cost is quadratic. Measured at 8c267e01a through `ChordSheetConverter.convert(ChordSheet.ofPlainText("["×n + "\nhello world"))`:
n=5000 264 ms, 10000 921 ms, 20000 3.6 s (reported 20 s at 40k, over 10 minutes at 100k). A text file or a PDF with
such a line freezes the import (and, being the same on the import path, every `.txt`).

## Fix
Use `ChordProSyntax.brackets(line)` (linear: `indexOf` of the open bracket, then of the close one, and it stops when
there is no close) in `isChordPro`: `inline` counts lines where any bracket's `content` is a `chord(...)`, and the
attached-chord test becomes "any bracket whose content has no whitespace, `isChordName`, and whose next character
(`line[range.last + 1]`) is a letter in the old class". Remove the two private regexes (nothing else in the file uses them). **Not byte-identical, and that is accepted:**
(1) an empty `[]` is now seen and ignored; (2) `brackets()` pairs each `[` with the next `]` and resumes after it, the
way `ChordProParser.parseLyrics` reads a line, whereas the attached-chord regex retried inside a failed match, so
`[a b [C]x` used to count `[C]x` as an attached chord and no longer does (the parser would not read a chord there either).
Do not add a length cap: the measured cost without one is already trivial (n=100_000 of `[`, `(`, `[[ ` and `[ `: 37, 12, 131
and 62 ms through `convert`, tried in a worktree), and a cap would make `isChordPro` answer differently for a long
legitimate line.

## Tests
`ChordSheetConverterTest`: `convert` of a 100_000-`[` line plus a lyric line finishes in under 5 s (measure with
`TimeSource.Monotonic`; it took minutes before, ~40 ms after, so the bound only has to catch a regression to quadratic) and returns an escaped-prose result; the existing detection tests
(`[G]Hello` passes through, majority-of-lines rule, `[C]` footnote prose) still pass.

## Manual check
None beyond importing a text with an enormous line; it completes.
