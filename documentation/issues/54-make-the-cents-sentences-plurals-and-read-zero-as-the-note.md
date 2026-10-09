# Make the tuner's cents sentences plurals, and read a reading that rounds to 0 cents as the note alone

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`

## Problem

The exact reading is read with two plain strings (`strings.xml:391-393` at b5c8ed3b5):

```xml
<!-- How a screen reader reads the meter: the note, then how far it is off, e.g. "E2, 12 cents flat". -->
<string name="tuner_reading_flat">%1$s, %2$d cents flat</string>
<string name="tuner_reading_sharp">%1$s, %2$d cents sharp</string>
```

and `TunerDisplay.kt:76-77`:

```kotlin
reading.cents < 0 -> stringResource(Res.string.tuner_reading_flat, noteNameWithOctave(reading.note, notation), abs(reading.cents).roundToInt())
else -> stringResource(Res.string.tuner_reading_sharp, noteNameWithOctave(reading.note, notation), reading.cents.roundToInt())
```

- One cent reads "E2, 1 cents flat". The root `CLAUDE.md` asks for a `<plurals>` with `one` and `other` read with
  `pluralStringResource` for a counted sentence whose singular reads differently.
- A reading within half a cent that the tracker has not yet called in tune (it waits 300 ms,
  `PitchTracker.IN_TUNE_DELAY_MILLIS`) reads "E2, 0 cents flat" or "E2, 0 cents sharp".

## Fix

Land after plan 52: the sentences are the display's explored (non-live) description, `exactDescription` there.

1. Replace the two `<string>`s with `<plurals>` in place, in both files, keeping the comment:

   ```xml
   <!-- How a screen reader reads the meter: the note, then how far it is off, e.g. "E2, 12 cents flat". -->
   <plurals name="tuner_reading_flat">
       <item quantity="one">%1$s, %2$d cent flat</item>
       <item quantity="other">%1$s, %2$d cents flat</item>
   </plurals>
   <plurals name="tuner_reading_sharp">
       <item quantity="one">%1$s, %2$d cent sharp</item>
       <item quantity="other">%1$s, %2$d cents sharp</item>
   </plurals>
   ```

   ```xml
   <plurals name="tuner_reading_flat">
       <item quantity="one">%1$s, %2$d cent mély</item>
       <item quantity="other">%1$s, %2$d cent mély</item>
   </plurals>
   <plurals name="tuner_reading_sharp">
       <item quantity="one">%1$s, %2$d cent magas</item>
       <item quantity="other">%1$s, %2$d cent magas</item>
   </plurals>
   ```

   (Hungarian does not inflect after a numeral; both items are kept so the resource has the same shape in both files,
   as `import_replace_title` does.)

2. In `TunerDisplay`, round once and read zero as the note:

   ```kotlin
   val description = when {
       …                                  // reading == null (and plan 53's tone branch, if landed), unchanged
       else -> {
           val name = noteNameWithOctave(reading.note, notation)
           val cents = reading.cents.roundToInt()
           when {
               isInTune -> stringResource(Res.string.tuner_reading_in_tune, name)
               cents == 0 -> name
               cents < 0 -> pluralStringResource(Res.plurals.tuner_reading_flat, -cents, name, -cents)
               else -> pluralStringResource(Res.plurals.tuner_reading_sharp, cents, name, cents)
           }
       }
   }
   ```

   (`noteNameWithOctave` is plan 55's spoken name if it has landed.) Import
   `com.pandulapeter.campfire.presentation.localization.pluralStringResource` (never the
   `org.jetbrains.compose.resources` one) and `Res.plurals.tuner_reading_flat` / `_sharp`; drop the now unused
   `kotlin.math.abs` import and the `Res.string.tuner_reading_flat` / `_sharp` imports. Grep afterwards: nothing else
   reads the two keys.

No CLAUDE.md changes: nothing documented changes.

## Tests

None: the choice of item is the plugin's, and the branch is a Composable's. (If the executor prefers, the `when` can be
pulled into a pure `internal fun` returning which sentence and count to read, and tested; not required.)

## Manual check

TalkBack: hold a string 1 cent flat and touch the display: "E2, 1 cent flat". Bring it to within half a cent and touch
it before the check closes: "E2". Hungarian: "E2, 1 cent mély".
