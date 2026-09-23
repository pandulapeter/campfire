# 46 — A failed save on older Safari leaves the file half new, half old

**Severity:** data damage (web, browsers without `createWritable()`: Safari before 26 on macOS, every browser on
iOS 18) · **Area:** `app/web` (`src/wasmJsMain/resources/opfs-writer.js`)

**Read, not run.** This was found by reading the worker at HEAD; it has not been reproduced in a browser. The
"Verification" section below is how to confirm it (the existing WEB-003 dev patch is almost the reproduction), and
confirming it is the first step of the work.

## What the user sees

On Safari 18 (or any iOS 18 browser), a save of an **existing** file that fails part of the way — the origin's quota
running out, the disk filling up, a `write()` that stops short — is reported as failed ("Could not save the song"),
which is correct. But the file on disk is no longer the previous version either: its beginning is the new text and
its end is whatever the old version had beyond that point. Nothing shows it at once, because the repository still
holds the previous version in memory. It shows later:

- after a reload, the song opens with its first part edited and an old tail after it — a line cut in half, a chorus
  twice, a stray `{end_of_chorus}`;
- if sync is connected, the next run hashes the damaged file, sees that it changed and **uploads** it, so every other
  device gets it too;
- the same writer carries `preferences/preferences.json`, the setlists and `preferences/sync-index.json`: a damaged
  preferences document is set aside as `.bad` on the next launch and the settings start over; a damaged index makes
  the next sync compare by content.

Browsers with `createWritable()` (Chrome, Edge, Firefox, Safari 26) are not affected: that path writes into a swap
file the browser only puts in place on `close()`, and `abort()`s it on failure
(`FileStorage.wasmJs.kt:246-249`). A **new** file is not affected either: the worker removes it on failure.

## Cause

`app/web/src/wasmJsMain/resources/opfs-writer.js:19-41`:

```js
        var existed = true;
        var file;
        try { file = await directory.getFileHandle(request.name); }
        catch (error) { if (!error || error.name !== 'NotFoundError') throw error; existed = false; file = await directory.getFileHandle(request.name, { create: true }); }
        try {
            var access = await file.createSyncAccessHandle();
            try {
                // write() may write fewer bytes than it was given and says so only in what it returns, so it is called
                // again from where it stopped. One that makes no progress fails the save: truncating to the full length
                // after it would keep the old file's tail behind the new beginning and report that as saved.
                var total = request.data.byteLength;
                for (var written = 0; written < total;) {
                    var count = access.write(request.data.subarray(written), { at: written });
                    if (!(count > 0)) throw new Error('Only ' + written + ' of ' + total + ' bytes could be written.');
                    written += count;
                }
                access.truncate(total);
                access.flush();
            } finally { access.close(); }
        } catch (error) {
            if (!existed) try { await directory.removeEntry(request.name); } catch (ignored) { }
            throw error;
        }
```

A sync access handle writes **in place**, at the offsets it is given. The previous round of fixes made a short write
fail the save instead of being truncated into a "success" (the comment at `:26-28`), which was right, but the bytes
`[0, written)` have already replaced the old ones by then, and nothing puts them back: the `catch` at `:38-41` only
cleans up a file that did not exist before. A `write()` that *throws* part of the way (a `QuotaExceededError` on the
second call) leaves the same state.

## The change

Invoke the **`code-style`** skill before the first edit (MPL header and comment voice apply to the `.js` too).

Three layers, each covering what the one before cannot:

1. **Reserve the space before overwriting anything.** When the new content is longer than the file, grow the file to
   the new length with `truncate(total)` first. The File System spec has `truncate` to a larger size fail with a
   `QuotaExceededError` when the origin's quota does not allow it — before a byte of the old content is touched. That
   turns the likeliest failure, running out of quota, into a clean one. (A shorter or equal new content needs no new
   space at all.)
2. **Keep the old bytes and put them back if the write still fails.** Read the whole previous content before writing;
   on failure, write it back at 0, truncate to its length and flush. Songs, setlists and the preferences are small; the
   largest thing this writer carries is a few kilobytes to a few hundred.
3. **Say so when even that fails.** Putting the old bytes back writes only inside the file's existing length, so it
   needs no quota from the browser; it can still fail — an I/O error, or a copy-on-write disk (APFS) that is truly
   full. There is nothing more a page can do then, so the error says the file was left damaged rather than pretending
   the save merely did not happen. The editor still has the user's text, and saving again once there is room writes
   the whole file.

Rejected: writing into a temporary sibling and swapping it in. The sync access handle API has no rename, and
`FileSystemHandle.move()` onto an existing name is neither available everywhere this fallback runs nor atomic — it
would need a `removeEntry` of the target first, and a crash between the two loses the song outright, which is worse
than the damage this plan is fixing.

Replace the body of `opfs-writer.js` from `var queue` on with:

```js
var queue = Promise.resolve();
self.onmessage = function (event) {
    var request = event.data;
    queue = queue.then(async function () {
        if (!Array.isArray(request.path) || request.path.length === 0) throw new TypeError('No directory given.');
        var directory = await navigator.storage.getDirectory();
        for (var i = 0; i < request.path.length; i++) directory = await directory.getDirectoryHandle(request.path[i], { create: true });
        var existed = true;
        var file;
        try { file = await directory.getFileHandle(request.name); }
        catch (error) { if (!error || error.name !== 'NotFoundError') throw error; existed = false; file = await directory.getFileHandle(request.name, { create: true }); }
        try {
            var access = await file.createSyncAccessHandle();
            try {
                var total = request.data.byteLength;
                // A sync access handle writes in place, so a write that fails part of the way has already replaced
                // the beginning of the old file. What was there is kept to be put back; failing to read it fails the
                // save before anything has been overwritten.
                var previous = existed ? readAll(access) : null;
                try {
                    // Growing the file first is what asks the browser for the space: a quota that does not allow it
                    // refuses here, with the old content untouched, rather than in the middle of the writes.
                    if (total > access.getSize()) access.truncate(total);
                    writeAll(access, request.data);
                    access.truncate(total);
                    access.flush();
                } catch (error) {
                    if (previous) restore(access, previous, error);
                    throw error;
                }
            } finally { access.close(); }
        } catch (error) {
            if (!existed) try { await directory.removeEntry(request.name); } catch (ignored) { }
            throw error;
        }
    }).then(function () { self.postMessage({ id: request.id }); }, function (error) {
        self.postMessage({ id: request.id, error: (error && error.name) || 'Error', message: String((error && error.message) || error) });
    });
};

/**
 * write() may write fewer bytes than it was given and says so only in what it returns, so it is called again from
 * where it stopped. One that makes no progress throws: truncating to the full length after it would keep the old
 * file's tail behind the new beginning and report that as saved.
 */
function writeAll(access, data) {
    var total = data.byteLength;
    for (var written = 0; written < total;) {
        var count = access.write(data.subarray(written), { at: written });
        if (!(count > 0)) throw new Error('Only ' + written + ' of ' + total + ' bytes could be written.');
        written += count;
    }
}

/** read() may, like write(), return fewer bytes than asked for. */
function readAll(access) {
    var size = access.getSize();
    var buffer = new Uint8Array(size);
    for (var read = 0; read < size;) {
        var count = access.read(buffer.subarray(read), { at: read });
        if (!(count > 0)) throw new Error('Only ' + read + ' of ' + size + ' bytes of the file could be read before writing it.');
        read += count;
    }
    return buffer;
}

/**
 * Puts the previous content back after a write that failed part of the way. It writes only inside the length the file
 * already has, so it asks the browser for no space; if it fails all the same, the file is left damaged, and the error
 * that is passed on says so instead of reporting a save that simply did not happen.
 */
function restore(access, previous, error) {
    try {
        writeAll(access, previous);
        access.truncate(previous.byteLength);
        access.flush();
    } catch (restoreError) {
        var reason = (error && error.message) || String(error);
        var restoreReason = (restoreError && restoreError.message) || String(restoreError);
        throw new Error('The file could not be written (' + reason + ') and its previous content could not be put back (' + restoreReason + '), so it is left damaged.');
    }
}
```

Notes for whoever implements it:

- The restore's `throw` replaces the original error only when the restore failed; otherwise the original (a
  `QuotaExceededError` keeps its name) is what reaches `FileStorage.wasmJs.kt:34`, as today. A thrown `DOMException`'s
  `message` is read-only, which is why the combined case is a new `Error` rather than an edited one.
- `getSize()`, `read()`, `write()`, `truncate()` and `flush()` are called synchronously, as the worker already calls
  `write`, `truncate` and `flush` today (and as WEB-002 has been passing on Safari 18 with). Keep them un-`await`ed;
  if a Safari 18 build turns out to answer `getSize()` or `read()` with a promise, that is the first thing
  verification step 4 would show.
- A new file (`existed === false`) keeps today's handling: nothing to restore, and the Kotlin side and the worker both
  remove it on failure.
- The Kotlin side needs no change: every failure is still a rejected promise, folded into `LibraryStorageException`
  by `failingAsStorage` (`FileStorage.wasmJs.kt:75-86`), and the save is reported as failed exactly as now.

## Tests

- **No unit test is possible.** The worker is a plain script in `resources/` with no JavaScript test set-up, and its
  whole subject is the behaviour of `FileSystemSyncAccessHandle`, which exists only in a browser's dedicated worker.
- Compile check that the distribution still assembles and copies the worker:
  `./gradlew :app:web:wasmJsBrowserDistribution` and check `opfs-writer.js` is in
  `app/web/build/dist/wasmJs/productionExecutable`.

## Verification

Confirm the bug first — it was read, not run — then the fix, with the same patches.

Forcing the fallback in Chrome is the existing WEB-003 recipe: add
`<script>delete FileSystemFileHandle.prototype.createWritable</script>` as the first script of
`app/web/src/wasmJsMain/resources/index.html`, then `./gradlew :app:web:wasmJsBrowserDevelopmentRun`.

1. **Short write** (the reproduction). Temporarily add at the top of `opfs-writer.js`, after `'use strict';`:

   ```js
   var writeCalls = 0;
   var originalWrite = FileSystemSyncAccessHandle.prototype.write;
   FileSystemSyncAccessHandle.prototype.write = function (buffer, options) {
       writeCalls++;
       if (writeCalls === 1) return originalWrite.call(this, buffer.subarray(0, buffer.byteLength >> 1), options);
       if (writeCalls === 2) return 0;
       return originalWrite.call(this, buffer, options);
   };
   ```

   (A worker is started per write, so the counter starts again with every save.) Open a demo song in the editor,
   replace its first line with `XXXXXXXX…` (a long run of X), Save, then reload the page.
   - **Before the fix:** "Could not save the song"; after the reload the song begins with half of the edited text and
     continues with the old song from that byte on.
   - **After the fix:** the same message; after the reload the song is byte for byte what it was before the edit
     (export it and compare with the demo file, or read it in DevTools → Application → Storage → OPFS).
2. **Quota at the reservation.** Replace the patch with one that makes `truncate` throw when it would grow the file:

   ```js
   var originalTruncate = FileSystemSyncAccessHandle.prototype.truncate;
   FileSystemSyncAccessHandle.prototype.truncate = function (size) {
       if (size > this.getSize()) throw new DOMException('Patched quota.', 'QuotaExceededError');
       return originalTruncate.call(this, size);
   };
   ```

   Make a song longer and save. **Expected:** "Could not save the song"; the file is unchanged after a reload; a save
   that makes a song *shorter* still succeeds under this patch.
3. **Restore that fails.** Patch `write` to return half on its first call and 0 on every call after it. **Expected:** the
   save fails, and the console shows the worker's message "…its previous content could not be put back…, so it is left
   damaged." (the file *is* damaged in this case — that is the documented limit, and the point of this step is only
   that it is said).
4. Remove the patches and run WEB-002 on a real Safari 18.x (the shorter-then-longer saves, the tag, the reorder, the
   zip import): the fallback must still save whole files.
5. Chrome with no patches at all: WEB-001 — no worker appears, nothing changed for the `createWritable` path.

## Docs

- `app/web/CLAUDE.md`, lines 60-64, the `opfs-writer.js` bullet, currently ends: "It writes until every byte is in,
  since `write()` may write fewer than it was given, and fails the save rather than truncate behind a write that
  stopped short." Replace that sentence with: "It writes in place, so it grows the file to the new length first (a
  quota refusal then comes before anything is overwritten), writes until every byte is in, since `write()` may write
  fewer than it was given, and when a write still fails it puts the previous content back and fails the save; only if
  that fails too is the file left damaged, and the error says so."
- `data/source/local/implementation/CLAUDE.md`, lines 36-41, says writes are atomic on the three other platforms and
  "OPFS has no such primitive". Add after the sentence that ends "…text encoded as UTF-8 on the way.": "Without
  `createWritable()`'s swap file a write is in place, so the worker keeps the previous content and puts it back when a
  write fails part of the way (see `app/web`)."
- `documentation/testing/06-web.md`, WEB-003 (lines 81-90): add to **Expected**: "Reload: the song is exactly what it
  was before the edit, not the edited beginning followed by the old end." Mark it 🆕 as it already is, and add a
  WEB-003b with steps 2 and 3 above (P2, dev patch).

## Files touched

- `app/web/src/wasmJsMain/resources/opfs-writer.js`
- `app/web/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`
- `documentation/testing/06-web.md`

## Depends on

Nothing. Plan 11 (`storage-failures-reported-as-unknown`) touches how storage failures are *reported* on the Kotlin
side; this plan changes no Kotlin, and the failure still arrives as a rejected promise either way.
