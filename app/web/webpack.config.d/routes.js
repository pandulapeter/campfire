/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The development server's copy of what the site's 404.html does for the deployment: an address that names a screen
// of the app rather than a file (…/song/…, opened from the address bar or reloaded) is redirected to the page with
// that path in the query string, which index.html turns back into the address. Serving the page at the deep address
// instead, the usual history fallback, would leave index.html no way of telling where the folder it lives in ends.
//
// Only navigations to one of the app's own first segments are redirected, so that a missing file still gets a 404.
;(function (config) {
    if (!config.devServer) {
        return;
    }
    var ROUTES = ['search', 'setlists', 'settings', 'song', 'setlist'];
    var setupMiddlewares = config.devServer.setupMiddlewares;
    config.devServer.setupMiddlewares = function (middlewares, devServer) {
        var result = setupMiddlewares ? setupMiddlewares(middlewares, devServer) : middlewares;
        result.unshift({
            name: 'campfire-routes',
            middleware: function (request, response, next) {
                var url = new URL(request.url, 'http://localhost');
                var segments = url.pathname.split('/').filter(Boolean);
                var accept = request.headers.accept || '';
                if (request.method !== 'GET' || accept.indexOf('text/html') === -1 || ROUTES.indexOf(segments[0]) === -1) {
                    next();
                    return;
                }
                var query = url.search ? '&' + url.search.slice(1).replace(/&/g, '~and~') : '';
                response.writeHead(302, { Location: '/?/' + segments.join('/') + query });
                response.end();
            },
        });
        return result;
    };
})(config);
