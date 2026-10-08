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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.withSearchedShapes
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A song ready to be laid out: what [SongLyrics] draws, which can be built on any thread (see [prepareSongLyrics]).
 *
 * @param isCut Whether [sections] stop short of the end of [song], see [LayoutBudget].
 * @param shouldShowChords False for lyrics-only mode, see [prepareSongLyrics].
 * @param notation The notation [song] is written in, which decides what is a chord in the brackets of a comment.
 * @param chordInstrument The instrument [chords] were collected for, which their pending shapes are looked for on.
 */
internal class SongLyricsModel(
    val song: ChordProSong,
    val sections: List<RenderSection>,
    val isCut: Boolean,
    val shouldShowChords: Boolean,
    val notation: ChordNotation = ChordNotation.STANDARD,
    val chords: List<SongChord> = emptyList(),
    val chordInstrument: ChordInstrument? = null,
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
        chords = chordInstrument?.let { chords.withSearchedShapes(it) } ?: chords,
        chordInstrument = chordInstrument,
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
 * @param chordInstrument The instrument the song's chords are collected for its Chords section on (see [songChordsOf]),
 * which is the slow part of that section and so belongs here; null where the song has no such section.
 * @param notation The notation [song] is written in.
 * @param standardSong [song] before it was written in [notation], which its chords are read from.
 * @param searchesShapes False to leave the shapes only the search finds for [SongLyricsModel.withSearchedShapes], for a
 * model built on the main thread.
 */
internal fun prepareSongLyrics(
    song: ChordProSong,
    shouldShowChords: Boolean,
    labels: DefaultSectionLabels,
    showsTiming: Boolean = true,
    chordInstrument: ChordInstrument? = null,
    notation: ChordNotation = ChordNotation.STANDARD,
    standardSong: ChordProSong = song,
    searchesShapes: Boolean = true,
) = LayoutBudget.fit(song.toRenderSections(shouldShowChords, labels, showsTiming)).let { (sections, isCut) ->
    SongLyricsModel(
        song = song,
        sections = sections.withoutEmptyTimings(),
        isCut = isCut,
        shouldShowChords = shouldShowChords,
        notation = notation,
        chords = if (shouldShowChords && chordInstrument != null) {
            songChordsOf(standardSong, notation, chordInstrument, standardSong.metadata.capo ?: 0, searchesShapes = searchesShapes)
        } else {
            emptyList()
        },
        chordInstrument = chordInstrument,
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
    val tempoOverride: Int? = null,
    val capoOverride: Int? = null,
    val showsTiming: Boolean = true,
    val chordInstrument: ChordInstrument? = null,
)

/**
 * Whether a page's first model is built in place, so that it never shows an empty frame: the page is on screen or
 * headed for, or it is the one before the target page, which a step back from the top of a song scrolls to its end
 * at the moment of the press - a page with no model yet has no end to scroll to.
 */
internal fun buildsModelInPlace(page: Int, currentPage: Int, targetPage: Int, isVisible: Boolean) =
    page == currentPage || page == targetPage || page == targetPage - 1 || isVisible

/**
 * The model [prepare] builds of [inputs], null until there is one. Where [buildsInPlace] (see [buildsModelInPlace]) the
 * first one is built right here - for the page being read, headed for or stepped back to, so that it never opens on an
 * empty frame - but without the chord shapes only the search finds, which follow from [Dispatchers.Default] right after
 * (see [SongLyricsModel.withSearchedShapes]). The page beside it on the other side builds its first one on
 * [Dispatchers.Default], whole, since nobody is looking at it; should it become one that builds in place before that
 * is done, it builds in place after all, and the background result for the same inputs then takes over, carrying the
 * searched shapes the in-place one went without. Every later model is built on [Dispatchers.Default] whole, with the
 * one before it staying on screen until it is ready. A change that arrives meanwhile cancels the wait, although not the
 * parse or the search itself, which are not cooperative: that one finishes in the background and its result is dropped.
 */
@Composable
internal fun rememberSongLyricsModel(
    inputs: SongLyricsInputs,
    buildsInPlace: Boolean,
    prepare: (inputs: SongLyricsInputs, searchesShapes: Boolean) -> SongLyricsModel,
): SongLyricsModel? {
    val latestPrepare by rememberUpdatedState(prepare)
    val state = remember { mutableStateOf(if (buildsInPlace) inputs to prepare(inputs, false) else null) }
    // Not snapshot state: it is written while composing, and only ever read in the same composition that wrote it.
    val inPlace = remember { InPlaceSongLyricsModel() }
    if (state.value != null) {
        inPlace.built = null
    } else if (buildsInPlace && inPlace.built?.first != inputs) {
        inPlace.built = inputs to prepare(inputs, false)
    }
    LaunchedEffect(inputs) {
        val built = state.value
        state.value = when {
            built == null || built.first != inputs -> inputs to withContext(Dispatchers.Default) { latestPrepare(inputs, true) }
            built.second.hasPendingShapes -> inputs to withContext(Dispatchers.Default) { built.second.withSearchedShapes() }
            else -> return@LaunchedEffect
        }
    }
    return state.value?.second ?: inPlace.built?.second
}

/** The model a page built in place while its first one was still being built in the background. */
private class InPlaceSongLyricsModel {

    var built: Pair<SongLyricsInputs, SongLyricsModel>? = null
}
