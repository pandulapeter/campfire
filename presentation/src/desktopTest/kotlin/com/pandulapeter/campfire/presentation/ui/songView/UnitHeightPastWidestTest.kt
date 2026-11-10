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

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.ui.notation.SongNotations
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldedRuns
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import com.pandulapeter.campfire.presentation.ui.songView.model.flatPastWidestOf
import com.pandulapeter.campfire.presentation.ui.songView.model.prepareSongLyrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every chunk [flatPastWidestOf] measures once for every width past its widest line, composed as the song page composes
 * it, answers the same height at every width from its widest line on: what lets the grid search measure it once there.
 * On the desktop, where text is laid out for real.
 */
internal class UnitHeightPastWidestTest {

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

    @Test
    fun `every chunk measured once past its widest line is as tall at every width past it`() =
        assertFlatPastWidest(SONG, LayoutDirection.Ltr, foldedHeader = FOLDED)

    @Test
    fun `a chorded line of a song read from the right is as tall at every width past its widest line`() =
        assertFlatPastWidest(HEBREW_SONG, LayoutDirection.Rtl, foldedHeader = null)

    private fun assertFlatPastWidest(text: String, layoutDirection: LayoutDirection, foldedHeader: String?) {
        val sections = prepareSongLyrics(song = ChordProParser.parse(text), shouldShowChords = true, labels = labels, showsTiming = true).sections
        val folded = sections.filterIsInstance<RenderSection.Lines>().filter { it.header == foldedHeader }.map { it.foldKey }.toSet()
        assertEquals(foldedHeader != null, folded.isNotEmpty())
        val units = SongUnits.of(sections, isFolded = { it is RenderSection.Lines && it.foldKey in folded }) { it is RenderSection.Lines && it.foldKey !in folded }
        val flags = flatPastWidestOf(units, sections)
        val kinds = units.itemRanges.indices.filter { flags[it] }.map { sections[units.unitSections[it]]::class.simpleName }.toSet()
        assertTrue(kinds.containsAll(EXPECTED_KINDS.filter { layoutDirection == LayoutDirection.Ltr }), kinds.toString())
        val failures = mutableListOf<String>()
        val foldedRuns = FoldedRuns(collapsed = folded, toggled = emptySet(), onToggled = {})
        val style = TextStyle(fontSize = 16.sp)
        val chordStyle = style.copy(fontWeight = FontWeight.Bold)
        val density = Density(2f)
        val textMeasurements = SongTextMeasurements(
            textMeasurer = TextMeasurer(createFontFamilyResolver(), density, layoutDirection),
            lyricsStyle = style,
            chordStyle = chordStyle,
            annotationStyle = style,
        )
        units.itemRanges.forEachIndexed { unit, items ->
            if (!flags[unit]) return@forEachIndexed
            val section = sections[units.unitSections[unit]]
            val scene = ImageComposeScene(width = SCENE_WIDTH, height = SCENE_HEIGHT, density = density) {
                MaterialTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Layout(
                            content = {
                                SongSectionItem(
                                    modifier = Modifier,
                                    section = section,
                                    items = items,
                                    notation = ChordNotation.STANDARD,
                                    isSongInfoShown = false,
                                    songInfoEditing = null,
                                    playingControls = null,
                                    playingTools = null,
                                    animatesRows = false,
                                    chordDiagrams = null,
                                    scaleSuggestions = null,
                                    tuningVisibility = MutableTransitionState(false),
                                    chordLayouts = null,
                                    onChordFoldToggled = null,
                                    isChordsSectionFadingIn = false,
                                    isScalesSectionFadingIn = false,
                                    headerStyle = chordStyle,
                                    lyricsStyle = style,
                                    sentenceStyle = style,
                                    chordStyle = chordStyle,
                                    screenLayoutDirection = layoutDirection,
                                    textMeasurements = textMeasurements,
                                    notations = SongNotations.NONE,
                                    foldedRuns = foldedRuns,
                                    defaultLabels = labels,
                                    fontScale = 1f,
                                )
                            },
                        ) { measurables, constraints ->
                            val chunk = measurables.single()
                            val widest = chunk.maxIntrinsicWidth(Constraints.Infinity)
                            val height = chunk.maxIntrinsicHeight(widest)
                            (widest..widest + PAST_WIDEST step STEP).forEach { width ->
                                val atWidth = chunk.maxIntrinsicHeight(width)
                                if (atWidth != height) failures += "$section $items: $height at its widest line, $widest, but $atWidth at $width"
                            }
                            layout(constraints.minWidth, constraints.minHeight) {}
                        }
                    }
                }
            }
            try {
                scene.render()
            } finally {
                scene.close()
            }
        }
        assertEquals(emptyList(), failures)
    }

    private companion object {
        const val SCENE_WIDTH = 2_000
        const val SCENE_HEIGHT = 2_000
        const val PAST_WIDEST = 600
        const val STEP = 7
        const val FOLDED = "Folded"
        val EXPECTED_KINDS = listOf("Lines", "Comment", "KeyChange", "Timing")
        val SONG = """
            {title: Probe}
            {key: G}
            {tempo: 80}
            {time: 4/4}
            {start_of_verse}
            [G]A chorded [D]line of the [Em]verse
            A line long enough to wrap in any column narrower than it is, which goes on and on and on and on for a while
            {comment: A comment between the lines}
            {end_of_verse}
            {transpose: 2}
            {comment: A comment of its own}
            {tempo: 96}
            {time: 3/4}
            {start_of_chorus: The chorus}
            [D]The chorus on its [A]card
            {end_of_chorus}
            {start_of_tab}
            e|--0--2--3--5--7--8--10--12--|
            B|--1--1--1--1--1--1--1---1---|
            G|--0--0--0--0--0--0--0---0---|
            {end_of_tab}
            {start_of_grid}
            | G . . . | D . . . | Em . . . | C . . . |
            {end_of_grid}
            {start_of_verse: Folded}
            [G]Folded away
            {end_of_verse}
        """.trimIndent()
        val HEBREW_SONG = """
            {title: Shir}
            {start_of_verse}
            [Am]שיר על [G]הדרך הארוכה
            [C]שיר על [E]הלילה
            {end_of_verse}
        """.trimIndent()
    }
}
