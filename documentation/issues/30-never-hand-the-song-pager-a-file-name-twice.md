# Never hand the song pager a file name twice, so that opening a duplicate from the import screen cannot crash

**Kind:** bug (crash)  ·  **Severity:** high  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CampfireDestination.kt (KDoc only),
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/SongDetailsPages.kt (new),
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/SongDetailsPagesTest.kt (new)

## Problem

The import result's DUPLICATES list can name one library file more than once, by design: the planner gives every
incoming copy of a song the library already has `fileName = libraryFileName` and `Status.IDENTICAL`
(`domain/implementation/.../ImportPlanner.kt`, `if (libraryFileName != null) return@mapIndexed song.toEntry(fileName =
libraryFileName, status = ImportPlan.Status.IDENTICAL)`), and `ImportFilesUseCaseImpl` appends each DISREGARD to
`duplicateFileNames` (`duplicateFileNames += it`); a repeated copy of a *new* song does the same through
`repeatedEntryIndex`/`storedNames`. The import screen's rows already cope (`ImportReportSections.kt`'s `toRows` suffixes
the key of a repeat with `#1`, and `ImportReportRow.key`'s KDoc says "a batch can bring the same file twice"), but the
list of file names handed to the pager does not:

`ImportReportScreen.kt:584-595` (8ee010b36)
```kotlin
val songFileNames = section.rows.filter { it.isSong }.map { it.fileName }
...
onClick = if (row.isSong) {
    { onOpenSong(songFileNames, songFileNames.indexOf(row.fileName).coerceAtLeast(0)) }
}
```
`onOpenSong` is `CampfireViewModel.openReportedSong(songFileNames, index)` (`CampfireViewModel.kt:1720`), which pushes
`CampfireDestination.SongDetails(songFileNames = songFileNames, …)` unchanged. `SongDetailsScreen.kt:214-220` maps it
one to one (`destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }`) and
`SongDetailsScreen.kt:723-732` builds `HorizontalPager(…, key = { songs[it].fileName }, beyondViewportPageCount = 1)`.

Scenario: the library holds `a-b.cho`; the user picks `a-b.cho` and `a-b (1).cho` (the same file downloaded twice), or
drops a folder holding both. The import is clean, so it ends in a snackbar whose **Details** opens the import screen;
"Already in the library" lists two rows, both `isSong`. Tapping either pushes `SongDetails(["a-b.cho", "a-b.cho"], 0)`.
The pager composes page 0 and page 1 (`beyondViewportPageCount = 1`) under the same key. The pager is a lazy layout
over `SubcomposeLayout`, whose slot id is the item key: `SubcomposeLayout.kt` (compose ui 1.12.1, line ~613)
`requirePrecondition(itemIndex >= currentIndex) { "Key \"$slotId\" was already used. If you are using LazyColumn/Row
please make sure you provide a unique key for each item." }`, and the lazy layout's `SaveableStateHolder` throws
`require(key !in registries) { "Key $key was used multiple times " }` (runtime-saveable 1.12.1,
`SaveableStateHolder.kt:88`). Either way the app crashes, on every platform. (Read in the 1.12.1 sources jars under
`~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-android` and `androidx.compose.runtime/runtime-saveable`.)

Every other way onto the screen already names each song once: `openSong` and `openImportedSong` name one,
`openSongInSetlist` and the web's `BrowserRoutes` read setlist entries, which the local source's mapper deduplicates
(`SetlistMappers.kt`, `distinctBy { it.file }`), and the rename path at `CampfireViewModel.kt:1864` ends in
`.distinct()`. But `SongDetails` is also restored from the saved back stack, so a stack saved by a build before this fix
would crash again on the next launch unless the screen itself refuses a repeat.

## Fix

Three small changes; the first two remove the cause, the third is the guarantee the brief asks for ("no list of file
names handed to the pager can carry a repeat"):

1. `ImportReportScreen.kt`, in `sections(…)`: `val songFileNames = section.rows.filter { it.isSong }.map { it.fileName }.distinct()`.
   `indexOf(row.fileName)` already finds the first occurrence, so both duplicate rows open the one page.
2. `CampfireViewModel.openReportedSong`: normalise before pushing, so the destination itself never carries a repeat.
   Extract the arithmetic as a pure helper in a new `ui/navigation/SongDetailsPages.kt`:
   ```kotlin
   /**
    * [songFileNames] with every name once, since the song pager is keyed by file name, and [index] moved to where the
    * song it pointed at is in that list (page 0 for an index that points nowhere).
    */
   internal fun reportedSongPages(songFileNames: List<String>, index: Int): Pair<List<String>, Int> {
       val pages = songFileNames.distinct()
       return pages to pages.indexOf(songFileNames.getOrNull(index)).coerceAtLeast(0)
   }
   ```
   and use it:
   ```kotlin
   internal fun openReportedSong(songFileNames: List<String>, index: Int) {
       val (pages, initialIndex) = reportedSongPages(songFileNames, index)
       openSongDetails(CampfireDestination.SongDetails(songFileNames = pages, setlistFileName = null, initialIndex = initialIndex))
   }
   ```
3. `SongDetailsScreen.kt`, the `songs` remember: end the mapping with `.distinctBy { it.fileName }`
   (`destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }.distinctBy { it.fileName }`),
   and say in its comment that the pager's key is the file name, so a repeated name would be a repeated key and a crash,
   whatever built the destination (a saved back stack included). The initial page is already only approximate when a
   named song is missing (`mapNotNull` shifts the indices the same way), so no further change to the index handling.

Add one line to the `SongDetails` KDoc in `CampfireDestination.kt`: `songFileNames` names each song once.

## Tests

`presentation/src/commonTest/.../ui/navigation/SongDetailsPagesTest.kt` for `reportedSongPages`:
- `["a", "a"], 1` → `(["a"], 0)`;
- `["a", "b", "a", "c"], 3` → `(["a", "b", "c"], 2)`;
- `["a", "b"], 1` → unchanged `(["a", "b"], 1)`;
- an index past the end → page 0.

The crash itself is in the lazy layout and cannot be unit-tested.

## Manual check

On any platform: have `a-b.cho` in the library, import a selection of `a-b.cho` and a byte-identical copy named
`a-b (1).cho`; tap **Details** on the snackbar, then either row under "Already in the library". The song opens, one
page, no crash; Back returns to the import screen.
