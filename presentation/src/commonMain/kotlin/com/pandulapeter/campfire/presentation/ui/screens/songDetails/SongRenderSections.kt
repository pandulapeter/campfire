/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.size
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.UNNAMED_SECTION_HEADER
import com.pandulapeter.campfire.presentation.ui.songLayout.areAll
import com.pandulapeter.campfire.presentation.ui.songLayout.header
import com.pandulapeter.campfire.presentation.ui.songLayout.withNumberedSections

/**
 * Flattens the parsed song into the sections the layout places.
 *
 * A section a comment cut in two (see `ChordProBlock.Section.isContinuation`) is put back together here, with the
 * comment between its halves, since the layout would otherwise place the comment and each half as units of their own,
 * free to land in different columns or rows. The comments a section opens or ends with are put inside it too
 * (`ChordProBlock.Comment.placement`), so that every comment written in a section folds away with it, while one written
 * between two sections stays a unit of its own that nothing folds. Breaks and `{transpose}` directives cut a section
 * the same way, so they are joined over as well, a `{transpose}` leaving a line that names the new key where it stood,
 * for a song that declares one ([RenderSection.KeyChange]); a `{chorus}` recall is a section of its own and is not.
 *
 * A change of tempo or time signature ([ChordProBlock.Timing]) is joined over the same way where [showsTiming] is
 * false, leaving nothing behind. Otherwise it is the one cut besides a recall that is not: the section ends before it,
 * the change is a line of its own ([RenderSection.Timing]), and the rest of the section after it is headed by its fold
 * toggle alone, since it starts a page of its own (see [SongUnits.timingStarts]).
 *
 * A `{chorus}` recall repeats the chorus the parser found for it (`ChordProBlock.ChorusRecall.blocks`), every piece of
 * it and the comments inside it, headed once. In lyrics-only mode the chords go away with the sections that consist
 * of nothing else: tabs and grids say nothing without them, and a line that was only chords would leave a blank behind.
 * A comment written inside a tab or a grid goes with it, since it is a note about what is no longer there, and so does
 * the line of a key change, the key being the chords' own.
 */
internal fun ChordProSong.toRenderSections(
    shouldShowChords: Boolean,
    defaultLabels: DefaultSectionLabels,
    showsTiming: Boolean,
): List<RenderSection> {
    val sections = mutableListOf<RenderSection>()
    // The rest of a section a change cut is folded under a key of the section it continues, so that switching the
    // metronome on or off, which decides whether the change cuts it, does not renumber the folds after it.
    var lastFoldKey: String? = null
    var timingContinuations = 0
    var isAfterTiming = false
    // Counted for every section the file has, whether or not this mode shows it, so that lyrics-only mode dropping a
    // tab does not rename the sections after it.
    val foldNameCounts = mutableMapOf<String, Int>()

    /**
     * Adds a section and what [joinCutSections] joined to it, or only the comments where lyrics-only mode leaves it
     * with nothing else to show; true when a section was added.
     */
    fun addSection(pieces: List<ChordProBlock>, header: String?, foldKey: String): Boolean {
        val comments = pieces.filterIsInstance<ChordProBlock.Comment>().mapNotNull { it.toRenderSection(shouldShowChords) }
        val sectionPieces = pieces.filterIsInstance<ChordProBlock.Section>()
        // A section that is nothing but tablature or a grid goes away entirely in lyrics-only mode, its heading with
        // it: neither says anything without the chords, and a heading over nothing is worse than no heading at all.
        // The comments in it that are not about the tablature or the grid still say something, so they stay.
        if (!shouldShowChords && sectionPieces.all { piece -> piece.lines.all { it.needsChords() || it.isBlank() } }) {
            sections += comments
            return false
        }
        val parts = mutableListOf<SectionPart>()
        pieces.forEach { piece ->
            when (piece) {
                is ChordProBlock.Section -> {
                    val lines = piece.lines.prepareForDisplay(shouldShowChords)
                    if (lines.isEmpty()) return@forEach
                    // What cut the section there drew nothing (a break, a transposition of a song with no key to
                    // name), so the halves are one run of lines again, and a tab on either side of the cut is folded and
                    // wrapped as the one run it is.
                    val previous = parts.lastOrNull()
                    if (previous is SectionPart.Lines) {
                        parts[parts.lastIndex] = SectionPart.Lines(previous.lines + lines)
                    } else {
                        parts += SectionPart.Lines(lines)
                    }
                }

                is ChordProBlock.Comment -> piece.toRenderSection(shouldShowChords)?.let { parts += it }

                is ChordProBlock.Transpose -> piece.toRenderSection(shouldShowChords)?.let { parts += it }

                else -> Unit
            }
        }
        // A section that ended up with no line to show is dropped, unless its header still says something.
        if (parts.none { it is SectionPart.Lines } && header.isNullOrEmpty()) {
            sections += comments
            return false
        }
        sections += RenderSection.Lines(
            header = header,
            foldKey = foldKey,
            parts = parts,
            isOnCard = sectionPieces.first().type == SectionType.Chorus,
        )
        return true
    }

    blocks.withNumberedSections(defaultLabels).joinCutSections(joinsTimings = !showsTiming).forEach { pieces ->
        val block = pieces.firstSection()
        val continuesAfterTiming = isAfterTiming && block is ChordProBlock.Section && block.isContinuation
        isAfterTiming = block is ChordProBlock.Timing || (isAfterTiming && block !is ChordProBlock.Section)
        when (block) {
            is ChordProBlock.Break -> Unit // The column layout makes its own breaks.

            is ChordProBlock.Timing -> if (showsTiming) sections += block.toRenderSection()

            is ChordProBlock.Transpose -> block.toRenderSection(shouldShowChords)?.let { sections += it }

            is ChordProBlock.Comment -> block.toRenderSection(shouldShowChords)?.let { sections += it }

            is ChordProBlock.ChorusRecall -> {
                // The heading goes on the first piece of the chorus that is shown, and stays behind on its own when
                // none is: a recall has always said where the chorus is sung, even with nothing under it.
                val recalled = block.blocks.firstOrNull { it is ChordProBlock.Section } as? ChordProBlock.Section
                val label = block.label ?: recalled?.label
                var header: String? = block.label ?: recalled?.header(defaultLabels) ?: defaultLabels.chorus
                // A recall is folded apart from the chorus it repeats, as the chorus it is, and whatever of it is
                // shown after its first piece is named after that piece.
                val recallFoldKey = foldNameCounts.nextFoldKey(label ?: SectionType.Chorus.foldName)
                block.blocks.joinCutSections().forEachIndexed { index, recalled ->
                    when (val first = recalled.firstSection()) {
                        is ChordProBlock.Section -> {
                            val foldKey = if (index == 0) recallFoldKey else "$recallFoldKey/$index"
                            if (addSection(recalled, header, foldKey)) header = null
                        }

                        is ChordProBlock.Comment -> first.toRenderSection(shouldShowChords)?.let { sections += it }
                        else -> Unit
                    }
                }
                header?.let { sections += RenderSection.Lines(header = it, foldKey = recallFoldKey, parts = emptyList(), isOnCard = true) }
            }

            // The rest of a section a chorus recall cut in two was named where it started, so it is not named again,
            // but it still folds on its own: every part of a song can be folded away, and its chevron is all it needs.
            // Whatever else cuts a section was joined over by joinCutSections, and arrives here with its start.
            is ChordProBlock.Section -> addSection(
                pieces = pieces,
                header = if (block.isContinuation) UNNAMED_SECTION_HEADER else block.header(defaultLabels),
                foldKey = lastFoldKey?.takeIf { continuesAfterTiming }?.let { "$it$TIMING_CONTINUATION_SEPARATOR${++timingContinuations}" }
                    ?: foldNameCounts.nextFoldKey(block.foldName()).also {
                        lastFoldKey = it
                        timingContinuations = 0
                    },
            )
        }
    }
    return sections
}

/**
 * Groups the blocks so that a section comes with the comments it opens with, every continuation of it that follows
 * and the comments, breaks and transpositions that cut it there, and the comments it ends with, in their order. Every
 * other block is a group of its own, and so is a comment written between two sections, or a break or a transposition
 * that no continuation follows.
 */
private fun List<ChordProBlock>.joinCutSections(joinsTimings: Boolean = true): List<List<ChordProBlock>> {
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
private fun List<ChordProBlock>.firstSection() = firstOrNull { it is ChordProBlock.Section } ?: first()

private fun ChordProBlock.isCommentPlaced(placement: CommentPlacement) = this is ChordProBlock.Comment && this.placement == placement

/** What a comment is drawn as, or null where lyrics-only mode leaves out the tab or grid it is a note about. */
private fun ChordProBlock.Comment.toRenderSection(shouldShowChords: Boolean) =
    if (isInTabOrGrid && !shouldShowChords) null else RenderSection.Comment(text = text, style = style)

/** The line naming the key a modulation takes the song to, or null where the song declares none or the chords are not shown. */
private fun ChordProBlock.Transpose.toRenderSection(shouldShowChords: Boolean) =
    key?.takeIf { shouldShowChords && it.isNotBlank() }?.let(RenderSection::KeyChange)

/** The line naming how the song is played from a change on, read the way the click reads it. */
private fun ChordProBlock.Timing.toRenderSection() = RenderSection.Timing(
    tempo = ChordProTempo.parse(tempo)?.let(MetronomePattern::coerceBpm)?.toString(),
    time = (ChordProTime.parse(time)?.let { (beats, unit) -> TimeSignature(beats, unit) } ?: TimeSignature.COMMON_TIME).toString(),
)

/**
 * Whether the block is one that cuts a section in two without being a section itself (see `ChordProParser`), and is
 * always joined over; a [ChordProBlock.Timing] is one too, joined over only where it is not shown.
 */
private fun ChordProBlock.isSectionCut() = this is ChordProBlock.Comment || this is ChordProBlock.Break || this is ChordProBlock.Transpose

/**
 * What a section's fold is keyed by: its label as the file writes it, or what kind of section it is where it has
 * none. Never the heading it is shown under, which is translated for the sections the file leaves unnamed, and a
 * folded chorus would unfold with the app's language otherwise.
 */
private fun ChordProBlock.Section.foldName() = label ?: when (val sectionType = type) {
    is SectionType.Custom -> sectionType.name
    SectionType.Paragraph -> when {
        lines.areAll<ChordProLine.Tab>() -> FoldableKind.TAB.name.lowercase()
        lines.areAll<ChordProLine.Grid>() -> FoldableKind.GRID.name.lowercase()
        else -> sectionType.foldName
    }

    else -> sectionType.foldName
}

private val SectionType.foldName
    get() = when (this) {
        SectionType.Verse -> "verse"
        SectionType.Chorus -> "chorus"
        SectionType.Bridge -> "bridge"
        SectionType.Paragraph -> "paragraph"
        is SectionType.Custom -> name
    }

/** What joins the fold key of a section to the number of the stretch a change of tempo or time cut it into. */
private const val TIMING_CONTINUATION_SEPARATOR = "~"

/**
 * Drops the chords when they are not wanted, along with the lines that were nothing but chords, and trims the blank
 * lines off the end so that a section does not carry empty space into the column layout.
 *
 * Tablature and grids go with the chords: both say nothing at all without them, and now that they are runs inside a
 * section rather than sections of their own, the lines are what has to be dropped.
 */
private fun List<ChordProLine>.prepareForDisplay(shouldShowChords: Boolean): List<ChordProLine> = let { lines ->
    if (shouldShowChords) lines else lines.mapNotNull { line ->
        when (line) {
            is ChordProLine.Lyrics -> if (line.text.isBlank()) null else line.copy(chords = emptyList())
            else -> if (line.needsChords()) null else line
        }
    }
}.dropLastWhile { it.isBlank() }

/** Whether a line says nothing at all once the chords are hidden: tablature and a grid are chords and little else. */
private fun ChordProLine.needsChords() = this is ChordProLine.Tab || this is ChordProLine.Grid

internal fun ChordProLine.isBlank() = when (this) {
    ChordProLine.Blank -> true
    is ChordProLine.Lyrics -> text.isBlank() && chords.isEmpty()
    is ChordProLine.Tab -> text.isBlank()
    is ChordProLine.Grid -> tokens.isEmpty()
}
