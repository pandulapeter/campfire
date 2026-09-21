# 12 · The web app cannot save anything on Safari before 26, and a write that fails leaves an empty file behind on every browser

**Severity:** data loss (web; certain on every iPhone and iPad on iOS 18 and every Mac whose Safari is older than 26, and on any browser whenever a write of a new file is refused) · **Area:** `:data:source:local:implementation` (`OpfsFileStorage`), `:app:web` (a new worker script next to `index.html`) · **Decision:** fall back to a dedicated Web Worker using `createSyncAccessHandle()` where `createWritable()` is missing; and in every case fix the write order so a failed write never leaves an empty file behind.

## Symptom

1. Open the web build in Safari 18.2–18.x (iOS 18, macOS 14/15 — until the App Store builds exist this is what every
   Apple user is sent to). The app starts, because Wasm GC and `navigator.storage.getDirectory()` are both there.
2. The first run's demo import fails, and `preferences/preferences.json` is left behind with **zero bytes**, so the
   next start is no longer a first run and the demo songs are never planted.
3. Write a song in the editor and press Save: "Could not save the song". Press it again: the same, and the library now
   holds `title.cho` and `title_2.cho`, both empty (the first attempt took the name, so `uniqueName` numbers the
   second). Every attempt adds one. With sync connected the empty files are uploaded.
4. On Chrome or Firefox the same leftover appears whenever the write of a **new** file is refused for any other
   reason: storage quota exceeded (Chromium reports it from `close()`), or Firefox's `NoModificationAllowedError`
   when two writers want one file.

## Cause

`data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
(line numbers as of `29820b93`):

```kotlin
// :86-94
failingAsStorage(name) { writeFileText(requireFileHandle(directory, name), text).await() }
// :113-114  requireFileHandle -> fileHandle(directory, name, create = true)   <- the file exists from here on
// :198-205  writeFileText     -> js("handle.createWritable().then(...)")       <- TypeError where there is none
// :154-155  isOpfsAvailable() only asks for navigator.storage.getDirectory
```

Two separate faults:

- **The API is not there.** Browser facts, checked against MDN's browser-compat-data on 2026-09-21:
  `FileSystemFileHandle.createWritable()` is Chrome 86, Firefox 111, **Safari 26** (iOS Safari mirrors it).
  `createSyncAccessHandle()` is Chrome 102, Firefox 111, **Safari 15.2**, exists **only in a dedicated worker** and
  only for OPFS files; its `close`/`flush`/`getSize`/`truncate` became synchronous in Safari 16.4 / Chrome 108, which
  is older than any Safari that can run the app at all (Wasm GC is 18.2), so the worker may call them synchronously.
  On Safari < 26, `handle.createWritable` is `undefined`, the `js(...)` body throws a `TypeError`, and
  `failingAsStorage` turns it into a `LibraryStorageException` — after `getFileHandle(create: true)` made the file.
- **Created first, written second.** Nothing removes the entry `create: true` made when the write after it fails. The
  existing abort only covers a rejected `write()`; a rejected `createWritable()` or `close()` is not covered at all.

## Fix

The order below matters only in that step 1 (the script) has to exist before step 2 is tried in a browser.

### 1. New file `app/web/src/wasmJsMain/resources/opfs-writer.js`

Everything in `src/wasmJsMain/resources` is served by the development server and copied into
`build/dist/wasmJs/productionExecutable` next to `index.html` (that is how `icon-192.png` gets there), so the script
is reachable as the relative URL `opfs-writer.js` in both, and under `/campfire/` on the deployment.

```js
/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
'use strict';

// How the library is written in a browser that has no FileSystemFileHandle.createWritable(), which is every Safari
// before 26. What those have instead is createSyncAccessHandle(), and that exists only inside a dedicated worker -
// which is the whole reason this file is one. OpfsFileStorage (in :data:source:local:implementation) starts it on
// the first write and never in a browser that has createWritable().
//
// A request is { id, path: [directory segments], name, data: string | Int8Array } and is answered by { id } or by
// { id, error: <the DOMException's name>, message }.

var directories = new Map();

// An access handle is an exclusive lock on its file, so a second write of the same file started while the first is
// awaiting its handle would be refused with a NoModificationAllowedError. The writes are small; running them one
// after another costs nothing and makes that impossible.
var queue = Promise.resolve();

self.onmessage = function (event) {
    var request = event.data;
    queue = queue.then(function () { return write(request); }).then(
        function () { self.postMessage({ id: request.id }); },
        function (error) {
            self.postMessage({
                id: request.id,
                error: (error && error.name) || 'Error',
                message: String((error && error.message) || error),
            });
        }
    );
};

async function directory(path) {
    var key = path.join('/');
    if (!directories.has(key)) {
        var handle = await navigator.storage.getDirectory();
        for (var index = 0; index < path.length; index++) {
            handle = await handle.getDirectoryHandle(path[index], { create: true });
        }
        directories.set(key, handle);
    }
    return directories.get(key);
}

async function write(request) {
    var parent = await directory(request.path);
    var bytes = typeof request.data === 'string'
        ? new TextEncoder().encode(request.data)
        : new Uint8Array(request.data.buffer, request.data.byteOffset, request.data.byteLength);
    var existed = true;
    var file;
    try {
        file = await parent.getFileHandle(request.name);
    } catch (error) {
        if (!error || error.name !== 'NotFoundError') throw error;
        existed = false;
        file = await parent.getFileHandle(request.name, { create: true });
    }
    try {
        var access = await file.createSyncAccessHandle();
        try {
            // Written over the old content and cut to length afterwards, rather than emptied first: there is no swap
            // file behind an access handle, so a tab killed between the two calls leaves the new text with the end of
            // a longer old one after it, where the other order would leave an empty song.
            var written = access.write(bytes, { at: 0 });
            if (written !== bytes.byteLength) {
                throw new DOMException('Only ' + written + ' of ' + bytes.byteLength + ' bytes were written.', 'QuotaExceededError');
            }
            access.truncate(bytes.byteLength);
            access.flush();
        } finally {
            access.close();
        }
    } catch (error) {
        // Only what this request created is taken back. A file that was there before keeps whatever it holds.
        if (!existed) {
            try { await parent.removeEntry(request.name); } catch (ignored) { }
        }
        throw error;
    }
}
```

Nothing else in `app/web` changes for it:

- **`finishWebDistribution`** needs no edit. Its byte total counts `*.wasm` only, and the page's `fetch` wrapper never
  sees the script, since `new Worker(url)` does not go through `window.fetch`. The script is a few hundred bytes of a
  write path, not part of the start, so it must **not** be added to the progress total or preloaded.
  `compressibleSuffixes` already holds `.js`, and `web-publish.yml`'s `rsync --delete` copies the whole folder.
- Do **not** inline it as a Blob URL instead: the page would work, but a `blob:` worker is the first thing a future
  Content-Security-Policy would forbid, and a separate file is what the task decision asks for.

### 2. `FileStorage.wasmJs.kt` — one write path, the fallback, and the clean-up

Replace `writeText`, `writeBytes`, `requireFileHandle`, `writeFileText` and `writeFileBytes`; drop the `create`
parameter of `fileHandle` and of the `getFileHandle` js function (after this change nothing in Kotlin creates a file
through it — `create: true` lives inside the two write functions only, which is the point). New imports:
`kotlinx.coroutines.Job` and `kotlin.js.toJsString` (if the compiler asks for it; it is in `kotlin.js`).

Inside `OpfsFileStorage`:

```kotlin
    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            awaitWrite(directory, name)
            fileHandle(directory, name)?.let { readFileBytes(it).await()?.toByteArray()?.decodeLibraryText() }
        }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            awaitWrite(directory, name)
            fileHandle(directory, name)?.let { readFileBytes(it).await()?.toByteArray() }
        }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = write(directory, name, text.toJsString())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = write(directory, name, bytes.toInt8Array())

    private suspend fun write(directory: StorageDirectory, name: String, data: JsAny) = withContext(Dispatchers.Default) {
        requireValidFileName(name)
        failingAsStorage(name) {
            writing(directory, name) {
                if (canCreateWritable) {
                    writeFile(directoryHandle(directory), name, data).await()
                } else {
                    writeFileInWorker(writerHost, WRITER_SCRIPT, directory.pathSegments.joinToString(PATH_SEPARATOR), name, data).await()
                }
            }
        }
        Unit
    }

    /** Asked once: the answer cannot change while the page is open, and every write needs it. */
    private val canCreateWritable by lazy { hasCreateWritable() }

    /** The worker behind the fallback and the requests it has not answered yet, see [writeFileInWorker]. */
    private val writerHost by lazy { createWriterHost() }

    /**
     * The writes that are going on, by file. Two writers of one file do not work anywhere: Firefox refuses the second
     * `createWritable()` outright, Chromium lets the one that closes last win whichever was asked for last, and an
     * access handle is exclusive by definition. A read waits as well, because the fallback writes in place and a read
     * in the middle of that would see the new text followed by the end of the old one. The browser has one thread, so
     * nothing can come between finding the map empty and putting the marker in.
     */
    private val writesInFlight = mutableMapOf<Pair<StorageDirectory, String>, Job>()

    private suspend fun awaitWrite(directory: StorageDirectory, name: String) {
        while (true) {
            writesInFlight[directory to name]?.join() ?: return
        }
    }

    private suspend fun <T> writing(directory: StorageDirectory, name: String, write: suspend () -> T): T {
        awaitWrite(directory, name)
        val marker = Job()
        writesInFlight[directory to name] = marker
        return try {
            write()
        } finally {
            writesInFlight.remove(directory to name)
            marker.complete()
        }
    }
```

`fileHandle` becomes:

```kotlin
    private suspend fun fileHandle(directory: StorageDirectory, name: String): JsAny? {
        requireValidFileName(name)
        return getFileHandle(directoryHandle(directory), name).await()
    }
```

(and its two other callers, `info` and `exists`, lose the `create = false` argument), with two constants added to
the companion object:

```kotlin
        /** Relative to the page, which is where `:app:web` puts it: next to `index.html`. */
        const val WRITER_SCRIPT = "opfs-writer.js"
        const val PATH_SEPARATOR = "/"
```

The top-level js functions. `getFileHandle` loses `create` (`parent.getFileHandle(name).catch(...)`, KDoc
unchanged); `writeFileText` and `writeFileBytes` are replaced by:

```kotlin
/** Safari has it from version 26 on; before that the only way to write a file is [writeFileInWorker]. */
private fun hasCreateWritable(): Boolean =
    js("typeof FileSystemFileHandle !== 'undefined' && typeof FileSystemFileHandle.prototype.createWritable === 'function'")

/**
 * Creates the file or replaces its content, [data] being a string or an `Int8Array`. The content only replaces the
 * file when the writable is closed, so a write that fails leaves a file that was there as it was.
 *
 * Whether the file was there is asked before it is created, and one that this call created is removed again when
 * anything after that fails - `createWritable()`, the write, or the `close()` that Chromium reports a full disk from.
 * Without that, every refused save would leave an empty song in the library, under a name the next attempt then has
 * to avoid. A writable holds a lock on its file until it is closed or aborted, so it is aborted before the failure
 * is passed on: left open, it would make every later write and the deletion of that file fail as well.
 */
private fun writeFile(parent: JsAny, name: String, data: JsAny): Promise<JsAny?> = js(
    """(async function () {
        var existed = true;
        var handle;
        try {
            handle = await parent.getFileHandle(name);
        } catch (error) {
            if (!error || error.name !== 'NotFoundError') throw error;
            existed = false;
            handle = await parent.getFileHandle(name, { create: true });
        }
        try {
            var writable = await handle.createWritable();
            try {
                await writable.write(data);
                await writable.close();
            } catch (error) {
                try { await writable.abort(); } catch (ignored) { }
                throw error;
            }
        } catch (error) {
            if (!existed) {
                try { await parent.removeEntry(name); } catch (ignored) { }
            }
            throw error;
        }
        return null;
    })()"""
)

private fun createWriterHost(): JsAny = js("({ worker: null, nextId: 1, pending: new Map() })")

/**
 * The same write where there is no `createWritable()`: handed to `opfs-writer.js`, a dedicated worker, because
 * `createSyncAccessHandle()` exists nowhere else. The worker is started by the first write that needs it, and the
 * promise is settled by its reply, matched through the request's id - a Kotlin lambda cannot be handed into a
 * `js(...)` block, so the bookkeeping of who is waiting for what lives in [host] on the JavaScript side.
 *
 * A worker that fails as a whole (the script did not load, or it threw outside a request) rejects everything that
 * was waiting and is forgotten, so the next write starts a new one rather than waiting for an answer that cannot
 * come. The event is cancelled there because an unhandled worker error is reported on the page as well, where the
 * loading screen would take it for a failed start.
 */
private fun writeFileInWorker(host: JsAny, script: String, path: String, name: String, data: JsAny): Promise<JsAny?> = js(
    """new Promise(function (resolve, reject) {
        if (host.worker === null) {
            var worker = new Worker(script);
            worker.onmessage = function (event) {
                var waiting = host.pending.get(event.data.id);
                if (!waiting) return;
                host.pending.delete(event.data.id);
                if (event.data.error) {
                    var error = new Error(event.data.message);
                    error.name = event.data.error;
                    waiting.reject(error);
                } else {
                    waiting.resolve(null);
                }
            };
            worker.onerror = function (event) {
                event.preventDefault();
                if (host.worker === worker) host.worker = null;
                worker.terminate();
                var error = new Error('The OPFS writer stopped: ' + (event.message || 'it could not be loaded.'));
                host.pending.forEach(function (waiting) { waiting.reject(error); });
                host.pending.clear();
            };
            host.worker = worker;
        }
        var id = host.nextId++;
        host.pending.set(id, { resolve: resolve, reject: reject });
        host.worker.postMessage({ id: id, path: path.split('/'), name: name, data: data }, typeof data === 'string' ? [] : [data.buffer]);
    })"""
)
```

Notes for whoever carries this out:

- The `Int8Array` is transferred rather than copied. That is safe because `bytes.toInt8Array()` has just made it and
  nothing else holds it; do not start transferring a buffer that came from anywhere else.
- A `new Worker(...)` that throws does so inside the promise's executor, so it is a rejection like the rest and
  reaches `failingAsStorage` as a `JsException`. **Every** failure of either path therefore ends as the same
  `LibraryStorageException("Could not access …")` the callers already handle; no new exception type, no new string.
- Do not route reads, `list`, `info`, `exists` or `delete` through the worker. They work on the main thread in every
  Safari that can run the app, and the worker would only add a round trip to each.
- Do not reach for `FileSystemFileHandle.move()` (Safari 15.2+) to get a temp-file-and-rename write in the worker:
  whether `move` replaces an existing target was never specified, and it cannot be checked without every browser at
  hand. The in-place write above is the honest version of what that API guarantees.

### 3. What the exclusive lock means (no code; this is the reasoning the KDoc above condenses)

- **Two writes of one file at once:** cannot reach the browser. `writing` lets the second wait for the first on the
  Kotlin side, and the worker's `queue` runs requests one after another besides, so a
  `NoModificationAllowedError` from an access handle (or from Firefox's exclusive writable) has no way to happen
  inside one tab. The later write wins, which is what the caller that came later asked for.
- **A read during a write:** waits for it (`awaitWrite`). `getFile()` itself takes no lock in any engine, so without
  the wait it would succeed and, on the fallback only, could return a torn snapshot.
- **A second tab** is the one writer this cannot see. Plan 49 (a Web Lock at start, the second tab gets a page saying
  so) is what closes that; until it lands, a second tab's write of the same file at the same moment is refused with
  a `NoModificationAllowedError`, surfaces as "Could not save", and — this plan's clean-up — leaves nothing behind.
- **A failed write of a file that already existed** leaves it untouched with `createWritable()` (swap file) and
  possibly torn on the fallback (in place). It is never deleted in either case.

## Tests

None. `OpfsFileStorage` runs only in a browser and the module's tests run on the desktop target; the JVM and iOS
storages are unaffected. (Do not add a wasm test task for this.)

## Verify

Compile: `./gradlew :app:web:wasmJsBrowserDistribution`, then check that
`app/web/build/dist/wasmJs/productionExecutable/opfs-writer.js` exists and that the "Web distribution: N files" line
went up by one. Run `./gradlew :app:web:wasmJsBrowserDevelopmentRun` for the rest.

A console helper to look at the songs folder (paste into DevTools):

```js
for await (const [name, handle] of (await (await (await navigator.storage.getDirectory()).getDirectoryHandle('library')).getDirectoryHandle('songs')).entries()) console.log(name, (await handle.getFile()).size);
```

1. **Normal path (Chrome, Firefox, Safari 26):** clear the site data, reload: the two demo songs and the setlist
   appear; create a song, edit it, reload — the text is there. No worker is listed in DevTools → Sources/Threads.
2. **Fallback, simulated in Chrome:** temporarily (do not commit) add
   `<script>delete FileSystemFileHandle.prototype.createWritable;</script>` as the first script in `index.html`.
   Clear site data, reload: demo library planted, a new song saves and survives a reload, an import of a zip of a few
   hundred songs completes, export gives the same files back. DevTools shows one `opfs-writer.js` worker, started at
   the first write.
3. **Fallback failing as a whole:** with the script tag from 2 still in, rename `opfs-writer.js` in the served folder
   (or block the URL in DevTools → Network). Save a new song: "Could not save the song", the loading page's failure
   screen does **not** appear, and the console helper shows no new entry. Unblock it and save again: it works
   without a reload (a new worker was started).
4. **Refused write, normal path:** remove the script tag. DevTools → Application → Storage → "Simulate custom storage
   quota", set it just above the current usage, import a large zip until writes start failing: the helper must list
   no zero-byte `.cho`. An existing song edited under the same quota keeps its old text.
5. **The real thing:** an iOS 18.x simulator runtime (Xcode → Settings → Components) reaches the development server at
   `http://localhost:8080/`; repeat step 1 there in Safari. Safari 18.x on macOS 14/15 if one is at hand.

## Docs

- `data/source/local/implementation/CLAUDE.md`, the "Writes are atomic on the three platforms that can be … OPFS has
  no such primitive." bullet: replace the last sentence with — "OPFS comes close: `createWritable()` writes to a swap
  file that replaces the target on `close()`, and a file that a failed write created is removed again, so a refused
  save leaves no empty song. Safari before 26 has no `createWritable()`; there the write is handed to
  `opfs-writer.js` (in `:app:web`), a dedicated worker, because `createSyncAccessHandle()` exists nowhere else, and
  that one writes in place. Writes of one file wait for each other, and a read waits for a write."
- `app/web/CLAUDE.md`: add a bullet after the `icon-192.png` one — "`src/wasmJsMain/resources/opfs-writer.js` — the
  dedicated worker `OpfsFileStorage` writes through in a browser without `createWritable()` (Safari before 26). It is
  plain JavaScript in the resources rather than Kotlin because it has to be a file of its own next to the page, and
  it is started by the first write, so it is neither in the progress bar's total nor preloaded."
- Root `CLAUDE.md`, Web section, the "OPFS, the file input and the download link are reached through `js(...)`
  blocks" bullet: append "The one exception to 'results come back as promises from the API itself' is the write
  fallback for Safari before 26, whose promise is settled by a worker's reply (see `:data:source:local:implementation`)."

## Touches

- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `app/web/src/wasmJsMain/resources/opfs-writer.js` (new)
- `data/source/local/implementation/CLAUDE.md`
- `app/web/CLAUDE.md`
- `CLAUDE.md`

## Depends on

Nothing. Related, not required: plan 49 (single instance; closes the second-tab case described above) and plan 53
(the message an unsupported browser gets — a browser with neither `createWritable()` nor a working worker still ends
in "Could not save", which is acceptable because no browser that runs Wasm GC is in that position).
