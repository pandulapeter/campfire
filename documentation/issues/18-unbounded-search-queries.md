# 18 · A very long paste into a search field crashes the Android app when it goes to the background

**Severity:** crash (Android; needs a paste of roughly a quarter of a million characters, so unlikely — but it is a
deterministic crash on every trip to the background until the field is cleared) plus performance (all platforms: every
keystroke lays out and searches with the whole pasted text) · **Area:** `:presentation` (`components/SearchState.kt`,
`components/Search.kt`, `dialogs/Dialogs.kt`)

## Symptom
1. Android. Copy a large text (a long document, a whole songbook exported as text — ~250,000 characters or more).
2. Songs screen → search → paste it into the field. The field accepts all of it; typing becomes sluggish, since the
   single line is laid out again and every song is matched against it on each change.
3. Press Home.

The app crashes in the background with `RuntimeException: android.os.TransactionTooLargeException: data parcel size
… bytes` from the activity's saved state, and does so again every time it is backgrounded while the search holds the
text. The setlists search behaves the same way; the song and setlist pickers' search fields and the language picker's
field hold theirs in `rememberSaveable` too.

## Cause
Nothing caps what a search field holds, and the text is put into the saved state twice:
- `components/SearchState.kt:54` — `val textFieldState = TextFieldState(initialText = initialQuery)`, and
  `components/Search.kt:523-530` binds it to a `BasicTextField` with no `inputTransformation`;
- `CampfireViewModel.kt:713-717` persists every change of it into the `SavedStateHandle` as JSON
  (`SavedSearch(isOpen, query)`);
- `components/ListItemAnimation.kt:77` — `ScrollToTopWhenChanged` keeps its key, which contains the query
  (`SongsScreen.kt:254`, `SetlistsScreen.kt:249`), in `rememberSaveable`.

A Bundle stores a `String` as UTF-16, so ~250k characters twice is past the ~1 MB Binder limit of the saved state
transaction, which Android 7+ turns into a crash. The editor was given a 50,000 character cap for exactly this reason
(`EditorFieldSaver`, see `presentation/CLAUDE.md`); the dialogs cap their fields too (`Dialogs.kt`, `MAX_TITLE_LENGTH`,
`MAX_TAG_LENGTH`, `MAX_DESCRIPTION_LENGTH`), but the search fields were left out: `Dialogs.kt:753` (language picker
`query`), `:851` (setlist picker), `:956` (song picker) and `PickerSearchField` (`:1023-1036`) only strip newlines.

## Fix
One limit for every search field, applied where the text enters.

1. `components/SearchState.kt`: add
   ```kotlin
   /**
    * The longest query a search field takes. A search is a few words, and the field's text goes into the saved state
    * of the Activity - twice, for a list screen - where a paste of a few hundred thousand characters is more than one
    * Binder transaction holds, and Android crashes the app on its way to the background.
    */
   internal const val MAX_SEARCH_QUERY_LENGTH = 100
   ```
   and start the state from a capped query: `TextFieldState(initialText = initialQuery.take(MAX_SEARCH_QUERY_LENGTH))`
   (a query saved by an earlier build may be longer).
2. `components/Search.kt`, the `BasicTextField` of `SearchField`: add
   `inputTransformation = TruncateSearchQuery,` with
   ```kotlin
   /** Keeps what fits of a paste rather than refusing all of it, which is what `InputTransformation.maxLength` does. */
   private object TruncateSearchQuery : InputTransformation {
       override fun TextFieldBuffer.transformInput() {
           if (length > MAX_SEARCH_QUERY_LENGTH) replace(MAX_SEARCH_QUERY_LENGTH, length, "")
       }
   }
   ```
   (imports: `androidx.compose.foundation.text.input.InputTransformation`, `TextFieldBuffer`).
3. `dialogs/Dialogs.kt`: the three `String` query fields take `.take(MAX_SEARCH_QUERY_LENGTH)` after their
   `replace("\n", "")`, the way the dialogs' other fields take their own maximum:
   - `SongLanguagesDialog`, `onValueChange = { query = it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH) }`;
   - `PickerSearchField`, `onValueChange = { onQueryChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) }`,
     which covers both pickers.

   (`Dialogs.kt` is in the `dialogs` package: import `com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH`.)

Every place a query reaches the saved state is then bounded, because each one is fed from a field capped above: the
`SavedSearch` JSON written by `CampfireViewModel.kt:713-717` (and read back through `restoreSearch`, `:1622`, into the
capped `TextFieldState`), `ScrollToTopWhenChanged`'s `rememberSaveable` key (`SongsScreen.kt:254`, `SetlistsScreen.kt:249`,
which embed the open search's text), and the three dialogs' `rememberSaveable` queries. No other search text is saved
(checked: every `rememberSaveable`/`SavedStateHandle` use in `:presentation`). This plan is the whole fix for the search
half of the crash; 17 only bounds the back stack and adds no second cap.

Do **not** shorten the saved state instead (e.g. stop persisting the query): restoring an open search after process
death is deliberate (`presentation/CLAUDE.md`, `CampfireDestination.kt` bullet).

## Tests
None (UI is untested).

## Verify
1. Android (`./gradlew :app:android:assembleDebug`): put 300,000 characters on the clipboard (e.g. `adb shell` with
   `cmd clipboard` where available, or paste from a long note), paste into the songs search, press Home. Before:
   crash. After: the field holds the first 100 characters, no crash; returning to the app shows the same search.
2. Same in the setlists search, the song picker ("Add songs"), the setlist picker ("Setlist assignments") and the
   language picker (song details → language chip).
3. Typing a normal query, clearing it and reopening the search behave as before.
4. Desktop and web: pasting a long text leaves the first 100 characters in the field, typing stays responsive.
5. Compile checks: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, in the `ui/navigation/CampfireDestination.kt` bullet, after "The song filter and the two
searches are saved the same way;": add "a search field takes at most 100 characters (`MAX_SEARCH_QUERY_LENGTH`, the
pickers' fields included), since its text goes into that saved state as well;".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchState.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Search.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 36 also edits `SearchField` in `Search.kt` (its `LaunchedEffect`, not the text field's parameters); schedule
the two one after another. 17 (the back stack half of the same crash) edits `CampfireViewModel.kt`, which this plan
does not touch.
