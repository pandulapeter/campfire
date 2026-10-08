/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The page's one `opfs-writer.js` worker, started by the first request that needs it, with its replies matched to the
 * requests by their id: starting one per write fetched, parsed and started the script again for every song of an
 * import. The worker queues the requests itself. One that fails as a whole fails every request waiting on it and is
 * dropped, so that the next request starts a new one; being a global of the page, it never outlives the version that
 * started it. Its address carries a version of the script's own content (see `index.html` in `:app:web`), because a
 * worker the browser still had from another release would be handed requests in a shape it may not understand.
 *
 * A global function rather than a `js(...)` block of its own, since both the writes and the recovery send requests
 * and a `js(...)` block cannot call another.
 */
internal fun installOpfsWriter(): Unit = js(
    """{
        if (window.__campfireSendToOpfsWriter) return;
        window.__campfireSendToOpfsWriter = function (request) {
            var writer = window.__campfireOpfsWriter;
            if (!writer) {
                writer = window.__campfireOpfsWriter = { worker: new Worker(window.campfireVersioned('opfs-writer.js')), next: 1, pending: new Map() };
                writer.worker.onmessage = function (event) {
                    var entry = writer.pending.get(event.data.id);
                    if (!entry) return;
                    writer.pending.delete(event.data.id);
                    event.data.error ? entry.reject(Object.assign(new Error(event.data.message), { name: event.data.error })) : entry.resolve();
                };
                writer.worker.onerror = function (event) {
                    var error = event.error || new Error(event.message);
                    writer.pending.forEach(function (entry) { entry.reject(error); });
                    writer.pending.clear();
                    writer.worker.terminate();
                    if (window.__campfireOpfsWriter === writer) window.__campfireOpfsWriter = null;
                };
            }
            return new Promise(function (resolve, reject) {
                request.id = writer.next++;
                writer.pending.set(request.id, { resolve: resolve, reject: reject });
                try { writer.worker.postMessage(request); }
                catch (error) { writer.pending.delete(request.id); reject(error); }
            });
        };
    }"""
)

/**
 * Plays back what every write in this directory left unfinished. A `<name>.campfire-new` marker is the page's own (see
 * [writeFile]): a new file whose first write never closed, which is removed while it is still empty, since an empty file
 * under a song's name would be read as a song, and uploaded by sync as one. The other journal files are the worker's
 * (see `opfs-writer.js`), and only ever exist where the worker writes, so a browser with `createWritable()` never finds
 * any and never starts the worker for this.
 */
internal fun recoverInterruptedWrites(directory: JsAny, path: String): Promise<JsAny?> = js(
    """(async function () {
        var created = [];
        var hasWorkerJournal = false;
        for await (var name of directory.keys()) {
            if (name.endsWith('.campfire-new')) created.push(name.slice(0, -'.campfire-new'.length));
            else if (name.endsWith('.campfire-tmp') || name.endsWith('.campfire-commit')) hasWorkerJournal = true;
        }
        var ignoreMissing = function (error) { if (!error || error.name !== 'NotFoundError') throw error; };
        for (var i = 0; i < created.length; i++) {
            var file = null;
            try { file = await (await directory.getFileHandle(created[i])).getFile(); } catch (error) { ignoreMissing(error); }
            if (file && file.size === 0) try { await directory.removeEntry(created[i]); } catch (error) { ignoreMissing(error); }
            try { await directory.removeEntry(created[i] + '.campfire-new'); } catch (error) { ignoreMissing(error); }
        }
        if (hasWorkerJournal) await window.__campfireSendToOpfsWriter({ path: path.split('/'), recover: true });
        return null;
    })()"""
)

/**
 * A writable holds a lock on its file until it is closed or aborted, so one whose write fails is aborted before the
 * failure is passed on: left open, it would make every later write and the deletion of that file fail as well.
 *
 * Creating a file puts it under its final name, empty, before anything is written, and a page that is closed before the
 * write ends cannot take it back. So a new file is announced first by an empty `<name>.campfire-new`, which
 * [recoverInterruptedWrites] uses to remove an empty leftover, and which is removed once the write is closed - except
 * for a name within its 12 bytes of the 255 bytes Safari's file system allows, where the marker could never be created
 * and the file is written without one, as an existing file always is (the writable's swap file keeps that one whole).
 * Decided by the length rather than by a failure to create the marker, so that a real failure of the storage is never
 * taken for that.
 *
 * Where there is no `createWritable()` (Safari before 26), the write is handed to `opfs-writer.js`, a dedicated worker
 * (see [installOpfsWriter]), since `createSyncAccessHandle()` exists nowhere else; it is given the directory by its
 * path and the content as bytes. The page creates nothing there: the worker creates the file only after its own
 * journal says what it is to hold.
 */
internal fun writeFile(parent: JsAny, path: String, name: String, data: JsAny): Promise<JsAny?> = js(
    """(async function () {
        if (typeof FileSystemFileHandle === 'undefined' || typeof FileSystemFileHandle.prototype.createWritable !== 'function') {
            var bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data;
            await window.__campfireSendToOpfsWriter({ path: path.split('/'), name: name, data: bytes });
            return null;
        }
        var existed = true;
        var marker = null;
        var handle;
        try { handle = await parent.getFileHandle(name); }
        catch (error) {
            if (!error || error.name !== 'NotFoundError') throw error;
            existed = false;
            if (new TextEncoder().encode(name).length <= 243) {
                marker = name + '.campfire-new';
                await parent.getFileHandle(marker, { create: true });
            }
            try { handle = await parent.getFileHandle(name, { create: true }); }
            catch (creationError) { if (marker) try { await parent.removeEntry(marker); } catch (ignored) { } throw creationError; }
        }
        try {
            var writable = await handle.createWritable();
            try { await writable.write(data); await writable.close(); }
            catch (error) { try { await writable.abort(); } catch (ignored) { } throw error; }
        } catch (error) {
            if (!existed) try { await parent.removeEntry(name); } catch (ignored) { }
            if (marker) try { await parent.removeEntry(marker); } catch (ignored) { }
            throw error;
        }
        // The file is whole by now, so a marker that cannot be removed only leaves recovery a file it keeps.
        if (marker) try { await parent.removeEntry(marker); } catch (ignored) { }
        return null;
    })()"""
)

/**
 * Resolves instead of rejecting with a `NotFoundError` when the file is not there, which makes deleting a missing file
 * a no-op. Every other rejection is passed on, since a rename writes the new file before it deletes the old one and
 * a deletion reported as done when it was refused would leave the song there twice.
 */
internal fun removeEntry(parent: JsAny, name: String): Promise<JsAny?> = js(
    """parent.removeEntry(name).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)
