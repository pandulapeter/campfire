<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songEditor

The raw ChordPro editor. **Nothing but Save ever writes the file**: the menus' sheets, transposition, Prettify, Replace,
and revert all edit the field (one undo step each).

## App bar

- Title row: undo/redo, Save, overflow menu; Close rather than Back. The control row holds the transposition stepper and
  Edit / Preview / Split, the two controls that do not write at the caret, so they stay when the rest folds.
- Split is offered only at `WindowSize.EXPANDED` but is the default; a window that narrows only *shows* Edit, so
  widening returns to both panes. The pane choice is drawn whenever the row is, so the stepper is never alone; its
  segments have no side padding, keeping "Preview" whole at 360dp.
- Where the keyboard leaves under 240dp the control row animates away and its Shortcuts chevron moves into the title
  row; only visibility follows the keyboard, so the user's pane and fold come back.
- Keyboard up in a short window: 48dp title row, lines 18sp apart at unchanged letter size, and on Android the status
  bar hidden.
- Menu: Find and Find and replace first (promoted to buttons where the bar keeps the title 160dp wide); About the song
  over the draft in every pane; Prettify; the directives reference; Revert last, behind a confirmation so it can't be
  mistapped next to Save, done through the field's editing so it is undoable. No Export or Delete: those belong to the
  list and the viewer.
- Sheets from the menu or the preview's card edit a snapshot of the draft, applied as field edits. They stay up when
  the file leaves the library, since the draft remains.

## Notation

**The field is in the reader's notation, the file always in standard** (tested). Under a numbering the field is in
letters and only the preview numbers: correcting a `{key}` would otherwise move every chord at Save, and a half-typed
key leaves numbers nothing to count from.

- Tab string names convert with the chords, columns kept; a file opened and saved unedited is byte-for-byte unchanged.
- Field-vs-file comparisons (unsaved state, draft reopened) are made in the field's notation; the stored draft is in the
  file's, so it survives a notation change.
- A chord typed in an alternative spelling is saved one way, and the save brings the field to what was written.
- Transposition converts to standard, transposes, and converts back. Settings cannot change the notation under an open
  editor, since selecting a top level screen clears the stack.

## Transposition, caret, and undo

- The stepper rewrites the file text: no amount or reset; it shows the song's key, or `?`. Anything it can move (a
  chord, a tune, a `{key}`) enables it; an `abc` tune moves through a `%%transpose` line it writes, updates, or removes.
- Every whole-text rewrite (transposition, Prettify, sheet edits, save respelling, file-follow) keeps the caret and
  selection next to their text. A revert keeps the offset.
- Over 50,000 characters, a whole-document rewrite (transposition, revert, Replace all of several matches) clears the
  undo history first, since each is recorded as the whole text twice.

## Pasting a chord sheet

- An edit bringing two non-blank lines or more (a one-line paste looks like a keyboard's committed word) has its chords
  over lyrics converted (headings as sections, no title guessed) unless it lands in a tab, a grid, or delegated
  notation; then the whole song is prettified, caret kept (tested). One undo step with the paste; never the editor's
  own edits; nothing past 50,000 characters.
- A change of the whole text into the whole text is first narrowed to what differs: the web reports a typed character
  that way, which would otherwise read as a paste of the song. A paste over the whole text (Select all) cannot be told
  from a keystroke, so it loses the lines it shares with the old text at either end.

## The field and panes

- A `BasicTextField` scrolled by a surrounding `verticalScroll` and `horizontalScroll`, not its own `scrollState` (which
  cannot hold room after the last line). **It never wraps**: tab staff lines must stay one screen line each. Margins and
  the bottom room are the field's decorator, so presses there place the caret.
- **One `textDirection` for the whole field**, the song's: Skia resolves one for the whole text, and inserted marks
  would shift every offset the layout reports. Left and Right follow each line's own direction (a directive, `#`, tab,
  grid, or delegated line is left to right, a lyric or comment by its letters); only character steps are answered,
  word and line steps stay the platform's. A letterless row of a right-to-left song shows reversed in the editor only.
- Only the keyboard is excluded outside the scroll, read during layout. The preview lays out against a height ignoring
  the keyboard, so sliding it moves neither pane's composition nor the columns.
- Scroll states live next to the `TextFieldState`, not in the panes, which are recomposed from scratch when Edit,
  Preview, and Split change places. The panes are `weight(1f)`, not `fillMaxSize()`, or a long song overflows instead
  of scrolling.
- Ctrl / Cmd + S is heard by the whole editor, which takes focus on open and whenever panes change, except that a field
  being typed in keeps focus across a change that still shows it (rotation, resize across Split).
- Insertion rows leave whenever only the preview shows.
- An editor opened on a selection (a shared song's filled-in title) focuses the field, asks for the keyboard, and
  scrolls to the selection once the keyboard has settled, so the first key typed replaces it.
- **Bold, Italic, and Underline toggle** (tested): tags around or at the ends of the selection that set the style in any
  spelling are taken off (a span only loses what sets it), else `<b>`, `<i>`, or `<u>` wraps it, line by line, since
  markup is read within one. Only where the parser reads markup (a lyric line, a comment's value); a tab, grid,
  delegated, other directive, Campfire 3 heading (`{c: Verse 1}` with no environment open), or `#` line is left alone.
- Unreadable directive values (`{time: 5/7}`) are drawn whole in the error colour.

## Preview

- **Shows the text as written**: none of the reader's transposition (the file's own `{transpose}` applies), and it
  ignores the Chords and Metronome switches. The text size preference reaches the preview but not the field (source;
  scaling would break tab columns).
- Split stacks the song in one column, since columns reshuffling on every keystroke hide the line being typed.
- Typing in a tune keeps its staff until the new one is engraved, so the sections under it hold still.

## Unsaved text and the draft

- An untouched field follows its file (`ui/state/songText/CLAUDE.md`). Every way out goes through
  `Navigator.navigateBack`, which asks save / discard / cancel; the draft lives in the view model because that dialog
  saves it.
- **The dialog's Save leaves only after the write succeeded**: until then the editor holds the only copy, so a failed
  write keeps the editor (and a closing desktop window) open.
- A draft reopened on launch is unsaved like any other (update gate, way out, web `beforeunload`). An editor composed in
  a restored process waits for draft recovery, so a long document gets its text from the draft, not the file.
- A failed first read shows the details screen's failed state (Retry, Close).

## A new song from a pasted or shared text

- Opened on `NEW_SONG_FILE_NAME`, which no file has: nothing read, written, or synced before Save. A missing title and
  artist come from the share's page title (`presentation/CLAUDE.md`), else localized placeholders, the title selected.
  Its draft goes to disk at once (it is then the only copy of an iOS share).
- Always unsaved, so Close asks; Discard leaves nothing behind. **Save needs a title** (a kept placeholder is one);
  Revert is off.
