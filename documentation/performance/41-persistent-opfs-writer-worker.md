<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 41 — One persistent OPFS writer worker

| | |
|---|---|
| Lane | E |
| Impact | medium on the affected browsers (Safari before 26, every iOS 18 browser); none elsewhere |
| Confidence | medium (worker start-up cost not measured here) |
| Platforms | web |
| Files | `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`, `data/source/local/implementation/CLAUDE.md`; `app/web/CLAUDE.md` (the `opfs-writer.js` bullet: add that one worker serves the page); `app/web/src/wasmJsMain/resources/opfs-writer.js` is not edited, its request/reply shape already fits |
| Depends on / conflicts with | Conflicts with 33 and 34 (same file); land after them. |
| Commit message | `Keep one OPFS writer worker for the page instead of starting one per write.` |

## Problem
**A new worker is started for every write.**
- `FileStorage.wasmJs.kt:270-277`, the path taken wherever `createWritable()` is missing:
  ```js
  var worker = new Worker(window.campfireVersioned('opfs-writer.js'));
  await new Promise(function (resolve, reject) {
      worker.onmessage = function (event) { worker.terminate(); … };
      …
      worker.postMessage({ id: 1, path: path.split('/'), name: name, data: bytes });
  });
  ```
- Each time that means fetching the script (from cache), parsing it and creating a new worker, then terminating it.

**How often it happens:**
- Every save, every imported song, every sync download, every preferences write and every periodic index write.
- A 2000-file import or first sync on those browsers starts 2000 or more workers, one after another for the import.

**The worker already supports a long life.**
- `opfs-writer.js` serialises requests on its own queue (`var queue = Promise.resolve(); … queue = queue.then(...)`).
- It answers with the request's `id`: `self.postMessage({ id: request.id })` or `{ id, error, message }`.

## Fix
- Keep one worker per page, created lazily, and match replies to requests by id:
  ```js
  // inside writeFile's fallback branch
  var writer = window.__campfireOpfsWriter;
  if (!writer) {
      writer = window.__campfireOpfsWriter = { worker: new Worker(window.campfireVersioned('opfs-writer.js')), next: 1, pending: new Map() };
      writer.worker.onmessage = function (event) {
          var entry = writer.pending.get(event.data.id); if (!entry) return; writer.pending.delete(event.data.id);
          event.data.error ? entry.reject(Object.assign(new Error(event.data.message), { name: event.data.error })) : entry.resolve();
      };
      writer.worker.onerror = function (event) {
          // A worker that failed as a whole is dropped; every write waiting on it fails, and the next write starts a new one.
          var error = event.error || new Error(event.message);
          writer.pending.forEach(function (entry) { entry.reject(error); });
          writer.pending.clear(); writer.worker.terminate();
          if (window.__campfireOpfsWriter === writer) window.__campfireOpfsWriter = null;
      };
  }
  var id = writer.next++;
  await new Promise(function (resolve, reject) {
      writer.pending.set(id, { resolve: resolve, reject: reject });
      writer.worker.postMessage({ id: id, path: path.split('/'), name: name, data: bytes });
  });
  ```
- **Must not change:**
  - The `existed` / `removeEntry` clean-up after a failed write stays in `writeFile`, around the await.
  - The versioned worker URL: a page from another release still never talks to a stale worker, because the global lives only as long as the page.
  - The `createWritable()` path is untouched.
  - The worker still restores the previous content on a partial write. That lives in `opfs-writer.js` and is unchanged.

## Verification
- Build: `./gradlew :app:web:wasmJsBrowserDistribution`.
- **Manual checks** in Safari 18 (or Safari 26 with `FileSystemFileHandle.prototype.createWritable` deleted from the console before the app starts, to force the fallback):
  - Import an archive of a few hundred songs and compare wall time before and after.
  - Check the Web Inspector's Workers list shows one worker.
  - Fill the quota and save: the error still surfaces as a failed save, and the next save works.
- **Docs:** add "one worker per page, requests matched by id" to the OPFS bullet in `data/source/local/implementation/CLAUDE.md`, and to the `opfs-writer.js` bullet of `app/web/CLAUDE.md` (which today says nothing about the worker's lifetime).
