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
            try { access.write(request.data, { at: 0 }); access.truncate(request.data.byteLength); access.flush(); }
            finally { access.close(); }
        } catch (error) {
            if (!existed) try { await directory.removeEntry(request.name); } catch (ignored) { }
            throw error;
        }
    }).then(function () { self.postMessage({ id: request.id }); }, function (error) {
        self.postMessage({ id: request.id, error: (error && error.name) || 'Error', message: String((error && error.message) || error) });
    });
};
