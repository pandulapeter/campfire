# Never leave an empty file behind a new file's interrupted first write on the web

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web (every browser; both the `createWritable()` path and the worker path)
**Files:** `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`,
`app/web/src/wasmJsMain/resources/opfs-writer.js` (only if the worker needs a change; see Fix),
`app/web/tests/opfs-writer.test.cjs`, `data/source/local/implementation/CLAUDE.md`, `app/web/CLAUDE.md`
**Challenged:** amended — no marker for a name within 12 bytes of the 255-byte limit (Safari 26+ has `createWritable()` and stores OPFS entries under their real names on APFS, so `<name>.campfire-new` would fail every first write of such a song, a regression); the recovery loop's restructuring spelled out; the Node test is a guard of the worker invariant that passes before the fix too, said so.

## Problem

`writeFile` creates the final name before any content exists (`FileStorage.wasmJs.kt:420-438` at 8ee010b36):

```js
try { handle = await parent.getFileHandle(name); }
catch (error) { if (!error || error.name !== 'NotFoundError') throw error; existed = false; handle = await parent.getFileHandle(name, { create: true }); }
try {
    if (typeof handle.createWritable === 'function') {
        var writable = await handle.createWritable();
        try { await writable.write(data); await writable.close(); } ...
    } else {
        ...
        await window.__campfireSendToOpfsWriter({ path: path.split('/'), name: name, data: bytes });
    }
} catch (error) { if (!existed) try { await parent.removeEntry(name); } catch (ignored) { } throw error; }
```

`getFileHandle(name, { create: true })` puts an empty file under the final name at once. The clean-up only runs if the
page is still alive to catch an error, so a tab closed, reloaded or reclaimed between that call and `close()` (or the
worker's reply) leaves a zero-byte `name`: the swap file of `createWritable()` is discarded, and the worker's
`recover` deletes a `.campfire-tmp` without a marker, but nothing removes the empty file. On the worker path this also
breaks the documented guarantee (`data/source/local/implementation/CLAUDE.md`) that an interrupted write "leaves a
file that is whole, old or new": for a new file "old" is absent, and the page already created it.

The window is open for most of each new file's write, which is most of an import's writing phase and most of a first
sync's downloads on the web. What a leftover becomes:
- a song: an empty song titled by its file name in the library; an import of the same archive then finds the name
  "taken by something different" and asks about it; and if it was a sync **download** that was interrupted, the next
  run finds an unindexed local file and a remote one, plans `Resolve`, uploads the empty file under the song's name
  and keeps the real song only as its ` (2)` copy — on every device;
- a setlist: `SetlistDocumentFormat.decode("")` throws, so it is not shown, but `loadLibraryFiles` lists it and sync
  uploads an empty `*.setlist.json`;
- `preferences.json` on the first launch: `hasStoredUserPreferences()` is an existence check, so the next launch is
  not a first run — no demo songs and no welcome sheet.

## Fix

**Worker path** (no `createWritable()`): do not create the file on the page at all. Decide the path without a handle,
`typeof FileSystemFileHandle !== 'undefined' && typeof FileSystemFileHandle.prototype.createWritable === 'function'`,
and on the worker path just send the request. The worker's `write` already creates `name` only inside the final
`replace(directory, name, data)`, after `.campfire-commit` exists, so a kill from then on is replayed to the new
content, and its `replace` removes a file it created when the write fails. No change to `opfs-writer.js` is needed.

**`createWritable()` path**: journal the creation with a marker of its own, which the listings already know how to
hide:
1. If the file does not exist, first create an empty `<name>.campfire-new` (atomic), then
   `getFileHandle(name, { create: true })`, write and close, then remove the marker. If the file exists, write as today
   (the swap file keeps it whole).
   **Except** when `new TextEncoder().encode(name).length > 243` (255 − the 12 bytes of `.campfire-new`): then write
   without a marker, exactly as today. Song names reach 245 bytes (`LibraryFiles.MAX_NAME_BYTES` per half), and Safari
   26+ — which has `createWritable()`, so takes this path — keeps an OPFS entry as a real file under the name it is given
   (WebKit's `FileSystemStorageHandle::requestCreateHandle`), where APFS refuses names over 255 bytes; the marker would
   make every first write of such a song fail where today it succeeds. Decided by length rather than by catching the
   marker's failure, so a real storage failure (quota) is never swallowed. (Plan 19 is about the same limit on the
   worker path; this keeps 18 independent of it.)
2. On failure with the page alive, remove `name` (as today) and the marker.
3. Add `.campfire-new` to both listing filters (`:269`, `:282`: `!name.endsWith('.campfire-new')`) and to
   `recoverInterruptedWrites`'s trigger. Handle it on the page, before the existing worker request. The function
   currently `break`s at the first journal name it meets; restructure it to walk every name once, collecting the
   `.campfire-new` ones and noting whether any `.campfire-tmp` / `.campfire-commit` exists, then:
   for each `<name>.campfire-new`, if `<name>` exists and `(await (await directory.getFileHandle(name)).getFile()).size
   === 0`, remove `<name>`; then remove the marker (both removals tolerating `NotFoundError`); and only afterwards, and
   only if a worker journal was seen, send the one `recover` request as today — so browsers with `createWritable()`
   still never start the worker. (A kill after `close()` and before the marker's removal leaves a complete file, which
   the size check keeps; the one thing this can remove is a file whose new content really was empty, which nothing in
   the app writes on purpose, and losing it equals the write not having happened.)

Not covered, on purpose: an empty file the released build already left behind has no marker and stays as it is.

Update `data/source/local/implementation/CLAUDE.md` (the OPFS write paragraph: a new file is created by the worker
after its commit marker, and on the `createWritable()` path behind a `.campfire-new` marker that recovery uses to
remove an empty leftover) and `app/web/CLAUDE.md`'s `opfs-writer.js` paragraph if it says the page creates the file.

## Tests

The page-side `js(...)` blocks have no test harness, so the page changes are covered by the manual check only. In
`app/web/tests/opfs-writer.test.cjs`, add a case for the worker path: a write of a **new** file (the fixture's
`worker(new Map())`), killed at every recorded snapshot and recovered, never leaves `name` present with empty content —
it is absent or holds the new content — and leaves no journal file behind. (The current fixture creates files on
`getFileHandle(..., { create: true })`, so it can assert this directly.) This case passes before the fix as well: the
worker already behaves this way, and what it guards is that the page may now rely on it; the bug itself is the page's
creation, which no Node test reaches. Run `node --test app/web/tests/opfs-writer.test.cjs`.

## Manual check

In Chrome (`createWritable()`) and in Safari 18 / an iOS 18 browser (worker path): import a large zip of songs into an
empty library and close the tab during the "Importing" phase; reopen the app. No song with an empty text appears, and
importing the same zip again asks no question about any song. Repeat by closing the tab during a first Dropbox sync of
a large folder, then let the next run finish: no ` (2)` copies appear and no song is empty.
