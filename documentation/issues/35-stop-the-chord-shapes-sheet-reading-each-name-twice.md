# Stop the Chord shapes sheet reading each chord's name twice

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all (screen readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`

## Problem

Each cell of the Chord shapes sheet draws the chord's name, and its secondary name where it has one, as `Text`s in a
`FlowRow` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt:179`
at b5c8ed3b5):

```kotlin
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = chord.name,
            ...
        chord.secondaryName?.let {
            Text(
                text = it,
```

and then the diagram, whose `description = chordCellDescription(cell)` is its `contentDescription`. That description
(`chords/ChordCellDescription.kt:29`) already starts with both names:

```kotlin
    val name = cell.soundingName?.let { textResource(Res.string.song_details_chord_sounding, cell.name, it) }
        ?: cell.letterName?.let { textResource(Res.string.song_details_chord_letters, cell.name, it) }
        ?: cell.name
    val shape = cell.selection.shape ?: return textResource(Res.string.song_details_chord_diagram_none, name)
    return textResource(Res.string.song_details_chord_diagram, name, spokenShape(shape))
```

and `SongChord.secondaryName` is `soundingName ?: letterName` (`chords/SongChords.kt:58`), the same one. So a screen reader reads "Am", then "Am: x 0 2 2 1 0"; on a capoed page "G", "A", then "G, sounding as
A: …". Every name is heard twice, and the swipes past a cell are one more than they need to be.

## Fix

Take the name row out of the semantics tree; the diagram's description says everything it said:

```kotlin
    // The diagram's description names the chord, both of its names included (chordCellDescription), so the names drawn
    // above it are left out of what a screen reader reads rather than heard twice.
    FlowRow(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = ...
```

(`androidx.compose.ui.semantics.clearAndSetSemantics`.) The notes line under the diagram stays readable: it is not
part of the description.

## Tests

None: semantics only. (`chordCellDescription` is unchanged.)

## Manual check

- TalkBack / VoiceOver, a capoed song's Chord shapes sheet: each cell is read as its diagram's description, its notes,
  and its stepper — the name is not read separately before the diagram.
