# Bound retained song texts during long browsing sessions

**Kind:** performance · **Severity:** medium (P2) · **Platforms:** all, especially phones/web
**Reviewed at:** `b8cc0bc2`
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongContentRepositoryImpl.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`.

## Problem

The application-singleton content repository stores every opened text in a plain map (27, 44–60). There is no
entry limit, byte budget, eviction, or navigation hook. Entries leave only on invalidation. Browsing an unchanged
library without a rescan therefore accumulates its complete text for the lifetime of the process.

`pruneSongTexts` in the ViewModel (1065–1078) only prunes the ViewModel's map. Its comment about preventing texts
from piling up does not apply to the repository. Moreover, it keeps every file named by an active setlist
destination, not just the current/adjacent pages: paging through a large setlist accumulates texts there too.
The 8 MiB per-file input ceiling does not bound this aggregate. This is separate from issue 06's Skia layout
allocations; limiting rendered lines will not release these source strings.

## Evidence / reproduction

A temporary repository test opened 1,000 distinct files, changed their backing contents without invalidation,
then revisited all 1,000. Every read returned its original cached text. Source inspection confirms there is no
upper bound. This proves retention, not a measured phone out-of-memory threshold. For scale, 1,000 visited
100 KiB texts retain about 100 MiB of text payload before platform string overhead and UI models.

## Fix

1. Give the repository an LRU with both a small entry limit and an aggregate text-size budget. Count retained
   characters conservatively; do not encode the entire text merely to decide its cache cost. An individual text
   above the budget should be returned without retention.
2. Evict under the existing mutex, preserving generation checks. Eviction is a memory decision and must not emit
   a “file changed” notification.
3. Keep only current and prefetched pager texts in the ViewModel, plus active editor/draft baselines. Prune when
   a page settles as well as after navigation transitions; protect pages still participating in a transition.

## Verification

- Count backing reads to prove recently used entries hit and older entries are evicted under either limit.
- Verify oversized entries are not cached and invalidation still prevents stale insertion.
- Page through a generated setlist, return to Songs, force GC in a profiling build, and compare retained text
   sizes. The retained source-text total should plateau rather than track every visited song.
- Verify unsaved-editor detection and adjacent-page prefetch remain correct.

## Conflicts

Shares the content repository with 08/11. Does not replace issue 06's render budget.
