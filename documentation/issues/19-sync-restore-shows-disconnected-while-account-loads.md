# 19 · On a slow connection Settings offers "Connect to Dropbox" to a connected user, and the launch sync waits, until the account request ends

**Severity:** minor (all platforms; every launch on a poor or captive network) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl.restore`), `:data:source:remote:api` (`SyncProvider`), `:data:source:remote:implementation` (`DropboxSyncProvider`)

## Symptom
1. An account is connected. Start the app on hotel Wi-Fi before the portal has been answered, or with one bar of
   signal.
2. Open Settings → Library. For as long as Dropbox's `get_current_account` takes — the 20 s connect timeout, the 60 s
   request timeout, or about a minute and a half of 5xx back-off — the sync section says **Connect to Dropbox**, as
   if the account were gone.
3. Pressing it starts a second OAuth flow that races the restore: the browser opens, and a little later the state
   flips from "Connecting…" to the connected account by itself. The launch sync is held back for the same time.

## Cause
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`:
the state starts as `Disconnected` (`:82`) and `restore()` (`:113-143`) only replaces it after a network call:

```kotlin
val connected = providers.firstOrNull { it.isConnected() }
…
val account = try {
    connected.loadAccount()          // get_current_account, with every retry and timeout in front of it
} catch (exception: CancellationException) {
```

`DropboxSyncProvider.loadAccount()` (`DropboxSyncProvider.kt:149-180`) already falls back on the stored
`displayName` / `email` / `accountId` when the request fails — but only *after* it has failed. What the device
already knows is enough to put the account on screen, and to start the launch sync, straight away; the request is
only needed to notice a changed name and a revoked grant, and a run notices the latter on its own.

## Fix
Executed after plans 11 and 17, which also edit `restore()`: plan 11 owns the redirect check at the very top, plan 17
adds an early return right after it. This plan changes what follows `val connected = …`.

1. **`:data:source:remote:api`, `SyncProvider`** — one new member, next to `loadAccount()`:

   ```kotlin
   /**
    * Who is connected as far as this device remembers, answered from what is stored and without a request - so that
    * a start up on a bad network can show the account at once and leave [loadAccount] to catch up behind it. Null
    * when nothing is connected, or when nothing was ever stored that the account could go by.
    */
   suspend fun storedAccount(): SyncAccount?
   ```

2. **`DropboxSyncProvider`** — implement it, and let the fallback of `loadAccount()` use the same function so the two
   cannot drift apart:

   ```kotlin
   override suspend fun storedAccount() = credentialsStore.load()
       ?.takeIf { it.providerId == id.id && it.refreshToken.isNotEmpty() }
       ?.let(::storedAccountOf)

   /**
    * An account whose name was never read - the first read after the authorization failed too - is still a working
    * connection, so it goes by its id until a read succeeds, which is also the name the authorization gave it.
    */
   private fun storedAccountOf(credentials: SyncCredentialsDocument): SyncAccount? {
       val name = credentials.displayName.ifEmpty { credentials.email }.ifEmpty { credentials.accountId }.ifEmpty { return null }
       return SyncAccount(providerId = id, displayName = name, email = credentials.email.takeIf(String::isNotEmpty))
   }
   ```

   In `loadAccount()`'s generic `catch`, replace the two lines that build the fallback with
   `storedAccountOf(credentials)` and shorten its comment to the first sentence ("Offline, or an answer that could not
   be read, is not "not connected": the stored name is what the app knew last time, and it is still true."), since
   the rest of it moved into the KDoc above. (`displayName` → `email` → `accountId` matches the order the network
   path uses, `displayName.ifEmpty { account.email }`.)

3. **`FakeSyncProvider`** (`data/repository/implementation/src/commonTest/.../sync/FakeSyncProvider.kt`) — add
   `override suspend fun storedAccount() = null` next to `loadAccount()`.

4. **`SyncRepositoryImpl.restore()`** — replace the block from `val account = try {` down to the closing brace of its
   `catch` with:

   ```kotlin
   // What this device already knows about the account goes on screen before the service is asked anything: on a
   // bad network that answer can take over a minute, and until it came the settings screen would offer to connect
   // an account that is connected. Only a connection nothing was ever stored about has to wait for it.
   val storedAccount = connected.storedAccount()
   val account = storedAccount ?: try {
       connected.loadAccount()
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       // Start up must never end in an exception because of a service: the library is what the app is for,
       // and sync is a thing it does on the side.
       println("Could not restore the ${connected.id} connection: ${exception.message}")
       null
   }
   ```

   The `if (account == null) { … ConnectionFailed … }` block, `loadIndex()`, the `Connected` state and the
   `isRunInProgress` write stay exactly as they are. Immediately before the final `return SyncRepository.RestoreResult(
   isConnected = true, …)` add:

   ```kotlin
   if (storedAccount != null) {
       refreshAccount(connected)
   }
   ```

   `restore()` now returns after two local reads, so `RestoreSyncUseCase` starts the launch sync without waiting for
   the account request. That is intended: a run whose credentials were revoked ends as `Failure(AUTHORIZATION)` by
   itself.

5. **`SyncRepositoryImpl`, new private members** — the job next to `indexWriteJob` and the function next to
   `scheduleIndexWrite`:

   ```kotlin
   private var accountRefreshJob: Job? = null
   ```

   ```kotlin
   /**
    * Asks the service who the account is behind a start up that has already shown what was stored. The answer only
    * ever changes a state that is still connected to the same provider: a new name replaces the stored one, and a
    * refusal - the grant was revoked elsewhere - takes the connection down the way a start up that waited would
    * have. Anything else, a dead network above all, leaves what is on screen alone.
    */
   private fun refreshAccount(provider: SyncProvider) {
       if (accountRefreshJob?.isActive == true) return
       accountRefreshJob = scope.launch {
           val account = try {
               provider.loadAccount()
           } catch (exception: CancellationException) {
               throw exception
           } catch (exception: Exception) {
               println("Could not refresh the ${provider.id} account: ${exception.message}")
               return@launch
           }
           updateConnected { connected ->
               when {
                   connected.account.providerId != provider.id -> connected
                   account == null -> SyncState.ConnectionFailed(provider.id, SyncFailureReason.AUTHORIZATION)
                   else -> connected.copy(account = account)
               }
           }
       }
   }
   ```

   Add `import com.pandulapeter.campfire.data.source.remote.api.SyncProvider`. Every exception is caught inside the
   launch, so this adds nothing that could escape the scope (plan 06 gives the scope a handler regardless).

6. **`SyncRepositoryImpl.disconnect()`** — first statement of the function, ahead of `syncJob?.cancelAndJoin()`:

   ```kotlin
   // An answer still on its way belongs to the account that is about to go, and loadAccount writes the name it
   // reads into whatever credentials are stored by then - which could be the next account's.
   accountRefreshJob?.cancel()
   ```

7. **Do not** publish `Connected` from `completePendingAuthorization` any differently, and do not start a refresh
   there: `completeAuthorization` has just asked the service. Do not make `connect()` refuse while a restore is
   running either — with the state `Connected` within milliseconds the button is simply not there.

   A refusal that arrives while the launch sync is going replaces `Connected` (and with it the progress) by
   `ConnectionFailed`; the run then fails on its next request with the same refusal and reports into a state that
   is no longer `Connected`, which `updateConnected` already drops by design.

## Tests
`data/source/remote/implementation/src/commonTest/.../dropbox/DropboxRequestTest.kt` — three cases for the new
member. Turn the private `ConnectedStorage` object into a small class so a test can choose the stored names,
`private class ConnectedStorage(private val names: String = "") : SyncStateLocalSource` whose document is
`{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":…$names}`, and let the
`provider(...)` helper take `storage: SyncStateLocalSource = ConnectedStorage()`:

- `the stored account is answered without a request` — `names = ,"displayName":"Jane","email":"jane@example.com"`,
  handler `{ error("No request was expected.") }`; expect
  `SyncAccount(SyncProviderId.DROPBOX, displayName = "Jane", email = "jane@example.com")`.
- `a stored account whose name was never read goes by its id` — `names = ,"accountId":"dbid:1"`; expect
  `displayName = "dbid:1"`, `email = null`.
- `a connection nothing was stored about has no stored account` — default storage; expect `null`.

`SyncRepositoryImpl` has no test harness; `SyncEngineTest` only needs the one-line fake from step 3 to compile.

## Verify
- `./gradlew :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`
- Desktop (`./gradlew :app:desktop:run`), account connected. Make requests hang rather than fail at once: macOS's
  Network Link Conditioner with the "100% Loss" profile (a refused connection, as from an `/etc/hosts` entry, fails
  too quickly to show anything). Start the app and open Settings → Library at once: the account name must be there
  immediately, never the Connect button; the launch sync starts and, after the connect timeout, ends as "Could not
  reach the service".
- Restore the network, revoke the app in Dropbox's web settings (Settings → Apps), start Campfire: the account shows
  for a moment, then Settings changes to the "reconnect" failure state, and no crash or stuck progress remains.
- Change the display name in Dropbox, start Campfire: the old name shows first and is replaced within a second or two.
- `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
- `data/repository/implementation/CLAUDE.md`, last sentence ("`restore` never throws for a service that refuses the
  stored credentials — the app starts disconnected and says so."): append "It shows the account from what is stored
  and asks the service behind that, so a slow network never makes a connected account look disconnected; a refusal
  that arrives later takes the connection down then."
- `data/source/remote/api/CLAUDE.md`, `SyncProvider` bullet: add "`storedAccount` answers who is connected without
  a request, `loadAccount` with one."

## Touches
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (`restore`, `disconnect`, new `refreshAccount` and `accountRefreshJob`)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`
- `data/source/remote/api/CLAUDE.md`

## Depends on
11 and 17 (same function, executed first; this plan's hunk sits below theirs). 07 touches
`DropboxRequestTest.kt`'s `provider(...)` helper too — keep both of its new parameters. Plan 20 builds on
`storedAccount()` and must come after this one.
