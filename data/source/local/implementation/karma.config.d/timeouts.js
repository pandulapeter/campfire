/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// Merged into the karma.conf.js Gradle writes for wasmJsBrowserTest. OpfsStagedUnpackTimeTest unpacks a staged archive
// of 70,000 entries, which takes far longer than mocha's two seconds a test and prints nothing meanwhile; the test holds
// itself to its own budget, so these only keep the runner from ending it first.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 15 * 60 * 1000 });
config.browserNoActivityTimeout = 15 * 60 * 1000;
