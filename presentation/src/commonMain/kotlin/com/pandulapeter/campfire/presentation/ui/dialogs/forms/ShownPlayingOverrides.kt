/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo

/**
 * The overrides [songPlayingOverrides] names and resets: a [transposition] other than zero, a [capo] and a [tempo]
 * other than the song's own, each zero or null where there is none or where it is not shown.
 */
internal data class ShownPlayingOverrides(
    val transposition: Int,
    val capo: EffectiveCapo?,
    val tempo: EffectiveTempo?,
)

/**
 * Which of [song]'s overrides are shown: the transposition and the capo go with the chords ([areChordsEnabled]), and
 * the transposition with a song that has some, the tempo with the metronome ([isMetronomeEnabled]).
 */
internal fun shownPlayingOverrides(
    song: Song,
    transposition: Int,
    capo: EffectiveCapo,
    tempo: EffectiveTempo,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
) = ShownPlayingOverrides(
    transposition = transposition.takeIf { areChordsEnabled && song.hasChords } ?: 0,
    capo = capo.takeIf { areChordsEnabled && !it.isDefault },
    tempo = tempo.takeIf { isMetronomeEnabled && !it.isDefault },
)
