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
import com.pandulapeter.campfire.presentation.ui.songLayout.firstSection
import com.pandulapeter.campfire.presentation.ui.songLayout.groupIntoRuns

/** A tune of a song: its ABC text, and the semitones of the key change it stands after ([abcTunesOf]). */
internal data class AbcTune(val text: String, val offset: Int)

/**
 * The ABC tunes of a song's [blocks], in song order, each moved on top of the song's own transposition by the semitones
 * of the last `{transpose}` written before it, as the chords around it are (a key change further down the song), 0
 * before the first. A tune written again under another key change is another tune; one written again under the same
 * one is the same tune, rendered once. Only the song's own blocks move it: a key change inside the chorus a `{chorus}`
 * recall repeats moves nothing after the recall, as with the chords.
 */
internal fun abcTunesOf(blocks: List<ChordProBlock>): Set<AbcTune> {
    val tunes = LinkedHashSet<AbcTune>()
    var offset = 0
    blocks.forEach { block ->
        when (block) {
            is ChordProBlock.Transpose -> offset = block.semitones
            is ChordProBlock.Section -> block.lines.groupIntoRuns().forEach { run -> run.abcTune()?.let { tunes += AbcTune(it, offset) } }
            else -> Unit
        }
    }
    return tunes
}

/**
 * The semitones of the key change each of the `joinCutSections` [groups] starts under, the walk [abcTunesOf] makes, so
 * that the screen and the page find every tune where it was rendered: a group's last `{transpose}` moves the groups
 * after it, and a chorus recall moves nothing.
 */
internal fun groupStartOffsetsOf(groups: List<List<ChordProBlock>>): List<Int> {
    var offset = 0
    return groups.map { group ->
        offset.also {
            if (group.firstSection() !is ChordProBlock.ChorusRecall) {
                group.filterIsInstance<ChordProBlock.Transpose>().lastOrNull()?.let { offset = it.semitones }
            }
        }
    }
}
