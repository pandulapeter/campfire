# Show the tuner button in one place while the instrument chips fade in or out

**Challenged:** amended — made the fix one concrete expression (`chipsVisibility.targetState || chipsVisibility.currentState`)
and dropped the "else crossfade" branch: neither `SongPlayingControlsRow` nor `SongPlayingMetadata` fades a tool button
(`BalancedRows` / `PlayingControl` only spring the items that move; a button comes and goes in one frame, as when the
metronome is switched on or off), so the controls row drops or takes the tuner at once and its neighbours spring.
Checked against `198023d81` (untouched: same `FlowRow` and padding) and the first-frame rule (state starts idle).

**Kind:** bug (UI rule: every user-caused change is animated)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/firstSection/SongMetadataSection.kt`,
`.../firstSection/CLAUDE.md` (tool buttons bullet)

## Problem

In `SongMetadataSection`, the chips row fades out with its tuner button still composed:

```kotlin
AnimatedVisibility(
    visible = instrumentChips != null,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    ...
        lastChip.content?.invoke()
        if (playingTools != null) {
            SongPlayingToolButtons(
                tools = playingTools,
                // Only the tuner ever stands here, wherever the values put the metronome.
                places = playingTools.places(hasInstrumentChips = true, hasChordValues = false, hasClickValues = false),
                place = PlayingToolPlace.AFTER_INSTRUMENT_CHIPS,
```

while the rows below are told the chips are gone at once:

```kotlin
SongPlayingMetadata(
    ...
    hasInstrumentChips = instrumentChips != null,
)
...
SongPlayingControlsRow(
    ...
    hasInstrumentChips = instrumentChips != null,
)
```

`playingToolPlaces` then puts the tuner after the chord or click values immediately. Turning chords off from the song
details screen shows two tuner buttons for the length of the fade; turning them on removes the tuner from the controls
row while a second one fades in above.

## Fix

In `SongMetadataSection`, drive the chips' `AnimatedVisibility` from a hoisted state and derive the tuner's place from it:

```kotlin
val chipsVisibility = remember { MutableTransitionState(instrumentChips != null) }
    .apply { targetState = instrumentChips != null }
// The tuner stands with the chips for as long as they are drawn, fading in and out with them, and joins the values
// only once they have gone: one button at a time.
val isTunerWithChips = chipsVisibility.targetState || chipsVisibility.currentState
AnimatedVisibility(visibleState = chipsVisibility, enter = fadeIn(), exit = fadeOut()) { /* as today */ }
...
SongPlayingMetadata(..., hasInstrumentChips = isTunerWithChips)
SongPlayingControlsRow(..., hasInstrumentChips = isTunerWithChips)
```

`MutableTransitionState.currentState` stays `true` until an exit finishes, and turns `true` only when an enter finishes,
so:

- **Chips leaving:** the tuner fades out with them; when the fade ends, the controls row takes it in the same frame the
  chips' row leaves the layout, and its other items spring (`isAnimated`), as for any tool button arriving.
- **Chips arriving:** the tuner leaves the controls row at once (neighbours spring) and fades in above with the chips.
- **Interrupted:** a reversal mid-fade keeps `targetState || currentState` true throughout; the tuner never shows twice.
- **First frame:** the state starts at its own target (idle), so the first frame places the tuner where it belongs with
  nothing animated, as the tuning chip's `MutableTransitionState` in `SongLyrics` does.

`198023d81`'s narrow-screen fix (the chips' `FlowRow` starting `PICKER_CHIP_START_PADDING` short of the keyline, the
tuner composed inside that `FlowRow`) is not touched. The section's measured size is keyed by `RenderSection.Metadata`
(whose `instrumentChips` changes when the chips are switched, not when their fade ends); the fix moves the tuner's
arrival in the controls row to the frame the chips' row already leaves the layout, so it adds no size change at a
moment that was not one already. Check by eye that the sections below do not jump at the end of the fade.

Update `firstSection/CLAUDE.md`'s tool buttons bullet: "the tuner after the instrument and tuning chips where they are
shown (while they fade, too), else ...".

## Tests

None worth it: a frame-by-frame state of an animation. Check by eye on the desktop build: song details, the chords
switched off and on again from the details screen (one tuner button throughout, no double), and a quick off-on
reversal mid-fade.
