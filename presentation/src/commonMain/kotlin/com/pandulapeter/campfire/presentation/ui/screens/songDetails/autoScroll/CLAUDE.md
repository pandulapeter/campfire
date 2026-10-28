<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songDetails/autoScroll

A song laid out in one column that scrolls by itself (`SongLayout.CONTINUOUS`, Settings → Preferences' Layout, a
segmented control heading the Song page section), the other way of reading it than the pages `stepping/` turns. Paging cannot scroll on its own: in a row of
columns the bottom of one and the top of the next share a height, so any motion takes away what is still to be read.

### The layout

- `SongDetailsPage(isContinuous)` asks `SongLyrics` for an unmeasured single column (`isSingleColumn`, no
  `availableHeight`, no row viewport) still cut into a chunk a line (`canCutSections`), so `SongRows` reports every
  line's top and how many lines each chunk plays (`playedLines`: lyrics and tab or grid lines, and an engraved staff
  system's bars, two a line, none for a title).
- The step buttons stay (nudging, and going on to the setlist's songs at either end, named Scroll up / down); the dots
  leave. `AutoScrollControls` adds play / stop just above the next button (filled as the metronome's play button is,
  its triangle pointing down, unlike the metronome's), only while there is anything left to play (not for a song that
  fits the screen, nor at the end), its place kept when it is not; and only while playing, growing up out of it, the
  countdown and above that the speed slider, faster downwards as the song moves. Paused, the column holds nothing else:
  a still countdown and a slider by the edge read as a scrollbar.
- **The countdown** is the time until the scroll can go no further, which is when playing stops (the last screen at
  rest), not until the last line passes the reading line; it follows every drag, fling, nudge, fold, and speed change.
- A fling is not capped to a step there (`RowSnapFlingBehavior.isCapped`).

### The pace

- **Time is spent on lines, not pixels** (`AutoScrollTrack`): each line takes the same time, its chunk's height moving
  past the reading line (a third down what can be read) in it; what plays no line (a folded section, a chunk of no
  height) is passed at the song's average speed. A pinch, a resize, or a wrapped line changes the speed on screen, never
  the pace through the song.
- **A line's time** (`ownSecondsPerLine`): the file's `{duration}` shared over its lines, else two bars at the playing
  tempo (overrides included) and the file's time signature, four seconds with neither.
- **The speed is a share of that pace**, per song (`UserPreferences.autoScrollSpeeds`, percent, 25–400, synced, none at
  100), on a logarithmic slider faster downwards, its middle the song's own pace (`AutoScrollSpeedScale.kt`). Written once
  a drag ends; the dragged value plays meanwhile and holds until the store says the same. Read only mode writes it too
  (a device preference, not the library); an import preview's page never does, its dragged value lasting as long as
  it, except that a preview of one song, rewritten in place into its song once imported, hands it to that song and
  writes it (`SongAutoScrolling.followImportedPreview`).

### What moves and what stops it

- **The clock is the scroll position**: each frame moves the song by the speed where it is (`SongAutoScroller.play`),
  skipping frames anything else moves it in. A drag, a fling, a tap, or a pedal only moves the reader; playing goes on.
  A relayout while playing (a fold, a pinch, a transposition, a resize) holds the song until the reader is back in
  place, then plays on from there.
- Up, Down, Page Up, Page Down, the tap halves, and the step actions nudge two chorded lines (smoothly, playing or not);
  a press while one moves nudges on from where it is headed, so two quick presses are two nudges. Past either end they
  go on to the setlist's song beside it as a step does. Space plays and stops.
- It stops only when asked, at the end of the song, on another page, with the layout back to pages, under anything
  drawn over the screen (a sheet, a dialog, another screen), or on `ON_STOP` (another app, a minimised window, a hidden
  tab); it never starts by itself, nor again once what covered it has gone.

### Asking once

- The onboarding's Song layout page answers it by being shown (`markSongLayoutChosen`); anyone else is asked over the
  first song they open (`AskForSongLayoutOnce`, `SongLayoutSheet`), once, a pick written at once and closing the sheet.
