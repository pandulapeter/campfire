# Enumerate setlist files from storage when exporting the library

**Kind:** bug · **Severity:** high (P1: incomplete backup reported as complete) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt`, setlist repository/source API and implementation, export tests.

## Problem

The library export enumerates song file sizes from disk, but gets setlist names from `loadSetlistsIfNeeded()`
(51–52, 73–75). That is the parsed, cached list, not the directory. A setlist downloaded since the last refresh,
or placed in the folder externally, is omitted. More persistently, `SetlistLocalSourceImpl.loadSetlists`
(39–63) skips oversized, unreadable, and malformed documents and returns the rest successfully. Such files never
reach export's loop, so they are absent from both the archive and `skippedFileNames`.

A user backing up before migration or troubleshooting can receive an apparently complete archive that lacks
setlists still present on disk. A malformed JSON file may contain recoverable work and is deliberately left
on disk by the scanner; the backup should preserve it or explicitly name its omission.

## Evidence / reproduction

A temporary domain test provided a repository with an empty scanned setlist list and an independently readable
`unscanned.setlist.json` document. With one song available, export succeeded with only that song in the archive
and an empty skipped list. The real local-source scan supplies exactly this shape for per-file decode failures.

## Fix

1. Expose a fresh setlist name/size listing through the source and repository, analogous to songs.
2. Export raw documents from that listing without requiring successful model decoding. Preserve readable invalid
   JSON as backup data; do not parse and reserialize it. Enforce per-file limits before reading.
3. Put every omitted supported setlist name in the existing skipped-file result. A directory-listing failure
   must fail the export, since completeness cannot be established.
4. Keep deterministic ordering and the existing `setlists/` archive layout.

## Verification

- Export a valid file added after the cache was populated; it must be included.
- Export readable malformed JSON; preserve its document or explicitly report it under a documented policy.
- An oversized or unreadable file must appear in `skippedFileNames`; an inaccessible directory must fail.
- Confirm the UI shows the existing partial-export warning and round-trip valid setlists with transpositions.

## Conflicts

Coordinate the exporter changes with 11 and 13. Rescanning before export alone does not fix decode omissions.
