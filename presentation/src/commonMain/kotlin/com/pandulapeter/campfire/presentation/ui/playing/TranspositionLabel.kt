/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.presentation.ui.components.KEY_SEPARATOR

/** What the transposition stepper reads for [transposition], with the [key] it takes the song to where there is one. */
internal fun transpositionLabel(transposition: Int, key: String?) = (if (transposition > 0) "+$transposition" else transposition.toString())
    .let { if (key.isNullOrBlank()) it else "$it $KEY_SEPARATOR $key" }
