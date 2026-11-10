/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.abc.api.AbcMessage
import com.pandulapeter.campfire.chordpro.edit.ChordProAbcTunes

/**
 * The tokens of the editor's [text] that the notation engine complained about while engraving its tunes, as ranges of
 * [text], drawn in the error colour so that what keeps a tune from rendering, or what it skipped, is found where it was
 * typed.
 *
 * A check runs a while after typing stops, so the field shows a newer text most of the time: [rangesIn] carries the
 * ranges over to it, those of the stretch that changed dropped until the next check.
 */
internal class AbcProblems(
    val text: String,
    val ranges: List<IntRange>,
) {
    private var lastText: CharSequence? = null
    private var lastRanges: List<IntRange> = ranges

    /** [ranges] in [current]: kept above the edit since [text], moved by its change in length below it. */
    fun rangesIn(current: CharSequence): List<IntRange> {
        if (current === lastText) return lastRanges
        if (ranges.isEmpty()) return ranges
        val prefix = text.commonPrefixWith(current).length
        val suffix = text.commonSuffixWith(current).length.coerceAtMost(minOf(text.length, current.length) - prefix)
        val changedEnd = text.length - suffix
        val delta = current.length - text.length
        return ranges.mapNotNull { range ->
            when {
                range.last < prefix -> range
                range.first >= changedEnd -> range.first + delta..range.last + delta
                else -> null
            }
        }.also {
            lastText = current
            lastRanges = it
        }
    }

    companion object {

        /**
         * The problems of [text], whose tunes are [tunes] and each tune's messages those [messagesOf] answers: every message
         * placed on the token it names, one with no line put nowhere.
         */
        fun of(text: String, tunes: List<ChordProAbcTunes.Tune>, messagesOf: (ChordProAbcTunes.Tune) -> List<AbcMessage>) = AbcProblems(
            text = text,
            ranges = tunes.flatMap { tune ->
                messagesOf(tune).mapNotNull { message -> message.line?.let { tune.rangeAt(line = it, column = message.column) } }
            }.distinct().sortedBy { it.first },
        )
    }
}
