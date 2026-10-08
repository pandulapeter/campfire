/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.SectionType

/** Resolves what every `{chorus}` of a parsed song repeats, see [withChorusesRecalled]. */
internal object ChordProChorusRecalls {

    /**
     * [blocks] with every recall carrying the chorus it repeats: the last one that was over by the time the recall is
     * reached, all of it — a chorus cut in two by a comment is its first section, the comment and the continuation. A
     * recall standing inside a chorus (in a tab written there) repeats the chorus before that one, since the one it
     * stands in is not over yet. A recall inside the recalled chorus is left out of the copy, which would otherwise
     * repeat a chorus inside a repeat of itself, and so are a `{transpose}`, a `{tempo}` and a `{time}` inside it: a
     * recalled chorus is played at the transposition, the tempo and in the time of the place it is recalled at.
     *
     * The recalls of a song together repeat at most [RECALL_BUDGET_FLOOR] plus [RECALL_BUDGET_PER_WEIGHT] times the
     * weight of what the song writes down itself (see [recallWeight]); a recall past that carries no chorus and is drawn
     * as its heading alone, the way one with no chorus before it is. Every later step — the transposition, the notation,
     * the viewer and the PDF — expands each recall on its own, so without a bound a small file of recalls is millions of
     * lines to all of them. The weight counts characters and chords as well as lines, since one long line recalled a
     * thousand times costs as much as a thousand lines.
     */
    internal fun withChorusesRecalled(blocks: List<ChordProBlock>): List<ChordProBlock> {
        if (blocks.none { it is ChordProBlock.ChorusRecall }) return blocks
        // Every chorus, by the index of its last piece; ascending, since they are found in order.
        val choruses = mutableListOf<Triple<Int, List<ChordProBlock>, Int>>()
        var pieces: MutableList<ChordProBlock>? = null
        var lastPieceIndex = -1
        blocks.forEachIndexed { index, block ->
            if (block !is ChordProBlock.Section) return@forEachIndexed
            val chorus = pieces
            if (chorus != null && block.isContinuation) {
                chorus += blocks.subList(lastPieceIndex + 1, index).filterNot {
                    it is ChordProBlock.ChorusRecall || it is ChordProBlock.Transpose || it is ChordProBlock.Timing
                }
                chorus += block
            } else {
                chorus?.let { choruses += recalledChorus(lastPieceIndex, it + blocks.commentsEnding(lastPieceIndex)) }
                pieces = if (block.type == SectionType.Chorus) (blocks.commentsOpening(index) + block).toMutableList() else null
            }
            lastPieceIndex = index
        }
        pieces?.let { choruses += recalledChorus(lastPieceIndex, it + blocks.commentsEnding(lastPieceIndex)) }
        var budget = RECALL_BUDGET_FLOOR + RECALL_BUDGET_PER_WEIGHT * blocks.sumOf { it.recallWeight() }
        var chorusIndex = -1
        return blocks.mapIndexed { index, block ->
            if (block !is ChordProBlock.ChorusRecall) return@mapIndexed block
            while (chorusIndex + 1 < choruses.size && choruses[chorusIndex + 1].first < index) chorusIndex++
            val (_, chorus, weight) = choruses.getOrNull(chorusIndex) ?: return@mapIndexed block.copy(blocks = emptyList())
            // A recall past the budget keeps its heading and repeats nothing, the way one with no chorus before it does;
            // it is never cut in the middle, and a later, smaller chorus may still fit.
            if (weight > budget) return@mapIndexed block.copy(blocks = emptyList())
            budget -= weight
            block.copy(blocks = chorus)
        }
    }

    /** A chorus whose last piece is at [lastPieceIndex], with its weight, weighed once however often it is recalled. */
    private fun recalledChorus(lastPieceIndex: Int, blocks: List<ChordProBlock>) = Triple(lastPieceIndex, blocks, blocks.sumOf { it.recallWeight() })

    /** What a recall of this block makes every later step do: its rows, its characters and its chords. */
    private fun ChordProBlock.recallWeight(): Int = when (this) {
        is ChordProBlock.Section -> (label?.length ?: 0) + lines.sumOf { it.recallWeight() }
        is ChordProBlock.Comment -> RECALL_PIECE_WEIGHT + text.length
        // Its own line, not what it repeats, which is what the budget is spent on.
        is ChordProBlock.ChorusRecall -> RECALL_PIECE_WEIGHT + (label?.length ?: 0)
        else -> RECALL_PIECE_WEIGHT
    }

    private fun ChordProLine.recallWeight(): Int = RECALL_PIECE_WEIGHT + when (this) {
        is ChordProLine.Lyrics -> text.length + chords.sumOf { it.name.length }
        is ChordProLine.Tab -> text.length
        is ChordProLine.Grid -> tokens.size
        ChordProLine.Blank -> 0
    }

    /** The comments the section at [index] opens with, which are part of the chorus a recall repeats. */
    private fun List<ChordProBlock>.commentsOpening(index: Int) =
        subList(0, index).takeLastWhile { (it as? ChordProBlock.Comment)?.placement == CommentPlacement.START_OF_SECTION }

    /** The comments the section whose last piece is at [index] ends with, which are part of the chorus a recall repeats. */
    private fun List<ChordProBlock>.commentsEnding(index: Int) = subList(index + 1, size)
        .takeWhile {
            it is ChordProBlock.Break || it is ChordProBlock.Transpose || it is ChordProBlock.Timing ||
                (it as? ChordProBlock.Comment)?.placement == CommentPlacement.IN_SECTION
        }
        .filterIsInstance<ChordProBlock.Comment>()

    /**
     * The weight every song's recalls may repeat whatever its size: a long live version's 40-line chorus recalled 20
     * times is about 49 000, so no real song loses a recall to it.
     */
    private const val RECALL_BUDGET_FLOOR = 64_000

    /**
     * How many times its own weight a song's recalls may repeat on top of [RECALL_BUDGET_FLOOR], so that a songbook
     * pasted into one file keeps its recalls while the worst case stays a constant factor of what the file costs to parse.
     */
    private const val RECALL_BUDGET_PER_WEIGHT = 2

    /** What a line, a comment or a break weighs before its characters: the cost of a row, however short. */
    private const val RECALL_PIECE_WEIGHT = 8
}
