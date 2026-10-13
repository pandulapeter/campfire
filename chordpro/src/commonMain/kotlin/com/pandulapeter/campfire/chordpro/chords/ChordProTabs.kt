/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.TabNotice
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.chordpro.model.TabTuning
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens

/**
 * The tabs of a parsed song as one reader is shown them: which of the tabs written for several instruments they see
 * ([select]), and what they are told about the ones that were moved for them or left out ([withNotices]). The moving
 * itself is the transposition's (see `ChordProTransposer.transpose`'s `tabPlayer`), which runs between the two.
 */
public object ChordProTabs {

    /**
     * [song] with only the tabs [selectors] pick out of the ones written for particular instruments.
     *
     * A tab written for an instrument (`{start_of_tab-ukulele}`) is one of the versions of the music around it, so the
     * tabs with selectors written one after the other, with nothing but blank lines and comments between them, are one
     * group, and of each group only the ones for the first of [selectors] it has are kept. A group with none for the
     * reader keeps its guitar tab, or its first one where it has none, rather than nothing: the song's author wrote the
     * music down once for somebody, and a tab moved to the reader's strings, or shown with a notice, is more use than a
     * gap. A tab with a negated selector (`{start_of_tab-guitar!}`) is for everybody but the players of that instrument,
     * and a tab without a selector for everybody. A comment written inside a tab goes with it.
     */
    public fun select(song: ChordProSong, selectors: List<String>): ChordProSong =
        song.copy(blocks = selectBlocks(song.blocks, selectors))

    private fun selectBlocks(blocks: List<ChordProBlock>, selectors: List<String>): List<ChordProBlock> {
        val environments = TabEnvironments(blocks)
        if (environments.selectors.all { it == null }) return blocks.map { block -> block.withRecallSelected(selectors) }
        val shown = environments.shown(selectors)
        val selected = blocks.mapIndexedNotNull { blockIndex, block ->
            when (block) {
                is ChordProBlock.Section -> block.copy(
                    lines = block.lines.filterIndexed { lineIndex, _ -> environments.of(blockIndex, lineIndex)?.let { it in shown } ?: true },
                ).takeUnless { section -> section.lines.all { it == ChordProLine.Blank } }
                is ChordProBlock.Comment -> block.takeIf { environments.of(blockIndex)?.let { it in shown } ?: true }
                else -> block.withRecallSelected(selectors)
            }
        }
        return selected.withPlacementsMended()
    }

    private fun ChordProBlock.withRecallSelected(selectors: List<String>) =
        if (this is ChordProBlock.ChorusRecall) copy(blocks = selectBlocks(blocks, selectors)) else this

    /**
     * [song] with every notice the transposition left on a run of tablature written above it as a comment, in the words
     * [describe] gives it: a note about the tab, folded and left out with it ([ChordProBlock.Comment.isInTabOrGrid]),
     * which the song's page and the printed one both draw the way they draw any other. Where the reader's instrument
     * [TabPlayer.hidesTabs], every tab is taken out, its comments with it, and a notice of that stands in its place.
     */
    public fun withNotices(song: ChordProSong, player: TabPlayer, describe: (TabNotice) -> String): ChordProSong =
        song.copy(blocks = blocksWithNotices(song.blocks, player, describe))

    private fun blocksWithNotices(blocks: List<ChordProBlock>, player: TabPlayer, describe: (TabNotice) -> String): List<ChordProBlock> {
        val result = mutableListOf<ChordProBlock>()
        blocks.forEach { block ->
            when (block) {
                is ChordProBlock.Section -> result += sectionWithNotices(block, player, describe)
                is ChordProBlock.Comment -> if (!(player.hidesTabs && block.isInTab)) result += block
                is ChordProBlock.ChorusRecall -> result += block.copy(blocks = blocksWithNotices(block.blocks, player, describe))
                else -> result += block
            }
        }
        return result.withPlacementsMended()
    }

    /**
     * The section cut in front of every run of tablature it says something about, with the notice between the pieces
     * as the comment a file would have there, and the run taken out where tabs are hidden.
     */
    private fun sectionWithNotices(section: ChordProBlock.Section, player: TabPlayer, describe: (TabNotice) -> String): List<ChordProBlock> {
        val runs = runsOf(section.lines)
        if (runs.none { run -> player.hidesTabs || (section.lines[run.first] as ChordProLine.Tab).notice != null }) return listOf(section)
        val pieces = mutableListOf<ChordProBlock>()
        var lines = mutableListOf<ChordProLine>()
        var isContinuation = section.isContinuation
        fun cut(notice: TabNotice) {
            if (lines.any { it != ChordProLine.Blank }) {
                pieces += section.copy(lines = lines, isContinuation = isContinuation)
                isContinuation = true
            }
            lines = mutableListOf()
            pieces += ChordProBlock.Comment(
                text = describe(notice),
                style = CommentStyle.ITALIC,
                placement = if (isContinuation) CommentPlacement.IN_SECTION else CommentPlacement.START_OF_SECTION,
                isInTabOrGrid = true,
                isInTab = !player.hidesTabs,
            )
        }
        var index = 0
        while (index < section.lines.size) {
            val run = runs.firstOrNull { it.first == index }
            val first = section.lines[index]
            when {
                run != null && player.hidesTabs -> {
                    val tabs = run.map { section.lines[it] }.filterIsInstance<ChordProLine.Tab>()
                    cut(TabNotice(TabNotice.Kind.HIDDEN, instrument = instrumentOf(tabs, player)))
                    index = run.last + 1
                    continue
                }
                run != null && first is ChordProLine.Tab && first.notice != null -> cut(first.notice)
            }
            lines += section.lines[index]
            index++
        }
        if (lines.any { it != ChordProLine.Blank }) pieces += section.copy(lines = lines, isContinuation = isContinuation)
        // A notice whose run was taken out with nothing of the section after it is about the section before it, or,
        // where nothing of the section is left at all, about nothing but itself.
        return pieces.mapIndexed { index, piece ->
            if (piece !is ChordProBlock.Comment || pieces.drop(index + 1).any { it is ChordProBlock.Section }) return@mapIndexed piece
            piece.copy(placement = if (pieces.take(index).any { it is ChordProBlock.Section }) CommentPlacement.IN_SECTION else CommentPlacement.BETWEEN_SECTIONS)
        }
    }

    /** The instrument a hidden run is for: the one its selector names, or the one as many strings make it most likely. */
    private fun instrumentOf(tabs: List<ChordProLine.Tab>, player: TabPlayer): String? {
        val lines = tabs.map { it.text }
        val isStaffLine = lines.map(ChordProTokens::isStaffLine)
        val strings = ChordProTabWrapper.systems(lines, isStaffLine).firstOrNull { range -> range.any { isStaffLine[it] } }
            ?.count { isStaffLine[it] } ?: return null
        return player.assumedTuning(strings, tabs.first().selector?.takeUnless { it.endsWith(NEGATION) })?.instrument
    }

    /** The runs of tablature among [lines]: from a tab line that starts an environment to the last tab line of it. */
    private fun runsOf(lines: List<ChordProLine>): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        var end = -1
        lines.forEachIndexed { index, line ->
            when {
                line is ChordProLine.Tab -> {
                    if (start < 0 || !line.continuesEnvironment) {
                        if (start >= 0) runs += start..end
                        start = index
                    }
                    end = index
                }
                line == ChordProLine.Blank -> Unit
                start >= 0 -> {
                    runs += start..end
                    start = -1
                }
            }
        }
        if (start >= 0) runs += start..end
        return runs
    }

    /**
     * Where a section was taken out from between its comments, the comments it opened with no longer open anything:
     * they are written between sections again, which is where the reader finds them, rather than over whatever section
     * comes next. A section taken out from after its first half leaves the comments that half ended with in its place.
     */
    private fun List<ChordProBlock>.withPlacementsMended(): List<ChordProBlock> = mapIndexed { index, block ->
        if (block is ChordProBlock.Comment && block.placement == CommentPlacement.START_OF_SECTION) {
            val next = drop(index + 1).firstOrNull { !(it is ChordProBlock.Comment && it.placement == CommentPlacement.START_OF_SECTION) }
            if (next is ChordProBlock.Section && !next.isContinuation) block else block.copy(placement = CommentPlacement.BETWEEN_SECTIONS)
        } else {
            block
        }
    }

    /**
     * The tab environments of a list of blocks, numbered in the order they are written: which one each tab line and
     * each comment written inside a tab belongs to, and the selector of each.
     */
    private class TabEnvironments(blocks: List<ChordProBlock>) {

        val selectors = mutableListOf<String?>()

        /** The groups of [select]: environments with a selector written one after the other. */
        private val groups = mutableListOf<MutableList<Int>>()
        private val lineEnvironments = mutableMapOf<Pair<Int, Int>, Int>()
        private val commentEnvironments = mutableMapOf<Int, Int>()

        init {
            var current = -1
            var isGroupOpen = false
            // A comment written inside a tab before its first line belongs to the environment that line opens.
            val pendingComments = mutableListOf<Pair<Int, ChordProBlock.Comment>>()
            fun settlePending(next: Int?) {
                pendingComments.forEach { (blockIndex, comment) ->
                    val environment = next?.takeIf { selectors[it] == comment.tabSelector } ?: current.takeIf { it >= 0 } ?: next
                    environment?.let { commentEnvironments[blockIndex] = it }
                }
                pendingComments.clear()
            }
            blocks.forEachIndexed { blockIndex, block ->
                when (block) {
                    is ChordProBlock.Section -> block.lines.forEachIndexed { lineIndex, line ->
                        when {
                            line is ChordProLine.Tab -> {
                                if (!line.continuesEnvironment || current < 0) {
                                    val environment = selectors.size
                                    selectors += line.selector
                                    settlePending(environment)
                                    val selector = line.selector
                                    if (selector != null && !selector.endsWith(NEGATION)) {
                                        if (isGroupOpen) groups.last() += environment else groups += mutableListOf(environment)
                                        isGroupOpen = true
                                    } else {
                                        groups += mutableListOf(environment)
                                        isGroupOpen = false
                                    }
                                    current = environment
                                } else {
                                    settlePending(current)
                                }
                                lineEnvironments[blockIndex to lineIndex] = current
                            }
                            line == ChordProLine.Blank -> Unit
                            else -> {
                                settlePending(null)
                                isGroupOpen = false
                            }
                        }
                    }
                    is ChordProBlock.Comment -> if (block.isInTab) pendingComments += blockIndex to block
                    else -> {
                        settlePending(null)
                        isGroupOpen = false
                    }
                }
            }
            settlePending(null)
        }

        fun of(blockIndex: Int, lineIndex: Int) = lineEnvironments[blockIndex to lineIndex]

        fun of(blockIndex: Int) = commentEnvironments[blockIndex]

        /** The environments [selectors] see, see [select]. */
        fun shown(selectors: List<String>): Set<Int> = groups.flatMap { group ->
            val selector = this.selectors[group.first()]
            when {
                selector == null -> group
                selector.endsWith(NEGATION) -> group.takeIf { selector.removeSuffix(NEGATION) !in selectors }.orEmpty()
                else -> {
                    val ranks = group.associateWith { selectors.indexOf(this.selectors[it]) }
                    val best = ranks.values.filter { it >= 0 }.minOrNull()
                    when {
                        best != null -> group.filter { ranks[it] == best }
                        group.any { this.selectors[it] == FALLBACK_SELECTOR } -> group.filter { this.selectors[it] == FALLBACK_SELECTOR }
                        else -> group.filter { this.selectors[it] == selector }
                    }
                }
            }
        }.toSet()
    }

    /**
     * The lines of an empty staff of one bar for [strings] (as [TabTuning.strings] holds them, the bottom string first),
     * each named the way a tab moved to them is (`e|----------------|`), which is what the editor writes for a new tab:
     * a staff that names its strings says what it is written in to whoever reads it on another tuning.
     */
    public fun emptyStaff(strings: List<Int>, stringName: (Int) -> String): List<String> {
        val names = ChordProTabRetuner.stringNamesOf(strings.asReversed(), stringName).map { it.orEmpty() }
        val width = names.maxOfOrNull { it.length } ?: 0
        return names.map { name -> name.padStart(width) + "|" + "-".repeat(EMPTY_BAR_LENGTH) + "|" }
    }

    /** What a tab with a selector for none of the reader's instruments falls back to: the instrument most tabs are for. */
    private const val FALLBACK_SELECTOR = "guitar"
    private const val EMPTY_BAR_LENGTH = 16
    private const val NEGATION = "!"
}
