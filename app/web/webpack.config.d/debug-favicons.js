/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The development server answers the favicons (and the splash's icon, which is one of them) from debug-favicons/, the
// logo drawn with a dashed line, under the release names, so index.html, FaviconEffect.kt, and the service worker stay
// as they are and a production build, which has no development server, can never pick one up.
//
// __dirname is build/wasm/packages/<package> at the repository root.
;(function (config) {
    if (!config.devServer) {
        return;
    }
    var path = require('path');
    var fs = require('fs');
    var folder = path.resolve(__dirname, '../../../../app/web/debug-favicons');
    var setupMiddlewares = config.devServer.setupMiddlewares;
    config.devServer.setupMiddlewares = function (middlewares, devServer) {
        var result = setupMiddlewares ? setupMiddlewares(middlewares, devServer) : middlewares;
        result.unshift({
            name: 'campfire-debug-favicons',
            middleware: function (request, response, next) {
                var name = new URL(request.url, 'http://localhost').pathname.split('/').pop();
                var file = path.join(folder, name);
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
