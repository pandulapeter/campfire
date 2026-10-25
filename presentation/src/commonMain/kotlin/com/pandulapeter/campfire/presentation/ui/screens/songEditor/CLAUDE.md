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

The raw ChordPro editor. **Nothing but Save ever writes the file** the editor is open on: the menus' sheets, the
transposition, Prettify, Replace, and revert all edit the field (one more undo step each).

## App bar rows

- **Title row**: undo/redo, Save, overflow menu. Close rather than Back.
- **Control row**: transposition stepper + Edit / Preview / Split. These are the two controls that do not write at the
  caret, so they are the ones that stay when the rest folds. Split is offered only at `WindowSize.EXPANDED` but is the
  default; a window that narrows only *shows* Edit, so widening returns to both panes. The choice is drawn whenever the
  row is, so the stepper is never alone in a row; its segments have no side padding, which keeps "Preview" whole at
  360dp. Where the keyboard leaves under 240dp (`MIN_ROOM_FOR_CONTROL_ROW`) the row animates away and its Shortcuts
  chevron moves into the title row; only visibility follows the keyboard, so the user's pane and fold come back.

### Folding

- Keyboard up in a short window: 48dp title row (`isCompact`), lines 18sp apart at unchanged letter size, and on Android
  the status bar hidden (`CompactKeyboardEffect`).

## Notation

**The field is in the reader's notation, the file always in standard** (`EditorSession.editorNotation`,
`SongRenderer.editorTextOf` / `fileTextOf`, tested). Under a numbering the field is in letters (`Notation.forTyping`)
and only the preview numbers: correcting a `{key}` would otherwise move every chord at Save, and a half-typed key leaves
numbers nothing to count from.

- Tab string names convert with the chords, columns kept; a file opened and saved unedited is byte-for-byte unchanged
  (see `:chordpro`'s `ChordProStaffNames`).
- Field-vs-file comparisons (unsaved state, draft reopened) are made in the field's notation; the stored draft is in
  the file's, so it survives a notation change.
- A chord typed in an alternative spelling is saved one way, and the save brings the field to what was written through
  `editorTextEdits`.
- Transposition converts to standard, transposes, and converts back. Settings cannot change the notation under an open
  editor, since selecting a top level screen clears the stack.

## Transposition, caret, and undo

- The stepper (`TextTranspositionControls`) mirrors the details screen's, but rewrites the file text: no amount or
  reset; it shows the song's key, or `?` (a `{key}` naming none too). A chord, or a `{key}` it can move, enables it.
- Every whole-text rewrite keeps the caret and selection next to their text: transposition
  (`ChordProTransposer.transposedOffset`), Prettify (`ChordProPrettifier.prettifiedOffset`), sheet edits, save
  respelling, and file-follow (`editedOffset`). A revert keeps the offset.
- In documents over 50,000 characters (`LONG_DOCUMENT_LENGTH`), a whole-document rewrite (transposition, revert,
  Replace all of several matches) clears the undo history first, since each is recorded as the whole text twice.

## Pasting a chord sheet

- `PastedTextFormatting`, the field's `InputTransformation`, answers an edit that brought two non-blank lines or more
  (a one-line paste looks like a keyboard's committed word): chords over lyrics in it are converted
  (`ChordSheetConverter.convertFragment`, headings as sections, no title guessed) unless the paste lands in a tab, a
  grid, or delegated notation (`ChordProLiteralLines`), then the whole song is prettified as
  the menu's Prettify does, caret kept by `prettifiedOffset` (`formattedAfterPaste`, tested). One undo step with the
  paste; never the editor's own `edit`s; nothing past `LONG_DOCUMENT_LENGTH`.

## Overflow menu

- Find and Find and replace first (promoted to buttons where the bar keeps the title 160dp wide, `editorMenuMaxWidth`);
  then the About the song sheet over the draft (`SongEditTarget.EditorDraft`), in every pane; Prettify; the ChordPro
  directives reference link; and Revert last.
- Revert is in the menu, behind `DialogType.RevertChanges`, so it can't be mistapped next to Save. The screen answers
  it through the field's own editing, so it is undoable.
- Sheets opened from the menu or the preview's card edit a snapshot of the draft (`toEditorSong`,
  `DialogType.SongEdit.target`), applied via `EditorSession.editorTextEdits` / `EditOnRequest`. They are not taken
  down when the file leaves the library, since the draft remains.
- No Export or Delete here: those belong to the list and the viewer.

## The field and panes

- A `BasicTextField` scrolled by a surrounding `verticalScroll` and `horizontalScroll`, not its own `scrollState`
  (which cannot hold room after the last line). **It never wraps**: tab staff lines must stay one screen line each.
  Margins and the bottom room are the field's decorator, so presses there place the caret.
- Only the keyboard is excluded outside the scroll (`EditorFieldPadding`, read during layout). The preview lays out its
  sections against a height ignoring the keyboard, so sliding it moves neither pane's composition nor the columns.
- Scroll states live next to the `TextFieldState`, not in the panes: panes are recomposed from scratch when Edit,
  Preview, and Split change places.
- The panes are `weight(1f)`, not `fillMaxSize()`: a weightless `Column` child is measured against unbounded height
  and a long song would overflow instead of scrolling.
- Ctrl / Cmd + S is heard by the whole editor, which takes focus itself on open and whenever panes change, except that
  a field being typed in keeps focus across a change that still shows it (rotation, resize across Split).
- Insertion rows leave whenever only the preview shows.
- **Bold, Italic, and Underline toggle** (`formattingToggle`, tested), from the Shortcuts row and as Ctrl / Cmd + B, I,
  and U while the field shows (`editorFormattingShortcuts`): tags around or at the ends of the selection are taken off,
  else written around it; several lines are formatted line by line, since markup is read within one, directives and
  `#` lines left alone.
- `ChordProOutputTransformation` colours tokens like the viewer (and draws search matches); unreadable directive values
  (`{time: 5/7}`) are drawn whole in the error colour.

## Preview

- **Shows the text as written**: none of the reader's transposition (the file's own `{transpose}` applies), and it
  ignores the Chords and Metronome switches (`shouldShowChords = true`, no `shouldShowTempo`), since it shows what is
  being written. The text size preference reaches the preview but not the field (the field is source, and scaling would
  break tab columns).
- Split stacks the song in one column (`isSingleColumn`), since columns reshuffling on every keystroke hide the line
  being typed; Preview alone lays out like the details screen.

## Unsaved text and the draft

- Unsaved text is the field's, measured by `hasUnsavedEditorChanges`; an untouched field follows its file
  (`ui/state/songText/CLAUDE.md`).
- Every way out goes through `Navigator.navigateBack`, which asks `UnsavedChanges` (save / discard / cancel).
  The draft lives in the view model because that dialog saves it.
- **The dialog's Save leaves only after the write succeeded** (`saveEditorChangesAndLeave`): until then the editor
  holds the only copy, so a failed write keeps the editor (and a closing desktop window) open with `SaveFailed`.
- A draft reopened on launch is unsaved like any other (update gate, way out, web `beforeunload`). An editor composed
  in a restored process waits for draft recovery (`EditorSession.editorDraftRecovery`) so a long document gets its text
  from the draft, not the file.
- A failed first read shows the details screen's failed state (Retry, Close).

## A new song from a pasted or shared text

- Opened by `EditorSession.openNewSongEditor` on `NEW_SONG_FILE_NAME`, which no file has: nothing is read, nothing is
  written before Save, and nothing is synced. The text is shaped like a new song (`DraftSongFromTextUseCase`: for a
  missing title and artist, the share's page title and artist (`sharedTextTitle`, see `presentation/CLAUDE.md`), else
  localized placeholders; the template's header lines), with the filled-in title selected. Its draft goes to disk at
  once (it is then the only copy of an iOS share) and survives process death like any draft.
- Always unsaved, so Close asks; its Discard leaves nothing behind. **Save needs a title** (`Message.NewSongUntitled`;
  a kept placeholder is a title); Revert is off.
