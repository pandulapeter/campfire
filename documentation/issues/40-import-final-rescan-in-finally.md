# 40 · `ImportFilesUseCaseImpl` skips the final rescan when a write throws

**Severity:** low · **Area:** `:domain:implementation`

`ImportFilesUseCaseImpl.kt:37–85`: the two `rescan()` calls come after the loops, not in a `finally`; a disk-full or
`IllegalStateException` from `importSong` mid-archive leaves the already-written songs on disk but absent from the
cached list until the next rescan.

## Fix

Wrap the two loops in `try { … } finally { if (importedSongFileNames.isNotEmpty()) songRepository.rescan(); if (importedSetlistFileNames.isNotEmpty()) setlistRepository.rescan() }`
(the rescans are `suspend`; a `finally` after a `CancellationException` should run them under
`withContext(NonCancellable)`). The exception still propagates, so the ViewModel still reports `ImportFailed`.
