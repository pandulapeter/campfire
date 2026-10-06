# Keep the Pick a date sheet open when the setlist sheet's date row changes between its row and column layouts

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (a rotation on a phone; a window resize on desktop and the web)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Problem

The setlist details sheet's date field opens the Pick a date sheet. `SetlistDateRow` (Dialogs.kt at dac1d9d59) lays the
field and the Countdown checkbox out in a `Row` when there is room and in a `Column` when there is not:

```kotlin
private fun SetlistDateRow(
    ...
) = BoxWithConstraints {
    if (maxWidth >= MIN_DATE_ROW_WIDTH) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetlistDateField(
                modifier = Modifier.weight(1f),
                ...
    } else {
        Column {
            SetlistDateField(
                modifier = Modifier.fillMaxWidth(),
                ...
```

`SetlistDateField` owns the picker's state and the picker sheet itself:

```kotlin
var isPickerVisible by rememberSaveable { mutableStateOf(false) }
...
if (isPickerVisible) {
    var selectedMillis by rememberSaveable { mutableStateOf(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()) }
    val openedMillis = rememberSaveable { date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
    ...
    CampfireBottomSheet(title = stringResource(Res.string.setlists_pick_date), ...)
```

The two branches are different composition groups, so when `maxWidth` crosses `MIN_DATE_ROW_WIDTH` (360dp) the field
in one branch is disposed and a new one composed in the other, starting with `isPickerVisible = false`. The activity
handles rotation in place (`android:configChanges`), so nothing is restored from saved state either: rotating the phone
with the calendar open closes it and loses the day picked but not yet saved (reproduced:
`android/61_date_land.png` → `android/62_date_rotated_portrait.png`). The comment next to `selectedMillis` ("the day
picked but not yet confirmed is carried through a rotation by selectedMillis") is true only when the branch does not
change. A desktop or browser window resized across the width does the same.

## Fix

Hoist the picker out of the branch, so its state lives above `BoxWithConstraints`:

1. Split `SetlistDateField` in two:
   - `SetlistDateField(modifier, date, onPickerRequested: () -> Unit)` — the read-only `OutlinedTextField` with its
     `interactionSource` (a `PressInteraction.Release` calls `onPickerRequested`) and the calendar icon button
     (calls `onPickerRequested`). No picker state.
   - `SetlistDatePickerSheet(date, onDateChange, onDismiss)` — everything now inside `if (isPickerVisible) { … }`
     (`selectedMillis`, `openedMillis`, the locale, the `DatePickerState`, the `CampfireBottomSheet` with its Save),
     unchanged, `dismiss` becoming `onDismiss`. Keep the `@OptIn(ExperimentalMaterial3Api::class)` on it and the
     existing comments.
2. Make `SetlistDateRow` a block-bodied composable:

   ```kotlin
   @Composable
   private fun SetlistDateRow(...) {
       // Above the two layouts rather than in the field, so that the calendar stays open, on the day picked in it,
       // when the sheet crosses the width between them - a rotation, a window resized.
       var isPickerVisible by rememberSaveable { mutableStateOf(false) }
       BoxWithConstraints {
           if (maxWidth >= MIN_DATE_ROW_WIDTH) { Row { SetlistDateField(..., onPickerRequested = { isPickerVisible = true }) ... } }
           else { Column { SetlistDateField(..., onPickerRequested = { isPickerVisible = true }) ... } }
       }
       if (isPickerVisible) {
           SetlistDatePickerSheet(date = date, onDateChange = onDateChange, onDismiss = { isPickerVisible = false })
       }
   }
   ```
3. Keep the KDoc of `SetlistDateField` (it describes the field) and move the picker-specific sentences to the new
   sheet's KDoc.

The `OutlinedTextField` itself still moves between branches (its focus/press state is throwaway), which is fine.
Alternative: `movableContentOf` for the field — more machinery for the same result; not recommended.

No behaviour or doc change beyond the fix (`presentation/CLAUDE.md` already says the date is "a read-only field that
opens Material's date picker").

## Tests

None: composition state, not pure logic.

## Manual check

On the 360 × 640 dp emulator: Setlists → a setlist's Edit details → tap the date → pick another day without saving →
rotate to landscape and back: the calendar stays open on the picked day both times; Save writes it. On desktop: same
with the window dragged narrower and wider across the point where the Countdown checkbox moves under the field.
