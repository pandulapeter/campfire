# Let a song's `{define}` of a chord outrank a `{chord}` diagram of it, since the spec's `{chord}` only shows a diagram where it stands

**Kind:** bug (spec compatibility)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/CLAUDE.md`

## Problem

The spec (https://www.chordpro.org/chordpro/directives-chord/, checked 2026-10-06): "This directive is similar to define but it only displays the chord immediately in the song where the directive occurs." — while of `{define}` it says "If it is an already known chord the new definition will overwrite the previous one." A `{chord}` is a diagram printed at one point (often an alternative voicing for one passage), not the song's definition of that chord.

Campfire reads both into one list, the last of a chord on an instrument winning (`ChordProParser.kt`, `MetadataBuilder.addDefinition`):

```kotlin
fun addDefinition(definition: ChordDefinition) {
    val index = definitions.indexOfFirst { it.name == definition.name && it.instrument == definition.instrument }
    if (index < 0) definitions += definition else definitions[index] = definition
}
```

Proved with a probe test at dac1d9d59: `parse("{define: G frets 3 2 0 0 0 3}\n{chord: G frets 3 x 0 0 3 3}\n[G]x").metadata.definitions` → only `G [3, null, 0, 0, 3, 3]`: the passing diagram replaces the song's G everywhere in the Chords section, the editor preview and the PDF. `ChordProDefinitions.rangeOf` (where the editor's Chord shape button sends the caret) likewise takes the last line of either kind ("the last one, which is the one that counts").

Campfire has no place to draw a diagram inline, so reading a `{chord}` shape at all is still the best use of it where the song has no `{define}` of that chord (many files use `{chord}` that way) — only the precedence is wrong.

## Fix

- In `ChordProParser.handleDirective`, pass whether the directive is a `define` (the part of `directive.name` before `-` is `ChordProDefinitions.DEFINE`) to `metadata.addDefinition`.
- In `MetadataBuilder`, keep a set of `(name, instrument)` pairs given by a `{define}`. `addDefinition(definition, isDefine)`: a `{define}` replaces whatever is there (as now, in the place of the first); a `{chord}` is added where nothing is there yet and replaces only another `{chord}`'s, never a `{define}`'s.
- In `ChordProDefinitions.rangeOf`, look for the last matching `{define}` line first and fall back to the last matching `{chord}` line, so the caret goes to the line that counts.
- Leave the text transposition alone: `{chord}` lines keep being renamed and moved with the song, as the Chords section draws them in the song's key (a settled decision about definitions).

Update the `ChordDefinition` KDoc ("A shape a song gives one of its chords, from a `{define}` or a `{chord}` directive") and `rangeOf`'s KDoc, and `chordpro/CLAUDE.md`: the parser paragraph ("a `{define}` or `{chord}` with a shape Campfire draws is read into `ChordProMetadata.definitions`") and the `ChordProDefinitions` paragraph ("The last shape of a chord on an instrument wins, in the place of the first") — "the last `{define}` of a chord on an instrument wins, in the place of the first; a `{chord}`, which the spec has show a diagram only where it stands, counts only where the song defines none".

## Tests

In `ChordProDefinitionsTest`, next to `the last shape of a chord on an instrument wins, in the place of the first`:

- `{define: G frets 3 2 0 0 0 3}\n{chord: G frets 3 x 0 0 3 3}` → G `[3, 2, 0, 0, 0, 3]`;
- `{chord: G frets 3 x 0 0 3 3}\n{define: G frets 3 2 0 0 0 3}` → the same, in the first place;
- `{chord: G frets 3 x 0 0 3 3}\n{chord: G frets 3 2 0 0 3 3}` → the last chord's;
- `{chord: C frets 0 0 0 3}` alone is still read (the existing first test);
- `rangeOf("{define: G frets 3 2 0 0 0 3}\n{chord: G frets 3 x 0 0 3 3}", "G", GUITAR)` is the range of `3 2 0 0 0 3`.

## Manual check

Open a song holding `{define: G frets 3 2 0 0 0 3}` and later `{chord: G frets 3 x 0 0 3 3}`: the Chords section draws G as the `{define}` gives it.
