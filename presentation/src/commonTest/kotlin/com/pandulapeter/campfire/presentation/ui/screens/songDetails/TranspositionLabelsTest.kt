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
        val render = render(spelling) { calls++ }
        val actual = transpositionLabelsForKeys(keysOf(songs), render)
        val distinctInputs = songs.filter { it.hasChords }.distinctBy { it.key to it.transpose }.size
        assertEquals(distinctInputs * (CampfireViewModel.MAX_TRANSPOSITION - CampfireViewModel.MIN_TRANSPOSITION + 1), calls)

        val oldLabels = songs.filter { it.hasChords }.flatMap { song ->
            (CampfireViewModel.MIN_TRANSPOSITION..CampfireViewModel.MAX_TRANSPOSITION).map { transposition ->
                transpositionLabel(transposition, render(song.key, song.transpose, transposition))
            }
        }.distinct()
        assertEquals(oldLabels, actual)
    }

    @Test
    fun songsSharingAKeyReadTheLabelsOfOneOfThem() {
        val spelling = UserPreferences.ChordSpelling.Default
        val render = render(spelling) {}
        val one = transpositionLabelsForKeys(keysOf(listOf(song("first", "G", 1))), render)
        val several = transpositionLabelsForKeys(keysOf(listOf(song("first", "G", 1), song("second", "G", 1), song("third", "G", 1))), render)
        assertEquals(one, several)
    }

    private fun keysOf(songs: List<Song>) = songs.filter { it.hasChords }.map { it.key to it.transpose }

    private fun render(spelling: UserPreferences.ChordSpelling, onCall: () -> Unit): (String?, Int, Int) -> String? = { key, transpose, transposition ->
        onCall()
        if (key == "B" && spelling.isGermanNotationEnabled) "H${transpose + transposition}"
        else key?.let { "$it${transpose + transposition}" }
    }

    private fun song(fileName: String, key: String?, transpose: Int, hasChords: Boolean = true) = Song(
        fileName = "$fileName.cho",
        title = fileName,
        artist = "",
        key = key,
        transpose = transpose,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = hasChords,
        canUpdateFileName = false,
        lastModified = 0L,
        size = 0L,
    )
}
