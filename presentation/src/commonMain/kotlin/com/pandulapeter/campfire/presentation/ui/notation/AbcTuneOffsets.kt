/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.presentation.ui.songLayout.groupIntoRuns

/**
 * The semitones each ABC tune of a song's [blocks] is moved by on top of the song's own transposition: those of the
 * last `{transpose}` written before it, as the chords around it are (a key change further down the song), 0 before the
 * first. A tune written twice is moved as it first stands.
 */
internal fun abcTuneOffsets(blocks: List<ChordProBlock>): Map<String, Int> {
    val offsets = LinkedHashMap<String, Int>()
    var offset = 0
    blocks.forEach { block ->
        when (block) {
            is ChordProBlock.Transpose -> offset = block.semitones
            is ChordProBlock.Section -> block.lines.groupIntoRuns().forEach { run -> run.abcTune()?.let { offsets.getOrPut(it) { offset } } }
            else -> Unit
        }
    }
    return offsets
}
