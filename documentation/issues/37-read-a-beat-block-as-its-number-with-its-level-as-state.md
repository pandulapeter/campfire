# Read a metronome beat block as "Beat 1" with its level as its state, and say what tapping it does

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (screen readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/BeatRow.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`

## Problem

Each beat of the bar (the Metronome tab's row and the song details panel's) is a column with a `Role.Button`, and
everything about it is one content description
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/BeatRow.kt:191` at b5c8ed3b5):

```kotlin
    val description = stringResource(
        Res.string.metronome_beat_description,
        index + 1,
        stringResource(
            when (level) {
                BeatLevel.ACCENT -> Res.string.metronome_level_accent
                BeatLevel.NORMAL -> Res.string.metronome_level_normal
                BeatLevel.MUTED -> Res.string.metronome_level_muted
            }
        ),
    )
    ...
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLeaving,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (isLeaving) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = description }),
```

with `metronome_beat_description` = `Beat %1$d, %2$s` / `%1$d. ütés, %2$s`. Double-tapping a beat moves it from
accent to plain to muted, but a change of a content description is not announced as a state change, so the user hears
nothing and has to move away and back to learn what the beat is now. Nor is it said what double-tapping does
("double-tap to activate").

## Fix

Split the name from the state and label the action:

```kotlin
    val name = stringResource(Res.string.metronome_beat, index + 1)
    val levelName = stringResource(
        when (level) {
            BeatLevel.ACCENT -> Res.string.metronome_level_accent
            BeatLevel.NORMAL -> Res.string.metronome_level_normal
            BeatLevel.MUTED -> Res.string.metronome_level_muted
        }
    )
    val changeLabel = stringResource(Res.string.metronome_beat_change)
    ...
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLeaving,
                onClickLabel = changeLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isLeaving) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    // The level is the beat's state rather than part of its name, so that a tap is heard as the level
                    // it moved the beat to.
                    Modifier.semantics {
                        contentDescription = name
                        stateDescription = levelName
                    }
                },
            ),
```

New strings, next to the metronome's `metronome_level_*` keys (replace the `metronome_beat_description` line and its
comment), both files:

```xml
<!-- One beat of the bar as a screen reader names it; how it sounds (metronome_level_…) is read as its state. -->
<string name="metronome_beat">Beat %1$d</string>
<!-- What tapping a beat does, read by a screen reader as "double-tap to change the accent". -->
<string name="metronome_beat_change">Change the accent</string>
```
```xml
<!-- One beat of the bar as a screen reader names it; how it sounds (metronome_level_…) is read as its state. -->
<string name="metronome_beat">%1$d. ütés</string>
<!-- What tapping a beat does, read by a screen reader as "double-tap to change the accent". -->
<string name="metronome_beat_change">Hangsúly módosítása</string>
```

**Remove `metronome_beat_description` from both files**: `BeatRow.kt` is its only user (`grep -rn
metronome_beat_description presentation/src` finds nothing else at b5c8ed3b5). The `metronome_level_*` keys stay,
now read as the state.

## Tests

None: semantics only.

## Manual check

- TalkBack, Metronome tab (4/4): focus beat 1 — "Beat 1, accent, button, double-tap to change the accent"; double-tap —
  "plain" is announced; again — "muted"; again — "accent".
- The same in the song details screen's metronome panel.
- Hungarian: "1. ütés, hangsúlyos", and TalkBack's double-tap hint ends in "Hangsúly módosítása".
- Change the time signature from 4/4 to 3/4: the leaving fourth block is not focusable or read while it shrinks away.
