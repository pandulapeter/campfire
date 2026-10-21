# Keep a slow read of the account's list from writing an older list over a fresher mirror

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — an edit's own save is kept from refilling a mirror cleared since the edit began (plan 01 now
carries `share()`'s edit through a disconnect); the first test needs the fake to snapshot the document before its hook,
since as written it passes without the fix (checked); two concurrent reads named as an accepted gap.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksMirror.kt`,
`…/shared/SharedLinksRegistry.kt`, `…/shared/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`
(`downloadDocument` only; lane K's plan 06 lists the same file, for other members)

(`…/shared/` = `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/`.)
Line numbers are at the reviewed commit `a869c9ff7`; find the code by its snippet.

Lane R, after plans 01–03, which do not change `SharedLinksRegistry.read` or the mirror's `save` / `clear` (03 only
exposes the mirror's state as a flow). Independent of their behaviour.

## Problem

`SharedLinksRegistry.read` (`SharedLinksRegistry.kt:60-73`) saves whatever it downloaded into the mirror, however long
the download took:

```kotlin
val remote = folder.downloadDocument(SharedLinksFormat.NAME)
    ?: return Read.Readable(revision = null, document = SharedLinksDocument()).also { mirror.save(null, it.document) }
…
mirror.save(remote.revision, document)
```

and `SharedLinksMirror.save` (`SharedLinksMirror.kt:66-68`) replaces the state unconditionally; its mutex only keeps the
first disk load from replacing a later save. Reads run concurrently: Settings' `refresh` on every visit, the export
screen's, `CreateShareLinkUseCaseImpl`'s, a run's `SharedCopySync`, and the reads inside every edit. A read of rev 1
that answers after `share()` wrote rev 2 (and saved it) puts rev 1 back: the new share vanishes from Settings until the
next read, `sharedNow()` no longer names it (so a rename made meanwhile is not journaled, before plan 02), and a run
whose listing names rev 2 reads again (harmless). Worse, a read for an account that answers after `disconnect`
cleared the mirror writes that account's list back into it, on disk too; the next account connected on this device
(`completePendingAuthorization` clears the mirror only when an index of another account is found, and disconnect
deleted the index) shows the previous account's shares until its first read.

Dropbox revisions are opaque strings (documented as such), so "keep the newer revision" cannot be decided by comparing
them.

## Fix

Give the mirror a generation, taken before a request and checked at the save:

```kotlin
/** Bumped by every save and clear, so that a read can tell whether the mirror changed while it was under way. */
private var generation = 0L  // read and written under mutex

suspend fun generation() = mutex.withLock { generation }

/** Saves what a read found, unless anything was saved or cleared since [since], which is then fresher. */
suspend fun saveRead(since: Long, revision: String?, document: SharedLinksDocument) = mutex.withLock {
    if (generation != since) return@withLock
    saveLocked(revision, document)  // today's save body, bumping generation
}
```

- `save` (used by `edit` after its own write) and `clear` bump the generation too. `save` keeps writing over other
  saves (a successful write is the newest list there is), but never over a clear made since its edit began: keep a
  second counter bumped by `clear` only (`clears`), have `edit` take it before its first read, and save its write with
  `saveWritten(sinceClear, revision, document)`, which does nothing where `clears` moved. Without it the case this plan
  names still happens through an edit: plan 01 runs `share()`'s edit under `NonCancellable`, so a share in flight
  through a disconnect (or a reset for another account) writes the old account's list back into the mirror and its
  file.
- Accepted gap: of two reads started under one generation, the one answering second is dropped even where it read the
  newer list; the mirror is then one read behind until the next, which every caller tolerates (a run's step re-reads
  where the listing names another revision).
- `SharedLinksRegistry.read` takes `val since = mirror.generation()` before `downloadDocument` and saves with
  `saveRead(since, …)` on both paths (no document, and decoded). It returns what it read either way: callers act on
  what they read, only the mirror is protected.
- Make the counter safe on every platform: it is only touched under `mutex`, which is enough (no atomics needed).
- `shared/CLAUDE.md`, mirror bullet: "Replaced on every read and successful edit" → "replaced by a successful edit, and
  by a read unless the mirror changed while it was under way".

## Tests

`./gradlew :data:sync:implementation:desktopTest`

`SharedLinksRepositoryImplTest`:

- `FakeSyncProvider.downloadDocument` reads `documents[name]` **after** `onDownloadDocument`, so a gated read answers
  with the list as it is when released, the share included, and the first test below passes without the fix (checked
  at `f7ff3d945`). Take the snapshot before the hook (every current use of the hook only throws, so nothing else
  changes), and say so in its KDoc.
- `a read that answers after a share keeps the share in the list`: gate the first `downloadDocument` on a
  `CompletableDeferred` (in `onDownloadDocument`, only for the first call), launch `refresh()`, `runCurrent()`, then
  `share(SONG, "a.cho", …)` (its own reads pass), then complete the gate; `shares.first()` still names `a.cho`.
- `a read that answers after a disconnect leaves the mirror empty`: same gate, launch `refresh()`, disconnect (or call
  the mirror's `clear()` as `forgetSharedLinks` does), complete the gate; the mirror holds no shares and its file is
  gone (`FakeSharedLinksLocalSource`).
- `an edit that answers after a disconnect leaves the mirror empty`: gate `onUploadDocument` of a `share`, clear the
  mirror, release; the mirror stays empty.
