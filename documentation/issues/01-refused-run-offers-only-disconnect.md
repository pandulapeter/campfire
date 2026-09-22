# 01 · When Dropbox refuses a sync run, Settings says "Connect the account again" but only offers Disconnect, and disconnecting brings deleted songs back

**Severity:** wrong behaviour (all platforms. Likely for anyone whose Dropbox grant is revoked while the app is running, from Dropbox's "Connected apps" page, a password reset that revokes sessions, or an expired grant. The message then leads the user to Disconnect, which throws away the index: the next connection's first run downloads every file deleted on this device since its last run, uploads every file deleted elsewhere, and makes conflict copies of everything edited on both sides) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl.kt`, the run's failure path)

## Symptom
1. Connect Dropbox and sync. Delete a song.
2. Revoke Campfire on dropbox.com (Settings, Connected apps). Keep the app running.
3. Tap **Sync now**. The status reads "The connection was refused. Connect the account again." The only rows are
   Sync now and Disconnect. There is no Connect.
4. Disconnect and connect the same account again. The first run brings the deleted song back, and anything deleted on
   another device in the meantime is uploaded again.

If the app is restarted after step 2 instead, the refusal is found at start up, the section shows **Connect Dropbox**
with "Dropbox refused the connection.", and reconnecting keeps the index. The run path and the start up path end up
in different states for the same revocation.

## Cause
A refused run only records a failed outcome and stays `Connected`
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:399-402`):

```kotlin
} catch (exception: Exception) {
    println("The sync run failed: ${exception.message}")
    withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
    updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason())) }
```

`ConnectedSyncSettings` (`presentation/.../settings/SyncSettings.kt:186-245`) has no Connect row, so the only way
to act on `settings_sync_failed_authorization` is Disconnect, and `disconnect()` deletes the index
(`SyncRepositoryImpl.kt:283-286`, `syncStateLocalSource.saveSyncIndex(null)`).

A `SyncAuthorizationException` out of a run means the credentials cannot be renewed. The provider already renews the
access token on a 401 and treats 429 and 5xx from the token endpoint as network failures
(`DropboxSyncProvider.kt:432-474`). The refusals left are a refused refresh token, a second 401, or no credentials.
Start up handles exactly that case by going to `ConnectionFailed` (`refreshAccount`, `:470-475`, and `:181`). The run
does the same when it finds no connected provider (`:324`). Its **Connect** goes through
`completePendingAuthorization`, where `loadIndex().adoptedBy(account)` keeps the index for the same account
(`:583-586`).

## Fix
`SyncRepositoryImpl.kt`, `runSynchronization`, replace the `updateConnected` line of the `catch (exception: Exception)`
branch (`:402`) with:

```kotlin
                updateConnected {
                    if (exception is SyncAuthorizationException) {
                        // Refused after a renewal was tried, so only a new authorization can answer it, and that
                        // is what ConnectionFailed offers. Kept Connected, the one way on would be Disconnect, which
                        // deletes the index and brings back everything deleted since the last run once the same
                        // account is connected again. Connecting keeps it (completePendingAuthorization).
                        SyncState.ConnectionFailed(it.account.providerId, SyncFailureReason.AUTHORIZATION)
                    } else {
                        it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason()))
                    }
                }
```

`SyncAuthorizationException` is already imported (used by `toFailureReason`). Leave the rest alone:
- `finishRunCutShort` still runs first, so the index is saved without the running marker and the lists are rescanned
  where files moved.
- The `finally` only touches a `Connected` state, so it leaves `ConnectionFailed` as it is.
- The notifier stops, because the state no longer carries progress.

`settings_sync_failed_authorization` stays. The `when` in `SyncSettings.statusText` has to cover
`SyncFailureReason.AUTHORIZATION`, and no path produces `Failure(AUTHORIZATION)` any more. The text is harmless there,
so it is not worth a string change in both languages.

## Tests
`SyncRepositoryImplTest.kt`, next to `a run asked for after the credentials went reports the connection as failed`:

```kotlin
@Test
fun `a run the service refuses reports the connection as failed and keeps the index`() = runTest {
    val stateLocalSource = FakeSyncStateLocalSource()
    val repository = repository(
        provider = FakeSyncProvider(
            files = mapOf(song(1) to "One".encodeToByteArray()),
            onDownload = { throw SyncAuthorizationException("Refused") },
            account = ACCOUNT,
        ),
        stateLocalSource = stateLocalSource,
    )

    repository.restore()
    repository.synchronize(SyncDeletionPolicy.ASK)
    val state = repository.syncState.first { it is SyncState.ConnectionFailed }

    assertEquals(SyncFailureReason.AUTHORIZATION, assertIs<SyncState.ConnectionFailed>(state).reason)
    assertNotNull(stateLocalSource.index)
    assertFalse("\"isRunInProgress\": true" in stateLocalSource.index.orEmpty())
}
```

Import `com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException`. Run
`./gradlew :data:repository:implementation:desktopTest`.

## Verify
1. Dropbox-configured desktop build: connect, sync, delete a song, sync. Revoke the app on dropbox.com, then tap
   **Sync now** without restarting. The section changes to **Connect Dropbox** with "Dropbox refused the connection."
2. Connect the same account again and sync. The deleted song stays deleted.
3. Sync with the network off. That is still `Failure(NETWORK)` with the account shown.
4. Compile `:data:repository:implementation` for desktop and wasmJs.

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet, after "…turns it into `ConnectionFailed` rather than
doing nothing, so an account whose credentials are gone never stays on screen with a button that cannot work.": add
"A run the service refuses (`SyncAuthorizationException`, which the provider only throws once a renewal has been
refused) ends the same way, rather than as a failed outcome under the account: its only way on would be Disconnect,
which deletes the index, while Connect keeps the index for the same account."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 03 and 08 edit other parts of `runSynchronization` (the `loadIndex` call at `:336` and the
`DeletionsNeedConfirmation` branch at `:358-367`). Run them one after another.
