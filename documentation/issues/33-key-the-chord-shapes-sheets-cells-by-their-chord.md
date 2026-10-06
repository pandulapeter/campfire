# Key the Chord shapes sheet's cells by their chord, so a list that changes under the open sheet never steps one chord through another's shapes

**Challenged:** amended — the key keeps the name for good: plan 34 still lists one chord twice where the song defines its two spellings with different shapes, so the id alone would not be unique even after it; also spelled out what a rename while open does.

**Kind:** bug (state)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`

## Problem

The sheet emits its cells by position (`ChordShapesSheet.kt`):

```kotlin
chords.orEmpty().forEach { chord ->
    ChordShapeCell(
        chord = chord,
        ...
        onShapeSelected = { shape -> viewModel.setChordVoicing(instrumentPreference, chord.chord.id, shape?.let(ChordVoicings::write)) },
    )
}
```

and each cell holds the chord's shapes in a `produceState`:

```kotlin
val shapes by produceState<List<ChordVoicing>?>(null, chord.chord, instrument, selection.source) {
    value = if (selection.source == SelectedShape.Source.DEFINED) emptyList() else withContext(Dispatchers.Default) { ChordVoicings.all(chord.chord, instrument) }
}
```

`produceState` keeps its previous value while a restarted producer runs. The sheet's `chords` are rebuilt whenever
the song text, the transposition, the capo or the notation changes under it (`produceState(null, text, transposition,
capo, spelling, instrument)`) — a sync run downloading the song or a synced capo override while the sheet is open. When
a chord is added or removed above a cell, the cell slot at that position is handed another chord, but `shapes` still
holds the *previous* chord's shapes until `ChordVoicings.all` returns (milliseconds, or much longer for an unusual
chord). In that window `options` is built from the old chord's shapes (`listOf(current) + all` where the current shape
is not among them), and a tap on the stepper calls `select(options[...])` → `setChordVoicing(..., chord.chord.id, …)`:
the old chord's shape is stored under the new chord's id, for every song. The `Crossfade(targetState = current)` also
crossfades one chord's diagram into another's instead of the slot being a new cell.

## Fix

Give every cell its own identity:

```kotlin
chords.orEmpty().forEach { chord ->
    key(chord.chord.id, chord.name) {
        ChordShapeCell(...)
    }
}
```

(`androidx.compose.runtime.key`). Keep `name` in the key even after plan 34: it lists a chord once per spelling *and
definition*, so a song that defines `A#` and `Bb` with different shapes still has two cells with one id, and a key of
the id alone would give those two cells one identity again (Compose tells duplicate keys apart only by their order). A change of notation or transposition while the sheet is open renames every
cell, so each starts afresh (stepper hidden until its shapes are there, no crossfade from the old diagram): that is a
new list, which is what the key is for. A new key starts the cell's
`produceState` from `null`, so the stepper is hidden (`options == null`, the placeholder `Spacer` keeps its room)
until that chord's own shapes are there.

## Tests

None: Compose state identity is UI, which the project does not test by code.

## Manual check

Open a song's Chord shapes sheet on the desktop, keep it open, and edit the song's file on disk (or on another synced
device) to add a chord before the first one; once the sheet updates, step the second cell: the shape it shows and
stores is one of that chord's, and reopening the sheet shows the same.
