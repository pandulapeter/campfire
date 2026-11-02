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

A song in one column that scrolls by itself (`SongLayout.CONTINUOUS`, Preferences' Layout), the other way of reading
than the pages `stepping/` turns. Paging cannot scroll on its own: in a row of columns the bottom of one and the top of
the next share a height, so any motion takes away what is still to be read.

## The layout

- An unmeasured single column, still cut into a chunk a line, so `SongRows` reports every line's top and how many lines
  each chunk plays (lyrics, tab or grid lines, an engraved staff system's bars two a line, none for a title).
- The step buttons stay (nudging, and going on to the setlist's songs, named Scroll up / down); the dots leave. Play /
  stop sits just above the next button (its triangle pointing down, unlike the metronome's), only while anything is left
  to play, its place kept when it is not; only while playing do the countdown and above it the speed slider grow out of
  it. Paused, the column holds nothing else: a still countdown and a slider by the edge read as a scrollbar.
- **The countdown** runs until the scroll can go no further (the last screen at rest, when playing stops), not until the
  last line passes the reading line; it follows every drag, fling, nudge, fold, and speed change.
- A fling is not capped to a step here.

## The pace

- **Time is spent on lines, not pixels**: each line takes the same time, its chunk's height moving past the reading line
  (a third down) in it; what plays no line (a folded section, a chunk of no height) passes at the song's average speed.
  A pinch, a resize, or a wrapped line changes the speed on screen, never the pace through the song.
- **A line's time**: the file's `{duration}` shared over its lines, else two bars at the playing tempo (overrides
  included) and the file's time signature, four seconds with neither.
- **The speed is a share of that pace**, per song (percent, 25–400, synced, none stored at 100), on a logarithmic slider
  faster downwards, its middle the song's own pace. Written once a drag ends; the dragged value plays meanwhile and
  holds until the store says the same. Read only mode writes it too (a device preference); an import preview never
  does, except that a one-song preview rewritten into its song once imported hands it over and writes it.

## What moves and what stops it

- **The clock is the scroll position**: each frame moves the song by the speed where it is, skipping frames anything else
  moves it in. A drag, fling, tap, or pedal only moves the reader; playing goes on. A relayout while playing holds the
  song until the reader is back in place, then plays on.
- Step keys, tap halves, and step actions nudge two chorded lines (smoothly, playing or not); a press mid-nudge nudges
  on from where it is headed. Past either end they go on to the setlist's neighbour as a step does.
- It stops only when asked, at the song's end, on another page, with the layout back to pages, under anything drawn over
  the screen, or on `ON_STOP` (another app, a minimised window, a hidden tab); it never starts by itself, nor again once
  what covered it has gone.
- The layout is asked over the first song opened (never by the onboarding), as the song details notes say.
