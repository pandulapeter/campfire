# Draw the heard bar's own accents while a change to another time signature waits for the barline, and ignore accent taps until it lands

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContext.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/MetronomePanelState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContextTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`

Execute after plan 22: this plan takes `Playing.pattern` to be the bar that is heard, which plan 22 makes true when two
song changes follow each other across a barline.

## Problem

`rememberMetronomePanelState` (`ui/screens/MetronomePanelState.kt` L52–56 at d650f3cb0) takes the panel's time
signature from the pattern that is heard, but its accents from the song the pager is heading for:

```kotlin
val timeSignature = (playback as? MetronomePlayback.Playing)?.pattern?.timeSignature
    ?: metronomeTimeSignatureOf(context, settings) { songsByFileName[it] ?: songsBeingRenamed[it] }
return MetronomePanelState(
    timeSignature = timeSignature,
    beatLevels = settings.beatLevelsOf(timeSignature, songBeatLevels = (context as? MetronomeContext.Song)?.let { songBeatLevels[it.songFileName] }),
```

When song A (4/4, with accents of its own) plays and the player pages to song B in 3/4, the click finishes A's bar
(`MetronomeSequencer.kt` L73 plays the old timing's levels while the signatures differ:
`level = if (pattern.timeSignature == timing.timeSignature) pattern.beatLevel(beatIndex) else timing.beatLevel(beatIndex)`),
and `Playing` is `pattern = A, pendingPattern = B`. For the rest of that bar the panel draws four beats with *B's* 4/4
accents — usually the tab's 4/4 ones — instead of A's that are heard.

A tap on the row in that window calls `viewModel.setBeatLevels(panel.timeSignature, levels)` (`SongDetailsAppBar.kt`
L415), and `MetronomeController.setBeatLevels` (L198–210) writes it for the context, B:
`preferences.beatLevels.withSongBeatLevels(context.songFileName /* B */, timeSignature /* 4/4 */, levels, settings)`. B
gains a stray `"4/4"` entry in `UserPreferences.beatLevels` (synced to every device), A's accents are not changed, and
nothing audible happens (B plays 3/4).

When the two signatures are the same the sequencer already plays B's accents from the next click, and the panel's
drawing of B's accents is right; only a change of signature has this window, and it lasts at most one bar.

## Fix

1. In `ui/metronome/MetronomeContext.kt`, next to `metronomeTimeSignatureOf`, add a KDoc'd pure helper:

   ```kotlin
   /**
    * The pattern of the bar being heard while a change to another time signature waits for it to end, or null when the
    * bar heard is the one the panel would draw anyway. The click finishes that bar with its own accents, which belong
    * to the song (or the stretch of it) being left, so the panel draws them and takes no taps until the change lands.
    */
   internal val MetronomePlayback.barBeingLeft: MetronomePattern?
       get() = (this as? MetronomePlayback.Playing)?.takeIf { playing ->
           // No smart cast: Playing is declared in another module.
           playing.pendingPattern?.let { it.timeSignature != playing.pattern.timeSignature } == true
       }?.pattern
   ```

   (Same signature: the sequencer already plays the new pattern's accents, so null.)
2. `rememberMetronomePanelState`: when `playback.barBeingLeft` is non-null, use its `timeSignature` and
   `List(it.timeSignature.beats) { index -> it.beatLevel(index) }` as the beat levels (`beatLevel` pads a list of
   another length); otherwise keep today's computation. Update its KDoc ("A playing click draws the bar that is heard
   instead…") to say the accents are the heard bar's too while a change of signature waits.
3. `MetronomeController.setBeatLevels`: return at once when `metronome.playback.value.barBeingLeft != null`, with a
   comment: the bar on screen is the one being left, whose song is no longer the context, so a tap could only be written
   under the wrong song; it lasts until the barline. (The check is at the write rather than in the composable so that a
   tap composed a frame before the change is caught too.)

Options considered: routing the tap to the song being left would need the engine's pattern to carry which song it was
played for, or the controller to remember the context of every pattern it sent — more state for a window of at most one
bar; not recommended. Showing the row disabled would add a visible state change for less than two seconds; not
recommended either.

Update `ui/metronome/CLAUDE.md`, the paragraph under "A later `{tempo}` or `{time}` is a change from where it stands":
"**Until then the screen says what is heard**: the beat row draws the bar heard (`Playing.pattern`)" gains "with its own
accents where its signature differs from the one waiting (`barBeingLeft`), and a tap on it is ignored, since the song it
belongs to is no longer the one the accents would be written for".

Note: within one song, a stretch with another `{time}` takes the same path — the bar being left is the song's own
earlier stretch, whose accents the panel already draws correctly today; the only change there is that a tap in that one
bar is ignored rather than stored. Acceptable for the window it lasts.

## Tests

In `MetronomeContextTest` (pure, `commonTest`), `the bar being left is only one of another signature`:

- `MetronomePlayback.Stopped().barBeingLeft` is null;
- `Playing(a).barBeingLeft` is null, with `a = MetronomePattern(bpm = 100, beatLevels = listOf(ACCENT, MUTED, NORMAL, MUTED))`;
- `Playing(a, pendingPattern = MetronomePattern(bpm = 90)).barBeingLeft` is null (same 4/4);
- `Playing(a, pendingPattern = MetronomePattern(bpm = 90, timeSignature = TimeSignature(3, 4))).barBeingLeft` is `a`,
  and its levels as the panel builds them are `[ACCENT, MUTED, NORMAL, MUTED]`.

Run `./gradlew :presentation:desktopTest`.

## Manual check

Give song A (4/4) accents of its own on the song details panel (e.g. mute beats 2 and 4) and put it before a 3/4 song B
in a setlist. Play the metronome on A and page to B early in a bar: until the barline the panel should keep A's muted
beats 2 and 4, then slide to B's three beats. Tap a beat in that moment: nothing should change, and after a sync B should
have no 4/4 accents of its own (check `preferences.json`'s `beatLevels`).
