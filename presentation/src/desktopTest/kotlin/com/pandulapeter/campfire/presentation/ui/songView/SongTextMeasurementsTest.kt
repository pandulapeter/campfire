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

import com.pandulapeter.campfire.presentation.ui.registerComposeUiRuntime
import androidx.compose.ui.text.intl.LocaleList
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

    init {
        registerComposeUiRuntime()
    }

    private val style = TextStyle(fontSize = 14.sp)
    private val monospaceStyle = style.copy(fontFamily = FontFamily.Monospace)
    private val measurements = SongTextMeasurements(
        textMeasurer = TextMeasurer(createFontFamilyResolver(), LocaleList("en"), Density(1f), LayoutDirection.Ltr),
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
    fun `every chunk of a grid run shares one layout of its bars`() {
        val run = listOf(
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("Am"), GridToken.Beat, GridToken.Bar("|"))),
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("C"), GridToken.Chord("G"), GridToken.Bar("|"))),
        )
        assertSame(measurements.gridLayout(run, styles), measurements.gridLayout(run.toList(), styles))
    }

    /** A key of its own font resolver, compared by identity, so that no other test's measurements are handed back. */
    private val resolver = createFontFamilyResolver()

    private fun keyOf(fontSize: Float = 14f, density: Float = 1f, layoutDirection: LayoutDirection = LayoutDirection.Ltr) = SongTextMeasurementsKey(
        fontFamilyResolver = resolver,
        localeList = LocaleList("en"),
        density = Density(density),
        layoutDirection = layoutDirection,
        lyricsStyle = TextStyle(fontSize = fontSize.sp),
        chordStyle = TextStyle(fontSize = fontSize.sp),
        annotationStyle = TextStyle(fontSize = fontSize.sp),
    )

    @Test
    fun `equal keys made of separately built styles are handed the same measurements`() {
        val measurements = SongTextMeasurementsHolder.of(keyOf())

        assertSame(measurements, SongTextMeasurementsHolder.of(keyOf()))
        assertNotSame(measurements, SongTextMeasurementsHolder.of(keyOf(fontSize = 20f)))
        assertNotSame(measurements, SongTextMeasurementsHolder.of(keyOf(density = 2f)))
        assertNotSame(measurements, SongTextMeasurementsHolder.of(keyOf(layoutDirection = LayoutDirection.Rtl)))
    }

    @Test
    fun `the least recently asked for measurements leave once more are kept`() {
        val first = SongTextMeasurementsHolder.of(keyOf())
        repeat(MAX_KEPT_STYLE_SETS) { SongTextMeasurementsHolder.of(keyOf(fontSize = 15f + it)) }

        assertNotSame(first, SongTextMeasurementsHolder.of(keyOf()))
    }

    @Test
    fun `a chord measured on one page is not measured again on the next`() {
        val chord = ChordProLine.Lyrics.Chord(position = 0, name = "F#m7", isAnnotation = false)
        val layout = SongTextMeasurementsHolder.of(keyOf()).chordLayout(chord)

        assertSame(layout, SongTextMeasurementsHolder.of(keyOf()).chordLayout(chord))
    }
}
