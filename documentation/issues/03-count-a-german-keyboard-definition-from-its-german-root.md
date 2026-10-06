# Count a keyboard definition's keys from the root its chord has in the notation the song is read in, so a German `{define: B keys …}` plays a B flat

**Challenged:** amended — `renamedFromNotation` re-roots through a `keysShape` helper shared with `read` (so 01's key fold and 07's bass reading apply to the renamed keys) instead of shifting notes by hand; land after 01 and 07; added a German slash-chord test.
**Kind:** bug  ·  **Severity:** low-medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt` (cross-lane, lane C: one call in `normalized`), `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProDefinitions.read` turns a keyboard shape's relative keys into absolute ones with the **standard** reading of the name, while the song is still as its file (or field) spells it:

```kotlin
keys != null -> {
    if (selected != null && selected != ChordInstrument.KEYBOARD) return Reading.Invalid
    val root = ChordProChords.parse(name)?.root ?: 0
    val absolute = keys.map { root + it }
```

The parser calls it from `parseAsWritten` (`ChordProParser.kt`, `ChordProDefinitions.definitionOf(it, selector)`), and only afterwards does `ChordProNotation.normalized` rename the song into the standard notation — with `ChordProTransposer.rewriteChords(song, rewriteTabLines, rename)`, whose `ChordRewrite.rewriteDefinition` defaults to `{ it.copy(name = rename(it.name)) }`: the name moves, the absolute keys do not.

Proved with a probe test at dac1d9d59: `ChordProParser.parse("{define: B keys 0 4 7}\n[H]x [B]y")` and `ChordProParser.parse("{define: B keys 0 4 7}\n[B]y", ChordNotation.GERMAN)` both give `ChordDefinition(name=Bb, KEYBOARD, Keys(notes=[11, 15, 18]))` — a B major chord drawn under the name B flat. The editor's Chord shape button in German writes exactly that line (`ChordProDefinitions.line("B", Keys([10, 14, 17]), GERMAN)` → `{define: B keys 0 4 7}`, verified), and the preview parses the field with the reader's notation, so the shape just chosen is drawn a semitone off, and the song details screen's definition cells (`definitionCellsOf`, root `parse("B", GERMAN) = 10`) light no root. The file itself is right (the save converts the name to `Bb` and keeps the relative keys), so a German *file* shows it only where an `H` elsewhere makes it German. Latin names are not affected (`parse("Sol")` already reads G — verified).

## Fix

Options:

- **A (recommended): re-root the keys when the notation renames the definition.** Add to `ChordProDefinitions`:

  ```kotlin
  /**
   * [definition], read from a text in another notation, renamed by [rename] into the standard one: a keyboard's keys,
   * which [read] counted from the root the name has in the standard notation, counted from the root of its new name.
   */
  internal fun renamedFromNotation(definition: ChordDefinition, rename: (String) -> String): ChordDefinition
  ```

  It renames; a fretted shape is only renamed. For `ChordVoicing.Keys` it does not shift the voicing by hand: factor the end of `read`'s keyboard branch (from the relative keys to the `Keys` value: root, absolute notes, octave lift and whatever plans 01 and 07 add there — the fold into `MAX_KEY` and the slash chord's bass) into one private function `keysShape(name: String, keys: List<Int>): ChordVoicing.Keys`, which `read` calls with the keys as written, and have `renamedFromNotation` call it with the new name and the keys relative to the old root, `(listOfNotNull(voicing.bass) + voicing.notes).map { it - (ChordProChords.parse(definition.name)?.root ?: 0) }` — the same `?: 0` fallback `read` uses, so it undoes exactly what `read` assumed. So the re-rooted keys are bounded by 01's fold as `read`'s are, and 07's bass is decided by the new name: a German `{define: C/B keys 10 12 16 19}` (C over B flat, as the Chord shape button writes it) has a lowest key that is no B natural, so `read` cannot see its bass, while its new name `C/Bb` can. Then, in `ChordProNotation.normalized` (lane C's file), build the rewrite with it:

  ```kotlin
  return ChordProTransposer.rewriteChords(song) {
      ChordProTransposer.ChordRewrite(
          rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
          rename = rename,
          rewriteDefinition = { ChordProDefinitions.renamedFromNotation(it, rename) },
      )
  }
  ```

  Only `normalized` gets this: `toNotation` (display) and `convertText` (text, where keys are relative and stay as they are) must keep the plain rename.
- B: thread the notation into the parser's definition reading. It cannot be done soundly inside `parseAsWritten`, because whether a standard-declared file is German is only known once the whole file has been read (`isGermanNotated` looks for an `H` anywhere, and a definition may come before it), so it would still need a second pass.

If lane C's plan for `ChordProNotation` lands first, apply the three-line change on top of it; nothing else there needs to change.

Note in `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph that a keyboard's keys are counted from the root its chord has in the notation the text is read in.

## Tests

In `ChordProDefinitionsTest` `a keyboard shape is counted from the root`, add:

```kotlin
assertEquals(ChordVoicing.Keys(listOf(10, 14, 17)), ChordProParser.parse("{define: B keys 0 4 7}\n[H]x [B]y").metadata.definitions.single().voicing)
assertEquals(ChordVoicing.Keys(listOf(10, 14, 17)), ChordProParser.parse("{define: B keys 0 4 7}\n[B]y", ChordNotation.GERMAN).metadata.definitions.single().voicing)
assertEquals(ChordVoicing.Keys(listOf(11, 15, 18)), ChordProParser.parse("{define: H keys 0 4 7}\n[H]y", ChordNotation.GERMAN).metadata.definitions.single().voicing)
```

and a round trip: for `ChordVoicing.Keys(listOf(10, 14, 17))`, `ChordProParser.parse(ChordProDefinitions.line("B", it, ChordNotation.GERMAN) + "\n[B]x", ChordNotation.GERMAN)` reads back that voicing; and, with plan 07 landed, the same round trip for `"C/B"` and `ChordVoicing.Keys(listOf(12, 16, 19), bass = 10)` (C over B flat in German) reads back the bass.

Order: land after plans 01 and 07, which change the keyboard branch the helper is cut from.

## Manual check

Settings → Songs: German notation, Instrument: Keyboard. Open a song in a flat key with a `B` (B flat) chord in the editor, put the caret on it, pick a shape with the Chord shape button: the preview's diagram is the one picked, its root lit; after saving, the song details screen shows the same.
