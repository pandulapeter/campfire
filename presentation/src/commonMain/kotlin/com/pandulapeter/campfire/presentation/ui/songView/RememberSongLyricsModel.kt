/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsInputs
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import com.pandulapeter.campfire.presentation.ui.songView.model.buildsModelInPlace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.hasSongChords
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.withSearchedShapes
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.tabs.TabReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
