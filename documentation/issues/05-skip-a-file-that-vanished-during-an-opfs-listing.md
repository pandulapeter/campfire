# 05 — Skip a file that vanished during an OPFS listing

## What the user sees

On the web build, a sync run that deletes files makes the song list go red. The library counts are kept moving
during a run by a live rescan (`SyncRepositoryImpl`'s `liveRescanPauseAfter`), so while the engine is deleting a
handful of songs the app is listing the songs directory at the same time — and on a browser, both share the one
thread and interleave at every `await`. If a file is removed between the moment the listing enumerates the
directory and the moment it asks that entry for its size, the whole listing rejects. The repository turns that into
`DataState.Failure`, and the user sees the error state over a library that is perfectly fine, in the middle of a
sync that is working.

The same rejection can come from the conflict path with nothing deleted by the user at all:
`SyncEngine.discardCopy` removes a conflict copy it has just written, and a rescan running at that moment sees the
entry and then does not.

Pressing **Refresh** fixes it, so it reads as a flaky web build rather than as a bug with a cause.

## Cause

`data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt:183-196`,
verified at HEAD `984861e4`:

```kotlin
/** Every file of the directory as name, size and last modification time, separated by control characters. */
private fun listEntries(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var field = String.fromCharCode(0);
        var entries = [];
        for await (var entry of directory.entries()) {
            if (entry[1].kind === 'file') {
                var file = await entry[1].getFile();
                entries.push(entry[0] + field + file.size + field + file.lastModified);
            }
        }
        return entries.join(String.fromCharCode(1));
    })()"""
)
```

`entry[1].getFile()` on line 190 rejects with a `NotFoundError` `DOMException` when the file has been removed since
`directory.entries()` handed the entry out. The rejection escapes the `for await` loop and rejects the whole
promise, so `list()` — lines 42-57 — fails entirely:

```kotlin
    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.Default) {
        // One string instead of a handle per file: crossing the Kotlin/JS boundary for every entry would be far slower.
        listEntries(directoryHandle(directory)).await()?.toString().orEmpty()
```

Note that `list` is one of the few calls on this class that is **not** wrapped in `failingAsStorage`, so the
rejection surfaces as the plain `Exception` the coroutines library makes of a rejected promise (`failingAsStorage`'s
own KDoc, lines 107-113, describes exactly that shape) rather than as a `LibraryStorageException`. Either way the
song scan's caller sees a failed read.

The single-file paths already get this right and are the precedent to follow — `getFileHandle`, lines 172-181:

```kotlin
/**
 * Resolves to `null` instead of rejecting with a `NotFoundError` when the file is not there. Every other rejection is
 * passed on: a file that is there but cannot be reached is not the same as a missing one.
 */
private fun getFileHandle(parent: JsAny, name: String): Promise<JsAny?> = js(
    """parent.getFileHandle(name).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)
```

and `removeEntry`, lines 250-255, does the same for a deletion.

## The change

Skip the one entry, in JavaScript, inside the same `js(...)` block. A file that was there when the directory was
enumerated and is gone by the time it is asked for its size is a file that is not in the directory — which is
exactly what leaving it out of the listing says.

```kotlin
/**
 * Every file of the directory as name, size and last modification time, separated by control characters.
 *
 * An entry that is gone by the time it is asked for its file is left out rather than failing the listing: a sync
 * run deletes files while the live rescan is listing the same directory, and on one thread the two interleave at
 * every `await`. A `NotFoundError` there says the file is not in the directory any more, which is what leaving it
 * out of the listing says too. Every other rejection still fails the listing, since a file that is there and
 * cannot be reached must never be reported as absent - sync plans a deletion for a file it cannot see.
 */
private fun listEntries(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var field = String.fromCharCode(0);
        var entries = [];
        for await (var entry of directory.entries()) {
            if (entry[1].kind === 'file') {
                var file;
                try { file = await entry[1].getFile(); }
                catch (error) { if (error && error.name === 'NotFoundError') continue; throw error; }
                entries.push(entry[0] + field + file.size + field + file.lastModified);
            }
        }
        return entries.join(String.fromCharCode(1));
    })()"""
)
```

Two constraints from `app/web/CLAUDE.md` and the root `CLAUDE.md` that this respects and that must not be broken
while editing:

- **One crossing of the Kotlin/Wasm boundary per operation, not one per element.** The root `CLAUDE.md` says OPFS
  is reached through `js(...)` blocks rather than typed wrappers because "one crossing of the Kotlin/Wasm boundary
  per operation is far cheaper than one per element". So the `catch` belongs inside the JavaScript, not in Kotlin
  around a per-entry call. Do not restructure `listEntries` into a handle-per-file loop.
- **A Kotlin lambda cannot be passed into a `js(...)` block.** Nothing here needs one; the whole decision is made
  in JavaScript.

Also fix the same hazard one line further on, in `fileInfo` (lines 206-208), which `info()` calls after
`fileHandle()` has already resolved:

```kotlin
private fun fileInfo(handle: JsAny): Promise<JsString?> =
    js("handle.getFile().then(function (file) { return file.size + String.fromCharCode(0) + file.lastModified; })")
```

A file deleted between `getFileHandle` and `getFile` rejects here too, and `info()` is wrapped in nothing, so it
throws where the contract says a missing file is null. `info` is used by `SongLocalSourceImpl.loadSong` after a
save, and by `readSong` for a file the listing just named. Give it the same treatment as `getFileHandle`:

```kotlin
/**
 * The size and the last modification time of one file, separated by the same control character `listEntries` uses.
 * Resolves to `null` for a file deleted between the handle and the question, which `info`'s contract calls missing.
 */
private fun fileInfo(handle: JsAny): Promise<JsString?> = js(
    """handle.getFile().then(function (file) {
        return file.size + String.fromCharCode(0) + file.lastModified;
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)
```

`info()` already returns null when the promise resolves to null (`fileInfo(handle).await()?.toString()` at line
65), so no Kotlin change is needed there.

`listEntryNames` (lines 198-204) needs nothing: it iterates `directory.keys()` and never opens a file.

### What is deliberately not changed

`getFileHandle`, `readFileBytes`, `writeFile` and `removeEntry` keep their current behaviour. In particular
`readFileBytes` must go on rejecting: a read that answers null for a file it could not open would be reported as a
missing file, and the module's own `CLAUDE.md` states why that is dangerous — "a file reported as missing is
planned as a deletion, and that deletion reaches every other device".

## Tests

There are none to write. `commonTest` runs on the desktop target and `OpfsFileStorage` is `wasmJsMain`; the
module's `CLAUDE.md` says so outright:

> Exercises the file system implementation the JVM platforms share against a temporary directory. The iOS and web
> implementations of the same interface can only be verified by running the app.

State that in the commit message. The existing suite must stay green because nothing outside `wasmJsMain` is
touched:

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

```
./gradlew :app:web:wasmJsBrowserDevelopmentRun
./gradlew :app:web:wasmJsBrowserDistribution
```

A syntax error inside a `js(...)` string is a runtime failure, not a compile one, so the build passing proves
nothing here. Open the app and exercise it:

1. Import a few hundred songs into the web build so a scan takes long enough to overlap something.
2. With the songs screen open, delete a song from the overflow menu while the list is still filling. The list must
   not go to the error state.
3. **Needs a Dropbox account:** connect sync in the browser, delete twenty songs from the Dropbox folder in
   another tab, and press **Sync now** with the songs screen open. Before the change, expect the library to flash
   into its failure state during the run; after it, the counts simply tick down.
4. Watch the browser console throughout: no unhandled rejection, and no `NotFoundError` reaching Kotlin.
5. Confirm the opposite still holds — that a real failure is still a failure. In the devtools console, take an
   exclusive lock on the origin's OPFS or deny storage, and confirm the app still reports the library as unreadable
   rather than as empty. (If that cannot be staged, at minimum re-read the `readFileBytes` and `getFileHandle`
   blocks and confirm they are unchanged.)

The `MEMORY.md` note "Web target verification" has the recipe for driving the web build in a hidden Chrome tab,
including the rAF override and the Web Lock pitfalls; use it rather than re-deriving it.

## Docs

`data/source/local/implementation/CLAUDE.md` — this sentence states the contract this change operates at the edge
of, and needs the listing's new exception spelled out so that nobody later reads it as permission to fold a read
into null:

> - A read answers null for a file that is **not there** and for nothing else. A file that is there and cannot be
>   read or written throws `LibraryStorageException` (from `:data:source:local:api`) on every platform: iOS asks
>   `dataWithContentsOfFile` for its `NSError` rather than taking its nil as absence, the JVM wraps the
>   `IOException`, and OPFS folds only a `NotFoundError` into null.

Add: on OPFS, an entry that disappears between the directory listing and the question about its size is left out
of the listing rather than failing it, because a sync run deletes files while the live rescan is listing.

`app/web/CLAUDE.md` describes the OPFS-specific behaviour of this build; add the same sentence there if it carries
a list of the OPFS quirks. Nothing in the root `CLAUDE.md` or in `documentation/` is contradicted.

## Files touched

- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `data/source/local/implementation/CLAUDE.md`
- `app/web/CLAUDE.md` (if it lists the OPFS quirks)

## Depends on

Nothing.

## Rules

- Load the `code-style` skill before the first edit.
- Keep the OPFS work inside the `js(...)` blocks: one boundary crossing per operation, never one per file.
- No Kotlin lambda may be passed into a `js(...)` block.
- No new UI strings.
