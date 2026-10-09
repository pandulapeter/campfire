# Make every Stepper button carry the value it steps, so a screen reader says the new value after each step

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (screen readers: TalkBack, VoiceOver, the desktop's)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Stepper.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/TempoStepper.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (new `## Accessibility` section),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md` (only if it describes `Stepper`'s parameters)

Paths below are relative to `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

Every stepper of the app (tempo, transposition, capo, text size, the editor's, the Metronome tab's beats and note
value, the tuner's reference pitch, the print font size and margins, the Chord shapes sheet) is a `Stepper`
(`components/Stepper.kt`). Its two buttons are plain `IconButton`s whose only semantics is the icon's label
(`components/Stepper.kt:198` at b5c8ed3b5):

```kotlin
IconButton(
    modifier = Modifier.size(width = buttonLength, height = height),
    enabled = isEnabled,
    interactionSource = interactionSource,
    onClick = { if (holdState.hasRepeated) holdState.hasRepeated = false else onClick() },
) {
    Icon(
        modifier = Modifier.size(ICON_SIZE * fontScale),
        painter = icon,
        contentDescription = label,
    )
}
```

The value is a separate `Text` node in `StepperValue` (`text = label.value`, line 243). A screen reader user who
double-taps "Faster" hears nothing afterwards: the focused node (the button) did not change, and the value that did is
a node the focus is not on. To learn the new tempo they have to swipe to the value and back for every step.

## Fix

1. Give `StepperButton` the stepper's value and set it as the button's state:

   ```kotlin
   @Composable
   private fun StepperButton(
       icon: Painter,
       label: String,
       stateDescription: String,
       isEnabled: Boolean,
       ...
   ) {
       ...
       IconButton(
           modifier = Modifier
               .size(width = buttonLength, height = height)
               // The value is what a step changes, so the button says it: a screen reader re-reads the state of the
               // node it is on, and the value is a node of its own the focus is not on.
               .semantics { this.stateDescription = stateDescription },
           ...
   ```

   (`import androidx.compose.ui.semantics.semantics`, `import androidx.compose.ui.semantics.stateDescription`; both
   exist in Compose Multiplatform 1.12.1 — `SemanticsProperties.StateDescription`, already used in
   `screens/songDetails/StepProgressIndicator.kt`.) A change of `StateDescription` on the focused node is announced by
   TalkBack and read as the element's value by VoiceOver. No live region: it would announce every change, including
   the ones the slider and a held button make many times a second.

2. Add an optional `valueDescription: String? = null` parameter to `Stepper`, documented in its KDoc
   ("How a screen reader says [value], where its written form reads badly aloud — `1 / 24`, a bare number of BPM;
   the value itself when left out"). Pass `valueDescription ?: value` as `stateDescription` to both buttons, and in
   `StepperValue` give the value `Text` `Modifier.semantics { contentDescription = valueDescription }` when it is not
   null, so the value node reads the same as the buttons.

3. Use it at once in `metronome/TempoStepper.kt`, whose value is a bare number:

   ```kotlin
   value = bpm.toString(),
   valueDescription = stringResource(Res.string.song_details_tempo, bpm.toString()),
   ```

   (`song_details_tempo` is `%1$s BPM` / `%1$s BPM`, already read with `stringResource` and a number elsewhere, e.g.
   `screens/songDetails/SongDetailsAppBar.kt:240`.) Plan 34 uses the same parameter for the Chord shapes sheet.

4. Start a `## Accessibility` section in `ui/CLAUDE.md` (after `## Haptics`) with the rule: "A stepper says its value:
   both of a `Stepper`'s buttons carry it as their state description, so a step is heard from the button that made
   it; `valueDescription` gives it a spoken form where the written one reads badly." Plans 31, 41 and 42 add their
   rules to the same section.

## Tests

None: the change is semantics on a Composable, and the UI is untested by code (root `CLAUDE.md`).

## Manual check

- Android with TalkBack: Metronome tab, focus "Faster", double-tap three times — each tap is followed by "121 BPM", "122 BPM",
  "123 BPM". Same on a song's tempo, transposition and capo steppers,
  and on the tuner's reference pitch ("A4 = 441 Hz").
- iOS with VoiceOver: the same steps read the value after each double-tap.
- Hold a button down with TalkBack's touch exploration off: nothing is announced per repeated step beyond what TalkBack
  does for a focused node's state (no flood of announcements).
- Sighted check: nothing looks different.
