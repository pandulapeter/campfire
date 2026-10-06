# Build a song page's first model without the chord shape search, and fill in the searched shapes away from the main thread

**Challenged:** amended — corrected the "nothing is laid out again" claim (the Chords section is measured and the page placed again, at the same size, as for a chosen shape), added the `SongChord.defaultShape` KDoc, the editor preview and the cancellation reasoning, made the test's chord unique to it, and stated the empty-frame trade-off as the user's decision D-30.

**Kind:** performance  ·  **Severity:** medium (hostile songs: a frozen frame of up to a second) / low (ordinary songs: 5–43 ms per page on the desktop, several times that on a phone)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicings.kt` (one function, cross-lane: lane C's plan 11 owns the file), `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicingsTest.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`, `presentation/CLAUDE.md`, `chordpro/CLAUDE.md`

## Problem

The song details page builds its first model in composition, on the main thread, so that it never opens on an empty
frame (`SongLyrics.kt`, `rememberSongLyricsModel`):

```kotlin
val latestPrepare by rememberUpdatedState(prepare)
val state = remember { mutableStateOf(inputs to prepare(inputs)) }
LaunchedEffect(inputs) {
    if (state.value.first != inputs) state.value = inputs to withContext(Dispatchers.Default) { latestPrepare(inputs) }
}
```

`prepare` is `prepareSongLyrics(...)` (`SongDetailsScreen.kt`, the lambda handed to `rememberSongLyricsModel`), which
collects the Chords section's chords in the same pass:

```kotlin
chords = if (shouldShowChords && chordInstrument != null) {
    songChordsOf(standardSong, notation, chordInstrument, standardSong.metadata.capo ?: 0)
} else {
    emptyList()
},
```

and `songChordsOf` (`ui/chords/SongChords.kt`) asks for every chord's default shape:

```kotlin
defaultShape = if (definition == null) ChordVoicings.default(sounding, instrument) else null,
```

`ChordVoicings.default` is a table lookup for the chords the tables hold and **the full shape search** for every
other fretted chord (`tableShapes(chord, instrument).firstOrNull() ?: search(chord, instrument).firstOrNull()`). The
live desktop review measured the first composition of a 48-chord song of unusual chords on the guitar at 491–604 ms
of main thread time (a 600–1000 ms gap between frames), and 5–43 ms per page for ordinary songs; the pager composes
the neighbouring pages too, and a phone is several times slower. The same happens on every page swiped to for the
first time. `presentation/CLAUDE.md` says the opposite ("`prepareSongLyrics` collects a song's chords away from the
main thread with the rest of the model"), which is true only of the later builds.

Lane C's plan 11 (`11-bound-the-fretted-shape-search-and-cache-the-default-shape.md`) prunes the search and caches
`default`, which makes a repeated chord, a notation change and a swipe back cheap. It cannot make the *first* sight
of an unusual chord cheap, and that one still lands in composition.

## Fix

Keep the first model synchronous (the page must be right on its first frame, see the no-incidental-animations rule
in the root `CLAUDE.md`), but leave the search out of it; fill the searched shapes in on `Dispatchers.Default` right
after, without rebuilding the sections.

1. **`:chordpro`, cross-lane (coordinate with lane C; land after plan 11).** Add to `ChordVoicings`:
   ```kotlin
   /**
    * Whether [default] would have to run the search for [chord] on [instrument]: a fretted chord the tables do not
    * hold and nothing has looked for yet this session. What builds a page in a frame asks this first.
    */
   fun needsSearch(chord: Chord, instrument: ChordInstrument): Boolean =
       instrument != ChordInstrument.KEYBOARD && (chord to instrument) !in defaults && tableShapes(chord, instrument).isEmpty()
   ```
   (`defaults` is plan 11's cache. If plan 11 has not landed, leave that clause out.)
2. **`SongChord`** (`SongChords.kt`) gets `val isShapePending: Boolean = false` (KDoc: "the default shape has not been
   looked for yet, see [songChordsOf]'s `searchesShapes`; drawn as the empty frame until it has").
   **`songChordsOf`** gets a parameter `searchesShapes: Boolean = true`; in its `map`:
   ```kotlin
   val isShapePending = definition == null && !searchesShapes && ChordVoicings.needsSearch(sounding, instrument)
   ...
   defaultShape = if (definition == null && !isShapePending) ChordVoicings.default(sounding, instrument) else null,
   isShapePending = isShapePending,
   ```
   and a new function next to it:
   ```kotlin
   /** [chords] with the shapes [songChordsOf] left to be looked for filled in: the search, so never on the main thread. */
   internal fun List<SongChord>.withSearchedShapes(instrument: ChordInstrument) =
       if (none { it.isShapePending }) this else map { if (it.isShapePending) it.copy(defaultShape = ChordVoicings.default(it.chord, instrument), isShapePending = false) else it }
   ```
   The Chord shapes sheet (which collects its chords on `Dispatchers.Default`) and the PDF (`printChordsOf`, part of
   the export's layout) keep the default `searchesShapes = true`, as today.
3. **`prepareSongLyrics`** gets `searchesShapes: Boolean = true`, passed on to `songChordsOf`. **`SongLyricsModel`**
   gets `val chordInstrument: ChordInstrument? = null` (set from `prepareSongLyrics`'s `chordInstrument`) and
   ```kotlin
   /** Whether some chord's shape is still to be looked for, see [withSearchedShapes]. */
   val hasPendingShapes get() = chords.any { it.isShapePending }

   /** This model with its pending shapes found: the same song and the same sections, so only the Chords section changes. */
   fun withSearchedShapes() = SongLyricsModel(song, sections, isCut, shouldShowChords, notation, chordInstrument?.let { chords.withSearchedShapes(it) } ?: chords, chordInstrument)
   ```
   Keeping the very same `sections` list matters: `SongLyrics`' `remember(model.sections, …)` keys stay equal
   (`withMetadataSection` is not run again, `SongDetailsScreen`'s `timings` stay), and only the `cells`
   (`remember(model, …)`) and with them the Chords section change. That is not "nothing laid out": `withChordsSection`
   makes a new list, so `units`, `sectionMeasurements` (the pool re-measures the Chords section alone, every other
   section's size is reused by content) and the section animations are rebuilt and the page is placed again — exactly
   the path choosing a shape in the sheet already takes. The Chords section's measured size does not change (step 5)
   and it is never cut, so every section lands where it was and `onRowsPlaced` reports the same rows.
4. **`rememberSongLyricsModel`** takes `prepare: (SongLyricsInputs, searchesShapes: Boolean) -> SongLyricsModel` and
   becomes:
   ```kotlin
   val state = remember { mutableStateOf(inputs to prepare(inputs, false)) }
   LaunchedEffect(inputs) {
       val (builtFrom, model) = state.value
       state.value = when {
           builtFrom != inputs -> inputs to withContext(Dispatchers.Default) { latestPrepare(inputs, true) }
           model.hasPendingShapes -> inputs to withContext(Dispatchers.Default) { model.withSearchedShapes() }
           else -> return@LaunchedEffect
       }
   }
   ```
   Update its KDoc: the first model is built in place without the search, and its searched shapes follow from
   `Dispatchers.Default`. In `SongDetailsScreen.kt` the lambda passes the flag on:
   `{ inputs, searchesShapes -> … prepareSongLyrics(…, searchesShapes = searchesShapes) }`.
5. Nothing else changes in the cells: `chordCellsOf` → `selectShape` already falls back to `chord.defaultShape`, and
   `ChordCellContent` draws `emptyChordDiagramGeometryOf(cell.instrument)` for a null shape. The cell's size is fixed by
   `FRETTED_WIDTH`/`FRETTED_HEIGHT` whatever the geometry, and every searched shape is drawn in four frets anyway (the
   search rejects a stretch of four frets or more, so `fretCount` is `MIN_FRETS`), so the dots appearing move nothing.
   They appear without an animation: this is data arriving, which the convention says not to narrate. For that frame
   or two the cell's spoken description is the "no diagram" one (`chordCellDescription`), which is acceptable for the
   time of a search. Update `SongChord.defaultShape`'s KDoc ("worked out with the rest of the song away from the main
   thread" is no longer true of a page's first model): "…or null while [isShapePending]". A player's
   stored shape or the song's definition is never pending (`selectShape` takes those first), and the keyboard never
   searches.

Threading and lifecycle: the effect is keyed on `inputs`, so an input that changes while the search runs cancels it;
the search itself is not cooperative and finishes in the background, but `withContext` re-checks cancellation as it
returns, so its result is never written over the newer model, and the new effect sees `builtFrom != inputs` and
rebuilds with the search (whose answers plan 11 has now cached). The model searched from is read from `state` at the
start of the effect, when `builtFrom == inputs`, so it is never a stale one. Plan 11's cache is a copy-on-write map
behind `@Volatile`, so the main thread's `needsSearch` and the background `default` may race on it safely (a race only
repeats a search). The editor's preview is untouched: it calls `prepareSongLyrics` without a `chordInstrument` (its
Chords section is `definitionCellsOf`, which never searches) and builds its own first model, not through
`rememberSongLyricsModel`. The Chord shapes sheet and the PDF keep the default `searchesShapes = true`; both already
run on `Dispatchers.Default`.

Notation and instrument changes still rebuild the whole model off the main thread, as today; with plan 11's cache the
searches in it are lookups, so no reuse of the previous model's shapes is needed here.

**DECISION D-30 for the user** — this knowingly gives up "right on the first frame" (the no-incidental-animations
rule) for a chord the tables do not hold, the first time it is seen in a session: its cell opens as the empty frame
and its dots pop in. **Alternatives considered**: (b) keep the synchronous search and rely on plan 11 alone —
the first sight of a hostile song still freezes for hundreds of milliseconds; (c) build the whole first model off the
main thread — every page would open on a blank frame and fade in, which the no-incidental-animations rule rejects.
Recommended: this plan, accepting that a chord outside the tables shows its empty frame for the time of its search
(a frame or two for an ordinary slash chord, the first time in a session only).

Docs: in `presentation/CLAUDE.md`'s Chord diagrams paragraph replace "`prepareSongLyrics` collects a song's chords
away from the main thread with the rest of the model" with: the chords are collected with the model; the first model
of a page, built in place, leaves out the shapes only the search finds, which follow from `Dispatchers.Default`
without laying anything out again (the cell keeps its size, drawn as the empty frame until then). Also amend the
`rememberSongLyricsModel` sentence in the `SongLyrics.kt` paragraph. In `chordpro/CLAUDE.md`'s `ChordVoicings`
paragraph name `needsSearch`.

## Tests

- `SongChordsTest`: `the first build leaves the search out` — `songChordsOf(song("[C]la [Ebmaj9#11/Bb]la"), STANDARD,
  GUITAR, searchesShapes = false)` (a chord no other `:presentation` test names, since plan 11's cache is shared by
  every test of the module's process; grep before choosing): `C` has its table shape and is not pending; the unusual chord has a null
  `defaultShape` and `isShapePending`; `withSearchedShapes(GUITAR)` gives it `ChordVoicings.default(chord, GUITAR)` and
  clears the flag; the keyboard is never pending; a chord the song `{define}`s is never pending. Assert
  `ChordVoicings.needsSearch(chord, GUITAR)` first, as the test's precondition, so a cache another test filled shows up
  as that assertion failing rather than as a vacuous pass. `SongLyricsModel.withSearchedShapes()` keeps the very same
  `sections` instance (`assertSame`).
- `ChordVoicingsTest` (lane C's file): `needsSearch` is false for the keyboard and for a table chord (`G`), true for a
  chord the tables lack until `default` has answered for it.

## Manual check

On the desktop, with the guitar, open a song holding forty-odd unusual chords (`[C7(b9,#9,#11,b13)]`, `[Cmaj9#11]`, …)
from the list: the page opens at once, with the Chords section's frames in place and their dots filled in a moment
later, nothing moving. Open an ordinary song (G, C, D, Em, D/F#): it opens as before. Repeat on a phone.
