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
 * The model [prepare] builds of [inputs] and the inputs it was built of, which are the ones before while a new one is
 * built, null until there is one. Where [buildsInPlace] (see [buildsModelInPlace]) the
 * first one is built right here - for the page being read, headed for or stepped back to, so that it never opens on an
 * empty frame - but without the chord shapes only the search finds, which follow from [Dispatchers.Default] right after
 * (see [SongLyricsModel.withSearchedShapes]). The page beside it on the other side builds its first one on
 * [Dispatchers.Default], whole, since nobody is looking at it; should it become one that builds in place before that
 * is done, it builds in place after all, and the background result for the same inputs then takes over, carrying the
 * searched shapes the in-place one went without. Every later model is built on [Dispatchers.Default] whole, with the
 * one before it staying on screen until it is ready. A change that arrives meanwhile cancels the wait, although not the
 * parse or the search itself, which are not cooperative: that one finishes in the background and its result is dropped.
 *
 * Every model built is kept in [build]'s cache by its inputs, the in-place one as soon as it is built, so a song opened
 * again takes it from there wherever it would be built in place, shapes searched included where the search had finished.
 * A page building in the background takes a kept model only from the effect, after its first frame, since handing it
 * one at once would lay a second whole song out on the push frame.
 */
@Composable
internal fun rememberSongLyricsModel(
    inputs: SongLyricsInputs,
    buildsInPlace: Boolean,
    build: SongModelBuild,
    prepare: (inputs: SongLyricsInputs, searchesShapes: Boolean) -> SongLyricsModel,
): Pair<SongLyricsInputs, SongLyricsModel>? {
    val cache = build.cache
    val latestPrepare by rememberUpdatedState(prepare)
    val inPlaceModelOf = { of: SongLyricsInputs -> of to (cache?.get(of) ?: prepare(of, false).also { cache?.keep(of, it) }) }
    val state = remember { mutableStateOf(if (buildsInPlace) inPlaceModelOf(inputs) else null) }
    // Not snapshot state: it is written while composing, and only ever read in the same composition that wrote it.
    val inPlace = remember { InPlaceSongLyricsModel() }
    if (state.value != null) {
        inPlace.built = null
    } else if (buildsInPlace && inPlace.built?.first != inputs) {
        inPlace.built = inPlaceModelOf(inputs)
    }
    LaunchedEffect(inputs) {
        val built = state.value
        val model = when {
            built == null || built.first != inputs ->
                cache?.get(inputs)?.takeUnless { it.hasPendingShapes } ?: withContext(Dispatchers.Default) { latestPrepare(inputs, true) }
            built.second.hasPendingShapes -> withContext(Dispatchers.Default) { built.second.withSearchedShapes() }
            else -> return@LaunchedEffect
        }
        state.value = inputs to model
        cache?.keep(inputs, model)
    }
    return state.value ?: inPlace.built
}

/** The model a page built in place while its first one was still being built in the background. */
private class InPlaceSongLyricsModel {

    var built: Pair<SongLyricsInputs, SongLyricsModel>? = null
}
