# Read a definition that repeats `frets`, `fingers`, `base-fret` or `keys` as invalid, so the editor's transpose can no longer crash on it or leave a stale value behind

**Kind:** bug (crash)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordDefinitionTransposerTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProDefinitions.read` assigns each keyword's arguments in a loop, so a repeated keyword silently keeps the **last** value:

```kotlin
while (index < words.size) {
    when (words[index++].lowercase()) {
        BASE_FRET -> baseFret = …
        FRETS -> frets = …
        FINGERS -> fingers = …
        KEYS -> keys = …
```

`rewrittenLine` (the text transposition behind the editor's Transpose, `ChordProTransposer.transposeText`) rewrites the **first** occurrence:

```kotlin
fun argumentsOf(keyword: String) = keywordIndices.firstOrNull { value.substring(words[it]).lowercase() == keyword }?.let { start -> … }
argumentsOf(FRETS)?.forEachIndexed { string, word ->
    val fret = moved.frets[string]
```

Proved with a probe test at dac1d9d59:

- `transposeText("{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}\n[G]x", 1)` → **throws `IndexOutOfBoundsException: Index 6 out of bounds for length 6`** (the first `frets` list has seven words, `moved.frets` six). In the editor that is the Transpose action crashing the app.
- `transposeText("{define: A base-fret 3 frets x 1 3 3 3 1 base-fret 5}\n[A]x", 2)` → `{define: B base-fret 7 frets x 1 3 3 3 1 base-fret 5}`, which reads back as the **unmoved** A shape `[null, 5, 7, 7, 7, 5]` named B.
- `transposeText("{define: G frets 3 2 0 0 0 3 fingers 1 2 3 4 1 2 3 fingers 3 2 0 0 0 4}\n[G]x", 1)` → `{define: Ab frets 4 3 1 1 1 4 fingers 3 2 0 0 0 4}`: the first fingering was removed, the stale second one now fingers the moved shape.

## Fix

Two options were proposed:

- **A (recommended): a definition that gives `base-fret`, `frets`, `fingers` or `keys` more than once is `Reading.Invalid`.** In `read`, keep a `mutableSetOf<String>()` of the value keywords seen and `return Reading.Invalid` when one comes a second time (`display`, `format`, `diagram`, `copy` and `copyall` are not counted — they are either ignored or make the reading `Other`). Nothing else changes: `rewrittenLine` already returns an `Invalid` line byte for byte (`if (reading == Reading.Invalid) return rawLine`), so the crash and both stale-value cases are gone by construction; `ChordProHighlighter` already marks a line `read` calls `Invalid` as `INVALID`, so the author sees the line in red and can fix it; `rangeOf` already skips a line that is no `Shape`.
- B: make `argumentsOf` take the last keyword (`lastOrNull`) and guard `moved.frets[string]` / `fingers[string]` with `getOrNull`. It keeps drawing such lines, but leaves a dead first value in the user's text that nothing reads and the transposition never moves, which is exactly the confusion the editor's "a second line of what is said once" marking exists to prevent elsewhere.

A is recommended because a line that says one thing twice is ambiguous to its reader too, it fixes the crash without a second code path that has to agree with `read`, and the spec documents redefining a property only after `copy` (`{define: Am7 copy Am7 frets x 0 2 0 1 3}`), which Campfire reads as `Other` either way. Drop the plan for B only if the user wants such lines to keep drawing.

Update the `Reading.Invalid` KDoc ("a fret that is no number, `frets` with nothing after it, fingers that do not match" + "a value given twice") and `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph ("a `copy`, a `display` alone or a string count no instrument has is `Other`, and what cannot be read `Invalid`") to name a repeated value as `Invalid`, and the highlighter paragraph's list ("a fret that is no number, fingers that do not match the frets, …").

## Tests

- `ChordProDefinitionsTest` `what declares no shape is no definition, and what cannot be read is invalid`: add `"G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3"`, `"A base-fret 3 frets x 1 3 3 3 1 base-fret 5"`, `"G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4 fingers 3 2 0 0 0 4"` and `"C keys 0 4 7 keys 0 3 7"` → `Invalid`; assert `"G copy G7 frets 3 2 0 0 0 3"` is still `Other`.
- `ChordDefinitionTransposerTest` `a line that cannot be read is left byte for byte`: add `transposeText("{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}\n[G]x", 1)` == `"{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}\n[Ab]x"` (no exception), and the same for the repeated `base-fret` line moved by 2.

## Manual check

In the editor, type `{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}` and `[G]la` and use Transpose up: the app does not crash, the line is shown as invalid and stays as typed while `[G]` becomes `[Ab]`.
