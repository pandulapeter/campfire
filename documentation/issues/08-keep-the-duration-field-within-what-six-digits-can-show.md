# Keep the duration field within what its six digits can show, so a long duration is never silently cut on the next keystroke

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DurationDigits.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DurationDigitsTest.kt

**Challenged:** sound

## Problem
The duration field is typed as at most six digits (`hh:mm:ss`), but what it writes and what it reopens with are not
held to six (`DurationDigits.kt` at 800ebde0b):

```kotlin
internal fun durationDigitsTyped(text: String) = text.filter { it in '0'..'9' }.trimStart('0').take(MAX_DURATION_DIGITS)

internal fun durationDigitsOf(text: String?) = ChordProDuration.parse(text)?.let { duration ->
    ChordProDuration.format(duration).filter { it != ':' }.trimStart('0')
}.orEmpty()
```

- Typing `999999` (99h 99m 99s) is carried over by `durationTextOf` to `100:40:39`, which is written. Reopening the
  form, `durationDigitsOf("100:40:39")` gives the seven digits `1004039`; the next digit typed makes `10040395`, which
  `durationDigitsTyped` cuts to `100403` — the field now reads `10:04:03` and Save writes that. The user typed one digit
  and the value lost an order of magnitude.
- The same happens for a file that says `{duration: 120:00:00}` (`ChordProDuration` parses up to three hour digits).

## Fix
Hold both directions to what six digits can show:

1. `durationTextOf`: cap the carried value at `99:59:59` — `val duration = (…).coerceAtMost(MAX_DURATION)` with
   `private val MAX_DURATION = 99.hours + 59.minutes + 59.seconds` — so a timer-style overflow such as `999999` writes
   the most the field can show, which it then reopens unchanged. Mention the cap in its KDoc.
2. `durationDigitsOf`: return `""` for a duration above `MAX_DURATION`, the way it already does for a duration it
   cannot read. `SongMetadataDialog` compares the draft with the draft it opened with (`offeredValues`), so a duration
   the field cannot show opens empty and is only removed if the user types into it — the existing, documented path
   ("a duration the field could not show, and so opened empty, is only removed when the user typed into it"). Update
   `durationDigitsOf`'s KDoc: "empty where there is none it can read, or one longer than the field's six digits can show".

## Tests
In `DurationDigitsTest`:
- `durationTextOf("999999")` is `"99:59:59"`; `durationTextOf("995959")` is `"99:59:59"`;
- `durationDigitsOf("99:59:59")` is `"995959"`; `durationDigitsOf("100:40:39")` and `durationDigitsOf("120:00:00")`
  are `""`;
- round trip: `durationDigitsOf(durationTextOf(digits)).length <= MAX_DURATION_DIGITS` for `"999999"`, `"6030"`,
  `"75"`;
- `mapOf(Field.DURATION to "120:00:00").toSongMetadataDraft()[Field.DURATION]` is `""`.

## Manual check
New song → Duration: type 999999 → Create → open Edit song details: Duration reads `99:59:59`; type another digit — it
is ignored (six digits) rather than the value changing. Edit a song file by hand to `{duration: 120:00:00}`, open Edit
song details: Duration is empty; change the title and Save — the file still says `120:00:00`.
