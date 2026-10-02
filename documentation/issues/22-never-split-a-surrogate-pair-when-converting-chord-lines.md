# Do not place a chord between the halves of a surrogate pair
**Challenged:** amended — moved the surrogate rule to the one place where the final text is assembled (so it survives plan 21's rewrite of the `at` computation and cannot disagree with it), removed the unnecessary escape-length caveat (escape is length preserving) and dropped the optional `render()` change that would alter alignment of every line with an emoji. Do it after 21-align-chords-over-lyrics-in-linear-time.md.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`

## Problem
`merge()` chooses an insertion index in UTF-16 units; `"C   G\nHel😀lo wor"` converts at 8c267e01a to
`[C]Hel\uD83D[G]\uDE00lo wor` — a lone high surrogate before `[G]` and a lone low one after it, which becomes
replacement characters when the file is written as UTF-8. `inline()`'s `start`/`end` offsets for `(C)` and styled runs
can in principle land inside a pair the same way, and `render()` assigns one position per UTF-16 unit, so an emoji is
two columns wide.

## Fix
`ChordProLiteralText.escape` preserves the length (it maps `[`, `]`, `{`, `}`, `#` one for one), so an index into `lyrics.text`
is an index into the escaped text, and one rule in one place is enough:
- In `merge()`'s final `buildString` loop (after plan 21 the `at` computation is a different code path, so do not patch
  it there), when an insertion index `i` satisfies `0 < i < escaped.length && escaped[i - 1].isHighSurrogate() && escaped[i].isLowSurrogate()`,
  emit that index's chords after the pair instead (carry them to `i + 1`, ahead of any chords already due there, so
  their left-to-right order is kept). The chord then lands just after the emoji, the side nearer to where the word
  following it starts. The trailing-index branch (`i == escaped.length`) cannot be inside a pair.
- In `inline()` leave the replacements alone: `(C)` ranges start and end on ASCII, and a styled run's `start` is a position
  match and its `end` is `start + clean(span.text).length`; guard only that last one: skip a replacement whose `start` or `end`
  falls between a high and a low surrogate of the escaped text (the `end <= text.length` guard sits right there).
- Do NOT give both halves of a pair one position in `render()` (the draft's optional item): it changes the width
  arithmetic of every following character on the line, breaks plan 21's monotone/duplicate reasoning and changes
  existing output for no reported fault.

## Tests
`ChordSheetConverterTest`: the sample above yields a string with no lone surrogate (check every high surrogate is
followed by a low one and vice versa) and the chord on the nearest side of the emoji; one with a pair at the very
start and end of the lyric line.

## Manual check
Import a pasted chord sheet with an emoji in the lyrics; the emoji displays intact.
