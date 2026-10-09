# Let SelectableChip grow with a large text size instead of clipping its label

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (system font scale; the app's own interface scale)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SelectableChip.kt`

## Problem

`SelectableChip` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SelectableChip.kt:79`
at b5c8ed3b5) fixes its height:

```kotlin
            .height(FilterChipDefaults.Height)
            .padding(horizontal = CHIP_PADDING),
```

`FilterChipDefaults.Height` is 32dp. The labels are `labelLarge` (14sp on a 20sp line), so from about 1.6x font scale
on the line is taller than the chip, and at 2.0x (40dp) the chip's `clip(FilterChipDefaults.shape)` cuts the top and
bottom off every label: the song filters, the song picker's tags and languages (`CountedFilterChip`), the Metronome
tab's time signatures and sounds, the tuner's instruments, strings and reference-tone chip. Material's own `FilterChip`
uses a minimum height for exactly this reason.

## Fix

```kotlin
            .defaultMinSize(minHeight = FilterChipDefaults.Height)
            .padding(horizontal = CHIP_PADDING),
```

(`androidx.compose.foundation.layout.defaultMinSize`; drop the `height` import if nothing else uses it.) The Row
already centers its content vertically, so a taller label grows the chip evenly.

Callers checked at 1.0x, where nothing changes because every chip's content is shorter than 32dp:
`CountedFilterChip` (an 18dp icon, a `labelLarge` and a `labelSmall`), `tuner/InstrumentChoice.kt`,
`tuner/TunerStrings.kt`, `tuner/ReferencePitchSetting.kt` (an 18dp `PlayStopMark` and a `labelLarge`, beside a 40dp
stepper in a centered row), `screens/metronome/SoundChoice.kt`, `metronome/TimeSignaturePicker.kt`. No parent gives
the chips a fixed height: `SortableChipRow` is a horizontally scrolling `Row` with only its divider sized to
`FilterChipDefaults.Height`, which may stay as it is (a divider shorter than a grown chip is fine).

## Tests

None: a modifier on a Composable.

## Manual check

- Android, Settings → Display → Font size at maximum (and display size large): Songs → filters, the song picker's
  chip rows, the Metronome tab's chips, the Tuner tab's instrument and string chips — no label is cut at the top or
  bottom, and chips in one row stay the same height.
- At the default font size, the same screens look exactly as before (compare with a screenshot of the previous build).
