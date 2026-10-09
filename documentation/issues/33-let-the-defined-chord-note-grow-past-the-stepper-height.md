# Let the Chord shapes sheet's "Defined in this song" line grow past the stepper's height instead of clipping

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`

## Problem

In the Chord shapes sheet, a chord the song defines with `{define}` has a line instead of a stepper
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt:221` at
b5c8ed3b5):

```kotlin
    if (selection.source == SelectedShape.Source.DEFINED) {
        Text(
            modifier = Modifier.padding(top = 8.dp).height(STEPPER_PLACEHOLDER_HEIGHT),
            text = if (selection.movedBy == 0) {
                stringResource(Res.string.song_details_chord_defined)
            } else {
                pluralStringResource(Res.plurals.song_details_chord_defined_moved, abs(selection.movedBy), abs(selection.movedBy))
            },
```

`STEPPER_PLACEHOLDER_HEIGHT` is 40dp, two `bodyMedium` lines at 1.0x, in a cell 132dp wide. The moved form ("Defined in
this song, moved by 2 frets", "A dal határozza meg, 2 bunddal eltolva") already fills both lines at 1.0x, and with any
larger text it needs three or more, which the fixed height cuts off — the part cut is how far the shape was moved.

The placeholder height's intent, from the `else` branch: "The stepper's room is kept while the shapes are looked for,
so that the cells do not grow under the finger." The defined line uses the same height so that a defined chord's cell
is as tall as a stepper cell beside it. Both only need the line to be *at least* that tall.

## Fix

```kotlin
            modifier = Modifier.padding(top = 8.dp).heightIn(min = STEPPER_PLACEHOLDER_HEIGHT),
```

(`androidx.compose.foundation.layout.heightIn`.) Leave the `Spacer` in the `else` branch at `height(...)`: it holds
no text. A defined chord's cell can now be taller than its neighbours, which the sheet's `FlowRow` absorbs by making
that row taller; the text is not centered today and stays top-aligned.

## Tests

None: a modifier on a Composable.

## Manual check

- A song with `{define: G base-fret 3 frets 1 3 3 2 1 1}` and a capo or transposition that moves it, Hungarian
  language, system font at 1.3x and at 2.0x: open Chord shapes; the whole "… bunddal eltolva" line is visible.
- At 1.0x the sheet looks as before.
