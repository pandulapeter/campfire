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

### Scrolling, stepping, and snapping

- **A fling rests the top of the screen exactly below a row's divider** (`RowSnapping.kt`, rows from `SongRows`); a row
  taller than the screen is landed on at its top and then scrolls freely. Rows are measured by content, never trailing
  space. The fling finishes the sub-pixel remainder. The top fade is the scroll past the current row's top
  (`SongRows.scrolledIntoRow`), so a rested row is unfaded. Dividers come from the lookahead pass, not mid-animation.
- **Step buttons** (`StepButtons`, one pair over the whole pager, answering the current page's `SongStepper`): stops are
  rows or pages, or sections in an unpaged single column, each stop `EDGE_FADE_SIZE` above its section. `SongStepper`
  scans offsets without sorting or allocating per scroll update.
- Inside a stop taller than the screen a step pages (`nextStepTarget`, `previousStepTarget`, `ReadingWindow`): the screen
  less the top fade, the bars, and two lines (at most a third), adjusted to start at a line (`lineTops`); it moves on only
  once the stop's end is in view. Buttons read "Scroll down" / "Scroll up" while paging.
- A press moves at least a line where that skips nothing (near-equal clamped stops are chained); the last press hands off
  only once the whole song is on screen.
- **A press mid-step steps on from its target**, so two quick pedal presses are two steps.
- Previous leaves only at the very top; next only once the end has been on screen; a song that fits has neither. In a
  setlist they go on to the next song's top and the previous song's end (that page's scroll is set to its end
  beforehand, else a second press skips it).
- **They never cover a line**: a scrolling song is laid out narrower (`stepButtonInset`); the full width is kept where the
  song fits there as one row and the full-width grid does too (`isReadWithoutStepping`), never in a setlist
  (`keepsStepButtonInset`). An unpaged single column is assumed to scroll.
- The dots (`StepProgressIndicator`) count reachable stops (`reachableStops`), the top padding belonging to the first
  (`SongRows.belowPadding`). The stop is read through derived state so it recomposes per stop, not per pixel; drawn and
  faded by color, not a layer, for the web build (see `FastScroller`).
- **Tap to step** (`stepOnTap`): top half previous, bottom half next, only for taps nothing inside consumed; never a long
  press, a right or middle click, a fling-stopping press, or one the system cancelled (Android's home and back gestures
  arrive as a consumed release). A drag under 250 ms and a tenth of the height is still a tap (a strumming hand slides),
  stepping from where the song was at finger-down and replacing the fling.
- **A fling goes no further than one step** (`oneStepCappedTarget`); a drag goes anywhere.
- Focus: the buttons take none, and the screen retakes it when lost to nothing while uncovered (`isUncovered`), or
  Compose spends the next arrow on a focus search, which scrolls. While something is over it the step keys are
  swallowed: the export screen shares the window and takes focus itself (`focusTarget()` on its root).
- **A held step key is one step until released** (window focus loss counts as a release): key repeats would chain pages.
  A held arrow still scrolls where there are no buttons.

#### Keeping the reader in place

- The scroll keeps pixels across a resize, pinch, fold, or transposition, so `RowSnapFlingBehavior.keepReaderInPlace`
  records at rest a `ReadingAnchor` (the stop's first section and the offset past it) and, inside a tall stop, a
  `LineAnchor` (section, chunk, fraction). Once a new layout has held still for 250 ms it scrolls there, snapped by
  `settledScrollTarget`. The wait prevents flicker during a pinch; anchors are taken only at rest and never from its own
  moves, so a pinch through many layouts returns to the starting row.
- A height-only change (metronome panel, title row collapse) only changes row trailing padding, so it is followed in the
  same layout pass (`onRowsPlaced`), before the scroll places the song: the divider never slides into view. Only the
  current page follows frame by frame; pages beside it use `pageViewportHeight`.

### Text size and the steppers

- `fontScale` is snapshot state on the view model, not a flow, so a pinch recomposes only its readers. Saved to
  `UserPreferences.fontScale` debounced; a pending value is written on `onCleared` and before the desktop exits. The
  stored value wins whenever nothing local is pending, so Settings, a restore, or sync are followed.
- Pages beside the current one read `settledFontScale` (held still 500 ms; a stepper or shortcut sets it at once; a
  swipe settles it, so the song being left does not jump back).
- Kept in whole percent, so sub-percent pinch frames lay nothing out again; `FontScaleAccumulator` adds the wheel's
  unrounded deltas, since each fraction would otherwise round away.
- `fontScaleGestures`: pinch damped by an exponent; Ctrl / Cmd + wheel counted in notches (`verticalWheelNotches`, the
  web reporting pixel or line deltas); both consumed in the initial pass. A touchpad pinch the platform reports (macOS
  desktop, browsers' Ctrl-less wheel) goes to `magnifyByTouchpad` with the same `PINCH_SENSITIVITY`. Ctrl / Cmd + plus,
  minus, and zero are answered by the desktop window and the web page (`zoomSongText`), since the browser acts first.
- Steppers are shorter than Material buttons and lower `LocalMinimumInteractiveComponentSize` to match (touch still gets
  48dp). Height is `STEPPER_HEIGHT` in a bar or menu, `songControlHeight` in a song; `trailing` holds the Tap segment.
- **The transposition wraps around the octave** (`wrapTransposition`, −5 to +6), when stepped and when read, so its
  buttons are never disabled.
- The app bar: the text size stepper where it fits, else at the end of the menu (`MenuStepperRow` as `menuFooter`, the
  menu staying open while pressed). As the bar narrows the text size goes into the menu, then Choose setlists, then the
  cover, each while the title keeps 160dp (`appBarButtons`, from the settled width, for every page at once). Performance
  mode: the stepper in the bar where the title keeps 160dp (`showsFontScaleInPerformanceBar`), else alone in a menu; the
  cover only where the title keeps 160dp beside both (`showsCoverInPerformanceMode`).

### The app bar

- **The subtitle (`SongHeaderNote`) names the sounding key**, transposition and capo applied (unlike the Transposition
  control), and inside a setlist the duration, after 16dp dots (the cards' 24dp would grow the bar and shrink the
  lyrics' room). Worked out for the song its crossfaded content was composed for, since a page change draws two. Chords
  off drop the key. No tempo: the first section says it.
- **The title scrolls the song to its top, and at the top opens the About the song sheet** (its only way in). A chevron
  (`ic_expand`) shows while a tap would open it; its room is kept wherever the sheet is offered, so a long title is not
  cut afresh. The touch target (`titleTouchTarget`) is all the bar's free space but reports only the title's room. No
  ripple: a press dims the title, cover, and chevron. The scroll is read inside the title slot, so passing the top
  recomposes only it. Nothing is taken at the top where the sheet is not offered.

### Keeping the display on

`Modifier.keepScreenOn()` on the root, since a song is read with both hands on the instrument: the view flag on Android,
`idleTimerDisabled` on iOS, a Screen Wake Lock on the web (retaken when the tab is shown, secure contexts only), nothing
on the desktop.

### Keyboard (`SongKeyboardShortcuts.kt`)

- Up and Down scroll a tenth of the viewport, linearly over the key repeat interval; Left and Right change the setlist's
  song. Where the step buttons are, Up, Down, Page Up, and Page Down press them instead (a Bluetooth page turner sends
  exactly these; a nudge leaves the player mid-verse); elsewhere the page keys are unconsumed. Counted from the page the
  pager is heading to, so two quick presses go two songs.
- The screen takes focus as it opens (nothing on it would otherwise), and the handler is in the preview pass, so it sees
  keys wherever focus has moved and the scroll container never handles an arrow. The trade is 2D focus traversal; Tab
  still works.
- Left and Right are unconsumed at the setlist's ends and for a single song; all four with Alt, Meta, or Ctrl (Alt + Left
  is the web's Back, Cmd + Left / Right a Mac's Back and Forward).
- The pager bar numbers by place in the setlist, missing entries included; pages are only the songs that exist.

### The metronome button

- `MetronomeButton` shows and hides the panel (`toggleMetronomePanel`): opening starts nothing, closing stops a playing
  click. M toggles the click from anywhere on the song, opening the panel with it.
- The pendulum (`MetronomeIcon`, `ic_metronome_body` + `ic_metronome_pendulum`, `rememberMetronomeIconBeat`; the
  navigation item too) is at an arc end on every heard beat and swings over one beat at the tempo read from `playback`,
  each beat restarting the swing from where it is, so it never drifts from the ear. The Animate switch stills it.
- `TempoStepper` is highlighted while overridden and reset by a tap on its value; Tap sits in its `trailing` slot, since
  it sets the number the stepper steps.
