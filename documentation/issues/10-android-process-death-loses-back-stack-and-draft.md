# 10 · Android: process death while the editor is open loses the typed text and the navigation stack

**Severity:** high (silent loss of a draft) · **Area:** `:presentation` (`CampfireViewModel`, navigation)

## Symptom

Open a song in the editor, type, switch to a browser to look something up, get the process killed (low-memory
device, or **Don't keep activities**), come back: the app restarts on Songs, the draft is gone, nobody was asked.
Any process death also drops the reader back to Songs instead of the song they were reading.

## Cause

`CampfireViewModel.backStack` (`CampfireViewModel.kt:165`) is a plain `mutableStateListOf(CampfireDestination.Songs)`
and the ViewModel takes no `SavedStateHandle`. The editor's `TextFieldState` *is* `rememberSaveable` (per Nav3 entry,
`SongEditorScreen.kt:170`), so the text is in the Bundle — orphaned, because the `SongEditor` entry never returns.

## Fix

1. **Make the destinations serializable.** In `navigation/CampfireDestination.kt`, annotate the sealed interface and
   every implementation with `@kotlinx.serialization.Serializable` (add the serialization plugin and
   `libs.kotlin.serialization.json` to `:presentation` if not present; `data object`s serialize fine). `contentKey`
   is a computed property and needs no change.

2. **Give the ViewModel a `SavedStateHandle`.** Add `savedStateHandle: SavedStateHandle` as a constructor parameter;
   `@KoinViewModel` with `koinViewModel()` supplies it on every platform through `koin-core-viewmodel`
   (`androidx.lifecycle:lifecycle-viewmodel-savedstate` multiplatform is the dependency; add it to `:presentation`
   commonMain if it is not transitively there). On non-Android platforms the handle is simply empty.

3. **Persist the stack on every change and restore it on creation.** In `updateBackStack` (the one place the list
   changes) write `savedStateHandle[BACK_STACK_KEY] = Json.encodeToString(backStack.toList())` after the change, and
   in the property initializer restore:

   ```kotlin
   val backStack: SnapshotStateList<CampfireDestination> = mutableStateListOf<CampfireDestination>().apply {
       val restored = savedStateHandle.get<String>(BACK_STACK_KEY)?.let { runCatching { Json.decodeFromString<List<CampfireDestination>>(it) }.getOrNull() }
       addAll(restored?.takeIf { it.isNotEmpty() } ?: listOf(CampfireDestination.Songs))
   }
   ```

   The two places that assign `backStack[index] = …` directly (`updateSongFileName`) must go through the same
   persist call. A restored `SongDetails` whose songs are gone is handled by issue 52.

4. **The draft follows the entry.** Nav3's `NavDisplay` keeps per-entry saveable state keyed by `contentKey`
   (`SongEditor`'s is `"songEditor|<fileName>"`, stable). With the entry back on the stack, the field's
   `rememberSaveable` restores the typed text, `ReportDraft` reports it, and `hasUnsavedEditorChanges` becomes true
   once `_songTexts` has the file's text — so leaving asks, as it should. Verify this rather than assuming it; if
   `CampfireApp` builds the `NavDisplay` with a fresh `SaveableStateHolder`, use `rememberSaveableStateHolder()`.

5. Also persist the two `SearchState` queries and the `songFilter` if they turn out to be lost the same way (both live
   in the ViewModel); a filter that comes back after process death is fine, one that comes back after a real relaunch
   would not be — process death restore is exactly the case where it should.

6. Docs: `presentation/CLAUDE.md` navigation section.

## Verification

Emulator, **Don't keep activities** on: open the editor, type, press Home, reopen from Recents: editor is up with the
text; press back: the unsaved-changes dialog appears. Read a song, Home, reopen: the same song is shown.
