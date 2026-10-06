# Wrap a Chord shapes sheet cell's names onto a second line instead of clipping them at the cell's edge

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`

## Problem

A sheet cell is a fixed `FRETTED_CELL_WIDTH = 132.dp` (`KEYBOARD_CELL_WIDTH = 176.dp`) column, and its name row is two
single-line texts in a `Row` (`ChordShapesSheet.kt`, `ChordShapeCell`):

```kotlin
Row(verticalAlignment = Alignment.Bottom) {
    Text(
        text = chord.name,
        style = MaterialTheme.typography.titleMedium,
        color = LocalSecondAccentColor.current,
        maxLines = 1,
    )
    chord.secondaryName?.let {
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
```

`maxLines = 1` with the default `TextOverflow.Clip`: whatever does not fit 132dp is cut off with no ellipsis. At
`titleMedium` (16sp, roughly 9dp a character) a numbered chord and its letters — Nashville `b77sus4/4` plus its letters `Bb7sus4/F` in C,
about 170dp — or a capoed keyboard's `A#maj9#11` plus its sounding `Bmaj9#11` lose the end of the secondary name, which
is the half that says what the shape under it is. (The Chords section's own cells are not affected: their column has no
fixed width and grows with the name, `TextOverflow.Visible`.)

## Fix

Let the names wrap within the cell rather than clip: replace the `Row` with a centered `FlowRow`, so the secondary
name moves under the first where both do not fit, and let a single name too long for the cell on its own wrap too:

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    itemVerticalAlignment = Alignment.Bottom,
) {
    Text(text = chord.name, style = MaterialTheme.typography.titleMedium, color = LocalSecondAccentColor.current, textAlign = TextAlign.Center)
    chord.secondaryName?.let {
        Text(text = it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
```

(drop the secondary text's `padding(start = 6.dp)`, the arrangement spaces them; `FlowRow` is already imported). The
cells of one row of the sheet's `FlowRow` may then differ in height by a line; the outer `FlowRow` aligns them at the
top, which is fine, since the steppers are not compared across cells. No animation is involved: the wrapping only
depends on the names, which do not change while the sheet is open.

## Tests

None: text measurement is UI.

## Manual check

Settings → Songs → Nashville numbers, open a song with `{key: C}` and a `[Bb7sus4/F]` (a step and its letters) and its
Chord shapes sheet on a 360dp-wide phone: both names are whole, the letters on a second line. With the keyboard and a
capo, `A#maj9#11` and its sounding name are whole too. Ordinary names (`G`, `D/F#`) stay on one line.
