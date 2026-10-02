# Keep the editor's caret next to its text when Prettify rewrites the document
**Challenged:** amended — the matching step was O(lines x duplicates) ("nearest unmatched equal line" on a song of repeated `{c: Chorus}` lines is quadratic on the main thread) and used line lengths that are wrong for CRLF text; replaced by a hashed, pointer-consumed matching over `lineStartOffsets`, the editor flow (single `edit`, one undo step, no extra threading) was checked and mirrors `replaceWithTransposition`, and a duplicate-lines performance test and a CRLF test are added. Depends on plan 18 (same file) only for ordering.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifier.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifierTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`, `chordpro/CLAUDE.md`
Do this after 18-keep-tab-and-grid-environments-inside-their-paragraph-when-prettifying.md (same file).

## Problem
`onPrettify` in `SongEditorScreen.kt` calls the private `TextFieldState.replaceAll(prettified)`:
`val caret = selection.start.coerceAtMost(text.length); delete(0, length); insert(0, text); selection = TextRange(caret)`.
The offset is kept as a character count, but Prettify reorders the header, inserts and removes blank lines and
changes line endings, so the caret jumps to unrelated text (and the selection collapses). The transposition has the
right behaviour: `replaceWithTransposition` uses `ChordProTransposer.transposedOffset`. `replaceAll` is also used by
revert and the metadata edits, which should stay as they are. Prettify receives the editor's own text
(`viewModel.prettifyText` = `prettifyChordPro(text)`, no notation conversion) and is line-based, so notation is not an
issue.

## Fix
1. Add `fun prettifiedOffset(before: String, after: String, offset: Int): Int` to `ChordProPrettifier`, documented as
   the counterpart of `ChordProTransposer.transposedOffset`. Algorithm, linear in the text (a long document is
   prettified on the main thread on every keystroke already, so this must not be quadratic: `after` may hold tens of
   thousands of identical `{c: Chorus}`-style lines):
   - clamp `offset` to `0..before.length`; take line starts with `ChordProSyntax.lineStartOffsets` (NOT lengths summed
     from `splitLines`: the editor's text may carry CRLF or lone CR, and `prettify` writes LF) and lines with
     `ChordProSyntax.splitLines`; column = offset - start of its line;
   - key every non-blank line of `after` by its trimmed text in a `HashMap<String, IntArray/ArrayDeque of line indices>`;
     walk `before`'s non-blank lines in order; for each take the first still-unused index of its key that is >= the
     previous match, else (metadata was reordered) the first unused one, else unmatched. Each index list is consumed
     with a moving pointer, so the whole pass is O(lines);
   - a caret on a matched line keeps its column, shifted by the difference of the two lines' leading whitespace and
     clamped to `0..newLine.length`; a caret on a blank line, an unmatched line, or the break after a line goes to the
     start of the next matched line of `after` (or `after.length` when none);
   - the result is clamped to `0..after.length` and the function never throws (an empty `before`, an empty `after`,
     an offset past the end, a text with no final newline).
2. In `SongEditorScreen.kt` add `replaceWithPrettification(prettified)` next to `replaceWithTransposition`. Copy its
   shape exactly: `val before = text.toString()`, early return when `before == prettified`, the same
   `LONG_DOCUMENT_LENGTH` history clearing, then inside the one `edit { }` compute `start`/`end` from `selection.start` /
   `selection.end` (so a reversed selection keeps its direction), `replace(0, length, prettified)`,
   `selection = TextRange(start, end)`. Call it from `onPrettify` instead of `replaceAll`. Threading is already safe:
   `onPrettify` runs on the main thread, `prettifiedText.value` is a `derivedStateOf` over the same `TextFieldState`
   snapshot, and the whole change is one `edit` (one undo step), exactly like transposition. `replaceAll` stays for
   revert and the metadata edits.

## Tests
`ChordProPrettifierTest`: (a) a caret in the middle of a lyric line stays in that line at the same column after the
header is reordered and blank lines are added above it; (b) a caret on a removed duplicate blank line lands at the next
line's start; (c) identical text returns the offset; (d) every offset -3..length+3 of a sample returns a value in `0..after.length` and never throws, for an empty `before`, an
empty `after`, CRLF input and input with no final newline; (e) 50_000 identical `{c: Chorus}` lines plus a lyric line map a caret
on the last line in under 2 s (it is linear; the bound is generous) and to the right line.

## Manual check
Editor with a long song whose header is out of order; put the caret mid-verse, Prettify; the caret stays in that verse.
