# 01 · Web on Safari before 26 (every browser on iOS 18): nothing can ever be saved — no song, setlist, preference or sync connection, and the demo library never arrives

**Severity:** wrong behaviour bordering on data loss (web only; certain on every browser without `createWritable()` —
Safari before 26 on macOS, and *every* browser on iPhones and iPads still on iOS 18.2–18.x, since they are all WebKit;
the app runs there, Wasm GC being 18.2) · **Area:** `:data:source:local:implementation` (`OpfsFileStorage`),
`:app:web` (`opfs-writer.js`) · Regression of the fix for review 2 plan 12 (`bb501e03`).

## Symptom
Open the web build in Safari 18.x (or anything on iOS 18):
1. First launch: the demo songs are never planted, and it stays a "first run" on every launch after, since
   `preferences.json` is never written either.
2. Create a song: "The change could not be written". Edit an imported one and press Save: "Save failed". Tag a
   song, reorder a setlist, change the theme: each one fails (the theme appears to change, and is gone on reload).
3. Connecting Dropbox fails, since the credentials are a text file on the web.

The editor keeps its text, so nothing typed is lost silently, but the web build is read-only on those browsers — and
the one write path that does "succeed" (below) reports success for bytes that went somewhere else.

## Cause
Where `createWritable()` is missing, `writeFile` hands the write to the worker
(`data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt:220-225`):

```js
var worker = new Worker('opfs-writer.js');
...
worker.postMessage({ id: 1, path: [], name: name, data: data });
```

Two things are wrong with that message, compared with what `app/web/src/wasmJsMain/resources/opfs-writer.js` does with
it and with what the review 2 plan specified (the Kotlin side passed `directory.pathSegments.joinToString("/")`, and
the worker encoded a string with `TextEncoder`; `bb501e03` dropped both when it inlined the worker call into
`writeFile`):

1. **`path: []`.** The worker walks `request.path` down from `navigator.storage.getDirectory()`
   (`opfs-writer.js:16-17`), so an empty path is the *root* of the origin private file system, not `library/songs`,
   `library/setlists` or `preferences`. The file the main thread created in the right directory
   (`FileStorage.wasmJs.kt:213`, `getFileHandle(name, { create: true })`) is never written.
2. **A string is posted as it is.** `writeText` passes `text.toJsString()` (`:92`). `FileSystemSyncAccessHandle.write`
   takes a `BufferSource` only, so `access.write(request.data, { at: 0 })` (`opfs-writer.js:24`) throws a `TypeError`
   for every text write, and `request.data.byteLength` is `undefined` besides.

What each write therefore does on those browsers:
- `writeText` (every song, setlist, `preferences.json`, `sync-index.json`, `sync-credentials.json`): the worker throws
  the `TypeError`, removes the file *it* created at the root, the main thread removes the empty file it created in the
  real directory (`:227`) and `writeText` throws. Every save fails.
- `writeBytes` (sync downloads through `LibraryFileLocalSource`, `preferences.json.bad`): the worker writes the bytes to
  the root and answers success. For a new file the real directory keeps the **empty** file the main thread created;
  for an existing one it keeps the old content. The caller is told the write happened. (Sync cannot connect on these
  browsers today because of the credentials — but fixing only the strings would open exactly this path: a download
  recorded in the index as done, an empty local file, and the next run uploading the empty file over the remote song.
  Both halves have to be fixed together.)

## Fix
All in the two files named above.

1. `FileStorage.wasmJs.kt`: `writeFile` gets the directory's path besides its handle, joined with `/` (a segment
   never contains one):

   ```kotlin
   override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.Default) {
       failingAsStorage(name) { writeFile(directoryHandle(directory), directory.pathSegments.joinToString("/"), name, text.toJsString()).await() }
       Unit
   }
   ```

   (and the same for `writeBytes`), and in the JS:

   ```js
   private fun writeFile(parent: JsAny, path: String, name: String, data: JsAny): Promise<JsAny?> = js(
       ...
               var bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data;
               worker.postMessage({ id: 1, path: path.split('/'), name: name, data: bytes });
       ...
   ```

   Encoding on the main thread rather than in the worker keeps the worker's contract "bytes only", which is what it
   already assumes with `byteLength`. `TextEncoder` writes UTF-8, which is what `createWritable().write(string)`
   writes on the normal path, so the two paths produce the same file.
2. `opfs-writer.js`: nothing has to change for the fix, but make it refuse what it cannot write instead of relying on
   a `TypeError` from deep inside: at the top of the queued function,
   `if (!Array.isArray(request.path) || request.path.length === 0) throw new TypeError('No directory given.');` —
   a request for the root is always a bug here, since the app keeps nothing there.
3. Update the KDoc of `writeFile` (`:204-207`) to say what the fallback is: "Where there is no `createWritable()`
   (Safari before 26), the write is handed to `opfs-writer.js`, a dedicated worker, since `createSyncAccessHandle()`
   exists nowhere else; it is given the directory by its path and the content as bytes."

Do **not**:
- pass the `FileSystemDirectoryHandle` itself in the message: whether Safari 18 structured-clones handles into a
  worker is exactly the kind of thing that cannot be checked here, while a path always works;
- move reads, listings or deletions to the worker — they work on the main thread on every Safari that runs the app;
- change the order "create empty file, write, remove it again on failure" — review 2 plan 12 chose it and it is right
  once the write lands in the same directory.

Clean-up of files already written to the OPFS root by the broken path is not needed: only `preferences.json.bad` ever
reached it, and nothing reads the root.

## Tests
None (the OPFS storage runs only in a browser; `:data:source:local:implementation`'s tests cover the JVM storage).

## Verify
1. Chrome, the fallback forced: `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, open the page with DevTools, and in
   *Sources → Snippets* (or the console before the app finishes loading, with "Pause on load") run
   `delete FileSystemFileHandle.prototype.createWritable`, then reload with the snippet run as a local override
   (simplest: temporarily add `<script>delete FileSystemFileHandle.prototype.createWritable</script>` as the first
   script of `app/web/src/wasmJsMain/resources/index.html` in a scratch checkout — not committed).
   - Before: after "Clear site data" and a reload, no demo songs; creating a song fails; Application → Storage → the
     OPFS root is empty or holds stray files.
   - After: the demo songs and setlist appear, a new song is created and survives a reload, a tag, a setlist reorder
     and a theme change all survive a reload. DevTools → Application → "Storage buckets"/OPFS explorer (or
     `for await (const k of (await (await (await navigator.storage.getDirectory()).getDirectoryHandle('library')).getDirectoryHandle('songs')).keys()) console.log(k)`)
     lists the files under `library/songs`, and the root holds only `library` and `preferences`.
2. Same setup: import a song with non-ASCII text (`Tükörfúrógép`), reload, open it — accents intact (UTF-8).
3. Real Safari 18.x (macOS 15 without the Safari 26 update, or an iOS 18 simulator/device): the same three checks.
4. Chrome/Firefox/Safari 26 without the override: no worker appears in DevTools → Threads while saving.
5. `./gradlew :app:web:wasmJsBrowserDistribution` builds and `opfs-writer.js` is in
   `app/web/build/dist/wasmJs/productionExecutable`.

## Docs
`data/source/local/implementation/CLAUDE.md`, the "Writes are atomic…" bullet: after "OPFS has no such primitive." add
"Where the browser has no `createWritable()` (Safari before 26, every browser on iOS 18), a write is handed to
`app/web`'s `opfs-writer.js`, a dedicated worker, since `createSyncAccessHandle()` exists nowhere else: it is given the
directory by its path segments and the content as bytes, text encoded as UTF-8 on the way." `app/web/CLAUDE.md` lists
the files of `src/wasmJsMain/resources/` (`index.html`, `icon-192.png`) and not this one: add a bullet
"`src/wasmJsMain/resources/opfs-writer.js` — the dedicated worker `OpfsFileStorage` writes through where there is no
`createWritable()`; a request is `{ id, path: [directory segments], name, data: bytes }`. Not preloaded and not part of
the loading screen's byte count: it is only fetched by the first write that needs it."

## Touches
- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `app/web/src/wasmJsMain/resources/opfs-writer.js`
- `data/source/local/implementation/CLAUDE.md`, `app/web/CLAUDE.md`

## Depends on
Nothing. Do it before 29, which edits `failingAsStorage`, the reads and the KDoc in the same Kotlin file (different
lines); the `writeText`/`writeBytes` shape above already calls `failingAsStorage` the way 29 leaves it. Unrelated to
05 (network, other modules).
