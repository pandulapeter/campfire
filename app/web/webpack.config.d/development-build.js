/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// What makes the development build (wasmJsBrowserDevelopmentRun) a debug one, which the production build served the same
// way (wasmJsBrowserProductionRun) and a distribution never are:
// - its page says so (window.campfireDevelopmentBuild, which isDebugBuild in Platform.wasmJs.kt reads), injected into the
//   page as served, so index.html itself stays the one every build ships;
// - the favicons (and the splash's icon, which is one of them) come from debug-favicons/, the logo drawn with a dashed
//   line, under the release names, so FaviconEffect.kt and the service worker stay as they are.
//
// __dirname is build/wasm/packages/<package> at the repository root.
;(function (config) {
    if (!config.devServer || config.mode !== 'development') {
        return;
    }
    var path = require('path');
    var fs = require('fs');
    var root = path.resolve(__dirname, '../../../../app/web');
    var favicons = path.join(root, 'debug-favicons');
    var page = path.join(root, 'src/wasmJsMain/resources/index.html');
    var setupMiddlewares = config.devServer.setupMiddlewares;
    config.devServer.setupMiddlewares = function (middlewares, devServer) {
        var result = setupMiddlewares ? setupMiddlewares(middlewares, devServer) : middlewares;
        result.unshift({
            name: 'campfire-development-build',
            middleware: function (request, response, next) {
                var pathname = new URL(request.url, 'http://localhost').pathname;
                var name = pathname.split('/').pop();
                if (request.method === 'GET' && (pathname === '/' || pathname === '/index.html')) {
                    var html = fs.readFileSync(page, 'utf8').replace('<head>', '<head>\n<script>window.campfireDevelopmentBuild = true;</script>');
                    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
                    response.end(html);
                    return;
                }
                var file = path.join(favicons, name);
                if (!/^favicon-[a-z]+\.png$/.test(name) || !fs.existsSync(file)) {
                    next();
                    return;
                }
                response.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' });
                fs.createReadStream(file).pipe(response);
            },
        });
        return result;
    };
})(config);
