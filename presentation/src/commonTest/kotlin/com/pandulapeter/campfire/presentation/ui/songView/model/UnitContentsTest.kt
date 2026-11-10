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

import androidx.compose.ui.graphics.Path
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import com.pandulapeter.campfire.presentation.ui.notation.NotationReading
import com.pandulapeter.campfire.presentation.ui.notation.NotationRendering
import com.pandulapeter.campfire.presentation.ui.notation.NotationSystemPaint
import com.pandulapeter.campfire.presentation.ui.notation.RenderedNotations
import com.pandulapeter.campfire.presentation.ui.notation.SongNotations
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitContentsTest {

    private val labels = DefaultSectionLabels(
        verse = "Verse",
        chorus = "Chorus",
        bridge = "Bridge",
        tab = "Tab",
        grid = "Grid",
        abc = "Sheet music",
        lilyPond = "LilyPond",
        svg = "SVG",
        intro = "Intro",
        preChorus = "Pre-chorus",
        solo = "Solo",
        outro = "Outro",
    )
    private val sections = prepareSongLyrics(
        song = ChordProParser.parse("{sov}\n[C]One\n[G]Two\n{eov}\n\n{start_of_abc}\n$TUNE\n{end_of_abc}"),
        shouldShowChords = true,
        labels = labels,
        showsTiming = false,
    ).sections
    private val units = SongUnits.of(sections) { it is RenderSection.Lines }
    private val tune = AbcTune(TUNE, 0)

    @Test
    fun `only the chunks of a tune carry what it draws, and a tune drawn as text carries that`() {
        val contents = unitContentsOf(units, sections, emptySet(), notationsOf(null))
        // The tune's slots, each a chunk of its own, after the verse's lines.
        val tuneUnits = contents.indices.filter { contents[it].notation != null }
        assertEquals(contents.indices.filter { units.unitSections[it] == 1 }, tuneUnits)
        assertTrue(tuneUnits.isNotEmpty())
        tuneUnits.forEach { assertEquals(listOf(null), contents[it].notation) }
        val rendered = unitContentsOf(units, sections, emptySet(), notationsOf(rendering()))
        tuneUnits.forEach { assertNotNull(rendered[it].notation?.single()) }
    }

    @Test
    fun `the same rendering gives equal contents, and a new one changes only the tune's chunks`() {
        val rendering = rendering()
        val before = unitContentsOf(units, sections, emptySet(), notationsOf(rendering))
        assertEquals(before, unitContentsOf(units, sections, emptySet(), notationsOf(rendering)))
        val after = unitContentsOf(units, sections, emptySet(), notationsOf(rendering()))
        before.indices.forEach { unit ->
            if (before[unit].notation == null) assertEquals(before[unit], after[unit]) else assertNotEquals(before[unit], after[unit])
        }
        assertNull(before.first().notation)
    }

    private fun rendering() = NotationRendering(
        drawing = NotationDrawing(emptyList()),
        unitSize = 1f,
        systems = listOf(NotationSystemPaint(width = 100f, height = 40f, fills = Path(), strokes = emptyList(), knockouts = Path(), texts = emptyList())),
        reading = NotationReading(emptyList(), emptyList(), null),
    )

    private fun notationsOf(rendering: NotationRendering?) = SongNotations(
        rendered = RenderedNotations(
            request = null,
            renderings = rendering?.let { mapOf(tune to it) }.orEmpty(),
            rerenderedTunes = emptySet(),
            arrivingTunes = emptySet(),
        ),
        standIns = emptyMap(),
        unitSize = 1f,
        maxWidth = 400f,
        minWidth = 200f,
        placement = null,
        partChoices = null,
        instrument = null,
    )

    private companion object {
        const val TUNE = "X:1\nK:G\nGABc|"
    }
}
