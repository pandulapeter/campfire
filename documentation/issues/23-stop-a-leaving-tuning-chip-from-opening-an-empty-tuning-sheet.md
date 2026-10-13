# Stop a tuning chip that is fading out from opening the tuning sheet, and close a tuning sheet with nothing to list

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/InstrumentChips.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TuningSheet.kt`

## Problem

`InstrumentChips` keeps the tuning chip while it fades out after the instrument changed to one without tunings (the
keyboard and Other, whose `Instrument.tunings` is empty), so it "leaves as it was rather than as nothing"
(`components/InstrumentChips.kt` at 72d48deb7):

```kotlin
AnimatedVisibility(visible = shownTuning != null, enter = fadeIn(), exit = fadeOut()) {
    val lastTuning = lastShownTuning.value ?: return@AnimatedVisibility
    PickerChip(
        …
        isEnabled = isEnabled,
        onClick = onTuningClicked,
    )
}
```

The leaving chip is still enabled and still opens the sheet. `TuningSheet.kt` lists the instrument now picked:

```kotlin
subtitle = instrumentLabel(preferences.instrument),
…
preferences.instrument.tunings.forEach { tuning -> RadioListItem(…) }
```

so a tap during the fade (Guitar → Keyboard, then a quick tap where the tuning chip was) opens a "Tuning" sheet
subtitled "Keyboard" with its description and no tunings. This shows on
every screen with the chips: the song's first section, the Tuner tab, the Chords tab, Settings → Songs, and the
onboarding's instrument page.

## Fix

1. `InstrumentChips.kt`: while the chip is leaving, do nothing on a tap and say nothing about a tap: inside the
   `AnimatedVisibility` content, read `val isLeaving = shownTuning == null` (or
   `transition.targetState != EnterExitState.Visible`) and pass `onClick = { if (!isLeaving) onTuningClicked() }`,
   or better add a `isClickable` notion so the leaving chip is also not offered to a screen reader (e.g. wrap it in
   `Modifier.clearAndSetSemantics { }` while leaving). Do not use `isEnabled = false` for it: that dims the chip to
   half alpha in one frame, a jump in the middle of its fade.
2. `TuningSheet.kt`: close itself where the instrument has no choice to offer (every instrument today has none or more
   than one, but the chip is only shown for more than one) — `if (preferences.instrument.tunings.size <= 1)
   LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }` before the sheet is drawn (the way `ChordReferenceSheet`
   closes itself when `chordTuning()` is null) — so no route to an empty list is left.

## Tests

None: both are composable behaviour with no pure decision worth extracting (the condition is `instrument.tunings.size > 1`,
which `ui/instrument/InstrumentsTest` already covers through `Instrument.tunings`).

## Manual check

On any screen with the chips (the Chords tab is easiest): guitar in Drop D, pick Keyboard from the instrument chip and
immediately tap where the tuning chip is fading: nothing opens. With TalkBack/VoiceOver on, the fading chip is not
announced as a button. Pick Guitar again: the tuning chip fades in and opens the tuning sheet as before.
