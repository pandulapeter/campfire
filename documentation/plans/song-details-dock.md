<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: song details as a reading surface with a dock

**Status:** steps 1 to 5 and 8 done; step 6 next. The gate decided the dock scales to the room (below). The mockups this plan was agreed on (360 × 640 dp, dark):
https://claude.ai/artifact/PDMZvTbTcRH6CSQGQPJrra. `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Why

Song details is the screen people perform from. Six problems, in the owner's words:

1. Sections get folded by accident while tapping to page.
2. Quick taps to page are lost to scroll detection.
3. Chord diagrams should be visible at any time.
4. Scale suggestions should be visible where they matter.
5. The metronome is apart from the tempo, and opening it moves the control that opened it (its panel grows in the bar).
6. On a phone the first page is mostly controls, Chords, and Scales: the first verse needs a page turn.

Everything shown today stays reachable; the text size is never traded for room.

## Decisions

- **The song area is a reading surface.** The first section (instrument pill, tuner, transposition, capo, tempo, Tap,
  time signature, metronome button), the Chords section, and the Scales section leave the song on song details only;
  the song starts at its first line. The editor's preview (whose first section is the About the song tiles) and the PDF
  keep theirs, so the renderer takes a flag rather than losing the sections.
- **A dock** under the song (a phone held upright) holds what left: one `surfaceContainer` surface with rounded top
  corners, never collapsed, no divider or elevation.
  - Row 1: the **key chip**, then the **scale suggestions** as a sideways-scrolling row of chips (the home scales with no
    heading, then each change under its heading, "Over E7"). A chip opens today's scale sheet.
  - Row 2: the song's **chord diagrams** as a sideways-scrolling strip (a diagram opens today's chord sheet), then the
    **tempo chip**.
  - **Its rows are decided by the screen, never the song**: by the Features and Preferences (chords, chord diagrams,
    scale suggestions, metronome) and the instrument. The dock sits outside the pager, so a height that followed the song
    would re-lay out the viewport on every setlist page turn. A row a song has nothing for shows a quiet line saying so
    ("No scale suggestions for this song"); its contents crossfade by the pager's target page, as the bar's subtitle does.
  - Without diagrams (switched off, or an instrument without shapes) the key, scale chips, and tempo share one line; an
    instrument without shapes puts the instrument pill there, so the diagrams can be brought back
    (`chords/CLAUDE.md`). Without scale suggestions the key joins the diagram row. Without the metronome there is no
    tempo chip. **With only the metronome** (chords off) the dock is the metronome panel itself: tempo stepper, Tap,
    play, and a full-width row of beats whose accents are tapped in place. With neither, there is no dock.
  - In a setlist the **pager bar is the dock's last row**, the same surface, its "Up next … Step again" state kept.
  - **The import preview's card joins the dock surface** instead of floating over it, measured in the same pass as the
    pages (`PagesAbovePreviewBar`); there is no metronome in a preview, so the dock is one row there.
- **Fades only where a scrolling row meets something that does not scroll** (the scale row's start edge at the key
  chip, the diagram strip's end edge at the tempo chip). An edge at the screen's edge does not fade: the row runs to it,
  its content padded instead.
- **Rows scroll themselves only on a page turn**, never under a finger: the scale row to the group of the page on
  screen, the diagram strip to the page's first chord. A row swiped by hand stays where it was left until the next turn.
- **The key chip** names what the Transposition control names today (the key of the page's chords, shapes under
  `SHAPES`, `inferredKeyOf` where the file names none). **A capo other than zero is always on screen**: the chip gains a
  second, orange half, "Capo 2". The bar's subtitle keeps naming the sounding key, unchanged. Either half opens the
  **Playing sheet**: instrument and tuning, the tuner, transposition, and capo. "Capo keeps the key" and the keyboard
  follow `ui/playing/CLAUDE.md` as the steppers do today.
- **The tempo chip**: the number, a beat row lit while the click plays (as many beats as the bar has, a playing
  stretch's own accents until its bar ends), and play / stop in a fixed place. The number opens the **Tempo sheet**:
  stepper, Tap, and the song's accents (tapped per song by signature, removed once back to the signature's). **The
  time signature is not there**: it is written into the file, so it stays in Song defaults. A later `{tempo}` shows the
  stretch being played. The bar's metronome panel goes, and with it `MetronomeSettings.isSongPanelShown`; M starts and
  stops the click, T taps the tempo wherever the tempo chip is.
- **Taps on the song only step.** A short tap steps (top half back, bottom half next; a strip's halves across), as
  today; drags and flings stay as they are (a drag goes anywhere, a fling at most one step, pinch takes two fingers).
  - An unfolded section header no longer folds on a tap: a long press opens a menu with Fold (a mouse click on its
    chevron still folds, the pointer being exact). A tap on a folded one still unfolds it, which loses nothing. Screen
    readers keep the toggle as an action. Performance mode still folds nothing.
  - Real buttons inside the song keep their taps: a missing picture's Add and Download, the sheet music part picker,
    the cut-song notice. A picture opens its viewer on a long press.
  - Chord names in the lyrics get no gesture: the strip has every diagram.
- **Lost taps are measured before they are fixed.** Breadcrumbs log every tap the stepper drops and why (moving freely,
  second pointer, too long or too far a slide). Then only the case that bites changes, most likely: a press that
  stops a capped fling's settle (a step in effect) steps on from its target. A press that stops a real drag-fling, or a
  self-scrolling column, still only stops it.
- **The title always opens About the song** (its chevron shown wherever the sheet is offered: never in an import
  preview). Back to the top moves to the overflow menu, the Home key, and a long press on the back step button where
  there is one.
- **Short and wide windows** get the dock as a side panel at the end side (key and tempo, scale chips wrapping, the
  diagrams in a grid; every chord of the song where the height allows), the step buttons between the song and the
  panel. The grid is searched with the panel's width taken off. The switch at `SHORT_WINDOW_HEIGHT` travels or
  crossfades, never jumps.
- **Read only mode follows today's matrix per element**, not one switch: the key chip opens nothing (it would write
  overrides) and drops its chevron; scale chips still open their sheets; diagrams do not open the chord sheet in
  performance mode (it writes the player's pick) but do in an import preview; the click still plays.
- **Retired**: `CHORDS_FOLD_KEY` and `SCALES_FOLD_KEY` (nothing left to fold; no migration for a tiny user base), the
  first section on song details, the bar's metronome panel. The PDF keeps its first section, Chords, and Scales;
  `songReadingOf`, `groupedByKey`, and the scale walk stay shared.
- **Considered and set aside**: keeping the first section in the song as one compact line with only Chords and Scales in
  the dock (half the dock height, but goals 3 to 5 only half met); a collapsible dock.

## The cost, and the gate

The dock is a constant cost on every page (about 122 dp on a 360 × 640 phone, 170 dp in a setlist) where the sections it
replaces cost one page once. The grid is "fewest pages first" (`ui/songView/grid/CLAUDE.md`), so this is measured
before anything ships: step 1.

**Measured (step 1, offscreen at 360 × 640 dp, default text size).** Leaving the three sections out saves one page or
step everywhere, but a fixed 122 dp dock then costs 1 to 3 pages or column steps on a long song (a 7-verse song turned
by column: 12 → 15; in a setlist by page: 13 → 15), and more at 592 dp (the bars taken off). The break-even is about
80 dp turned by page and about 60 dp turned by column; in a column-turned setlist only 20 to 40 dp above the pager row.

**Decided: the dock scales to the room, and an upright phone keeps its diagrams.** Its height is chosen by the screen
(the window's height, the reading mode, a setlist's pager row), never by the song: full diagrams where they fit the
step 1 break-even for the mode (`dockBudgetOf`), smaller diagrams below that, and in a short window
(`SHORT_WINDOW_HEIGHT`) one row of chord-name chips (a chip opens the chord sheet as a diagram does) beside the key
and tempo, until step 13 gives it the side panel. The owner chose diagrams on an upright phone at the cost of up to two
pages on a long song; the one-row tier is for short windows only. `PageCountTest` holds every case within two pages or
steps of its count before the dock, and fails past that.

**Measured (`PageCountTest`, 360 × 640 dp, the smaller diagrams' 112 dp dock).** Pages or column steps, before → with
the dock: House of the Rising Sun 8 → 8 by page, 9 → 9 in a setlist, 7 → 8 by column, 8 → 9 in a setlist; a
seven-verse strummed song 10 → 12, 11 → 13, 10 → 12, 10 → 12. Diagrams 50 dp tall (a 120 dp dock) cost the long song
three steps by column in a setlist, so they are 42 dp. Full ones come from about 1,120 dp tall.

**Decided: a step inside a tall stop pages at least half the screen** rather than two thirds, so a chorded line the page
cuts at its bottom is whole at the next one's top (the dock exposed it on a phone on its side at the largest text size).

## Steps

### 1. Measure (the gate)

On the Medium Phone AVD at 360 × 640 dp (and the offscreen renderer), count pages and the first page's lines for House
of the Rising Sun and a long song, alone and in a setlist, turned by page and by column, today and with a stand-in dock
of the proposed height. If the page count grows on the long song, decide with the owner before step 3: a shorter
diagram row, or the diagrams scaled to the room.

### 2. Breadcrumbs and the lost taps (independent, can land first)

Log each dropped tap in `stepping/StepTaps.kt` with its reason (kinds, never content). From what the logs and a few
sessions show, change only the case that loses taps; add a UI test of two quick taps stepping twice
(`tools/screenshots`, `AppTestNavigation.kt`).

### 3. The dock scaffold

A `ui/songView`-independent `SongDock` in `screens/songDetails/dock/` (its own `CLAUDE.md`), placed outside the pager
under the pages, above the bottom inset. The pages' bottom padding, reading window, and `SongRows` follow its height in
the same pass, so the pedal guarantee holds (no line under the dock, the last line of a rested page unfaded). Rows from
the screen's switches only. Traversal index after the bar, before the song. Edge fades per the rule above
(`components/EdgeFade.kt`).

### 4. The key chip and the Playing sheet

The chip with its capo half, its naming as decided, crossfading by target page. The sheet moves the instrument pill,
tuner button, transposition, and capo steppers out of `songView/firstSection` (their stepping, pending writes, and
`PendingWrites` unchanged). Ctrl / Cmd + Shift + Up and Down step the transposition wherever the chip is.

### 5. The chord strip

From `SongChordsSection`'s data: every chord once, first-played order, key groups under a "Key: A♭" label inside the
strip, the degree line where degrees are on, cells scaling with the text, the keyboard's wider cells, the crossfade on a
changed shape. Over `MAX_SONG_CHORDS` (a songbook) the row says so. A cell opens the chord sheet as the section's does.

### 6. The scale row

From `songScaleAnalysisOf`: the home group, then each moment under its heading, the chips `SongScaleGroupsTest` pins.
None for a songbook or over `MAX_PLAYED_CHORDS`. Scrolls to the page's group on a page turn only.

### 7. The tempo chip, the Tempo sheet, and the metronome-only dock

Move the tempo stepper, Tap, and the beat row out of the first section and the bar's panel. The beat row draws the
playing bar (`metronome/CLAUDE.md`), any signature's length. Delete `isSongPanelShown` (the preferences document only
gains fields: it stays readable and is no longer written). M and T reworded in `SongKeyboardShortcuts` and the
shortcuts sheet.

### 8. The song without its first sections

Leave the first section, Chords, and Scales out of song details' model (`SongModelBuild`, `sectionKeysOf`), keeping
them for the editor's preview and the PDF. Retire the two fold keys. The grid's measurements lose three sections, which
simplifies `SectionMeasurements`; check "full width where the song fits as one row", which now holds for more songs.

### 9. Setlist and import preview

The pager bar as the dock's last row; the preview card in the dock surface, both measured with the pages.

### 10. Taps on the song

Long press Fold on unfolded headers, a tap on folded ones, the mouse click kept, accessibility actions, the 900 ms
fold hold unchanged. Pictures on long press; the in-song buttons untouched.

### 11. The bar

The title opens About the song on every tap; Back to the top in the overflow menu, Home, and the back step button's
long press.

### 12. Sheets over a self-scrolling column

A chord or scale sheet opened from the dock pauses the column and resumes it when it closes; every other cover still
stops it (`autoScroll/CLAUDE.md`).

### 13. Short and wide windows

The side panel, its order with the step column, the grid searched without its width, the travelling switch at
`SHORT_WINDOW_HEIGHT`, the tablet and desktop widths.

### 14. Finish

Strings in all eleven languages (counted ones as plurals), breadcrumbs for every chip, sheet, and long-press menu, the
`CLAUDE.md` files of `songDetails`, `stepping`, `autoScroll`, `songView`, `firstSection`, `chords`, `metronome`, and
`ui` rewritten for the new places, UI tests for the dock variants and the capo half. Store and website screenshots and
the promo video follow at release.
