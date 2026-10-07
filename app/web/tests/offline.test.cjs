/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The pure half of keeping the web build in the browser, run with `node --test app/web/tests/offline.test.cjs`: what
// service-worker.js does with a request, and the decisions index.html takes about what to download, what to start and
// what to delete. Both are run as they are shipped - the worker file whole, and the page's own campfire-launch script.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const resources = path.join(__dirname, '../src/wasmJsMain/resources');

const worker = vm.createContext({ URL });
vm.runInContext(fs.readFileSync(path.join(resources, 'service-worker.js'), 'utf8'), worker);
const route = (address, mode = 'cors', method = 'GET') => worker.campfireRoute(method, mode, address, SCOPE);

const page = fs.readFileSync(path.join(resources, 'index.html'), 'utf8');
const launchScript = page.match(/<script id="campfire-launch"[^>]*>([\s\S]*?)<\/script>/)[1];
const window = {};
vm.runInContext(launchScript, vm.createContext({ window, URL, JSON, Object }));
const launch = window.campfireLaunch;

const SCOPE = 'https://campfire-songbook.com/app/';
const digest = character => character.repeat(64);
const FILES = {
    'campfire.js': { sha256: digest('a'), size: 10 },
    'abc.wasm': { sha256: digest('b'), size: 20 },
    'composeResources/x/font/inter regular.ttf': { sha256: digest('c'), size: 30 },
};
const plain = value => JSON.parse(JSON.stringify(value));

test('the worker answers a navigation to the folder with the kept page, whatever its query string', () => {
    assert.deepEqual(plain(route(SCOPE, 'navigate')), { action: 'page' });
    assert.deepEqual(plain(route(`${SCOPE}?code=abc&state=def`, 'navigate')), { action: 'page' });
    assert.deepEqual(plain(route(`${SCOPE}?/song/x.cho`, 'navigate')), { action: 'page' });
    assert.deepEqual(plain(route(`${SCOPE}index.html`, 'navigate')), { action: 'page' });
});

test('the worker sends a screen\'s address to the form the site\'s 404 page produces', () => {
    assert.deepEqual(plain(route(`${SCOPE}song/green_day-basket_case.cho`, 'navigate')), {
        action: 'redirect',
        location: `${SCOPE}?/song/green_day-basket_case.cho`,
    });
    assert.deepEqual(plain(route(`${SCOPE}settings/library/?code=a&state=b`, 'navigate')), {
        action: 'redirect',
        location: `${SCOPE}?/settings/library&code=a~and~state=b`,
    });
    assert.deepEqual(plain(route(`${SCOPE}setlist/s%C3%A9t.setlist.json/a.cho`, 'navigate')), {
        action: 'redirect',
        location: `${SCOPE}?/setlist/s%C3%A9t.setlist.json/a.cho`,
    });
    assert.deepEqual(plain(route(`${SCOPE}import`, 'navigate')), {
        action: 'redirect',
        location: `${SCOPE}?/import`,
    });
});

test('the worker leaves alone what is not the app\'s', () => {
    assert.equal(route(`${SCOPE}unknown/page`, 'navigate'), null);
    assert.equal(route('https://campfire-songbook.com/', 'navigate'), null);
    assert.equal(route('https://campfire-songbook.com/privacy/'), null);
    assert.equal(route('https://api.dropboxapi.com/2/files/list_folder', 'cors', 'POST'), null);
    assert.equal(route('https://coverartarchive.org/release-group/x/front-250'), null);
    assert.equal(route(`${SCOPE}campfire.js`, 'no-cors', 'POST'), null);
});

test('the worker always sends build.json and itself to the network', () => {
    assert.equal(route(`${SCOPE}build.json?launch=abc`), null);
    assert.equal(route(`${SCOPE}service-worker.js`), null);
});

test('the worker answers every other file of the folder from the cache by its exact address', () => {
    assert.deepEqual(plain(route(`${SCOPE}campfire.js?v=0123456789abcdef`, 'no-cors')), { action: 'cache' });
    assert.deepEqual(plain(route(`${SCOPE}composeResources/x/values/strings.commonMain.cvr?v=1`)), { action: 'cache' });
    assert.deepEqual(plain(route(`${SCOPE}index.html`)), { action: 'cache' });
});

test('a file is kept under the address the page asks for it with', () => {
    assert.equal(launch.addressOf('campfire.js', FILES['campfire.js'], SCOPE), `${SCOPE}campfire.js?v=aaaaaaaaaaaaaaaa`);
    assert.equal(
        launch.addressOf('composeResources/x/font/inter regular.ttf', FILES['composeResources/x/font/inter regular.ttf'], SCOPE),
        `${SCOPE}composeResources/x/font/inter%20regular.ttf?v=cccccccccccccccc`,
    );
});

test('a build is missing exactly the files the cache does not hold under their own version', () => {
    const all = launch.missingFiles(FILES, [], SCOPE).map(file => file.path);
    assert.deepEqual(plain(all), ['abc.wasm', 'campfire.js', 'composeResources/x/font/inter regular.ttf']);
    const kept = [
        SCOPE,
        launch.addressOf('campfire.js', FILES['campfire.js'], SCOPE),
        `${SCOPE}abc.wasm?v=dddddddddddddddd`,
    ];
    const missing = launch.missingFiles(FILES, kept, SCOPE);
    assert.deepEqual(plain(missing.map(file => file.path)), ['abc.wasm', 'composeResources/x/font/inter regular.ttf']);
    assert.equal(missing[0].address, `${SCOPE}abc.wasm?v=bbbbbbbbbbbbbbbb`);
    assert.equal(missing[0].entry.size, 20);
    const everything = Object.keys(FILES).map(file => launch.addressOf(file, FILES[file], SCOPE));
    assert.equal(launch.missingFiles(FILES, everything, SCOPE).length, 0);
});

test('only what the build does not name is deleted, and never the kept page', () => {
    const own = launch.addressOf('campfire.js', FILES['campfire.js'], SCOPE);
    const other = `${SCOPE}abc.wasm?v=dddddddddddddddd`;
    assert.deepEqual(plain(launch.staleAddresses(FILES, [SCOPE, own, other], SCOPE)), [other]);
});

test('a kept page is recognized by the id the build wrote into it', () => {
    const id = '0123456789abcdef';
    assert.equal(launch.isPageOf(`var build = {"id":"${id}","binaryCount":2};`, id), true);
    assert.equal(launch.isPageOf('var build = {"id":"fedcba9876543210","binaryCount":2};', id), false);
    assert.equal(launch.isPageOf(page, id), false);
    assert.equal(launch.isPageOf(null, id), false);
});

test('only a build\'s own answer is taken from build.json', () => {
    const answer = { id: 'a', page: digest('e'), files: FILES };
    assert.equal(launch.parseAnswer(answer), answer);
    assert.equal(launch.parseAnswer(null), null);
    assert.equal(launch.parseAnswer({ id: 'a', files: FILES }), null);
    assert.equal(launch.parseAnswer({ id: 1, page: 'x', files: FILES }), null);
    assert.equal(launch.parseAnswer({ id: 'a', page: 'x' }), null);
});

test('another build is downloaded once per tab, and nothing else is', () => {
    assert.equal(launch.nextStep('a', null, null), 'keep');
    assert.equal(launch.nextStep('a', { id: 'a' }, null), 'keep');
    assert.equal(launch.nextStep('a', { id: 'b' }, null), 'update');
    assert.equal(launch.nextStep('a', { id: 'b' }, 'c'), 'update');
    assert.equal(launch.nextStep('a', { id: 'b' }, 'b'), 'keep');
});

test('a kept build is whole only with its page, its worker and every file of its map', () => {
    const everything = Object.keys(FILES).map(file => launch.addressOf(file, FILES[file], SCOPE));
    assert.equal(launch.isComplete(FILES, [SCOPE, ...everything], SCOPE, true, true), true);
    assert.equal(launch.isComplete(FILES, [SCOPE, ...everything], SCOPE, false, true), false);
    assert.equal(launch.isComplete(FILES, [SCOPE, ...everything], SCOPE, true, false), false);
    assert.equal(launch.isComplete(FILES, [SCOPE, ...everything.slice(1)], SCOPE, true, true), false);
});

test('build.json is waited for briefly only for a whole kept build that no late answer has overtaken', () => {
    assert.equal(launch.answerWindow(true, null, 'a'), 800);
    assert.equal(launch.answerWindow(false, null, 'a'), 3000);
    assert.equal(launch.answerWindow(true, 'other', 'a'), 3000);
    assert.equal(launch.answerWindow(false, 'other', 'a'), 3000);
    assert.equal(launch.answerWindow(true, 'a', 'a'), 800);
});
