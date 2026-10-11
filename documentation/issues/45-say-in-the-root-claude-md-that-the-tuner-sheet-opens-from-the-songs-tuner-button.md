# Say in the root CLAUDE.md that the tuner sheet opens from the song's tuner button, and from the menu only as a fallback

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `CLAUDE.md` (the repository root)

## Problem

The root `CLAUDE.md`'s Tuner section (~L385 and ~L390 at d650f3cb0) still describes the tuner sheet as a menu entry:

> A fourth tab, and a sheet over the song details screen opened from its menu, hearing one note at a time through the
> microphone and playing reference tones.

> The Tuner switch in Settings → Features takes the tab, the sheet's menu entry and every way the app could ask
> for the microphone.

The way in is now the `TunerButton` in the song's own first section, next to the capo or the transposition
(`screens/songDetails/SongPlayingTools.kt`, `hasTuner = onTunerClicked != null`). The menu entry exists only as a
fallback: `SongDetailsAppBar.kt` builds
`val tunerAction = if (isTunerEnabled && !shouldShowChords && !isMetronomeEnabled) tunerAction(...) else null`, and
`ui/tuner/TunerAction.kt`'s KDoc says it is "for a song with no first section to hold the [TunerButton]: with both the
chords and the metronome switched off there is nothing played left to put it next to." The second sentence also lacks
the serial comma. The rest of the root file's tuner wording (the Product rules line, the module table) is still right.

## Fix

Match by text (other lanes may have edited nearby lines):

1. Replace "a sheet over the song details screen opened from its menu," with "a sheet over the song details screen
   opened from the tuner button in the song's first section (from its menu where the chords and the metronome are both
   switched off, which leaves that section nothing to hold the button beside),".
2. Replace "takes the tab, the sheet's menu entry and every way the app could ask for the microphone." with "takes the
   tab, the sheet's button and menu entry, and every way the app could ask for the microphone."

Leave `ui/tuner/CLAUDE.md` and `tuner/implementation/CLAUDE.md` alone; their tuner wording belongs to another plan.

## Tests

None: documentation only.

## Manual check

None.
