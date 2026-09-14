# 43 · `updateData` after a failed read hides the failure and pins a one-item cache

**Severity:** low · **Area:** `:data:repository:implementation` (`BaseLocalDataRepository`)

`BaseLocalDataRepository.updateData` (:55) turns any state into `Idle(transform(it.data))`; after `Failure(null)`
from an unreadable songs directory, `createSong` produces `Idle([song])`, and `loadDataIfNeeded()` returns that list
forever since `data` is non-null. The library appears to hold one song until a manual rescan. Also, `read()` (:98)
replaces the state with the scan result rather than merging, so a `createSong` that lands between `list()` and the
end of a scan is dropped from the list until the next rescan.

## Fix

1. `updateData` keeps a `Failure` a failure: `_dataState.update { if (it is DataState.Failure) DataState.Failure(transform(it.data)) else DataState.Idle(transform(it.data)) }`.
2. `loadDataIfNeeded` re-reads when the state is `Failure` even if `data` is non-null:
   `mutex.withLock { _dataState.value.let { if (it is DataState.Failure || it.data == null) read() else it.data } }`.
3. The scan-vs-write window: record the `updateData` calls that arrive during a `read()` (a counter or a list of
   pending transforms under the mutex) and re-apply them to the scan result before publishing it. A simpler and
   sufficient alternative given the live rescans of a sync run: after `loadDataFromLocalSource()` returns, publish
   it, then if any `updateData` ran meanwhile (a `@Volatile var writesDuringRead`), trigger one more `read()`. Pick
   the second; document it in the class KDoc.
