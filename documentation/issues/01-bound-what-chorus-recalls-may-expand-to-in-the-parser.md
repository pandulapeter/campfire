# Bound what `{chorus}` recalls may expand to, in the parser, so a small file cannot multiply into millions of lines

**Kind:** crash  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`, `chordpro/CLAUDE.md`
**Challenged:** amended — the budget counts characters as well as lines (a single long chorus line recalled a thousand times passed the line-count cap and still ran out of memory: one line of 5 000 `[H]a` and 1 000 `{chorus}` lines, a 29 KB file, OOMs in `transpose` at HEAD and stays within a 1 002-line budget); numbers, tests and the CLAUDE.md sentence follow.

## Problem

`ChordProParser.withChorusesRecalled` (ChordProParser.kt:132-157 at 8ee010b36) gives every `ChordProBlock.ChorusRecall`
the whole chorus it repeats:

```kotlin
return blocks.mapIndexed { index, block ->
    if (block !is ChordProBlock.ChorusRecall) return@mapIndexed block
    while (chorusIndex + 1 < choruses.size && choruses[chorusIndex + 1].first < index) chorusIndex++
    block.copy(blocks = choruses.getOrNull(chorusIndex)?.second.orEmpty())
}
```

The list is shared, so the parse itself is cheap — but every step after it expands each recall on its own:
`ChordProTransposer.rewriteChords` (ChordProTransposer.kt:113-116) copies the chorus once per recall

```kotlin
is ChordProBlock.ChorusRecall -> block.copy(
    label = block.label?.let { rewriteLyricsLineChords(it, rewrite.rename) },
    blocks = block.blocks.map { rewriteBlock(it, rewrite) },
)
```

and it runs inside `parse` itself (`ChordProNotation.normalized`, whenever the file has an `H`, a lowercase minor or a
`♯`/`♭` anywhere), on every viewer transposition or forced accidental spelling, and on `toNotation` for German readers.
Then the viewer (`SongLyrics.kt` `toRenderSections`, the `is ChordProBlock.ChorusRecall` branch) and the PDF layout
(`PrintLayout.kt` `recallRows`) draw the whole chorus for every recall. Work and memory are recalls × chorus lines.

Measured with a throwaway probe test on the desktop target (`{title: x}`, `{soc}`, N lines of `[chord]a`, `{eoc}`,
N `{chorus}` lines):

| N | file | recalled lines | `parse` | `transpose(song, 2)` |
|---|------|----------------|---------|----------------------|
| 300, `[G]` | 4 KB | 90 000 | 3 ms | 51 ms |
| 1000, `[G]` | 14 KB | 1 000 000 | 2 ms | 115 ms |
| 1000, `[H]` | 14 KB | 1 000 000 | 130 ms | 105 ms |
| 2000, `[H]` | 28 KB | 4 000 000 | **OutOfMemoryError** in `parse` after 1.7 s (Gradle's test JVM heap) |

The reviewer saw the 42 KB / N = 3000 file take 2.5 s to parse and the transposition OOM with a 2 GB heap. Even the
14 KB standard-notation file hands the viewer a million lines to lay out. The import and the library list are fine
(`summarize` never resolves recalls), so such a file is imported and listed normally and crashes or freezes the app
the moment it is opened — and on Android a restored back stack reopens it on every launch. The import side was
hardened against crafted input in the twelfth review, and `ChordProTabWrapper` budgets its output for this reason;
recalls have no bound.

## Fix

Bound the expansion where the structure is built, in `withChorusesRecalled`, so the transposer, the notation, the
viewer and the PDF layout all receive a model whose total size is linear in the file. The consumers need no change:
a recall with empty `blocks` already exists (a `{chorus}` before any chorus) and both of them draw it as its heading
alone (`SongLyrics.kt`: `header?.let { sections += RenderSection.Lines(header = it, …, parts = emptyList(), isOnCard = true) }`;
`PrintLayout.kt`: `.ifEmpty { header?.let { labelRows(it, width) }.orEmpty() }`). Such a heading is the recall's own
label or the default "Chorus", not the chorus's own label — acceptable for the crafted files that reach the cap.

**The budget is a weight, not a line count.** Counting lines alone is not enough: the cost of every later step is
per *character and chord*, and a chorus of one very long line is one line. Measured at HEAD with a probe: a chorus of
one line of 2 000 `[H]a` recalled 1 000 times (17 KB) takes 225 ms to parse and 231 ms to transpose; with 5 000 (29 KB)
`transpose` runs out of memory — and a cap of "1 000 lines plus two per own line" (1 002 here) lets all 1 000 recalls
through. So each piece is weighed by what it makes the later steps do:

```kotlin
/**
 * How much a song's recalls may repeat, in weight (see [recallWeight]): [RECALL_BUDGET_FLOOR] plus
 * [RECALL_BUDGET_PER_WEIGHT] times the weight of what the song writes down itself. …why: every later step (the
 * transposition, the notation, the viewer, the PDF) expands each recall on its own, so without it a small file of
 * recalls is millions of lines; the weight counts characters and chords and not only lines, since one long line
 * recalled a thousand times is as costly as a thousand lines…
 */
private const val RECALL_BUDGET_FLOOR = 64_000
private const val RECALL_BUDGET_PER_WEIGHT = 2
/** What a line, a comment or a break weighs before its characters: the cost of a row, however short. */
private const val RECALL_PIECE_WEIGHT = 8
```

with

```kotlin
private fun ChordProLine.recallWeight(): Int = RECALL_PIECE_WEIGHT + when (this) {
    is ChordProLine.Lyrics -> text.length + chords.sumOf { it.name.length }
    is ChordProLine.Tab -> text.length
    is ChordProLine.Grid -> tokens.size
    ChordProLine.Blank -> 0
}

private fun ChordProBlock.recallWeight(): Int = when (this) {
    is ChordProBlock.Section -> (label?.length ?: 0) + lines.sumOf { it.recallWeight() }
    is ChordProBlock.Comment -> RECALL_PIECE_WEIGHT + text.length
    is ChordProBlock.ChorusRecall -> RECALL_PIECE_WEIGHT + (label?.length ?: 0) // its own line, not what it repeats
    else -> RECALL_PIECE_WEIGHT
}
```

(use `Long` for the sums if the executor prefers; with the import's 8 MiB text limit an `Int` cannot overflow, since
each character weighs at most 2 and each line 8 more). In `withChorusesRecalled`, compute each chorus's weight once
where it is collected (store it next to the list, e.g. `Triple(lastIndex, blocks, weight)`), and before the final
`mapIndexed`:

```kotlin
var budget = RECALL_BUDGET_FLOOR + RECALL_BUDGET_PER_WEIGHT * blocks.sumOf { it.recallWeight() }
var chorusIndex = -1
return blocks.mapIndexed { index, block ->
    if (block !is ChordProBlock.ChorusRecall) return@mapIndexed block
    while (chorusIndex + 1 < choruses.size && choruses[chorusIndex + 1].first < index) chorusIndex++
    val (_, chorus, weight) = choruses.getOrNull(chorusIndex) ?: return@mapIndexed block.copy(blocks = emptyList())
    // A recall past the budget keeps its heading and repeats nothing, the way one with no chorus before it does.
    if (weight > budget) return@mapIndexed block.copy(blocks = emptyList())
    budget -= weight
    block.copy(blocks = chorus)
}
```

A recall that does not fit is left without its chorus whole — never cut in the middle — and a later, smaller chorus
may still fit. `parse` runs `withChorusesRecalled` (inside `parseAsWritten`) before `ChordProNotation.normalized`, so
the German / lowercase-minor / `♯` rewrite inside `parse` already sees the capped model.

**Why these numbers.** A realistic chorus line is 30–60 characters of lyrics and a few chords, weighing about 50–70;
a long live version's 40-line chorus recalled 20 times is about 40 × 61 × 20 ≈ 49 000, under the 64 000 floor alone,
and the song's own weight (its chorus and verses, doubled) adds 15 000–30 000 more. A songbook pasted into one file
gets twice its own weight in recalls, which choruses shorter than its body can never use up. So no real song loses a
recall. The crafted files: 3 000 lines of `[H]a` (chorus weight 30 000; with the 3 000 recall lines the song weighs 54 000)
get 64 000 + 2 × 54 000 = 172 000, i.e. five recalls (15 000 recalled lines) instead of 9 000 000; one line of 5 000
`[H]a` (weight 10 008; the song 18 008) gets about 100 000, i.e. nine recalls (45 000 chords) instead of 5 000 000. The worst case of the import's 8 MiB text limit stays a constant
factor (≤ 3×) of what the file costs to parse anyway — the same shape as `ChordProTabWrapper.wrap`'s budget.

Optional, not needed once the cap is in: memoizing the rewritten chorus per (identity of `recall.blocks`, offset) in
`rewriteChords` would keep recalls sharing one list after a rewrite. Its cost is bounded by the cap, so leave it out.

Update `chordpro/CLAUDE.md` where it describes `ChorusRecall` (the "A `ChorusRecall` carries the chorus it repeats
(`blocks`) …" sentence in the `model/` bullet): add that the recalls of a song together repeat at most a fixed
allowance plus twice the song's own size (counted in characters and chords as well as lines), past which a recall
carries no chorus and is drawn as its heading, and why.

## Tests

In `ChordProParserTest` (each without a timing assertion: without the fix the first two run out of memory):
- `recalls stop repeating once they would multiply the song`: `{soc}`, 3 000 lines of `[H]a` (the German case runs the
  normalization rewrite inside `parse`), `{eoc}`, 3 000 `{chorus}` lines; assert `parse` returns, the first recall
  carries the whole chorus, the last carries `emptyList()`, and the recalls together carry at most 20 000 lines. Then
  `ChordProTransposer.transpose(song, 2)` returns.
- `one long chorus line recalled many times stops repeating too`: `{soc}`, one line of 5 000 `[H]a`, `{eoc}`, 1 000
  `{chorus}` lines; the first recall carries the line, the last carries `emptyList()`, and `transpose(song, 2)` returns.
- `a long chorus recalled many times is repeated every time`: a 40-line chorus whose lines are each
  `"[G]Some words of a long chorus line that [C]go on and [D]on"` and 20 `{chorus}` lines; every recall carries all
  40 lines.

## Manual check

Import a file made of `{soc}`, 3 000 lines of `[H]a`, `{eoc}` and 3 000 `{chorus}` lines on Android; open it: the song
shows the chorus, five recalls of it and then 2 995 "Chorus" headings, without freezing; transpose it and export it as
a PDF. Open a demo song with `{chorus}` recalls and check each recall still shows the whole chorus.
