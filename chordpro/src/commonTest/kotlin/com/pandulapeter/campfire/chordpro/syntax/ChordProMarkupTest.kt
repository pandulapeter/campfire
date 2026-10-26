/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSerializer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting.Style
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProMarkupTest {

    @Test
    fun `bold, italic, and underline tags format the text they enclose`() {
        val line = lyrics("a <b>bold</b>, <I>italic</I>, <strong><em>both</em></strong> and <u>under</u>")

        assertEquals("a bold, italic, both and under", line.text)
        assertEquals(
            listOf(
                Formatting(2, 6, Style.BOLD),
                Formatting(8, 14, Style.ITALIC),
                Formatting(16, 20, Style.BOLD),
                Formatting(16, 20, Style.ITALIC),
                Formatting(25, 30, Style.UNDERLINE),
            ),
            line.formatting,
        )
    }

    @Test
    fun `chords stay on the characters they were written at`() {
        val line = lyrics("[C]There <b>[Am]is a</b> [F]house")

        assertEquals("There is a house", line.text)
        assertEquals(listOf(0, 6, 11), line.chords.map { it.position })
        assertEquals(listOf(Formatting(6, 10, Style.BOLD)), line.formatting)
    }

    @Test
    fun `a tag left open ends with the line and a stray closing tag is dropped`() {
        val (line, next) = lines("one</i> <u>two\nthree")

        assertEquals("one two", line.text)
        assertEquals(listOf(Formatting(4, 7, Style.UNDERLINE)), line.formatting)
        assertEquals(emptyList(), next.formatting)
    }

    @Test
    fun `other text in angle brackets stays lyrics`() {
        val line = lyrics("<3 <intro> </chorus> a<b c> <span color> <font size=> <b>")

        assertEquals("<3 <intro> </chorus> a<b c> <span color> <font size=> ", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `tags without a style are hidden and keep their text`() {
        val line = lyrics("x<SUP>2</SUP> <span foreground=\"red\" size='large'>red</span> <font color=red>d</font>")

        assertEquals("x2 red d", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `styled tags may carry attributes and sit inside hidden ones`() {
        val line = lyrics("<span size=\"large\"><b class=\"x\">[G]loud</b></span> soft")

        assertEquals("loud soft", line.text)
        assertEquals(listOf(Formatting(0, 4, Style.BOLD)), line.formatting)
        assertEquals(0, line.chords.single().position)
    }

    @Test
    fun `images are hidden whatever their address is written as`() {
        val line = lyrics("a<img src=\"cover.png\"/>b<IMG src=https://example.com/c.png>c<img src=c.png/>d<img alt='x' />e")

        assertEquals("abcde", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `a line break is a space between the words it separates`() {
        val line = lyrics("one<br>two <br/>three<BR /> [C]<br>four")

        assertEquals("one two three four", line.text)
        assertEquals(14, line.chords.single().position)
    }

    @Test
    fun `a span's attributes set the styles they name`() {
        val line = lyrics(
            "<span weight=\"bold\">a</span> <span font_style='italic' underline=\"single\">b</span> " +
                "<span style=\"font-weight: 700; text-decoration: underline\">c</span> <span style=\"oblique\">d</span> " +
                "<span font_desc=\"Sans Bold 12\">e</span> <span weight=\"normal\" underline=\"none\">f</span>",
        )

        assertEquals("a b c d e f", line.text)
        assertEquals(
            listOf(
                Formatting(0, 1, Style.BOLD),
                Formatting(2, 3, Style.ITALIC),
                Formatting(2, 3, Style.UNDERLINE),
                Formatting(4, 5, Style.BOLD),
                Formatting(4, 5, Style.UNDERLINE),
                Formatting(6, 7, Style.ITALIC),
                Formatting(8, 9, Style.BOLD),
            ),
            line.formatting,
        )
    }

    @Test
    fun `a closing span ends the styles of the innermost span open`() {
        val line = lyrics("<span weight=\"bold\">a<span color=\"red\">b</span>c</span>d</span><span style=\"italic\">e")

        assertEquals("abcde", line.text)
        assertEquals(listOf(Formatting(0, 3, Style.BOLD), Formatting(4, 5, Style.ITALIC)), line.formatting)
    }

    @Test
    fun `a self-closing styled tag formats nothing`() {
        val line = lyrics("a<b/>b")

        assertEquals("ab", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `comments drop hidden tags when stripped`() {
        assertEquals("Capo 2 slow", ChordProMarkup.strip("<span weight=\"bold\">Capo 2</span> <small>slow</small>").text)
    }

    @Test
    fun `adjacent ranges of one style are one range`() {
        assertEquals(listOf(Formatting(0, 2, Style.BOLD)), lyrics("<b>a</b><b>b</b>").formatting)
    }

    @Test
    fun `markup inside a chord's brackets leaves the chord's name and formats nothing`() {
        val line = lyrics("[<b>Am</b>]la [<span color=\"red\">C&#x266F;</span>]li")

        assertEquals(listOf("Am", "C#"), line.chords.map { it.name })
        assertEquals("la li", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `a symbol is the text a chart writes it as`() {
        val line = lyrics("B<sym flat/> <SYM color=\"red\" arrow-up-with-accent /> <sym circle-A/> <sym repeat-end/> <sym unknown/>x<strut w=\"1em\"/>y")

        assertEquals("B♭ ↑ A :| xy", line.text)
    }

    @Test
    fun `character references are read as the characters they name`() {
        val line = lyrics("&lt;3 &lt;b&gt;not bold&lt;/b&gt; Rock &amp; Roll &#233;&#x1F3B8; [C]&amp;&nbsp;Rock & Roll &bogus; &#0;")

        assertEquals("<3 <b>not bold</b> Rock & Roll é🎸 & Rock & Roll &bogus; &#0;", line.text)
        assertEquals(emptyList(), line.formatting)
        assertEquals(35, line.chords.single().position)
    }

    @Test
    fun `formatted lines survive serializing`() {
        val parsed = ChordProParser.parse("<b>[C]Bold</b> plain [G]<i>it<u>al</i>ic</u>[D]\n[Am]<b></b>x")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    private fun lyrics(line: String) = lines(line).single()

    private fun lines(text: String) = ChordProParser.parse(text).blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>()
}
