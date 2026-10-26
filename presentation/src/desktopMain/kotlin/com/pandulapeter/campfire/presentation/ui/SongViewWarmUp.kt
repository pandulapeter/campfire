/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songView.model.prepareSongLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Runs, once and in the background, the part of the first song details open of a JVM session that needs no
 * composition: the back stack serializer `Navigator.persistBackStack` builds on the click, and the parser and model
 * classes the first page's in-place model loads, over a bundled demo song. Nothing it makes is kept or shared with the
 * window; what it leaves behind is classes loaded and linked, and the serializer cached. Only the desktop's: Android has
 * its Baseline Profile, and iOS and the web are compiled ahead of time.
 *
 * A warm-up must never take the app down, so whatever it throws is printed (which reaches `campfire.log`) and dropped.
 */
@OptIn(ExperimentalResourceApi::class)
internal suspend fun warmUpSongView(songRenderer: SongRenderer, labels: DefaultSectionLabels) = withContext<Unit>(Dispatchers.Default) {
    try {
        Json.encodeToString<List<CampfireDestination>>(
            listOf(
                CampfireDestination.Songs,
                CampfireDestination.SongDetails(songFileNames = listOf("warm-up.cho"), setlistFileName = null, initialIndex = 0),
            ),
        )
        val spelling = UserPreferences.ChordSpelling.Default
        val text = Res.readBytes("files/demo/traditional_american-house_of_the_rising_sun.cho").decodeToString()
        val transposed = songRenderer.transposedSong(text = text, transposition = 0, spelling = spelling)
        prepareSongLyrics(
            song = songRenderer.notatedSong(transposed, spelling),
            standardSong = transposed,
            shouldShowChords = true,
            labels = labels,
            chordTuning = ChordTuning.GUITAR,
            notation = spelling.notation.toChordNotation(),
            searchesShapes = true,
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (throwable: Throwable) {
        println("Could not warm up the song view: $throwable")
    }
}
