/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.CommentPlacement

/**
 * Groups the blocks so that a section comes with the comments it opens with, every continuation of it that follows
 * and the comments, breaks and transpositions that cut it there, and the comments it ends with, in their order. Every
 * other block is a group of its own, and so is a comment written between two sections, or a break or a transposition
 * that no continuation follows.
 */
internal fun List<ChordProBlock>.joinCutSections(joinsTimings: Boolean = true): List<List<ChordProBlock>> {
    fun ChordProBlock.isJoinedCut() = isSectionCut() || (joinsTimings && this is ChordProBlock.Timing)
    val groups = mutableListOf<List<ChordProBlock>>()
    var start = 0
    while (start < size) {
        var end = start
        while (end < lastIndex && this[end].isCommentPlaced(CommentPlacement.START_OF_SECTION)) end++
        if (this[end] !is ChordProBlock.Section) end = start
        if (this[end] is ChordProBlock.Section) {
            while (true) {
                var next = end + 1
                while (next < size && this[next].isJoinedCut()) next++
                val continuation = getOrNull(next) as? ChordProBlock.Section
                if (continuation?.isContinuation != true) break
                end = next
            }
            // A break or a transposition between the section and a comment it ends with is joined over, as it would be
            // between two of its halves.
            var next = end + 1
            while (next < size && (this[next] is ChordProBlock.Break || this[next] is ChordProBlock.Transpose ||
                    (joinsTimings && this[next] is ChordProBlock.Timing) || this[next].isCommentPlaced(CommentPlacement.IN_SECTION))) {
                if (this[next] is ChordProBlock.Comment) end = next
                next++
            }
        }
        groups += subList(start, end + 1)
        start = end + 1
    }
    return groups
}

/** The section a group of [joinCutSections] is of, or its only block where it is not one. */
internal fun List<ChordProBlock>.firstSection() = firstOrNull { it is ChordProBlock.Section } ?: first()

private fun ChordProBlock.isCommentPlaced(placement: CommentPlacement) = this is ChordProBlock.Comment && this.placement == placement

/**
 * Whether the block is one that cuts a section in two without being a section itself (see `ChordProParser`), and is
 * always joined over; a [ChordProBlock.Timing] is one too, joined over only where it is not shown.
 */
private fun ChordProBlock.isSectionCut() = this is ChordProBlock.Comment || this is ChordProBlock.Break || this is ChordProBlock.Transpose
