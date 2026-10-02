# Stop probing one name at a time when numbering a colliding file

**Challenged:** amended — the directory is listed only once the first numbered candidate (`_2`) is taken too, not as soon as the desired name collides: a listing is O(library) on every platform, and an import of a few hundred songs that each collide once (a re-import of an edited backup, Keep both) would otherwise list the whole directory per song where it costs a few `exists` calls today; and the redundant second `exists(desired)` of a non-rename call is dropped, which the test's call count needs.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all, worst on the web (every probe is an OPFS worker round trip)
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`

## Problem
`FileStorage.uniqueName` numbers a collision by linear probing: `isFree(candidate)` calls `exists(directory, candidate)`
for `x`, `x_2`, `x_3`, ... Each imported song is its own `uniqueName` call (`SongLocalSourceImpl.importSong`, under
`SongRepositoryImpl.nameMutex`), so a 500-song songbook whose songs all resolve to the same name (`untitled.cho`) costs
1 + 2 + ... + 500 ≈ 125,000 `exists` calls (and a fully-numbered family of N costs N probes for each new song forever).
Only a rename (`currentName != null`) lists the directory today:
```kotlin
// Only a rename needs the exact names (see currentName): for anything else, exists() already says all a listing would.
val takenNames = if (currentName == null) emptySet() else listNames(directory).toHashSet()
```

## Fix
Keep the rename path as it is (it needs the exact names up front). For any other call, list the directory lazily, the
first time a *numbered* candidate turns out to be taken:
```kotlin
var takenNames: Set<String>? = if (currentName == null) null else listNames(directory).toHashSet()
suspend fun isFree(candidate: String) =
    takenNames?.contains(candidate) != true && !isTaken(candidate) && (isOwnName(candidate) || !exists(directory, candidate))
...
while (true) {
    val candidate = base + collisionSuffix(index) + extension
    if (isFree(candidate)) return candidate
    // A family already numbered past here: one listing tells every taken number at once, where probing them would
    // cost one storage call each.
    if (takenNames == null) takenNames = listNames(directory).toHashSet()
    index++
}
```
Also make the second look at `desired` (`if (isFree(desired)) return desired`) rename-only: for any other call the first
line has just asked `exists(desired)` and been told it is taken, so asking again is a wasted storage call (today a
single collision costs three calls, not two). The listing only short-cuts names it shows are taken; a candidate absent from it is still confirmed with `exists()`,
so the case/Unicode folding of APFS/NTFS (which `exists` knows and an exact listing does not) is preserved, and a file
written meanwhile by sync (outside `nameMutex`) is still caught: a stale listing can only make a name look taken,
never free. Cost: the no-collision path stays one `exists`, a single collision drops from three to two, and a family of N costs
three `exists` plus one listing instead of N + 2 probes. Update the `currentName` KDoc paragraph ("and only then is
the directory listed") to say the directory is also listed once a numbered name is taken, and only to skip numbers it
shows are taken.

## Tests
In `FileNamesTest` extend `InMemoryFileStorage` with counters of `exists` and `listNames` calls. Write `a.cho`,
`a_2.cho`, ... `a_50.cho`; assert `uniqueName(SONGS, "a.cho") == "a_51.cho"`, `existsCalls <= 3` and `listCalls == 1`
(fails before: 52 `exists` calls). With only `a.cho` present, assert `a_2.cho`, two `exists` calls and no listing. Keep
the existing folded-name tests passing (`foldsNames = true`: a candidate that differs only in case from a listed file
must still be reported taken, via `exists`).

## Manual check
Import a zip of 300 songs that all lack a title on the web build: the import's IMPORTING phase no longer slows down as
it goes; compare the elapsed time with the build before the change.
