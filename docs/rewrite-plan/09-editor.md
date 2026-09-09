# Step 09: the editor

**Goal:** a raw ChordPro text editor with syntax highlighting, a live rendered preview, insertion helpers,
undo/redo, and autosave. Reached from the Songs list, the song details screen and "New song".

**Depends on:** 06, 07.

## 1. Navigation

- `CampfireDestination.SongEditor(fileName: String)` with `contentKey = "songEditor|$fileName"`, pushed on top of
  either a top-level destination or `SongDetails`. It uses the same push/pop transitions as `SongDetails`.
- View model: `openEditor(fileName)`, and `createSong(title, artist)` now navigates here after creating the file.
- Predictive back / Escape / the back arrow leave the editor after a final save (see §5).

## 2. Screen layout (`presentation/.../screens/songEditor/SongEditorScreen.kt`)

- Top app bar: back, the song title (live, from the current text's `{title}`), actions: "Preview" toggle (narrow
  layouts only), overflow with "Transpose up/down" (rewrites the text with `ChordProTransposer.transposeText`),
  "Insert section" submenu (verse, chorus, bridge, tab, grid → inserts `{start_of_x: }` … `{end_of_x}` around the
  selection or at the caret), "Insert chord" (inserts `[]` and places the caret inside), "Insert comment"
  (`{comment: }`), "Delete song" (existing dialog), "Export" (step 08).
- Body: `WindowSize` (existing helper) ≥ Expanded → `Row` with the editor on the start half and the preview on the
  end half separated by a `VerticalDivider`; otherwise one pane at a time controlled by the Preview toggle (a
  `SegmentedChoice`, existing component, "Edit" / "Preview" above the content).
- The preview is the existing `SongLyrics` in a `Column(verticalScroll)`, fed with the parse of the current text,
  debounced 150 ms (`snapshotFlow { state.text.toString() }.debounce(150)` in a `LaunchedEffect`), transposed by the
  song's user transposition so it matches what the viewer shows. Parse errors cannot happen (the parser is total).
- Desktop: `Ctrl/Cmd+S` saves immediately (add to `handleKeyEvent` in `CampfireDesktopApp.kt`), `Ctrl/Cmd+Z` / `Shift+Z`
  are handled by the text field itself.

## 3. The text field

- `BasicTextField(state = textFieldState, …)` with a `TextFieldState` remembered per file name
  (`rememberTextFieldState(initialText)`, re-created when the file changes). Monospace font
  (`FontFamily.Monospace`), `bodyLarge` scaled by the user's font scale, `lineLimits = TextFieldLineLimits.MultiLine()`,
  `KeyboardOptions(capitalization = None, autoCorrect = false)`.
- **Highlighting** via `outputTransformation = ChordProOutputTransformation` that walks the buffer's lines and calls
  `TextFieldBuffer.addStyle(SpanStyle(...), start, end)`:
  - `{directive: value}` → directive name in `primary` + bold, the value in `onSurfaceVariant`;
  - `[chord]` → chord colour (same as the viewer: `primary`, bold), `[*annotation]` italic;
  - `#` comment lines → `outline` colour, italic;
  - text inside `{start_of_tab}` … `{end_of_tab}` untouched (no chord highlighting).
  If `TextFieldBuffer.addStyle` does not exist in the pinned Compose Foundation version, fall back to the older
  `BasicTextField(value: TextFieldValue, visualTransformation = …)` API with a `VisualTransformation` that returns an
  `AnnotatedString` and `OffsetMapping.Identity`; note the choice in the execution notes.
- Undo/redo: `textFieldState.undoState.undo()` / `redo()` behind two app bar icons, enabled by `canUndo` / `canRedo`.
- Insertion helpers use `textFieldState.edit { … }` (`insert`, `replace`, `selection`).

## 4. View model state

- `openEditor` loads the content through `GetSongContentUseCase`; `SongEditorScreen` gets the initial text and an
  `onTextChanged`/`onSave` hook. Keep the `TextFieldState` in the composable (it is UI state that Navigation 3
  restores via `rememberSaveable`-compatible `TextFieldState.Saver`), not in the view model.
- `saveSongContent(fileName, text)`: `SaveSongContentUseCase` → also invalidates the parsed cache from step 06 so the
  viewer re-renders. Title/artist changes flow to the list through `SongRepository.saveSong` re-parsing metadata.

## 5. Autosave

- A `LaunchedEffect(textFieldState)` collects `snapshotFlow { state.text.toString() }`, `debounce(1000)`, and saves
  when the text differs from the last saved text.
- `DisposableEffect` `onDispose` saves synchronously-ish (launch on the view model scope with `NonCancellable`) if
  unsaved; also save on `Lifecycle.Event.ON_PAUSE` (`LifecycleEventEffect`, from lifecycle-runtime-compose).
- Show a small "Saved" / "Saving…" label at the end of the app bar (`song_editor_saved`, `song_editor_saving`;
  hu "Mentve", "Mentés…"). Save failures show a snackbar (`song_editor_save_failed`, "Could not save the song";
  hu "A dalt nem sikerült menteni") and keep the text in the field.

## 6. "New song" template

`CreateSongUseCase` writes:
```
{title: <title>}
{artist: <artist>}
{key: }

{start_of_verse: Verse 1}
{end_of_verse}
```
(omit `{artist}` when blank), and the editor opens with the caret on the empty line inside the verse.

## 7. Strings (both languages)

`song_editor_edit` (Edit / Szerkesztés — reuse `song_details_edit` if identical), `song_editor_preview` (Preview /
Előnézet), `song_editor_undo` (Undo / Visszavonás), `song_editor_redo` (Redo / Újra), `song_editor_insert_chord`
(Insert chord / Akkord beszúrása), `song_editor_insert_section` (Insert section / Szakasz beszúrása),
`song_editor_insert_comment` (Insert comment / Megjegyzés beszúrása), `song_editor_section_verse` … `_grid` (Verse,
Chorus, Bridge, Tab, Grid / Versszak, Refrén, Bridge, Tab, Rács), `song_editor_transpose_text_up` / `_down`
(Transpose text up / down; hu Szöveg transzponálása fel / le), `song_editor_saved`, `song_editor_saving`,
`song_editor_save_failed`.

Remove the `TODO(step 09)` stubs from step 07 (empty state "New song" → editor, "Edit" in the song menu and in the
song details app bar).

## Verify

- Full build for all four platforms.
- Desktop: open `everything.cho`, edit a chord, watch the preview follow within ~150 ms and the title in the app bar
  follow a `{title}` edit; wait a second, restart the app: the change is on disk. Undo/redo work. Transpose text
  up: tab lines untouched, `{key}` updated. Ctrl/Cmd+S saves immediately.
- Android (phone width): Edit/Preview toggle; the software keyboard does not cover the caret (use
  `imePadding`); rotating keeps the text.
- iOS: same as Android; the back swipe saves.
- Web: typing is not laggy on a 300-line song (if it is, throttle the highlighting to the visible lines or drop the
  output transformation on web with an `expect`; note it).

## Execution notes

- **`TextFieldBuffer.addStyle` exists in the pinned Compose Multiplatform (1.12.0)**, so the `OutputTransformation`
  route in section 3 was taken and the `VisualTransformation` fallback was not needed.
- **What counts as a directive or a chord is decided in `:chordpro`, not in the editor.** A new
  `ChordProHighlighter.tokenize` returns typed spans (directive name, directive value, chord, annotation, comment)
  and the transformation only maps those to colours - the viewer's colours, so a chord looks the same on both sides
  of the divider. It sits next to the parser so the two cannot drift, and it comes with ten tests, which a Compose
  transformation could not have.
- **Two bugs that only a real device found:**
  - The app bar title started as a regex of this screen's own with an unescaped `}`. The JVM accepts that; Android's
    ICU engine rejects it, and the editor **crashed on Android the moment it opened**. It now reads the title from
    `ChordProParser.parseMetadata`, which is the same title the song list will show (file name fallback included) -
    no second definition, and no regex dialect to trip over. The rest of the codebase was checked for the same
    pattern; `ChordProSyntax` escapes its braces properly.
  - The text field was wrapped in a `verticalScroll`, which swallowed the press that puts the caret in it: on
    Android **nothing could be typed at all**. `BasicTextField` scrolls itself when it is allowed more than one
    line, so the wrapper is gone and it is given its own `scrollState` instead. Desktop hid this, because the tests
    there drive the state rather than the pointer.
- **Ctrl/Cmd+S is handled by the text field**, not by `handleKeyEvent` in `CampfireDesktopApp` as section 2
  suggests: the window's key handler has no way to reach the text, which lives in the screen's `TextFieldState`.
  A `Modifier.onPreviewKeyEvent` on the field needs no plumbing and works on the web too.
- **The save label has three states, one of them empty**: "Saving…" only while a write is in flight, "Saved" once
  the text on screen is the text on disk, and nothing in the second in between. Saying "Saving…" through the
  debounce would be claiming something the app is not doing yet.
- The `{key: }` line and the empty line inside the verse are part of the new song template, so that the caret has
  somewhere to land; section 6's snippet shows the verse without that blank line but asks for the caret to be on it.
- `ic_rename` was renamed to `ic_edit`: it was always a pencil, and it is now what both "Rename setlist" and "Edit
  song" use.
- The editor's overflow keeps every text rewrite in one menu ("Insert chord", "Insert comment", "Insert section" and
  its submenu, transpose up/down, Export, Delete). "Insert section" wraps the selection when there is one and drops
  the block at the caret when there is not, adding the newline that a block directive needs to be alone on its line.

### Verified

- All four platforms build; `:chordpro:desktopTest` (51, ten of them new) and
  `:data:source:local:implementation:desktopTest` (28) pass.
- **Desktop** (temporary hooks in `app/desktop`'s `main` and in the screen, both removed; the files match `HEAD`):
  - `everything.cho` opens with its directives, values, `#` comment line and chords each in their own style.
  - Typing replaces the text: the preview follows within the debounce, and the app bar title follows the `{title}`
    edit ("Edited By Driver").
  - A second after the typing stops the new text is on disk, read straight back from the library folder.
  - Undo restores the previous text and leaves redo available.
  - "Insert chord" puts `[]` at the caret and the caret between the brackets; "Insert section" wraps
    `{start_of_bridge}` / `{end_of_bridge}` around a blank line; "Transpose text up" by 2 rewrites `[E] [A] [B7]` to
    `[Gb] [B] [Db7]` **and** `{key: E}` to `{key: Gb}`.
  - A 1400dp window puts the editor and the preview side by side with a divider; an 800dp one shows the Edit /
    Preview toggle instead.
  - Creating a song lands in the editor on the template, with `shouldStartInsideFirstSection` set.
- **Android** (emulator, phone width): "Edit" opens the editor from the song's long-press sheet, the toggle is
  there, tapping the text focuses it and typing lands in the document, the autosave writes, and with the software
  keyboard up the caret is scrolled above it rather than hidden behind it.
- **Web** (Chrome, 334 line song): the editor opens side by side, typing 52 characters produced **no long task over
  50 ms at all**, the highlighting keeps up and the preview picks up the new line. No throttling of the output
  transformation was needed, so the `expect` escape hatch the plan allows for was not used.
- **iOS is again the gap**: the framework links and the app launches, but the editor was not opened there. Driving
  the simulator needs it to be the frontmost application, which this machine would not grant.
