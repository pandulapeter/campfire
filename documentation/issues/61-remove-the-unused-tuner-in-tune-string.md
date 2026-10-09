# Remove the unused `tuner_in_tune` string from both languages

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`

## Problem

`strings.xml:388` and `values-hu/strings.xml:388` at b5c8ed3b5:

```xml
<string name="tuner_in_tune">In tune</string>
```

```xml
<string name="tuner_in_tune">Tiszta</string>
```

Nothing reads it. `grep -rn "tuner_in_tune\b"` over the repository (excluding `build/`) finds only these two lines;
every Kotlin hit of `tuner_in_tune` is `tuner_reading_in_tune` (`TunerDisplay.kt:43`, `:75`). `TunerStrings.kt` reads
only `tuner_string`, `TunerMeter.kt` only `tuner_flat` and `tuner_sharp`, and no Swift, Android resource or script
names it. The in-tune state is drawn as the meter's check and read as `tuner_reading_in_tune`. The code-style skill asks
that an unused key goes from every locale file.

## Fix

Delete the line from both files. Leave its neighbours `tuner_flat` / `tuner_sharp` (the meter's two end labels) and
the comment above them in place. Re-run the grep afterwards (it must find nothing), since plan 52 adds keys nearby and
reuses `tuner_reading_in_tune`, not this one.

No CLAUDE.md changes.

## Tests

None. The build (resource accessor generation) is the check: a remaining reference would fail to compile.

## Manual check

None beyond the build; the Tuner tab in tune still shows the check and reads "…, in tune".
