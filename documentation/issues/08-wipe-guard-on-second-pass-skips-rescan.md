# 08 · If the wipe guard stops a run on its second pass, the song and setlist lists are not read again

**Severity:** minor (all platforms. Rare: pass 0 has to move files and have an upload refused as a conflict, and between the two listings another device has to delete more than half of the library. The files pass 0 downloaded, and the ` (2)` copies it wrote, are on disk but missing from the lists until the next rescan. A setlist or song edited in between is built on the stale cache) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl.runSynchronization`)

## Symptom
A run downloads some songs and writes a conflict copy on its first pass. One of its uploads meets a remote change,
so it lists again, and by then another device has emptied most of the folder. Settings asks "Delete them here too /
Keep them and upload", which is right. But the songs the first pass downloaded do not appear in the list, and neither
do the ` (2)` copies, until something else rescans (the next run, an import, a restart).

## Cause
The engine checks the guard on every pass
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:137-140`),
so it can return `DeletionsNeedConfirmation` after pass 0 has applied operations. The repository's branch assumes it
cannot (`SyncRepositoryImpl.kt:358-368`):

```kotlin
is SyncEngine.Result.DeletionsNeedConfirmation -> {
    // The run stopped before anything moved, so there is nothing for the lists to read again and
    // the index only has to stop saying that a run is going.
    latestIndex?.let { saveIndexQuietly(it().copy(isRunInProgress = false)) }
```

`hasFinishedOperations` (set in `onIndexChanged`, `:349-353`) is true in that case, but the branch ignores it. The
index is saved correctly. It is only the rescan that is missing, and every other ending that may follow moved files
does that rescan (`finishRunCutShort`, `:516-524`).

Checking the guard only on pass 0 is **not** the fix. A folder emptied between the two listings is exactly what the
guard exists for, and pass 1 would then delete the local copies without asking.

## Fix
`SyncRepositoryImpl.kt`, the `DeletionsNeedConfirmation` branch. Replace the comment and the save with:

```kotlin
                    is SyncEngine.Result.DeletionsNeedConfirmation -> {
                        // Asked before the deletions moved, but not necessarily before anything did: a second pass
                        // can find the folder emptied after the first one had already brought files in, and those
                        // are on disk whether or not the question is answered.
                        latestIndex?.let { saveIndexQuietly(it().copy(isRunInProgress = false)) }
                        if (hasFinishedOperations) {
                            rescanLibraryAfterRun()
                        }
                        updateConnected { ... }   // unchanged
                    }
```

The first pass's summary (for example the names of its conflict copies) is still not reported. The outcome is the
question, and the copies are visible in the library once it is rescanned. Carrying the summary into
`SyncOutcome.DeletionsNeedConfirmation` would need a new line of UI text for a rare case. It is not part of this fix.

## Tests
`SyncRepositoryImplTest.kt`, `a run stopped by the question on its second pass reads the library again`:
- Local library: `song(1)`…`song(10)` with contents `"Song n"`, plus `song(11)` holding `"New"`. Index (written into
  `stateLocalSource.index` as in 03's test, or built with the same helper) records `song(1)`…`song(10)` at `"r1"` with
  their hashes. The provider holds `song(1)`…`song(10)` at `"r1"` plus `song(12)` (`"Incoming"`), which is new to this
  device.
- `onUpload = { key -> if (key == song(11)) { (1..10).forEach { provider.files -= song(it) };
  provider.files[song(11)] = "Other".encodeToByteArray() to "r9" } }`, so the upload meets a remote file (`Conflict`)
  and the second listing is missing ten of the twelve indexed files. The provider has to be declared first and
  `onUpload` assigned after, since it is a `var`.
- `songRepository = RecordingSongRepository(onRescan = { snapshots += local.files.keys.toSet() })`.
- `restore()`, `synchronize(ASK)`, `awaitOutcome()`. Expect `lastOutcome` to be `SyncOutcome.DeletionsNeedConfirmation`
  and `song(12) in snapshots.last()`. The live rescan at the start of the run happens before the download, and the next
  one is throttled for a second, so only the rescan after the run can see `song(12)`.

Run `./gradlew :data:repository:implementation:desktopTest`.

## Verify
Hard to reproduce by hand, since it needs two devices racing within one run. The test covers it. Compile
`:data:repository:implementation` for desktop and wasmJs.

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet: replace "the engine returns
`Result.DeletionsNeedConfirmation` before anything moves, and the repository reports it as the run's outcome without
a rescan." with "the engine returns `Result.DeletionsNeedConfirmation` before those deletions move, and the repository
reports it as the run's outcome, rescanning only when an earlier pass of the same run had already moved files."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 01 and 03 edit other lines of `runSynchronization`. Run them one after another.
