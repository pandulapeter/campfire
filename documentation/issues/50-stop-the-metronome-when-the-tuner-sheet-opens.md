# Stop the metronome when the tuner sheet opens over a song, as the export screen does

**Decided:** the user took this plan on 2026-10-09; its condition below no longer applies.
**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** iOS, Android (the mic hearing the clicks: all)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

> Written as the recommended answer to a pending user decision. **Drop this plan if the user decides to keep the click
> playing under the tuner sheet.**

## Problem

The tuner sheet (`DialogType.Tuner`) is opened from the song details overflow menu, where a click can be playing from
the metronome panel. Only the export screen stops a click as it is put up (`CampfireViewModel.kt:333-337` at b5c8ed3b5):

```kotlin
addBeforeDialogChange { _, dialogType ->
    // The export screen covers the song the click is played from as a screen of its own would, and leaves no way
    // to stop it, so it stops a click the way pushing a destination does (see updateBackStack).
    if (dialogType is DialogType.Export) metronome.stop()
}
```

Nothing else stops it for `DialogType.Tuner`, so the click goes on under the sheet, and the sheet starts listening
(`TunerListeningEffect`) the moment it is up:

- **iOS**: `tuner/implementation/src/iosMain/.../IosTunerSession.kt:53` switches the shared `AVAudioSession` to
  `AVAudioSessionCategoryPlayAndRecord` / `AVAudioSessionModeMeasurement` underneath the running metronome engine
  (which set `AVAudioSessionCategoryPlayback` in `AudioOutput.ios.kt:86`), and on release calls
  `setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation)` (`IosTunerSession.kt:69`), deactivating
  the session the click is still playing through.
- **Android**: a string or reference tone requests `AUDIOFOCUS_GAIN_TRANSIENT` (`AndroidToneOutput.kt:47`), which the
  metronome's focus listener (`AudioOutput.android.kt:71-72`) answers with `MetronomeStopReason.AUDIO_INTERRUPTED` —
  the click stops with an "interrupted" message the user did not cause.
- **Everywhere**: the microphone hears the clicks, so the meter jumps to every beat.

`documentation/plans/tuner.md` §6 states the invariant this breaks: "A metronome click | Cannot be playing: every way
onto another tab stops it. The two never hold the audio together." It was written before the sheet existed.

**Starting a click while the sheet is up is not possible**, so stopping it on open is enough: the sheet is modal
(the panel's play button is under the scrim), `MetronomeController.toggleMetronomeByKey` returns early while
`dialogHost.visibleDialog.value != null`, and the Android media session only offers `ACTION_STOP`, `ACTION_PAUSE` and
`ACTION_PLAY_PAUSE` on a click that is playing (`CampfireMetronomeService.kt:74`) — once stopped, the notification goes
with it.

## Fix

Extend the existing listener rather than adding one, and say why in its comment:

```kotlin
addBeforeDialogChange { _, dialogType ->
    // The export screen covers the song the click is played from as a screen of its own would, and leaves no way
    // to stop it, so it stops a click the way pushing a destination does (see updateBackStack). The tuner sheet
    // listens to the room through the audio session the click plays in, and the two never hold the audio together.
    if (dialogType is DialogType.Export || dialogType is DialogType.Tuner) metronome.stop()
}
```

The panel stays up (it is a preference, `MetronomeSettings.isSongPanelShown`), as it does after the export screen.

Docs:
- `ui/metronome/CLAUDE.md`, the "Three rules stop a click outright" paragraph: "`setVisibleDialog`, as the export
  screen or the tuner sheet is dealt in over the song …"; and in "The click belongs to the screen it is played from"
  add "the tuner sheet" to the list of what stops it.
- `ui/tuner/CLAUDE.md`: one sentence after the `TunerController` bullet's "Nothing of the tuner outlives its screen":
  "Opening the sheet stops a click playing on the song, since the two never hold the audio together."

## Tests

None: the rule is a dialog listener in the view model, which is not tested (only pure helpers are).

## Manual check

On an iPhone and an Android phone: open a song, start the click from the metronome panel, open Tuner from the overflow
menu. The click stops at once without an "interrupted" message; the sheet listens and the meter does not jump to beats.
Close the sheet: the panel is still up, and its play button starts the click again.
