# Split ChordVoicings into shape geometry, the shape search and a cached facade with a test reset

**Kind:** testability  ·  **Severity:** low  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/.../chordpro/ChordVoicings.kt` (object `ChordVoicings`: `defaults`, `default`, `needsSearch`, `all`, `read`, `write`, `baseFret`, `fingerCount`, `isHoldable`, `pitchClasses`, private `table`, `TableShape`, `readTable`, `tableShapes`, `playedOn`, `shiftsOf`, `shiftedBy`, `position`, `search`, `omissions`, `stretch`, `compareFrets`, `enumerate`, `keyboard`, constants); new `ChordShapeGeometry.kt`, `ChordVoicingSearch.kt` (both internal), optionally `ChordVoicingFormat.kt`; `ChordProDefinitions.kt` (calls `ChordVoicings.baseFret`, `isHoldable`); tests `ChordVoicingsTest.kt`, `ChordVoicingTablesTest.kt`, `ChordDefinitionTransposerTest.kt`, `desktopTest/.../ChordVoicingContactSheetTest.kt`; `presentation/src/commonTest/.../ui/chords/SongChordsTest.kt` (read only, see Tests); `chordpro/CLAUDE.md`
**Depends on:** none (land after the :chordpro split lane, which narrows `fingerCount` / `pitchClasses` to internal)

## Problem

`ChordVoicings` (305 lines) is four things in one object:

- the storage format of a player's chosen shape (`read` / `write`: `"x 3 2 0 1 0"`, `"4 7 12 / 0"`);
- hand geometry (`baseFret`, `fingerCount`, `isHoldable`, `pitchClasses`, private `stretch`, `position`);
- the table lookup and the exhaustive search (`tableShapes`, `search`, `omissions`, `enumerate`, `compareFrets`, `keyboard`);
- a process-wide cache:
  ```kotlin
  @Volatile
  private var defaults: Map<Pair<Chord, ChordInstrument>, ChordVoicing?> = emptyMap()
  …
  fun needsSearch(chord: Chord, instrument: ChordInstrument): Boolean =
      instrument != ChordInstrument.KEYBOARD && (chord to instrument) !in defaults && tableShapes(chord, instrument).isEmpty()
  ```

Because the cache is global and never reset, `needsSearch` depends on which test ran first in the JVM. The suites already
work around it by picking a chord no other test touches: `ChordVoicingsTest` uses `Dbmaj9#11/Ab`, `SongChordsTest` in
`:presentation` uses `Ebmaj9#11/Bb` and `Fbmaj9#11/Cb`. A new test reusing one of those chords, or a test calling
`default` for it earlier, turns the assertion `assertTrue(ChordVoicings.needsSearch(unusual, ChordInstrument.GUITAR))`
order-dependent. The geometry helpers cannot be read or tested without scrolling past the search.

(The finding also called `ChordProDefinitions.shapeOf` "a second grammar for the same shapes". It is not: `shapeOf`
writes ChordPro's own `{define}` syntax — `base-fret 1 frets x 3 2 0 1 0 fingers …`, `keys 0 4 7` relative to the root —
while `ChordVoicings.write` is Campfire's compact preference format. Both are needed; they only share `baseFret`. Leave
`shapeOf` alone.)

## Fix

1. **Add `internal fun resetCache()`** on `ChordVoicings` (sets `defaults = emptyMap()`), and call it from a `@BeforeTest`
   in `ChordVoicingsTest`. One commit; nothing else changes.

2. **Extract `internal object ChordShapeGeometry`** with `baseFret`, `fingerCount`, `isHoldable`, `pitchClasses`,
   `stretch`, `position` and the constants they use (`DIAGRAM_FRETS`, `OPEN_POSITION_FRETS`, `MAX_FINGERS`,
   `MAX_HOLDABLE_FRET`). `ChordVoicings.baseFret` stays public as a one-line delegate (`:presentation`'s
   `ChordDiagramGeometry` and `SongChordsSection` call it); `isHoldable` / `fingerCount` / `pitchClasses` delegate too or
   callers inside `:chordpro` (`ChordProDefinitions`, the tests) switch to `ChordShapeGeometry`. Move the geometry
   assertions of `ChordVoicingsTest` (`fingerCount`, `isHoldable`, `baseFret`) into a `ChordShapeGeometryTest`.

3. **Extract `internal object ChordVoicingSearch`** with `table`, `TableShape`, `readTable`, the two lazy tables,
   `tableShapes`, `playedOn`, `shiftsOf`, `shiftedBy`, `search`, `omissions`, `compareFrets`, `enumerate`, `keyboard`
   and their constants (`MAX_POSITION`, `MAX_TABLE_POSITION`, `MAX_KEYS`, …). It is stateless. `ChordVoicings`
   keeps `default`, `needsSearch`, `all` (each now a thin wrapper that consults the cache and calls the search), the
   cache, and `read` / `write` (which calls `ChordVoicingSearch.tableShapes` for the fingering lookup).

4. *(optional, recommended only if step 3 leaves `ChordVoicings` mixed)* move `read` / `write` and `MUTED`,
   `BASS_SEPARATOR`, `MAX_FRET`, `MAX_KEY` into `internal object ChordVoicingFormat`, with the public `ChordVoicings.read`
   / `write` delegating — `:presentation` (`ChordSelection`, `ChordShapeInsertion`, `SongChordsSection`) and
   `UserPreferences`' docs name them.

The public surface of `ChordVoicings` is unchanged throughout; only `:chordpro`-internal call sites move.

## Tests

- `ChordVoicingsTest`: `@BeforeTest fun reset() = ChordVoicings.resetCache()`; the `needsSearch` test can then use `G7#9`-like
  chords freely. New `ChordShapeGeometryTest` for the moved geometry assertions.
- `ChordVoicingTablesTest` (every table shape checked against its chord), `ChordDefinitionTransposerTest`,
  `ChordVoicingContactSheetTest` (desktopTest) and `:presentation`'s `SongChordsTest` guard behaviour. `SongChordsTest`
  cannot call the internal reset; leave its unique chord as it is and say so in a comment there.
- `./gradlew :chordpro:desktopTest :presentation:desktopTest`.

## Manual check

none — covered by tests.
