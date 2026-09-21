# 07 · Stopping a sync run is reported as a network failure, and the run's clean-up is skipped

**Severity:** wrong behaviour (all platforms; every Stop, Disconnect or cancelled Connect that lands while a request is in flight, which during a run is nearly always) · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider`), `:data:repository:implementation` (`SyncRepositoryImpl.completePendingAuthorization`)

## Symptom
1. Connect Dropbox, start a sync of a library large enough to take a few seconds, and press **Stop** in Settings
   (or the stop action of the Android notification, or **Disconnect**).
2. Settings does not say the run was stopped. Songs the run downloaded or deleted in the last second or two are not
   in the lists until something else rescans.
3. Close and reopen the app: Settings says the last sync was **interrupted**, and the launch sync is not started
   (`RestoreResult.wasInterrupted`), although the user stopped the run cleanly. `sync-index.json` still says
   `isRunInProgress = true` and misses the last ≤ 2 s of finished transfers.

Same root cause while connecting: press **Connect**, approve in the browser, and press **Cancel** in the app while
the token exchange is in flight. Settings shows "Could not reach the service" instead of going back to the plain
Connect button.

## Cause
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt:353-363`:

```kotlin
private suspend fun <T> transport(block: suspend () -> T): T = try {
    block()
} catch (exception: SyncAuthorizationException) {
    throw exception
} catch (exception: SyncNetworkException) {
    throw exception
} catch (exception: DropboxApiException) {
    throw exception
} catch (exception: Exception) {
    throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
}
```

`CancellationException` is an `Exception`. A cancelled `httpClient.post` (and a cancelled `mutex.withLock` inside
`accessToken()`, which runs inside the same `transport { block() }`) resumes with one, and it leaves `transport` as a
`SyncNetworkException`. `SyncEngine.runOperation` (`sync/SyncEngine.kt:238-250`) catches `CancellationException`
*first* — but never sees one — and rethrows `SyncNetworkException`. A child that ends with a non-cancellation
exception while its scope is cancelling becomes that scope's final cause, so `engine.synchronize` throws
`SyncNetworkException` into `SyncRepositoryImpl.runSynchronization` (`SyncRepositoryImpl.kt:302-321`), which lands in
`catch (exception: Exception)` instead of the `CancellationException` branch that does the `NonCancellable`
clean-up. In that branch the first suspending call of an already cancelled coroutine (`indexWriteJob?.cancelAndJoin()`)
throws, so the final `saveIndex(... isRunInProgress = false)`, the outcome and the rescan are all skipped.

`completePendingAuthorization` (`SyncRepositoryImpl.kt:403-425`) has the same hole one level up: its only clause is
`catch (exception: Exception)`, so even a genuine `CancellationException` from `provider.completeAuthorization`
becomes `fail(...)` → `ConnectionFailed`, and `connect()`'s own `CancellationException` branch never runs.

### What Ktor actually throws (checked in the 3.5.2 sources in `~/.gradle/caches`, `ktor-client-core`)
- **Our own cancellation.** `attachToUserJob` (`engine/Utils.kt`) cancels the call with the caller's own
  `CancellationException`. A Stop is `Job.cancel()`, whose exception has no cause, so `unwrapCancellationException()`
  returns it as it is: `post` throws a plain `CancellationException`, and the coroutine that sees it is itself
  cancelled. (When a sibling transfer failed instead, the cancellation's cause is that failure, the unwrap hands
  *it* out, and it passes through `transport` by type — harmless, `coroutineScope` reports the first failure anyway.)
- **The request timeout.** `HttpTimeout`'s `applyRequestTimeout` does
  `executionContext.cancel(cause.message!!, HttpRequestTimeoutException(request))`, i.e. the call *is* cancelled with a
  `CancellationException` whose cause is the timeout. `HttpCallValidator` — installed unconditionally by `HttpClient`
  (`HttpClient.kt`, `config.install(HttpCallValidator)`), whatever `expectSuccess` says — runs
  `cause.unwrapCancellationException()` on `RequestError` and `ReceiveError`, which walks the cause chain to the first
  non-cancellation. The caller therefore gets `HttpRequestTimeoutException`, which is a `kotlinx.io.IOException`
  (`java.io.IOException` on the JVM), not a cancellation. The `nonJvmMain` actual of the unwrap is identical, so this
  holds on iOS and the web too.
- **Connect and socket timeouts** are `ConnectTimeoutException` / `SocketTimeoutException`, both `IOException`s thrown
  by the engines directly.

So with today's Ktor every timeout already reaches the generic clause as an `IOException`, and the only
`CancellationException` `transport` ever sees is our own — or a `TimeoutCancellationException` of the
`withTimeoutOrNull` in `disconnect()`, which is also "ours" (the timeout coroutine is the current job and it is
cancelled). The fix below still distinguishes the two cases instead of rethrowing blindly, because the cost of being
wrong is asymmetric: a foreign `CancellationException` rethrown from inside one of `SyncEngine`'s `async` children
would end the run as "stopped" with nobody having stopped it.

## Fix
1. **`DropboxSyncProvider.transport`** — add the cancellation clause as the *first* one and extend the KDoc. New
   imports, in the existing `kotlinx.coroutines` group: `kotlinx.coroutines.currentCoroutineContext` and
   `kotlinx.coroutines.ensureActive`.

   ```kotlin
   /**
    * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
    * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
    * wrapped, so that a body Campfire cannot make sense of stays the programming error it is.
    *
    * A cancellation is the one exception that says nothing about the network. Stopping a run or giving up on a
    * consent page resumes every suspended request with one, and it has to leave here as what it is: the engine and
    * the repository both answer a stopped run differently from a failed one, and can only do so if they are told.
    */
   private suspend fun <T> transport(block: suspend () -> T): T = try {
       block()
   } catch (exception: CancellationException) {
       // Thrown on again only while this coroutine really is cancelled. A cancellation that reaches a coroutine
       // nobody cancelled belongs to something underneath - a client that was closed, a timeout surfacing as one -
       // and passed on it would end a transfer of the engine's without a word, as though the user had stopped it.
       currentCoroutineContext().ensureActive()
       throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
   } catch (exception: SyncAuthorizationException) {
       throw exception
   } catch (exception: SyncNetworkException) {
       throw exception
   } catch (exception: DropboxApiException) {
       throw exception
   } catch (exception: Exception) {
       throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
   }
   ```

   `ensureActive()` throws the job's own cancellation exception when the job is cancelled, which is exactly the
   rethrow that is wanted (inside `disconnect()`'s `withTimeoutOrNull` that is the `TimeoutCancellationException`,
   so `withTimeoutOrNull` goes back to returning `null` by design rather than by the accident of the
   `catch (exception: Exception) { null }` around it).

2. **The three body readers in the same file** — `retryAfterSecondsInBody` (`:342-346`), `errorSummary`
   (`:376-380`) and `oAuthError` (`:427-431`) each wrap the suspending `bodyAsText()` in a bare
   `catch (exception: Exception)`. The body is already buffered, so this is not where a stop usually lands, but a
   swallowed cancellation there turns into a `DropboxApiException(status, "")` one line later. Give each a first
   clause, nothing more:

   ```kotlin
   private suspend fun HttpResponse.retryAfterSecondsInBody() = try {
       json.decodeFromString<DropboxRateLimitResponse>(bodyAsText()).error.retryAfter
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       null
   }
   ```

   and the same two lines in `errorSummary()` and in the `try` of `oAuthError()`. A plain rethrow is right here: these
   run outside `transport`, and nothing in them can produce a cancellation of somebody else's.

3. **Leave alone** in `DropboxSyncProvider`: `loadAccount()` and both `try` blocks of `disconnect()` already rethrow
   `CancellationException` first; `delete()` only catches `DropboxApiException`; `exchange()` and `accessToken()` have
   no catch of their own and go through `transport`. `SyncCredentialsStore.read()` is plan 18.

4. **`SyncRepositoryImpl.completePendingAuthorization`** — in the `else -> try { … }` branch, add one clause ahead of
   the generic one (`CancellationException` is already imported in this file):

   ```kotlin
               true
           } catch (exception: CancellationException) {
               // Giving up during the token exchange is not the exchange failing: connect() answers it by going back
               // to Disconnected, which it can only do if this arrives there as a cancellation.
               throw exception
           } catch (exception: Exception) {
   ```

   Nothing else in the function changes. Both callers are fine with it: `connect()` has the `CancellationException`
   branch (clears the pending authorization under `NonCancellable`, sets `Disconnected`, rethrows), and `restore()` is
   called from a `viewModelScope.launch` in `CampfireViewModel.init` that rethrows cancellation.

5. **Do not** touch the `catch (exception: Exception)` block of `runSynchronization` in this plan. With step 1 a
   stopped run reaches the `CancellationException` branch again, which already works under `NonCancellable`. Making
   the *failure* branch's clean-up cancellation-proof as well (a run that fails for a real reason a moment before it
   is stopped) is part of the final shape of that block, which plans 06 and 08 define; this plan is executed after
   them and must leave their block as it finds it.

6. Known leftover, deliberately not fixed here: a Cancel that lands in the few hundred milliseconds between
   `credentialsStore.save(...)` and the end of `loadAccount()` inside `DropboxSyncProvider.completeAuthorization`
   leaves valid credentials on disk while the state says `Disconnected`; the next start restores the connection the
   user had just approved in the browser. Revoking there would also throw away the tokens that `restore()` keeps on
   purpose after a refusal, so it is left as it is.

## Tests
`data/source/remote/implementation/src/commonTest/.../dropbox/DropboxRequestTest.kt` — `ktor-client-mock` is already a
`commonTest` dependency of the module (`libs.ktor.client.mock`), nothing to add to the build. Let the private
`provider(...)` helper take an optional client configuration ahead of the handler, so the existing call sites with a
trailing lambda compile unchanged:

```kotlin
private fun provider(
    configure: HttpClientConfig<*>.() -> Unit = {},
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = DropboxSyncProvider(
    httpClient = HttpClient(MockEngine(handler), configure),
    credentialsStore = SyncCredentialsStore(ConnectedStorage),
    appKey = APP_KEY,
)
```

New cases:

```kotlin
/** A stopped run resumes every request in flight with a cancellation, and the engine has to be told that it is one. */
@Test
fun `a request that is cancelled stays a cancellation`() = runTest {
    val hasStarted = CompletableDeferred<Unit>()
    val provider = provider {
        hasStarted.complete(Unit)
        awaitCancellation()
    }
    var failure: Throwable? = null
    val job = launch { failure = runCatching { provider.list() }.exceptionOrNull() }
    hasStarted.await()
    job.cancelAndJoin()
    assertIs<CancellationException>(failure)
}

/** What the client does with its own request timeout: it cancels the call, and reports the reason instead. */
@Test
fun `a request that times out is the service not being reached`() = runTest {
    val provider = provider(
        configure = { install(HttpTimeout) { requestTimeoutMillis = 50 } },
    ) { awaitCancellation() }
    val exception = assertFailsWith<SyncNetworkException> { provider.list() }
    assertIs<HttpRequestTimeoutException>(exception.cause)
}

@Test
fun `a cancellation nobody asked for is the service not being reached`() = runTest {
    val provider = provider { throw CancellationException("The engine gave up.") }
    assertFailsWith<SyncNetworkException> { provider.list() }
}
```

Imports: `io.ktor.client.HttpClientConfig`, `io.ktor.client.plugins.HttpTimeout`,
`io.ktor.client.plugins.HttpRequestTimeoutException`, `kotlinx.coroutines.CancellationException`,
`kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.awaitCancellation`, `kotlinx.coroutines.cancelAndJoin`,
`kotlinx.coroutines.launch`, `kotlin.test.assertIs`. `MockEngine` runs its handler on the engine's own dispatcher, in
real time, so the first two cases wait on it for a few milliseconds rather than in virtual time; `runTest` allows that.
The first case fails before the fix (`failure` is a `SyncNetworkException`); the second one pins the Ktor behaviour
the fix relies on, so a Ktor upgrade that changes it is noticed here rather than by a user.

No test for step 4: `SyncRepositoryImpl` has no test harness, and the clause is two lines.

## Verify
- `./gradlew :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`
- Desktop (`./gradlew :app:desktop:run`) with a `campfire.dropbox.appKey` in `local.properties`: connect, put a few
  hundred files into the library (import a zip), press **Sync now** and then **Stop** a second later. Settings must
  show the stopped/interrupted outcome straight away; `preferences/sync-index.json` must say
  `"isRunInProgress": false`; restarting the app must not report an interrupted run and must start its launch sync.
- Press **Connect**, approve in the browser, and press **Cancel** in the app as fast as possible (or disable the
  network after approving so that the exchange hangs, then Cancel): Settings must return to the plain Connect button,
  not to "Could not reach the service".
- With the network off, **Sync now** must still end as "Could not reach the service" (the generic clause is untouched).

## Docs
- `data/source/remote/implementation/CLAUDE.md`, last paragraph ("Tested in `commonTest` …"): extend "how requests
  answer being told to slow down" with "and being cancelled or timing out".
- `data/repository/implementation/CLAUDE.md` says a `CancellationException` "is caught *first* and rethrown" by the
  engine; that sentence becomes true again rather than untrue, no edit needed.

## Touches
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (`completePendingAuthorization` only)
- `data/source/remote/implementation/CLAUDE.md`

## Depends on
06 and 08 (same lane, executed first: they own the failure branch of `runSynchronization`, which this plan relies on
being cancellation-proof but does not edit). Nothing else.
