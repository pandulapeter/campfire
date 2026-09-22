# 41 · On a first launch through "Open with" or Share, the demo library is imported after the user's file, not before

**Severity:** minor (all platforms, only on the very first launch and only when that launch is an "Open with", a
share or a drop at startup. Usually harmless - the user ends up with their song and the demo either way - but when
the opened file carries a demo song's name with different content, the unannounced demo import asks a conflicts
question about files the user never imported, and "Replace" overwrites their copy with the demo) · **Area:**
`:presentation` (`CampfireViewModel`: `plantDemoLibraryOnFirstRun`, the import queue consumer)

## Symptom
1. Fresh installation. Open Campfire for the first time by tapping a `.cho` attachment ("Open with") or sharing one to
   it.
2. The user's song is imported first and opened. The demo songs are then planted into a library that is no longer
   empty.
3. If the opened file is named like a demo song but differs (an edited copy brought from another phone), a conflicts
   question comes up about "the library" - really about the demo, which the user never asked for - while the launch
   screen (`isDemoLibraryPending`) is still held. "Replace" overwrites the user's song with the demo version.

## Cause
The import queue is FIFO and the demo is the later arrival. The file handed over by the shell is queued at once:
`filesToImport` is collected on the first composition (`CampfireApp.kt:159`) and `importFiles(files)` enqueues it
(`CampfireViewModel.kt:1307-1309`), and the consumer takes it straight away (`:751-761`). The demo is only queued after
`isFirstRun()`, the first full library read and reading the bundled resources
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1373-1391`):

```kotlin
val library = screenData.first { it !is DataState.Loading }.data
if (library != null && library.unfilteredSongs.isEmpty() && library.setlists.isEmpty()) {
    // Through the queue like any other batch, so that a file opened with the app as it starts for
    // the first time is imported after the demo rather than racing it.
    readDemoLibrary()?.let { files -> enqueueImport(files, shouldAnnounceResult = false).await() }
}
```

The comment does not hold: the user's file is already ahead in the channel. The emptiness check is also taken
from the first scan, while the user's import is already writing.

## Fix
Hold the consumer back until the first-run decision is over, and plant the demo directly rather than through the
queue, so it is the first import of the installation. A gate alone is not enough: the user's file is already in the
channel ahead of anything queued later, so the demo must not go through the channel.

1. Next to `isDemoLibraryPending` (`:273`, which is declared before `init`, as this must be), add:

   ```kotlin
       /**
        * Completed once the first run has planted the demo library or found no reason to, which the consumer of
        * [importQueue] waits for before it takes its first batch: a file opened with the app as it starts for the first
        * time is queued long before that decision, and it belongs in a library that already has the demo in it rather
        * than the other way round - where the file has a demo song's name, the question about it is then asked of the
        * file the user opened, and not of songs they never asked for.
        */
       private val demoLibraryDecision = CompletableDeferred<Unit>()
   ```

2. The consumer in `init` (`:751-761`) waits for it first:

   ```kotlin
           viewModelScope.launch {
               demoLibraryDecision.await()
               for (request in importQueue) {
                   ...unchanged...
               }
           }
   ```

3. `plantDemoLibraryOnFirstRun` (`:1373-1391`) becomes:

   ```kotlin
       private suspend fun plantDemoLibraryOnFirstRun() {
           try {
               if (isFirstRun()) {
                   // Waited for rather than raced: what is being asked is whether the library is empty, and every
                   // library looks empty while it is still being read. Nothing else writes to it meanwhile, since the
                   // import queue waits for this.
                   val library = screenData.first { it !is DataState.Loading }.data
                   if (library != null && library.unfilteredSongs.isEmpty() && library.setlists.isEmpty()) {
                       // Imported here rather than through importQueue, where a file opened with the app is already
                       // waiting; see demoLibraryDecision. An empty library has no names for it to collide with, so
                       // this never asks anything.
                       readDemoLibrary()?.let { files ->
                           import(ImportRequest(files = files, shouldAnnounceResult = false, shouldOpenSong = false))
                           awaitImportSettled()
                       }
                   }
                   // Before the preferences are written rather than after: the queue has no reason to wait for those.
                   demoLibraryDecision.complete(Unit)
                   saveUserPreferences(userPreferences.filterNotNull().first())
               }
           } finally {
               // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
               // the launch screen is over the whole of it - and neither is the queue of imports.
               demoLibraryDecision.complete(Unit)
               isDemoLibraryPending.update { false }
           }
       }
   ```

   `complete` on an already completed deferred is a no-op, so the second call is harmless.

Why this is safe:
- `import` is only ever run by one caller at a time: the consumer is gated, `importDemoLibrary` (Settings) goes
  through the queue, and the Settings screen is behind the launch screen until `isDemoLibraryPending` is false. Its
  own guard (`_isImporting`, `pendingImport`) stays as it is. `import` and `applyImportPlan` catch every failure and
  clear `_isImporting` on every path, and `awaitImportSettled` only waits for that and for a conflicts question, which
  cannot arise in an empty library.
- No deadlock: nothing the planting awaits depends on the queue (`isFirstRun`, the scan, `readDemoLibrary` with its
  ten-second timeout, `import`). The `userPreferences.filterNotNull().first()` that can hang on an unavailable OPFS
  now comes after the gate is opened.
- Not a first run (every later launch): the `if` is skipped and `finally` opens the gate at once, after one read of
  whether the preferences exist.
- With the demo first, the user's file is imported second, after `isDemoLibraryPending` has gone false, so a
  conflicts question about it is shown over the app rather than behind the launch screen, and the opened song is
  still opened (`shouldOpenSong`).
- The flag suggested in the report for "leave conflicts alone" is not needed: the demo can no longer meet anything.

## Tests
None (UI).

## Verify
1. Android, fresh install (`adb uninstall`, then install the `.debug` build without launching it). Open a `.cho` file
   with Campfire from a file manager (or `adb shell am start -a android.intent.action.VIEW -d <content uri> -t text/plain`
   to the activity). After the launch screen: the opened song is shown, the library holds it plus the two demo songs
   and the demo setlist; one "import finished" snackbar for the user's file only.
2. Same, with a file named exactly like a demo song (copy one from
   `presentation/src/commonMain/composeResources/files/demo`, change a line). The conflicts question is shown over the
   app, about that file; "Keep both" leaves the demo song and the user's as `_2`; "Replace" leaves the user's version.
3. Fresh install opened normally: the demo is planted as before, no snackbar. Second launch: no delay before an
   "Open with" import.
4. Desktop: delete the app data directory and start with a file argument / macOS "Open with": the same as 1.

Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`, `:presentation:compileKotlinWasmJs`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
`presentation/CLAUDE.md`, the `ui/DemoLibrary.kt` bullet, after "…so that the next start is no longer a first one."
add: "It is imported directly rather than through the import queue, and the queue takes no batch before that decision
is over (`demoLibraryDecision`): a file opened with the app on its first start is queued before the library has even
been read, and it goes into a library that already has the demo rather than the demo being planted after it."
The phrase earlier in the same bullet, "so that they reach the library through `importFiles` exactly as a dropped
archive does", stays true (`import` is the same path).

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 17 edits `applyImportPlan` in the same file; run them one after another.
