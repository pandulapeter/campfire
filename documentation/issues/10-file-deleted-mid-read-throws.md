# 10 — A file deleted between the existence check and the read throws instead of reading as missing

**Severity:** a sync run fails for a file the user just deleted (all platforms, a race) ·
**Area:** `:data:source:local:implementation` (`JvmFileStorage.kt` ×2, `FileStorage.ios.kt`, `FileStorage.wasmJs.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. It is a race, so the "Verification" section leans on a unit test and on a forced interleaving rather than on
catching it by hand.

Reviewer finding 2-local#3.

## What the user sees

A sync run is going (a first sync of a large library takes minutes) and the user deletes or renames a song, or a
setlist rename moves its file. If the run's preparation had just listed that file and gets to reading it a moment
after it went, the run ends with "The library could not be read or written." instead of carrying the deletion
through. The next run is fine, so it looks like a flaky sync. The same read race exists for any other caller of
`readText` / `readBytes` (a song opened as it is deleted by sync), which then shows an error instead of "gone".

## Cause

The contract (`FileStorage.kt:57-64`, and `LibraryFileLocalSource.kt:31-36`): "Null if the file does not exist; one
that exists and cannot be read throws `LibraryStorageException`." Every implementation asks "is it there?" and then
reads, as two steps, and a file removed between them fails the second step as a storage failure:

JVM (`JvmFileStorage.kt:70-76`, desktop and Android copies identical):

```kotlin
    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readAllBytes().decodeLibraryText() } else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readAllBytes() } else null }
    }
```

`Files.readAllBytes` throws `java.nio.file.NoSuchFileException`, an `IOException`, which `failingAsStorage`
(`:155-160`) wraps into `LibraryStorageException`.

iOS (`FileStorage.ios.kt:138-146`):

```kotlin
    private fun readData(directory: StorageDirectory, name: String): NSData? {
        val path = filePath(directory, name)
        if (!fileManager.fileExistsAtPath(path)) return null
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSData.dataWithContentsOfFile(path, options = 0u, error = error.ptr)
                ?: throw LibraryStorageException("Could not read \"$name\": ${error.value?.localizedDescription}")
        }
    }
```

OPFS (`FileStorage.wasmJs.kt:229-230`) — the handle is resolved (`getFileHandle`, which *does* fold `NotFoundError`
into null, `:176-181`), then:

```kotlin
private fun readFileBytes(handle: JsAny): Promise<Int8Array?> =
    js("handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) { return new Int8Array(buffer); })")
```

`getFile()` rejects with `NotFoundError` for an entry removed after its handle was taken; `failingAsStorage`
(`:114-125`) turns that into `LibraryStorageException`. `fileInfo` (`:220-227`) and `listEntries` (`:192-206`)
already handle exactly this case; `readFileBytes` is the one that does not.

Sync reads the library for its plan **without** `LibraryFileLock` (HEAD `SyncEngine.kt:268-286`,
`readLocalStates`), so a save, delete or rename by the repositories can land between listing and read.

## The change

Invoke the **`code-style`** skill before the first edit. The rule in all three: a file that is not there *at the
moment of the read* is absent; the question "is it there?" is asked again by the failure itself, not by a second
check.

### JVM (both `JvmFileStorage.kt` copies, kept identical)

```kotlin
    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) readingAsStorage(name) { it.readAllBytes() }?.decodeLibraryText() else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) readingAsStorage(name) { it.readAllBytes() } else null }
    }

    /**
     * [failingAsStorage] for a read, which has one more answer: a file removed between the [File.isFile] check and
     * the read is not there, and null is what the contract says about a file that is not there. Anything else is a
     * file that is there and could not be read. Internal so that the test can hand it the exception.
     */
    internal inline fun <T : Any> readingAsStorage(name: String, read: () -> T): T? = try {
        read()
    } catch (_: NoSuchFileException) {
        null
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not access \"$name\".", exception)
    }
```

**Import `java.nio.file.NoSuchFileException` explicitly.** Kotlin auto-imports `kotlin.io.*`, which has its own
`NoSuchFileException` (a `kotlin.io.FileSystemException`); unqualified, the `catch` would name that class, which
`Files.readAllBytes` never throws, and the fix would silently do nothing.

### iOS (`FileStorage.ios.kt`)

```kotlin
    /**
     * `dataWithContentsOfFile` answers nil for a file that is not there and for one it could not read alike, and only
     * the first of those may come back as null: a file reported as missing is a deletion as far as sync is concerned.
     * A nil for a file that was there a moment ago is asked about again, since a save, a deletion or a sync run can
     * remove it between the two calls.
     */
    private fun readData(directory: StorageDirectory, name: String): NSData? {
        val path = filePath(directory, name)
        if (!fileManager.fileExistsAtPath(path)) return null
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSData.dataWithContentsOfFile(path, options = 0u, error = error.ptr) ?: run {
                if (!fileManager.fileExistsAtPath(path)) return null
                throw LibraryStorageException("Could not read \"$name\": ${error.value?.localizedDescription}")
            }
        }
    }
```

(`memScoped` and `run` are inline, so `return null` returns from `readData`.) Asking `fileExistsAtPath` again rather
than matching `NSFileReadNoSuchFileError` keeps the rule the same as the check before it and needs no platform
constant; a file that was deleted *and* recreated in between is then a real failure of one read, which plan 09
handles as one file's failure.

### OPFS (`FileStorage.wasmJs.kt`)

```kotlin
/**
 * The file's bytes, or `null` for a file removed between its handle and the read: a `NotFoundError` there is a file
 * no longer in the directory, which is what `readFileBytes`' callers call missing, the same way `fileInfo` does.
 */
private fun readFileBytes(handle: JsAny): Promise<Int8Array?> = js(
    """handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) {
        return new Int8Array(buffer);
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)
```

The Kotlin callers (`readText` / `readBytes`, `:79-89`) already handle a null resolution with `?.`.

## Tests

`data/source/local/implementation/src/desktopTest/.../storage/file/JvmFileStorageTest.kt` (the race itself cannot be
set up deterministically against a real file system; the decision can):

1. `` `reads a file removed just before the read as missing` ``:
   `assertNull(fileStorage.readingAsStorage("a.cho") { throw java.nio.file.NoSuchFileException("a.cho") })`.
2. `` `still reports any other failure of a read as a storage failure` ``:
   `assertFailsWith<LibraryStorageException> { fileStorage.readingAsStorage("a.cho") { throw java.nio.file.AccessDeniedException("a.cho") } }`.

Tests 1–2 need `readingAsStorage` to be `internal` (it is, like `retryingWhileDenied`, which the same test class
already calls). The existing `` `returns null for a missing file` `` and
`` `reports a file it cannot read as a failure rather than as missing` `` must still pass unchanged.

iOS and OPFS: no test source set; verified by hand below.

Run `./gradlew :data:source:local:implementation:desktopTest`, then the root unit test command.

## Verification

1. Unit tests above.
2. Compile every target: `./gradlew :data:source:local:implementation:compileKotlinDesktop :data:source:local:implementation:compileKotlinWasmJs :data:source:local:implementation:compileDebugKotlinAndroid :data:source:local:implementation:compileKotlinIosSimulatorArm64`.
3. Forced interleaving on desktop (optional, confirms the before/after end to end): temporarily add
   `delay(3_000)` at the start of `readLocalStates`' per-file read (after the listing, before the read) in a local
   build, connect a test account, start Sync now and delete a song from the app during the pause. **Before:** "The
   library could not be read or written." **After:** the run completes and the song is deleted from Dropbox too.
   Remove the delay.
4. Web: same experiment with `./gradlew :app:web:wasmJsBrowserDevelopmentRun` (same temporary delay) — the deletion
   reaches Dropbox and the run completes.

## Docs

- `data/source/local/implementation/CLAUDE.md:47-54` (the "A read answers null for a file that is **not there**"
  bullet): add after the OPFS sentence: "A file removed between the existence check and the read is not there either:
  the JVM catches `java.nio.file.NoSuchFileException` (not Kotlin's class of the same name), iOS asks
  `fileExistsAtPath` again when the read comes back nil, and OPFS folds a `NotFoundError` from `getFile()` into null,
  as `fileInfo` already did."
- `FileStorage.kt:57-64` KDoc is correct as written; no change.

## Files touched

- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt`
- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing. Touches the same three storage files as 09 (JVM `canHoldFileName`) and 11 (listing/deletion errors), in
different functions; land in any order, 11 right after this one is the natural pairing (both are about what a storage
failure is). Lane E plan 46 (`safari-worker-partial-write`) touches `FileStorage.wasmJs.kt`'s `writeFile`, a
different function — rebase only.
