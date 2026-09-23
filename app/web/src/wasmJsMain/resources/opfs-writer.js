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
