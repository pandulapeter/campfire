# Warn in the Disconnect confirmation while deletions of shared items have not stopped their links yet

**Kind:** bug · **Severity:** low · **Platforms:** all
**Challenged:** amended — `StopItem` / `StopAll` are noted for *every* deletion while an account is stored, shared or
not, so the warning shows after any deletion the list has not been told of yet; the sentence is hedged accordingly
(it cannot claim a link exists), and `CampfireViewModel` does build the controller by hand, so it gets the use case.
**Files:**
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/CLAUDE.md`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManager.kt` (KDoc of `forgetSharedLinks` only),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/sharing/GetPendingShareStopsUseCase.kt` (new),
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/sharing/GetPendingShareStopsUseCaseImpl.kt` (new),
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/RepositoryStubs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/share/ShareLinkController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/share/ShareLinkControllerTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (builds the controller by hand, `:387`: add the use case to its constructor and pass it on),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CampfireDialogs.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`tools/screenshots` (only if it constructs `ShareLinkController` or stubs `SharedLinksRepository`; grep)

## Problem

Deleting a shared song, a shared setlist, or the whole library promises the link stops:

```xml
<string name="songs_delete_song_confirmation_shared">Delete \"%1$s\"? … and its shared link stops working.</string>
<string name="setlists_delete_setlist_confirmation_shared">Delete the \"%1$s\" setlist? … Its shared link stops working.</string>
<string name="settings_library_delete_confirmation_shared">Every shared link stops working.</string>
```

The deletion only notes a stop in the journal and asks the applier to apply it later, without waiting
(`SharedLinksRepositoryImpl.kt:214-222`, `note` at `:246-256`):

```kotlin
override suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String) = note("note the deletion of $fileName") {
    sharedNow().entryFor(kind, fileName)?.let { listOf(SharedLinksJournal.Entry.Stop(it.shareId)) }
        ?: if (isAccountStored()) listOf(SharedLinksJournal.Entry.StopItem(kind = kind.id, fileName = fileName)) else emptyList()
}
…
if (isNoted) connectedFolder()?.let(journalApplier::applySoon)
```

The copy is deleted only when `SharedLinksJournalApplier.apply` or a run's `SharedCopySync` reaches Dropbox. Offline (or
when `applySoon` fails and no run has happened since), the stop stays in `preferences/shared-links-journal.json`. Disconnecting then wipes it
(`SyncConnectionManager.kt:316-321`, `:375-378`):

```kotlin
runner.withRunLock {
    indexStore.clear()
    forgetSharedLinks()
    …
private suspend fun forgetSharedLinks() {
    quietly("clear the shared links mirror") { sharedLinksMirror.clear() }
    quietly("clear the shared links journal") { sharedLinksJournal.clear() }
}
```

From then on, nothing on this device knows about the stop. The copy stays in `Apps/Campfire Sync/…` and its public link
keeps serving the deleted song. Reconnecting the same account brings the share back via the mirror as `orphaned`. It
still works, and the user has to find it in Settings → Sharing and stop it by hand.

The Disconnect confirmation does not mention this. Worse, `shares` is the mirror *with the journal applied*
(`SharedLinksRepositoryImpl.kt:58`), so a share with a pending stop is already gone from it. When that was the only
share, `CampfireDialogs.kt:331-336` drops even the generic line:

```kotlin
text = textResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName) +
    if (viewModel.shareLinkController.shares.collectAsStateWithLifecycle().value.isEmpty()) "" else
        " " + stringResource(Res.string.settings_sync_disconnect_shared_links)
```

`forgetStoredConnection` (a previous installation's credentials; the journal is never backed up) and connecting
*another* account (`SyncConnectionManager.kt:446-451`, whose tokens cannot reach the old folder) also clear the
journal. Neither can do better, and this plan leaves both alone.

## Fix

Options considered:

1. **Apply the journal in `disconnect()` before the credentials go** (bounded by a timeout, before
   `scheduler.stopForConnectionChange()` so a running step is waited for rather than raced). This does not help in the
   main case, offline, where it fails like `applySoon` did. It also keeps the account on screen as Connected for up to
   the timeout after the dialog closed, which breaks "Disconnected is shown at once" (`connection/CLAUDE.md`).
2. **Warn in the Disconnect confirmation while the journal holds a stop** (recommended). It covers every case,
   offline included, and the user can cancel, tap Sync now (which applies the journal first), and then disconnect. The
   change is small and local, and the user keeps the decision.
3. **Keep pending stops across a disconnect, tagged with the account id, and apply them when the same account
   connects again.** This adds account bookkeeping to the journal, a new reset path, and a downgrade concern. It still
   leaves the link working until that reconnect, which may never happen. Not worth it at this severity.

Recommended: option 2 only.

- `SharedLinksRepository`: add

  ```kotlin
  /**
   * Whether this device has deleted a shared item whose link the account's list has not stopped yet (offline, or
   * not applied since): forgotten with the account, so disconnecting now would leave that link working.
   */
  val hasPendingStops: Flow<Boolean>
  ```

- `SharedLinksRepositoryImpl`:

  ```kotlin
  override val hasPendingStops = journal.entries
      .map { entries -> entries.any { it !is SharedLinksJournal.Entry.Rename } }
      .distinctUntilChanged()
  ```

  `Stop`, `StopItem`, and `StopAll` all count. This over-warns, and not rarely: `stopSharingDeleted` notes a `StopItem`
  for **every** song or setlist deleted while an account is stored that the mirror does not name (another device may
  have shared it since), and `stopSharingEverything` a `StopAll` for every library deletion, so deleting an item that
  was never shared, offline or before `applySoon` finishes, shows the warning too. A `Stop` from `removeStopped`,
  whose copy is already deleted, also counts. The device cannot tell these apart from a live link without the list,
  so the sentence must not claim that a link exists (below); a sync clears all of them. Renames do not count: an unapplied rename only
  leaves the share orphaned, which the KDoc of `forgetSharedLinks` already accepts.
- Domain: `GetPendingShareStopsUseCase` (`operator fun invoke(): Flow<Boolean>`), a `@Factory`
  `GetPendingShareStopsUseCaseImpl` delegating to the repository, the way `GetSharedLinksUseCase` does. Add the member
  to `RepositoryStubs.kt`'s `SharedLinksRepository` stub.
- `ShareLinkController`: inject the use case and expose
  `val hasPendingStops: StateFlow<Boolean> = getPendingShareStops().asState(scope, false)`, as `shares` is. Update
  `ShareLinkControllerTest`'s construction.
- `CampfireDialogs.kt`, `DialogType.DisconnectSync`: append the new sentence when `hasPendingStops` is true. Show it
  in addition to `settings_sync_disconnect_shared_links`, which keeps its own `shares.isEmpty()` condition.
- Strings: add one key to `values/strings.xml` and `values-hu/strings.xml`, next to
  `settings_sync_disconnect_shared_links`. **These files may hold the user's uncommitted edits: add the new lines
  only, touch no other line, and stage only the added hunk (`git add -p`).**

  ```xml
  <string name="settings_sync_disconnect_pending_stops">Some songs or setlists deleted on this device have not been synced yet. If any of them is shared, its link keeps working unless you sync before disconnecting.</string>
  <string name="settings_sync_disconnect_pending_stops">Az ezen az eszközön törölt dalok vagy listák egy része még nincs szinkronizálva. Ha valamelyiknek van megosztott linkje, az továbbra is működik, hacsak nem szinkronizálsz a leválasztás előtt.</string>
  ```
- Docs: in `shared/CLAUDE.md`, extend the line "Mirror and journal are forgotten with the index (disconnect,
  `forgetStoredConnection`, another account connected)." with: "a stop still in the journal then leaves its link
  working, which the Disconnect confirmation says (`hasPendingStops`)". Add the same note to the KDoc of
  `forgetSharedLinks`.

## Tests

In `SharedLinksRepositoryImplTest` (`./gradlew desktopTest`), one test:
`a deletion the list could not be told yet is a pending stop until it is applied`

- Build the repository with the existing `repository(provider)` helper (connected state, empty mirror). Use a
  `FakeSyncProvider` whose `onDownloadDocument` throws `SyncNetworkException("offline")`, so that the applier
  launched by `applySoon` cannot read the list.
- `repository.followRename(SharedLinkKind.SONG, "a.cho", "b.cho")`, then `advanceUntilIdle()`, then assert
  `hasPendingStops.first()` is `false`. Only a rename is pending, which shows the `!is Rename` filter is right.
- `repository.stopSharingDeleted(SharedLinkKind.SONG, "song.cho")` (noted as a `StopItem`), then `advanceUntilIdle()`,
  then assert `true`.
- Clear `onDownloadDocument`, apply the journal (`SharedLinksJournalApplier(…).apply(provider)` on the same journal, or
  a `share(…)`, which applies it first), then assert `false`.

The test fails without the fix (the member does not exist). It also fails against wrong versions of it: counting
renames fails the first assertion, and reading the mirror's `shares` instead of the journal fails the second, since an
empty mirror names nothing. In `ShareLinkControllerTest`, one assertion that the controller's `hasPendingStops` follows
the use case's flow. The dialog itself is not unit tested.
