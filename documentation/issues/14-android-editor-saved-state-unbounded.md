# 14 · Android: pressing Home in the editor can kill the app and lose the unsaved text, after a dozen transposition taps on a long song or with any very large file open

**Severity:** crash + data loss (Android; a long song and a search for the right key is enough, a songbook-sized file does it on its own) · **Area:** `:presentation` (`SongEditorScreen`, `CampfireViewModel`, `CampfireApp`, strings)

## Symptom
- Open a long song in the editor (400 lines, 15–18 thousand characters). Tap the transposition stepper a dozen or so
  times looking for a key (one tap is one semitone; C to G is seven), or transpose and revert a few times over a
  session. Press Home. The process dies with `TransactionTooLargeException` (a crash since Android 7), and whatever
  was typed since the last save is gone — the very thing the saved state was there to protect.
- Or open a multi-song ChordPro "songbook" of a few hundred kilobytes (the parser accepts `{new_song}`) and press
  Home: the text alone is past the limit, with no edit at all.

Nothing of this exists on the other three platforms, which never parcel a saved state.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:183`

```kotlin
val textFieldState = rememberSaveable(destination.fileName, saver = TextFieldState.Saver) {
```

`TextFieldState.Saver` (Compose Foundation 1.12.0, `TextFieldState.kt:670-691`) saves the text, the selection **and
the undo manager**, which keeps up to 100 operations (`TEXT_UNDO_CAPACITY`). An edit with more than one change, or
one change spanning the document, is recorded as the entire text before and after (`TextUndoManager.kt`,
`recordChanges`: `preText = pre.toString(), postText = post.toString()`), and that is exactly what the editor's
whole-document rewrites are (`SongEditorScreen.kt:635-640`, called from the transposition stepper at `:312` and from
`RevertOnRequest` at `:630`):

```kotlin
private fun TextFieldState.replaceAll(text: String) = edit {
    val caret = selection.start.coerceAtMost(text.length)
    delete(0, length)
    insert(0, text)
    selection = TextRange(caret)
}
```

A Parcel stores strings as UTF-16, so one tap on a 16k character song adds ~64 KB to the saved state, and the whole
Activity state crosses to the system in one Binder transaction out of a buffer of about 1 MB per process. Nothing
bounds the text itself either.

What was checked, since the question was asked: **there is no second copy of the draft in the saved state.** Commit
`87aca9e7` restores the editor's text by saving the *back stack* into the `SavedStateHandle`, so that Navigation 3
hands this very `rememberSaveable` value back to the editor's entry; `CampfireViewModel._editorDraft` (`:358`) is
memory only, and is cleared whenever the screen is disposed (`ReportDraft`, `onEditorClosed`). The other things the
handle holds (`persist`, `:1491`: the back stack, the song filter, the two searches) are a setlist's file names at
the most — a few kilobytes — and need no cap. So this one saver is the whole of the problem.

Two things the obvious fix ("save the text and the selection only, and nothing above a threshold") would break,
because `CampfireActivity` declares no `configChanges` and is therefore recreated on every rotation, dark mode switch
and system language change, through this same saver:
- the undo history would be lost on every rotation, which it is not today;
- a text above the threshold would be thrown away on every rotation.

## Fix
One bounded saved state for process death, and the ViewModel — which outlives a configuration change, and already
holds `TextFieldState`s (`SearchState`) — for everything that needs no Bundle.

1. `SongEditorScreen.kt`: replace the `rememberSaveable` at 181–192 with

   ```kotlin
   // Keyed on the file name so that opening another song starts a new field with its own undo history, and saved
   // so that a rotation or a trip through process death does not lose what has been typed, see EditorFieldSaver.
   val fileText by rememberUpdatedState(initialText)
   val editorField = rememberSaveable(
       destination.fileName,
       saver = remember(viewModel, destination.fileName) {
           EditorFieldSaver(
               retain = { viewModel.retainEditorField(destination.fileName, it) },
               retained = { viewModel.retainedEditorField(destination.fileName) },
               fileText = { fileText },
           )
       },
   ) {
       EditorField(
           TextFieldState(
               initialText = initialText,
               initialSelection = if (destination.shouldStartInsideFirstSection) {
                   TextRange(initialText.caretInsideFirstSection())
               } else {
                   TextRange.Zero
               },
           )
       )
   }
   val textFieldState = editorField.textFieldState
   LaunchedEffect(editorField) {
       if (editorField.isDraftLost) viewModel.onEditorDraftLost()
   }
   ```

   and add, next to `replaceAll` (imports: `androidx.compose.runtime.saveable.Saver`, `SaverScope`,
   `androidx.compose.runtime.rememberUpdatedState`):

   ```kotlin
   /**
    * The editor's field, and what became of it on the way back from a saved state.
    *
    * @param isDraftLost True where the field held unsaved text too long to be saved and the process that held it
    * is gone: the field starts from the file then, and the user is told so rather than left to find out.
    */
   private class EditorField(
       val textFieldState: TextFieldState,
       val isDraftLost: Boolean = false,
   )

   /**
    * Saves the field as its text and its selection, and the text only while it is not long.
    *
    * What is saved here crosses to the system in one Binder transaction, together with everything else the Activity
    * saves and out of about a megabyte for the whole process; past that Android kills the app as it goes to the
    * background. The field's own saver also writes the undo history, in which every rewrite of the whole document (a
    * transposition, a revert) is the whole text twice, so a dozen taps on a long song add up to that megabyte.
    *
    * What this gives up is only ever wanted after a configuration change - a rotation restores through here as well -
    * and the view model lives through those, so the field itself is handed to it ([retain]) and taken back as it is,
    * undo history included and however long. A new process gets the text and the caret, or for a long document the
    * file.
    */
   private class EditorFieldSaver(
       private val retain: (TextFieldState) -> Unit,
       private val retained: () -> TextFieldState?,
       private val fileText: () -> String,
   ) : Saver<EditorField, Any> {

       override fun SaverScope.save(value: EditorField): Any {
           val textFieldState = value.textFieldState
           retain(textFieldState)
           val text = textFieldState.text.toString()
           return if (text.length <= LONG_DOCUMENT_LENGTH) {
               listOf(text, textFieldState.selection.start, textFieldState.selection.end)
           } else {
               // Whether there was anything to lose, so that an untouched long file does not come back with an apology.
               listOf(text != fileText())
           }
       }

       override fun restore(value: Any): EditorField {
           retained()?.let { return EditorField(it) }
           val saved = value as List<*>
           return if (saved.size == 1) {
               EditorField(textFieldState = TextFieldState(initialText = fileText()), isDraftLost = saved[0] as Boolean)
           } else {
               EditorField(
                   TextFieldState(
                       initialText = saved[0] as String,
                       initialSelection = TextRange(start = saved[1] as Int, end = saved[2] as Int),
                   )
               )
           }
       }
   }
   ```

   ```kotlin
   /**
    * What the editor takes for a long document: 100 KB as a saved state writes it, several times the longest song
    * and a tenth of what the transaction has room for.
    */
   private const val LONG_DOCUMENT_LENGTH = 50_000
   ```

   Update the screen's KDoc (121–130): the sentence "it is saved across configuration changes and process death with
   the field's own saver" becomes "it lives through a configuration change whole, in the view model's keeping, and
   through process death as its text and caret, see [EditorFieldSaver]".

2. `SongEditorScreen.kt`, `replaceAll` — bound the memory of the undo history too. There is no API for its capacity
   or for the size of an entry (`UndoState` offers `undo`, `redo`, `canUndo`, `canRedo`, `clearHistory`), so for an
   ordinary song it is left alone: 100 entries of a 16k character song are ~6 MB at worst, and clearing the history
   after every transposition would take away the undo the revert was deliberately given. A long document starts the
   history over *before* each rewrite, which keeps the rewrite itself undoable:

   ```kotlin
   /**
    * Replaces everything, for the rewrites that touch the whole document. The undo history records such an edit as
    * the whole text twice and keeps a hundred of them, which for a long document is more memory than a phone hands
    * out, so there the history starts over with the rewrite: it can still be undone, what came before it cannot.
    */
   private fun TextFieldState.replaceAll(text: String) {
       if (this.text.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
       edit {
           val caret = selection.start.coerceAtMost(text.length)
           delete(0, length)
           insert(0, text)
           selection = TextRange(caret)
       }
   }
   ```
   (Replacing only the span between the common prefix and suffix was weighed and dropped: a transposition's span
   runs from the first chord to the last, which is the document.)

3. `CampfireViewModel.kt`, next to `_editorDraft` (358) and its handlers (883–886):

   ```kotlin
       /**
        * The open editor's field as its screen last saved it, by file name. The screen's saved state only holds the text,
        * and not even that for a long document (see the editor's saver); this holds the field itself, for the
        * restorations this object lives through - a rotation, the system changing its theme or language - where the
        * undo history and a draft of any length can simply be handed back.
        */
       private var retainedEditorField: Pair<String, TextFieldState>? = null
   ```

   ```kotlin
       /**
        * Called by the editor whenever its state is saved. That includes one last time as the screen leaves for good,
        * by which time the stack has let go of it - and then there is nothing to keep the field for.
        */
       fun retainEditorField(fileName: String, textFieldState: TextFieldState) {
           if (backStack.any { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
               retainedEditorField = fileName to textFieldState
           }
       }

       fun retainedEditorField(fileName: String) = retainedEditorField?.takeIf { it.first == fileName }?.second

       /** Reported by the editor when it came back from a saved state that could not hold its unsaved text. */
       fun onEditorDraftLost() {
           _messages.trySend(Message.EditorDraftLost)
       }
   ```

   In `updateBackStack` (700–704), after `backStack.update()`:

   ```kotlin
           if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null
   ```

   In `Message` (1634–1642) add

   ```kotlin
           /** A long document's unsaved text did not survive the process being killed in the background. */
           data object EditorDraftLost : Message
   ```

   Do **not** move the draft into the `SavedStateHandle` (it would be encoded on every keystroke and is the same
   Bundle), and do not touch `_editorDraft`, `ReportDraft` or `onEditorClosed`: plans 05, 13, 46 and 47 work on those.

4. `CampfireApp.kt:439-453`, the `when` over the message: add
   `CampfireViewModel.Message.EditorDraftLost -> stringResource(Res.string.song_editor_draft_lost)`.

5. Strings, after `song_editor_save_failed` in both files:
   - `values/strings.xml`: `<string name="song_editor_draft_lost">The unsaved changes could not be restored</string>`
   - `values-hu/strings.xml`: `<string name="song_editor_draft_lost">A nem mentett módosításokat nem sikerült visszaállítani</string>`

What comes out: the saved state of the editor is at most 50,000 characters (100 KB) whatever the text and whatever was
done to it; a rotation keeps the text, the caret and the undo history at any size; process death keeps the text and
the caret of anything up to the bound, and says so where it could not. The one thing lost against today is the undo
history across *process death*, which no editor keeps.

## Tests
None (UI is untested).

## Verify
On an Android device or emulator, debug build (`./gradlew :app:android:assembleDebug`), with
`adb shell am kill com.pandulapeter.campfire.debug` standing in for the system killing the background process:
1. Import a 400-line song, open the editor, type a word, tap the transposition stepper 30 times, press Home. Before:
   the app crashes (`adb logcat | grep -i "TransactionTooLarge\|FAILED BINDER TRANSACTION"`). After: no crash and
   neither line in logcat. Kill the process, reopen from Recents: the editor is back with the typed word and the
   transposed text, the caret where it was; undo is empty.
2. Same song: type, transpose twice, rotate. The text, the caret and the undo history are all still there (undo twice
   puts the chords back). Switch the system to dark mode with the editor open: the same.
3. Make a 300 KB `.cho` (concatenate a song 100 times), import it, open the editor, press Home: no crash. Type a
   word, press Home, kill the process, reopen: the editor shows the file and a snackbar says the unsaved changes
   could not be restored. Repeat without typing: no snackbar. Rotate with unsaved text in it: nothing is lost.
4. In the same large file, tap transpose ten times: memory stays flat (Android Studio profiler), undo steps back
   once and then is disabled.
5. Close the editor and open another song's editor: it starts from that song's file, with an empty undo history.
6. Desktop (`./gradlew :app:desktop:run`): the editor behaves as before, revert and transpose are still undoable.
7. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`:
- In the `CampfireDestination.kt` bullet, "Navigation 3's per-entry saved state — keyed by `contentKey`, the editor's
  typed text included —" stays true; add after that sentence: "The editor saves its text and caret only, and only up
  to 50,000 characters (`EditorFieldSaver`): the Activity's saved state is one Binder transaction, and the field's own
  saver writes an undo history that holds the whole document twice per transposition. The field itself, undo history
  included, is kept by the view model across a configuration change (`retainEditorField`), and a long document that
  came back from a killed process without its unsaved text says so in a snackbar."
- In the song editor section, where the revert is described as "one more step of the undo history instead of the end
  of it", add: "In a document of more than 50,000 characters a whole-document rewrite (a transposition, a revert)
  starts the undo history over first, since each is recorded as the whole text twice."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plans 05, 13, 46 and 47 edit the same two Kotlin files (the draft, the save-and-leave path, the editor's
loading state) but none of the lines above; schedule them one after another rather than side by side.
