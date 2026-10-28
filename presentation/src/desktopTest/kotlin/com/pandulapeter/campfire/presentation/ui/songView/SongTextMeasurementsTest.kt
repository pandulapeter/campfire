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

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class SongTextMeasurementsTest {

    private val style = TextStyle(fontSize = 14.sp)
    private val monospaceStyle = style.copy(fontFamily = FontFamily.Monospace)
    private val measurements = SongTextMeasurements(
        textMeasurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
        lyricsStyle = style,
        chordStyle = style,
        annotationStyle = style,
    )
    private val styles = RunStyles(text = style, chord = style, monospaceText = monospaceStyle, monospaceChord = monospaceStyle)
    private val staff = listOf("e|---0---|", "B|---1---|", "G|---0---|")

    @Test
    fun `every chunk of a tab run shares one set of rows`() {
        assertSame(measurements.tabRows(staff, styles), measurements.tabRows(staff.toList(), styles.copy(text = style.copy())))
    }

    @Test
    fun `a different run or a different style has rows of its own`() {
        val rows = measurements.tabRows(staff, styles)
        assertNotSame(rows, measurements.tabRows(staff + "D|---2---|", styles))
        assertNotSame(rows, measurements.tabRows(staff, styles.copy(monospaceText = monospaceStyle.copy(fontSize = 20.sp))))
        assertNotSame(rows, measurements.tabRows(staff, styles.copy(text = style.copy(fontSize = 20.sp))))
    }

    @Test
    fun `a run drawn preformatted has no staff even where its lines look like one`() {
        val preformatted = measurements.tabRows(staff, styles, isPreformatted = true)

        assertNotSame(measurements.tabRows(staff, styles), preformatted)
        assertEquals(0, preformatted.rowGap)
        assertTrue(preformatted.at(1000).all { it.staff.strings.isEmpty() && it.staff.bars.isEmpty() })
    }

    @Test
    fun `the tab runs start over once there are too many of them`() {
        val first = measurements.tabRows(staff, styles)
        repeat(MAX_TAB_RUNS) { measurements.tabRows(listOf("e|---$it---|"), styles) }
        assertNotSame(first, measurements.tabRows(staff, styles))
    }

    @Test
    fun `every chunk of a grid run shares one alignment of its bars`() {
        val run = listOf(
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("Am"), GridToken.Beat, GridToken.Bar("|"))),
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("C"), GridToken.Chord("G"), GridToken.Bar("|"))),
        )
        assertSame(measurements.gridBars(run), measurements.gridBars(run.toList()))
    }
}
