# 37 · On a case-insensitive file system a rename that only changes case lands on `_2`

**Severity:** low (cosmetic, one-time) · **Area:** `:data:source:local:implementation` (`FileNames.kt`, `SongLocalSourceImpl.renameSong`, `SetlistLocalSourceImpl.renameSetlist`)

`isNamed` (`FileNames.kt:86`) compares `base == desiredBase` case-sensitively, while `uniqueName` asks the file
system, which on macOS and Windows says `foo.cho` exists when `Foo.cho` does. "Update file name" on a hand-named
`Foo.cho` → `foo_2.cho`.

## Fix

Treat "the same name in different case" as a rename the file system may or may not be able to see:

1. `uniqueName` gets an optional `currentName: String? = null`; a candidate that `equals(currentName, ignoreCase = true)`
   is accepted without the `exists` check (it is this file).
2. `renameSong` / `renameSetlist` pass the current name. Then handle the two file systems:
   - On a case-sensitive one, write-new-then-delete-old works as today.
   - On a case-insensitive one, writing `foo.cho` *is* writing `Foo.cho`, and the delete afterwards would remove the
     file. So when the desired name differs from the current one only by case, rename through a temporary name:
     write to `uniqueName(directory, desired)` with a throwaway suffix (`_renaming`), delete the old, write the final,
     delete the temporary. Three writes for a rare case; simple and safe on both kinds of file system.
3. `isNamed` stays case-sensitive (it decides whether to *offer* the rename, and a case change is worth offering on
   Linux and Android).
4. Test in the JVM storage tests on macOS (case-insensitive by default): rename `Foo.cho` to `foo.cho` yields exactly
   one file named `foo.cho`.
