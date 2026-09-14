# 31 · The library scan parses every song on the main thread

**Severity:** medium (a visible stall at start-up on a large library) · **Area:** `:data:source:local:implementation`

## Cause

`SongLocalSourceImpl.readSong` (`SongLocalSourceImpl.kt:105–108`) hops to `Dispatchers.IO` only inside
`fileStorage.readText`; `ChordProParser.summarize(text)` and `toSong` (two `normalizedName` calls) run on whatever
dispatcher `loadSongs` was called from. The chain is `CampfireViewModel.init` → `viewModelScope.launch` →
`LoadScreenDataUseCaseImpl` → `BaseLocalDataRepository.read()` → `loadSongs`, with no dispatcher switch, so all 64
`async`s of a batch resume on `Main.immediate` and parse serially there. `SetlistLocalSourceImpl.loadSetlists`
decodes JSON on Main the same way. `summarize` is a single line scan, but two thousand of them is a stall.

## Fix

1. `SongLocalSourceImpl.loadSongs`: wrap the body in `withContext(Dispatchers.Default) { coroutineScope { … } }`.
   The `onProgress` callback then runs on Default; `BaseLocalDataRepository.publishPartialData` only sets a
   `StateFlow`, which is thread-safe. `loadSong` (single file) the same, since a save re-reads through it.
2. `SetlistLocalSourceImpl.loadSetlists`: same wrapper.
3. Note in the class KDoc why (the reads are already on IO; the parsing was not on anything).
4. Do not move the dispatcher switch up into the use case: the local source is where the parsing is, and the rule
   in this codebase is that a source runs its own work off the caller's thread (see `ArchiveLocalSourceImpl`).

## Verification

Desktop with a 2000-file library (generate with a script in the scratchpad): the launch screen's handover is smooth
and the Songs list fills without the window freezing. Android: Perfetto / `StrictMode` would also show it; a
before/after of the main-thread time in a System Trace is the proof if one is wanted.
