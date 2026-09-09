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

_(filled in by the executing agent)_
