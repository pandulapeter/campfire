# Stack a setting's label above its stepper when a word of it would no longer fit beside it

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (large text on a narrow screen)
**Challenged:** amended — the form is decided by the widest tempo marking (a never-placed decidingLabel slot) and stays stacked once stacked at a width, so dragging the slider or stepping across a digit boundary cannot flip the row and move the slider/stepper under the finger
**Files:** new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/LabeledControlRow.kt`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/LabeledControlRowTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SettingsSubsection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/ReferencePitchSetting.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/TempoSetting.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/PrintOptions.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`

**Order:** after plans 30 and 36, which touch `TempoSetting.kt` and `Stepper` too. Lane A owns `ReferencePitchSetting.kt`.

Paths below are relative to `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

Three settings put a weighted label beside a wide control, at b5c8ed3b5:

- `tuner/ReferencePitchSetting.kt:53` — label, a stepper reading "A4 = 440 Hz", and the play chip:
  ```kotlin
  ) = Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
      Text(
          modifier = Modifier.weight(1f),
          text = stringResource(Res.string.tuner_reference_pitch),
  ```
- `screens/metronome/TempoSetting.kt:52` — "Tempo" over its Italian marking, beside the tempo stepper with its Tap
  segment:
  ```kotlin
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
  ```
- `screens/export/PrintOptions.kt:375` — Text size and Margins beside their steppers:
  ```kotlin
  private fun PrintStepperRow(
      label: String,
      stepper: @Composable () -> Unit,
  ) = Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
      Text(label, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
      stepper()
  }
  ```

The controls grow with the text (their values are `labelLarge`) and never wrap, so the weight hands the label whatever
is left. On a 360dp phone at 1.5x–2.0x that is narrower than a single word, and the word is broken between letters:
"Alaphan/g", "Szövegmére/t", "Moder/ato", "Allegre/tto". At 2.0x the reference pitch's controls alone nearly fill the
row.

## Fix

Options considered:

- A `FlowRow` of label and controls: the controls wrap under the label as soon as label and controls do not fit on
  one line *at the label's full width*, which changes 1.0x layouts where the label today wraps at a space beside the
  control (the reference pitch at 1.0x in Hungarian). Rejected.
- Always stacking, the `SettingsSubsection` way: changes every row at 1.0x. Rejected.
- **Recommended: one small layout that puts the label beside the controls while the label's longest word fits there,
  and above them otherwise.** The label's `minIntrinsicWidth` is its longest unbreakable run, so the row changes form
  exactly when a word would be broken, and never at 1.0x.

New `components/LabeledControlRow.kt`:

```kotlin
/**
 * A setting's label with its controls at the end of the row, the way a switch row has its switch, for as long as the
 * label's longest word still fits beside them; under that, the label goes above the controls, the way
 * [SettingsSubsection] sets a control that does not fit at the end of a row. The controls do not wrap and grow with the
 * text size, and a label handed only what they leave would be broken in the middle of a word.
 */
@Composable
internal fun LabeledControlRow(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    contents = listOf(label, controls),
) { (labelMeasurables, controlsMeasurables), constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val controls = controlsMeasurables.map { it.measure(loose) }
    val controlsWidth = controls.maxOfOrNull { it.width } ?: 0
    val controlsHeight = controls.maxOfOrNull { it.height } ?: 0
    val gap = LABEL_CONTROLS_GAP.roundToPx()
    val labelMinWidth = labelMeasurables.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
    val maxWidth = constraints.maxWidth
    if (!constraints.hasBoundedWidth || isLabelBesideControls(maxWidth, labelMinWidth, controlsWidth, gap)) {
        val labelWidth = (maxWidth - controlsWidth - gap).coerceAtLeast(0)
        val labels = labelMeasurables.map { it.measure(Constraints(minWidth = labelWidth, maxWidth = labelWidth)) }
        val labelHeight = labels.maxOfOrNull { it.height } ?: 0
        val height = maxOf(labelHeight, controlsHeight, constraints.minHeight)
        layout(maxWidth, height) {
            labels.forEach { it.placeRelative(0, (height - it.height) / 2) }
            controls.forEach { it.placeRelative(maxWidth - it.width, (height - it.height) / 2) }
        }
    } else {
        val labels = labelMeasurables.map { it.measure(loose.copy(maxWidth = maxWidth)) }
        val labelHeight = labels.maxOfOrNull { it.height } ?: 0
        val stackedGap = SUBSECTION_CONTROL_GAP.roundToPx()
        val height = maxOf(labelHeight + stackedGap + controlsHeight, constraints.minHeight)
        layout(maxWidth, height) {
            labels.forEach { it.placeRelative(0, 0) }
            controls.forEach { it.placeRelative(0, labelHeight + stackedGap) }
        }
    }
}

/**
 * Whether a label whose longest word is [labelMinWidth] wide still fits beside controls [controlsWidth] wide, [gap]
 * apart, in a row [maxWidth] wide. All in pixels.
 */
internal fun isLabelBesideControls(maxWidth: Int, labelMinWidth: Int, controlsWidth: Int, gap: Int) =
    labelMinWidth + gap + controlsWidth <= maxWidth

private val LABEL_CONTROLS_GAP = 16.dp
```

(the unbounded branch must not compute `maxWidth - …` from `Constraints.Infinity`; in practice all three rows are
`fillMaxWidth` in a vertically scrolling column, so the width is bounded — keep the guard simple, e.g. treat unbounded
as beside with `labelWidth = labelMinWidth`.) Make `SUBSECTION_CONTROL_GAP` in `components/SettingsSubsection.kt`
`internal` (no other declaration of that name in the package) so the stacked form keeps the subsection's 12dp between
label and control. The change between forms comes from the text size or the window, not from something the user did,
so it is not animated: the first frame is already right.

Use it in the three places, keeping each row's own padding and minimum height on `modifier`:

- `ReferencePitchSetting`: `LabeledControlRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical =
  12.dp), label = { Text(text = stringResource(Res.string.tuner_reference_pitch), …) }, controls = { Row(verticalAlignment
  = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stepper(…); SelectableChip(…) } })`
  — the label loses its `Modifier.weight(1f)`.
- `TempoSetting`: the label `Column` (name and marking) without `weight(1f).padding(end = 16.dp)`, the `TempoStepper` as
  the controls. The `Column`'s min intrinsic width is its widest word, the marking's included.
- `PrintStepperRow`: `LabeledControlRow(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal =
  16.dp), label = { Text(label, style = MaterialTheme.typography.bodyLarge) }, controls = stepper)`.

**Challenged — the form must not change while the user is changing the value.** Two of the three rows change their
widths with the value: the tempo's label `Column` holds the Italian marking, which changes many times a second while
the slider under the row is dragged (`tempoMarking(bpm)`: "Largo" … "Prestissimo"), and every stepper's value widens
at a digit boundary (99 → 100 BPM, 9 → 10 mm, 9 → 10 pt). At a text size where "Prestissimo" no longer fits beside the
stepper but "Largo" does, the row would flip between beside and stacked as the slider moves, changing the row's height
and moving the slider itself under the dragging finger (and a held Faster button out from under its finger). Two
changes:

1. **A label that decides without being drawn.** Add `decidingLabel: (@Composable () -> Unit)? = null` to
   `LabeledControlRow`, a third entry of `contents`, queried only for `minIntrinsicWidth` and never measured or placed
   (give it `Modifier.clearAndSetSemantics {}` so nothing of it reaches a screen reader); the decision uses
   `max(label's, decidingLabel's)` min intrinsic width. `TempoSetting` passes a `Column` of the name and **every**
   marking (`TEMPO_MARKINGS` in `metronome/TempoMarking.kt`, exposed as `internal val TEMPO_MARKING_NAMES =
   TEMPO_MARKINGS.map { it.second }`), each in the style it is drawn in, so the form follows the widest marking,
   "Prestissimo", whatever the tempo.
2. **Once stacked at a width, stay stacked at that width.** A controls width that grows by a digit can still tip the
   row. Keep a plain holder, `remember { StackedWidth() }` (`private class StackedWidth(var width: Int = -1)`, not a
   state: written during measurement and read only there), set to `maxWidth` whenever the row stacks, and stack
   whenever `maxWidth == stackedWidth.width`. A new width (rotation, a window resized, the screen composed again)
   decides afresh. Make the rule part of the pure helper so it is tested:

   ```kotlin
   internal fun isLabelBesideControls(maxWidth: Int, labelMinWidth: Int, controlsWidth: Int, gap: Int, stackedAtWidth: Int) =
       maxWidth != stackedAtWidth && labelMinWidth + gap + controlsWidth <= maxWidth
   ```

   and add to the tests: `` `a row that stacked at a width stays stacked there although the controls narrowed` `` —
   `isLabelBesideControls(328, 100, 200, 16, stackedAtWidth = 328)` is false, and `(400, 100, 200, 16, stackedAtWidth =
   328)` is true (the existing three cases pass `stackedAtWidth = -1`).

The manual check's "Allegretto" is not one of the markings; use "Prestissimo" (≥ 200 BPM) and "Largo" (40–54 BPM),
and add: at 1.5x on the 360dp phone, drag the slider from 40 to 240 BPM — the row keeps one form throughout and the
slider does not move under the finger; hold Faster from 95 to 105 BPM — the stepper does not jump.

Document `LabeledControlRow` in `ui/components/CLAUDE.md` (a line under the shared components: when a row's label goes
above its controls).

## Tests

`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/LabeledControlRowTest.kt`,
`kotlin.test`, backtick names:

- `` `a label whose longest word fits beside the controls stays beside them` `` — `isLabelBesideControls(328, 100, 200,
  16)` is true, and so is the exact fit `(316, 100, 200, 16)`.
- `` `a label whose longest word would be broken goes above the controls` `` — `(315, 100, 200, 16)` is false.
- `` `controls wider than the row put the label above them` `` — `(300, 0, 320, 16)` is false.

Run `./gradlew :presentation:desktopTest`.

## Manual check

- Android, 360 × 640dp emulator (see the smallest supported screen recipe), Hungarian and English, font scale 1.0,
  1.3, 1.5 and 2.0: Tuner tab's Reference pitch, Metronome tab's Tempo (with the marking at "Allegretto" ~ 110 BPM),
  Export → Text size and Margins. At 1.0x every row looks exactly as before; at large sizes the label is above the
  controls and no word is broken between letters.
- Wide windows (tablet, desktop) at 2.0x: the label stays beside the controls.
- The tempo's Tap segment and the reference pitch chip still work in the stacked form.
