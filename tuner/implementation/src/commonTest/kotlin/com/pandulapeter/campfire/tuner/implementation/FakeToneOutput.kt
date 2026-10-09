/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

/** An output that plays nothing, answers [isPlayable] and keeps the last `onLost`, so that a test can take the tone away. */
internal class FakeToneOutput : ToneOutput {
    var isPlayable = true
    var onLost: (() -> Unit)? = null
        private set
    var playCount = 0
        private set
    var stopCount = 0
        private set

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        playCount++
        this.onLost = onLost
        return isPlayable
    }

    override fun stop() {
        stopCount++
    }
}
