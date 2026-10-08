# Move writeData/transformAndWriteData out of BaseLocalDataRepository into a WholeDocumentRepository subclass used by UserPreferencesRepositoryImpl

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/.../implementation/base/BaseLocalDataRepository.kt` (`writeData`, `transformAndWriteData`, `persistLatest`, `writeMutex`, `lastPersistedData`, `_dataState` access); new `base/WholeDocumentRepository.kt`; `UserPreferencesRepositoryImpl.kt` (superclass); `src/commonTest/.../base/BaseLocalDataRepositoryTest.kt` (`TestRepository.write`/`change` and the write tests); `data/repository/implementation/CLAUDE.md` (the "Writing is deliberately not part of that shape… Only the preferences are persisted as a whole (`writeData`)" paragraph)
**Depends on:** none. Coordinate with the concurrent lane that adds a `LibraryListRepository` base for the song/setlist repositories: the two are siblings under `BaseLocalDataRepository` and do not touch the same members.

## Problem

`BaseLocalDataRepository<T>` (255 lines) is the read-and-cache base of three repositories, but about a third of it is
a write path only one of them may use:

```kotlin
protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) { … }
protected suspend fun transformAndWriteData(transform: (T) -> T, persist: suspend (T) -> Unit) { … }
private suspend fun persistLatest(data: T, persist: suspend (T) -> Unit) { writeMutex.withLock { … } }
```

`grep` at 2940b0e0a: their only callers are `UserPreferencesRepositoryImpl` (`saveUserPreferences`,
`updateUserPreferences`) and the base's own test harness. The module CLAUDE.md is explicit that this is on purpose —
"Writing is deliberately **not** part of that shape. Songs and setlists are one file each… Only the preferences are
persisted as a whole" — yet `SongRepositoryImpl` and `SetlistRepositoryImpl` inherit both methods and the
`writeMutex`/`lastPersistedData` state, and calling `writeData` from either would compile and replace the whole cached
library list with whatever was passed. The base's KDoc and test file interleave the two concerns.

## Fix

1. Add `internal abstract class WholeDocumentRepository<T> : BaseLocalDataRepository<T>()` holding `writeData`,
   `transformAndWriteData`, `persistLatest`, `writeMutex` and `lastPersistedData`, moved verbatim with their KDoc.
2. They read and update the state the base keeps private (`_dataState.value = …`, `_dataState.update { … }`). Give the
   base two `protected` members for exactly that — e.g. `protected val currentState: DataState<T>` and
   `protected fun updateState(transform: (DataState<T>) -> DataState<T>)` (an atomic `_dataState.update`) — rather than
   making `_dataState` itself protected, so the list repositories still cannot publish arbitrary states. Check that no
   ordering changes: `writeData` publishes before it waits for `writeMutex`, and `persistLatest` reads the latest
   published value under the lock; both must stay exactly so.
3. `UserPreferencesRepositoryImpl` extends `WholeDocumentRepository<UserPreferences>`.
4. Tests: split `BaseLocalDataRepositoryTest`'s harness — `TestRepository` (reads, partial publishes, `updateData`)
   stays on the base, and the write tests use a `TestDocumentRepository : WholeDocumentRepository<List<String>>`, moved
   to `WholeDocumentRepositoryTest`. Test bodies unchanged.
5. Update the CLAUDE.md paragraph to name the subclass.

One commit.

## Tests

None new; the moved write tests (publish before persist, last change wins, a burst ends in one write, a failed write
turns into `Failure` only while its data is on show, a no-op transform writes nothing) and
`UserPreferencesRepository`-backed tests in `SyncedPreferencesTest` guard it.

## Manual check

none — covered by tests
