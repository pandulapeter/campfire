# Name the chord sheet's real file in ui/chords/CLAUDE.md and describe the song details metronome mark's real colors

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

## Problem

1. `ui/chords/CLAUDE.md` (~L17–18 at d650f3cb0) lists a file that does not exist:

   > **Chord diagrams** (`ui/chords/`, `screens/songDetails/SongChordsSection.kt`, `components/ChordDiagram.kt`,
   > `dialogs/ChordShapesSheet.kt`).

   `ui/dialogs/` holds `ChordShapeSheet.kt` (singular), and no `ChordShapesSheet.kt` exists anywhere in the module.

2. `ui/screens/songDetails/CLAUDE.md` contradicts itself and the code about the metronome button's colors. Under "On the
   song details screen" (~L666–669):

   > Its mark stays the
   > metronome, in the primary color while the panel is up (the light half of the app's own palette darkens the second accent
   > for text, and a filled mark in it reads as red) and pulsing on every heard beat unless the Animate switch is off — the
   > growing alone there, the mark being in that color already and the panel's beat row being right under it.

   The code: `MetronomeButton` (`ui/metronome/MetronomeButton.kt`) is a `SongToolButton(isSelected = isPanelShown)`, and
   `SongToolButton` (`ui/components/SongToolButton.kt`) draws a selected button as a `secondaryContainer` circle with
   `onSecondaryContainer` content (otherwise `surfaceContainerHighest` and `onSurface`). `MetronomeIcon`
   (`ui/metronome/MetronomeIcon.kt`) tints the mark with that content color and, on every beat, both grows it and lerps
   it towards `MaterialTheme.colorScheme.primary` as the pulse fades
   (`ColorFilter.tint(lerp(tint, pulseColor, beat.pulse.coerceIn(0f, 1f)))`), on the details screen as everywhere. So the
   mark is never "in the primary color while the panel is up", and the pulse is not "the growing alone". The same file's
   earlier paragraph (~L174–177) already says it correctly: "the metronome's drawn in the secondary container colour while
   its panel is up and the tuner's while its sheet is open".

## Fix

1. In `ui/chords/CLAUDE.md`, replace `` `dialogs/ChordShapesSheet.kt` `` with `` `dialogs/ChordShapeSheet.kt` `` in the
   sentence quoted above. Grep the module's other `CLAUDE.md` files for `ChordShapesSheet` and fix any other mention the
   same way (there was none at d650f3cb0).
2. In `ui/screens/songDetails/CLAUDE.md`, replace the quoted sentence ("Its mark stays the metronome, in the primary
   color while the panel is up … being right under it.") with:

   > Its mark stays the metronome, drawn in the button's content color — `onSecondaryContainer` on the secondary
   > container circle while the panel is up, the way the tuner's is while its sheet is open (`SongToolButton`) — and
   > pulsing on every heard beat unless the Animate switch is off, growing and leaning towards the primary color as the
   > pulse fades, the way the navigation bar's does.

   Match the sentence by its text, not by line number: lanes that merge before this one also edit this file. If the
   sentence has already been rewritten to agree with the code by then, drop this half of the plan. Leave
   `ui/metronome/CLAUDE.md` alone; its metronome wording belongs to another plan.

## Tests

None: documentation only.

## Manual check

None needed beyond reading: open a song with the metronome feature on, tap the metronome button, and confirm the circle
turns the secondary container color and the mark pulses towards the primary color on each click, as the new sentence
says.
