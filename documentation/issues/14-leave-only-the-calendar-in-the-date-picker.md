# Leave only the calendar in the setlist date picker, so nothing in it is drawn in the system's language

**Kind:** localization  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
CLAUDE.md, presentation/CLAUDE.md

**Challenged:** requested by the user during the review, after the typed-entry strings were explained (it replaces the
dropped live-run finding L7); written by the coordinator, read against plans 03 and 04, which edit the same sheet.

## Problem

The setlist date sheet (`Dialogs.kt`, the `if (isPickerVisible)` block around lines 1122–1175 at 800ebde0b) shows
Material 3's `DatePicker` with its default mode toggle, so the user can switch from the calendar to typed entry:

```kotlin
DatePicker(
    modifier = Modifier
        .weight(1f, fill = false)
        ...
    state = state,
    dateFormatter = dateFormatter,
    colors = DatePickerDefaults.colors(containerColor = campfireBottomSheetContainerColor()),
    title = null,
    headline = { ... },
)
```

The calendar already follows the in-app language: `DatePickerState(locale = calendarLocale(languageCode), ...)`
formats the month and year header and the weekday letters, and the headline is drawn by the app for the same reason.
The typed-entry mode cannot be fixed the same way. Its field label ("Date"), its placeholder pattern and its errors
("Date does not match expected pattern: MM/DD/YYYY", "Date out of expected range") are Material's own resources, read
in the system's language (Android's configuration, `Locale.current` elsewhere), and material3 1.12.0-alpha03 offers no
parameter for any of them. A user with the app in Hungarian on an English phone, or the other way round, gets a
Hungarian sheet with an English field and error (live run, `29_badDate.png` in the session scratchpad). Typed entry
also opens a number pad over a sheet that otherwise needs no keyboard.

## Fix

Pass `showModeToggle = false` to that `DatePicker` call. The state's `initialDisplayMode` already defaults to
`DisplayMode.Picker` (the `DatePickerState(locale = …, initialSelectedDateMillis = …)` constructor), so the sheet
always shows the calendar and there is no way into the input mode. Keep everything else: the headline the app draws,
the `dateFormatter`, the UTC conversion, the scroll and fade modifiers, and Save in the header (plans 04 and 03 change
how Save closes the sheet and the guard while it closes; this plan touches neither).

Update the docs that describe the typed entry:

- Root `CLAUDE.md`, Conventions, the "Every modal with text inputs is a bottom sheet" bullet: `including the date
  picker, which can switch from a calendar to typed date entry` → the date picker is a bottom sheet too, holding only a
  calendar (no typed entry, whose strings Material draws in the system's language). Keep the rest of the bullet
  (its Material container using the shared sheet color).
- `presentation/CLAUDE.md` line ~126: `The calendar sheet opens in calendar mode and allows the user to switch to typed
  date entry.` → it shows only the calendar; and `The date picker also uses CampfireBottomSheet, since its calendar can
  switch to typed date entry, with Save in its header.` → it uses `CampfireBottomSheet` like the forms, with Save in
  its header.
- `presentation/CLAUDE.md` line ~129: in `What Material still draws in the system's language is its own strings, which
  the app cannot reach — the typed date field's label and its error, the accessibility labels of the month buttons —`
  drop the typed field's label and error, leaving the accessibility labels of the month and year buttons; say that
  typed entry is turned off for this reason.

## Tests

None: composition only.

## Manual check

On Android, set the phone to English and the app to Hungarian; open a setlist's details → the date → the calendar
sheet has no pencil button, every visible word is Hungarian, picking a day and Save still writes it (`date` in the
`*.setlist.json`) and the sheet slides away (plan 04). The year list (tap the month/year header) still works.
