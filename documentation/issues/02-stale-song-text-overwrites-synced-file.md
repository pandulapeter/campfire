# 02 · A tag or language toggle on an open song overwrites the version sync just pulled down

**Severity:** high (silent loss of another device's edit) · **Area:** `:presentation` (`CampfireViewModel`), `:data:repository:api/implementation`

## Symptom

Phone opens song S (text T0). A sync run downloads the laptop's newer T1 and records its hash in the index. The user
toggles a tag or a language on S from the details header. T0-with-the-tag is written over T1. The next run sees
"changed locally, unchanged remotely" and uploads it. T1 is gone from both devices, and the "changed on both sides →
keep both" rule never fires because only one side ever looked changed.

The details screen also keeps *showing* T0 after the run, since it renders from the same cache.

## Cause

`CampfireViewModel._songTexts` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:291`)
is filled by `loadSongContent` / `saveSongContent` and only ever pruned by `deleteSong` (:691) and `updateSongFileName`
(:661). A sync run ends with `rescanLibrary()` → `songRepository.rescan()` → `songContentRepository.invalidate()`, but
nothing tells the ViewModel. `setSongTag` (:700) and `setSongLanguages` (:711) read `songTexts.value[fileName]`
first, so they build on the stale copy.

## Fix

**A. Tell the ViewModel about invalidations (the real fix).**

1. In `SongContentRepository` (`data/repository/api/.../SongContentRepository.kt`) add

   ```kotlin
   /** Every [invalidate], as the file name it named — null for "everything". Whoever holds a copy of a text drops it. */
   val invalidations: Flow<String?>
   ```

   Implement it in `SongContentRepositoryImpl` with a `MutableSharedFlow<String?>(extraBufferCapacity = 64)` emitted
   from `invalidate` (use `tryEmit`; the flow is a notification, not a queue that may block the lock).

2. Add a `GetSongContentInvalidationsUseCase` (single-method interface in `domain/api`, `@Factory` impl in
   `domain/implementation`) returning that flow, and inject it into `CampfireViewModel`.

3. In the ViewModel `init`, collect it:

   ```kotlin
   viewModelScope.launch {
       getSongContentInvalidations().collect { fileName ->
           val affected = if (fileName == null) _songTexts.value.keys.toList() else listOf(fileName).filter { it in _songTexts.value }
           affected.forEach { name ->
               getSongContent(name)?.let { content -> _songTexts.update { it + (name to content.text) } }
                   ?: _songTexts.update { it - name }
           }
       }
   }
   ```

   Re-reading (rather than only dropping) is what keeps the details screen from flashing a loading indicator for a
   song that is on screen, and it is what makes `hasUnsavedEditorChanges` turn true when the editor's file changed
   underneath — which is the right answer: the user is then asked before their draft replaces the new version.

   Careful: `SongRepositoryImpl.saveSong` also calls `invalidate(fileName)` after every save the app makes itself. The
   re-read then fetches the text that was just written, so the ViewModel's own `_songTexts.update` after the save and
   this one agree; there is no loop because the collector never writes a file.

**B. Refuse to overwrite a file that changed underneath (belt and braces).**

4. Give `SongRepository.saveSong` an optional guard: `suspend fun saveSong(content: SongContent, expectedText: String? = null): Boolean`.
   In `SongRepositoryImpl.saveSong`, when `expectedText != null`, read the file first (`songLocalSource.loadSongContent`)
   and, if its text differs from `expectedText`, return `false` without writing. Thread the flag through
   `SaveSongContentUseCase`.
5. In the ViewModel, `setSongTag` and `setSongLanguages` pass the text they read as `expectedText`; on `false` they
   re-read (`getSongContent`), re-apply the edit to the fresh text and write once more (one retry is enough; after that
   report `Message.OperationFailed`). The editor's explicit save (`saveEditorChangesAndLeave`, `saveSongContent`)
   keeps overwriting: the user has just been asked, and their draft is the answer.

## Verification

- Desktop: open a song, edit the same file in the Dropbox folder from another device (or on dropbox.com), tap **Sync
  now**, then toggle a tag on the open song. The file must contain the other device's edit plus the tag.
- The details screen must show the downloaded text without leaving and re-entering.
- Editor open on a song that sync changes: leaving must ask about unsaved changes.
- Unit tests green; add a repository test for `saveSong(expectedText)` refusing a changed file if a fake local source
  is easy to build (the local source is an interface).
