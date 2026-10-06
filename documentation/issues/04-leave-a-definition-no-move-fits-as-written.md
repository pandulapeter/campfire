# Leave a fretted definition that no move along the neck fits as it is written, rather than renaming it over its old frets

**Challenged:** amended — "no move fits" must come from a null-returning helper shared with `transposed` (and with plan 05's limit-taking function), not from comparing voicings, which would stop renaming an all-muted definition; added its test.
**Kind:** bug  ·  **Severity:** low-medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordDefinitionTransposerTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProDefinitions.transposed` gives up when every candidate move would take a fret off the 24-fret neck, but still renames the definition:

```kotlin
is ChordVoicing.Fretted -> {
    val candidates = (if (voicing.frets.any { it == 0 }) listOf(shift) else listOf(shift - 12, shift))
        .filter { move -> voicing.frets.all { it == null || it + move in 0..MAX_FRET } }
    val move = candidates.firstOrNull { ChordVoicings.isHoldable(voicing.frets.map { fret -> fret?.plus(it) }) }
        ?: candidates.firstOrNull()
        ?: return definition.copy(name = name)
```

So the chord's new name is put on the old shape. Proved with a probe test at dac1d9d59 for a high E shape with open strings (it can only move up, and 14 + 11 is off the neck):

- the model: `ChordProTransposer.transpose(parse("{define: E frets 0 x 14 13 12 0}\n[E]x"), -1)` → `ChordDefinition(name=Eb, frets=[0, null, 14, 13, 12, 0], movedBy=0)`. With `movedBy == 0` `songChordsOf` (`presentation/…/ui/chords/SongChords.kt`) takes it as the song's own shape, so the reader transposing down a semitone sees an **E** chord drawn under the name E♭ in the Chords section;
- the text: `transposeText("{define: E frets 0 x 14 13 12 0}\n[E]x", -1)` → `{define: Eb frets 0 x 14 13 12 0}\n[Eb]x` — the editor's Transpose writes a false definition into the file, which every device then reads.

## Fix

A shape that cannot be moved is not the new chord's, so it is left alone:

- in `transposed`, `?: return definition` (not renamed, `movedBy` unchanged). The song's chords move on without it: `songChordsOf` looks a definition up by the chord's name or notes, so the moved chord gets the player's or the app's shape, and the untouched definition only applies where the transposed song plays its chord again, where it is right;
- in `rewrittenLine`, return `rawLine` unchanged when the line holds a fretted shape, `semitones.mod(12) != 0` and no move fits. Decide that with a private helper that both `transposed` and `rewrittenLine` use and that returns null for "no move fits" — **not** by comparing the result's voicing with the original: a shape of muted strings only (`{define: G frets x x x x x x}`, which `read` takes) is unchanged by every move, and must keep being renamed with the song. If plan 05 has landed, this helper is the private function it routes `rewrittenLine` through (the one taking the finger limit); give it the nullable result there. The name must not be renamed either: a `{define: Eb …}` over E frets is the bug. This line cannot have the "there and back" property, before or after the fix: moving it back by the opposite amount moves the untouched `E` shape up (to `{define: F frets 1 x 15 14 13 1}`), just as today it moves the wrongly named one; what the fix buys is that no step ever writes a shape under a name it does not play.

Extend `transposed`'s KDoc ("…one that would take a fret off the neck is out…") with "and a shape no move keeps on the neck stays as it is, named as it was", `rewrittenLine`'s ("A line that cannot be read is left byte for byte…") with "and so is one whose shape no move keeps on the neck", and `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph ("`transposed` moves one with the song: …") the same.

## Tests

In `ChordDefinitionTransposerTest`:

- `assertEquals("{define: E frets 0 x 14 13 12 0}\n[Eb]x", ChordProTransposer.transposeText("{define: E frets 0 x 14 13 12 0}\n[E]x", -1))`;
- `ChordProTransposer.transpose(ChordProParser.parse("{define: E frets 0 x 14 13 12 0}\n[E]x"), -1).metadata.definitions.single()` equals the parsed definition (name `E`, frets unchanged, `movedBy` 0);
- leave that line out of `there and back in the text is the line it started as`, for the reason given under Fix;
- a shape of muted strings is still renamed: `transposeText("{define: G frets x x x x x x}\n[G]x", 2)` == `"{define: A frets x x x x x x}\n[A]x"`.

## Manual check

In the editor, write `{define: E frets 0 x 14 13 12 0}` and `[E]la`, Transpose down once: the line stays `{define: E …}` while the chord becomes `[Eb]`, and the preview's E♭ diagram is an ordinary E♭ shape rather than the E one.
