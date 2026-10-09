# Let a Chord shapes cell widen to fit its stepper at large text, and read its position as "Shape 1 of 5"

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — size the cell from the widest position string (measured once per cell), not from IntrinsicSize.Max, which would resize the cell and slide its neighbours on every 9→10 step and through AnimatedContent's SizeTransform
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`

**Depends on:** plan 30 (`Stepper`'s `valueDescription`).

## Problem

1. Every cell of the sheet is a fixed width
   (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt:152` at
   b5c8ed3b5):

   ```kotlin
   ) = Column(
       modifier = Modifier.width(if (instrument.isFretted) FRETTED_CELL_WIDTH else KEYBOARD_CELL_WIDTH),
   ```

   with `FRETTED_CELL_WIDTH = 132.dp`. The `Stepper` inside it is two 36dp buttons around a value of at least 44dp —
   116dp at 1.0x — but the value "12 / 24" is `labelLarge` bold and grows with the font scale (~65dp at 1.3x, ~100dp at
   2.0x). The stepper's `Row` measures its children in order, so the last one, the Next button, gets what is left:
   from about 1.3x it is squeezed and at 2.0x it is mostly gone, and a guitar player with large text cannot step forward
   through the shapes.

2. The value is written `%1$d / %2$d` (`song_details_chord_shape_position`, `ChordShapesSheet.kt:235`), which a screen
   reader reads as "1 slash 24".

## Fix

1. Let the cell be as wide as its widest fixed-size child, never narrower than today, while the texts that wrap keep
   wrapping at today's width:

   ```kotlin
   val cellWidth = if (instrument.isFretted) FRETTED_CELL_WIDTH else KEYBOARD_CELL_WIDTH
   ...
   ) = Column(
       // At least as wide as the diagram's cell, and wider where the stepper is at a large text size: its value grows
       // with the text and its buttons do not shrink, and a Row hands its last child, the Next button, what is left.
       modifier = Modifier.widthIn(min = cellWidth).width(IntrinsicSize.Max),
   ```

   and give the name `FlowRow`, the notes `Text` and the defined line (plan 33) `Modifier.widthIn(max = cellWidth)` so
   that their max intrinsic width — a long name, the notes of a big chord — does not widen the cell; they wrap at
   `cellWidth` as they do today. The diagram is a fixed size. `IntrinsicSize.Max` then comes out as
   `max(cellWidth, stepper's width)`: the stepper is a `Surface` → `Row` of fixed-size `IconButton`s and an
   `AnimatedContent` around a `Text`, all of which answer intrinsic queries (no subcomposition in the cell).
   Because `cellWidth` is only declared inside the Composable after this, a `cellWidth` local replaces the inline
   `if` (the `Column` is an expression body, so make `ChordShapeCell` a block body or compute `cellWidth` in a
   parameter default — prefer the block body). Horizontally the cell stays `CenterHorizontally`.

   If intrinsics turn out to misbehave with `Crossfade`/`AnimatedContent` on some platform, the fallback is
   `Modifier.wrapContentWidth(unbounded = true)` on the `Stepper` alone, which lets it spill over the 16dp `CELL_GAP`
   at 2.0x; prefer the intrinsic cell, which never overlaps a neighbour.

   **Challenged — do not size the cell from the stepper's intrinsic width.** The stepper's value changes width as it
   steps ("9 / 12" → "10 / 12", and back at the wrap-around), and `AnimatedContent`'s default `SizeTransform` animates
   that change, so an intrinsic-sized cell would grow and shrink *while the user steps*: the `FlowRow` centers its
   cells (`Arrangement.spacedBy(CELL_GAP, Alignment.CenterHorizontally)`), so every neighbour slides and the Next button
   moves out from under the finger, and a row near its limit reflows a chord onto the next line. Size the cell from the
   **widest position it can show** instead, which is fixed for the cell's options:

   ```kotlin
   val textMeasurer = rememberTextMeasurer()
   val positionStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
   val widestPosition = options?.size?.let { stringResource(Res.string.song_details_chord_shape_position, it, it) }
   val density = LocalDensity.current
   // At least the diagram's cell, and as wide as the stepper at its widest position: two buttons, and a value that grows
   // with the text. Measured from the widest value rather than the current one, so stepping never resizes the cell and
   // the cells around it never slide under the finger.
   val stepperWidth = widestPosition?.let {
       with(density) { textMeasurer.measure(it, positionStyle).size.width.toDp() }.coerceAtLeast(STEPPER_VALUE_MIN_WIDTH) +
           STEPPER_BUTTON_LENGTH * 2
   } ?: 0.dp
   ...
   modifier = Modifier.width(maxOf(cellWidth, stepperWidth)),
   ```

   with `STEPPER_VALUE_MIN_WIDTH = 44.dp` and `STEPPER_BUTTON_LENGTH = 36.dp` made `internal` in `Stepper.kt` (as
   `VALUE_MIN_WIDTH` and `stepperButtonLength(1f)`; expose them rather than copying the numbers) and the name `FlowRow`,
   notes and defined line keep `Modifier.widthIn(max = cellWidth)` as above. The width changes only when the options
   arrive (the placeholder `Spacer` has none), once per cell as the sheet opens, never during a step; at 1.0x
   `stepperWidth` (~116dp) is under 132dp and nothing changes. The `IntrinsicSize.Max` text and the
   `wrapContentWidth` fallback below are superseded.

2. New string, next to `song_details_chord_shape_position` in both files:

   ```xml
   <!-- How a screen reader reads song_details_chord_shape_position, e.g. "Shape 1 of 5". -->
   <string name="song_details_chord_shape_position_description">Shape %1$d of %2$d</string>
   ```
   ```xml
   <!-- How a screen reader reads song_details_chord_shape_position, e.g. "Shape 1 of 5". -->
   <string name="song_details_chord_shape_position_description">%1$d. fogás (%2$d közül)</string>
   ```

   and pass it to the stepper:

   ```kotlin
   value = stringResource(Res.string.song_details_chord_shape_position, index + 1, options.size),
   valueDescription = stringResource(Res.string.song_details_chord_shape_position_description, index + 1, options.size),
   ```

   (two numbers, so `stringResource`, not `textResource`). With plan 30 the Previous and Next buttons then carry
   "Shape 2 of 5" as their state, so a step is heard as the new position.

## Tests

None: layout and semantics of a Composable.

## Manual check

- Android at 1.3x and 2.0x font scale (and the smallest supported screen, 360 × 640dp): a song with several chords,
  guitar → Chord shapes; every cell's Next button is whole and tappable; cells of long names ("Cmaj7/G" with its
  sounding name) still wrap their names over two lines rather than widening; at 1.0x the sheet looks as before.
- Keyboard instrument (176dp cells): the same.
- TalkBack: focus a cell's Next button, double-tap: "Shape 2 of 24" is read; Hungarian: "2. fogás (24 közül)".
