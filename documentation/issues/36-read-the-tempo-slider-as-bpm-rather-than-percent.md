# Read the Metronome tab's tempo slider as BPM rather than as a percentage

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (screen readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/TempoSetting.kt`

## Problem

The tempo slider has a name but no state
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/TempoSetting.kt:84` at
b5c8ed3b5):

```kotlin
    val sliderDescription = stringResource(Res.string.metronome_tempo_slider)
    Slider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = sliderDescription },
        value = bpm.toFloat(),
        onValueChange = { onBpmChanged(it.roundToInt()) },
        valueRange = MetronomePattern.BPM_RANGE.first.toFloat()..MetronomePattern.BPM_RANGE.last.toFloat(),
    )
```
 Material's slider only reports a
`ProgressBarRangeInfo`, which TalkBack turns into a percentage of the range, so 120 BPM is read "Tempo in beats per
minute, 36 percent", and every volume-key adjustment is announced as another percentage. Nobody sets a metronome in
percent.

## Fix

Give the slider a state description in BPM, from the existing `song_details_tempo` string (`%1$s BPM` in both
`values/strings.xml` and `values-hu/strings.xml`; its argument is a string, as `screens/songDetails/SongDetailsAppBar.kt:240`
reads it):

```kotlin
    val sliderDescription = stringResource(Res.string.metronome_tempo_slider)
    val sliderState = stringResource(Res.string.song_details_tempo, bpm.toString())
    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = sliderDescription
                // The range info alone is read as a percentage of the range, which says nothing about a tempo.
                stateDescription = sliderState
            },
```

(`androidx.compose.ui.semantics.stateDescription`, in Compose 1.12.1.) `StateDescription` takes the place of the
percentage in TalkBack and is VoiceOver's value. No new string.

## Tests

None: semantics only.

## Manual check

- TalkBack, Metronome tab: focus the slider — "Tempo in beats per minute, 120 BPM, slider"; volume up / down (or swipe
  up / down) — "121 BPM", "122 BPM".
- VoiceOver on iOS: swipe up / down on the slider reads the BPM.
- Hungarian: "Tempó, ütés percenként, 120 BPM".
