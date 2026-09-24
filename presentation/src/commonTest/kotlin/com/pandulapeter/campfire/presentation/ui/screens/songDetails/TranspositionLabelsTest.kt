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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class TranspositionLabelsTest {

    @Test
    fun deduplicatesRenderingInputsWithoutChangingLabels() {
        val songs = listOf(
            song("first", "B", 0),
            song("same key", "B", 0),
            song("transposed", "B", 2),
            song("other key", "C", 0),
            song("no chords", "D", 0, hasChords = false),
            song("no key", null, 0),
        )
        val spelling = UserPreferences.ChordSpelling(
            accidentals = UserPreferences.Accidentals.ORIGINAL,
            isGermanNotationEnabled = true,
        )
        var calls = 0
        val render: (Song, Int) -> String? = { song, transposition ->
            calls++
            if (song.key == "B" && spelling.isGermanNotationEnabled) "H${song.transpose + transposition}"
            else song.key?.let { "$it${song.transpose + transposition}" }
        }
        val actual = transpositionLabelsForSongs(songs, spelling, render)
        val distinctInputs = songs.filter { it.hasChords }.distinctBy { it.key to it.transpose }.size
        assertEquals(distinctInputs * (CampfireViewModel.MAX_TRANSPOSITION - CampfireViewModel.MIN_TRANSPOSITION + 1), calls)

        val oldLabels = songs.filter { it.hasChords }.flatMap { song ->
            (CampfireViewModel.MIN_TRANSPOSITION..CampfireViewModel.MAX_TRANSPOSITION).map { transposition ->
                transpositionLabel(transposition, render(song, transposition))
            }
        }.distinct()
        assertEquals(oldLabels, actual)
    }

    private fun song(fileName: String, key: String?, transpose: Int, hasChords: Boolean = true) = Song(
        fileName = "$fileName.cho",
        title = fileName,
        artist = "",
        key = key,
        transpose = transpose,
        tags = emptyList(),
        languages = emptyList(),
        hasChords = hasChords,
        canUpdateFileName = false,
        lastModified = 0L,
        size = 0L,
    )
}
