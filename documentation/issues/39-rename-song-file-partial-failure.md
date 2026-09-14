# 39 · "Update file name" leaves dangling setlist entries if a setlist save fails midway

**Severity:** low · **Area:** `:domain:implementation` (`RenameSongFileUseCaseImpl`)

`RenameSongFileUseCaseImpl.kt:32–51`: the file moves first (by design), then setlists are saved one by one, then the
preference. If `saveSetlist` throws on the second of three setlists, the file is already renamed and the remaining
setlists still point at the old name; nothing retries. Same for the transposition if the preference write fails.

## Fix

Attempt every follow-up and report at the end, so one failure does not stop the others:

```kotlin
val failures = mutableListOf<Throwable>()
setlists.forEach { setlist -> runCatching { setlistRepository.saveSetlist(…) }.onFailure { failures += it } }
runCatching { …preferences… }.onFailure { failures += it }
if (failures.isNotEmpty()) throw IllegalStateException("The song was renamed, but ${failures.size} reference(s) could not be updated.", failures.first())
```

(`runCatching` swallows `CancellationException`; check for it and rethrow, the codebase does this everywhere.) The
ViewModel's `launchLibraryChange` turns the exception into `Message.OperationFailed`, and the setlists that still
point at the old name show the song as missing until the user fixes them — which the message now at least says.
Document the partial-failure behaviour in the KDoc.
