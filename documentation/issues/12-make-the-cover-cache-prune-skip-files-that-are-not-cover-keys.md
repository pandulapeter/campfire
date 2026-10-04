# Make the cover cache prune delete only files named like a cover key, so it never takes a copy being written

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android, desktop (iOS possibly; the web is not affected)
**Challenged:** sound — `Sha256.hashToHex` is lowercase (`HEX_DIGITS = "0123456789abcdef"`) and the key has been the SHA-256 of the address since covers shipped, so no older cache file falls outside the filter. Side effect to accept: `clearCoverArtCache` (which is `keepOnlyCoverArt(emptySet())`) also stops deleting non-key names; the JVM leftovers sweep covers those.
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/CoverArtLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/CoverArtLocalSourceImplTest.kt` (new)
- `data/source/local/implementation/CLAUDE.md` (where it describes the cover copies, if it says how they are pruned)

## Problem

`CoverArtLocalSourceImpl.keepOnlyCoverArt` deletes every name in the covers directory that is not a wanted key:

```kotlin
override suspend fun keepOnlyCoverArt(keys: Set<String>) {
    quietly {
        fileStorage.listNames(StorageDirectory.COVERS)
            .filterNot { it in keys }
            .forEach { fileStorage.delete(StorageDirectory.COVERS, it) }
    }
}
```

`FileStorage.listNames` is documented as "The unfiltered directory names", and the JVM storage (Android and desktop,
`JvmFileStorage.kt`, identical in `androidMain` and `desktopMain`) does not filter: `directoryFile.list()?.toList()`.
Its `list()` does filter (`paths.filterNot { it.fileName.toString().endsWith(TEMPORARY_FILE_SUFFIX) }`), which is why the
cache-size row never sees them. Every write goes through `writeAtomically`, which creates the temporary file in the same
directory:

```kotlin
val temporary = Files.createTempFile(target.parent, TEMPORARY_FILE_PREFIX, TEMPORARY_FILE_SUFFIX)   // ".campfire-NNN.tmp"
write(temporary.toFile())
FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
... Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
```

The prune runs from `CoverArtRepositoryImpl`'s collector on every finished song list whose set of cover keys changed
(a sync run's refresh, an edit that sets a cover), while thumbnails on screen are being downloaded and saved
(`saveCoverArt`). A prune that lists the directory while a save is between `createTempFile` and the move deletes the
temporary file; the move then fails with `NoSuchFileException`, `saveCoverArt` logs "Could not access the cover art
cache" and the cover is not cached (it still shows this time, and is downloaded again next time it is needed, so
offline it is missing). The window is the write plus the `fsync`, so this is rare — hence low.

The web storage filters its own temporaries out of `listNames` (`listEntryNames` skips `.campfire-tmp` and
`.campfire-commit`), so it is not affected. iOS writes with `NSData.writeToFile(path, atomically = true)`, whose
auxiliary file Foundation names and places itself; whether it lands in the same directory is not documented, so iOS
may or may not be affected.

## Fix

Prune only names that have the shape of a cover key, rather than teaching the prune every storage's temporary-file
naming. A key is `Sha256.hashToHex(url.encodeToByteArray())` (`CoverArtRepositoryImpl.keyOf`): 64 hex characters
(verify the case `Sha256.hashToHex` produces in `:data:source:remote:api` and match it).

```kotlin
.filter { it.length == KEY_LENGTH && it.all { c -> c in '0'..'9' || c in 'a'..'f' } && it !in keys }
```

with a KDoc line saying why: the directory also holds the storage's own temporary files while a copy is being written,
and on iOS whatever Foundation's atomic write puts beside it; a name that is not a key was not written by this class and
is not this class's to delete (JVM leftovers are swept by `JvmFileStorage.removeLeftovers` after an hour anyway).

Do not change `JvmFileStorage.listNames` instead: `FileNames.kt` also uses it for taken names, where its unfiltered
answer is deliberate and harmless, and its KDoc promises unfiltered names.

## Tests

New `CoverArtLocalSourceImplTest` in `:data:source:local:implementation` `desktopTest`, on a `JvmFileStorage` rooted in a
temporary directory (see how `JvmFileStorageTest` builds one): save two covers under 64-hex keys, put a file named
`.campfire-123.tmp` into the covers directory by hand, call `keepOnlyCoverArt(setOf(firstKey))`; assert the first key is
still readable, the second is gone, and the `.tmp` file is still there.

## Manual check

None that can be timed by hand. On Android or desktop, with a library where many songs carry covers, run a sync that
changes cover addresses while the Songs screen is scrolled through; the log (`campfire.log` on desktop, logcat on
Android) shows no "Could not access the cover art cache" line, and the covers show offline afterwards.
