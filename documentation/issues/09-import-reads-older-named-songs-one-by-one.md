# Read the "arrived as" library files of an import in the batched prefetch, not one at a time

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (worst on web)
**Files:** domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt, domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt

## Problem
`ImportPlanner.planSongs` prefetches the texts of every library family the batch touches, 64 at a time in parallel
(`readAll`, `READ_BATCH_SIZE`), but the second way a song is matched — the library file it *arrived as* — is read
inline, one song after another:

```kotlin
// ImportPlanner.kt:86-91, inside songs.map { … }
val arrivedAs = song.sourceFileName?.takeIf { it != song.fileName && it in libraryFileNameSet }
val comparable = if (!family.hasLibraryTexts && arrivedAs == null) null else ChordProSplitter.comparable(song.text)
val libraryFileName = comparable?.let(family::libraryFileNameOf)
    ?: arrivedAs?.takeIf { readLibraryText(it)?.let(ChordProSplitter::comparable) == comparable }
```

That path is taken by every song of a library backup imported back into a library whose files were named before the
current naming rule (root CLAUDE.md: such files "keep their names until … Update file name"): the export keeps
library names inside the archive (`Artist - Title.cho`), the derived name (`artist-title.cho`) has no family in the
library, so each song falls through to a sequential `readLibraryText` →
`SongContentRepository.loadSongContent` → `FileStorage.readText`. On the web each of those is a `withContext` plus
one OPFS promise chain (`readFileTexts` for a single name), a few milliseconds each, so a 2,000-song backup adds
seconds of "preparing" that the prefetch exists to avoid — the domain CLAUDE.md states "Planning reads the library
files of every family the batch touches up front, 64 at a time in parallel, rather than one after another".

## Fix
1. In `planSongs`, compute the arrived-as names before the prefetch:
   `val arrivedAsNames = songs.mapNotNull { s -> s.sourceFileName?.takeIf { it != s.fileName && it in libraryFileNameSet } }`
   and pass `familyMembers.flatMap { … familyCandidates(…) } + arrivedAsNames` (distinct) to `readAll`.
2. Replace the inline `readLibraryText(it)` with `libraryTexts[it]`. Only read inline if the key is absent (it will
   not be, but keeps the function total).
3. Optional: cache `ChordProSplitter.comparable` of an arrived-as text in a local map, since a text can be compared
   more than once.
4. Test in `ImportPlannerTest` (commonTest): a `readLibraryText` fake that counts calls and records concurrency;
   plan 200 songs each arriving as a distinct old-named library file with identical text; assert every entry is
   `IDENTICAL` with `fileName` = the old name (behaviour unchanged) and that each library file was read exactly once.

## Verification
`./gradlew :domain:implementation:desktopTest`. Manual (web): library with a few hundred songs under hand-made names
(`Artist - Title.cho`), export the library, import the archive back; preparing should take about as long as for a
library named by the current rule, and every song is reported as already there.

## Conflicts
none known
