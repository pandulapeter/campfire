# Make the converter's chord-over-lyric merge and inline replacements linear
**Challenged:** amended — the plan assumed non-decreasing `positions` (false after the stacked-chord padding branch) and missed the per-chord `spans.firstOrNull` scan, which `ofPlainText`'s one-span-per-word lines make quadratic too, so its own 30_000-chord test would not have passed; the fix now uses exact structures (suffix minimum, prefix maximum) with an identical-by-construction fallback, and tests include a non-monotone line. Needs plan 20 only for the shared file; do it before 22-never-split-a-surrogate-pair-when-converting-chord-lines.md.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`
Do this before 22-never-split-a-surrogate-pair-when-converting-chord-lines.md (same functions).

## Problem
`merge()` does, per chord, `lyrics.positions.indexOfLast { it <= x }` and `starts.minByOrNull { abs(lyrics.positions[it] - x) }`:
O(chords x line length). Measured at 8c267e01a, a line of n chords over n×3 characters of lyrics: n=5000 123 ms,
n=10000 1.37 s (reported 11.7 s at 30k). `inline()` applies replacements with `text.replaceRange` one at a time
(quadratic in copies; 20000 `(C)` took only 63 ms here, so this half is minor — reported 2.5 s at 60k).

## Fix
Two assumptions of the first draft were wrong and are corrected here. (1) `positions` is NOT always non-decreasing:
`render()` adds the padding space of the stacked-chord branch (`span.start == before.start && chord(value) && chord(before.text)`)
at `before.end`, which is past the next span's `start`, and overlapping spans do the same. (2) The binary searches alone
do not make `merge()` linear: it also scans `lyrics.source.spans.firstOrNull { … }` once per chord, and
`ChordSheet.ofPlainText` makes one span per WORD, so the 30_000-chord test line has ~60_000 spans and would still take
seconds. Therefore, per merged line:
- compute once `val isMonotone = positions non-decreasing` and `val spansSorted = spans' start non-decreasing`; when a
  flag is false use today's code unchanged (so the output is byte-identical on every input by construction);
- `indexOfLast { it <= x }`: with a suffix-minimum array `suffixMin[i] = min(positions[i..])` (non-decreasing in `i`
  even when `positions` is not) the last index with `positions[i] <= x` is the last `i` with `suffixMin[i] <= x`: a binary
  search, exact for every input, so this one needs no flag;
- `starts.minByOrNull { abs(lyrics.positions[it] - x) }` (monotone only): binary-search the first word start whose
  position is >= x; the candidates are that one and the one before it, and the one before is first moved back to the
  first start with the same position value (duplicates arise from stacked chords); take the nearer, the lower index on
  a tie, which is what `minByOrNull` returns;
- the span containing `lyrics.positions[start]` (`spansSorted` only): with `prefixMaxEnd[i] = max(end of spans[0..i])` binary-search
  the first `i` with `prefixMaxEnd[i] > pos`; that span is the answer when its `start <= pos`, and there is none
  otherwise (every later span starts at or after it), identical to `firstOrNull`;
- `lastWidth` is already O(1);
- in `inline()`, `line.positions.indexOfFirst { abs(it - span.start) < 0.01 }` runs once per styled run (quadratic on a line of
  many bold chords): with monotone positions binary-search the first index with `position > span.start - 0.01` and check
  `< span.start + 0.01`; and build the result once: sort the replacements by start, skip one that overlaps the previously
  accepted (the old code applied overlapping ones on top of each other, which only ever produced garbage, so this
  is the one deliberate difference, covered by no existing test), append the segments to one `StringBuilder` over the
  escaped text (keep the `end <= text.length` guard).
Output must stay byte-identical for every input except that overlapping-replacement garbage.

## Tests
In `ChordSheetConverterTest`: a 30_000-chord line over matching lyrics and a 60_000-`(C)` line each convert in under
5 s (they take milliseconds after the fix; the bound only has to catch quadratic behaviour on a slow CI machine), plus a lyric line whose positions are not monotone (stacked chords, `Span` starts overlapping) that must equal the old output. Before changing the code, capture the output of the existing golden tests and a few random plain-text sheets
(`Random(7)`, seeded chord/word lines) and assert the new output equals what the old code produced (paste the expected
strings, or keep the old implementation as a private reference in the test).

## Manual check
None.
