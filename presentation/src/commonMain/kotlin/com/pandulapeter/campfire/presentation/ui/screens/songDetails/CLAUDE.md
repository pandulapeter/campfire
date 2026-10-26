<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songDetails

The song details screen. The song itself is drawn and laid out by `ui/songView/`; the pedal stepping is in
`stepping/`. What the first section holds and read only mode are in `ui/CLAUDE.md`; chord diagrams in `ui/chords/`;
the click in `ui/metronome/`.

The guarantee most of this file serves: **every line of a song can be reached with Up and Down alone** (a page turner
pedal), at any text size on any screen. `LayoutBudget` is its only exception.

### The songs it pages through

- `songDetailsSongsOf` resolves the destination against `songLookup` (the library and whether it has been read). Before
  the read it resolves only a destination whose songs are all present, so a restored setlist pager never restores its
  page index onto the wrong song over a partial batch.
- Once every song it names is gone from a read library, the screen closes itself.
- The pager bar numbers by place in the setlist, missing entries included; pages are only the songs that exist.

### The steppers

- Steppers are shorter than Material buttons and lower `LocalMinimumInteractiveComponentSize` to match (touch still gets
  48dp). Height is `STEPPER_HEIGHT` in a bar or menu, `songControlHeight` in a song; `trailing` holds the Tap segment.

### The app bar

- **The subtitle (`SongHeaderNote`) names the sounding key**, as the page and the Transposition control do (the
  transposition and the file's capo applied, `{key}` read by `SongKeys`, via `Song.chordsKey`), after a 16dp dot (the cards' 24dp would grow the bar and shrink the lyrics' room). Worked out for the song
  its crossfaded content was composed for, since a page change draws two. Chords off drop the key. No tempo (the first
  section says it) and no duration.
- **A file with no `{key}` is named by the key its chords are in** (`inferredKeyOf`), shown and moved as a written key
  is. `InferredSongKeys` works it out off the main thread as `SongTextStore` reads the text, before handing the text on,
  and keeps it by file name for the view model's life: a song reopened names it on its first frame; one never read
  gets it in the frame its lyrics arrive, crossfading in with them. The list cards never parse a file, so they name
  only a written key.
- **The title scrolls the song to its top, and at the top opens the About the song sheet** (its only way in). A chevron
  (`ic_expand`) shows while a tap would open it; its room is kept wherever the sheet is offered, so a long title is not
  cut afresh. The touch target (`titleTouchTarget`) is all the bar's free space but reports only the title's room. No
  ripple: a press dims the title, cover, and chevron. The scroll is read inside the title slot, so passing the top
  recomposes only it. Nothing is taken at the top where the sheet is not offered.
- The text size stepper is in the bar where it fits, else at the end of the menu (`MenuStepperRow` as `menuFooter`, the
  menu staying open while pressed). As the bar narrows the text size goes into the menu, then Choose setlists, then the
  cover, each while the title keeps 160dp (`appBarButtons`, from the settled width, for every page at once). Performance
  mode: the stepper in the bar where the title keeps 160dp (`showsFontScaleInPerformanceBar`), else alone in a menu; the
  cover only where the title keeps 160dp beside both (`showsCoverInPerformanceMode`).

### Keeping the display on

`Modifier.keepScreenOn()` on the root: a song is read with both hands on the instrument. It is the view flag on
Android, `idleTimerDisabled` on iOS, a Screen Wake Lock on the web (retaken when the tab is shown, secure contexts
only), and nothing on the desktop.

### Keyboard (`SongKeyboardShortcuts.kt`)

- Up and Down scroll a tenth of the viewport, linearly over the key repeat interval; Left and Right change the setlist's
  song. Where the step buttons are, Up, Down, Page Up, and Page Down press them instead (a Bluetooth page turner sends
  exactly these; a nudge leaves the player mid-verse); elsewhere the page keys are unconsumed. Counted from the page the
  pager is heading to, so two quick presses go two songs.
- The screen takes focus as it opens (nothing on it would otherwise), and the handler is in the preview pass, so it sees
  keys wherever focus has moved and the scroll container never handles an arrow.
- Left and Right are unconsumed at the setlist's ends and for a single song; all four with Alt, Meta, or Ctrl (Alt + Left
  is the web's Back, Cmd + Left / Right a Mac's Back and Forward).
- Ctrl/Cmd + E opens the editor, as the menu's Edit does: not in performance mode, which has no Edit.

### The metronome button

- `MetronomeButton` shows and hides the panel (`toggleMetronomePanel`): opening starts nothing, closing stops a playing
  click. M toggles the click from anywhere on the song, opening the panel with it.
