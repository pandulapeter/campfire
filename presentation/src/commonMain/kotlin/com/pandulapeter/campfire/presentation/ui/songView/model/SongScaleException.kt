/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.scales.Scale

/**
 * A chord of a [SongScaleGroup] worth a scale of its own, with the scales to play over it: one row of chips of the
 * Scales section, titled by the chord.
 *
 * @property chordNames The chords the scales are played over, as the page shows them in the reader's notation, in play
 *   order: more than one where one scale serves several, a V and its V7.
 * @property rootName The note [scales] are played on, in the standard notation.
 */
@Immutable
internal data class SongScaleException(
    val chordNames: List<String>,
    val rootName: String,
    val scales: List<Scale>,
)
