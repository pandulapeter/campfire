# 22 · Connecting Dropbox on a device that cannot write its credentials crashes the app instead of saying so

**Severity:** crash (android, ios; desktop and the web survive it but are left on "Connecting…"; unlikely — needs the storage or the Keystore / Keychain to refuse a write at the moment of connecting) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl.connect`), `:data:source:remote:implementation` (`SyncCredentialsStore.write`), `:presentation` (`CampfireViewModel.connectSyncProvider`)

## Symptom
1. The device cannot write the credentials: the disk is full, or the Android Keystore refuses the key (a restored
   device, a Keystore that lost its key after a lock screen change), or the iOS Keychain answers with an error.
2. Settings → Library → **Connect to Dropbox** — most reachable as a *re*connect, from the "connect again" message a
   revoked grant leaves, because the stored tokens are kept on purpose in that state.
3. Android and iOS: the app closes. Desktop and the web: the exception is logged, Settings stays on "Connecting…",
   and Cancel does nothing (the job is dead — plan 21 makes that Cancel work regardless).

What should happen: Settings goes back to the Connect button with "The connection did not go through." above it, as
for any other failed connection.

## Cause
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:182-193`
— the clean-up runs *inside* the `catch` blocks, where nothing catches what it throws:

```kotlin
} catch (exception: CancellationException) {
    withContext(NonCancellable) { pendingAuthorizationStore.clearPendingAuthorization() }
    _syncState.update { SyncState.Disconnected }
    throw exception
} catch (exception: Exception) {
    println("Could not connect to $providerId: ${exception.message}")
    pendingAuthorizationStore.clearPendingAuthorization()      // throws again, from inside the catch
    _syncState.update { SyncState.ConnectionFailed(providerId, exception.toFailureReason()) }
    false
}
```

and the clear really does write again, because of the order in
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStore.kt:62-66`:

```kotlin
private suspend fun write(document: SyncCredentialsDocument?) {
    cached = document          // remembered before it is known to be stored
    hasRead = true
    syncStateLocalSource.saveSyncCredentials(document?.let { json.encodeToString(it) })
}
```

The sequence: `savePendingAuthorization` → `update` → `write` sets `cached` to "tokens + pending" and then throws
(`LibraryStorageException` from the file storage, a `GeneralSecurityException` from `AndroidSecretStore.save`, the
`check` in `IosSecretStore.save`) → `connect()`'s generic `catch` → `clearPendingAuthorization()` → `update` reads
the cache, finds a pending authorization that was never stored, sees a change, calls `write` again → the same
failure, now outside any `try` → out of `connect()` → out of `ConnectSyncProviderUseCaseImpl` → into
`CampfireViewModel.kt:1444`:

```kotlin
syncConnectionJob = viewModelScope.launch { connectSyncProvider.invoke(providerId, completionPage) }
```

a bare `launch` with no handler, which ends the process on Android and iOS. The state update after the throwing
line never runs, hence "Connecting…" where the process survives.

Two more ways into the same hole, found while checking the reviewer's one:
- The `Cancelled` outcome (`:167-180`) clears the pending authorization *inside* the `try`. If that write fails, the
  generic `catch` clears again and throws — and had the second attempt gone through, a user who merely closed the
  browser would have been shown a failed connection.
- The `CancellationException` branch: a storage exception thrown under `NonCancellable` replaces the cancellation
  as what the cancelled job ends with, and a launched job that ends with anything but a cancellation is an uncaught
  exception like any other. `Disconnected` is never set either.

`completePendingAuthorization`'s own clear (`:381`) is fine as it is: from `connect()` it is inside the `try` and ends
as `ConnectionFailed(STORAGE)`, which is true, and `restore()`'s caller in `CampfireViewModel.init` catches.

## Fix
Three layers, each sufficient for the reported path on its own; all three are small. Executed after plans 18 (same
store, same test class) and 21 (same repository area).

1. **`SyncCredentialsStore.write`** — remember only what was stored, and ask the storage again after a write whose
   end nobody saw:

   ```kotlin
   private suspend fun write(document: SyncCredentialsDocument?) {
       try {
           syncStateLocalSource.saveSyncCredentials(document?.let { json.encodeToString(it) })
       } catch (exception: Exception) {
           // Refused, or cancelled somewhere between starting and being seen to finish: either way what is stored
           // is no longer known here, and a guess would be acted on - a pending authorization that was never
           // written being "cleared" by a second write, a disconnect that did not happen being believed for the
           // rest of the process. The storage is asked again by whoever comes next.
           cached = null
           hasRead = false
           throw exception
       }
       cached = document
       hasRead = true
   }
   ```

   `CancellationException` is an `Exception`, so the one clause covers both and rethrows both unchanged. This
   composes with plan 18's `read()`: a cancelled re-read is not latched either.

   Consequence worth knowing, and accepted: on a device that cannot write, a token refresh is no longer remembered
   in memory for the rest of the process, so every request of a run refreshes, fails to store the result and fails —
   the run ends as `Failure(STORAGE)`, which is what plan 06 makes of a device that cannot write its index, and is
   the truth. Do **not** "fix" that by caching the refreshed token anyway: memory and the file would then disagree
   about which tokens are current, which is exactly the state this step exists to rule out.

2. **`SyncRepositoryImpl`** — a private helper next to `completePendingAuthorization`:

   ```kotlin
   /**
    * Forgets the authorization that was started, for the ways out that have already decided how they end. Clearing
    * writes the credentials document, and a storage that refuses that write must not replace the ending with an
    * exception of its own: [connect] runs in a launched coroutine with nobody to throw to, and the state has to
    * leave [SyncState.Connecting] whatever the storage says. What stays behind is a verifier nothing asks for
    * again, and the next authorization writes over it.
    */
   private suspend fun discardPendingAuthorization() = try {
       pendingAuthorizationStore.clearPendingAuthorization()
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       // Only the kind of failure: the message of one that came from the credentials document may quote it.
       println("Could not clear the pending authorization: ${exception::class.simpleName}")
   }
   ```

   Use it at exactly these places in `connect()`, changing nothing else in them:
   - the `Cancelled` branch: `pendingAuthorizationStore.clearPendingAuthorization()` → `discardPendingAuthorization()`;
   - the `CancellationException` branch: `withContext(NonCancellable) { discardPendingAuthorization() }`;
   - the generic `catch`: `pendingAuthorizationStore.clearPendingAuthorization()` → `discardPendingAuthorization()`.

   And in `cancelConnection()` (added by plan 21), replace the whole inline `try { … } catch … { … }` around the
   clear with `discardPendingAuthorization()`; its comment moves into the KDoc above and is dropped there.

   Leave `completePendingAuthorization`'s clear (`:381`) as it is — there a storage that cannot write *is* the
   outcome, and both callers already turn it into one. After this, `connect()` throws nothing but a cancellation.

3. **`CampfireViewModel.connectSyncProvider`** (`:1442-1445`) — the last line of defence the other writes of this
   view model already have. `launchLibraryChange` returns its `Job`, so:

   ```kotlin
   fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) {
       if (syncConnectionJob?.isActive == true) return
       // Connecting writes the credentials, and a storage that refuses them is reported by the repository as a
       // failed connection. This is for the exception that one day is not: it must cost a message, not the app.
       syncConnectionJob = launchLibraryChange { connectSyncProvider.invoke(providerId, completionPage) }
   }
   ```

   The lambda's `Boolean` is coerced to `Unit`. Do not route `cancelSyncConnection()` (plan 21) through it: nothing
   it calls can throw.

## Tests
**`SyncCredentialsStoreTest`** (the class plan 18 creates in `:data:source:remote:implementation`). Extend its
`FakeStorage`:

```kotlin
var writeAttemptCount = 0
var shouldRefuseNextWrite = false
var shouldCancelNextWriteAfterStoring = false

override suspend fun saveSyncCredentials(document: String?) {
    writeAttemptCount++
    if (shouldRefuseNextWrite) {
        shouldRefuseNextWrite = false
        throw IllegalStateException("The storage refused the write.")
    }
    credentials = document
    if (shouldCancelNextWriteAfterStoring) {
        shouldCancelNextWriteAfterStoring = false
        throw CancellationException("The writer went away.")
    }
}
```

- `` `a write that was refused is not remembered` `` — `FakeStorage(CONNECTED)`; `val stored = store.load()!!`;
  `shouldRefuseNextWrite = true`; `assertFailsWith<IllegalStateException> { store.save(stored.copy(accessToken = "new")) }`;
  then `store.load()?.accessToken == "access"` and `storage.readCount == 2`.
- `` `a pending authorization that could not be saved leaves nothing to clear` `` — `FakeStorage(CONNECTED)`,
  `val pendingStore = PendingAuthorizationStoreImpl(store)`; `shouldRefuseNextWrite = true`;
  `assertFailsWith<IllegalStateException> { pendingStore.savePendingAuthorization(SyncProviderId.DROPBOX, request) }`
  (the `request` of plan 18's second case); `shouldRefuseNextWrite = true` again; `pendingStore.clearPendingAuthorization()`
  must not throw; `storage.writeAttemptCount == 1`; `pendingStore.loadPendingAuthorization() == null`;
  `storage.credentials == CONNECTED`. This is the reviewer's double fault, and fails before step 1.
- `` `a write that was cancelled after it was stored is read back` `` — `FakeStorage(null)`;
  `shouldCancelNextWriteAfterStoring = true`; `assertFailsWith<CancellationException> { store.save(document) }` with
  a connected `SyncCredentialsDocument`; then `store.load()?.refreshToken == "refresh"` and `storage.readCount == 1`
  (the first read of this store happens only now, because the write dropped what it knew).

**`SyncRepositoryImplTest`** (harness from plan 06, fakes as extended by plan 21). Two more knobs:
`FakePendingAuthorizationStore` gets `var onWrite: () -> Unit = {}`, called first by both
`savePendingAuthorization` and `clearPendingAuthorization`; `FakeSyncAuthenticator` gets
`private val onAuthorize: suspend () -> Unit = {}`, run by `authorize` before it answers.

- `` `a connection whose storage refuses every write ends as a failure` `` —
  `store.onWrite = { throw LibraryStorageException("Full") }`; `connect(...)` returns `false` (before the fix it
  throws) and the state is `ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE)`.
- `` `a closed browser is not a failure even when the clean up is` `` — authenticator with
  `AuthorizationOutcome.Cancelled()`; `onWrite` starts throwing only after the first call (a counter in the
  lambda), so the save succeeds and the clear fails; `connect(...)` returns `false`, state `Disconnected`.
- `` `giving up on a connection whose clean up fails still ends disconnected` `` — authenticator with
  `onAuthorize = { awaitCancellation() }`, `onWrite` failing after the first call as above;
  `val job = launch { repository.connect(...) }`, `syncState.first { it is SyncState.Connecting }`,
  `job.cancelAndJoin()`; expect `job.isCancelled`, the state `Disconnected`, and — this is the assertion that
  matters — `runTest` itself not failing with an uncaught `LibraryStorageException`.

## Verify
- `./gradlew :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`
- Desktop (`./gradlew :app:desktop:run`), easiest place to make the storage refuse: quit the app, make the
  preferences directory of the app's data folder read-only (`chmod 555 <data dir>/preferences`), start it, Settings →
  Library → Connect to Dropbox. Expected: no browser opens, "The connection did not go through." appears above the
  Connect button, the log has one "Could not connect to DROPBOX" line and no stack trace; the button works again after
  `chmod 755` without restarting.
- Android emulator, debug build: temporarily make `AndroidSecretStore.save` start with
  `throw GeneralSecurityException("test")`, run, press Connect — the app must stay open and show the same message.
  Remove the line.
- `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
- `data/source/remote/implementation/CLAUDE.md`, the OAuth bullet, after the sentence about
  `SyncCredentialsStore.update`: add "The store remembers only what it has seen stored: a write that is refused or
  cancelled drops the cache, and the next caller reads the storage again." In the closing "Tested in `commonTest`"
  paragraph, extend plan 18's addition to "the credentials store's cache (a cancelled read is not an answer, a
  failed write is not remembered)".
- `data/repository/implementation/CLAUDE.md`, the sync paragraph, next to "`restore` never throws for a service that
  refuses the stored credentials": add "`connect` never throws anything but a cancellation — every way out of it
  leaves `Connecting`, and a clean-up the storage refuses is logged rather than allowed to replace the outcome."

## Touches
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStore.kt`
  (`write` only)
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStoreTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (`connect`: three call sites; `cancelConnection`: the inline `try`; new private `discardPendingAuthorization`)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
  (`connectSyncProvider` only)
- `data/source/remote/implementation/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`

## Depends on
18 (creates `SyncCredentialsStoreTest` and its `FakeStorage`, and edits `read()` in the same store), 21 (adds
`cancelConnection()` and the fake authenticator / pending store knobs this plan builds on) and, through 21, 06 (the
`SyncRepositoryImplTest` harness). In the same lane after the sync writer's plans on `SyncRepositoryImpl.kt` (01, 06,
08, 16, 17, 23, 24), none of which edits `connect()`. Plan 07 adds a `CancellationException` clause inside
`completePendingAuthorization`, which this plan relies on (a cancelled token exchange reaches `connect()`'s
cancellation branch) but does not touch.
