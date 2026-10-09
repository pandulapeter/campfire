# Group the single-choice chip rows with selectableGroup(), so a screen reader reads them as one choice

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all (screen readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/InstrumentChoice.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/SoundChoice.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/TimeSignaturePicker.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (`## Accessibility`)

Lane A owns `tuner/InstrumentChoice.kt` for this change; lane P does not touch it.

## Problem

Three rows of chips are a choice of one — every chip in them is a `SelectableChip(role = Role.RadioButton, …)` — but
the `FlowRow` holding them carries no group semantics, at b5c8ed3b5:

- `tuner/InstrumentChoice.kt:43` — chromatic and the eight presets:
  ```kotlin
  ) = FlowRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  ```
- `screens/metronome/SoundChoice.kt:40` — the five click sounds, the same `FlowRow` modifier.
- `metronome/TimeSignaturePicker.kt:59` — the six common time signatures:
  ```kotlin
      FlowRow(
          modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
  ```

So TalkBack reads each chip as a lone radio button ("Guitar, not selected, radio button") and never says that the row is
one choice with its position in it ("in list, 9 items"), and VoiceOver lacks the grouping too. The app already groups
its radio lists (`screens/settings/GeneralSection.kt:127` and `SongDisplaySection.kt:120`,
`Column(modifier = Modifier.selectableGroup())`). The tuner's string chips (`tuner/TunerStrings.kt`) are
`Role.Button` toggles and the filter chips `Role.Checkbox`, so they are not single choices and are left alone.

## Fix

Add `.selectableGroup()` (`androidx.compose.foundation.selection.selectableGroup`) to the three `FlowRow` modifiers:

```kotlin
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
```

and in `TimeSignaturePicker` only on the chips' `FlowRow`, not on the `Column` that also holds the two steppers (they
set any other signature and are not options of the group).

Add to `ui/CLAUDE.md`'s `## Accessibility`: "A row of `Role.RadioButton` chips is a `selectableGroup()`, as the radio
lists are."

## Tests

None: semantics only.

## Manual check

- TalkBack: Tuner tab → Instrument — a chip is read with the group ("… radio button, 2 of 9" / "in list"); the same on
  the Metronome tab's Sound and Time signature chips, and on the Song defaults sheet's time signatures.
- The chips look and work as before.
