# Read the finger values (`1`–`9`, `A`–`Z`, anything else ignored) and the `base_fret` spelling the ChordPro spec allows instead of rejecting the definition

**Kind:** bug (compatibility)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordDefinitionTransposerTest.kt`, `chordpro/CLAUDE.md`

## Problem

The spec (https://www.chordpro.org/chordpro/directives-define/, checked 2026-10-06) says: "Finger settings may be numeric (`1` .. `9`) or uppercase letters (`A` .. `Z`). All other values are ignored." and "values corresponding to open or damped strings are ignored". Its own "most common use" example is `{define: A frets 0 0 2 2 2 0 base_fret 1}`, with an underscore.

`ChordProDefinitions.read` instead refuses every finger that is not `0..5` or one of `- x X N`, and knows only `base-fret`:

```kotlin
FINGERS -> fingers = arguments().takeIf { it.isNotEmpty() }?.map { word ->
    if (word in unusedFingers) 0 else word.toIntOrNull()?.takeIf { it in 0..MAX_FINGER } ?: return Reading.Invalid
} ?: return Reading.Invalid
…
private const val BASE_FRET = "base-fret"
private val keywords = setOf(BASE_FRET, FRETS, FINGERS, KEYS, COPY, COPY_ALL, "display", "format", "diagram")
```

Proved with a probe test at dac1d9d59: `read("F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1")` (a thumb over the bass string, as guitarists write it) → `Invalid`; `read("F frets 1 3 3 2 1 1 fingers 9 3 4 2 1 1")` → `Invalid`; `read("A frets 0 0 2 2 2 0 base_fret 1")` → `Invalid` (the `base_fret` word is read as a seventh fret). Such lines from other apps' files draw nothing and are marked as errors in the editor.

## Fix

In `ChordProDefinitions.kt`:

1. `read`'s `FINGERS`: a number `1..MAX_FINGER` is that finger, and **every other word** — `0`, `-`, `x`, a letter, `6`–`9`, anything — is a string with no finger shown (`0`), never `Invalid`. Only the count must still match the frets (`fingers.size != frets?.size` → `Invalid`), which the spec also requires. `unusedFingers` then only serves `rewrittenLine`.
2. `base_fret` is read as `base-fret`: add it to `keywords` and treat both spellings as one keyword in `read` (`BASE_FRET, "base_fret" ->`), in `rewrittenLine` (its two `lowercase() == BASE_FRET` comparisons become a membership test of a `baseFretKeywords` set) and in plan 02's repetition check if that has landed (`base-fret` and `base_fret` together are a repetition).
3. `rewrittenLine` must not rewrite a finger word the move did not change, now that several words read as `0`. Replace

   ```kotlin
   if (fingers[string] > 0 || value.substring(words[word]) !in unusedFingers) replacements[word] = fingers[string].toString()
   ```

   with a rewrite only where the finger the line reads for that string changed: `if (fingers[string] != voicing.fingers?.get(string)) replacements[word] = fingers[string].toString()`. A `T` on a string that keeps no finger stays `T`, a `-` stays `-`, as today.

Update the class KDoc and `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph ("frets counted from the `base-fret` (`x`, `X`, `N` and `-1` muted), fingers, …"): `base_fret` is read too, and a finger that is no number Campfire draws is shown as none.

## Tests

- `ChordProDefinitionsTest`: `read("F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1")` is a `Shape` with fingers `[0, 3, 4, 2, 1, 1]`; same for `fingers 9 3 4 2 1 1`; `read("A frets 0 0 2 2 2 0 base_fret 1")` is the A shape `[0, 0, 2, 2, 2, 0]`; `read("A base_fret 5 frets x 1 3 3 3 1")` has frets `[null, 5, 7, 7, 7, 5]`; `fingers 1 2 3` against six frets is still `Invalid`.
- `ChordDefinitionTransposerTest`: `transposeText("{define: F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1}", 2)` == `"{define: G frets 3 5 5 4 3 3 fingers T 3 4 2 1 1}"`, and `transposeText("{define: A base_fret 5 frets x 1 3 3 3 1}", 2)` == `"{define: B base_fret 7 frets x 1 3 3 3 1}"`.

## Manual check

Import a ChordPro file with `{define: F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1}` and `[F]la`: the song details' Chords section draws that F with fingers on the other five strings, and the editor does not mark the line.
