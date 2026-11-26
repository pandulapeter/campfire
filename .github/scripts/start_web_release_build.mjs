/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// Starts the web distribution in headless Chrome, for tests.yml. Binaryen's optimizer and the dead code elimination of
// the production build change what runs, and what they break fails only in a browser, so the distribution is served
// under /app/ from localhost (a secure context, as the site's https is: OPFS, the Web Lock, and the service worker need
// one) and loaded as a first visit, then again at the addresses of Chords and Settings' About tab, in the "?/" form
// the site's 404.html redirects a screen address to. Each load has to take the loading screen away within its time,
// which the page does only once the app drew its first frames, keep its address, and throw nothing: an uncaught error,
// a rejected promise, or a Wasm trap, which Chrome reports as uncaught too, fails it.
//
// Chrome is driven over its DevTools protocol on a pipe, which needs nothing beyond Node itself. CHROME_BIN (which the
// runner sets, as Karma reads it) names the browser, else the PATH's google-chrome or chromium, else macOS's Google
// Chrome.
//
//     node .github/scripts/start_web_release_build.mjs app/web/build/dist/wasmJs/productionExecutable

import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { delimiter, extname, join, normalize, sep } from 'node:path';

const LOAD_TIMEOUT = 120_000;
const SETTLE = 5_000;
const TYPES = {
    '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm',
    '.json': 'application/json', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.ttf': 'font/ttf',
    '.otf': 'font/otf', '.woff2': 'font/woff2', '.txt': 'text/plain', '.xml': 'application/xml',
};

const distribution = process.argv[2];
if (!distribution || !existsSync(join(distribution, 'index.html'))) {
    console.error(`::error::No web distribution at '${distribution}'.`);
    process.exit(2);
}
const root = normalize(distribution + sep);

const server = createServer((request, response) => {
    const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const file = path === '/app/' ? join(root, 'index.html') : normalize(join(root, path.slice('/app/'.length)));
    if (!path.startsWith('/app/') || !file.startsWith(root) || !existsSync(file) || !statSync(file).isFile()) {
        response.writeHead(404).end();
        return;
    }
    const type = TYPES[extname(file)] ?? 'application/octet-stream';
    response.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-cache' });
    response.end(readFileSync(file));
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const origin = `http://localhost:${server.address().port}`;

function chromePath() {
    if (process.env.CHROME_BIN) {
        return process.env.CHROME_BIN;
    }
    const names = ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser'];
    const found = (process.env.PATH ?? '').split(delimiter)
        .flatMap((folder) => names.map((name) => join(folder, name)))
        .find(existsSync);
    return found ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
}

const profile = mkdtempSync(join(tmpdir(), 'campfire-web-'));
// The sandbox is left out, since the runner's AppArmor refuses the user namespaces it is made of and the page is the
// build's own. A GPU there is not: WebGL, which Skia draws the canvas with, is SwiftShader's, which Chrome gives a page
// only when asked to.
const chrome = spawn(chromePath(), [
    '--headless', '--remote-debugging-pipe', `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check',
    '--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--window-size=1280,800', 'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] });
let chromeOutput = '';
chrome.stderr.on('data', (chunk) => {
    chromeOutput += chunk;
});

function finish(code, message) {
    if (message) {
        console.log(code === 0 ? message : `::error::${message}`);
    }
    if (code !== 0 && chromeOutput) {
        console.log(chromeOutput.split('\n').slice(-50).join('\n'));
    }
    chrome.kill('SIGKILL');
    server.close();
    rmSync(profile, { recursive: true, force: true });
    process.exit(code);
}

chrome.on('exit', (code) => finish(1, `Chrome exited (${code}) before the check was done.`));

let nextId = 0;
const pending = new Map();
const thrown = [];
let loadEvents = 0;
let buffer = '';
chrome.stdio[4].on('data', (chunk) => {
    buffer += chunk;
    let end;
    while ((end = buffer.indexOf('\0')) >= 0) {
        const message = JSON.parse(buffer.slice(0, end));
        buffer = buffer.slice(end + 1);
        if (message.id !== undefined) {
            pending.get(message.id)?.(message);
            pending.delete(message.id);
        } else if (message.method === 'Page.loadEventFired') {
            loadEvents++;
        } else if (message.method === 'Runtime.exceptionThrown') {
            const details = message.params.exceptionDetails;
            thrown.push(details.exception?.description ?? details.text);
        }
    }
});

function send(method, params = {}, sessionId = undefined) {
    const id = ++nextId;
    chrome.stdio[3].write(JSON.stringify({ id, method, params, sessionId }) + '\0');
    return new Promise((resolve, reject) => {
        pending.set(id, (message) => {
            if (message.error) {
                reject(new Error(`${method}: ${message.error.message}`));
            } else {
                resolve(message.result);
            }
        });
    });
}

const { targetInfos } = await send('Target.getTargets');
const { targetId } = targetInfos.find((target) => target.type === 'page');
const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
const page = (method, params) => send(method, params, sessionId);
await page('Runtime.enable');
await page('Page.enable');

async function evaluate(expression) {
    return (await page('Runtime.evaluate', { expression, returnByValue: true })).result.value;
}

/** Loads the page at the address, waits for the app to take the loading screen away, then for it to settle. */
async function load(address, expectedPath) {
    const startedAt = Date.now();
    const loadsBefore = loadEvents;
    await page('Page.navigate', { url: `${origin}/app/${address}` });
    for (;;) {
        if (thrown.length) {
            finish(1, `The web release build threw while loading ${expectedPath}: ${thrown.join('\n')}`);
        }
        const state = await evaluate(`(() => {
            const splash = document.getElementById('splash');
            const message = document.getElementById('splash-message');
            if (message && !message.hidden) return 'failed: ' + message.textContent;
            return !splash || splash.classList.contains('is-done') ? 'ready' : 'loading';
        })()`).catch(() => 'loading');
        // Until the new page's load event, the document asked may still be the one before it.
        if (state === 'ready' && loadEvents > loadsBefore) {
            break;
        }
        if (state.startsWith('failed')) {
            finish(1, `The web release build showed its loading failure at ${expectedPath}: ${state}`);
        }
        if (Date.now() - startedAt > LOAD_TIMEOUT) {
            finish(1, `The web release build did not draw ${expectedPath} in ${LOAD_TIMEOUT / 1000} s.`);
        }
        await new Promise((resolve) => setTimeout(resolve, 250));
    }
    await new Promise((resolve) => setTimeout(resolve, SETTLE));
    if (thrown.length) {
        finish(1, `The web release build threw after drawing ${expectedPath}: ${thrown.join('\n')}`);
    }
    const path = await evaluate('location.pathname');
    if (path !== expectedPath) {
        finish(1, `The web release build left ${expectedPath} for ${path}.`);
    }
    console.log(`Drawn in ${((Date.now() - startedAt - SETTLE) / 1000).toFixed(1)} s: ${expectedPath}.`);
}

try {
    await load('', '/app/songs');
    // What a first visit opens over the songs (the onboarding, or What's new) would be opened again by the next load
    // instead of its address until it is left, which Escape does as it would for a user.
    for (const type of ['keyDown', 'keyUp']) {
        await page('Input.dispatchKeyEvent', { type, key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
    }
    await new Promise((resolve) => setTimeout(resolve, SETTLE));
    await load('?/chords', '/app/chords');
    await load('?/settings/about', '/app/settings/about');
} catch (error) {
    finish(1, `Chrome could not be driven: ${error.message}`);
}
finish(0, 'The web release build started and drew every address.');
