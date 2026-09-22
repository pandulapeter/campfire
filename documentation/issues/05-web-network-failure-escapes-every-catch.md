# 05 · Web: when the network drops, "Sync now" silently does nothing, the run is taken for interrupted at the next start, and what it brought in is not in the lists

**Severity:** wrong behaviour, with a data-loss path (web only; likely — any network failure during a run: Wi-Fi drop, offline laptop, a blocked `dropboxapi.com`, a CORS/extension refusal) · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider.transport`, `disconnect`), `:data:repository:implementation` (`SyncRepositoryImpl.runSynchronization`)

## Symptom
On the web build, with a connected Dropbox:
1. Go offline (DevTools → Network → Offline, or pull the cable) and tap **Sync now**. The progress row appears and
   disappears; the line under the account goes back to "Last synced …" as though a run had just finished. No
   "Could not reach Dropbox" message, ever.
2. Reload the page while still online: Settings says the last run was **interrupted**, and the automatic run on start
   is skipped for it (`RestoreSyncUseCase`).
3. Worse, with the network dropping **during** a run that has already downloaded files: those files are on disk, but
   the song and setlist lists still show the library from before the run (no rescan), so the next setlist edit (a
   transposition tap in a setlist, a reorder, an archive) writes the cached old setlist over the one sync just brought
   down — exactly the data-loss path review 2 plan 08 (`2847fddf`) closed for the other ways a run can end. The last
   up to two seconds of transfers are also missing from `sync-index.json`.
4. **Disconnect** while offline: the credentials are cleared, but the index stays and the state stays "Connected";
   **Sync now** then does nothing at all (see 15) until Disconnect is tapped a second time.
5. Returning from the Dropbox consent page with the network gone: Settings stays "not connected" with no reason shown.

Nothing crashes (an uncaught exception in a coroutine on wasm is re-thrown asynchronously and only reaches the
console), which is why this has gone unnoticed.

## Cause
The browser engine of Ktor does not report a failed `fetch` as an `Exception`. Ktor 3.5.2,
`ktor-client-core` wasmJs, `io/ktor/client/engine/js/compatibility/Utils.kt:55-58`:

```kotlin
onRejected = { it: JsAny ->
    continuation.resumeWithException(Error("Fail to fetch", JsError(it)))
    null
}
```

`kotlin.Error` is a `Throwable`, not an `Exception`, and so is `JsError` (`engine/js/WasmJsClientEngine.kt:188`,
`public class JsError(public val origin: JsAny) : Throwable(...)`), which is what a body read that fails half way
throws (`engine/js/browser/BrowserFetch.kt:47-48`). Nothing in Ktor's pipeline wraps it: `HttpStatement.kt:79`,
`HttpClient.kt:1407` and `HttpCallValidator.kt:149` catch `Throwable` only to rethrow it as it is.

Every catch between that and the user is `catch (exception: Exception)`:
- `DropboxSyncProvider.kt:385-401`, `transport`, the one place meant to turn every transport failure into
  `SyncNetworkException`:
  ```kotlin
  } catch (exception: Exception) {
      throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
  }
  ```
- `DropboxSyncProvider.kt:142-150`, the revoke in `disconnect`, is not even inside `transport`.
- `SyncEngine.kt:246-274`, `runOperation`: `catch (exception: Exception)` for per-file failures.
- `SyncRepositoryImpl.kt:379-392`: `catch (exception: CancellationException)` and `catch (exception: Exception)`. An
  `Error` takes neither branch, so `finishRunCutShort` (`:381`, `:386`) never runs — the periodic writer is not joined,
  the index is not written without its `isRunInProgress` marker (written `true` at `:325`), and the lists are not
  rescanned — and no `lastOutcome` is set, so the `finally` at `:388-392` leaves `lastOutcome = null` from `:313`.
  The throwable then leaves the job and is only logged by the scope's `CoroutineExceptionHandler` (`:104-108`).
- `SyncRepositoryImpl.kt:265-269` (disconnect), `:573` (token exchange in `completePendingAuthorization`),
  `CampfireViewModel.kt:673` (restore) and `:1651` (`launchLibraryChange`) — same shape.

The OPFS storage does **not** share the trait, whatever its KDoc says: its operations are promises, and
`kotlinx.coroutines.await` 1.11.0 resumes a rejected one with `JsPromiseError.toThrowable()`, whose wasm actual
(`wasmJsMain/Promise.wasm.kt:8-12`) turns anything that is not a wrapped Kotlin throwable into a plain
`Exception("Non-Kotlin exception …")` — caught by every `catch (exception: Exception)` above, and reported as
`SyncFailureReason.UNKNOWN` (29 makes it `STORAGE`). Ktor's engine is different only because it does not use
`await`: `commonFetch` builds its own continuation and resumes it with `Error(…)` by hand.

## Fix
Two layers; the first is the fix, the second makes "a run always reports how it ended" true whatever throws.

1. `DropboxSyncProvider.transport` (`:385-401`): the last branch catches `Throwable` rather than `Exception`. Only the
   HTTP call is inside `block`, so whatever else it throws is by definition the transport's failure; say so in the
   KDoc ("The browser engine reports a failed `fetch` as a `kotlin.Error`, not an `Exception`, so the last branch
   takes any `Throwable`: everything `block` can throw that is not a cancellation is the call failing."). Keep the
   `CancellationException` branch first, unchanged.
2. `DropboxSyncProvider.disconnect` (`:142-150`): run the revoke `post` through `transport { … }` so it is covered
   too (its existing `catch (exception: Exception)` then catches the `SyncNetworkException`).
3. `SyncRepositoryImpl.runSynchronization` (`:384`): add a third branch after `catch (exception: Exception)`:
   ```kotlin
   } catch (throwable: Throwable) {
       // Not an Exception: what a synchronous js(...) call throws on the web (a JsException), or a real Error. The
       // run still owes everything a failed one owes, or the marker stays on disk and the lists keep the old library.
       println("The sync run failed: $throwable")
       withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
       updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.UNKNOWN)) }
   }
   ```
   Not rethrown: the scope's handler would only log it again.
4. Do **not** change `SyncEngine.runOperation` to catch `Throwable`: after (1) no provider throws anything else, and a
   per-file branch that swallows a real `Error` (an `OutOfMemoryError` on the desktop) would carry on with the next
   file. Do not add `Throwable` catches in the ViewModel either; (1) removes the case there.
5. Leave the OPFS storage to 29: its failures are already `Exception`s (see Cause), so nothing here depends on it.

## Tests
- `DropboxRequestTest` (commonTest, run on desktop):
  - `a request the browser could not send is the service not being reached`: `provider { throw Error("Fail to fetch") }`,
    `assertFailsWith<SyncNetworkException> { provider.list() }`; same for `download` and `upload`.
  - `a token exchange the browser could not send is the service not being reached`: handler throws `Error` only for
    `TOKEN_URL`, storage with `expiresAt = 0` → `assertFailsWith<SyncNetworkException>`.
  - `disconnecting while the browser cannot send the revocation still disconnects`: handler throws `Error` for the
    revoke URL; `provider.disconnect()` returns normally and the storage's credentials are `null`.
- `SyncRepositoryImplTest`: `a run that ends in something other than an exception still reports and clears its marker`:
  a `FakeSyncProvider` whose `onDownload` throws `Error("Fail to fetch")` on the second of three downloads; after
  `awaitOutcome()`: `lastOutcome == SyncOutcome.Failure(SyncFailureReason.UNKNOWN)`, `progress == null`, and the
  stored index does not contain `"isRunInProgress": true`. (Which songs it names depends on the order the concurrent
  transfers finish in; do not assert it. For the rescan, make the error hit the last of three downloads and put
  `CONCURRENT_TRANSFERS` out of the picture by having `onDownload` for the first two complete before the third is
  asked for — or leave the rescan to `a run that fails after moving files reads the library again`, which covers the
  same `finishRunCutShort`.)

## Verify
1. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, connect a Dropbox test account (with a key in
   `local.properties`), sync once.
2. DevTools → Network → Offline, **Sync now**: Settings says the run failed because Dropbox could not be reached.
   Reload online: no "interrupted", and the automatic run happens.
3. Put ~200 songs in the Dropbox folder from another device, start a run, go Offline half way: the failure message
   shows, the Songs screen lists the files that did arrive, and the next run downloads only the rest.
4. Offline → Disconnect → confirm: Settings shows the connect button at once.
5. `./gradlew :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`.

## Docs
- `data/source/remote/implementation/CLAUDE.md`, the `network/` bullet: add "The browser engine reports a failed
  `fetch` as a `kotlin.Error` rather than an `Exception`, so the provider's `transport` takes any `Throwable` that is
  not a cancellation as the service not being reached."
- `data/repository/implementation/CLAUDE.md`, after "The repository's scope carries a `CoroutineExceptionHandler`
  that logs": "and a run that ends in a throwable that is not an `Exception` (a synchronous `js(...)` failure on the web, a real `Error`) is
  finished and reported like a failed one rather than left to it".

## Touches
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/source/remote/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 29 (the OPFS storage's own exceptions) is independent and may land in either order. 15 also edits
`SyncRepositoryImpl.kt` (`disconnect`, the top of `runSynchronization`), and 45 edits `DropboxSyncProvider.request` and
the token calls next to `transport`: schedule 05, 15 and 45 one after another, 05 first, since 45's `request`
keeps its call inside the `transport` changed here.
