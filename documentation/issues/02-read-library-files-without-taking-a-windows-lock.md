# 02 — Read library files without taking a Windows lock

## What the user sees

On Windows, a sync run that overlaps a library scan reports files it "could not sync", and a song saved at the same
moment as a rescan fails with "Could not save". The failures are not reproducible on demand: they depend on which
of the 64 files a scan batch is holding open when the writer reaches that name. The user sees a run that never
quite finishes cleanly, and because a run with any failed file keeps the previous `lastSyncedAt`
(`SyncRepositoryImpl.kt:377-383`), Settings goes on saying the library was last synced successfully hours ago
however many times they press **Sync now**.

The same class of failure hits the anti-virus scanners every Windows machine runs: they open a file the moment it
appears, and a rename over it during that window is refused.

## Cause

`data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt:68-74`
(and the byte-identical Android copy at the same lines — `diff` of the two files at HEAD shows one word of KDoc
different and nothing else):

```kotlin
    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readBytes().decodeLibraryText() } else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readBytes() } else null }
    }
```

`kotlin.io.File.readBytes()` opens a `FileInputStream`. On Windows the JDK opens a `FileInputStream` **without**
`FILE_SHARE_DELETE`, so while the stream is open the file cannot be renamed over or deleted. `java.nio`'s channel
factory does pass `FILE_SHARE_DELETE`, which is why `Files.readAllBytes` does not have the problem.

The writer that loses is `writeAtomically`, same file, lines 91-109:

```kotlin
    private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
        val target = file(directory, name).toPath()
        // A name of its own per write, so two writes of one file cannot share a temporary file.
        val temporary = Files.createTempFile(target.parent, TEMPORARY_FILE_PREFIX, TEMPORARY_FILE_SUFFIX)
        try {
            write(temporary.toFile())
            // Flushed to the device before the rename, or a power loss right after could keep the name and lose the bytes.
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Some file systems (network shares, some Android storage) cannot do it in one step; the plain move still
                // never leaves the target truncated, since it copies first and replaces at the end.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
```

A locked target makes `Files.move` throw `java.nio.file.AccessDeniedException`, which is an `IOException` but
**not** an `AtomicMoveNotSupportedException`, so the fallback on line 101 does not catch it and nothing retries.
`failingAsStorage` turns it into a `LibraryStorageException`, which the caller reports as a save that failed or a
file that could not be synced.

`delete` (lines 84-89) has the same exposure and no wrapper at all:

```kotlin
    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val file = file(directory, name)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Could not delete \"$name\".")
        }
    }
```

The two really do overlap. The song scan reads `BATCH_SIZE = 64` files at once
(`SongLocalSourceImpl.kt:139`), the sync run's own hashing pass reads `READ_BATCH_SIZE = 64` at once
(`SyncEngine.kt:634`), and `SyncRepositoryImpl` keeps a **live rescan** going *while* a run writes — the
per-module `CLAUDE.md` says so: "The library counts are kept moving during a run by a live rescan that waits five
times what the previous one took". So on Windows, 64 open `FileInputStream`s at a time are racing the run's
downloads and deletions by design.

## The change

Two independent parts. Do both; the first removes the app's own contribution to the race, the second covers the
scanners the app cannot control.

### 1. Read through `Files.readAllBytes`

```kotlin
    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readAllBytes().decodeLibraryText() } else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readAllBytes() } else null }
    }

    /**
     * Not [File.readBytes], which opens a `FileInputStream`: on Windows the JDK opens one without
     * `FILE_SHARE_DELETE`, so for as long as a scan holds the stream, a rename over that file or a deletion of it is
     * refused - and a scan reads sixty-four files at a time while a sync run is writing. The NIO channel behind
     * `Files.readAllBytes` shares deletion, so the same read leaves the file movable.
     */
    private fun File.readAllBytes(): ByteArray = Files.readAllBytes(toPath())
```

`Files.readAllBytes` throws `NoSuchFileException` (an `IOException`) for a file deleted between the `isFile` check
and the read, where `File.readBytes` threw `FileNotFoundException` (also an `IOException`) — `failingAsStorage`
wraps both into `LibraryStorageException`, so the contract of "null only for a file that is not there" is
unchanged in both shapes. Do **not** try to fold that into null: the storage contract deliberately distinguishes
"missing" from "there and unreadable" because a file reported missing is planned as a deletion that reaches every
device (`:data:source:local:implementation/CLAUDE.md`).

`toPath()` throws `InvalidPathException` on Windows for a name containing `? : * " < > |` — see plan 03. Today
those names are unreachable on this path because `it.isFile` answers false first and the read returns null before
`toPath()` is ever called, and that stays true here, since `isFile` is still the guard. Plan 03 removes the
underlying hazard; this plan does not depend on it.

Apply the identical change to **both** copies of `JvmFileStorage.kt` (`desktopMain` and `androidMain`). They are
kept byte-identical apart from one word of KDoc; keep it that way.

### 2. A bounded retry around the move and the delete

This is **not** a debounce and not a race answered with a window. It is a bounded retry of one documented,
transient operating-system failure — `AccessDeniedException` on Windows, which is what the OS returns while
another process holds a handle without `FILE_SHARE_DELETE`. The state it retries on is the exception itself, which
either stops being thrown or does not; the loop ends either way after a fixed number of attempts and reports the
failure honestly. Nothing about the app's own ordering is being papered over, because part 1 removed the app's own
locks.

```kotlin
    /**
     * Retries [operation] while Windows refuses it because somebody else is holding the file open. An anti-virus
     * scanner opens every file that appears, and a rename over it or a deletion of it is refused for as long as it
     * does - for tens of milliseconds, not for seconds. A bounded retry of one named operating-system failure
     * rather than a wait for a race to settle: the app's own reads share deletion (see `readAllBytes`), so anything
     * still holding the file is outside the process and will let go or will not.
     */
    private fun <T> retryingWhileDenied(operation: () -> T): T {
        repeat(DENIED_RETRIES) { attempt ->
            try {
                return operation()
            } catch (_: AccessDeniedException) {
                if (!escapesDeviceNames) throw ...  // see below
                Thread.sleep(DENIED_RETRY_DELAY_MILLIS shl attempt)
            }
        }
        return operation()
    }
```

Concretely:

- `DENIED_RETRIES = 3` and `DENIED_RETRY_DELAY_MILLIS = 20L`, so the delays are 20 ms, 40 ms and 80 ms and the
  worst case adds 140 ms to one write before the failure is reported as it is today. That is under the frame budget
  the user would notice on a save, and comfortably longer than a scanner's handle.
- Retry **only on Windows**. On Linux, macOS, Android and iOS an `AccessDeniedException` means a permission
  problem that will not clear, and retrying spends 140 ms per file failing a library the app genuinely cannot
  write. `JvmFileStorage` already has the Windows answer in its constructor
  (`escapesDeviceNames`, line 38); rename that parameter to `isWindows`, since it is the same question and will
  now have two callers:

  ```kotlin
  internal class JvmFileStorage(
      private val root: File,
      private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("windows", ignoreCase = true),
  ) : FileStorage {
  ```

  `JvmFileStorageTest.kt:128` constructs `JvmFileStorage(root, escapesDeviceNames = true)`; update it.
- Do not use `Thread.sleep` from `commonMain`. This is `desktopMain` / `androidMain` JVM code, so it is allowed —
  but the enclosing call is already inside `withContext(Dispatchers.IO)` from a `suspend` function, so prefer
  making the helper `suspend` and using `kotlinx.coroutines.delay`, which keeps the IO thread free and stays
  cancellable. Both `writeAtomically` and `delete` are called from suspend contexts, so making
  `writeAtomically` suspend is a local change.

Wrap two call sites:

```kotlin
            retryingWhileDenied {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
```

and in `delete`, replace `File.delete()` — which returns `false` and says nothing about why — with
`Files.deleteIfExists(file.toPath())` inside the same retry, so the reason reaches the log and the retry has
something to catch:

```kotlin
    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val file = file(directory, name)
        // Not File.delete(), whose false says nothing about why: an AccessDeniedException is a file somebody else is
        // holding open and worth retrying, and anything else belongs in the message.
        failingAsStorage(name) { retryingWhileDenied { Files.deleteIfExists(file.toPath()) } }
        Unit
    }
```

Note the behaviour change this brings: `delete` now throws `LibraryStorageException` rather than
`IllegalStateException`. That is the right type — the interface's own KDoc in `FileStorage.kt` says a file that is
there and cannot be written throws `LibraryStorageException` — but check the callers of `deleteLibraryFile` and
`deleteSong` for any `catch (IllegalStateException)` before changing it. If one exists, keep the
`IllegalStateException` and retry around it instead; do not quietly change what a caller catches.

## Tests

`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`.
The Windows behaviour itself cannot be tested on the CI runners this project uses, so the tests pin the parts that
can be:

1. `reads a file without keeping it open` — write a file, start a read, and in the same test move a second
   file over it; on POSIX this passes either way, so assert instead the observable property that matters:
   `readBytes` and `readText` still return the exact bytes and text after the switch to `Files.readAllBytes`
   (extends the existing `round trips bytes` and `writes, lists and reads back text files`).
2. `reports a file it cannot read as a failure rather than as missing` already exists (line 172) and must stay
   green: it is what proves `Files.readAllBytes`'s `AccessDeniedException` on an unreadable file still becomes a
   `LibraryStorageException` rather than null. Re-read it after the change — it is the single most load-bearing
   test here.
3. `returns null for a missing file` (line 164) must stay green: `Files.readAllBytes` on a missing file throws
   rather than returning null, and the `isFile` guard is what keeps the contract.
4. `deletes a file and ignores a missing one` (line 153) must stay green through the
   `Files.deleteIfExists` rewrite.
5. `retries a move the file system refuses` — construct `JvmFileStorage` with `isWindows = true` and a `root`
   whose parent directory is made read-only so the first move fails, restoring it from another coroutine after
   30 ms; assert the write succeeds. If that turns out to be too flaky or impossible on macOS (the CI host), drop
   it and test the helper directly by extracting `retryingWhileDenied` to a private function with an injected
   operation — a counter that throws `AccessDeniedException` twice and then succeeds, asserting three calls and
   one return value, plus a counter that always throws, asserting the exception reaches the caller after exactly
   `DENIED_RETRIES + 1` calls. That is the version to write: it is deterministic and tests the rule rather than
   the file system.

## Verification

```
./gradlew :data:source:local:implementation:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:desktop:run
./gradlew :app:android:assembleDebug
```

Manual:

- Desktop, any OS: import a few hundred songs, then press **Sync now** and pull-to-refresh the song list at the
  same time. No file may be reported as failed.
- **Needs a Windows PC, and this is the check the plan exists for**: with a real anti-virus running, connect sync
  to a Dropbox account holding a few hundred songs and let a first sync run while the songs screen is open and
  being scrolled. Before the change, expect a handful of "files could not be synced" and a `lastSyncedAt` that
  never advances; after it, a clean run. Repeat with the app's own rescan (**Refresh**) pressed during the run.
- **Needs an Android device**: the same change lands in `androidMain`; confirm a large import followed by a sync
  run still behaves, since Android is where `ATOMIC_MOVE` most often falls back.

## Docs

`data/source/local/implementation/CLAUDE.md` — this sentence is where the reading rule belongs and currently says
nothing about how a file is read:

> - Writes are atomic on the three platforms that can be (a `.campfire-<number>.tmp` temporary file of its own per
>   write, named without the target so a name at the file-system limit still saves, flushed to the device and moved
>   over the target on the JVM, `atomically` on iOS), so a crash in the middle of a save cannot truncate a song.

Add, next to it, that the JVM storage reads through `Files.readAllBytes` rather than a `FileInputStream` so that a
scan does not lock the files a concurrent write is moving over, and that the move and the delete retry a bounded
number of times on a Windows `AccessDeniedException`.

And this sentence, which lists the Windows-specific behaviour and is where the renamed constructor parameter now
has two jobs:

> - The JVM storage removes its own temporary files older than an hour on first touching each directory. On Windows
>   it stores device names such as `con.cho` with a leading underscore and reports the ordinary library name back.

No user-facing documentation changes: nothing here is visible when it works.

## Files touched

- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing. It shares `JvmFileStorage.kt` with plan 03 and renames `escapesDeviceNames` to `isWindows`, which plan 03
also reads — land 02 first and rebase 03 onto it.

## Rules

- Load the `code-style` skill before the first edit.
- The two `JvmFileStorage.kt` copies stay byte-identical apart from the one word of KDoc naming the other platform.
- A race is answered with state, not with a debounce window. State the retry's justification in the KDoc exactly as
  above: it is a bounded retry of one named OS failure after the app's own locks have been removed, not a wait for
  the app's own ordering to settle.
- No new UI strings.
