/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.SectionType

/**
 * Accumulates the lines of the section that is currently open. Sections are appended to [blocks] when they close,
 * so that a comment or a break appearing inside one can be emitted in the right order.
 */
internal class SectionBuilder(private val blocks: MutableList<ChordProBlock>) {

    private var type: SectionType? = null
    private var label: String? = null
    private var lineMode: LineMode? = null

    /** The label of the tab or grid environment that is open, which each of its lines carries. */
    private var lineModeLabel: String? = null
    private val lines = mutableListOf<ChordProLine>()

    /**
     * How many blank lines [close] trimmed off the end of the section, which a continuation [addBlock] opened carries
     * over so that [rejoin] can put them back between the two halves.
     */
    private var trimmedBlankLineCount = 0

    /**
     * Whether [lines] holds anything but blank lines, kept as they are added: an environment keeps its blank lines,
     * and scanning them again for every block that follows would make a file of blanks and comments quadratic.
     */
    var hasContentLine = false
        private set

    /**
     * The `{comment}` a legacy heading section was opened by, which is what is left of it if no line ever
     * follows: a comment that happens to start with a section name is still a line of the user's file, and a
     * section with nothing in it has nowhere to show it.
     */
    private var headingText: String? = null

    var isExplicit = false
        private set

    private var isContinuation = false

    /** Whether the tab environment that is open has had a line yet, see [ChordProLine.Tab.continuesEnvironment]. */
    private var hasTabLine = false

    /**
     * Whether a part of this section was emitted already, so that a block added now stands inside it rather than
     * at its start (see [CommentPlacement]).
     */
    private var hasEmittedLines = false

    /** Where in [blocks] the comments this section opened with are, which [close] moves out of it if no line follows. */
    private val openingComments = mutableListOf<Int>()

    /**
     * Whether the tab or grid environment that is open has had a line that is not blank, and where in [blocks] the
     * comments written inside it are. These outlive the cut [addBlock] makes, since the environment does, and only
     * the end of the environment reads them ([finishLineMode]).
     */
    private var hasLineModeLine = false
    private val lineModeComments = mutableListOf<Int>()

    /**
     * Whether a `{start_of_tab}`, a `{start_of_grid}` or one of the verbatim environments is open, which says how
     * lines are read and not what section they are in.
     */
    val isInLineMode get() = lineMode != null

    /** Whether one of the environments ChordPro hands to another program is open, see [ChordProSyntax.matchDelegatedDirective]. */
    val isDelegated get() = lineMode == LineMode.VERBATIM

    /** Whether the running paragraph was opened by the tab or grid environment that is open, see [closeLineMode]. */
    private var isOpenedByLineMode = false

    fun open(
        type: SectionType,
        label: String?,
        isExplicit: Boolean,
        headingText: String? = null,
        isContinuation: Boolean = false,
    ) {
        this.type = type
        this.label = label
        this.isExplicit = isExplicit
        this.headingText = headingText
        this.isContinuation = isContinuation
        hasEmittedLines = isContinuation
        hasTabLine = false
        isOpenedByLineMode = false
        openingComments.clear()
        lines.clear()
        hasContentLine = false
        trimmedBlankLineCount = 0
    }

    /**
     * A tab or grid environment starts. It never opens a section of its own, but it does need one to live in,
     * so a file that puts tablature outside every environment gets the same implicit paragraph a bare line of
     * lyrics would get - carrying the environment's own label as its heading. Inside a section that is already
     * running the section's own heading wins, and the environment's label is carried by its lines instead
     * ([ChordProLine.Tab.label], [ChordProLine.Grid.label]), which is where a viewer finds "Riff" for the run.
     */
    fun openLineMode(mode: LineMode, label: String?) {
        if (type == null) {
            open(SectionType.Paragraph, label = label, isExplicit = false)
            isOpenedByLineMode = true
        }
        lineMode = mode
        lineModeLabel = label
        hasTabLine = false
        hasLineModeLine = false
        lineModeComments.clear()
    }

    /**
     * An environment that opened a paragraph of its own and wrote nothing in it leaves nothing behind: its label
     * names that environment, and a line after it would otherwise be headed by it.
     */
    fun closeLineMode() {
        finishLineMode()
        lineMode = null
        lineModeLabel = null
        if (isOpenedByLineMode && lines.all { it == ChordProLine.Blank }) close()
        isOpenedByLineMode = false
    }

    fun close() {
        finishLineMode()
        lineMode = null
        lineModeLabel = null
        val type = type ?: return
        trimmedBlankLineCount = 0
        while (lines.isNotEmpty() && lines.last() == ChordProLine.Blank) {
            lines.removeAt(lines.lastIndex)
            trimmedBlankLineCount++
        }
        if (lines.isNotEmpty()) {
            blocks += ChordProBlock.Section(type = type, label = label, lines = lines.toList(), isContinuation = isContinuation)
        } else {
            if (!hasEmittedLines) openingComments.forEach { blocks.replaceComment(it) { copy(placement = CommentPlacement.BETWEEN_SECTIONS) } }
            headingText?.let { blocks += ChordProBlock.Comment(it, CommentStyle.PLAIN) }
        }
        openingComments.clear()
        this.type = null
        label = null
        isExplicit = false
        isContinuation = false
        hasTabLine = false
        isOpenedByLineMode = false
        hasEmittedLines = false
        headingText = null
        lines.clear()
        hasContentLine = false
    }

    /**
     * A comment in a tab or grid environment that never had a line is not a note about one: there is nothing it
     * would be hidden with, and nothing for the serializer to write it back inside.
     */
    private fun finishLineMode() {
        if (lineMode == null) return
        if (!hasLineModeLine) lineModeComments.forEach { blocks.replaceComment(it) { copy(isInTabOrGrid = false) } }
        lineModeComments.clear()
    }

    private fun MutableList<ChordProBlock>.replaceComment(index: Int, change: ChordProBlock.Comment.() -> ChordProBlock.Comment) {
        this[index] = (this[index] as ChordProBlock.Comment).change()
    }

    /**
     * Emits a standalone block without losing the section around it: the section is flushed and then reopened.
     * Only a section with lines in it is flushed, so the heading of the one being reopened has already been shown
     * as its label, and the reopened half does not carry [headingText]: were nothing to follow the block, it
     * would otherwise come back as a comment repeating that label. A tab or grid environment that is open carries
     * on in the reopened half. The reopened half is marked as the continuation it is, so that it is neither headed a
     * second time nor left out of a recall of its chorus.
     */
    fun addBlock(block: ChordProBlock) {
        // Blank lines alone are not a part of the section yet: flushed, they would be trimmed away, and the lines
        // after the block would be the continuation of a section that never started.
        val isCut = type != null && hasContentLine
        val placedBlock = if (block is ChordProBlock.Comment) block.placed(isCut) else block
        if (isCut) {
            val type = this.type!!
            val label = this.label
            val isExplicit = this.isExplicit
            // The block interrupts the section and not the tab or grid environment the file is in the middle of:
            // that one ends at its own `{end_of_…}`, which is where the text transposition, the summary and the
            // highlighter end it as well.
            val lineMode = this.lineMode
            val lineModeLabel = this.lineModeLabel
            val hasTabLine = this.hasTabLine
            this.lineMode = null
            close()
            val trimmedBlankLineCount = this.trimmedBlankLineCount
            blocks += placedBlock
            open(type, label, isExplicit, isContinuation = true)
            this.trimmedBlankLineCount = trimmedBlankLineCount
            this.lineMode = lineMode
            this.lineModeLabel = lineModeLabel
            this.hasTabLine = hasTabLine
        } else {
            blocks += placedBlock
        }
        if (placedBlock is ChordProBlock.Comment) {
            if (placedBlock.placement == CommentPlacement.START_OF_SECTION) openingComments += blocks.lastIndex
            if (placedBlock.isInTabOrGrid) lineModeComments += blocks.lastIndex
        }
    }

    /**
     * Undoes the cut [addBlock] made for a block that was taken out again: the half it emitted is the last of the
     * [blocks], and it becomes the start of the open section once more, with the blank lines [close] trimmed off it
     * and whatever blank lines the continuation holds already. Nothing happens where the block cut nothing.
     */
    fun rejoin() {
        if (!isContinuation || hasContentLine) return
        val removed = blocks.lastOrNull() as? ChordProBlock.Section ?: return
        if (removed.type != type || removed.label != label) return
        blocks.removeAt(blocks.lastIndex)
        val continued = lines.toList()
        lines.clear()
        lines += removed.lines
        repeat(trimmedBlankLineCount) { lines += ChordProLine.Blank }
        lines += continued
        isContinuation = removed.isContinuation
        hasEmittedLines = removed.isContinuation
        hasContentLine = true
        trimmedBlankLineCount = 0
    }

    /**
     * Where a comment written now stands. Only an environment is a section to the reader: the lines of an implicit
     * paragraph are just lines, unless a legacy heading named them.
     */
    private fun ChordProBlock.Comment.placed(isCut: Boolean): ChordProBlock.Comment {
        val isInSection = type.let { it != null && (isExplicit || lineMode != null || it != SectionType.Paragraph) }
        return copy(
            placement = when {
                !isInSection -> CommentPlacement.BETWEEN_SECTIONS
                isCut || hasEmittedLines -> CommentPlacement.IN_SECTION
                else -> CommentPlacement.START_OF_SECTION
            },
            isInTabOrGrid = lineMode == LineMode.TAB || lineMode == LineMode.GRID,
        )
    }

    fun addContent(rawLine: String, trimmedLine: String) {
        if (trimmedLine.isEmpty()) {
            when {
                type == null -> Unit
                isExplicit || lineMode != null -> lines += ChordProLine.Blank
                else -> close() // A blank line ends an implicit paragraph or a legacy heading section.
            }
            return
        }
        if (type == null) {
            open(SectionType.Paragraph, label = null, isExplicit = false)
        }
        if (lineMode == LineMode.TAB || lineMode == LineMode.GRID) hasLineModeLine = true
        lines += when (lineMode) {
            LineMode.TAB -> ChordProLine.Tab(rawLine, continuesEnvironment = hasTabLine, label = lineModeLabel).also { hasTabLine = true }
            LineMode.GRID -> ChordProLine.Grid(ChordProSyntax.parseGridTokens(trimmedLine), label = lineModeLabel)
            LineMode.VERBATIM -> ChordProLine.Lyrics(text = rawLine, chords = emptyList())
            null -> ChordProParser.parseLyrics(rawLine)
        }
        hasContentLine = true
    }
}

internal enum class LineMode { TAB, GRID, VERBATIM }
