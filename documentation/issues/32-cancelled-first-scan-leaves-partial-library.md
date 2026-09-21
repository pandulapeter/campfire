# 32 · Leaving the app during the first library scan leaves half the library in place as if it were all of it

**Severity:** wrong behaviour (Android only — the one platform where the view model dies while the Koin singletons
live on; needs a library large enough for the first scan to take seconds and the activity to be finished during it:
Back at the root on Android 11 and older, "Don't keep activities", or a task swiped away while the sync service keeps
the process) · **Area:** `:data:repository:implementation` (`BaseLocalDataRepository`)

## Symptom

1. A library of a few thousand songs on a slow phone. Start the app: the list fills up 64 songs at a time.
2. Press Back on the root screen before it has finished (Android 8–11 finishes `CampfireActivity`; the process stays
   cached, and `CampfireAndroidApplication` started Koin, so `SongRepositoryImpl` survives).
3. Open the app again. The list shows the 640 songs the last partial publish held and never grows. Setlist entries
   for the other songs are drawn as missing, **Export library** writes a "backup" of 640 songs, the progress indicator
   never goes away, and the "Add the demo songs" row in Settings stays disabled once tapped, because
   `importDemoLibrary()` waits for a state that is not `Loading` and none ever comes.
4. Only pull-to-refresh or the death of the process repairs it.

## Cause

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt:138-143`

```kotlin
} catch (exception: CancellationException) {
    // … Whatever a partial publish put up is dropped rather than kept, or half a
    // library would sit there as the finished one and nothing would ever read the rest of it.
    value = DataState.Idle(previousData ?: throw exception)
    throw exception
}
```

The comment describes the right behaviour, but on a *first* read `previousData` is null, so the `throw` inside the
expression runs before the assignment and the state keeps whatever `publishPartialData` last put there:
`Loading(partial)`. The cancellation comes from `viewModelScope`: `LoadScreenDataUseCaseImpl` deliberately runs the
reads in the caller's scope, and `CampfireViewModel`'s `init` (`CampfireViewModel.kt:632`) is that caller.

`loadDataIfNeeded()` (`:66-68`) then takes any non-null data for a finished read:

```kotlin
protected suspend fun loadDataIfNeeded(): T? = mutex.withLock {
    _dataState.value.data?.takeUnless { hasReadFailed } ?: read()
}
```

so the next view model's `loadScreenData(false)` returns the partial list without reading anything, and the state
stays `Loading`. The same hole has a second way in: a change that lands through `updateData` during the first scan
turns the state into `Idle(partial + change)`, and the cancellation leaves *that* behind.

## Fix

All in `BaseLocalDataRepository.kt`.

1. **`readOnce()`, the `CancellationException` branch** — assign before rethrowing, in both cases. What a cancelled
   first read falls back to is `Loading(null)`: the state the repository starts in, "nothing has been read yet",
   which is true again, is not an error, and carries no data for anybody to mistake for the library. A cancelled
   *re-read* keeps what is in the cache at that moment rather than what was there when it started: partial publishing
   is off during a re-read, so the only thing that can have changed the cache meanwhile is an `updateData` for a file
   that is already on disk, and going back to `previousData` would drop that change from the list until the next
   rescan.

   ```kotlin
   } catch (exception: CancellationException) {
       // A read the caller gave up on is not a read that failed: the data on screen stays what it was, and the
       // next caller reads again. Whatever a partial publish put up is dropped rather than kept, or half a
       // library would sit there as the finished one and nothing would ever read the rest of it - so a first read
       // goes back to having read nothing, which is also what sends the next caller to the local source. A re-read
       // publishes no partial data, and what the cache holds by now is the previous data plus the changes that
       // landed while it ran, which are on disk and stay.
       update { current -> if (previousData == null) DataState.Loading(null) else DataState.Idle(current.data ?: previousData) }
       throw exception
   }
   ```

   (`update` is `kotlinx.coroutines.flow.update`, already imported; inside `_dataState.run { … }` it is called on the
   flow.) Do not turn this into `DataState.Failure`: a cancelled read is not reported as an error, and
   `hasReadFailed` stays as it was — if the read before this one had failed, the next caller still reads again.

2. **`loadDataIfNeeded()`** — a `Loading` state is never "loaded". Reads hold `mutex` for their whole length, so a
   `Loading` state seen from inside the lock cannot be a read in progress; it is either the initial state or what a
   read left behind that ended in neither of the ways `readOnce` handles (an `Error` thrown out of a scan — see
   plan 15 for the one that is likely). Replace the body and extend the KDoc:

   ```kotlin
   /**
    * … (keep the two existing paragraphs)
    *
    * A [DataState.Loading] is read again whatever it carries. Every read holds the lock this runs under, so one seen
    * from here is not a read in progress but the leftover of one that never finished, and the data in it is however
    * much of the library that read had got through.
    */
   protected suspend fun loadDataIfNeeded(): T? = mutex.withLock {
       _dataState.value.takeUnless { it is DataState.Loading || hasReadFailed }?.data ?: read()
   }
   ```

3. **Who triggers the read afterwards** — nothing new is needed, and nothing should be added: the repository does
   not restart a read on its own, because nobody may be there to see it. Every caller that needs the library already
   goes through `loadDataIfNeeded()` and now reads instead of being handed the leftover: the next
   `CampfireViewModel`'s `init` (`loadScreenData(false)`), `ExportLibraryUseCaseImpl` and `PrepareImportUseCaseImpl`
   (`loadSongsIfNeeded()`), `SetlistRepositoryImpl.updateSetlist`, and `rescan()` reads regardless. While nobody
   asks, the state is `Loading(null)`, which `GetScreenDataUseCaseImpl` reports as loading — and the first thing a new
   view model does is ask.

What must NOT change: `publishPartialData` still only publishes while there is nothing on screen; `LoadScreenDataUseCaseImpl`
keeps running the reads in the caller's scope (moving them into a scope of the repository's own would hide this bug
but leave a scan running for an app nobody is looking at); `writeData` is plan 30's.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepositoryTest.kt`.
Give `TestRepository` a gate the load waits at after its partial publishes, and a way to make a change:

```kotlin
/** Completed by the test to let a load past its partial publishes, so that it can be cancelled or changed under first. */
var gate: CompletableDeferred<Unit>? = null

fun add(item: String) = updateData { it.orEmpty() + item }

override suspend fun loadDataFromLocalSource(): List<String> {
    batches.dropLast(1).forEach { publishPartialData(it) }
    gate?.await()
    if (shouldFail) throw IllegalStateException("The local source could not be read.")
    return batches.last()
}
```

New cases (each starts the load with `val load = launch { repository.load() }` followed by `runCurrent()`):

- `a first read that is cancelled leaves nothing behind` — batches `[a]`, `[a, b]`, gate set. After `runCurrent()`
  the last state is `Loading([a])`; `load.cancelAndJoin()`; the last state is `DataState.Loading<List<String>>(null)`.
- `a cancelled first read is read again by the next caller` — as above, then `gate = null`; `repository.load()`
  returns `[a, b]` and the last state is `Idle([a, b])`.
- `a change that landed during a cancelled first read does not pass for the library` — as the first, but
  `repository.add("x")` before the cancellation (the state is then `Idle([a, x])`); after `cancelAndJoin()` the last
  state is `Loading(null)`, and a following `load()` with the gate open returns `[a, b]`.
- `a re-read that is cancelled keeps the library and what changed meanwhile` — load `[a, b]` to the end, set the gate
  and `batches = [[c], [c, d]]`, `launch { repository.reload() }`, `runCurrent()`, `repository.add("x")`,
  `cancelAndJoin()`; the last state is `Idle([a, b, x])`, and no state ever carried `[c]`.

## Verify

1. `./gradlew :data:repository:implementation:desktopTest`, then the compile checks.
2. Android emulator (API 30, where Back at the root finishes the activity), a library of a few thousand songs (copy
   the demo songs under generated names with `adb push` into the app's `files/library/songs`, or import a generated
   zip once). Force-stop, start the app, press Back while the list is still growing, start it again from the
   launcher without killing the process: the list starts over and ends at the full count, and the progress indicator
   goes away. Before the fix it stops at the count it had reached.
3. Same steps, then **Settings → Export library**: the archive holds every song.

## Docs

`data/repository/implementation/CLAUDE.md`, the paragraph on partial data: after "A read that fails or is cancelled
falls back on the data from *before* it started rather than on whatever it had published, …" add: "For a first read
that is cancelled that is `Loading(null)`, the state the repository starts in, and `loadDataIfNeeded()` reads again
whenever it finds a `Loading` state — under the lock it runs in, one can only be what an unfinished read left
behind." The later sentence "A cancelled read is not a failed one: it is rethrown and leaves the cached data as it
was" becomes "… and leaves the cache with what it held before, plus any change that landed while it ran".

## Touches

- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepositoryTest.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on

Nothing. Shares `BaseLocalDataRepository.kt` with 30 (which rewrites `writeData`, a different function) and its test
class with whatever 30 adds there; the two do not overlap and can land in either order, one after the other.
