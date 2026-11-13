/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ChordSheetHtmlTest {

    @Test
    fun `chords drawn from empty elements are set over the characters they stand before`() {
        assertEquals(
            """
            |                    Em
            |Well, I heard there was a secret chord
            |     G                   Em
            |That David played and it pleased the Lord
            |
            |      C          Em         C          G    D   G
            |Hallelujah, Hallelujah, Hallelujah, Hallelujah
            """.trimMargin(),
            ChordSheetHtml.chordSheetText(HALLELUJAH),
        )
    }

    @Test
    fun `a fragment of many tags left open or closed astray is read in linear time`() {
        val count = 400_000
        val html = "<span data-chord=\"Am\"></span>" + "<span>a".repeat(count) + "</i>".repeat(count)
        val started = TimeSource.Monotonic.markNow()
        val text = ChordSheetHtml.chordSheetText(html)
        assertTrue(started.elapsedNow() < 30.seconds, started.elapsedNow().toString())
        assertEquals("Am\n" + "a".repeat(count), text)
    }

    @Test
    fun `a chord over a word's middle splits it in the converted song`() {
        val song = ChordSheetConverter.convert(ChordSheet.ofPlainText(ChordSheetHtml.chordSheetText(HALLELUJAH)!!)).single()

        assertTrue("Hallel[C]ujah, Halle[Em]lujah" in song, song)
        assertTrue("That [G]David played and it [Em]pleased the Lord" in song, song)
    }

    @Test
    fun `a chord too close to the one before it keeps a space after that one`() {
        assertEquals(
            "Am7 Cmaj7\nab",
            ChordSheetHtml.chordSheetText("<div><i data-chord=\"Am7\"></i>a<i data-chord=\"Cmaj7\"></i>b</div>"),
        )
    }

    @Test
    fun `the first chord-valued data attribute names the chord`() {
        assertEquals(
            "F#\nSing",
            ChordSheetHtml.chordSheetText("<p><span data-i=\"0\" data-a=\"F#\" data-aff=\"Gb\"></span>Sing</p>"),
        )
    }

    @Test
    fun `entities are decoded and line breaks, paragraphs, and source whitespace read as a browser lays them out`() {
        assertEquals(
            "C\nRock &  roll\n      G\nsweet été\n\nlast",
            ChordSheetHtml.chordSheetText(
                "<p>\n  <b data-c=\"C\"></b>Rock &amp;&nbsp; roll<br>\n  sweet <u data-c=\"G\"/>&eacute;t&#233;\n</p>last",
            ),
        )
    }

    @Test
    fun `an element with text in it is no chord, whatever its attributes`() {
        assertNull(ChordSheetHtml.chordSheetText("<div><span data-chord=\"Am\">Am</span> walking</div>"))
    }

    @Test
    fun `a fragment with no chord-valued empty element is left to its plain text`() {
        assertNull(ChordSheetHtml.chordSheetText("<pre>Am      C\nHello there</pre><div data-id=\"1\" data-lang=\"en\"></div>"))
    }

    private companion object {
        /** A trimmed copy of boiteachansons.net's Hallelujah as Chrome puts it on the clipboard. */
        const val HALLELUJAH = "<meta charset='utf-8'>" +
            "<div class=\"pL\" style=\"padding-top: 10px; font-family: &quot;Oxygen Mono&quot;, monospace;\">Well, I heard there " +
            "<span class=\"sI\" style=\"display: inline-block; width: 0px;\"><span class=\"a\" data-a=\"Em\" data-aff=\"Em\" " +
            "data-i=\"1\"></span></span>was a secret chord</div>" +
            "<div class=\"pL\">That <span class=\"sI\"><span class=\"a\" data-a=\"G\" data-aff=\"G\" data-i=\"0\"></span></span>" +
            "David played and it <span class=\"sI\"><span class=\"a\" data-a=\"Em\" data-aff=\"Em\" data-i=\"1\"></span></span>" +
            "pleased the Lord</div>" +
            "<div class=\"pLVV\"></div>" +
            "<div class=\"pL\">Hallel<span class=\"sI\"><span class=\"a\" data-a=\"C\" data-aff=\"C\" data-i=\"2\"></span></span>" +
            "ujah, Halle<span class=\"sI\"><span class=\"a\" data-a=\"Em\" data-aff=\"Em\" data-i=\"1\"></span></span>lujah, " +
            "Hall<span class=\"sI\"><span class=\"a\" data-a=\"C\" data-aff=\"C\" data-i=\"2\"></span></span>elujah, " +
            "Hal<span class=\"sI\"><span class=\"a\" data-a=\"G\" data-aff=\"G\" data-i=\"0\"></span></span>leluj" +
            "<span class=\"sI\"><span class=\"a\" data-a=\"D\" data-aff=\"D\" data-i=\"3\"></span></span>ah&nbsp;&nbsp;" +
            "<span class=\"sI\"><span class=\"a\" data-a=\"G\" data-aff=\"G\" data-i=\"0\"></span></span></div>" +
            "<div class=\"pLVV\"></div><br class=\"Apple-interchange-newline\">"
    }
}
