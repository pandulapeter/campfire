# 11 — A library folder that cannot be listed or a file that cannot be deleted is reported as an unknown failure

**Severity:** wrong message (all platforms) · **Area:** `:data:source:local:implementation` (`JvmFileStorage.kt` ×2,
`FileStorage.ios.kt`, `FileStorage.wasmJs.kt`, `FileStorage.kt` KDoc)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it.

Reviewer finding 2-local#4.

## What the user sees

A sync run on a device whose library folder cannot be listed — permissions taken away on a desktop, an external tool
holding the directory, OPFS refusing the directory on the web — ends with **"The sync did not finish."** (the
`UNKNOWN` sentence) rather than **"The library could not be read or written."** (`STORAGE`), which is the one that
tells the user where to look. The same for a local deletion the file system refuses on iOS or the web.

## Cause

`SyncRepositoryImpl.toFailureReason` (`SyncRepositoryImpl.kt:657-663`) recognises storage failures by type:

```kotlin
    private fun Throwable.toFailureReason() = when (this) {
        is SyncAuthorizationException -> SyncFailureReason.AUTHORIZATION
        is SyncNetworkException -> SyncFailureReason.NETWORK
        is SyncRemoteStorageFullException -> SyncFailureReason.REMOTE_STORAGE_FULL
        is LibraryStorageException -> SyncFailureReason.STORAGE
        else -> SyncFailureReason.UNKNOWN
    }
```

Reads and writes throw `LibraryStorageException` on every platform. Listings and (on two platforms) deletions do not:

- JVM, `JvmFileStorage.kt:45-60` (both copies):

  ```kotlin
          val files = directoryFile.listFiles()
              ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyArray()
  ...
          directoryFile.list()?.toList() ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyList()
  ```

- iOS, `FileStorage.ios.kt:66-67`, `:75-76` and `:118-120`:

  ```kotlin
              ?: if (fileManager.fileExistsAtPath(directoryPath)) throw IllegalStateException("Could not read \"$directoryPath\".") else emptyList<Any?>()
  ...
          if (fileManager.fileExistsAtPath(path) && !fileManager.removeItemAtPath(path, null)) {
              throw IllegalStateException("Could not delete \"$name\".")
          }
  ```

- OPFS, `FileStorage.wasmJs.kt:42-61` and `:101-105`: `list`, `listNames` and `delete` call the `js(...)` promises
  without `failingAsStorage`, so a rejection arrives as a plain `Exception` (or a `JsException`, which is not even an
  `Exception`, and reaches `runSynchronization`'s `Throwable` branch — `UNKNOWN` again).

The sync run lists the library first (`LibraryFileLocalSourceImpl.loadLibraryFiles` → `fileStorage.list`), so a
listing failure is the first thing a broken folder produces. The interface KDoc (`FileStorage.kt:35-37`) still says
"errors surface as exceptions of the platform, callers map them to `DataState.Failure`", which is how the repositories
treat them (any exception) and why nobody noticed; sync is the caller that distinguishes.

## The change

Invoke the **`code-style`** skill before the first edit. Every failure of a storage operation on something that is
there becomes a `LibraryStorageException`; nothing else about the behaviour changes (a directory that is not there is
still empty, a missing file is still a no-op to delete).

- **JVM** (both copies, identical): in `list` and `listNames` replace `throw IOException("Could not read …")` with
  `throw LibraryStorageException("Could not read \"${directoryFile.absolutePath}\".")`. `delete` is already wrapped.
  If `IOException` is then unused in the file, keep the import only if `failingAsStorage` still needs it (it does).
- **iOS**: the three `throw IllegalStateException(...)` above become `throw LibraryStorageException(...)` with the same
  messages. (`rootPath`'s `requireNotNull` — no data directory at all — stays an `IllegalArgumentException`: that is
  not a folder that is there and refuses, and nothing can be done about it from Settings.)
- **OPFS**: wrap the three in the existing `failingAsStorage` (`:114-125`), which already turns anything but a
  cancellation or a bad name into `LibraryStorageException`:

  ```kotlin
      override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.Default) {
          failingAsStorage(directory.displayName) {
              // One string instead of a handle per file: crossing the Kotlin/JS boundary for every entry would be far slower.
              listEntries(directoryHandle(directory)).await()?.toString().orEmpty()
          }
              .split(ENTRY_SEPARATOR)
              ...unchanged...
      }

      override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.Default) {
          failingAsStorage(directory.displayName) { listEntryNames(directoryHandle(directory)).await()?.toString().orEmpty() }
              .split(ENTRY_SEPARATOR).filter { it.isNotEmpty() }
      }

      override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
          failingAsStorage(name) {
              requireValidFileName(name)
              removeEntry(directoryHandle(directory), name).await()
          }
          Unit
      }

      private val StorageDirectory.displayName get() = pathSegments.joinToString("/")
  ```

  `failingAsStorage`'s KDoc speaks of "a file that exists and cannot be used"; widen it to "a file or a directory".
  Note that `directoryHandle`'s `IllegalStateException(OPFS_UNAVAILABLE)` now also surfaces as
  `LibraryStorageException` from these three, as it already does from reads and writes — nothing matches on that
  message (checked: it is only thrown, never caught by text).
- **`FileStorage.kt:35-37`** KDoc: replace "errors surface as exceptions of the platform, callers map them to
  `DataState.Failure`" with "a file or directory that is there and cannot be listed, read, written or deleted throws
  `LibraryStorageException` on every platform (sync reports that as a storage failure rather than an unknown one); a
  name that is a path throws `IllegalArgumentException` (see `requireValidFileName`). Callers map either to
  `DataState.Failure`."

## Tests

`data/source/local/implementation/src/desktopTest/.../storage/file/JvmFileStorageTest.kt`:

1. `` `reports a directory it cannot list as a storage failure` ``: write one song, then
   `root.walk().first { it.name == "songs" && it.isDirectory }.setReadable(false)`; if `directory.list() != null`
   afterwards (a superuser, or Windows, where the flag means nothing) `return@runBlocking` like the existing
   unreadable-file test does; otherwise `assertFailsWith<LibraryStorageException> { fileStorage.list(StorageDirectory.SONGS) }`
   and the same for `listNames`. Restore `setReadable(true)` at the end so `tearDown` can delete it.

The repository side needs no new test: `SyncRepositoryImplTest` already covers `LibraryStorageException` →
`SyncFailureReason.STORAGE` (`a run whose index cannot be written ends as a storage failure`).

## Verification

1. Unit test above; root unit test command.
2. Compile every target (the iOS and wasm changes have no tests):
   `./gradlew :data:source:local:implementation:compileKotlinIosSimulatorArm64 :data:source:local:implementation:compileKotlinWasmJs :data:source:local:implementation:compileDebugKotlinAndroid :data:source:local:implementation:compileKotlinDesktop`.
3. Desktop by hand (macOS/Linux): connect a test account, quit, `chmod 000 "…/Campfire/library/songs"`, start, Sync
   now. **Before:** "The sync did not finish." **After:** "The library could not be read or written." `chmod 755` it
   back and sync again: completes.

## Docs

- `FileStorage.kt` KDoc as above.
- `data/source/local/implementation/CLAUDE.md:47-49` — "A file that is there and cannot be read or written throws
  `LibraryStorageException` … on every platform" → "A file that is there and cannot be read, written or deleted, and a
  directory that is there and cannot be listed, throw `LibraryStorageException` … on every platform". Also the last
  sentence of the JVM bullet (`:69`), "A delete that fails throws `LibraryStorageException`", can stay; it is now true
  of every platform, so move it into the general bullet.

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`
- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt`
- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing. Same files as 09 (JVM `canHoldFileName`, `FileStorage.kt` `canHoldFileName`/`requireValidFileName`) and 10
(read functions), different functions; natural order 10 → 11 → 09. Lane E plan 46 edits `FileStorage.wasmJs.kt`'s
`writeFile` — rebase only.
