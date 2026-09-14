# 44 · The whole filter/sort/count pipeline runs on the main thread, once per emission

**Severity:** medium (perf) · **Area:** `:domain:implementation` (`GetScreenDataUseCaseImpl`)

`GetScreenDataUseCaseImpl.invoke` (:41–96) has no `flowOn`; the consumer is `getScreenData(_songFilter).stateIn(viewModelScope, Eagerly)`
(`CampfireViewModel.kt:157`), so `combine`'s transform executes on `Dispatchers.Main`. `createScreenData()`
normalizes artist+title for every song, sorts, and counts tags/languages three times over. During the initial scan
the source publishes a partial list every 64 files, so a 2000-song library triggers ~31 full sorts of a growing list
on Main before the first frame is interactive; every tag chip tap does one more.

## Fix

Add `.flowOn(Dispatchers.Default)` between the `combine { … }` and the final `.distinctUntilChanged()`:

```kotlin
}.flowOn(Dispatchers.Default).distinctUntilChanged()
```

`combine`'s transform then runs on Default; downstream (`stateIn`) stays on the ViewModel's scope. The `cache` field
is written from one coroutine at a time (combine is sequential), so no synchronization is needed; say so in a
comment. `import kotlinx.coroutines.flow.flowOn`.

Check with a log line that `createScreenData` is no longer called on the main thread (`Thread.currentThread().name`
on the desktop target).
