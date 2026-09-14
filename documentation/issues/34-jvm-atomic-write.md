# 34 · JVM atomic write: a crash window on Windows, a non-atomic fallback, a fixed temp name, no fsync

**Severity:** low-medium (desktop and Android; crash windows only) · **Area:** `:data:source:local:implementation` (`JvmFileStorage`, both copies)

## Cause

`JvmFileStorage.writeAtomically` (`desktopMain/.../JvmFileStorage.kt:74–89`, identical copy in `androidMain`):

- On Windows the target is `delete()`d before `renameTo`; a crash between the two leaves only `name.tmp`, which
  `list` filters out, so the song vanishes.
- When `renameTo` fails, `copyTo(overwrite = true)` truncates the target first, so a crash during the copy leaves a
  half file — the very case the class doc says cannot happen.
- The temp name is `name + ".tmp"`, so an editor save and a sync `writeLibraryFile` of the same file can interleave:
  B truncates A's temp, A renames B's bytes into place, B's rename fails, B's `copyTo` throws `NoSuchFileException`.
- No `fsync` before the rename, so a power loss (not an app crash) can leave a zero-length file on some file systems.

## Fix

Replace the body with `java.nio.file`:

```kotlin
private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
    val target = file(directory, name).toPath()
    // A name of its own per write, so two writes of one file cannot share a temporary file.
    val temporary = Files.createTempFile(target.parent, "$name.", TEMPORARY_FILE_SUFFIX)
    try {
        write(temporary.toFile())
        // Flushed to the device before the rename, or a power loss right after could keep the name and lose the bytes.
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: AtomicMoveNotSupportedException) {
            // Some file systems (network shares, some Android storage) cannot do it in one step; the plain move still
            // never leaves the target truncated, since it copies first and replaces at the end.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}
```

- `ATOMIC_MOVE` with `REPLACE_EXISTING` replaces on Windows without the delete step (NTFS supports it).
- `list` keeps filtering `TEMPORARY_FILE_SUFFIX`; the temp name still ends in it.
- Drop `IS_WINDOWS`.
- Both copies (`desktopMain`, `androidMain`) must stay identical; the KDoc says so.
- The existing JVM file storage unit test (`desktopTest`) covers reads and writes; add a case that two concurrent
  `writeText`s of the same name both succeed and the file holds one of the two texts in full.
