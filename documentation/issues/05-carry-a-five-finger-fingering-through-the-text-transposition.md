# Carry a fingering that a move leaves needing a fifth finger through the text transposition, so transposing there and back no longer erases the file's fingering

**Kind:** bug (data loss, documented behaviour — needs the user's decision)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordDefinitionTransposerTest.kt`, `chordpro/CLAUDE.md`

## Problem

When a move stops open strings, `ChordProDefinitions`' private `ChordVoicing.Fretted.movedBy` makes them the first finger's barre and moves every other finger one on, then drops the whole fingering if any finger passes the hand's four:

```kotlin
stoppedNow.isNotEmpty() -> fingers.mapIndexed { string, finger -> if (string in stoppedNow) 1 else if (finger > 0) finger + 1 else 0 }
…
adjusted.takeIf { it.all { finger -> finger in 0..MAX_HAND_FINGER } }
```

`rewrittenLine` uses the same move for the editor's Transpose and, where the fingering is gone, removes the `fingers` keyword and its values from the file ("a fingering the move leaves out goes with its keyword", pinned by the test of that name in `ChordDefinitionTransposerTest`). Any open shape whose fingering already uses the little finger loses it on the first step up, and moving back cannot bring it back. Proved with a probe test at dac1d9d59:

- `transposeText("{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}\n[G]x", 1)` → `{define: Ab frets 4 3 1 1 1 4}`;
- transposing that by −1 → `{define: G frets 3 2 0 0 0 3}` — the user's fingering is permanently erased from their file by a there-and-back that should be a no-op (and the next sync run spreads that).

Moving `4 3 1 1 1 4` with fingers `4 3 1 1 1 5` back down does recover `3 2 0 0 0 4` exactly (one finger released, the others move one back), so the information is only lost because the text drops it.

## Fix

Options:

- **A (recommended): let the text keep a fingering up to the spec's own range of five.** Give the move a finger limit: `movedBy(move, maxFinger)`, with `transposed` (the model, what the Chords section draws) passing `MAX_HAND_FINGER` as now — so on the page a moved shape that needs a fifth finger still draws no fingering and still gives way to the player's shape, as root `CLAUDE.md` says — and `rewrittenLine` passing `MAX_FINGER` (5, what `read` already accepts). `rewrittenLine` currently calls `transposed(...)`; route it through a private function taking the limit instead. The `released.size > 1` case still drops the fingering in both (it cannot be undone), so the "goes with its keyword" code stays.
- B: leave the whole line unchanged (name included) when its fingering cannot be carried. Never loses data, but the definition then stops applying to the transposed chord at all, which is a bigger visible change than a `5` in a fingering.
- C: keep today's behaviour (documented and pinned): drop the plan.

With A, a definition the editor moved reads back with a `5` among its fingers and `movedBy == 0`, so the diagram for that file shows the fingering as written, a 5 included — which is what the file says.

Update `rewrittenLine`'s KDoc ("a fingering the move leaves out goes with its keyword") to say the text keeps a fingering up to five fingers and drops only one it cannot carry back, `transposed`'s ("A shape that would need a fifth finger keeps no fingering") to say that is the model, and `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph ("the fingering following a barre coming or going (and left out where it would need a fifth finger or several fingers came to rest open)") the same way.

## Tests

In `ChordDefinitionTransposerTest`:

- change `a fingering the move leaves out goes with its keyword`: the existing C example now keeps its fingering — `"{define: D frets 2 5 4 2 3 5 fingers 1 4 3 1 2 5}"` from `transposeText("{define: C frets 0 3 2 0 1 3 fingers 0 3 2 0 1 4}", 2)` — and the keyword still goes where several fingers come to rest open: `transposeText("{define: C# frets x 1 1 2 3 x fingers 0 1 2 3 4 0}", -1)` == `"{define: C frets x 0 0 1 2 x}"` (verified at dac1d9d59);
- add `"{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}"` to `there and back in the text is the line it started as`;
- `ChordProTransposer.transpose(parse("{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}\n[G]x"), 1)`'s definition still has `fingers == null` (the model is unchanged).

## Manual check

In the editor, write `{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}` and `[G]la`, Transpose up once and down once: the line is back as written, fingering included.
