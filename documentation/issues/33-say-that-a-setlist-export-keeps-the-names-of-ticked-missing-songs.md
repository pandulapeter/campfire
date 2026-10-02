# Correct the docs about the manifest of a partially ticked setlist export

**Challenged:** sound

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/CLAUDE.md`, `CLAUDE.md` (Printing section)

## Problem
`domain/implementation/CLAUDE.md` (the `ExportLibraryUseCaseImpl / ExportSongsUseCaseImpl / ExportSetlistUseCaseImpl`
bullet) says the manifest names only the ticked songs "so that an import never finds a setlist naming a song the archive
left out". Root `CLAUDE.md` says "so that an import finds every song it names". `ExportSetlistUseCaseImpl` deliberately
skips a ticked song whose file is missing (`loadSongContent(...)?.let { put(...) }`, KDoc: "the setlist still names it,
and importing it somewhere the song does exist will find it again") while `SetlistLocalSourceImpl.loadSetlistDocument`
filters by the ticked set only, so the manifest still names it. The code is as intended; the sentences overstate it.

## Fix
Edit both sentences to: the manifest names only the ticked songs, so that the archive never names a song the user left
out; a ticked song whose file is missing from the library is still named, and an import shows it as a missing song, as
the setlist itself did. No code change.

## Tests
None (docs).

## Manual check
None.
