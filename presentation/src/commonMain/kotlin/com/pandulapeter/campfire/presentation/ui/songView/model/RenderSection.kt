/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongChordsSection
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongSectionContent
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.places
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.chordSlotCount
import com.pandulapeter.campfire.presentation.ui.songLayout.environmentLabel
import com.pandulapeter.campfire.presentation.ui.songLayout.foldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.groupIntoRuns
import com.pandulapeter.campfire.presentation.ui.songLayout.runSlotCount
import com.pandulapeter.campfire.presentation.ui.songLayout.wholeFoldableKind
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

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
 * What the instrument and tuning chips at the top of a song's first section name: part of the section's content, since
 * how wide their labels are decides how many lines they take, and the measured sizes of a section are kept by it.
 *
 * @param tuning The tuning the tuning chip names, a leaving one included until it has faded out, since it is laid out
 * until then; null where there is no tuning chip, an instrument with a single tuning, so that two instruments without
 * one are not told apart by a tuning nobody sees.
 * @param notation What the tuning chip names the tuning's note in.
 */
@Immutable
internal data class InstrumentChipsContent(
    val instrument: UserPreferences.Instrument,
    val tuning: InstrumentTuning?,
    val notation: ChordNotation,
)

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
     * @param readsDefaults Whether that line names a capo of none, and the default tempo and time signature, too, see
     * [withMetadataSection].
     * @param hasTuner Whether the tuner's button is drawn among them, part of the content for the same reason.
     * @param hasMetronome Whether the metronome's button is drawn among them, part of the content for the same reason.
     * @param instrumentChips What the instrument and tuning chips at its top name, null where it has none, part of the
     * content for the same reason, see [withChordsSection].
     */
    @Immutable
    data class Metadata(
        val metadata: ChordProMetadata,
        val hasPlayingControls: Boolean = false,
        val readsDefaults: Boolean = false,
        val hasTuner: Boolean = false,
        val hasMetronome: Boolean = false,
        val instrumentChips: InstrumentChipsContent? = null,
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
                        List(run.runSlotCount(kind)) { SectionItemKind.RUN }
                    } else {
                        run.map { line -> if (line == ChordProLine.Blank) SectionItemKind.BLANK else SectionItemKind.CONTENT }
                    }
                }
            }
        }
        val itemCount get() = itemKinds.size

        /** What each of the section's items is, in the order [itemKinds] counts them, see [SectionItem]. */
        val sectionItems: List<SectionItem> = buildSectionItems(parts, foldKey)

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

/**
 * One item of a [RenderSection.Lines], worked out with the section rather than while it is composed: a section that may
 * be cut is composed as one chunk per item, and a chunk that had to walk the section to find its own items would make a
 * section of thousands of lines cost the square of that on every step of a pinch.
 */
@Immutable
internal sealed interface SectionItem {

    /** A comment or a key change standing between two lines. */
    @Immutable
    data class Inline(val part: SectionPart) : SectionItem

    /** A line that is not part of a run of tablature or a grid. */
    @Immutable
    data class Line(val line: ChordProLine) : SectionItem

    /** The [slot]-th of the [slotCount] pieces of a run of tablature or grid lines, folded under [runFoldKey]. */
    @Immutable
    data class RunSlot(
        val run: List<ChordProLine>,
        val kind: FoldableKind,
        val slot: Int,
        val slotCount: Int,
        val runFoldKey: String,
    ) : SectionItem
}

/**
 * The items of the section folded as [sectionFold] that consists of [parts], one for each of its [RenderSection.Lines.itemKinds].
 * Every run's fold is counted, drawn or not, since a run is named by how many runs of its name came before it in the section.
 */
private fun buildSectionItems(parts: List<SectionPart>, sectionFold: String): List<SectionItem> {
    val runNameCounts = mutableMapOf<String, Int>()
    return parts.flatMap { part ->
        when (part) {
            is RenderSection.Comment, is RenderSection.KeyChange -> listOf(SectionItem.Inline(part))
            is SectionPart.Lines -> part.runs.flatMap { run ->
                val kind = run.first().foldableKind()
                if (kind == null) {
                    run.map { SectionItem.Line(it) }
                } else {
                    val runFoldKey = runNameCounts.nextRunFoldKey(sectionFold = sectionFold, label = run.first().environmentLabel, kind = kind)
                    val slotCount = run.runSlotCount(kind)
                    List(slotCount) { slot -> SectionItem.RunSlot(run = run, kind = kind, slot = slot, slotCount = slotCount, runFoldKey = runFoldKey) }
                }
            }
        }
    }
}
