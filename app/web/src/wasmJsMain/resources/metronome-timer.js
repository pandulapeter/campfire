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
 * The metronome's wake-up (see AudioOutput.wasmJs.kt in :metronome:implementation): the page schedules its clicks on
 * the audio clock a little ahead, and has to be woken to schedule the next ones. A hidden tab's own timers run at most
 * once a second, which would leave the click silent between them; a dedicated worker's are not throttled that way.
 */
var INTERVAL_MILLISECONDS = 25;

var timer = null;
self.onmessage = function (event) {
    if (event.data === 'start' && timer === null) {
        timer = setInterval(function () { self.postMessage('tick'); }, INTERVAL_MILLISECONDS);
    } else if (event.data === 'stop' && timer !== null) {
        clearInterval(timer);
        timer = null;
    }
};
