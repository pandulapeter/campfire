<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: multiple carets in the editor, as IntelliJ has them

**Status:** not started; the first step is a spike. Written against the code on 2026-10-26 (56b49ba78). `editor/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/`.

The editor gets IntelliJ's multiple carets and selections: carets added by mouse and keyboard, column (block)
selection, typing, deleting, moving, and pasting at every caret, and the toolbar's writes at every caret. The main
use is tab staffs and chord-over-lyric lines, where a column has to change on several lines at once; the field never
wraps, so a column of the text is a column on screen.

## Decisions

- **Desktop and desktop browsers only**: `isDesktopPlatform` (the desktop app, and the web build used with a pointer).
  Android, iOS, and a touch-driven web page keep one caret, and none of the code below runs there. No soft keyboard is
  supported, so no autocorrect or suggestion strip has to be replayed. A hardware keyboard's composition (macOS dead
  keys: ´ then e is é, which Hungarian typing uses all the time) is replayed (step 3).
- **IntelliJ's default keymaps**, the macOS keymap on a Mac (desktop app and browser), the Windows/Linux keymap
  elsewhere. This needs a new `expect val isMacKeymap` in `ui/platform` (desktop: `os.name`; web:
  `navigator.userAgentData.platform`, else `navigator.platform`). The app treats Ctrl and Cmd as one today, which
  stays true for every existing shortcut.
- **The field's own selection is the primary caret.** `TextFieldState` holds exactly one selection, so the others
  (*secondary carets*) live next to it, in `MultiCarets`, remembered with the `TextFieldState` in `LoadedSongEditor` so
  that they survive pane changes. They are not saved: a reload or a new process comes back with one caret.
- **No virtual space.** A caret is always at a real offset. A column selection over shorter lines puts a caret at
  each short line's end, the way IntelliJ does with virtual space off. A tab staff's lines are one length anyway.
- **Every edit at all carets is one `TextFieldState.edit`**, so it is one undo step, and undo restores the carets
  (step 9).
- **The editor's whole-text rewrites map every caret** with the function that already maps the primary (transposition,
  Prettify, sheets, save respelling, and file-follow). Where there is nothing to map by (revert), or the feature is
  about one place (Find, a header insertion), the carets collapse to the primary first. The table is in step 8.
- **Carets merge** wherever two of them touch or overlap after any change, as in IntelliJ. Merging keeps the primary
  if it is one of the two.
- **No new screen and no new setting.** The only visible control is a checked **Column selection mode** item in the
  editor's overflow menu, desktop only, as IntelliJ has in Edit.

## Keymap

| Action                                | Windows / Linux                    | macOS                          |
|---------------------------------------|------------------------------------|--------------------------------|
| Add or remove a caret                 | Alt + Shift + click                | Option + Shift + click         |
| Add a selection                       | Alt + Shift + drag                 | Option + Shift + drag          |
| Column selection                      | Alt + drag, middle-button drag     | Option + drag, middle-button drag |
| Toggle column selection mode          | Alt + Shift + Insert               | Cmd + Shift + 8                |
| Clone caret above / below             | Ctrl, Ctrl (hold) + Up / Down      | Option, Option (hold) + Up / Down |
| Add next occurrence                   | Alt + J                            | Ctrl + G                       |
| Remove the last added occurrence      | Alt + Shift + J                    | Ctrl + Shift + G               |
| Select all occurrences                | Ctrl + Alt + Shift + J             | Ctrl + Cmd + G                 |
| Add carets to ends of selected lines  | Alt + Shift + G                    | Option + Shift + G             |
| Back to one caret                     | Escape                             | Escape                         |

Character keys with Alt are heard only without Ctrl, since AltGr arrives as Ctrl + Alt on Windows and the web, as the
existing shortcuts already require (`editorFormattingShortcuts`). On a Mac, Option + J, G, and Shift + G type
characters; IntelliJ takes them over there too, and so do we, only while the field has the focus. On the web, Ctrl +
G (Mac) and Alt + J have no browser binding; Ctrl + Cmd + G has none either. Alt + drag moves windows under several
Linux window managers, which is one more reason the middle button does column selection too, in every keymap and in the
browser as well (step 7).

## Where it hooks in today

- `editor/ChordProTextField.kt`: the `BasicTextField(state = textFieldState, …)`, its scroll containers
  (`bounceVerticalScroll`, `bounceHorizontalScroll`), `fieldModifier` (the content layout inside both scrolls),
  `inputTransformation = PastedTextFormatting`, `onTextLayout` into `TextLayoutHolder`, `cursorBrush`, the
  decorator's start padding (`contentPadding` start + 16dp), and `revealOffset` for scrolling to an offset.
- `editor/LoadedSongEditor.kt`: where `textFieldState` is wired to every feature, and the editor-wide
  `onPreviewKeyEvent`s (`editorSearchShortcuts`, `editorFormattingShortcuts`).
- `editor/EditorTextReplacement.kt` (`replaceAll`, `replaceWithTransposition`, `replaceWithPrettification`,
  `replaceMatches`), `editor/EditedOffset.kt`, `ChordProTransposer.transposedOffset`,
  `ChordProPrettifier.prettifiedOffset`: the offset mapping each rewrite uses for the primary today.
- `editor/EditorFormatting.kt` (`formattingToggle`, pure) and `editor/toolbar/EditorInsertionWrites.kt`
  (`insertAtSelection`, `insertIntoHeader`, `insertChordShape`): the toolbar's writes, all over `selection`.
- `editor/search/EditorSearchMatches.kt` (`searchMatchStarts`): reused for occurrences.
- `ui/platform/Clipboard.kt` (`plainTextClipEntry`): for copy and cut.
- `presentation/src/wasmJsMain/CLAUDE.md`, *Keys*: a focused field on the web is a hidden `<input>` beside the canvas,
  so what reaches Compose and what the browser does by itself is the spike's main question.

## Steps

### 1. Spike (decides how steps 3, 4, and 7 are built, not whether)

A throwaway branch with a field, one hard-coded secondary caret, and logging, run on the desktop app (macOS, and
Windows or Linux if at hand) and on the web build in Chrome, Firefox, and Safari. Answer:

1. **Key consumption on the web.** Does consuming Backspace, Delete, an arrow, Home, End, or Enter in a field's
   `onPreviewKeyEvent` stop the hidden input from changing too, or does the change still arrive through the input
   event (deleting twice, or letting the input drift from the `TextFieldState`)? If it still arrives, the editing keys
   go through the replay of step 3 rather than through key handling.
2. **Typed characters** arrive through the `InputTransformation` on both platforms (expected); confirm that the
   transformation sees `originalSelection`, the change ranges, and composition changes for dead keys (macOS desktop
   and web).
3. **Escape** consumed by the field with secondaries present: it must not also be forwarded to the back handler on
   the web (`startForwardingEscapeKey`), which would ask to leave the editor.
4. **Copy and cut** on the web: whether Compose copies from the browser's `copy` event (then our text must go into
   that event) or from the key (then `Clipboard.setClipEntry` from the key handler works).
5. **Modifier key events** (Ctrl down, Ctrl up, Option down) reach `onPreviewKeyEvent` on both platforms, for the
   double-press clone gesture.
6. **Pointer modifiers**: `PointerEvent.keyboardModifiers` reports Alt and Shift on a press in both, and a consumed
   Initial-pass press keeps the field from moving its caret. Middle-button presses and drags arrive on the desktop
   and in all three browsers. In the browser: a `mousedown` listener in the web shell that prevents the default of a
   middle-button press over the canvas while the editor is on top stops Chrome's and Firefox's autoscroll on
   Windows; and on Linux, the middle button's paste of the primary selection does not land in the field's hidden
   input (if it does, the same listener also prevents `auxclick` and the `paste` that follows a middle press).
7. **Undo merging**: how `TextFieldState`'s undo merges consecutive typed characters when each keystroke changes
   several ranges. Several undo steps per word is acceptable; one per keystroke is noted, not fixed.

Write the answers into the package `CLAUDE.md` of step 1's package.

### 2. The caret model (pure, `commonTest`)

New package `editor/multiCaret/` with its own `CLAUDE.md`.

- `MultiCarets` (`@Stable`, snapshot state): `secondaries: List<TextRange>`, where a `TextRange`'s `start` is the
  anchor and `end` the active end, so a selection made upwards keeps its direction. Also `preferredX`
  (per caret, for vertical moves) and `isColumnMode`. `hasSecondaries`, `collapse()`, `all(primary)` (primary plus
  secondaries in text order, the primary marked).
- `CaretSet` (pure): the carets as a list with a primary index; `normalized()` sorts and merges (step 2's rule) and
  keeps the primary; `mapped(map: (Int) -> Int)` for rewrites.
- `CaretEdits.kt` (pure): applies one edit at every caret of a `CaretSet` to a text, from the last caret to the first
  so that offsets stay valid, returning the new text, the new `CaretSet`, and the list of `(start, end, text)`
  replacements (applied to the buffer in step 3/4). The edits:
  - `ReplaceSelection(text)`: typing over a selection, or inserting at a caret.
  - `DeleteAround(before, after)`: characters before and after a collapsed caret, by code point (never splitting a
    surrogate pair); a caret with a selection deletes the selection instead.
  - `PerCaret(texts)`: the distributed paste (step 7).
- `CaretNavigation.kt` (pure over `CaretLayout`): left and right by code point; word left and right (Ctrl on
  Windows/Linux, Option on macOS) with IntelliJ's word boundaries (letters and digits, `_`, then punctuation runs, then
  whitespace) and with `[`, `]`, `{`, `}`, and `:` as boundaries so that a chord is a word; line start (IntelliJ's
  smart Home: first non-blank, then column 0) and line end; up and down through `preferredX`; page up and down by the
  viewport's line count. Each move has a Shift variant that moves only the active end. With a selection and no Shift,
  left and right collapse to the selection's edge, as now.
- `CaretLayout` (interface): line count, line of an offset, line start and end, x of an offset, offset at an x on a
  line. `TextLayoutResult` implements it in the field; tests use a monospace fake (one unit per character), the only
  fake.

Tests: merging (touching, overlapping, the primary kept), every edit at carets on one line and on several, deletion at
line starts joining lines and merging carets, surrogate pairs, every move with and without Shift, `preferredX` kept
across a short line.

### 3. Typing at every caret: the replay transformation

`CaretReplay`, an `InputTransformation` remembered with `MultiCarets`, put before `PastedTextFormatting`
(`CaretReplay.then(PastedTextFormatting)`).

- Does nothing while there are no secondaries, so one-caret editing is unchanged.
- Otherwise it reads what the user's edit did to the primary as an edit relative to the primary's
  `originalSelection`: a replaced selection, or characters deleted before and after a collapsed caret plus inserted
  text. It applies the same edit at every secondary (`CaretEdits`), sets the buffer's selection to the moved primary,
  and writes the new secondaries to `MultiCarets`.
- Composition replays the same way: the dead key's ´ is an insertion at every caret, and the é that replaces it is
  "one deleted before, é inserted" at every caret. Nothing special is needed if the spike confirms composition passes
  through the transformation.
- An edit it cannot read as one change at the primary (several ranges, a change away from the primary) collapses the
  secondaries and lets the edit through as it is.
- `PastedTextFormatting` steps aside while there are secondaries: a paste at several carets is never prettified.

Tests: a buffer driven through the transformation with secondaries, for a typed character, a replaced selection,
Enter, a dead-key sequence, and an unreadable edit.

### 4. Keys at every caret

`Modifier.multiCaretKeys(…)` in `fieldModifier`, an `onPreviewKeyEvent` active only while `isDesktopPlatform`. Keys
that would only affect the primary are taken while there are secondaries; with none, only the keys that add carets
are heard.

- **Backspace, Delete**, and their word variants (Ctrl on Windows/Linux, Option on macOS), and Cmd + Backspace (to
  line start) on macOS: one `edit` through `CaretEdits` (or through the replay if spike question 1 says so).
- **Arrows, Home, End, Page Up, Page Down**, with Shift: `CaretNavigation` for every caret, then merged. Ctrl/Cmd +
  Home and End (document start and end) collapse to one caret there.
- **Escape**: back to the primary. Heard before the editor's back handling, so a first Escape never leaves the
  editor while there are secondaries.
- **Ctrl/Cmd + A** collapses and lets the field select all. **Undo and redo** are left to the field; step 9 restores
  the carets.
- All carets are drawn by step 5 while there are secondaries, so the primary blinks with them.

Tests: the key-to-action mapping is pure (`multiCaretActionOf(key, modifiers, isMacKeymap)`), tested per keymap, as
`appShortcutOf` is.

### 5. Drawing the carets

`Modifier.multiCaretDrawing(…)` on the decorator's inner `Box` (so that its coordinates are the text layout's).

- Secondary selections are drawn behind the text in `LocalTextSelectionColors.current.backgroundColor`, the primary's
  colour; the field keeps drawing the primary selection itself.
- All carets, the primary included, are drawn in front, at the field's cursor width (2dp) in `colorScheme.primary`.
  `cursorBrush` turns transparent while there are secondaries, so every caret blinks in step: one blink of 500ms on
  and 500ms off, restarted by every caret change.
- Drawn only while the field has the focus, like the field's own caret and selection.
- The output transformation only adds styles, so offsets are the same in the drawn text; confirm this once in
  `ChordProOutputTransformation`.

### 6. Adding carets

**Mouse** (`Modifier.multiCaretPointer(…)` in `fieldModifier`, a `pointerInput` reading the Initial pass, which
consumes only the presses it takes):

- Alt/Option + Shift + click adds a caret, or removes the caret (or the selection) under the pointer. The press asks
  for the field's focus through `fieldFocusRequester`.
- Alt/Option + Shift + drag adds a selection.
- A plain click or Shift + click collapses to one caret and is left to the field, which places or extends the primary
  as now. Double and triple clicks, too.
- Positions are content coordinates (`fieldModifier` is inside both scrolls) minus the decorator's start padding,
  mapped by `getOffsetForPosition`.
- A drag near an edge of the viewport scrolls it, in both axes, at a speed growing with the distance past the edge, as
  the field's own selection drag does.
- While Alt/Option is held over the field, the pointer is `PointerIcon.Crosshair`.

**Keyboard** (`multiCaretKeys`):

- **Add next occurrence**: with a collapsed primary, selects the word under it first (the first press only selects).
  Then it adds a selection at the next occurrence of the newest selection's text after it, case sensitive, wrapping
  past the end and skipping occurrences already selected (`searchMatchStarts`). The new selection becomes the
  primary, as in IntelliJ, and is scrolled into view (step 8, *Reveal*).
- **Remove the last added occurrence**: undoes the last add, keeping a stack of additions in `MultiCarets` that any
  other change clears.
- **Select all occurrences**: every occurrence of the primary's text (or of the word under it).
- **Clone caret above / below**: the second press of Ctrl (Option on a Mac) within 400ms, held, turns Up and Down into
  adding a caret on the line above the topmost (below the bottommost) caret at the primary's `preferredX`, clipped
  to the line's end. Releasing the modifier ends it.
- **Add carets to ends of selected lines**: one caret at the end of every line the primary selection touches, the
  selection dropped.

Tests: occurrences (wrapping, skipping, case, the first press selecting the word, the removal stack), clone above and
below over short and blank lines, carets at line ends.

### 7. Column selection and the clipboard

**Column selection** (`ColumnSelection.kt`, pure geometry over `CaretLayout`):

- Alt/Option + drag, or a drag with the middle button (the scroll wheel held down) on every keymap, in the desktop app
  and the browser alike: a rectangle from the press to the pointer. A middle-button press without a drag does nothing
  (no caret placed, no paste), and in the browser the shell keeps the browser's own autoscroll and primary-selection
  paste from it (spike question 6). Each line
  it spans gets a selection from the offset at the left x to the offset at the right x, clipped to the line, and
  a caret at a short line's end. The anchor side follows the drag direction, and the primary is on the line under the
  pointer.
- **Column selection mode** (the toggle, and the overflow menu's checked item): plain drags and Shift + arrows make
  rectangles instead of runs. Shift + Up and Down add or remove a line of the rectangle; Shift + Left and Right widen
  or narrow every line of it. The mode lasts until toggled off or the editor closes.

**Clipboard** (`MultiCaretClipboard.kt`, keys heard only while there are secondaries):

- Copy writes every selection's text in text order, one per line (`plainTextClipEntry`). Carets with nothing
  selected add nothing; with nothing selected anywhere, nothing is copied.
- Cut copies, then deletes every selection as one edit.
- Paste needs no key: it arrives through the replay. When the pasted text has as many lines as there are carets, each
  caret gets its own line (`CaretEdits.PerCaret`), as IntelliJ does; otherwise every caret gets all of it.
  A column of a tab staff copied and pasted at another column therefore lands line by line.

Tests: rectangles dragged in all four directions, over short and blank lines, the mode's Shift moves, copy joining,
and per-caret paste with matching and different line counts.

### 8. Every editor feature with several carets

| Feature                                                          | With secondaries                                            |
|------------------------------------------------------------------|-------------------------------------------------------------|
| Typing, paste, Backspace, Delete, moves                          | Every caret (steps 3, 4, and 7)                             |
| Bold, Italic, Underline (keys and Shortcuts row)                 | Every caret: `formattingToggle` per selection, from the last one, each decided on its own |
| Content insertions (sections, chords, comments, …)               | Every caret: `insertAtSelection` per selection, from the last one |
| Header insertions (`insertIntoHeader`), Chord shape              | Collapse to the primary first: the header is one place      |
| Transposition stepper                                            | Every caret mapped by `transposedOffset`                    |
| Prettify, paste formatting                                       | Prettify maps every caret by `prettifiedOffset`; paste formatting is off (step 3) |
| Menu sheets (`EditOnRequest`), save respelling, file-follow      | Every caret mapped by `editedOffset` (`replaceAll`)         |
| Revert                                                           | Collapse                                                    |
| Find and Find and replace                                        | Opening either sheet collapses; its selection is one match |
| Preview, Split, pane changes                                     | Carets kept (they live next to the `TextFieldState`)        |
| Undo, redo                                                       | Carets restored (step 9)                                    |
| `leavesValueToType`, `TypingRequest`                             | The primary only                                            |

- `replaceAll`, `replaceWithTransposition`, and `replaceWithPrettification` take an optional `MultiCarets` and map
  its secondaries with the same function as the primary, inside the same `edit`. A whole-text rewrite that keeps no
  mapping collapses.
- The toolbar's per-caret writes are a loop in `EditorInsertionWrites` and `EditorFormatting` over the carets
  from the last to the first, each one's existing pure function reused, with the resulting selections written back.
- **Reveal**: `revealOffset` is generalised so that `selectionReveals` can carry an offset; an added occurrence or a
  cloned caret off screen is scrolled into view the way the search's current match is.

Tests: formatting and content insertions at several carets (including two on one line), and each rewrite mapping
secondaries.

### 9. Undo and redo restore the carets

`TextFieldState`'s undo knows only the primary's selection. `CaretUndoMemory` keeps, for each edit made while there were
secondaries, the caret set before and after, keyed by the text's length and hash on each side: undo reaching a
text with a remembered "before" set restores it, redo reaching an "after" set restores that. A text with nothing
remembered collapses. Bounded to the undo history's 100 entries, and off past `LONG_DOCUMENT_LENGTH`, where the
history is cleared anyway. A restored set is clamped to the text, so a hash collision is harmless.

Tests: type at three carets, undo, redo, and a collision clamped.

### 10. Documentation, strings, and a UI test

- `editor/multiCaret/CLAUDE.md`: the model, the replay, the spike's answers, and the keymap; a short *Multiple carets*
  section in `editor/CLAUDE.md` pointing there and saying what collapses; a line in the root `CLAUDE.md`'s product
  rules ("Multiple carets and column selection on the desktop and desktop browsers only: `ui/screens/songEditor`").
- One string pair (`en`, `hu`): the overflow menu's *Column selection mode* ("Oszlopkijelölés mód").
- The web shell (`SearchShortcutEffect`'s editor list) prevents nothing new: none of the keys above has a browser
  binding to keep from it, which the spike confirms.
- A `tools/screenshots` UI test: open the editor on the demo song, Alt + Shift + click two lines, type, Escape, and
  check the text; then Alt + drag a column over a tab staff and type.
- The website's editor page and the release's what's new mention it at release time.

## Size

Steps 2 to 9 add about 10 small files in `editor/multiCaret/` and touch `ChordProTextField`, `LoadedSongEditor`,
`EditorTextReplacement`, `EditorInsertionWrites`, `EditorFormatting`, `EditorMenu`, and `ui/platform`. Most of the
logic is pure and tested in `commonTest`; the Compose side is the transformation, two modifiers (keys and pointer),
and one drawing modifier. The risky parts are all in the spike: the web's hidden input, and composition.
