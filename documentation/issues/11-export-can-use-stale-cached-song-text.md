# Read fresh song content for exports

**Kind:** bug · **Severity:** medium (P2) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongContentRepositoryImpl.kt`, song-content API/use case, export use cases and tests.

## Problem

`loadSongContent(fileName, shouldCache = false)` still returns a cache hit unconditionally (45–46). The flag only
prevents insertion after a storage read (57–60). Library and setlist exports use this call, so enumerating files
freshly does not guarantee exporting their current contents.

Open a song, replace its file outside the repository, and export before the next rescan/refresh. The export
silently uses the old text. External edits to the owned plain-text folder and the interval between a sync write
and its live refresh both create this state. Because the cache is currently unbounded, the affected file need
not still be on screen. Export is not required to be an atomic snapshot of the entire library, but it should
not deliberately prefer an older in-memory version to a completed disk change.

## Evidence / reproduction

A temporary test exercised the real content repository over a mutable source: cache `old`, replace the backing
file with `new`, then request `shouldCache = false`. The returned text was `old`. No failure accompanies it.
The library-export caller at `ExportLibraryUseCaseImpl.kt:62` uses that exact read mode.

## Fix

Make cache policy explicit: distinguish ordinary cached reads from fresh reads that also avoid retention.
Use the latter for all exports. Either clarify/change the current flag and audit its callers, or introduce a
separate read policy so existing cache-only intentions remain clear. Keep the storage read outside the mutex,
and preserve cancellation and unreadable-file behavior. Invalidating the whole library before an export is
unnecessary work and would trigger unrelated viewer reloads.

## Verification

- Cache a file, modify storage, and export: the archive must contain the new text.
- Delete a cached file before export: report/handle it as missing instead of resurrecting its cached text.
- Verify fresh reads neither fill the browsing cache nor evict unrelated entries.
- Cover library, setlist, and individual song exports as applicable to their read contracts.

## Conflicts

Same cache as 08/09; implement freshness independently of LRU eviction. Coordinate with 10's export listing work.
