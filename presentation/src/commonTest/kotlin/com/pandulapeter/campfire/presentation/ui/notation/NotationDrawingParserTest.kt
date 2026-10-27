/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.abc.api.AbcSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Over systems shaped as abc2svg writes them: the styles and shared shapes in the first, used by the ones after it. */
class NotationDrawingParserTest {

    @Test
    fun `a system uses the styles and shapes an earlier system of the rendering defined`() {
        val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
        val system = drawing.systems.single()
        assertEquals(360f, system.width)
        assertEquals(92.4f, system.height)
        val staff = system.shapes.first()
        assertEquals(NotationStroke(width = 0.7f, dashes = null), staff.stroke)
        assertEquals(NotationTransform(x = 0f, y = 61.6f, scale = 1f), staff.transform)
        assertEquals(NotationStroke(width = 3f, dashes = null), system.shapes[1].stroke)
        assertNull(system.shapes[2].stroke)
    }

    @Test
    fun `a system that draws nothing is left out`() {
        assertEquals(1, parseNotationDrawing(listOf(HEADER, SYSTEM))?.systems?.size)
    }

    @Test
    fun `music symbols are placed by their own coordinates`() {
        val glyphs = parseNotationDrawing(listOf(HEADER, SYSTEM))!!.systems.single().glyphs
        assertEquals(listOf(10f to 61.6f, 20f to 55.6f), glyphs.map { it.x to it.y })
        assertEquals(listOf(abcMusicGlyphs[0xE050], abcMusicGlyphs[0xE0A4]), glyphs.map { it.glyph })
        assertTrue(glyphs.all { it.size == 24f })
    }

    @Test
    fun `chord symbols and lyrics are told apart, and a group moves its words`() {
        val texts = parseNotationDrawing(listOf(HEADER, SYSTEM))!!.systems.single().texts
        val chord = texts.single { (it.runs.single() as NotationTextRun.Words).text == "Em" }
        val lyric = texts.single { (it.runs.single() as NotationTextRun.Words).text == "la" }
        assertEquals(NotationTextRole.CHORD, (chord.runs.single() as NotationTextRun.Words).role)
        assertEquals(NotationTextRole.LYRIC, (lyric.runs.single() as NotationTextRun.Words).role)
        assertEquals(NotationFont(size = 12f, isBold = false, isItalic = false, isMusic = false), (chord.runs.single() as NotationTextRun.Words).font)
        assertEquals(61.6f - 39.1f, chord.y, 0.001f)
        assertEquals(NotationAnchor.MIDDLE, lyric.anchor)
        assertEquals(NotationFont(size = 13f, isBold = true, isItalic = false, isMusic = false), (lyric.runs.single() as NotationTextRun.Words).font)
    }

    @Test
    fun `a tempo mixes a music symbol into its words`() {
        val tempo = parseNotationDrawing(listOf(HEADER, system("""<style>.f4{font:9.0px text,serif}</style><text class="f4" x="50" y="20"><tspan class="f0" style="font-size:15.6px" dy="-1">&#xECA5;</tspan> <tspan dy="1">=</tspan> 120</text>""")))!!
            .systems.single().texts.single()
        val glyphs = tempo.runs[0] as NotationTextRun.Glyphs
        assertEquals(15.6f, glyphs.size)
        assertEquals(-1f, glyphs.dy)
        assertEquals(listOf(" ", "=", " 120"), tempo.runs.drop(1).map { (it as NotationTextRun.Words).text })
        assertEquals(listOf(-1f, 0f, 0f), tempo.runs.drop(1).map { it.dy })
    }

    @Test
    fun `a scaled group scales what it holds`() {
        val glyph = parseNotationDrawing(listOf(HEADER, system("""<g transform="translate(100,50) scale(0.5)"><text x="10" y="4">&#xE050;</text></g>""")))!!
            .systems.single().glyphs.single()
        assertEquals(105f, glyph.x)
        assertEquals(52f, glyph.y)
        assertEquals(12f, glyph.size)
    }

    @Test
    fun `anything the drawing does not know gives no drawing`() {
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<circle r="2"/>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<g transform="rotate(45)"><path d="M0 0h1"/></g>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<text x="1" y="1">&#xF8FF;</text>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<use x="0" y="0" xlink:href="#missing"/>"""))))
        assertNull(parseNotationDrawing(listOf(AbcSystem(svg = "<svg", height = 1f))))
    }

    private companion object {
        val HEADER = AbcSystem(
            svg = """
                <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
                 xmlns:xlink="http://www.w3.org/1999/xlink"
                 fill="currentColor" stroke-width=".7" class="f0 tune0"
                 width="360px" height="28.00px" viewBox="0 0 360 28.00">
                <style>
                .f0{font:24.0px music}
                @font-face{
                 font-family:music;
                 src:url("data:application/octet-stream;base64,AAEAAAAOAIAAAwBg") format("truetype")}
                .f0 text,tspan{white-space:pre}
                .bthW{stroke:currentColor;fill:none;stroke-width:3}
                .slW{stroke:currentColor;fill:none;stroke-width:.7}
                </style>
                </svg>
            """.trimIndent(),
            height = 28f,
        )

        val SYSTEM = system(
            """
            <style>
            .f3{font:12.0px text,sans-serif}
            .f5{font:bold 13.0px text,serif}
            </style>
            <defs>
            <path id="stdef" class="slW" d="m0 0h358.0m-358.0 -6.00h358.0"/>
            </defs>
            <use x="0.0" y="61.6" xlink:href="#stdef"/>
            <path class="bthW" d="M63.9 61.6v-24.0"/>
            <path d="m134.3 59.4l37.3 4.3v-3.2l-37.3 -4.3z"/>
            <text x="10.0,20.0"
            y="61.6,55.6"
            >&#xE050;&#xE0A4;</text>
            <g transform="translate(0,61.6)">
            <text class="f3 gch" x="208.0" y="-39.1">Em</text>
            <text class="f5 lyr" x="81.0" y="25.9" text-anchor="middle">la</text>
            </g>
            """.trimIndent(),
        )

        fun system(content: String) = AbcSystem(
            svg = """
                <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
                 xmlns:xlink="http://www.w3.org/1999/xlink"
                 fill="currentColor" stroke-width=".7" class="f0 tune0"
                 width="360px" height="92.40px" viewBox="0 0 360 92.40">
            """.trimIndent() + "\n" + content + "\n</svg>",
            height = 92.4f,
        )
    }
}
