/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// Serves the build index.html keeps in the browser, so that the app opens without a connection. It decides nothing and
// remembers nothing: which build runs, and what is in the cache, is the page's business (see index.html), and this only
// answers from the cache what the cache holds. That is also why a new version of it may take over at once.
//
// Two things about it outlive any release, because a browser keeps a worker long after the page that registered it:
//
// - The cache's layout is the contract between this and every page that was ever kept: one cache, CACHE_NAME, holding
//   every file of a build under its versioned address and the page under the address of the folder. A different
//   layout gets a different cache name, never a new meaning for this one.
// - This address is permanent, since the browser checks it for a new version on every navigation whatever the cached
//   page does, which makes it the way out of a kept page that turned out to be broken: publish a worker that sends
//   navigations to the network. Taking offline support away means publishing one that deletes the cache and
//   unregisters itself, never deleting the file.
var CACHE_NAME = 'campfire-web-app';

// The first segments of the app's screens (BrowserRoutes in :presentation), as the development server's routes.js and
// the site's 404.html know them.
var ROUTES = ['search', 'setlists', 'settings', 'song', 'setlist', 'import'];

/**
 * What to do with a request: `{ action: 'page' }` answers with the kept page, `{ action: 'redirect', location }` sends
 * a navigation on, `{ action: 'cache' }` answers with the cache entry of the request's own address, and null leaves
 * the request to the browser.
 *
 * A screen's address is redirected to the form the site's 404.html produces (…/app/?/song/…) rather than answered
 * with the page, which works out where its folder ends from the address it was loaded at. The page's own address is
 * answered whatever its query string, which is where an authorization answer arrives - so that is never a cache key.
 * build.json is what the page asks the deployment, and this file is what the browser does, so both always go to the
 * network.
 */
function campfireRoute(method, mode, address, scope) {
    if (method !== 'GET' || address.indexOf(scope) !== 0) {
        return null;
    }
    var url = new URL(address);
    var path = url.pathname.substring(new URL(scope).pathname.length);
    if (mode === 'navigate') {
        if (path === '' || path === 'index.html') {
            return { action: 'page' };
        }
        var segments = path.split('/').filter(Boolean);
        if (ROUTES.indexOf(segments[0]) === -1) {
            return null;
        }
        var query = url.search ? '&' + url.search.slice(1).replace(/&/g, '~and~') : '';
        return { action: 'redirect', location: scope + '?/' + segments.join('/') + query + url.hash };
    }
    if (path === 'build.json' || path === 'service-worker.js') {
        return null;
    }
    return { action: 'cache' };
}

if (typeof self !== 'undefined' && typeof self.addEventListener === 'function') {
    self.addEventListener('install', function () {
        self.skipWaiting();
    });

    self.addEventListener('activate', function (event) {
        event.waitUntil(self.clients.claim());
    });

    self.addEventListener('fetch', function (event) {
        var request = event.request;
        var scope = self.registration.scope;
        var route = campfireRoute(request.method, request.mode, request.url, scope);
        if (!route) {
            return;
        }
        if (route.action === 'redirect') {
            event.respondWith(Response.redirect(route.location, 302));
            return;
        }
        // A cache that cannot be opened - storage blocked, or cleared under the worker - is a miss like any other.
        var key = route.action === 'page' ? scope : request.url;
        event.respondWith(caches.open(CACHE_NAME).then(function (cache) {
            return cache.match(key);
        }).catch(function () {
            return undefined;
        }).then(function (cached) {
            return cached || fetch(request);
        }));
    });
}
