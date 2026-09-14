# 51 · Two writes to the same song from the details header can overwrite each other

**Severity:** medium · **Area:** `:presentation` (`CampfireViewModel`)

## Cause

`setSongTag` (:699–702) and `setSongLanguages` (:710–713) read `songTexts.value[fileName]`, edit, then call the
member `saveSongContent(fileName, text)` (:760–773), which launches a second coroutine and returns at once; the
outer `launchLibraryChange` never sees the write's outcome, `_songTexts` is only updated after the write and the
repository's re-parse finish, and no per-file lock exists. Tap the ✕ of tag A and then of tag B before the first
write has round-tripped: both edits start from the same text; whichever lands last wins and the other tag comes
back. Two editor saves can land on disk out of order the same way.

## Fix

One writer per song, and the header edits read the freshest text *inside* the lock:

1. `private val songWriteMutex = Mutex()` in the ViewModel (one lock is enough: writes are rare and short; a
   per-file map is not worth it).
2. Extract a `private suspend fun writeSongContent(fileName: String, text: String)` from `saveSongContent` that does
   the `NonCancellable` write and the `_songTexts` update, called under `songWriteMutex.withLock`. `saveSongContent`
   (the launching wrapper used by the editor) calls it.
3. `setSongTag` / `setSongLanguages`: acquire the lock first, then read `songTexts.value[fileName] ?: getSongContent(…)`,
   compute the edit, and write while still holding it. With issue 02's `expectedText` guard the same call also
   refuses a file sync changed underneath.
4. `_isSavingSong` stays as it is (set around the write).

## Verification

Details screen with three tags: tap two ✕ quickly; both are gone from the file. Ctrl+S twice in the editor with
different text: the file holds the second.
