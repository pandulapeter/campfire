# 19 — A secret store that cannot be read for a moment looks like no connection, and connecting again overwrites the good token

**Severity:** low — shown as disconnected for the session; a reconnect started then destroys a working refresh token
(Android mostly; iOS Keychain, desktop/web file) · **Area:** `:data:source:local:implementation`
(`SyncStateLocalSourceImpl.kt`, `SecretStore.ios.kt`), `:data:source:remote:implementation`
(`auth/SyncCredentialsStore.kt`), `:data:repository:implementation` (`SyncRepositoryImpl.kt`), `:presentation`
(`SyncSettings.kt`, `strings.xml` ×2)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced — the failure
it is about is an Android Keystore answering `KeyStoreException` for a moment, which cannot be provoked on demand. The
unit tests below are the confirmation.

> **Work in progress at review time.** `FakeSyncProvider.kt` had uncommitted modifications by another agent; quotes
> from it are `git show HEAD:<path>`, and the hook this plan adds must be added to whatever is committed.

Reviewer finding 3-sync#5.

## What the user sees

On an Android phone whose Keystore answers `KeyStoreException` / `UnrecoverableKeyException` for a moment at start-up
(some OEM builds do under memory pressure), Settings shows **Connect Dropbox** as if the account had never been
connected, and no sync runs. If the user taps **Connect Dropbox** now, the credentials document is rewritten with only
the pending authorization in it — the refresh token that was fine is gone, and if they then cancel on the consent page
the connection is lost for good. The same happens on iOS for any Keychain status other than "not found" (for example
`errSecInteractionNotAllowed` before the first unlock), and on desktop/web for a credentials file that exists and
cannot be read.

The Android store was written to avoid exactly this — its comment (`SecretStore.android.kt:52-58`) says a transient
failure "is reported as a storage failure instead and the next launch reads the same file again" — but the layers
above swallow it.

## Cause

1. `AndroidSecretStore.load` (`SecretStore.android.kt:59`) throws `LibraryStorageException` for a transient failure,
   on purpose.
2. `SyncStateLocalSourceImpl.loadSyncCredentials` (`SyncStateLocalSourceImpl.kt:25-34`) turns every exception into
   "no credentials":

   ```kotlin
       /** Unreadable credentials are treated as none, which the user answers by connecting again. */
       override suspend fun loadSyncCredentials() = try {
           secretStore.load(CREDENTIALS_FILE_NAME) ?: migratePlainFileIfPresent()
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           // The message of a failure is free to quote what it failed on, which here would be a token.
           println("Could not read the sync credentials: ${exception::class.simpleName}")
           null
       }
   ```

3. `SyncCredentialsStore.read` (`auth/SyncCredentialsStore.kt:49-66`) would do the same for anything that got through,
   and caches the answer for the life of the process (`hasRead = true`).
4. `SyncRepositoryImpl.restoreConnection` (`:160-164`) sees `isConnected() == false` and shows `Disconnected`.
5. `connect` → `PendingAuthorizationStoreImpl.savePendingAuthorization` →
   `credentialsStore.update { (credentials ?: SyncCredentialsDocument()).copy(pending = …) }` writes a document with no
   tokens over the good one.

iOS (`SecretStore.ios.kt:93`) throws `IllegalStateException("The Keychain could not read …")` for every status but
success and not-found, which step 2 swallows the same way.

## The change

Invoke the **`code-style`** skill before the first edit. A storage failure of the credentials travels up as a
`LibraryStorageException`, is never cached, and is shown as a failed connection that nothing writes over.

1. **`SyncStateLocalSourceImpl.loadSyncCredentials`** — let the storage failure through:

   ```kotlin
       /**
        * Credentials that are there and cannot be read right now - a Keystore or a Keychain that refuses for a moment, a
        * file somebody else holds - throw [LibraryStorageException], so that nobody takes them for none and writes a
        * document without them over the good one. Anything else that goes wrong is treated as none, which the user
        * answers by connecting again.
        */
       override suspend fun loadSyncCredentials() = try {
           secretStore.load(CREDENTIALS_FILE_NAME) ?: migratePlainFileIfPresent()
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: LibraryStorageException) {
           println("Could not read the sync credentials for now: ${exception::class.simpleName}")
           throw exception
       } catch (exception: Exception) {
           // The message of a failure is free to quote what it failed on, which here would be a token.
           println("Could not read the sync credentials: ${exception::class.simpleName}")
           null
       }
   ```

   Also update `SyncStateLocalSource.loadSyncCredentials`' KDoc in `:data:source:local:api` to say it throws
   `LibraryStorageException` for credentials that are there and cannot be read.
2. **`IosSecretStore.load`** (`SecretStore.ios.kt:93`): `else -> throw LibraryStorageException("The Keychain could not
   read \"$key\": $status.")` — every status other than success and not-found is the Keychain refusing for now
   (locked before the first unlock, interaction not allowed), not an absent item.
3. **`SyncCredentialsStore.read`** — rethrow the storage failure without remembering anything, like the cancellation:

   ```kotlin
               } catch (exception: CancellationException) {
                   ...unchanged...
                   throw exception
               } catch (exception: LibraryStorageException) {
                   // Unreadable for now, which is not the same as none: remembered, "none" is what the next authorization
                   // would write its pending state over, destroying tokens that were never lost. Asked again next time.
                   throw exception
               } catch (exception: Exception) {
                   ...unchanged (a document that does not parse is none, once)...
               }
   ```

   (`:data:source:remote:implementation` already depends on `:data:source:local:api`, where the exception lives.)
4. **`SyncRepositoryImpl`**:
   - `restoreConnection` (`:160-164`): ask each provider on its own, and report a storage failure as one:

     ```kotlin
             val connected = try {
                 providers.firstOrNull { it.isConnected() }
             } catch (exception: LibraryStorageException) {
                 // The credentials are there and could not be read right now. Shown as not connected, the user would
                 // connect again and the new authorization would be written over tokens that still work; this says
                 // what happened, starts no run, and the next start - or the next attempt - reads them again.
                 println("Could not read the stored sync credentials: ${exception.message}")
                 _syncState.update { SyncState.ConnectionFailed(providers.first().id, SyncFailureReason.STORAGE) }
                 return disconnectedResult
             }
     ```

     (`providers` is non-empty whenever an exception came out of one of them; with more than one provider later, name
     the one that threw by iterating instead of `firstOrNull`.)
   - `disconnect` (`:270-281`): move `provider.isConnected()` inside the existing `try`, so a storage failure while
     disconnecting is logged and the disconnect still deletes the index and ends `Disconnected`:

     ```kotlin
                 providers.forEach { provider ->
                     try {
                         if (provider.isConnected()) provider.disconnect()
                     } catch (exception: CancellationException) { … } catch (exception: Exception) { … }
                 }
     ```

   - `runSynchronization` (`:348`): `providers.firstOrNull { it.isConnected() }` is outside the run's `try`; a storage
     failure there would escape into the scope's handler with nothing on screen. Wrap it: on `LibraryStorageException`,
     `updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.STORAGE)) }` and
     `return@withLock`. (Rare in practice — a successful read is cached — but a failed credentials *write* clears the
     cache, so the next read can be the one that fails.)
5. **`:presentation`** — `SyncSettings.kt:160-170` maps `SyncFailureReason.STORAGE` for a failed connection to
   `settings_sync_connection_failed_unknown` ("The connection did not go through."), which reads as "try Connect
   again" — the one thing that should not be the first reaction here. Add
   `settings_sync_connection_failed_storage` to both string files and map `STORAGE` to it:
   - `values/strings.xml`: `Campfire could not read or save the connection on this device. It will try again the next time it starts.`
   - `values-hu/strings.xml`: `A Campfire nem tudta beolvasni vagy elmenteni a kapcsolatot ezen az eszközön. A következő indításkor újra megpróbálja.`

   (The same reason is already produced by `connect` when the pending authorization cannot be written; the sentence is
   right for that case too.)

## Tests

1. `data/source/remote/implementation/src/commonTest/.../auth/SyncCredentialsStoreTest.kt` — give the private
   `FakeStorage` a `var shouldFailNextRead = false` that throws `LibraryStorageException("Keystore busy")` once:
   - `` `a read the storage refused is not remembered as no credentials` ``: `assertFailsWith<LibraryStorageException> { store.load() }`,
     then `store.load()?.refreshToken == "refresh"`, `readCount == 2`.
   - `` `an authorization started while the credentials cannot be read writes nothing over them` ``: with the failing
     read, `assertFailsWith<LibraryStorageException> { PendingAuthorizationStoreImpl(store).savePendingAuthorization(DROPBOX, REQUEST) }`,
     `writeAttemptCount == 0`, `storage.credentials == CONNECTED`.
2. `data/source/local/implementation/src/desktopTest/.../source/SyncStateLocalSourceTest.kt` (new; the class and
   `SecretStore` are internal to the module, which the test is in): a fake `SecretStore` whose `load` throws
   `LibraryStorageException` → `loadSyncCredentials()` throws it; one that throws `IllegalStateException` → `null`;
   `FileStorage` = `JvmFileStorage(tempDir)`.
3. `data/repository/implementation/src/commonTest/.../SyncRepositoryImplTest.kt` — `FakeSyncProvider` gets
   `var onIsConnected: suspend () -> Unit = {}` run at the start of `isConnected()`:
   - `` `credentials that cannot be read right now are reported as that and start nothing` ``: `onIsConnected = { throw LibraryStorageException("Keystore busy") }`;
     `restore().isConnected` is false and `syncState.value == ConnectionFailed(DROPBOX, STORAGE)`; `provider.listCount == 0`.
   - `` `a disconnect whose credentials cannot be read still ends disconnected` ``: restore first with a working
     provider, then make `isConnected` throw, `disconnect()`; state `Disconnected`, `stateLocalSource.index == null`.

## Verification

1. Tests above; root unit test command; compile `:data:source:local:implementation:compileKotlinIosSimulatorArm64`
   and `:presentation:compileKotlinDesktop`.
2. Desktop by hand (the file store stands in for the Keystore): connect a test account, quit,
   `chmod 000 "…/Campfire/preferences/sync-credentials.json"`, start. **Before:** Settings offers Connect Dropbox as if
   never connected. **After:** it shows the new storage sentence under Connect, no run starts, and the file is
   unchanged (`ls -l` size and mtime). `chmod 600` it and restart: connected again, and a run starts.
3. Android (optional, if a device reproduces Keystore hiccups): no deterministic way; the unit tests cover the logic.

## Docs

- `data/source/local/implementation/CLAUDE.md:74-78` (the `SecretStore` bullet): "that launch starts disconnected and
  the next one reads the same file again" → "that launch reports the connection as not readable (`ConnectionFailed`
  with a storage reason) rather than as disconnected, nothing is written over the file, and the next launch reads it
  again; `IosSecretStore` does the same for any Keychain status other than not-found."
- `data/source/remote/implementation/CLAUDE.md` — where `SyncCredentialsStore` is described (`:53` area): add "A read
  the storage refuses (`LibraryStorageException`) is thrown and not cached, like a cancelled one, so a document without
  tokens is never written over tokens that could not be read for a moment."
- `data/repository/implementation/CLAUDE.md` (HEAD `:174-178`, "`restore` never throws for a service that refuses the
  stored credentials"): add "nor for credentials the device cannot read right now, which it reports as a connection
  failure with a storage reason and leaves alone."

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceImpl.kt`
- `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/secret/SecretStore.ios.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SyncStateLocalSource.kt` (KDoc)
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncCredentialsStore.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`
- Tests: `SyncCredentialsStoreTest.kt`, new `SyncStateLocalSourceTest.kt` (desktopTest), `SyncRepositoryImplTest.kt`,
  `FakeSyncProvider.kt`
- `data/source/local/implementation/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`,
  `data/repository/implementation/CLAUDE.md`

## Depends on

- Land **after 18** (same repository file and fake).
- **Cross-lane:** `strings.xml` ×2 are also edited by lane D's plan 43 (removes `settings_sync_syncing`) — trivial
  merge. `SyncSettings.kt` is not known to be touched by another plan. Lane C's plan 28 (iOS forget-credentials
  failure) touches iOS credential handling; check `SecretStore.ios.kt` for overlap when both land.
- `data/source/remote/implementation/CLAUDE.md` and `FakeSyncProvider.kt` had uncommitted work at review time.
