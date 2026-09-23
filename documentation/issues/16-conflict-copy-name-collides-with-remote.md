# 16 — A conflict copy takes a name that is only free on this device, and swaps two songs' identities

**Severity:** wrong song under a name on every device, setlists pointing at the wrong song, two conflicts reported
(all platforms, sync) · **Area:** `:data:repository:implementation` (`sync/SyncEngine.kt`), `:data:source:local:api`
(`LibraryFileLocalSource.kt`), `:data:source:local:implementation` (`FileNames.kt`, `LibraryFileLocalSourceImpl.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The trace below is exact against HEAD, and the engine test is the confirmation.

> **Work in progress at review time.** `SyncEngine.kt`, `SyncEngineTest.kt`, `FakeSyncProvider.kt`, the root
> `CLAUDE.md`, `data/repository/implementation/CLAUDE.md` and `documentation/sync.md` had uncommitted modifications by
> another agent when this plan was written. Everything quoted below is from `git show HEAD:<path>`; re-locate each
> block in whatever is committed when the plan is executed and apply the change there.

Reviewer finding 3-sync#2.

## What the user sees

Device A and device B both edit `wonderwall.cho` between syncs. Meanwhile an earlier conflict on B (or any song that
happens to be called that) left a file `wonderwall (2).cho` in the cloud folder, which A has not downloaded yet. A
syncs:

- A writes B's version of Wonderwall as its conflict copy under `wonderwall (2).cho` — free on A, taken in the cloud.
- The real `wonderwall (2).cho` from the cloud is then "changed on both sides" as far as A can tell, so A resolves it
  too: **A's copy (B's Wonderwall) is uploaded over the cloud's `wonderwall (2).cho`**, and the cloud's version comes
  down as `wonderwall (3).cho`.
- Every device ends up with `wonderwall (2).cho` holding different content than before; a setlist entry that named
  `wonderwall (2).cho` now opens another version; Settings reports two conflicts where there was one.

Nothing is lost — every version exists under some name — but names are identities in this app (setlists point at
them), and a name now means another song.

## Cause

The copy's name is chosen against the local folder only. HEAD `SyncEngine.kt:534`, in `resolveWith`:

```kotlin
        val copyName = libraryFileLock.withLock { libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote) }
```

`LibraryFileLocalSourceImpl.writeLibraryFileToFreeName` (`:42-46`) → `FileStorage.uniqueName(…, ::arrivingCollisionSuffix)`
(`FileNames.kt:55-75`), which asks `exists()` on this device and nothing else.

Order of events in one pass (HEAD `SyncEngine.kt:643-651`: `Resolve` is group 0, `Download` group 1):

1. `Resolve(wonderwall.cho)` → `resolveWith` writes the copy locally as `wonderwall (2).cho`, uploads the local
   version over `wonderwall.cho`, then uploads the copy with `expectedRevision = null` (`:560`) — the cloud already
   has that name, so `RemoteWriteResult.Conflict`, and the copy gets no index entry (`:561-563`), but the summary still
   says `conflicts = listOf("wonderwall (2).cho")`.
2. `Download(wonderwall (2).cho)` (planned: remote-only, no index entry) → `download` (`:398-436`) reads the local
   file — the copy — finds it differs from the remote and from the (absent) index entry (`:411`), and calls `resolve`:
   the local copy keeps the name and goes up over the remote file; the remote file comes down as
   `wonderwall (3).cho`. A second conflict.

## The change

Invoke the **`code-style`** skill before the first edit. The copy's name must be free **on both sides**: not held by
a local file, and not held by any file in this pass's remote listing (folded, since the service may take two
spellings for one name).

### `:data:source:local:implementation` — `FileNames.kt` `uniqueName`

Add a parameter, defaulting to "nothing else is taken":

```kotlin
 * @param isTaken Names that are not free for a reason the directory cannot see - a file of that name elsewhere that
 *   will arrive here. Consulted for every candidate alongside the directory itself.
 */
internal suspend fun FileStorage.uniqueName(
    directory: StorageDirectory,
    desired: String,
    collisionSuffix: (index: Int) -> String = ::normalizedCollisionSuffix,
    currentName: String? = null,
    isTaken: (name: String) -> Boolean = { false },
): String {
    fun isOwnName(candidate: String) = candidate.equals(currentName, ignoreCase = true)
    if (!isOwnName(desired) && !isTaken(desired) && !exists(directory, desired)) return desired
    // Only a rename needs the exact names (see currentName): for anything else, exists() already says all a listing would.
    val takenNames = if (currentName == null) emptySet() else listNames(directory).toHashSet()
    suspend fun isFree(candidate: String) =
        candidate !in takenNames && !isTaken(candidate) && (isOwnName(candidate) || !exists(directory, candidate))
    ...unchanged...
```

(If plan 14 has landed, `isOwnName` reads `currentName != null && candidate.isSameFileNameAs(currentName)` — keep that.)

### `:data:source:local:api` — `LibraryFileLocalSource.kt:41-46`

```kotlin
    /**
     * Writes [bytes] under the first free variant of [desiredName] (" (2)", " (3)"…) and returns the name it got.
     * This is what an incoming copy of a file that changed on both sides lands under: nothing is ever overwritten
     * implicitly, here as everywhere else in the library. A name [isTaken] answers yes to is not free either, which is
     * how sync keeps the copy off a name the cloud folder already holds for a file that has not come down yet.
     */
    suspend fun writeLibraryFileToFreeName(
        kind: LibraryFileKind,
        desiredName: String,
        bytes: ByteArray,
        isTaken: (name: String) -> Boolean = { false },
    ): String
```

`LibraryFileLocalSourceImpl.kt:42-46` passes it through
(`fileStorage.uniqueName(kind.directory, desiredName, ::arrivingCollisionSuffix, isTaken = isTaken)`; the override
does not repeat the default).

### `SyncEngine.kt`

`resolveWith` (HEAD `:522-570`) gets the pass's remote listing, and both callers pass theirs (`resolve` at HEAD `:518`
and `download` at `:434` both already have `remoteFiles`):

```kotlin
    private suspend fun resolveWith(
        provider: SyncProvider,
        key: SyncKey,
        revision: String,
        localBytes: ByteArray,
        remote: ByteArray,
        remoteFiles: Map<SyncKey, RemoteFileState>,
    ): OperationOutcome {
        if (remote.contentEquals(localBytes)) { ...unchanged... }
        // Free on both sides, not only here: the listing may hold a file under the copy's name that has not come down
        // yet - another device's copy, or a song that simply has that name - and is planned as a download later in
        // this pass. A copy written under it here would be taken for that file changed on this device, go up over it,
        // and push the file itself on to the next number. Folded, as the service may take two spellings for one name.
        val remoteNames = remoteFiles.keys.filter { it.kind == key.kind }.mapTo(hashSetOf()) { it.folded().name }
        // Under the lock like every other write: the repositories pick a free name and write under it in two steps too,
        // and would otherwise be given the one this is.
        val copyName = libraryFileLock.withLock {
            libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote) { name ->
                SyncKey(kind = key.kind, name = name).folded().name in remoteNames
            }
        }
        ...rest unchanged...
```

`folded()` (HEAD `:88`) is private to the file and reachable from the class.

What this does not cover, and why that is acceptable: a file created in the cloud folder under the copy's name
*after* this pass listed it. The copy's upload (`expectedRevision = null`) is then refused as a conflict, the copy
stays local without an index entry, and the next run resolves it as two different files under one name — one more
numbered copy, no identity swap within a run that knew better. Checking the service at write time would be a request
per conflict for a race measured in seconds.

## Tests

`data/repository/implementation/src/commonTest/.../sync/FakeLibraryFileLocalSource.kt` — `writeLibraryFileToFreeName`
takes the new parameter and skips taken names:

```kotlin
    override suspend fun writeLibraryFileToFreeName(
        kind: LibraryFileKind,
        desiredName: String,
        bytes: ByteArray,
        isTaken: (name: String) -> Boolean,
    ): String {
        ...
            .first { SyncKey(kind = kind, name = it) !in files && !isTaken(it) }
```

`SyncEngineTest.kt`, new test `` `a conflict copy is not given a name the cloud folder already holds` ``:

- local: `song("x")` = `HERE`; remote: `song("x")` = `THERE`, `song("x (2)")` = `"Another song".encodeToByteArray()`;
  index: `song("x")` with `localHash(ORIGINAL)` and `remoteRevision = "r0"` (`indexOf(song("x") to ORIGINAL)`), no entry
  for `x (2)`.
- Assert: `Result.Completed`; `summary.conflicts == listOf("x (3).cho")`; locally `x.cho` = `HERE`, `x (2).cho` =
  `"Another song"` (downloaded, untouched), `x (3).cho` = `THERE`; remotely `x.cho` = `HERE`, `x (2).cho` still
  `"Another song"` **at revision `r1`** (never overwritten), `x (3).cho` = `THERE`.

`data/source/local/implementation/src/desktopTest/.../storage/file/JvmFileStorageTest.kt`:
`` `skips a name that is taken elsewhere` ``: with `a.cho` written, `uniqueName(SONGS, "a.cho", ::arrivingCollisionSuffix)
{ it == "a (2).cho" }` == `"a (3).cho"` (import `arrivingCollisionSuffix`).

## Verification

1. Tests above; root unit test command.
2. Two devices (desktop + web is the quickest pair), one Dropbox test account:
   1. Sync both. On B, create a song and rename its file (by hand in the folder, desktop) to `x (2).cho`; sync B only.
   2. Edit `x.cho` differently on A and on B; sync B, then A.
   3. **Before:** A's Settings names two conflicts; `x (2).cho` on dropbox.com now holds B's Wonderwall text.
      **After:** one conflict, `x (3).cho`; `x (2).cho` on dropbox.com unchanged (same revision in its version
      history).

## Docs

- Root `CLAUDE.md` (HEAD `:359-361`) — "the incoming one lands next to it as ` (2)`" → "…as ` (2)` — or the first
  number free both on this device and in the cloud folder, so that it never takes the name of a file still on its way
  down —".
- `documentation/sync.md:43-44` — "the incoming one lands next to it as ` (2)`" → "…as ` (2)` (or the next number
  that is free everywhere)".
- `data/source/local/api/CLAUDE.md:38-39` — add "and on a name sync says is free in the cloud folder too".
- `data/repository/implementation/CLAUDE.md` (HEAD `:101-103`, "A conflict's incoming version is written next to the
  local one…") — add: "under a name free on both sides: the pass's remote listing is passed to the free-name search,
  since a file under the copy's name that has not come down yet would otherwise be taken for the copy changed here."

## Files touched

- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeLibraryFileLocalSource.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `CLAUDE.md`, `documentation/sync.md`, `data/source/local/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`

## Depends on

- Land **after 09** (both edit `SyncEngine.kt`; this diff is smaller and easier to rebase).
- Shares `uniqueName` with plan 14 (signature vs. `isOwnName`) — whichever lands second rebases.
- Must be rebased onto the other agent's committed work in `SyncEngine.kt` and the docs (see the note at the top).
