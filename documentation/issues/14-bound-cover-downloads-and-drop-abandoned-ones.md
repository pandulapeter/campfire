# Bound concurrent cover downloads and skip the ones nobody waits for any more

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (worst on android, ios, desktop)
**Files:** data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImpl.kt, data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImplTest.kt, data/repository/implementation/CLAUDE.md

## Problem
`CoverArtRepositoryImpl.getCoverArt` (CoverArtRepositoryImpl.kt:86-96) starts every download in the repository's
own scope and keeps it running after the caller is cancelled:

```kotlin
downloads.getOrPut(url) { scope.async { load(url) } }
```

That is deliberate (a row scrolled out of view should not waste a download in flight), but nothing limits how many
are started or skips the ones that are still waiting to start. Every song row with a cover calls this through Coil's
`CoverArtFetcher` (presentation/.../ui/components/CoverArt.kt:80-87) as soon as the row is composed. So a library
whose covers are not on the device yet — after a sync from another device, an import, a restore to a new phone
(covers are kept out of the device backup by design), or turning "Cover art" on — gets one HTTP request per row the
list passes over. A fling through a library of a few hundred covered songs launches a few hundred downloads at once.

Cost, concretely:
- They all go through the one Ktor client (`DataRemoteSourceModule.httpClient`). OkHttp runs 5 per host and 64 in
  total, URLSession about 6 per host, and everything else queues in FIFO order. Almost every MusicBrainz cover is on
  `coverartarchive.org` (then redirected to archive.org), so after the fling stops the covers actually on screen wait
  behind every row that went by: at ~300 ms a download and 5 at a time, 300 queued rows are ~18 s of empty thumbnails.
- Ktor's `HttpTimeout` (`HttpClientConfiguration.kt`, 60 s request timeout) counts the time spent queued in the
  engine, so the tail of such a queue times out, becomes `CoverArtDownload.Unreachable`, and is then refused for a
  minute by the failure memory (CoverArtRepositoryImpl.kt:145) — the visible rows stay blank for another minute.
- CIO on the desktop allows 100 connections per route, so the same fling fires ~100 simultaneous requests at the
  Cover Art Archive, which invites 429/503 answers — also `Unreachable`, also a minute of blank covers.
- The same client carries sync; a burst of covers across several image hosts eats into OkHttp's 64-request total
  that Dropbox requests share.

## Fix
In `CoverArtRepositoryImpl`:
1. Track how many callers are waiting per address: replace `downloads: MutableMap<String, Deferred<ByteArray?>>`
   with a small holder `class Download(val deferred: Deferred<ByteArray?>, var waiters: Int)`. In `getCoverArt`,
   under `mutex`, increment `waiters` when joining or creating; `await()` in a `try`, and in `finally` decrement it
   under the mutex (use `withContext(NonCancellable)` for the decrement, since the caller is usually being cancelled).
2. Add `private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)` (kotlinx.coroutines.sync.Semaphore, e.g. 4).
3. In `load(url)`: read the device's copy first as now (no slot needed). Only for the remote part,
   `downloadSlots.withPermit { … }`, and as the first thing inside the permit check, under `mutex`, whether the entry
   still has `waiters > 0`; if not, return null **without** recording a failure (so the next asker starts afresh).
   A download that has started keeps running to the end whatever happens to its waiters — that is the documented
   behaviour worth keeping ("a row scrolled out of view … does not throw away a download another row, or the same row
   a moment later, is waiting for").
4. Keep `downloads.remove(url)` in `finally` so an abandoned entry is not reused.
5. Update the KDoc of the class and the `CoverArtRepositoryImpl` paragraph in `data/repository/implementation/CLAUDE.md`
   (and the Cover art section of the root `CLAUDE.md` if it mentions sharing downloads) to say downloads are bounded
   and one whose every asker has gone before it got a slot is not made.

Tests in `CoverArtRepositoryImplTest` (commonTest, pure logic with the existing fakes):
- With a remote fake that suspends on a gate, start `MAX_CONCURRENT_DOWNLOADS + 3` callers for distinct URLs, assert
  the fake saw exactly `MAX_CONCURRENT_DOWNLOADS` requests before the gate opens.
- Cancel the callers of the queued URLs, open the gate, assert the queued URLs never reached the remote and no
  failure was recorded (a later `getCoverArt` of one of them does reach the remote).
- A download already in flight whose only caller is cancelled still completes and is saved (existing sharing test
  semantics preserved).

## Verification
`./gradlew :data:repository:implementation:desktopTest`.
Manual (Android emulator, network throttled in the emulator settings): import a library of ~200 songs with
`{meta: cover https://coverartarchive.org/release-group/<id>/front-250}` lines (or clear `covers/`), fling the Songs
list to the bottom and stop: the covers on screen should appear within a couple of seconds rather than after the
rows that went by, and none should stay blank for a minute.

## Conflicts
Touches only the cover repository and its test; a lane changing `CoverArtRepositoryImpl` (pruning, failure memory)
would conflict. The data/repository CLAUDE.md is shared with sync lanes (only the `CoverArtRepositoryImpl` bullet
changes).
