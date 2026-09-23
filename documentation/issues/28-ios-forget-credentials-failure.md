# 28 — A reinstall that fails to forget the old sync credentials restores them on the next launch, for good

**Severity:** a connection nobody made on this installation (iOS; in principle every platform) · **Area:** `:data:repository` (`SyncRepositoryImpl.kt`), `:data:source:local` (`SyncStateLocalSource`)

**Read, not run.** This was found by reading the first-launch path at HEAD (2065e47f); it has not been reproduced in
a running build — provoking a Keychain delete failure needs a debugger or a fault-injecting build. The "Verification"
section below is how to confirm it.

## What the user sees

The iOS Keychain outlives an uninstall, so a reinstalled Campfire finds the previous installation's Dropbox tokens.
The first launch is meant to forget them before anything reads them (root `CLAUDE.md`, Sync: "A fresh installation
never inherits a connection … before anything restores them (`ForgetSyncConnectionUseCase`), so no run starts on an
account nobody connected here"). If forgetting fails:

- that same launch restores the previous account straight away (the call after the forget is `restoreSync()`),
  shows it connected in Settings and runs a first sync — which uploads the freshly planted demo library into the
  user's Dropbox folder, the exact thing the forget exists to prevent;
- and every later launch does the same, because the first launch still writes `preferences.json` (the demo library
  planting writes it "on every first run whether anything was planted or not"), so no later launch is a first launch
  and nothing ever tries to forget again.

How likely a Keychain delete failure is on a first launch: rare (`SecItemDelete` answering something other than
success or not-found — `errSecInteractionNotAllowed` while the device is locked is the known one, and a first launch
is normally in the foreground). The consequence is permanent, which is why it is worth closing.

## Cause

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:293-317`:

```kotlin
    override suspend fun forgetStoredConnection() = restoreMutex.withLock {
        withContext(NonCancellable) {
            providers.forEach { provider ->
                try {
                    provider.forgetStoredCredentials()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not forget the ${provider.id} credentials: ${exception.message}")
                }
            }
            // A build with no provider at all still has to lose an authorization written down by one that had one.
            discardPendingAuthorization()
            ...
            _syncState.update { SyncState.Disconnected }
        }
    }
```

The failure is logged and swallowed. The caller,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:796-797`:

```kotlin
                if (isFirstLaunch.await()) forgetSyncConnection()
                if (restoreSync()) {
```

goes on to `restoreSync()`, and `restoreConnection` (`SyncRepositoryImpl.kt:160`,
`providers.firstOrNull { it.isConnected() }`) finds the credentials that could not be deleted. `IosSecretStore.save(key,
null)` (`data/source/local/implementation/src/iosMain/.../storage/secret/SecretStore.ios.kt`) throws on any status
other than success / not-found (`check(status == errSecSuccess)`), and `DropboxSyncProvider.forgetStoredCredentials`
is `credentialsStore.save(null)`, so a failure does reach the `catch` above.

"First launch" is `IsFirstRunUseCase` — whether `preferences.json` exists — asked once as the app starts
(`CampfireViewModel.kt:768-776`); nothing remembers that the forget is still owed.

## The change

Invoke the **`code-style`** skill before the first edit.

Make the forget an obligation written down in the app's own files — which *are* removed with the app — before it is
attempted, crossed off only when it has succeeded, and honoured by every start up until then: a start up that finds
it owed tries again, and restores nothing if it still cannot. A connection made on this installation crosses it off
too, since it replaces whatever an earlier installation left.

This keeps the view model as it is — it still calls the forget on a first launch and restore on every launch; the
retry lives in the repository's restore — so, unlike the reviewer's sketch, **`CampfireViewModel.kt` is not touched**.

### `SyncStateLocalSource` — the marker

`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SyncStateLocalSource.kt`,
add:

```kotlin
    /**
     * Whether forgetting the stored credentials is still owed: noted before a fresh installation forgets what a
     * previous one left in a store that outlived it, and cleared once that has succeeded or a connection made here has
     * replaced it. A file of this installation's own, so that the start up after a failed attempt - which is no longer
     * a first launch - still knows the credentials in the store are not this installation's. Kept out of the device
     * backup: it is about this device's Keychain, and another device has its own.
     */
    suspend fun isForgettingCredentialsOwed(): Boolean

    suspend fun setForgettingCredentialsOwed(isOwed: Boolean)
```

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceImpl.kt`:

```kotlin
    override suspend fun isForgettingCredentialsOwed() = fileStorage.exists(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME)

    override suspend fun setForgettingCredentialsOwed(isOwed: Boolean) {
        write(FORGETTING_OWED_FILE_NAME, if (isOwed) "" else null)
        if (isOwed) {
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME)
        }
    }
```

with `const val FORGETTING_OWED_FILE_NAME = "sync-credentials-forget-pending"` in the companion. (`write(name, null)`
deletes, as it does for the index; deleting a file that is not there is not an error for any `FileStorage` — the index
is cleared the same way on a fresh installation today.) Android's backup is an allow-list of paths
(`app/android/src/main/res/xml`), so the file stays out of it without a change there.

### `SyncRepositoryImpl.kt`

1. The forget itself, split out so that `restore` can call it under the lock it already holds (the `Mutex` is not
   reentrant), and made to answer whether it worked:

   ```kotlin
       override suspend fun forgetStoredConnection() {
           restoreMutex.withLock { withContext(NonCancellable) { forgetStoredConnectionNow() } }
       }

       /**
        * Forgets every provider's credentials, the unfinished authorization and the index, and answers whether the
        * credentials are gone. It is noted as owed before anything is attempted and crossed off only once all of it
        * worked: a failure leaves a previous installation's account in the store, and without the note the next start
        * up - no longer a first launch - would restore it, and every one after it would too. [restoreConnection] asks
        * about the note first. Callers hold [restoreMutex].
        */
       private suspend fun forgetStoredConnectionNow(): Boolean {
           quietly("note that the sync credentials are to be forgotten") { syncStateLocalSource.setForgettingCredentialsOwed(true) }
           var haveCredentialsGone = true
           providers.forEach { provider ->
               try {
                   provider.forgetStoredCredentials()
               } catch (exception: CancellationException) {
                   throw exception
               } catch (exception: Exception) {
                   println("Could not forget the ${provider.id} credentials: ${exception.message}")
                   haveCredentialsGone = false
               }
           }
           // A build with no provider at all still has to lose an authorization written down by one that had one.
           discardPendingAuthorization()
           // There cannot be an index on a fresh installation, and one that is somehow there describes a folder
           // this installation has never looked at.
           quietly("clear the sync index") { syncStateLocalSource.saveSyncIndex(null) }
           _syncState.update { SyncState.Disconnected }
           if (haveCredentialsGone) {
               quietly("note that the sync credentials are forgotten") { syncStateLocalSource.setForgettingCredentialsOwed(false) }
           }
           return haveCredentialsGone
       }

       /** For the clean-ups whose failure is worth a line in the log and nothing more. */
       private suspend fun quietly(action: String, block: suspend () -> Unit) {
           try {
               block()
           } catch (exception: CancellationException) {
               throw exception
           } catch (exception: Exception) {
               println("Could not $action: ${exception::class.simpleName}")
           }
       }
   ```

   (The index clean-up's existing `try`/`catch`, `:309-315`, becomes the `quietly` call; its message loses the
   exception's message on purpose, like `discardPendingAuthorization`'s — a storage failure's message may quote a
   path, and the log line says enough.)

2. `restoreConnection` (`:136`), right after the pending-redirect block (`:142-149`) and before the in-process
   `Connected` check (`:153`):

   ```kotlin
           // A previous installation's credentials that the first launch could not forget are tried again before
           // anything reads them - and while they still cannot be forgotten, nothing is restored from them.
           val isForgettingOwed = try {
               syncStateLocalSource.isForgettingCredentialsOwed()
           } catch (exception: CancellationException) {
               throw exception
           } catch (exception: Exception) {
               // Not knowing is not a reason to disconnect an ordinary installation, which is every one but this rare case.
               println("Could not tell whether the sync credentials are to be forgotten: ${exception::class.simpleName}")
               false
           }
           if (isForgettingOwed && !withContext(NonCancellable) { forgetStoredConnectionNow() }) {
               return disconnectedResult
           }
   ```

   After the redirect block rather than before it: a redirect that completes an authorization is a connection made
   on this installation, which crosses the note off (step 3) rather than being forgotten by it.

3. `completePendingAuthorization` (`:591-642`): in the `else -> try {` branch, right after
   `provider.completeAuthorization(...)` has returned the account (`:621-625`):

   ```kotlin
                   // Connected on this installation, so whatever an earlier one left in the store has just been written
                   // over, and a forgetting still owed for it must not take this connection down at the next start up.
                   quietly("note that the sync credentials are this installation's") {
                       syncStateLocalSource.setForgettingCredentialsOwed(false)
                   }
   ```

   After the exchange and not before it: cleared first and the exchange then failing, the unforgotten credentials
   would be restored at the next start up. If clearing fails, the next start up forgets the new connection and the
   user connects again — the rare-squared case, and the safe direction.

### Nothing else

- `CampfireViewModel.kt` stays as it is. `ForgetSyncConnectionUseCase` stays `Unit`.
- `disconnect()` needs nothing: it deletes the credentials, and a note still owed is crossed off by the next
  start up's forget, which then has nothing to forget and succeeds.
- Desktop and the web keep the credentials in `preferences/sync-credentials.json`, removed with the app's data, so the
  note never outlives what it is about there; the mechanism is common code and costs one `exists` per start up.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/`:

- `sync/FakeSyncCollaborators.kt`, `FakeSyncStateLocalSource`: `var isForgettingOwed = false` with the two methods
  reading and writing it, and `var onSetForgettingOwed: (Boolean) -> Unit = {}` to make the write fail on demand.
- `sync/FakeSyncProvider.kt`: `var onForgetStoredCredentials: suspend () -> Unit = {}`, called at the start of
  `forgetStoredCredentials()` so that a throw leaves `connected` true; and `var completedAccount: SyncAccount? = null`,
  which `completeAuthorization` returns (throwing `UnsupportedOperationException` while it is null, as now) and which
  sets `connected = true`. **This file has uncommitted edits by another agent at the time of writing** — merge, do not
  overwrite.

In `SyncRepositoryImplTest.kt`, next to the existing "forgetting the connection …" tests (`:440-492`):

1. `a forgetting that fails restores nothing and is owed` — provider with `ACCOUNT`, `onForgetStoredCredentials`
   throwing: `forgetStoredConnection()`; then `restore()` answers not connected, the state is `Disconnected`,
   `stateLocalSource.isForgettingOwed` is true, `provider.connected` is still true.
2. `the next start up forgets what the first one could not` — continue 1 with a **new** repository over the same
   `stateLocalSource` and provider (the next launch), `onForgetStoredCredentials = {}`: `restore()` answers not
   connected, `provider.hasForgottenCredentials` is true, `isForgettingOwed` is false. A third repository's
   `restore()` (the launch after that, with the provider connected again by hand) connects normally — the note does
   not linger.
3. `a forgetting that works leaves nothing owed` — `forgetStoredConnection()` with a working provider:
   `isForgettingOwed` is false afterwards (it was set during: record the values `onSetForgettingOwed` sees, `[true,
   false]`).
4. `an ordinary launch does not forget anything` (existing, `:494`): additionally assert that
   `onSetForgettingOwed` was never called.
5. `connecting crosses off a forgetting still owed` — `isForgettingOwed = true`, `provider.completedAccount = ACCOUNT`,
   `FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received("https://example.com/?code=c&state=state"))`
   (the fake provider's authorization request uses state `"state"`): `connect(...)` answers true and
   `isForgettingOwed` is false; a new repository's `restore()` then answers connected.
6. `not knowing whether forgetting is owed restores as usual` — make `isForgettingCredentialsOwed` throw: `restore()`
   connects.

`:data:source:local:implementation` — the marker round trip on the JVM, in the desktop tests (a new
`SyncStateLocalSourceTest.kt` next to `UserPreferencesLocalSourceTest.kt`, over a `JvmFileStorage` in a temporary
folder with a trivial in-memory `SecretStore`): owed false → set true → owed true, file present under
`preferences/` → set false → owed false, file gone; set false twice in a row does not throw.

Run the root unit test command.

## Verification

The failure itself needs fault injection. On the iOS simulator:

1. Build a debug build in which `IosSecretStore.save(key, null)` throws once (a temporary `check(false)` behind a
   flag, not committed).
2. Install, connect Dropbox, uninstall, reinstall with the faulting build, launch.
   - **Before the fix:** Settings shows the old account connected, and a first sync runs (the demo songs appear in the
     Dropbox folder).
   - **After:** Settings shows disconnected; `preferences/sync-credentials-forget-pending` exists in the app container
     (`xcrun simctl get_app_container booted <bundle id> data`).
3. Relaunch with the fault switched off: still disconnected, the marker is gone, and the Keychain item is gone
   (connecting again asks for consent).
4. Regression, without the fault: connect, relaunch — stays connected, no marker is ever written. Reinstall — the
   first launch starts disconnected as before.
5. Desktop and web smoke test: connect, relaunch — stays connected.

## Docs

- Root `CLAUDE.md`, the library layout block (lines ~57-68 in the working tree): add a line
  `preferences/sync-credentials-forget-pending   a previous installation's credentials a first launch could not forget yet`,
  and in the Sync section's "A fresh installation never inherits a connection" bullet, append: "One that cannot
  forget them notes that it still owes it, in a file of its own (removed with the app, unlike the Keychain), and every
  start up tries again and restores nothing until it has; connecting on this installation crosses the note off." The
  root `CLAUDE.md` has uncommitted edits by another agent at the time of writing — merge.
- `data/repository/implementation/CLAUDE.md`, the sync paragraph on `restore`: add "A forgetting of a previous
  installation's credentials that failed is noted (`SyncStateLocalSource.setForgettingCredentialsOwed`) and retried by
  every `restore` before anything else is read; while it keeps failing, `restore` answers disconnected." (Uncommitted
  edits by another agent there too — merge.)
- `data/repository/api/CLAUDE.md` (line 29, "`forgetStoredConnection` is the local-only wipe a first launch does"):
  add "— retried by `restore` until it has worked".
- `data/source/local/api/CLAUDE.md`, the `SyncStateLocalSource` bullet (line 42, "the two documents sync remembers"):
  add "and the note that forgetting the credentials is still owed (see `SyncRepository.forgetStoredConnection`)".
- `data/source/local/implementation/CLAUDE.md`, the backup sentence (lines 28-31, "`SyncStateLocalSourceImpl` calls
  `keepOutOfDeviceBackup` after every write of `sync-index.json`"): "… of `sync-index.json` and of the
  forget-pending note". And line ~79 ("which is why a first launch forgets it"): "… and why a launch that could not
  goes on trying at every start".
- `SyncStateLocalSource`'s interface KDoc ("the two documents it has to remember between runs") → "the two documents it
  has to remember between runs, and one note about the credentials".

## Files touched

- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SyncStateLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceTest.kt` (new)
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `data/repository/api/CLAUDE.md`,
  `data/source/local/api/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on

- **`SyncRepositoryImpl.kt` is shared with lane B**: plan 18 (a cancelled connection keeps credentials — `connect` /
  `cancelConnection` / `completePendingAuthorization`), plan 19 (a transient Keystore failure disconnects —
  `restoreConnection`) and plan 20 (sync doc / first run). The edits here are the forget function, the start of
  `restoreConnection` and one call in `completePendingAuthorization`'s success branch. Land after 18 and 19, or merge
  by hand; do not let either overwrite the other.
- `FakeSyncProvider.kt`, `FakeSyncCollaborators.kt`, `SyncRepositoryImplTest.kt` and the root `CLAUDE.md` /
  `data/repository/implementation/CLAUDE.md` have **uncommitted edits by another agent** at the time of writing (the
  end-to-end work on `SyncEngine.kt` / `SyncProvider.kt`); build on the committed state plus those edits.
- `CampfireViewModel.kt` is **not** touched by this plan, contrary to the plan index's expectation.
