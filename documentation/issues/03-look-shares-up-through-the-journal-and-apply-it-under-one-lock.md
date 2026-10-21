# Look shares up through this device's pending journal, scope journaled renames to their share, and apply the journal under one lock

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — `share()` applies a non-empty journal before its edit and fails where entries stay, since 02's
`StopItem`, `StopAll`, and renames without a `shareId` resolve against the list when applied and would otherwise catch
the new share; the applier-versus-run test launches the run's step (run inline it waits on the new lock and the test
hangs); a test for a pending `StopItem`.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`,
`…/shared/SharedLinksJournal.kt`, `…/shared/SharedLinksJournalApplier.kt`, `…/shared/SharedCopyChanges.kt`,
`…/shared/SharedCopySync.kt`, `…/shared/CLAUDE.md`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt` (KDoc of `shares`),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`,
`…/commonTest/…/shared/SharedLinksJournalTest.kt`, `…/commonTest/…/shared/SharedCopySyncTest.kt`

(`…/shared/` = `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/`.)
Line numbers are at the reviewed commit `a869c9ff7`; find the code by its snippet.

Lane R, after plans 01 and 02. Assumes 01's `share()` edit (an entry with `copyMissing` is replaced, not answered) and
02's journal (renames noted for any item while connected, without a known share; `StopItem` and `StopAll` resolved
against the list by one function shared by the applier and the run). `CreateShareLinkUseCaseImpl` needs no change here
once `shares` is journal-applied; 01's `COPY_MISSING` check in it stays.

## Problem

**1. The share lookup ignores this device's pending journal.** `CreateShareLinkUseCaseImpl.kt:36-39`:

```kotlin
sharedLinksRepository.refresh()?.let { return CreateShareResult.Failed(it) }
sharedLinksRepository.shares.first()
    .firstOrNull { it.kind == kind && LibraryFiles.isSameLibraryName(it.fileName, fileName) }
    ?.let { return CreateShareResult.Shared(it) }
```

`shares` is `mirror.shares` (`SharedLinksRepositoryImpl.kt:56`), the list as last read, without the journal. So is the
lookup in `share()`'s edit, `document.entryFor(kind, fileName)` (`:106`), and the export screen's `shareOf(shares, …)`.
With a journaled `Rename(X → Y)` not yet applied (offline, or the applier failed), sharing a new song named X answers
Y's link, and the export screen of X shows it before anything is tapped. With a pending `Stop` of X (deleted offline),
X recreated and shared answers the old link, which the next application then kills.

**2. A journaled rename applies to every entry of that name.** `SharedCopyChanges.applyTo` (`SharedCopyChanges.kt:47-53`):

```kotlin
renames.fold(entry) { renamed, (from, to) ->
    if (LibraryFiles.isSameLibraryName(renamed.fileName, from)) renamed.copy(fileName = to) else renamed
}
```

Once 1 is fixed, `share()` adds a new X while the list still holds the old X waiting for its rename: the rename then
moves both to Y, and new X's link follows the wrong item.

**3. Two appliers remove entries by equality under different locks.** `SharedLinksJournal.remove`
(`SharedLinksJournal.kt:68-73`) removes each applied entry's first equal one. `SharedLinksJournalApplier.apply` holds
the applier's `mutex`; `SharedCopySync.synchronize` loads and removes under the run lock only. Both can load the same
`[Rename(X→Y)]`; the user then renames back and forth, appending `Rename(Y→X)` and `Rename(X→Y)` (equal to the first).
The applier removes the first `X→Y`; the run removes the next equal one, the user's latest rename, which is lost. The
list ends under X while the library has Y: `orphaned`, and the next rename finds nothing.

## Fix

**1. `shares` is the list as the journal will leave it.**

- `SharedLinksJournal` keeps its entries in memory as well: a `MutableStateFlow<List<Entry>?>` filled by the first
  `load()` and replaced by every `append`, `remove`, and `clear`, exposed as `val entries: Flow<List<Entry>>` (loading
  first, as `SharedLinksMirror.shares` does). A journal that cannot be read (`load()` throws) shows as empty there and
  keeps throwing for the callers that write it.
- `SharedLinksMirror` exposes the `Mirrored` flow it already holds (today only `shares`, mapped). In
  `SharedLinksRepositoryImpl`:
  ```kotlin
  override val shares = combine(mirror.mirrored, journal.entries) { mirrored, pending -> pendingApplied(mirrored.document, pending).toShares() }
      .distinctUntilChanged()
  ```
  where `pendingApplied` is the same function `sharedNow()` uses (and, after 02, resolves `StopItem` / `StopAll`
  against that document). `sharedNow()` becomes `pendingApplied(mirror.load().document, journal.load())`.
- `share()` first applies the journal where it holds anything (`journalApplier.apply(folder)`, under the application
  lock below), and answers a failure (mapped as `failureOf` maps the cause; `UNKNOWN` where nothing was thrown) where
  entries are still there afterwards. Looking up through the journal alone is not enough after plan 02: its
  `StopItem`, `StopAll`, and renames without a `shareId` are resolved against the list when they are applied, so a
  share added while they wait is caught by them: a song deleted and recreated offline and then shared has its new link
  stopped by the `StopItem`, a share made after Delete library by the `StopAll`, and a new item under a name renamed away
  has its share moved by the `Rename`. Applied first, they resolve against the list without the new entry. The cost is
  nothing while the journal is empty (checked before taking the lock), and a wait for a run's shared-copy step where it
  is not.
- `share()`'s edit then still looks the item up in the pending-applied list (an entry noted between the application and
  the edit): `pendingApplied(document, journal.load())` read once before the edit, applied to each attempt's
  `document` inside it (the lambda is not suspending). The entry it adds is added to the real `document`, as now.
- Update the `shares` KDoc in `SharedLinksRepository` ("The shares as last read") and `shared/CLAUDE.md`'s mirror bullet.

**2. A rename names its share where it is known.**

- `Entry.Rename` gains `val shareId: String? = null`. `followRename` fills it from `sharedNow().entryFor(kind, fileName)`
  when that names the item; 02's renames of items the mirror does not name keep `null`.
- `SharedCopyChanges.rename(from, to, shareId)`; `applyTo` renames the entry with that `shareId` when it is set, and
  falls back to the name match only when it is `null`.
- Journal format: device-local; a journal written before this change has no `shareId` and reads as `null` (by name, as
  today). An older build reading the new field ignores it (`ignoreUnknownKeys = true`). Nothing to migrate.

**3. One lock for applying the journal.**

- Move the applier's `mutex` into `SharedLinksJournal` as an application lock, separate from its file `mutex` (appends
  must go on while an application runs):
  ```kotlin
  /** Held from loading the entries to apply until they are removed, so that two appliers never take the same ones. */
  suspend fun <T> whileApplying(block: suspend () -> T): T = applicationMutex.withLock { block() }
  ```
- `SharedLinksJournalApplier.apply` wraps its body in it instead of its own `mutex`. `SharedCopySync.synchronize`
  wraps everything from `journal.load()` to the end of `write(...)` in it. Lock order: run lock, then the application
  lock, then the journal's file mutex; the applier never takes the run lock, so nothing waits in a circle. The applier
  may now wait for a run's step; that is fine, it is fire and forget.
- Under one applier at a time, the applied entries are always the journal's first ones, so `remove`'s first-equal
  match is right; say so in its KDoc. Unique ids per entry are then unnecessary (the alternative, rejected as a format
  change for nothing).
- Update `shared/CLAUDE.md` ("Both applying an entry is harmless") and the root `Locks` section of
  `data/sync/implementation/CLAUDE.md` with the new lock.

## Tests

`./gradlew :data:sync:implementation:desktopTest`

- `SharedLinksRepositoryImplTest`:
  - `a pending rename moves the share in the list before the list is told`: share `a.cho`, make the registry fail
    (`onUploadDocument` throws), `followRename(a.cho → b.cho)`; `shares.first()` names `b.cho`, nothing names `a.cho`.
  - `sharing an item named like one renamed while offline makes its own link`: as above, then let the registry work and
    `share(SONG, "a.cho", …)`: a new `shareId`; after the journal is applied, `b.cho` keeps the old share and `a.cho` has
    the new one.
  - `sharing an item deleted and recreated while offline makes a new link`: share, fail the registry,
    `stopSharingDeleted`, `share` again; the answer is a new `shareId`, and after application the old copy and entry are
    gone and the new ones stay.
- `SharedLinksJournalTest`: a `Rename` without `shareId` (the old form) reads as `null`.
- `SharedCopySyncTest` (or a journal applier test beside it): `the run and the applier never apply one entry twice`:
  journal `[X→Y]`; start the applier with its registry read suspended (`onDownloadDocument` awaiting a
  `CompletableDeferred`), append `Y→X` and `X→Y`, **launch** the step (it waits for the application lock; called inline
  the test body never gets to the release and hangs), `runCurrent()`, release the applier, join the step; the journal
  ends empty and the entry is named `Y`.
- `SharedLinksRepositoryImplTest`: `a share of an item deleted and recreated while a resolved stop waits keeps its new
  link`: a `StopItem` of `a.cho` journaled (applier failing), the registry working again, `share(SONG, "a.cho", …)`;
  the journal is applied first, the answer is a new `shareId`, and after the next application its copy and entry stay.
