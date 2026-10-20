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

/**
 * What the song's own transposition stepper reads for [transposition]: the [key] it takes the song to, alone, where
 * there is one, since its highlight and reset mark already say that it is moved, and the pill has no label to say
 * what it is; the semitones where the song names no key. [transpositionLabel] is what it says aloud.
 */
internal fun transpositionValue(transposition: Int, key: String?) = key?.takeIf { it.isNotBlank() } ?: transpositionLabel(transposition, null)
