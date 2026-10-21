# Keep a copy the account's list may name, notice copies that are gone, and replace such a share instead of handing out its dead link

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — the whole edit-and-decide (edit, re-read, `isKept`) is one `NonCancellable` block, since a
`withContext` result can be discarded by a cancellation on return; leftovers stay Stop sharing everything's (plan 05
dropped); a write the service lands after the re-read is named as an accepted gap the run now catches.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`,
`…/shared/SharedCopySync.kt`, `…/shared/SharedLinksDocument.kt`, `…/shared/SharedLinksFormat.kt`,
`…/shared/SharedLinksMappers.kt`, `…/shared/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`,
`…/commonTest/…/shared/SharedCopySyncTest.kt`, `…/commonTest/…/shared/SharedLinksFormatTest.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/share/ShareState.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt` (KDoc),
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/sharing/CreateShareLinkUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/sharing/CreateShareLinkUseCaseImplTest.kt`,
`domain/implementation/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SharedLinkList.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LinkExportState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LinkPreview.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`,
`presentation/src/commonMain/composeResources/values/strings.xml`, `…/values-hu/strings.xml`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LinkExportStateTest.kt`

(`…/shared/` = `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/`.)
Line numbers are at the reviewed commit `a869c9ff7`; find the code by its snippet, since other plans land around it.

This is the first plan of lane R. Plan 03 (journal-applied lookup) touches the same functions afterwards and assumes
this one landed (plan 05 was dropped in the challenge). Lane U's presentation plans (08–11) merge after lane R and locate
`LinkExportState`, `LinkPreview`, and `SharedLinkList` code by snippet; this plan only adds a branch to each.

## Problem

One defect, three ways in: a share whose copy is gone from the folder keeps its entry, and the app keeps handing its
link out. "A deleted file's link never comes back" (`data/source/remote/implementation/CLAUDE.md`), so such a link is
dead for good, and nothing in the app ever says so or offers a new one.

**(a) `share()` deletes a copy the list may already name.** `SharedLinksRepositoryImpl.kt:126-136`:

```kotlin
} finally {
    // A copy no list names is served by a link nobody was given, and only Stop sharing everything would find it.
    if (isUploaded && !isKept) {
        withContext(NonCancellable) {
            failureOf("delete the unused shared copy $shareId") {
                folder.deleteSharedFile(shareId)
```

`isKept` is set only after `registry.edit(...)` returns `Done` (`:113-116`). The edit can write the entry and still not
return: `SharedLinksRegistry.edit` uploads the document and only then `mirror.save(written.revision, document)`, a
suspending call whose mutex is cancellable; the response can be lost after the service wrote it, and a transport
failure past every retry then throws out of `edit` (a retry that meets its own write gets `Conflict` and the next
attempt finds the entry, but only while attempts remain: on the third attempt it ends `Contended`). The creation runs in
the view model's scope (`ShareLinkController`), so cancellation also happens when that scope ends. In each case the
`finally` deletes the copy while the account's list names it: Settings and every other device show a share whose link
opens nothing.

**(b) A run never notices that every copy is gone.** `SharedCopySync.kt:63-64`:

```kotlin
val pending = journal.load()
if (sharedFiles.isEmpty() && pending.isEmpty()) return true
```

When the user deletes `/shared/` (or each zip) by hand, the listing has no shared file, so the step returns before the
registry is looked at, and no entry is ever marked.

**(c) A missing copy reads as a missing item, and the dead link is handed out.** Where the step does run,
`SharedCopySync.kt:173`:

```kotlin
val remote = sharedFiles[entry.shareId] ?: return if (isListingCurrent) orphan(entry) else Unit
```

marks the entry `orphaned`, the same state as an item gone from the library. Settings then says "No longer in your
library" (`settings_sharing_orphaned`) about a song that is right there. Worse:

- `CreateShareLinkUseCaseImpl.kt:37-39` answers any share of the item, dead or not:
  ```kotlin
  sharedLinksRepository.shares.first()
      .firstOrNull { it.kind == kind && LibraryFiles.isSameLibraryName(it.fileName, fileName) }
      ?.let { return CreateShareResult.Shared(it) }
  ```
- `share()`'s edit (`SharedLinksRepositoryImpl.kt:105-111`) answers the existing entry instead of adding the new one:
  ```kotlin
  val existing = document.entryFor(kind, fileName)
  if (existing == null) { … } else { SharedLinksRegistry.Change(null, existing) }
  ```
- `linkExportStateOf` (`LinkExportState.kt`) shows `LinkExportState.Shared(share)` for any share of the item, whatever
  its state, so the export screen offers **Copy link** and the QR code of the dead link.

Other ways to a dead entry: Stop sharing deletes the copy first and then fails to edit the list (`stopSharing`, by
design), and another device's Stop sharing everything deleting a copy whose entry was not yet written. The item is still in the library in all of them, and nothing lets the user share it again.

## Fix

**1. `share()` keeps a copy whenever the list may name it** (`SharedLinksRepositoryImpl.share`).

- Run the edit, the re-read below, and the decision of `isKept` as one `NonCancellable` block, so that cancellation can
  no longer fall between the service's write and this function learning about it. `isKept` must be set **inside** the
  block: a `withContext` whose caller was cancelled meanwhile may throw `CancellationException` on its way out instead
  of returning its result (prompt cancellation when it resumes on another dispatcher), and a decision taken after it
  would then never be taken, and the `finally` would delete a copy the list names:
  ```kotlin
  isEditStarted = true
  // Not cancellable: once the list may have been written, this share has to learn whether it was, or the finally
  // below would delete a copy the list names.
  val result = withContext(NonCancellable) {
      val edit = try { registry.edit(folder) { document -> … } } catch (…) { … } // a throw goes to the re-read too
      … // decide, re-read where unclear, and set isKept here
  }
  ```
  A creation cancelled meanwhile (its screen's view model gone) therefore runs on until the edit ends, up to the
  edit's three attempts with the transport's retries: accepted, since the outcome decides whether the copy may go.
- For an edit that started and ended without a clear answer (it threw, or `Contended`), read the list once more, best
  effort and under `NonCancellable`, before deciding. Entry with this `shareId` found → `isKept = true` and answer
  `CreateShareResult.Shared(entry.toShare())`; no such entry → delete the copy as now; the read failed too → keep the
  copy (`isKept = true`) and answer the failure. A private helper reads well:
  ```kotlin
  /** The entry of [shareId] after an edit that may have written it; null where the list surely does not name it. Throws where it cannot tell. */
  private suspend fun registeredEntry(folder: SyncFolder, shareId: String): SharedLinksEntry?
  ```
- Delete in `finally` only where the copy is surely unlisted: the edit never started (upload, link, or entry check
  failed), it answered another share (`Done` with another `shareId`), it answered `FromNewerVersion` (the edit writes
  nothing then), or the re-read found no entry.
- An unlisted copy kept because nothing could tell is served by a link nobody was given (the call answered a failure);
  Settings' Stop sharing everything removes such leftovers, as today (it deletes what the listing names too).
- Accepted gap: a write whose answer timed out may still be carried out by the service after the re-read found no
  entry. The copy is then deleted while the list names it; step 3 marks that entry at the next run and step 4 lets the
  user replace it, so nothing stays dead silently. Update the KDoc of `SharedLinksRepository.share` ("Whatever fails after the
  upload deletes the copy again, as far as it can") to say a copy the list may name is kept.

**2. The run's step looks at the list whenever there is one** (`SharedCopySync.synchronize`):

```kotlin
if (sharedFiles.isEmpty() && pending.isEmpty() && registryRevision == null) return true
```

Still free for an account that never shared (no `shared-links.json`, so no revision). An account with a registry costs
`mirror.load()` (memory after the first read) and, only when the listing's revision differs from the mirror's, one
download: `read()` already uses the mirror when `mirrored.revision == registryRevision`. Update the class KDoc ("It
costs nothing when nothing is shared…") and `shared/CLAUDE.md` ("Skipped when the listing has no shared copy and the
journal is empty").

**3. A copy that is gone is recorded as such** (registry format, forward compatible).

- `SharedLinksEntry` gains `val copyMissing: Boolean = false`, and `SharedLinksFormat.ENTRY_KEYS` gains `"copyMissing"`.
  A member, not a new `state` value, on purpose: a build without this change maps an unknown `state` to `OK`
  (`SharedLinksMappers.toShare`'s `else -> ShareState.OK`) and would show the dead link as working, and its runs would
  write `orphaned` over the new value every run while new builds wrote it back. A member it does not know it keeps
  through its edits (`unknownFields`, and `copy(...)` keeps them), and the entry's `state` stays `orphaned` for it, which
  is what it would write anyway. The format `VERSION` stays 1.
- In `SharedCopySync.Pass.settleOrThrow`, look the copy up before the library, so that a dead link is recorded
  whether or not its item is still there:
  ```kotlin
  val remote = sharedFiles[entry.shareId]
  // A deleted copy's link never comes back, whatever the library holds.
  if (remote == null && isListingCurrent) return markCopyMissing(entry)
  … // the item lookup and orphan(entry) as now
  if (remote == null) return // a share newer than the listing, which could not have seen its copy
  ```
  with `markCopyMissing` updating only an entry not already `copyMissing && state == orphaned`:
  `changes.update(entry.shareId) { it.copy(state = SharedLinksEntry.STATE_ORPHANED, copyMissing = true) }`. Nothing
  ever clears the flag: a copy restored by hand does not bring the link back, and the replacement below is a new entry.
- `ShareState` gains `COPY_MISSING` ("The shared copy is gone from the cloud folder, deleted by hand or by a stop that
  could not finish, so the link opens nothing. Sharing the item again makes a new link."). Reword `ORPHANED`'s KDoc so
  it no longer covers that case. `SharedLinksEntry.toShare()` maps `copyMissing` to `COPY_MISSING` before looking at
  `state`.

**4. Sharing an item whose share is dead replaces that share.**

- `CreateShareLinkUseCaseImpl`: answer an existing share only where it works:
  `?.takeIf { it.state != ShareState.COPY_MISSING }?.let { return CreateShareResult.Shared(it) }`. Update
  `domain/implementation/CLAUDE.md`'s `CreateShareLinkUseCaseImpl` line.
- `SharedLinksRepositoryImpl.share`'s edit: an entry with `copyMissing` is not an existing share; it is removed as the
  new entry is added, in the same write:
  ```kotlin
  val existing = document.entryFor(kind, fileName)
  if (existing == null || existing.copyMissing) {
      SharedLinksRegistry.Change(document.copy(links = document.links.filterNot { it.shareId == existing?.shareId } + entry), entry)
  } else {
      SharedLinksRegistry.Change(null, existing)
  }
  ```
  The dead entry's copy is already gone, so nothing is deleted. Another device's journal `Stop` of the old id finds
  nothing to remove, which is harmless.
- Update `SharedLinksRepository.share`'s KDoc and `shared/CLAUDE.md`'s Sharing section: a share whose copy is gone is
  replaced, never answered.

**5. The screens say it** (minimal, so that the `when`s stay exhaustive; lane U may restyle later).

- Strings, beside `settings_sharing_orphaned` and `share_missing` (English, then Hungarian; keep the Oxford comma rule
  in any list):
  - `settings_sharing_copy_missing`: "Link no longer works" / "A link már nem működik"
  - `share_copy_missing`: "This link no longer works: its copy was removed from your Dropbox. Create a new link to share
    it again." / "Ez a link már nem működik, mert a példányát eltávolították a Dropboxodból. Hozz létre új linket, ha
    újra meg szeretnéd osztani."
- `SharedLinkList.kt`: `sharedLinkStatus` gains `ShareState.COPY_MISSING -> stringResource(Res.string.settings_sharing_copy_missing)`
  (shown in the error color like the others); the row's menu hides Copy link and Show QR code for it as it does for
  `ORPHANED` (`val isInLibrary = share.state != ShareState.ORPHANED` becomes a check of both; rename the val to say
  "the link is worth handing out"). Stop sharing stays: deleting a copy already gone is success, so it removes the row.
- `LinkExportState.kt`: a new `data object CopyMissing : LinkExportState` ("The item's share lost its copy: its link
  opens nothing, and Create link makes a new one."). In `linkExportStateOf`, a `COPY_MISSING` share counts as no share
  for the creation flow:
  ```kotlin
  val share = shareOf(shares, target)
  val isDead = share?.state == ShareState.COPY_MISSING
  return when {
      share != null && share.shareId in stoppingShareIds -> LinkExportState.Working
      share != null && !isDead -> LinkExportState.Shared(share)
      ownCreation == null -> if (isDead) LinkExportState.CopyMissing else LinkExportState.NotShared
      else -> … // the creation's result, unchanged
  }
  ```
  `linkActionOf(CopyMissing)` is `LinkAction.CREATE`. `LinkPreview.linkMessageOf` gains
  `LinkExportState.CopyMissing -> stringResource(Res.string.share_copy_missing)`. Once the replacement lands in the
  list, `shareOf` finds the new, working share.
- `presentation/…/screens/export/CLAUDE.md`: one clause where it lists the Link format's states.

## Tests

`./gradlew :data:sync:implementation:desktopTest :domain:implementation:desktopTest :presentation:desktopTest`

- `SharedLinksRepositoryImplTest`:
  - `a list written before its answer was lost keeps the copy and answers the share`: `onUploadDocument` lets the fake
    write the document and then throws `SyncNetworkException` (write first, then throw: add a hook after the write, or
    write the document in the hook with `writeDocumentElsewhere` and throw). Assert the copy is still in
    `provider.sharedFiles`, the registry names it, and the result is `Shared`.
  - The existing `a network failure between the upload and the list deletes the copy` must still pass (nothing was
    written, the re-read finds no entry).
  - `a share whose copy is gone is replaced by a new one`: registry entry with `copyMissing = true`; `share()` answers
    a new `shareId`, the registry holds only the new entry for that item.
- `SharedCopySyncTest`:
  - `copies deleted by hand mark their shares as dead`: a shared device, then remove every entry of
    `cloud.sharedFiles`, sync; the entry has `copyMissing` and state `orphaned`. A second sync writes nothing
    (`documentDownloadCount` unchanged where the mirror is current).
  - `nothing shared and nothing journaled asks for nothing` keeps passing (no registry, so no revision).
  - `a copy missing from a listing older than the registry is left alone` (the `isListingCurrent == false` path).
- `SharedLinksFormatTest`: `copyMissing` round-trips, an entry without it decodes as `false`, and an unknown member
  beside it survives a decode and encode.
- `CreateShareLinkUseCaseImplTest`: `an item whose share lost its copy is shared anew` (the stub's share has
  `ShareState.COPY_MISSING`; the use case builds a copy and calls `share`).
- `LinkExportStateTest`: a `COPY_MISSING` share gives `CopyMissing` with action `CREATE`; with a creation under way,
  `Working`; with the creation's `Shared` result, `Shared` of the new share.

## Manual check

On one device with a real Dropbox: share a song, delete `Apps/Campfire Sync/shared/<id>.zip` in Dropbox's web UI, sync
once, and confirm Settings → Sharing says "Link no longer works", the export screen offers Create link, and the new
link opens the song in a private browser window. (Needs an account; a mistake here hands out dead links.)
