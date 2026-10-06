# Transpose the chords in a tab or grid environment's label on the model, as the text transposition does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`

## Problem

Inside a running section the parser stores a tab or grid environment's label on each of its lines
(`ChordProLine.Tab.label`, `ChordProLine.Grid.label`), which the viewer names the run's fold by (`SongLyrics.kt`
`environmentLabel`) and the PDF prints. The model rewrite never touches it (ChordProTransposer.kt at 8ee010b36):

```kotlin
// rewriteLines, :175
rewritten += run.map { line -> if (line is ChordProLine.Tab) line.copy(text = tabLines.next()) else line }
// rewriteLine, :410
is ChordProLine.Grid -> line.copy(tokens = line.tokens.map { … })   // label kept
```

while the text path rewrites the chords of every `{start_of_…}` value (`ChordProSyntax.hasChordsInValue`), and
`rewriteBlock` rewrites a section's label. Verified with a probe test on
`{start_of_verse: V}` … `{start_of_tab: Riff [G]}` … `{start_of_grid: Bar [C]}` …: `transposeText(…, 2)` writes
`Riff [A]` / `Bar [D]`, `transpose(parse(…), 2)` keeps `Riff [G]` / `Bar [C]`. The same path serves
`ChordProNotation.toNotation`, so a German reader's viewer shows `[B]` where the editor shows `[H]`, and
`normalized` leaves an `[H]` or `♯` there unconverted.

## Fix

Rewrite the label with the same rename the lines get, as `rewriteBlock` does for `Section.label`:

```kotlin
// rewriteLines, flushRun()
rewritten += run.map { line ->
    if (line is ChordProLine.Tab) {
        line.copy(text = tabLines.next(), label = line.label?.let { rewriteLyricsLineChords(it, rename) })
    } else {
        line
    }
}
// rewriteLine
is ChordProLine.Tab -> line.copy(label = line.label?.let { rewriteLyricsLineChords(it, rename) })   // unreachable today
is ChordProLine.Grid -> line.copy(
    tokens = …unchanged…,
    label = line.label?.let { rewriteLyricsLineChords(it, rename) },
)
```

(`rewriteLines` passes `rename` to `flushRun` already in scope.) Every line of one environment has the same label, so
they stay equal after the rewrite, and `PrintLayout`'s run boundary (`previous.label != line.label`) is unaffected. The
labels stay out of `writtenChordNames`, as the KDoc of `rewriteBlock` says comments and labels do.

## Tests

In `ChordProTransposerTest`: `a tab or grid label inside a section is transposed like the text`, the probe's text
above, asserting the labels of `transpose(parse(text), 2)` are `Riff [A]` and `Bar [D]` and that `parse(transposeText(text,
2))` has the same labels. In `ChordProNotationTest`: `toNotation(…, GERMAN)` turns a `{start_of_tab: Riff [B]}` label
inside a verse into `Riff [H]`.

## Manual check

Write a verse with `{start_of_tab: Riff [G]}` inside it, transpose it up two on the song details screen: the tab's
fold heading reads `Riff [A]`, as the editor's transposed text does.
