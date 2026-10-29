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
`stepping/`, the one column that scrolls by itself in `autoScroll/`. What the first section holds and read only mode
are in `ui/CLAUDE.md`; chord diagrams in `ui/chords/`; the click in `ui/metronome/`.

The guarantee most of this file serves: **every line of a song can be reached with Up and Down alone** (a page turner
pedal), at any text size on any screen. `LayoutBudget` is its only exception.

### The songs it pages through

- `songDetailsSongsOf` resolves the destination against `songLookup` (the library and whether it has been read). Before
  the read it resolves only a destination whose songs are all present, so a restored setlist pager never restores its
  page index onto the wrong song over a partial batch.
- Once every song it names is gone from a read library, the screen closes itself.
- The pager bar numbers by place in the setlist, missing entries included; pages are only the songs that exist.
- Its first page's text and its neighbours' are asked for as the screen is pushed (`SongTextStore.readAhead`), so a
  song read before opens with its lyrics in the first frame and its in-place model is built before the transition
  moves, not during it.

### The import preview (`ImportPreviewState.kt`, `state/importing/ImportPreviewing.kt`)

- **Nothing from outside is written before Import.** A `SongDetails` with `importPreview` draws the plan's entries
  (`ImportReport.Review`) from their text, under page names no library file can have, one set per review
  (`importPreviewFileName`), whose inferred keys are forgotten once the review is over. It is read only as performance
  mode is, with no song actions, About the song, cover, metronome, or tuner button; folds are kept in memory
  (`ImportPreviewState.folds`); no override is written. Chord diagrams open their sheets, whose pick is a device
  preference. Never restored, addressed as `/import`. Bound to the review it was opened over (`ImportPreview.reviewId`);
  without it the screen closes itself (`CloseImportPreviewWithoutReview`), except while the report is `Importing`.
- **Marked plainly**: `ImportPreviewBar`, a secondary container card (as `WarningCard`) floating at the bottom (at most
  720dp wide), says the song is not in the library yet and, for a taken name, that it is. Its buttons
  (`ImportPreviewButtons`, `importPreviewActionsOf`) sit at its end, beside the message where that keeps 140dp, else
  under it; the pages' bottom padding clears the card (pedal rule), measured in the same pass as the pages
  (`PagesAbovePreviewBar`) so the lyrics are never flowed twice as the screen opens. **Import**, or for a taken name both **Import as a
  copy** and **Replace** (confirmed), Replace first for a shared link imported here before; side by side where they fit,
  stacked otherwise. Ctrl / Cmd + S presses the primary one unless it is Replace.
- **In a short window** (`SHORT_WINDOW_HEIGHT`) the card is one line and its buttons; over the review the page's tick
  and Keep both / Replace are in a dropdown behind its choices button (`ImportPreviewChoicesMenu`), and the line names
  them.
- **One song** (`ImportPreview.singleSong`) stands in for the import screen. Once written, it waits for the text and
  the lookup, then is rewritten in place into the song's screen (`ImportReportState.followImportedSong`, same `id`; the
  pager keeps the page by `rememberPageKeys`), with `Message.PreviewedSongImported`. A failed write puts it back. A
  batch joining it turns it into the import screen's review (`ImportReportState.bringReviewToFront`).
- **Over the review** (`isReviewPager`): each page shows its own tick and Keep both / Replace, bound to the row's
  `ImportChoices`, an unticked one's title saying it is left out (both titles laid out, so a tick moves no box); the
  button is the review's **Import N files**, which returns to the import screen, as a joining batch
  does (its songs are listed there); one opened while that batch is read appends its pages (`importPreviewSongIndices`).

### The steppers

- Steppers are shorter than Material buttons and lower `LocalMinimumInteractiveComponentSize` to match (touch still gets
  48dp). Height is `STEPPER_HEIGHT` in a bar or menu, `songControlHeight` in a song; `trailing` holds the Tap segment.

### The app bar

- **The subtitle (`SongHeaderNote`) names the sounding key**, as the page and the Transposition control do (the
  transposition and the file's capo applied, `{key}` read by `SongKeys`, via `Song.chordsKey`), after a 16dp dot (the cards' 24dp would grow the bar and shrink the lyrics' room). Worked out for the song
  its crossfaded content was composed for, since a page change draws two. Chords off drop the key. No tempo (the first
  section says it) and no duration.
- **A file with no `{key}` is named by the key its chords are in** (`inferredKeyOf`), shown and moved as a written key
  is: in the bar, the Transposition control and its menu, the read only line, and the PDF; a numbering counts from it
  (`SongRenderer.movedKey`, in the frame of the page's chords), the page, its Chords and Scales sections, the chord
  shape sheet, the editor's preview, and the PDF alike. `InferredSongKeys` works it out off the main thread as `SongTextStore` reads the text, before handing the text on,
  and keeps it by file name for the view model's life: a song reopened names it on its first frame; one never read
  gets it in the frame its lyrics arrive, crossfading in with them. The list cards never parse a file, so they name
  only a written key.
- **The title scrolls the song to its top, and at the top opens the About the song sheet**, as the menu's About the
  song does from anywhere. A chevron (`ic_expand`) shows while a tap would open it; its room is kept wherever the sheet
  is offered, so a long title is not cut afresh. The touch target (`titleTouchTarget`) is all the bar's free space but reports only the title's room. No
  ripple: a press dims the title, cover, and chevron. The scroll is read inside the title slot, so passing the top
  recomposes only it. Nothing is taken at the top where the sheet is not offered.
- The text size stepper is in the bar where it fits, else at the end of the menu (`MenuStepperRow` as `menuFooter`, the
  menu staying open while pressed). As the bar narrows the text size goes into the menu, then Choose setlists, then the
  cover, each while the title keeps 160dp (`appBarButtons`, from the settled width, for every page at once). Performance
  mode: the stepper in the bar where the title keeps 160dp beside it and the overflow button
  (`showsFontScaleInPerformanceBar`, about 394dp), else at the end of the menu; the cover only where the title keeps
  160dp beside all three (`showsCoverInPerformanceMode`). **The overflow button is always the bar's last.**

### The menu's own entries

- Both menus (read only mode's too) hold About the song (where the sheet is offered: never in a preview) and
  Preferences (`SongDisplaySheet`, the Preferences tab's Chords and Song page sections, written at once so the song
  changes behind it; the notation's sheet stacks over it). Never let out as buttons (`isAlwaysInMenu`).

### Keeping the display on

`Modifier.keepScreenOn()` on the root: a song is read with both hands on the instrument. It is the view flag on
Android, `idleTimerDisabled` on iOS, a Screen Wake Lock on the web (retaken when the tab is shown, secure contexts
only), and nothing on the desktop.

### Keyboard (`SongKeyboardShortcuts.kt`)

- Up and Down scroll a tenth of the viewport, linearly over the key repeat interval; Left and Right change the setlist's
  song, physically (`horizontalArrowStep`: in RTL Left is the next song), as the print preview's arrows turn pages. Where the step buttons are, Up, Down, Page Up, and Page Down press them instead (a Bluetooth page turner sends
  exactly these; a nudge leaves the player mid-verse); elsewhere the page keys are unconsumed. Counted from the page the
  pager is heading to, so two quick presses go two songs.
- The screen takes focus as it opens (nothing on it would otherwise), and the handler is in the preview pass, so it sees
  keys wherever focus has moved and the scroll container never handles an arrow.
- Left and Right are unconsumed at the setlist's ends and for a single song; all four with Alt, Meta, or Ctrl (Alt + Left
  is the web's Back, Cmd + Left / Right a Mac's Back and Forward).
- Ctrl/Cmd + E opens the editor, as the menu's Edit does: not in performance mode, which has no Edit.
- In a song in one column (`autoScroll/`) the step buttons and the four step keys nudge it, and Space plays and
  stops it.

### The metronome button

- `MetronomeButton` shows and hides the panel (`toggleMetronomePanel`): opening starts nothing, closing stops a playing
  click. M toggles the click from anywhere on the song, opening the panel with it.
