# Bound the frets, the base fret and the keys a `{define}` may give, so a hostile or mistyped definition can no longer overflow or hang the diagrams

**Kind:** bug (robustness)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProDefinitions.read` (`ChordProDefinitions.kt`) bounds frets, the base fret and keys only from below:

```kotlin
BASE_FRET -> baseFret = arguments().singleOrNull()?.toIntOrNull()?.takeIf { it >= 1 } ?: return Reading.Invalid
FRETS -> frets = arguments().takeIf { it.isNotEmpty() }?.map { word ->
    if (word in mutedFrets) null else word.toIntOrNull()?.takeIf { it >= 0 } ?: return Reading.Invalid
} ?: return Reading.Invalid
…
KEYS -> keys = arguments().takeIf { it.isNotEmpty() }?.map { it.toIntOrNull() ?: return Reading.Invalid } ?: return Reading.Invalid
…
frets = frets.map { fret -> if (fret == null || fret == 0) fret else fret + baseFret - 1 },
…
val absolute = keys.map { root + it }
```

Proved with a probe test at dac1d9d59:

- `read("G frets 3 2 0 0 0 99999999")` → `Fretted(frets=[3, 2, 0, 0, 0, 99999999])`.
- `read("G base-fret 2147483647 frets 3 2 1 1 1 3")` → `Fretted(frets=[-2147483647, -2147483648, 2147483647, 2147483647, 2147483647, -2147483647])` — `Int` overflow, negative frets.
- `read("C keys 0 4 99999999")` → `Keys(notes=[0, 4, 99999999])`; `read("C keys -99999999 4 7")` → `Keys(notes=[9, 100000012, 100000015])`.

Every one of those is a `Shape`, so it reaches the song's Chords section, the editor preview and the PDF. The diagram geometry (`presentation/…/ui/chords/ChordDiagramGeometry.kt`, `fretCount = max - baseFret + 1`, one octave per 12 keys) then draws a line per fret or a key per semitone: tens of millions of draw calls per frame, a hang on the song details screen, in the editor's preview (as it is typed — the line is valid at every keystroke while the number grows) and in the export. A file that arrives through sync or an import does this on its own.

The ChordPro spec (https://www.chordpro.org/chordpro/directives-define/) says the base fret "must be 1 or higher", gives no upper bound for frets, and for keyboards: "keys that would exceed the diagram are silently wrapped".

## Fix

In `read`, in `ChordProDefinitions.kt`:

1. `BASE_FRET`: accept `1..MAX_FRET` (the existing `MAX_FRET = 24`), `Invalid` otherwise.
2. `FRETS`: accept a fret word `0..MAX_FRET` (muted words as now), `Invalid` otherwise. After the loop, where the absolute fret `fret + baseFret - 1` of any non-open, non-muted string exceeds `MAX_FRET`, the reading is `Invalid` — both operands are already bounded, so the addition cannot overflow. Same neck as `transposed` already assumes (`it + move in 0..MAX_FRET`).
3. `KEYS`: follow the spec and wrap rather than refuse. Add `private const val MAX_KEY = 47` (four octaves, the diagram's widest; keep it equal to whatever upper bound lane C gives `ChordVoicings.read`'s keyboard notes — see cross-lane note). Fold every key word outside `-24..MAX_KEY` into that range by pitch class before adding the root (`k > MAX_KEY` → `MAX_KEY - 11 + k.mod(12)`, `k < -24` → `-24 + k.mod(12)`; `Int.mod` cannot overflow), then compute `absolute` and the octave shift as now, and finally fold any note still above `MAX_KEY` into the top octave the same way (`MAX_KEY - 11 + note.mod(12)`) before `distinct().sorted()`. A `toIntOrNull()` that fails stays `Invalid`.

Keep the `Reading` KDoc's list of what is `Invalid` in step ("a fret off the neck", "a base fret past the last fret"), and the class KDoc.

In `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph ("`read` takes a definition's value: frets counted from the `base-fret` …"), add that frets and the base fret stay on a 24-fret neck (`Invalid` past it) and that keys past four octaves are wrapped by their note, as the spec says.

Do not touch `rewrittenLine`: an `Invalid` line is already left byte for byte by it (`if (reading == Reading.Invalid) return rawLine`), and the highlighter marks it from the same `read`.

## Tests

In `ChordProDefinitionsTest`, extend `what declares no shape is no definition, and what cannot be read is invalid` with `"G frets 3 2 0 0 0 25"`, `"G frets 3 2 0 0 0 99999999"`, `"G base-fret 25 frets 1 1 1 1 1 1"`, `"G base-fret 2147483647 frets 3 2 1 1 1 3"` and `"G base-fret 22 frets 1 3 3 2 1 4"` (absolute 25) → `Invalid`; and a new test `keys past the diagram are wrapped by their note` asserting `read("C keys 0 4 99999999")` and `read("C keys -99999999 4 7")` give a `Keys` whose every note is in `0..47` and whose pitch classes are those of the keys as written (`99999999.mod(12)` and so on), and that `read("G base-fret 22 frets 1 3 3 2 1 3")` (absolute 24) is still a `Shape`. Add the same overflowing line to the highlighter test so it is shown `INVALID`.

## Manual check

In the editor of any song, type `{define: G frets 3 2 0 0 0 99999999}` and `{define: C keys 0 4 99999999}` in the header with `[G]x [C]y` below: the preview stays responsive, the G line is underlined as invalid and the C diagram shows a wrapped key; the song details screen and an export of it open at once.
