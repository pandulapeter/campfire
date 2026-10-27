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

import com.pandulapeter.campfire.presentation.ui.songView.SongLyrics
import com.pandulapeter.campfire.presentation.ui.songView.rememberSongLyricsModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.chordpro.scales.SongScaleGroups
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.hasSongChords
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.groupedByKey
import com.pandulapeter.campfire.presentation.ui.chords.withDegrees
import com.pandulapeter.campfire.presentation.ui.chords.withSearchedShapes
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.tabs.TabReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A song ready to be laid out: what [SongLyrics] draws, which can be built on any thread (see [prepareSongLyrics]).
 *
 * @param isCut Whether [sections] stop short of the end of [song], see [LayoutBudget].
 * @param shouldShowChords False for lyrics-only mode, see [prepareSongLyrics].
 * @param notation The notation [song] is written in, which decides what is a chord in the brackets of a comment.
 * @param chordTuning The tuning [chords] were collected for, which their pending shapes are looked for in.
 * @param hasChords Whether the song has a chord the Chords section could draw on some instrument, which [chords] does not
 * say where there was no tuning to collect them for (an instrument without shapes): the instrument and tuning pill still
 * heads the song's first section then ([RenderSection.Metadata.instrumentPill]), since it is what brings the diagrams
 * back.
 */
internal class SongLyricsModel(
    val song: ChordProSong,
    val sections: List<RenderSection>,
    val isCut: Boolean,
    val shouldShowChords: Boolean,
    val notation: ChordNotation = ChordNotation.STANDARD,
    val chords: List<SongChord> = emptyList(),
    val chordTuning: ChordTuning? = null,
    val hasChords: Boolean = chords.isNotEmpty(),
    val scaleGroups: List<SongScaleGroup> = emptyList(),
) {

    /** Whether some chord's shape is still to be looked for, see [withSearchedShapes]. */
    val hasPendingShapes get() = chords.any { it.isShapePending }

    /**
     * This model with its pending shapes found: the very same song and sections, so that only the Chords section is
     * measured again, at the size it already had.
     */
    fun withSearchedShapes() = SongLyricsModel(
        song = song,
        sections = sections,
        isCut = isCut,
        shouldShowChords = shouldShowChords,
        notation = notation,
        chords = chordTuning?.let { chords.withSearchedShapes(it) } ?: chords,
        chordTuning = chordTuning,
        hasChords = hasChords,
        scaleGroups = scaleGroups,
    )
}

/**
 * Builds the sections of [song] the way [SongLyrics] lays them out. It touches nothing but its arguments, so it is
 * run away from the main thread: it is a pass over the whole song that would otherwise land on the frame being waited
 * for, after a transposition or a pause in typing.
 *
 * @param shouldShowChords False for lyrics-only mode, which drops the chords, the sections that are nothing else and
 * the key, capo, tempo and time of the metadata section.
 * @param showsTiming Whether a change of tempo or time signature further down the song is a line of its own that starts
 * a page ([RenderSection.Timing]): false with the metronome switched off, which lays the song out as if it had none.
 * @param chordTuning The tuning the song's chords are collected for its Chords section in (see [songChordsOf]),
 * which is the slow part of that section and so belongs here; null where the song has no such section.
 * @param notation The notation [song] is written in.
 * @param standardSong [song] before it was written in [notation], which its chords are read from, and before an override
 * of its capo: the scales read the file's own, which says how its `{key}` is meant ([SongKeys]).
 * @param shapeCapo The fret the Chords section's shapes are fingered above, see [songChordsOf]'s `capo`.
 * @param searchesShapes False to leave the shapes only the search finds for [SongLyricsModel.withSearchedShapes], for a
 * model built on the main thread.
 * @param suggestsScales Whether the scales the song is played over are worked out ([songScaleGroupsOf]), for its Scales
 * section: a walk over every chord, so done here, with the chords on only, and never for a songbook (over
 * [LayoutBudget.MAX_CUT_SECTION_COUNT] sections) or a song playing over `SongScaleGroups.MAX_PLAYED_CHORDS` chords (the
 * `:chordpro` side's gate), where it grows past what the main thread can spare.
 * @param showsDegrees Whether each chord of the Chords section is labelled with the step of the key it stands on
 * ([withDegrees]), counted against [chordKeysOf]'s keys, the home the same walk finds first.
 */
internal fun prepareSongLyrics(
    song: ChordProSong,
    shouldShowChords: Boolean,
    labels: DefaultSectionLabels,
    showsTiming: Boolean = true,
    chordTuning: ChordTuning? = null,
    notation: ChordNotation = ChordNotation.STANDARD,
    standardSong: ChordProSong = song,
    searchesShapes: Boolean = true,
    suggestsScales: Boolean = false,
    showsDegrees: Boolean = false,
    shapeCapo: Int = 0,
) = LayoutBudget.fit(song.toRenderSections(shouldShowChords, labels, showsTiming)).let { (sections, isCut) ->
    val collected = if (shouldShowChords && chordTuning != null) {
        songChordsOf(standardSong, notation, chordTuning, searchesShapes = searchesShapes, capo = shapeCapo)
    } else {
        emptyList()
    }
    val isSongbook = standardSong.blocks.count { it is ChordProBlock.Section } > LayoutBudget.MAX_CUT_SECTION_COUNT
    val degreesShown = showsDegrees && collected.isNotEmpty() && !notation.isNumbering
    // One walk answers both the Scales section and the degrees, so that a page with both on pays for it once.
    val analysis = if (shouldShowChords && !isSongbook && (suggestsScales || degreesShown)) songScaleAnalysisOf(standardSong, labels) else null
    val keys = if (collected.isNotEmpty()) chordKeysOf(standardSong, analysis, isSongbook) else null
    val grouped = collected.groupedByKey(keys, notation)
    val chords = if (degreesShown) grouped.withDegrees(keys, notation) else grouped
    SongLyricsModel(
        song = song,
        sections = sections.withoutEmptyTimings(),
        isCut = isCut,
        shouldShowChords = shouldShowChords,
        notation = notation,
        chords = chords,
        chordTuning = chordTuning,
        // The chords collected answer it where there are any, which spares the first model of a page, built in the frame
        // being waited for, a second walk over the song's chords; none collected is not an answer, since there may have
        // been no tuning to collect them for, or more chords than a section draws.
        hasChords = shouldShowChords && (chords.isNotEmpty() || hasSongChords(standardSong, notation)),
        scaleGroups = if (analysis != null && suggestsScales) songScaleGroupsOf(standardSong, labels, notation, analysis) else emptyList(),
    )
}

/**
 * [this] without the changes of tempo or time signature that no line of the song follows before the next one or the
 * end: one after the last line, one whose stretch lyrics-only mode emptied, or one [LayoutBudget] cut off from what it
 * was written above. Each would start a page with nothing on it but itself.
 */
internal fun List<RenderSection>.withoutEmptyTimings(): List<RenderSection> {
    if (none { it is RenderSection.Timing }) return this
    var isFollowedByLines = false
    return asReversed().filter { section ->
        when (section) {
            is RenderSection.Timing -> isFollowedByLines.also { isFollowedByLines = false }
            is RenderSection.Lines -> true.also { isFollowedByLines = true }
            else -> true
        }
    }.asReversed()
}

/**
 * Where the stretches of a song's sections start ([starts], 0 and every change some line of the song comes before) and
 * the section each change is in force from ([timingSections], one per [RenderSection.Timing] in order: its own section,
 * or 0 for a change written before the song's first line, which is played from the first page).
 */
internal class TimingStretches(val starts: IntArray, val timingSections: List<Int>)

/**
 * The stretches of [sections]: 0, and every change some line of the song comes before, each of which starts a page and is
 * in force from its own section. A change written before the song's first line - inside an opening `{start_of_verse}`,
 * or under a comment above it - starts none, since its stretch would hold the song's metadata alone: it stands in place
 * on the first page, as it does in the preview and the PDF, and is in force from section 0, so the click plays it from
 * the song's first page.
 */
internal fun timingStretchesOf(sections: List<RenderSection>): TimingStretches {
    val starts = mutableListOf(0)
    val timingSections = mutableListOf<Int>()
    var hasSeenLines = false
    sections.forEachIndexed { index, section ->
        when (section) {
            is RenderSection.Lines -> hasSeenLines = true
            is RenderSection.Timing -> if (hasSeenLines) {
                timingSections += index
                if (index > 0) starts += index
            } else {
                timingSections += 0
            }
            else -> Unit
        }
    }
    return TimingStretches(starts = starts.toIntArray(), timingSections = timingSections)
}

/** Everything a [SongLyricsModel] is built from, see [rememberSongLyricsModel]. */
internal data class SongLyricsInputs(
    val text: String,
    val transposition: Int,
    val spelling: UserPreferences.ChordSpelling,
    val shouldShowChords: Boolean,
    val labels: DefaultSectionLabels,
    val tabCapo: Int = 0,
    val tempoOverride: Int? = null,
    val capoOverride: Int? = null,
    val shapeCapo: Int = 0,
    val inferredKey: String? = null,
    val showsTiming: Boolean = true,
    val chordTuning: ChordTuning? = null,
    val tabs: TabReading? = null,
    val suggestsScales: Boolean = false,
    val showsDegrees: Boolean = false,
)

/**
 * Whether a page's first model is built in place, so that it never shows an empty frame: the page is on screen or
 * headed for, or it is the one before the target page, which a step back from the top of a song scrolls to its end
 * at the moment of the press - a page with no model yet has no end to scroll to.
 */
internal fun buildsModelInPlace(page: Int, currentPage: Int, targetPage: Int, isVisible: Boolean) =
    page == currentPage || page == targetPage || page == targetPage - 1 || isVisible
