# 18 · Opening the app and leaving at once can leave sync "Disconnected" until the process restarts, and reconnecting then overwrites the stored tokens

**Severity:** wrong behaviour (all platforms, most reachable on android where the process outlives the view model; narrow timing window) · **Area:** `:data:source:remote:implementation` (`SyncCredentialsStore`)

## Symptom
1. Android, an account is connected. Start Campfire and press Back (or swipe it away and return) within the first
   moments, while start up is still reading the credentials out of the Keystore.
2. Open Campfire again — same process, new activity, new view model. Settings says **Connect to Dropbox** as if no
   account had ever been connected, and no launch sync runs. It stays that way until the process is killed.
3. If the user now presses Connect, the pending authorization is written *over* the perfectly good stored tokens, so
   backing out of the browser really does leave them disconnected.

## Cause
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStore.kt:48-60`

```kotlin
private suspend fun read(): SyncCredentialsDocument? {
    if (!hasRead) {
        cached = try {
            syncStateLocalSource.loadSyncCredentials()?.let { json.decodeFromString<SyncCredentialsDocument>(it) }
        } catch (exception: Exception) {
            println("Could not read the sync credentials: ${exception::class.simpleName}")
            null
        }
        hasRead = true
    }
    return cached
}
```

The first read of a process happens in `SyncRepositoryImpl.restore()`, which `CampfireViewModel.init` runs in
`viewModelScope`. `SyncStateLocalSourceImpl.loadSyncCredentials()` is careful to rethrow a `CancellationException`
(the Android `SecretStore.load` is a `withContext(Dispatchers.IO)`, so that is where a cleared view model surfaces) —
and `read()` catches it as an `Exception`, stores `cached = null` and latches `hasRead = true`. The store is a Koin
`@Single`, so "no credentials" is now the answer for the life of the process.
`PendingAuthorizationStoreImpl.savePendingAuthorization` then builds its document from
`credentials ?: SyncCredentialsDocument()` and writes it, which is the step that destroys the tokens on disk.

Every other failure of the read is already turned into `null` by the local source, so the only exceptions that can
reach this `catch` are the cancellation and a document that does not parse.

## Fix
1. **`SyncCredentialsStore.read()`** — rethrow the cancellation ahead of the generic clause. Because the exception
   leaves the function before `hasRead = true`, the next caller reads again; nothing else has to move. Add
   `import kotlinx.coroutines.CancellationException`.

   ```kotlin
   private suspend fun read(): SyncCredentialsDocument? {
       if (!hasRead) {
           cached = try {
               syncStateLocalSource.loadSyncCredentials()?.let { json.decodeFromString<SyncCredentialsDocument>(it) }
           } catch (exception: CancellationException) {
               // Nothing has been found out yet, so nothing may be remembered: this object outlives whoever was
               // cancelled, and "no credentials" kept from here on is what a later authorization would write its
               // pending state over.
               throw exception
           } catch (exception: Exception) {
               // A parse failure's message quotes the input around where it failed, which here can be a piece of a token.
               println("Could not read the sync credentials: ${exception::class.simpleName}")
               null
           }
           hasRead = true
       }
       return cached
   }
   ```

2. **Keep `hasRead = true` on the parse-failure path.** A document that does not parse is a definite answer ("none",
   as the local source's KDoc says: the user answers it by connecting again), and re-reading it on every request of
   a run would only print the same line a few hundred times.

3. **Do not** move the `cached = document` assignment in `write()` here — that is plan 22, which edits the same file
   and is executed after this one.

4. `load()`, `save()` and `update()` need no change: `mutex.withLock` releases the lock when the cancellation passes
   through, and every caller of theirs (`DropboxSyncProvider.isConnected`, `accessToken`, `loadAccount`, the pending
   authorization store) either rethrows cancellation already or has no catch at all.

## Tests
New file `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStoreTest.kt`
(MPL header copied from `DropboxRequestTest.kt`; `runTest` from `kotlinx-coroutines-test`, already a `commonTest`
dependency). It uses a private fake of `SyncStateLocalSource`:

```kotlin
/** Credentials in a variable, with a first read that can be made to end the way a cleared view model ends it. */
private class FakeStorage(var credentials: String?) : SyncStateLocalSource {
    var shouldCancelNextRead = false
    var readCount = 0

    override suspend fun loadSyncCredentials(): String? {
        readCount++
        if (shouldCancelNextRead) {
            shouldCancelNextRead = false
            throw CancellationException("The reader went away.")
        }
        return credentials
    }

    override suspend fun saveSyncCredentials(document: String?) {
        credentials = document
    }

    override suspend fun loadSyncIndex(): String? = null
    override suspend fun saveSyncIndex(document: String?) = Unit
}
```

with `CONNECTED = """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":1}"""`.

- `a cancelled first read is not remembered as no credentials` — `FakeStorage(CONNECTED)` with
  `shouldCancelNextRead = true`; `assertFailsWith<CancellationException> { store.load() }`; then
  `assertEquals("refresh", store.load()?.refreshToken)` and `assertEquals(2, storage.readCount)`.
- `an authorization started after a cancelled read keeps the stored tokens` — same set-up and the same failed first
  `load()`; then `PendingAuthorizationStoreImpl(store).savePendingAuthorization(SyncProviderId.DROPBOX,
  RemoteAuthorizationRequest(authorizationUrl = "https://example.com", redirectUri = null, state = "state", verifier =
  "verifier"))`; assert `storage.credentials` still contains `"refreshToken": "refresh"` (decode it with
  `Json { ignoreUnknownKeys = true }` into `SyncCredentialsDocument` and compare `refreshToken` and `pending?.state`).
- `a document that cannot be parsed is read as none, once` — `FakeStorage("not json")`; two `store.load()` calls both
  return `null`, `storage.readCount == 1`.
- `a document that was read is not read again` — `FakeStorage(CONNECTED)`; two loads, `readCount == 1` (pins the
  cache the requests of a run rely on).

The first two fail before the fix.

## Verify
- `./gradlew :data:source:remote:implementation:desktopTest`
- Android emulator, debug build with a connected account: the window is too short to hit by hand reliably, so widen
  it for the check only — put a temporary `delay(3000)` at the top of `SecretStore.android.kt`'s `load`, start the
  app, press Back within three seconds, start it again from the launcher: Settings must show the connected account and
  the launch sync must run. Remove the delay.
- `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
`data/source/remote/implementation/CLAUDE.md`, the closing "Tested in `commonTest` …" paragraph: add "the credentials
store's cache (a cancelled read is not an answer)" to the list. Nothing else describes this behaviour.

## Touches
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStore.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStoreTest.kt` (new)
- `data/source/remote/implementation/CLAUDE.md`

## Depends on
Nothing. Plan 22 edits the same file and the same new test class and comes after this one.
