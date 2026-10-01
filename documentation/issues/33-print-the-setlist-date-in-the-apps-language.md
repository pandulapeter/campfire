# Print the setlist's date as the app shows dates

**Kind:** output quality  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
**Challenged:** amended — no change to `PrintSource`, `PrintLabels`, the layout or its tests: the sheet fills the existing `PrintSource.date` string itself, which removes every conflict with lane A (plans 07 and 12 edit `PrintLabels` and the `layoutPrintDocument` call); the formatter is fed UTC midnight, as the dialog does.

## Problem

`date = setlist?.date?.toString()` prints `2026-10-01` on the running order. The setlist dialog shows the day through
Material's `DatePickerDefaults.dateFormatter()` in the language chosen in the app.

## Fix

`preparePrintSource` stops setting `date` (leave the field's default). The sheet already holds the dialog's setlist, so
it formats the day itself, where composition and the app's language are available, the way `SetlistDetailsDialog`
does:

```kotlin
val locale = remember(currentLanguage.value.code) { calendarLocale(currentLanguage.value.code) }
val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
val date = dialog.setlist?.date?.let { day ->
    dateFormatter.formatDate(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), locale) ?: day.toString()
}
```

and puts it into the source it lays out: `chosenSource = source.copy(date = date, songs = …)`. The day goes in as UTC
midnight because the formatter formats in UTC (the dialog's own `selectedMillis` is built the same way): any other
zone prints the previous or the next day. `formatDate` is an experimental Material API, so the function needs
`@OptIn(ExperimentalMaterial3Api::class)` as the dialog's does. The language is read as `currentLanguage.value`
inside composition, so a language change relays the document. `PrintSource.date` stays a `String?` and the layout
stays untouched.

## Tests

None needed: `PrintLayoutTest` still feeds the layout a string.

## Manual check

Export a running order in English and in Hungarian; the date reads as in the setlist dialog, and is the same day as
there in a time zone far from UTC.
