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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongLyricsModelCacheTest {

    private val labels = DefaultSectionLabels(
        verse = "Verse",
        chorus = "Chorus",
        bridge = "Bridge",
        tab = "Tab",
        grid = "Grid",
        intro = "Intro",
        preChorus = "Pre-chorus",
        solo = "Solo",
        outro = "Outro",
        abc = "ABC",
        lilyPond = "LilyPond",
        svg = "SVG",
    )
    private val text = "{title: Song}\n{sov}\n[G]Hello [C]there\n{eov}"
    private val inputs = SongLyricsInputs(text = text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default, shouldShowChords = true, labels = labels)

    private fun modelOf(inputs: SongLyricsInputs) = prepareSongLyrics(song = ChordProParser.parse(inputs.text), shouldShowChords = inputs.shouldShowChords, labels = inputs.labels)

    @Test
    fun `a model kept for inputs is found for equal ones and for no others`() {
        val cache = SongLyricsModelCache()
        val model = modelOf(inputs)
        cache.keep(inputs, model)

        assertSame(model, cache[inputs.copy(text = buildString { append(text) })])
        assertNull(cache[inputs.copy(transposition = 2)])
        assertNull(cache[inputs.copy(spelling = UserPreferences.ChordSpelling(UserPreferences.Accidentals.FLATS, UserPreferences.Notation.STANDARD))])
        assertNull(cache[inputs.copy(shouldShowChords = false)])
        assertNull(cache[inputs.copy(labels = labels.copy(verse = "Versszak"))])
        assertNull(cache[inputs.copy(chordTuning = ChordTuning.entries.first())])
    }

    @Test
    fun `the least recently read model leaves first - not the least recently kept`() {
        val cache = SongLyricsModelCache()
        val all = List(MAX_KEPT_MODELS) { inputs.copy(transposition = it) }
        all.forEach { cache.keep(it, modelOf(it)) }
        cache[all.first()]
        cache.keep(inputs.copy(transposition = 100), modelOf(inputs))

        assertTrue(cache[all.first()] != null)
        assertNull(cache[all[1]])
    }

    @Test
    fun `a model cut short by the budget is not kept`() {
        val cache = SongLyricsModelCache()
        val long = inputs.copy(text = List(LayoutBudget.MAX_LINES + 100) { "la $it" }.joinToString("\n"))
        val model = modelOf(long)
        assertTrue(model.isCut)
        cache.keep(long, model)

        assertNull(cache[long])
    }

    @Test
    fun `keeping again for the same inputs replaces the model`() {
        val cache = SongLyricsModelCache()
        cache.keep(inputs, modelOf(inputs))
        val searched = modelOf(inputs)
        cache.keep(inputs, searched)

        assertSame(searched, cache[inputs])
    }
}
