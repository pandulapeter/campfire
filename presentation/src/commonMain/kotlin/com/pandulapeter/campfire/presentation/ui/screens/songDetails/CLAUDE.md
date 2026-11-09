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

The song details screen. The song is drawn by `ui/songView/`; pedal stepping is in `stepping/`, the self-scrolling
column in `autoScroll/`. The first section and read only mode are in `ui/CLAUDE.md`.

The guarantee most of this file serves: **every line of a song can be reached with Up and Down alone** (a page turner
pedal), at any text size on any screen. `LayoutBudget` is its only exception.

## The songs it pages through

- Before the library is read, only a destination whose songs are all present resolves, so a restored setlist pager
  never restores its page index onto the wrong song over a partial batch. Once every song it names is gone from a read
  library, the screen closes itself.
- The pager bar numbers by place in the setlist, missing entries included; pages are only the songs that exist.
- **In a setlist a step leaves a song's end only on the next press** (`NextSongConfirmation`): the first turns the pager
  bar to name the next song (selecting it goes on too); going back, scrolling up, or another song forgets it. The bar's
  buttons, swipes, and Shift + an arrow do not ask.
- The first page's text and its neighbours' are read ahead as the screen is pushed, so a song read before opens with its
  lyrics in the first frame and its model is built before the transition moves, not during it.

## The import preview

- **Nothing from outside is written before Import.** Entries are drawn from their text under page names no library file
  can have, one set per review, whose inferred keys are forgotten once the review is over. Read only as performance
  mode is, with no song actions, About the song, cover, metronome, or tuner; folds kept in memory; no override written.
  Chord diagrams open their sheets, whose pick is a device preference. Never restored, addressed as `/import`. Without
  the review it was opened over the screen closes itself, except while the report is `Importing`.
- **Marked plainly**: a card floating at the bottom (at most 720dp wide) says the song is not in the library yet and,
  for a taken name, that it is. Its buttons sit beside the message where that keeps 140dp, else under it; the pages'
  bottom padding clears the card (pedal rule), measured in the same pass as the pages so the lyrics are never flowed
  twice as the screen opens. **Import**, or for a taken name **Import as a copy** and **Replace** (confirmed), Replace
  first for a shared link imported here before; side by side where they fit. Ctrl / Cmd + S presses the primary one
  unless it is Replace.
- **In a short window** the card is one line and its buttons; over the review the page's tick and Keep both / Replace go
  into a dropdown behind a choices button, and the line names them.
- **One song** stands in for the import screen. Once written, it waits for the text and the lookup, then is rewritten
  in place into the song's screen (same `id`, page kept), with a message. A failed write puts it back. A batch joining
  it turns it into the import screen's review.
- **Over the review** each page shows its own tick and Keep both / Replace, bound to the row's choices, an unticked one's
  title saying it is left out (both titles laid out, so a tick moves no box); the button is the review's **Import N
  files**, which returns to the import screen, as a joining batch does; one opened while that batch is read appends its
  pages.

## The app bar

- Steppers are shorter than Material buttons and lower `LocalMinimumInteractiveComponentSize` to match (touch still
  gets 48dp).
- **The subtitle names the sounding key** (transposition and capo played applied), whatever the chords are named by,
  after a 16dp dot (the cards' 24dp would grow the bar and shrink the lyrics' room). Worked out for the song its
  crossfaded content was composed for, since a page change draws two. Chords off drop the key. No tempo (the first
  section says it) and no duration.
- **A file with no `{key}` is named by the key its chords are in** (`inferredKeyOf`), shown and moved as a written key
  is everywhere (bar, Transposition control, read only line, numbering, Chords and Scales sections, chord shape sheet,
  editor preview, PDF). It is worked out off the main thread as the text is read, before handing it on, and kept by
  file name for the view model's life: a song reopened names it on its first frame; one never read gets it with its
  lyrics, crossfading in. The list cards never parse a file, so they name only a written key.
- **The title scrolls the song to its top, and at the top opens the About the song sheet.** A chevron shows while a tap
  would open it; its room is kept wherever the sheet is offered, so a long title is not cut afresh. The touch target is
  all the bar's free space but reports only the title's room. No ripple: a press dims the title, cover, and chevron.
  The scroll is read inside the title slot, so passing the top recomposes only it.
- As the bar narrows the text size stepper goes into the menu (at its end, the menu staying open while pressed), then
  Choose setlists, then the cover, each while the title keeps 160dp, decided from the settled width for every page at
  once. Performance mode: the read only mark (`ReadOnlyMark`, a lock opening the Preferences, so a mode switched on by
  accident is noticed and undone here) always before the overflow button; the stepper where the title keeps 160dp
  beside all three (about 434dp), the cover only where it keeps 160dp beside all four. **The overflow button is always
  the bar's last.**
- Both menus (read only mode's too) hold About the song (never in a preview) and Preferences (the sheet with every page,
  opening on Song, the read only mark's on General; written at once, so the song changes behind it), never let out as buttons.

## The questions asked once over a song

- First the layout (`autoScroll/`), then the chord names: the first time a song with chords is opened or paged to with a
  capo on, or its capo stepped while read (to none included; asked once the stepper has rested 1.5 s, so a capo stepped
  through to the third fret is not asked about at the first). Not where the answer changes nothing (chords off, the
  keyboard) or the reader is playing (read only mode's performance mode; an archived setlist's song still asks), never
  in an import preview or the editor. It waits for the layout question, the song's text, and nothing over the screen.
- Of its three cards (shapes, sounding, keeping the key), the first two end in the song's first four chords named their
  way, as the page would; a capo just stepped to none is exemplified by the one it was stepped from, so the two never
  read alike.
- Both are a `ConfirmedQuestionSheet`: a pick written at once, the title wrapped rather than cut, the Preferences hint
  under the choices, Confirm pinned under them, and no close button, swipe, or scrim, so a stray tap never puts them
  away. Only Confirm (or a pick in Preferences) marks them answered; one put away without it asks again the next time
  it matters (the layout over the next song screen, the chord names over the next song: once per song, or uncovering
  the screen would ask again at once).

## Keeping the display on

`Modifier.keepScreenOn()` on the root: a song is read with both hands on the instrument. On the web a Screen Wake Lock
(retaken when the tab is shown, secure contexts only); nothing on the desktop.

## Keyboard

- **Down and Right go on, Up and Left go back** (a pedal sends either pair, as it is set): where the step buttons are,
  the arrows, Page Up, and Page Down press them (a nudge leaves the player mid-verse); elsewhere the arrows scroll a
  tenth of the viewport, linearly over the key repeat interval, and the page keys are unconsumed. Left goes on in a
  strip read from the right, as its buttons and taps do; a song read down the screen mirrors with the layout.
- **Shift + an arrow switches the setlist's song** without the question above: Up and Down, and Left and Right
  physically, as the pager lays songs out (in RTL Left is the next song) and the print preview's arrows turn pages.
  Counted from the page the pager is heading to, so two quick presses go two songs. Taken at the setlist's ends and
  for a single song too, doing nothing, so a held Shift never turns a page instead.
- The screen takes focus as it opens, and the handler is in the preview pass, so it sees keys wherever focus has moved
  and the scroll container never handles an arrow.
- The arrows with Alt, Meta, or Ctrl are unconsumed (Alt + Left is the web's Back, Cmd + Left / Right a Mac's Back and
  Forward).
- Ctrl/Cmd + E opens the editor, not in performance mode, which has no Edit.
- Where the song has its playing controls (not read only), Ctrl / Cmd + Shift + Up and Down step its transposition
  (only where the stepper is: chords on, a song with chords) and T taps its tempo (metronome on), whether or not the
  steppers are in sight.
- In one column (`autoScroll/`) the step buttons and step keys nudge it, and Space plays and stops it.
- The metronome button only shows the panel (opening starts nothing, closing stops a click); M toggles the click from
  anywhere on the song, opening the panel with it.
