# Make the library file lock refuse being taken by a coroutine that already holds it

**Kind:** robustness / testing · **Severity:** low · **Platforms:** all · **Priority:** P3 · **Effort:** S
**Files:** `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLock.kt`,
new `data/source/local/api/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLockTest.kt`,
`data/sync/implementation/CLAUDE.md` (the `LibraryFileLock` bullet)

Reshaped from "assert the lock order in tests": see Problem for why the full tier assertion is not worth building.

**Challenged:** amended — decision D3 taken (land it); the mark names its own lock instance, so two locks (each device
of `TwoDeviceSyncTest` has one) never refuse each other and a nested other lock does not hide a reentry; a test for that.

## Problem

21 `Mutex()`s in `data/`, and their order is prose only: `LibraryFileLock`'s KDoc ("It is not reentrant, so nothing
that holds it may call anything that takes it - the repositories take their own locks first and this one inside
them, and the engine takes no other.") and the four tiers of `data/sync/implementation/CLAUDE.md`.

Checked at HEAD, the order is consistent: `SetlistRepositoryImpl.writing` and `SongRepositoryImpl.naming` take their own
mutex then `LibraryFileLock`; inside it, `SetlistRepositoryImpl.latest` takes the base read lock
(`loadDataIfNeeded`), and `SyncEngine`/`ConflictResolver` call only `LibraryFileLocalSource` and
`onLocalFileChanged` (which reaches `SyncLibraryRefresher.changedFilesMutex`, innermost). No lock is taken in both
orders, and git history has no deadlock fix. The one mistake the prose warns of — code holding `LibraryFileLock` calling
a repository write or another engine step that takes it — deadlocks every time it runs (kotlinx `Mutex` is not
reentrant), so a test reaching it hangs until the 20-minute UI test bound or forever under `runBlocking`, with no
message naming the lock. A general tier assertion (a context element carrying the held tier, checked at every one of
the 21 locks) would mostly re-detect that same reentry, at the price of wrapping every lock in the data layer.

## Fix

`LibraryFileLock.withLock` marks the coroutine context while it holds the lock and refuses to be entered again from
it:

```kotlin
private val heldKey = object : CoroutineContext.Key<Held> {}

private inner class Held : AbstractCoroutineContextElement(heldKey)

suspend fun <T> withLock(block: suspend () -> T): T {
    check(currentCoroutineContext()[heldKey] == null) { "LibraryFileLock is not reentrant: this coroutine already holds it." }
    return mutex.withLock { withContext(Held()) { block() } }
}
```

(shaped to the code-style rules). The key is the instance's own, not a shared object: tests build several locks (one
per device in `TwoDeviceSyncTest`, one per repository test), and a shared mark would make holding one refuse another, or
a nested other lock replace the mark and hide a reentry of the first. `withContext` with an element
and no dispatcher does not dispatch, so the cost is one context allocation per lock. Behaviour changes only on a path
that would otherwise deadlock: it now fails at once with a message. Children started inside the block inherit the mark
(an `async` inside the lock that took it again would deadlock too); a coroutine launched into another scope does not,
which is right, since it waits rather than deadlocks unless the holder awaits it — a case this does not catch, as the
KDoc should say.

Update `LibraryFileLock`'s KDoc and the sync `CLAUDE.md` bullet ("Not reentrant: …") to say the lock refuses reentry.
Do not add tier checks to the other locks. Noted as a future option only, not planned: one library-owner actor
serializing every library write (repositories and sync) instead of the locks.

Decision D3 (owner): land it.

## Tests

`LibraryFileLockTest`, in a new `commonTest` folder of `:data:source:local:api` (it has none yet; the convention plugin
gives it `kotlin("test")` and coroutines-test): taking the lock inside its own block throws `IllegalStateException`
instead of hanging (`runTest` with a short timeout); an `async` inside the block taking it throws too; two sibling coroutines taking it one after the other both complete; holding one `LibraryFileLock` and taking another
inside it completes, and taking the first again inside the second still throws; the lock is free again after the block
throws. The existing repository and `TwoDeviceSyncTest` suites must pass unchanged, which shows no current path
re-enters.
