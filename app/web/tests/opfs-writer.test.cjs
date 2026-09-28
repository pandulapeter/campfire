/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The journal of opfs-writer.js against an in-memory directory that records every state a write passes through, run
// with `node --test app/web/tests/opfs-writer.test.cjs`. Every recorded state is a moment the worker could have been
// terminated in, and recovering from each of them has to end in the old song or the new one, never in a mix.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/wasmJsMain/resources/opfs-writer.js'), 'utf8');
const old = Buffer.from('original complete song');
const next = Buffer.from('replacement song, complete and longer');

function worker(initial) {
    const files = new Map([...initial].map(([name, data]) => [name, Buffer.from(data)]));
    const snapshots = [];
    const snapshot = () => snapshots.push(new Map([...files].map(([name, data]) => [name, Buffer.from(data)])));
    const missing = () => Object.assign(new Error('Missing'), { name: 'NotFoundError' });
    let failWrite = false;
    const directory = {
        getDirectoryHandle: async () => directory,
        keys: async function* () { yield* [...files.keys()]; },
        removeEntry: async name => { if (!files.delete(name)) throw missing(); snapshot(); },
        getFileHandle: async (name, options = {}) => {
            if (!files.has(name)) {
                if (!options.create) throw missing();
                files.set(name, Buffer.alloc(0)); snapshot();
            }
            return { createSyncAccessHandle: async () => ({
                getSize: () => files.get(name).length,
                read: (target, { at }) => { const count = Math.min(5, target.length, files.get(name).length - at); target.set(files.get(name).subarray(at, at + count)); return count; },
                write: (data, { at }) => {
                    if (failWrite && name === 'song.cho') { failWrite = false; throw new Error('Quota'); }
                    const count = Math.min(5, data.length);
                    let bytes = files.get(name);
                    if (bytes.length < at + count) { const grown = Buffer.alloc(at + count); bytes.copy(grown); bytes = grown; }
                    bytes.set(data.subarray(0, count), at); files.set(name, bytes); snapshot(); return count;
                },
                truncate: size => { const bytes = Buffer.alloc(size); files.get(name).copy(bytes); files.set(name, bytes); snapshot(); },
                flush: snapshot,
                close: () => {},
            }) };
        },
    };
    let reply;
    const context = vm.createContext({ navigator: { storage: { getDirectory: async () => directory } }, self: { postMessage: result => { reply = result; } }, Uint8Array, console });
    vm.runInContext(source, context);
    return { files, snapshots, failNextWrite: () => { failWrite = true; }, send: async request => {
        context.self.onmessage({ data: { id: 1, path: ['songs'], ...request } });
        await context.queue; return reply;
    } };
}

test('every interrupted write snapshot recovers to complete old or new content', async () => {
    const original = worker(new Map([['song.cho', old]]));
    assert.equal((await original.send({ name: 'song.cho', data: next })).error, undefined);
    assert.deepEqual(original.files.get('song.cho'), next);
    for (const snapshot of original.snapshots) {
        const restarted = worker(snapshot);
        assert.equal((await restarted.send({ recover: true })).error, undefined);
        const content = restarted.files.get('song.cho');
        assert.ok(content.equals(old) || content.equals(next));
        assert.deepEqual([...restarted.files.keys()], ['song.cho']);
        // Recovery itself may also be interrupted; the committed temp must remain authoritative.
        for (const interruptedRecovery of restarted.snapshots) {
            const again = worker(interruptedRecovery);
            assert.equal((await again.send({ recover: true })).error, undefined);
            assert.ok(again.files.get('song.cho').equals(old) || again.files.get('song.cho').equals(next));
        }
    }
});

test('an ordinary failed write restores the old song and removes its journal', async () => {
    const instance = worker(new Map([['song.cho', old]]));
    instance.failNextWrite();
    assert.equal((await instance.send({ name: 'song.cho', data: next })).error, 'Error');
    assert.deepEqual(instance.files.get('song.cho'), old);
    assert.deepEqual([...instance.files.keys()], ['song.cho']);
});

test('a marker without its temporary file is dropped rather than blocking the directory', async () => {
    const instance = worker(new Map([['song.cho', old], ['song.cho.campfire-commit', Buffer.alloc(0)]]));
    assert.equal((await instance.send({ recover: true })).error, undefined);
    assert.deepEqual([...instance.files.keys()], ['song.cho']);
    assert.deepEqual(instance.files.get('song.cho'), old);
});

test('a temporary file without a marker is discarded', async () => {
    const instance = worker(new Map([['song.cho', old], ['song.cho.campfire-tmp', Buffer.from('half')]]));
    assert.equal((await instance.send({ recover: true })).error, undefined);
    assert.deepEqual([...instance.files.keys()], ['song.cho']);
    assert.deepEqual(instance.files.get('song.cho'), old);
});
