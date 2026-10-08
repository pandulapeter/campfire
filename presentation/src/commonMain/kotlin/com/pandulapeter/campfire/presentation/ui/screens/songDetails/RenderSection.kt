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
import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle

/**
 * [text] with its runs of whitespace collapsed into single spaces and cut to [DESCRIPTION_LENGTH] characters, an ellipsis
 * marking the cut, short enough for a screen reader to read as the name of a button. The cut never falls between the two
 * halves of a surrogate pair, which would leave half a character for the screen reader to stumble over.
 */
internal fun shortenedForDescription(text: String): String {
    val collapsed = text.trim().split(WHITESPACE).joinToString(" ")
    if (collapsed.length <= DESCRIPTION_LENGTH) return collapsed
    val end = if (collapsed[DESCRIPTION_LENGTH - 1].isHighSurrogate()) DESCRIPTION_LENGTH - 1 else DESCRIPTION_LENGTH
    return collapsed.take(end) + "…"
}

private const val DESCRIPTION_LENGTH = 40
private val WHITESPACE = Regex("\\s+")

/**
 * One section of the song. The column layout places sections whole, or as the chunks they may be cut into as a last
 * resort (see [SongUnits]).
 *
 * Immutable, and marked so on every class rather than on the interface alone, since the compiler would infer the lists
 * they hold as unstable: they are built fresh by [toRenderSections] and never changed, which is what lets a section
 * whose content did not change skip recomposition.
 */
@Immutable
internal sealed interface RenderSection {

    /**
     * The descriptive metadata, inserted after the body budget is applied and kept whole in the first grid cell.
     *
     * @param hasPlayingControls Whether the key, capo, tempo and time are drawn as the controls that set them rather
     * than as one line of text, which is part of the content because the measured sizes of a section are kept by it.
     * @param readsCapoAndTime Whether that line names a capo of none too, see [withMetadataSection].
     */
    @Immutable
    data class Metadata(
        val metadata: ChordProMetadata,
        val hasPlayingControls: Boolean = false,
        val readsCapoAndTime: Boolean = false,
    ) : RenderSection

    /**
     * The diagrams of the song's chords, inserted after the metadata the way it is: one [ChordCell] per chord the song
     * plays, in the order they are first played, see [withChordsSection]. It is cut between its rows of diagrams like any
     * section, its rows drawn as slots the way a run of tablature is drawn as systems (see [SongChordsSection]).
     *
     * @param isFolded Whether only the header is shown, which is part of the content because the measured sizes of a
     * section are kept by it.
     */
    @Immutable
    data class Chords(
        val cells: List<ChordCell>,
        val isFolded: Boolean,
    ) : RenderSection {

        /** One slot per row the diagrams can wrap into ([chordSlotCount]), the first headed by the pill; only the pill while folded. */
        val itemCount = if (isFolded) 1 else chordSlotCount(cells.size)

        /** Every row may start a piece: a row of diagrams is a line, with the header kept with the first one. */
        val chunkStarts = sectionChunkStarts(List(itemCount) { SectionItemKind.CONTENT })
    }

    /** A titled block of lines: an environment, an implicit paragraph, or a repeated chorus. */
    @Immutable
    data class Lines(
        val header: String?,
        /** What the section is folded away by, see [FoldedRuns]. */
        val foldKey: String,
        /** The lines, and the comments that stand between them inside the section, in the order the file has them. */
        val parts: List<SectionPart>,
        /** Choruses (and their recalls) are drawn on a raised card so that they stand out. */
        val isOnCard: Boolean,
    ) : RenderSection {

        /** Text-dependent values are built with the section, never again during zoom or theme recomposition. */
        val lines = parts.flatMap { (it as? SectionPart.Lines)?.lines.orEmpty() }

        /**
         * What [SongSectionContent] draws the section as, one under the other: every comment between two of its lines,
         * every line, and every run of tablature or grid lines as the pieces it may be cut into ([runSlotCount]).
         */
        val itemKinds = parts.flatMap { part ->
            when (part) {
                // A key change is kept with the line it is written above, as a comment is.
                is Comment, is KeyChange -> listOf(SectionItemKind.COMMENT)
                is SectionPart.Lines -> part.runs.flatMap { run ->
                    val kind = run.first().foldableKind()
                    if (kind != null) {
                        List(run.runSlotCount(kind)) { SectionItemKind.CONTENT }
                    } else {
                        run.map { line -> if (line == ChordProLine.Blank) SectionItemKind.BLANK else SectionItemKind.CONTENT }
                    }
                }
            }
        }
        val itemCount get() = itemKinds.size

        /** Where the section may be cut, see [sectionChunkStarts]. */
        val chunkStarts = sectionChunkStarts(itemKinds)
        val wholeFoldableKind = lines.wholeFoldableKind()
        val firstEnvironmentLabel = lines.firstNotNullOfOrNull { it.environmentLabel }

        /** The words of the section's first sung line, which name a fold toggle that has no header to read. */
        val firstLyric = lines.firstNotNullOfOrNull { line -> (line as? ChordProLine.Lyrics)?.text?.trim()?.takeIf { it.isNotEmpty() } }
            ?.let(::shortenedForDescription)
    }

    /** A comment between two sections, or one inside a section as one of its [SectionPart]s. */
    @Immutable
    data class Comment(
        val text: String,
        val style: CommentStyle,
    ) : RenderSection, SectionPart

    /**
     * Where a `{transpose}` further down the song changes its key, naming the [key] it is in from there on: between two
     * sections, or inside the one it was written in as one of its [SectionPart]s, the way a comment stands in either.
     */
    @Immutable
    data class KeyChange(val key: String) : RenderSection, SectionPart

    /**
     * Where a `{tempo}` or a `{time}` further down the song changes how it is played, naming both from there on as the
     * click plays them: the [tempo] held to the click's range, null where the song never names one, and the [time]
     * signature the click counts, the common time where the song names none. Always a unit of its own between two
     * sections, since it starts a page of its own where some line of the song comes before it (see
     * [SongUnits.timingStarts]; one written before the first line stands in place on the first page), and never folded
     * or cut.
     */
    @Immutable
    data class Timing(val tempo: String?, val time: String) : RenderSection
}

/** A piece of a [RenderSection.Lines]: a run of its lines, or a comment standing between two of them. */
@Immutable
internal sealed interface SectionPart {

    @Immutable
    data class Lines(val lines: List<ChordProLine>) : SectionPart {
        /** Run boundaries depend on the lines alone, including their environment labels. */
        val runs = lines.groupIntoRuns()
    }
}
