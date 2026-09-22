# 48 · A large "Keep both" import lists the whole library once per colliding song

**Severity:** performance (all platforms, worst on the web; quadratic in the size of an import whose songs collide
with the library under different content — re-importing an older export of a library that has been edited since,
or a second copy of a big collection, and answering "Keep both"; uncommon) · **Area:**
`:data:source:local:implementation` (`FileNames.kt`: `uniqueName`)

## Symptom
Import 3,000 songs whose names the library already holds with different text, answer **Keep both**. Measured on the
desktop JVM against a temporary directory on APFS (throwaway test, `JvmFileStorage`, 2 KB songs, library growing from
3,000 to 6,000 files): 3,000 colliding writes take **25.3 s**, of which about 10 s is directory listings; the same
writes with the listing gone take **15.0 s**, as long as 3,000 writes of new names do (15.9 s). One listing of a
6,000–9,000 file directory costs 3–7 ms on the JVM.

On the web every listing is an async walk of the OPFS directory through `listEntryNames` (a promise per entry,
`storage/file/FileStorage.wasmJs.kt:59-61`), which is estimated to be an order of magnitude slower per entry than a
native `readdir`; 3,000 of them over a 3,000–6,000 entry directory are minutes of an import that already runs on the
page's only thread. (Not measured in a browser.)

The same happens, per file, to a sync run that brings in many files colliding with local ones
(`LibraryFileLocalSourceImpl.kt:43`) and to setlists imported next to namesakes (`SetlistLocalSourceImpl.kt:104`).

## Cause
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt:53-72`:

```kotlin
fun isOwnName(candidate: String) = candidate.equals(currentName, ignoreCase = true)
if (!isOwnName(desired) && !exists(directory, desired)) return desired
val takenNames = listNames(directory).toHashSet()
suspend fun isFree(candidate: String) = candidate !in takenNames && (isOwnName(candidate) || !exists(directory, candidate))
```

The listing is taken for every name that collides, and `importSong` (`SongLocalSourceImpl.kt:86`) asks once per song.
It is only needed for a **rename** (`currentName != null`): there `exists` is skipped for a candidate that differs from
the file's own name only in case (a case-insensitive file system says it is taken, by this very file), and the exact
listing is what decides whether some *other* file holds it. Without `currentName`, `isOwnName` is always false, so
`isFree` is `candidate !in takenNames && !exists(candidate)` — and a name in the listing is a file that exists, so the
first half never decides anything the second does not.

## Fix
One line in `uniqueName`:

```kotlin
// Only a rename needs the exact names (see currentName): for anything else, exists() already says all a listing would.
val takenNames = if (currentName == null) emptySet() else listNames(directory).toHashSet()
```

Nothing else changes: renames (`SongLocalSourceImpl.kt:96`, `SetlistLocalSourceImpl.kt:82`) still list once, and a
non-rename probes `name_2`, `name_3`, … with `exists`, which is a stat on the JVM and iOS and one `getFileHandle` on the
web — one or two probes for an ordinary collision, rather than a walk of the directory.

Do **not** cache the listing across calls (the view of a directory the import is writing into would go stale), and do
not have `ImportPlanner` hand out the numbered names instead: the numbering at write time is what keeps a file that
appeared meanwhile (a sync run, the desktop user's own file manager) from being overwritten.

## Tests
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
(the JVM storage against a temporary directory; `uniqueName` is `internal` in the same module):
- `a collision is numbered without listing the directory`: a `FileStorage` wrapper around `JvmFileStorage(root)`
  whose `listNames` fails the test; with `a.cho` and `a_2.cho` written, `uniqueName(SONGS, "a.cho")` is `"a_3.cho"`.
- `a rename that only changes case still lists the directory`: with `a.cho` written,
  `uniqueName(SONGS, "A.cho", currentName = "a.cho")` is `"A.cho"` (the existing `RenameTest` covers the rest and must
  stay green).
The existing `FileNamesTest` and `RenameTest` pass unchanged with the line in place (verified in a worktree).

## Verify
1. `./gradlew :data:source:local:implementation:desktopTest`.
2. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): import an archive of 1,000 songs, edit one line in each
   with a script, zip and import again, **Keep both**: the second import finishes in about the time the first did,
   and every song arrives as `x_2.cho`.
3. Rename a song whose file name differs from its header only in case on macOS desktop (Update file name): it is
   renamed in place, not numbered.

## Docs
None: the KDoc of `currentName` already says the listing is there for renames; add "— and only then is the directory
listed" to its last sentence.

## Touches
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`

## Depends on
Nothing.
