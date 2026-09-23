# 14 — Renaming a file whose name differs from the new one only by case *and* Unicode form numbers it needlessly — and fixing only that would delete it

**Severity:** cosmetic today (a needless `_2`); latent data loss if half fixed (macOS desktop, APFS) ·
**Area:** `:data:source:local:implementation` (`FileNames.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it.

Reviewer finding 2-local#6. **The reviewer's warning is the core of this plan:** `uniqueName`'s own-name test and
`moveFile`'s same-file test must change *together*. Changing only `uniqueName` turns the needless `_2` into the
deletion of the only copy of the song.

## What the user sees

On a Mac, a song file whose name is capitalised and decomposed — `Ένα.cho` as macOS hands it out, `Ε` + U+0301 + `να`
(a file dropped into the library folder from Finder, or written by an older version) — with `{title: Ένα}` inside.
The song's menu offers **Update file name** (the desired name `ένα.cho` differs by case), and taking it produces
`ένα_2.cho` — numbered against itself, since there is no other file.

If plan 17 lands as recommended (a name that differs from the desired one only by case or Unicode form is no longer
offered a rename), this path is **no longer reachable from the UI**. The fix below is then hardening: `uniqueName` and
`moveFile` are the two functions every rename goes through, and the pair should agree about what "the same file" is
before anything else calls them with such a pair. If plan 17 is decided the other way, this plan fixes a live bug.

## Cause

`FileNames.kt:55-66` — the own-name test compares case only:

```kotlin
internal suspend fun FileStorage.uniqueName(
    directory: StorageDirectory,
    desired: String,
    collisionSuffix: (index: Int) -> String = ::normalizedCollisionSuffix,
    currentName: String? = null,
): String {
    fun isOwnName(candidate: String) = candidate.equals(currentName, ignoreCase = true)
    if (!isOwnName(desired) && !exists(directory, desired)) return desired
    // Only a rename needs the exact names (see currentName): for anything else, exists() already says all a listing would.
    val takenNames = if (currentName == null) emptySet() else listNames(directory).toHashSet()
    suspend fun isFree(candidate: String) = candidate !in takenNames && (isOwnName(candidate) || !exists(directory, candidate))
    if (isFree(desired)) return desired
```

`"ένα.cho"` (NFC) vs `"Ένα.cho"` (NFD): not equal ignoring case, because the code points differ in number. APFS
(case- and normalization-insensitive by default) says `exists("ένα.cho")` is true — it is the current file — so the
candidate is taken and the result is `ένα_2.cho`.

`FileNames.kt:86-103` — the move decides whether it needs the temporary-name detour by the same case-only comparison:

```kotlin
    if (newName.equals(currentName, ignoreCase = true)) {
        val extension = newName.knownExtension()
        val temporaryName = uniqueName(directory, newName.removeSuffix(extension) + TEMPORARY_MOVE_SUFFIX + extension)
        write(temporaryName)
        delete(directory, currentName)
        write(newName)
        delete(directory, temporaryName)
    } else {
        write(newName)
        delete(directory, currentName)
    }
```

With only `isOwnName` fixed, `uniqueName` returns `ένα.cho`, `moveFile` takes the `else` branch, `write("ένα.cho")`
atomically replaces **the same file** on APFS, and `delete("Ένα.cho")` then removes it — the song is gone. The
callers are `SongLocalSourceImpl.renameSong` (`:102-112`) and `SetlistLocalSourceImpl.renameSetlist` (`:98-113`).

## The change

Invoke the **`code-style`** skill before the first edit. One predicate, used by both functions (`normalizedToNfc` is
already imported in `FileNames.kt`):

```kotlin
/**
 * Whether [other] names the same file as this on a file system that ignores case and Unicode form - macOS by default,
 * and Windows for case - where writing the one is writing the other. [uniqueName] and [moveFile] have to agree about
 * it: a name taken for another file's by the first and for this file's by the second is a move that deletes what it
 * just wrote.
 */
private fun String.isSameFileNameAs(other: String) = normalizedToNfc().equals(other.normalizedToNfc(), ignoreCase = true)
```

`uniqueName`:

```kotlin
    fun isOwnName(candidate: String) = currentName != null && candidate.isSameFileNameAs(currentName)
```

`moveFile`:

```kotlin
    if (newName.isSameFileNameAs(currentName)) {
```

Update the KDocs: `uniqueName`'s `@param currentName` — "A case-insensitive file system … says a name that differs
from it only in case is taken" → "only in case or in Unicode form (a Mac hands names out decomposed)"; `moveFile`'s
second paragraph — "A name that differs from the current one only in case" → "only in case or in Unicode form".

Notes:

- On a file system that tells the two forms apart (Linux ext4, Android), the NFC name and the NFD name are two files;
  the detour through the temporary name still does the right thing there (write temp, delete current, write new,
  delete temp), at the cost of two extra writes for a move that happens once.
- `ignoreCase = true` compares per `Char` through upper and lower case, which also equates the Greek final `ς` with
  `σ`; that errs towards the detour, which is always safe.

## Tests

`data/source/local/implementation/src/commonTest/.../FileNamesTest.kt` — deterministic on every machine, with a small
in-memory `FileStorage` that behaves like APFS (add it as a private class in the test file): it stores each file under
the spelling it was first written with, and answers `exists` / `readText` / `writeText` / `delete` by the folded key
`name.normalizedToNfc().lowercase()`; `listNames` returns the stored spellings; everything else `TODO()`.

1. `aRenameToTheSameNameInAnotherCaseAndFormIsNotNumbered`: store `"Ένα.cho"` (Ένα, decomposed);
   `uniqueName(SONGS, "ένα.cho", currentName = <that>)` == `"ένα.cho"`.
2. `aMoveToTheSameNameInAnotherCaseAndFormKeepsTheFile`: same storage; `moveFile(SONGS, currentName = <decomposed>,
   newName = "ένα.cho") { writeText(SONGS, it, "{title: Ένα}") }`; assert `readText(SONGS, "ένα.cho") == "{title: Ένα}"`
   and `listNames(SONGS) == listOf("ένα.cho")`. **This is the test that fails if only `uniqueName` is changed.**
3. `aDifferentFileUnderTheOtherFormIsStillACollision`: a storage that does *not* fold (exact keys, like ext4) holding
   both the decomposed current name and a different file under the composed one → `uniqueName` gives `ένα_2.cho`.

`desktopTest/.../source/RenameTest.kt` (real file system; on a Mac this is APFS): add the same scenario as test 2
through `fileStorage.moveFile` directly — the file survives and is listed once, under the composed name. On Linux it
passes too (two files, one deleted).

## Verification

1. Tests above; root unit test command.
2. On a Mac, with plan 17 **not** applied (or its `isNamed` change temporarily reverted): quit the app, create
   `~/Library/Application Support/Campfire/library/songs/` + a file named with a decomposed capital
   (`python3 -c "open('Ένα.cho','w').write('{title: Ένα}\n')"` in that folder), start, open the song's menu →
   **Update file name**. **Before:** the file becomes `ένα_2.cho`. **After (both changes):** `ένα.cho`, one file, same
   text. **After with only `uniqueName` changed (do not ship; confirms why both are needed):** the song disappears.
3. With plan 17 applied: the menu no longer offers **Update file name** for that song (plan 17's own check).

## Docs

- `data/source/local/implementation/CLAUDE.md:93-96` — "a new name that differs from the old only in case is not
  numbered on a case-insensitive file system … and is moved through a temporary name" → "only in case or in Unicode
  form … on a file system that ignores them (macOS does both, Windows case) … moved through a temporary name; the
  two functions decide it with one predicate, since a name one of them takes for another file and the other for this
  one deletes the only copy."

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/RenameTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

- **Plan 17** edits `isNamed` in the same file (`FileNames.kt:126-132`) and `RenameTest.kt`'s two case-only tests.
  Land 17 first, then this on top; the two edits do not overlap (different functions), but 17 changes what the
  existing RenameTest cases assert, and this plan adds one next to them.
- **Plan 16** adds an `isTaken` parameter to `uniqueName`; whichever lands second rebases the signature (no logic
  conflict: `isOwnName` and `isTaken` are independent conditions of `isFree`).
