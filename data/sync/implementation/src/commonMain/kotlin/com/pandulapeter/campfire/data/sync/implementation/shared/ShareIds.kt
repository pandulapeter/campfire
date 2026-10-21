/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import kotlin.random.Random

/**
 * Names a new shared copy: 16 characters of `[a-z2-7]`, the form a link's grammar accepts. The id only has to be
 * unique, not secret, since what opens a copy is its link's key, which the service makes; 80 random bits are that.
 */
internal object ShareIds {

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
    private const val LENGTH = 16

    fun generate(random: Random = Random.Default) = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
