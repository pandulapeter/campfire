<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: turning a song by one column

**Status:** not started. `ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in
the root `CLAUDE.md`.

## Why

A user reading on a 13" 4K Surface asked for it: with two columns on screen, "next" should move the left column off
and slide the others left, "so your eyes can scroll with the chord sheet without missing a note. Especially handy for
hectic busy songs." Sheet music apps call this a half-page turn.

Today a page of N columns is turned whole, so the pedal can be pressed only once the last line of the rightmost
column is read, and then everything on screen changes at once. Turned by a column, it can be pressed any time while
reading the rightmost column: the column being read slides to where the eye already expects it, and the next one is
always in view.

## Decisions

- **An option of Pages, not a third layout.** Preferences' Layout keeps its two choices (Pages, Scrolling); under
  Pages a new segmented choice, **Turn by: Page / Column**, described as mattering only with several columns on
  screen. `SongLayout` is unchanged, the once-asked layout question too (still two cards); the new choice is never
  asked about. A device preference like the layout (not synced), default Page.
- **The song is one strip of columns**, each the height of the reading area, N of them in view (N as the grid already
  decides it for the width), read left to right (right to left in an RTL song). A step moves the strip by the width
  of the column leaving, so columns keep their own widths (a wide tab section keeps its wider column).
- **One column on screen still turns sideways.** A column is then a page, so nothing is lost, and the option means the
  same at every width: a tablet rotated between one and two columns does not change direction. A song whose columns
  all fit on screen has no steps, as a song that fits has today.
- **Tap halves become left and right** in this mode: the left half steps back, the right half forward (mirrored in an
  RTL song, as Left and Right are). Everything else about tap to step stays (`stepping/StepTaps.kt`: no long press,
  sloppy taps, only taps nothing inside consumed); the sloppy-tap travel limit is measured against the width.
- **Keys stay.** Up, Down, Page Up, and Page Down are the step keys (a page turner sends these); Left and Right still
  change the setlist's song. The step buttons keep their place and their names; their arrows point left and right.
- **A drag moves the strip; past either end it moves the setlist pager** (nested scroll), as a vertical drag past a
  song's end does not today. A fling goes no further than one step, as now.
- **What cannot be cut and is taller than the screen** (a tall uncuttable chunk at a big text size) cannot be read in a
  column that does not scroll. That song falls back to page turns, decided per song by the grid search, so the pedal
  rule holds. Expected to be rare: lines, tab slots, and staff systems are all cut points.

## Steps

### 1. The preference (`:data:model`, `:data:source:local:implementation`, `ui/state`, `ui/preferences`)

- `UserPreferences.pageTurn` (an enum `PageTurn { PAGE, COLUMN }` beside `SongLayout`), `UserPreferencesDocument` gains
  a defaulted `pageTurn` key (only gains a field, so no `version` bump and no fixture), mappers, a test of the mapping.
- `PreferencesController.setPageTurn`; a `SegmentedChoice` in `SongPreferencesPage.kt` under the Layout one, shown only
  while Pages is picked (animated in and out), with its strings in every language.

### 2. The strip search (`ui/songView/grid`)

- A strip mode of `SongGridSearch`: one row of as many columns as the song needs, each filled to the reading height by
  `ColumnFlow` (the magazine flow and `LayoutScoring`'s priced cuts as they are), scored by fewest columns, then the
  cut cost; columns are not evened out (the last one may end early, as the last page's does).
- Rules that change in a strip: "a section taller than the screen gets a row of its own" goes (it is cut across
  columns); a tempo or time change starts a new column instead of a new page; side-by-side neighbours in a single
  column, centering by the tallest, and section-gap spreading do not apply. Narrow columns only where they save
  columns, under the same quarter-of-lines rule.
- The search reports when a chunk taller than the reading height leaves no strip, and the page falls back to page turns.
- Pure, so tested in `commonTest` beside the grid's tests: column counts, cut points, timing starts, the fallback.

### 3. The strip on screen (`ui/songView/SongSectionsLayout.kt`, `ui/screens/songDetails`)

- `SongSectionsLayout` places the strip's columns side by side at the reading height; `SongRows` reports column starts
  instead of row tops, so stepping reads stops off it as it does rows.
- The song page's scroll container becomes horizontal in this mode, nested in the setlist pager (`SongPages.kt`'s
  `HorizontalPager`) so a drag past the strip's end turns the song.
- Motion as everywhere: the step glides with `SongMotionSpec`; a fold, pinch, or transposition re-flows the strip and
  glides places, never sizes. Switching between Page and Column is a staged `SongChange`.

### 4. Stepping (`ui/screens/songDetails/stepping`)

- Stops are column starts; next and previous move one column, the last stop showing the strip's last N columns. The
  dots count those stops; a press mid-step steps on from its target.
- In a setlist, previous from the first stop goes to the previous song's last stop (set beforehand, as its scroll end is
  today), next from the last to the next song's first.
- `StepTaps.kt` splits on x instead of y in this mode, mirrored in an RTL song; the sloppy tap's travel limit is a
  fraction of the width.
- Keeping the reader in place: the `ReadingAnchor` is the first column in view (its first chunk), followed once a new
  layout has held still, as now. `RowSnapFlingBehavior` snaps on x.
- The step buttons never cover a line: the strip is laid out narrower where it has steps, as a scrolling song is.

### 5. Docs and tests

- `ui/songView/grid/CLAUDE.md`, `ui/songView/CLAUDE.md`, `ui/screens/songDetails/CLAUDE.md`, `stepping/CLAUDE.md`, and
  the root `CLAUDE.md`'s product rule on reading in pages.
- A UI test (`tools/screenshots`) stepping a two-column song by columns with the keys and with taps.
- The keyboard shortcuts sheet needs nothing (the keys are unchanged).

## Not in v1

- Auto-scroll sideways (Scrolling mode stays a single column).
- Turning by column in the editor's preview or the print preview.
