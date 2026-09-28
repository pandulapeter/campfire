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

/*
 * A sync access handle writes in place, so a worker that is terminated part of the way through a write - a tab
 * closed or reloaded during a save, iOS reclaiming a background tab, a browser crash - would leave a file that is half
 * the new song and half the old one, which the next scan reads as a song with no sign that anything is wrong. So every
 * write is journaled, with nothing WebKit's OPFS lacks (it has no move and no rename):
 *
 * 1. The new content goes to a fresh `<name>.campfire-tmp`, flushed and closed. A kill here leaves the file untouched.
 * 2. An empty `<name>.campfire-commit` is created, which is atomic. From here on the temporary file is the truth.
 * 3. The file is written in place, then the marker is removed, then the temporary file.
 *
 * A marker found later means its temporary file is complete and newer, and it is copied over the file the same way; a
 * temporary file without a marker is an abandoned first step and is deleted. The storage asks for that playback for a
 * whole directory before it hands out anything in it, and every write does it for its own file first.
 */
var TEMP_SUFFIX = '.campfire-tmp';
var COMMIT_SUFFIX = '.campfire-commit';

var queue = Promise.resolve();
self.onmessage = function (event) {
    var request = event.data;
    queue = queue.then(async function () {
        if (!Array.isArray(request.path) || request.path.length === 0) throw new TypeError('No directory given.');
        var directory = await navigator.storage.getDirectory();
        for (var i = 0; i < request.path.length; i++) directory = await directory.getDirectoryHandle(request.path[i], { create: true });
        if (request.recover) await recoverAll(directory); else await write(directory, request.name, request.data);
    }).then(function () { self.postMessage({ id: request.id }); }, function (error) {
        self.postMessage({ id: request.id, error: (error && error.name) || 'Error', message: String((error && error.message) || error) });
    });
};

async function write(directory, name, data) {
    await recover(directory, name);
    await replace(directory, name + TEMP_SUFFIX, data);
    await directory.getFileHandle(name + COMMIT_SUFFIX, { create: true });
    try {
        await replace(directory, name, data);
    } catch (error) {
        // An ordinary failure (a quota refusal) has put the previous content back, and the write simply did not
        // happen. One that could not put it back leaves the journal, so that the file is repaired to the new content.
        if (!error.restoreFailed) await discardJournal(directory, name);
        throw error;
    }
    await discardJournal(directory, name);
}

/**
 * Every file is recovered on its own, and one that cannot be is only reported: failing the request would fail the
 * storage's access to the whole directory, and a library that cannot be opened at all is far worse than one file.
 */
async function recoverAll(directory) {
    var names = new Set();
    for await (var name of directory.keys()) {
        if (name.endsWith(TEMP_SUFFIX)) names.add(name.slice(0, -TEMP_SUFFIX.length));
        if (name.endsWith(COMMIT_SUFFIX)) names.add(name.slice(0, -COMMIT_SUFFIX.length));
    }
    for (var journaled of names) {
        try { await recover(directory, journaled); }
        catch (error) { console.error('Could not recover an interrupted write of ' + journaled + ': ' + ((error && error.message) || error)); }
    }
}

async function recover(directory, name) {
    if (!await exists(directory, name + COMMIT_SUFFIX)) {
        await removeIfPresent(directory, name + TEMP_SUFFIX);
        return;
    }
    // A marker without its temporary file is nothing the journal ever leaves (the marker is removed first), so there
    // is nothing to play back, and a marker kept would fail every later write of the file.
    if (!await exists(directory, name + TEMP_SUFFIX)) {
        await removeIfPresent(directory, name + COMMIT_SUFFIX);
        return;
    }
    var access = await (await directory.getFileHandle(name + TEMP_SUFFIX)).createSyncAccessHandle();
    var bytes;
    try { bytes = readAll(access); } finally { access.close(); }
    await replace(directory, name, bytes);
    await discardJournal(directory, name);
}

/** The marker first: once it is gone the file is complete, and a temporary file left behind is disposable. */
async function discardJournal(directory, name) {
    await removeIfPresent(directory, name + COMMIT_SUFFIX);
    await removeIfPresent(directory, name + TEMP_SUFFIX);
}

async function exists(directory, name) {
    try { await directory.getFileHandle(name); return true; }
    catch (error) { if (error && error.name === 'NotFoundError') return false; throw error; }
}

async function removeIfPresent(directory, name) {
    try { await directory.removeEntry(name); }
    catch (error) { if (!error || error.name !== 'NotFoundError') throw error; }
}

/** One in-place write, which puts the previous content back if it fails part of the way. */
async function replace(directory, name, data) {
    var existed = true;
    var file;
    try { file = await directory.getFileHandle(name); }
    catch (error) { if (!error || error.name !== 'NotFoundError') throw error; existed = false; file = await directory.getFileHandle(name, { create: true }); }
    try {
        var access = await file.createSyncAccessHandle();
        try {
            var total = data.byteLength;
            // What was there is kept to be put back; failing to read it fails the write before anything has been
            // overwritten.
            var previous = existed ? readAll(access) : null;
            try {
                // Growing the file first is what asks the browser for the space: a quota that does not allow it
                // refuses here, with the old content untouched, rather than in the middle of the writes.
                if (total > access.getSize()) access.truncate(total);
                writeAll(access, data);
                access.truncate(total);
                access.flush();
            } catch (error) {
                if (previous) restore(access, previous, error);
                throw error;
            }
        } finally { access.close(); }
    } catch (error) {
        if (!existed) try { await directory.removeEntry(name); } catch (ignored) { }
        throw error;
    }
}

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
 * already has, so it asks the browser for no space; if it fails all the same, the file is left damaged until the
 * journal is played back, and the error that is passed on says so instead of reporting a save that simply did not
 * happen.
 */
function restore(access, previous, error) {
    try {
        writeAll(access, previous);
        access.truncate(previous.byteLength);
        access.flush();
    } catch (restoreError) {
        var reason = (error && error.message) || String(error);
        var restoreReason = (restoreError && restoreError.message) || String(restoreError);
        throw Object.assign(new Error('The file could not be written (' + reason + ') and its previous content could not be put back (' + restoreReason + ').'), { restoreFailed: true });
    }
}
