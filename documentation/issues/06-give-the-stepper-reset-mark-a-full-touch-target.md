# Give a typed stepper's reset mark a full-size touch target

**Challenged:** amended — the original "widen the pointer input without changing the layout" cannot work: in Compose UI
1.12 (`jetbrains-compose` 1.12.1) `PointerInputModifierNode.touchBoundsExpansion` applies to stylus and eraser only, and
the automatic minimum-touch-target extension loses to any direct hit, while the mark is flush against two direct hits
(the text field on its left; the + button on its right, with no slack at all for a 3-digit tempo or any reference pitch).
`minimumInteractiveComponentSize()` grows the layout. The fix is now a wider laid-out press box for the pressable mark,
reserved by `StepperValueRoom` so the pill still never changes length while used.

**Kind:** accessibility / bug  ·  **Severity:** medium  ·  **Platforms:** Android, iOS, touch web
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/controls/StepperResetMark.kt`,
`.../controls/StepperValueRoom.kt`, `.../controls/StepperField.kt`, `.../controls/Stepper.kt` (`StepperValue`'s room
call only), `.../controls/CLAUDE.md`; test in
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/playing/TypedTempoTest.kt`

## Problem

With a `StepperEntry` (the tempo stepper on a song, the reference pitch on the Tuner) a tap on the value starts typing,
so the reset mark is the only way to reset by touch. Its press area is its own laid-out box, the 16 dp icon times the
song's text scale plus a 6 dp gap, by the pill's height (`StepperResetMark`):

```kotlin
Box(
    modifier = Modifier
        .fillMaxHeight()
        .then(
            ...
                    Modifier
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = STEPPER_RESET_MARK_SIZE * fontScale),
                            enabled = isShown,
                            role = Role.Button,
                            onClick = onReset,
                        )
            ...
        )
        // Split evenly, so that the icon sits in the middle of what takes its press.
        .padding(horizontal = STEPPER_RESET_MARK_GAP / 2),
```

```kotlin
internal fun stepperResetMarkWidth(fontScale: Float) = STEPPER_RESET_MARK_SIZE * fontScale + STEPPER_RESET_MARK_GAP
```

That is 22 dp wide at text scale 1 and less at a smaller song text. In `StepperField` the mark follows the
`BasicTextField` (as wide as its digits and unit) inside a row centered in `StepperValueRoom`, whose width is the widest
value plus `stepperResetMarkWidth`; the next thing to the right is the + `IconButton`. A miss to the left starts typing and
raises the keyboard; a miss to the right steps the tempo. Compose's own help does not reach here: the minimum touch target
extension only takes presses no other node is directly under, and `touchBoundsExpansion` is stylus-only
(`NodeCoordinator.isInExpandedTouchBounds`: "The expanded touch bounds only works for stylus at this moment").

## Fix

Make the pressable mark's laid-out box wider, and reserve that width in the value's room, so the pill keeps one length
whether or not the mark is shown (the "A pill never changes length while used" rule in `controls/CLAUDE.md`).

1. `StepperResetMark.kt`: add `internal fun stepperResetMarkWidth(fontScale: Float, isPressable: Boolean)`; pressable it
   is `maxOf(STEPPER_RESET_MARK_SIZE * fontScale + STEPPER_RESET_MARK_GAP, STEPPER_BUTTON_LENGTH)`, the unscaled length
   of the stepper's own − and + buttons (36 dp), whose press areas are capped at that length too
   (`LocalMinimumInteractiveComponentSize provides minOf(height, buttonLength)` in `Stepper`). Not 48 dp: that would make
   the mark's target larger than the buttons beside it and add 26 dp to the pill. `STEPPER_RESET_MARK_WIDTH` stays the
   unpressable width (check its users with a grep and keep them on it).
2. In `StepperResetMark`, when `onReset != null`, lay the box out at `width(stepperResetMarkWidth(fontScale, true))`
   (it is already `fillMaxHeight`, so the press spans the pill's height) with the icon centered, in place of the
   `padding(horizontal = STEPPER_RESET_MARK_GAP / 2)`. The ripple stays as it is (unbounded, the icon's radius, centered on
   the box, which is centered on the icon). Unpressable (`StepperValue`'s mark) keeps today's width.
3. `StepperValueRoom` gets `isResetPressable: Boolean` and reserves `stepperResetMarkWidth(fontScale, isResetPressable)`;
   `StepperField` passes `true` (it only reserves room when `onReset != null`), `StepperValue` passes `false`. The two
   widths are then one function of one flag, so the room and the mark cannot disagree.
4. Update the KDoc of `StepperResetMark` (`onReset`: "a touch area reaching as far on either side of it" becomes "a box
   as wide as the stepper's buttons") and `controls/CLAUDE.md` (Steppers: a typed stepper's reset icon takes a press as
   wide as − and +).

Cost: the tempo pill on a song and the reference pitch pill grow by about 14 dp (more at a small song text), always,
not while used; the digits-to-icon gap widens. The song's controls `FlowRow` wraps on its own measurements, so nothing
else changes; check by eye at 360 dp on the song details screen and the Tuner. The Metronome tab's tempo stepper has no
reset (`onReset = null`) and does not change.

## Tests

- `TypedTempoTest`: in `a changed tempo is reset from the icon after its value` (or a new test next to it), after the
  step, assert the reset node (`hasContentDescription(song_details_tempo_reset) and hasClickAction()`) is at least
  36 dp wide (`assertWidthIsAtLeast`); and that the pill's width (the node holding the stepper, or the + button's left
  edge) is the same before and after the step that makes the mark appear.
- `GoldenRendersTest`: any golden showing a song's tempo pill or the Tuner's reference pitch changes width; re-record on
  Linux as `tools/screenshots/CLAUDE.md` says and review the diff.
