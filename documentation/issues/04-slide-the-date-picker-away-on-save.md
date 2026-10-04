# Slide the date picker sheet away on Save instead of removing it in one frame

**Kind:** animation  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt

**Challenged:** amended — Save's enabled state compares against the day the picker opened on, not the live `date`: with close() the parent's date changes at the tap, which would grey the button out in one frame for the whole slide.

## Problem
`SetlistDateField`'s calendar sheet (`Dialogs.kt` ~1135-1146 at 800ebde0b) ignores the `close` that
`CampfireBottomSheet` hands its `actions` and flips its own visibility flag instead:

```kotlin
val dismiss = { isPickerVisible = false }
CampfireBottomSheet(
    title = stringResource(Res.string.setlists_pick_date),
    onDismiss = dismiss,
    actions = {
        BottomSheetConfirmButton(
            ...
            onClick = {
                state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                dismiss()
            },
        ) { Text(stringResource(Res.string.save)) }
    },
)
```

`isPickerVisible = false` removes the `ModalBottomSheet` from composition, so on Save the calendar vanishes in one frame
while its ✕, a swipe and the scrim all slide it away. Every other form calls the handed `close` after writing
(`TextFieldBottomSheet`'s `confirmButton` KDoc: "so that the sheet slides away rather than being gone in one frame").

## Fix
Take the handed close and call it after writing the date:

```kotlin
actions = { close ->
    BottomSheetConfirmButton(
        ...
        onClick = {
            state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
            close()
        },
    ) { Text(stringResource(Res.string.save)) }
},
```

And hold `enabled` to the day the picker **opened** on rather than the live `date`: `onDateChange` updates the parent
form's draft at the tap, so `state.selectedDateMillis != date…` turns false at once and the Save button would switch to
its disabled colours in one frame and slide away grey — the un-narrated change plan 03 warns about. Next to
`selectedMillis`:

```kotlin
// The day the calendar was opened on: the form's date changes as Save is tapped, and the button sliding away with the
// sheet must not turn grey in its last frames.
val openedMillis = rememberSaveable { date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
```

and `enabled = state.selectedDateMillis != null && state.selectedDateMillis != openedMillis`. (It is inside
`if (isPickerVisible)`, so every opening starts from the current date.)

`onDismiss = dismiss` stays: it runs at the end of the hide animation, which is when the flag should go. (Once plan 03
lands, a second tap on Save during the slide is ignored by the sheet; until then a double tap only writes the same day
twice into the parent form's draft, which is harmless.)

## Tests
None: UI wiring only.

## Manual check
Edit a setlist → tap the date field → pick another day → Save: the calendar slides down (as it does with ✕), its Save button
keeping its colour as it goes, and the setlist sheet shows the new date.
