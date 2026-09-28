# Show the date picker's calendar in the language the app is set to

**Challenged:** amended — verified in the 1.12.0-alpha03 sources: the public `DatePickerState(locale, …)` factory exists and is not experimental-gated; typealiases are as the plan says (jvmAndAndroid `java.util.Locale`, darwin `NSLocale`, web Compose `Locale`), and month/weekday names and month-year titles use the state's locale on all four. BUT `DatePickerDefaults.DatePickerHeadline` reads `defaultLocale()` (the system's), not the state's, so the plan's claim that the headline follows was wrong: step 3 now also supplies the `headline`. Also noted: on iOS and the web the first day of the week still comes from the system (`NSCalendar.currentCalendar`, `Locale.current`), and the `selectedMillis` state must live inside the `if (isPickerVisible)` block.

**Kind:** bug (localization)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt` (`SetlistDateField`),
a new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/CalendarLocale.kt` (expect)
and its four actuals (`androidMain`, `desktopMain`, `iosMain`, `wasmJsMain`, next to `LanguageNames.*.kt`),
`presentation/CLAUDE.md`

## Problem

The setlist dialog's calendar (339f3330) is Material 3's own:

```kotlin
val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
...
DatePicker(state = state)
```

`rememberDatePickerState` takes the calendar's locale from `defaultLocale()` — the system's (`java.util.Locale.getDefault()`
on the JVM, the configuration on Android, `NSLocale.preferredLanguages.first()` on iOS, Compose's `Locale.current` on the
web) — and `DatePickerDefaults.DatePickerTitle` reads Material's own "Select date". The app's language is set in the
app (`currentLanguage`, see root `CLAUDE.md`), not by the system. With the app in Hungarian on an English device, the
dialog's Cancel / Done and the field are Hungarian and the calendar above them — month, weekday initials, the headline
date, "Select date" — is English; the other way round for a Hungarian device with the app in English. The language
chips already solve the same problem for language names (`languageDisplayName(code, inLocaleCode)`).

## Fix

1. `ui/platform/CalendarLocale.kt`: `internal expect fun calendarLocale(languageCode: String): CalendarLocale`
   (`androidx.compose.material3.CalendarLocale`), with actuals: Android and desktop
   `java.util.Locale.forLanguageTag(languageCode)`; iOS `NSLocale(localeIdentifier = languageCode)`; wasmJs
   `androidx.compose.ui.text.intl.Locale(languageCode)` (typealiases verified: jvmAndAndroid `java.util.Locale`, darwin
   `NSLocale`, web Compose `Locale`). `currentLanguage.value.code` is what `Languages.kt` already passes as
   `inLocaleCode` ("en", "hu"), so no mapping is needed.
2. In `SetlistDateField`, build the state with the public `DatePickerState(locale = …, initialSelectedDateMillis = …)`
   factory instead of `rememberDatePickerState`, remembered on the locale:
   ```kotlin
   val languageCode = currentLanguage.value.code
   var selectedMillis by rememberSaveable { mutableStateOf(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()) }
   val state = remember(languageCode) { DatePickerState(locale = calendarLocale(languageCode), initialSelectedDateMillis = selectedMillis) }
   LaunchedEffect(state) { snapshotFlow { state.selectedDateMillis }.collect { it?.let { millis -> selectedMillis = millis } } }
   ```
   `rememberDatePickerState` was saveable; the factory is not, so the selection is carried in `selectedMillis` (declared inside the `if (isPickerVisible)` block, so a cancelled pick is
   not remembered the next time the dialog opens) and a
   rotation keeps the day picked but not yet confirmed. (The display mode — calendar or typed — is not kept across a
   rotation; acceptable.) Keep the existing UTC comment and the conversions exactly as they are.
3. Pass `title` explicitly, in the app's language, with the padding Material's own title uses
   (`PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp)`):
   `title = { Text(stringResource(Res.string.setlists_pick_date), modifier = Modifier.padding(...)) }`. **The headline must be
   passed too**: Material's `DatePickerHeadline` formats the selected day with `defaultLocale()`, the system's, whatever
   the state's locale is (only the month grid, the month-year title and the weekday row use the state's). Pass
   `headline = { Text(text = dateFormatter.formatDate(state.selectedDateMillis, locale, forContentDescription = false) ?: stringResource(Res.string.setlists_pick_date), color = DatePickerDefaults.colors().headlineContentColor, maxLines = 1, modifier = Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, bottom = 12.dp)).semantics { contentDescription = <formatDate(..., forContentDescription = true)> }) }`,
   with `dateFormatter = remember { DatePickerDefaults.dateFormatter() }` created in the field and given to `DatePicker` as
   well (`DatePickerFormatter.formatDate` is public; `locale` is the `calendarLocale(languageCode)` value, remembered
   with the state). The text style comes from `DatePicker`'s own `ProvideTextStyle`. Keep the mode toggle: typing a date is the keyboard's way in on the desktop and the web.
4. `presentation/CLAUDE.md`: a line where the dialogs or languages are described — the calendar is given the app's
   language through `calendarLocale`; what Material still draws in the system's (the typed-date field's label and its
   error, the navigation buttons' accessibility labels) is its own strings, which the app cannot reach, and the first
   day of the week on iOS and the web follows the system's region, not the app's language.

No new strings: `setlists_pick_date` ("Pick a date" / "Dátum kiválasztása") already exists in both files.

## Tests

None: this is composable and platform code. Compile all four targets
(`:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
`:app:web:compileKotlinWasmJs`).

## Manual check

On each platform, with the system in English and the app set to Hungarian: a setlist's date dialog shows Hungarian month
and weekday names and a Hungarian headline date ("2026. szept. 28." rather than "Sep 28, 2026"; on the web the day numbers may stay Western digits either way). Rotate
an Android phone with a new day picked but not confirmed: the day is still picked.
