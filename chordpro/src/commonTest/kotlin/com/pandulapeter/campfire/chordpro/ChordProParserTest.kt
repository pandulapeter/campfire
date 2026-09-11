/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProParserTest {

    @Test
    fun `long metadata directive names are parsed`() {
        val metadata = ChordProParser.parse(
            """
            {title: The Title}
            {subtitle: The Subtitle}
            {artist: The Artist}
            {composer: The Composer}
            {lyricist: The Lyricist}
            {album: The Album}
            {year: 2026}
            {key: Am}
            {tempo: 96}
            {time: 3/4}
            {duration: 2:30}
            {transpose: +2}
            """.trimIndent()
        ).metadata

        assertEquals("The Title", metadata.title)
        assertEquals("The Subtitle", metadata.subtitle)
        assertEquals("The Artist", metadata.artist)
        assertEquals("The Composer", metadata.composer)
        assertEquals("The Lyricist", metadata.lyricist)
        assertEquals("The Album", metadata.album)
        assertEquals("2026", metadata.year)
        assertEquals("Am", metadata.key)
        assertEquals("96", metadata.tempo)
        assertEquals("3/4", metadata.time)
        assertEquals("2:30", metadata.duration)
        assertEquals(2, metadata.transpose)
    }

    @Test
    fun `short metadata directive names are parsed`() {
        val metadata = ChordProParser.parse("{t: Short Title}\n{st: Short Subtitle}").metadata

        assertEquals("Short Title", metadata.title)
        assertEquals("Short Subtitle", metadata.subtitle)
    }

    @Test
    fun `capo is parsed as a number and ignored when it is not one`() {
        assertEquals(2, ChordProParser.parse("{capo: 2}").metadata.capo)
        assertNull(ChordProParser.parse("{capo: high}").metadata.capo)
    }

    @Test
    fun `meta directives end up in custom`() {
        assertEquals(
            mapOf("tuning" to listOf("DADGAD")),
            ChordProParser.parse("{meta: tuning DADGAD}").metadata.custom,
        )
    }

    @Test
    fun `verse chorus and bridge environments are parsed with their labels`() {
        val blocks = ChordProParser.parse(
            """
            {start_of_verse: Verse 1}
            [Am]first
            {end_of_verse}

            {start_of_chorus: The Chorus}
            [C]second
            {end_of_chorus}

            {start_of_bridge}
            [F]third
            {end_of_bridge}
            """.trimIndent()
        ).blocks

        assertEquals(3, blocks.size)
        assertEquals(SectionType.Verse, (blocks[0] as ChordProBlock.Section).type)
        assertEquals("Verse 1", (blocks[0] as ChordProBlock.Section).label)
        assertEquals(SectionType.Chorus, (blocks[1] as ChordProBlock.Section).type)
        assertEquals("The Chorus", (blocks[1] as ChordProBlock.Section).label)
        assertEquals(SectionType.Bridge, (blocks[2] as ChordProBlock.Section).type)
        assertNull((blocks[2] as ChordProBlock.Section).label)
    }

    @Test
    fun `short environment names and label attributes are parsed`() {
        val plain = ChordProParser.parse("{sov: Verse 1}\n[Am]a\n{eov}").blocks.single() as ChordProBlock.Section
        val attribute = ChordProParser.parse("{sov: label=\"Verse 1\"}\n[Am]a\n{eov}").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Verse, plain.type)
        assertEquals("Verse 1", plain.label)
        assertEquals(plain, attribute)
    }

    @Test
    fun `an unclosed environment at the end of the file is still a section`() {
        val section = ChordProParser.parse("{start_of_verse: Verse 1}\n[Am]a").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Verse, section.type)
        assertEquals(1, section.lines.size)
    }

    @Test
    fun `an environment closes the previous one`() {
        val blocks = ChordProParser.parse("{start_of_verse}\n[Am]a\n{start_of_chorus}\n[C]b\n{end_of_chorus}").blocks

        assertEquals(2, blocks.size)
        assertEquals(SectionType.Verse, (blocks[0] as ChordProBlock.Section).type)
        assertEquals(SectionType.Chorus, (blocks[1] as ChordProBlock.Section).type)
    }

    @Test
    fun `chorus recall is a block of its own`() {
        val blocks = ChordProParser.parse("{start_of_chorus}\n[C]a\n{end_of_chorus}\n\n{chorus}\n\n{chorus: Chorus 2}").blocks

        assertEquals(3, blocks.size)
        assertEquals(ChordProBlock.ChorusRecall(null), blocks[1])
        assertEquals(ChordProBlock.ChorusRecall("Chorus 2"), blocks[2])
    }

    @Test
    fun `the three comment styles are recognised`() {
        val blocks = ChordProParser.parse("{comment: Plain}\n{ci: Italic}\n{comment_box: Boxed}").blocks

        assertEquals(ChordProBlock.Comment("Plain", CommentStyle.PLAIN), blocks[0])
        assertEquals(ChordProBlock.Comment("Italic", CommentStyle.ITALIC), blocks[1])
        assertEquals(ChordProBlock.Comment("Boxed", CommentStyle.BOX), blocks[2])
    }

    @Test
    fun `source comment lines are ignored`() {
        val blocks = ChordProParser.parse("# a note to self\n   # indented too\n[Am]a").blocks

        val section = blocks.single() as ChordProBlock.Section
        assertEquals(1, section.lines.size)
    }

    @Test
    fun `lines outside environments form paragraphs split by blank lines`() {
        val blocks = ChordProParser.parse("first\nsecond\n\nthird").blocks

        assertEquals(2, blocks.size)
        assertEquals(SectionType.Paragraph, (blocks[0] as ChordProBlock.Section).type)
        assertEquals(2, (blocks[0] as ChordProBlock.Section).lines.size)
        assertEquals(1, (blocks[1] as ChordProBlock.Section).lines.size)
    }

    @Test
    fun `legacy comment headings open implicit sections`() {
        val blocks = ChordProParser.parse(
            """
            {c: Verse 1}
            [G]a

            {c: Pre-Chorus}
            [C]b

            {c: Chorus}
            [D]c
            """.trimIndent()
        ).blocks

        assertEquals(3, blocks.size)
        assertEquals(SectionType.Verse, (blocks[0] as ChordProBlock.Section).type)
        assertEquals("Verse 1", (blocks[0] as ChordProBlock.Section).label)
        assertEquals(SectionType.Custom("pre-chorus"), (blocks[1] as ChordProBlock.Section).type)
        assertEquals("Pre-Chorus", (blocks[1] as ChordProBlock.Section).label)
        assertEquals(SectionType.Chorus, (blocks[2] as ChordProBlock.Section).type)
    }

    @Test
    fun `a legacy heading inside an explicit environment stays a comment`() {
        val blocks = ChordProParser.parse("{start_of_chorus}\n{c: Verse 1}\n[C]a\n{end_of_chorus}").blocks

        assertEquals(2, blocks.size)
        assertEquals(ChordProBlock.Comment("Verse 1", CommentStyle.PLAIN), blocks[0])
        assertEquals(SectionType.Chorus, (blocks[1] as ChordProBlock.Section).type)
    }

    @Test
    fun `tab lines keep their indentation`() {
        val section = ChordProParser.parse("{start_of_tab: Riff}\n  e|---0---|\n{end_of_tab}").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Tab, section.type)
        assertEquals("Riff", section.label)
        assertEquals(ChordProLine.Tab("  e|---0---|"), section.lines.single())
    }

    @Test
    fun `grid lines are split into tokens`() {
        val section = ChordProParser.parse("{start_of_grid}\n| Am . . . | C . . . |\n{end_of_grid}").blocks.single() as ChordProBlock.Section

        assertEquals(
            listOf(
                GridToken.Bar("|"),
                GridToken.Chord("Am"),
                GridToken.Beat,
                GridToken.Beat,
                GridToken.Beat,
                GridToken.Bar("|"),
                GridToken.Chord("C"),
                GridToken.Beat,
                GridToken.Beat,
                GridToken.Beat,
                GridToken.Bar("|"),
            ),
            (section.lines.single() as ChordProLine.Grid).tokens,
        )
    }

    @Test
    fun `text after the last bar of a grid line is free text`() {
        val section = ChordProParser.parse("{sog}\n| Am | (twice)\n{eog}").blocks.single() as ChordProBlock.Section

        assertEquals(GridToken.Text("(twice)"), (section.lines.single() as ChordProLine.Grid).tokens.last())
    }

    @Test
    fun `annotations are told apart from chords and empty brackets are dropped`() {
        val section = ChordProParser.parse("[Am]a []b [*hold]c").blocks.single() as ChordProBlock.Section
        val line = section.lines.single() as ChordProLine.Lyrics

        assertEquals("a b c", line.text)
        assertEquals(
            listOf(
                ChordProLine.Lyrics.Chord(position = 0, name = "Am", isAnnotation = false),
                ChordProLine.Lyrics.Chord(position = 4, name = "hold", isAnnotation = true),
            ),
            line.chords,
        )
    }

    @Test
    fun `summarize reports no chords for a lyrics only song and for annotations only`() {
        val lyricsOnly = "{title: T}\n\njust some words\nand some more"
        val annotationsOnly = "{title: T}\n\n[*softly]just some words"

        assertFalse(ChordProParser.summarize(lyricsOnly).hasChords)
        assertFalse(ChordProParser.parse(lyricsOnly).hasChords)
        assertFalse(ChordProParser.summarize(annotationsOnly).hasChords)
        assertFalse(ChordProParser.parse(annotationsOnly).hasChords)
    }

    @Test
    fun `summarize reports chords in lyrics and in grids but not in tabs`() {
        val inTab = "{start_of_tab}\ne|--[Am]--|\n{end_of_tab}"

        assertTrue(ChordProParser.summarize("[Am]a").hasChords)
        assertTrue(ChordProParser.parse("[Am]a").hasChords)
        assertTrue(ChordProParser.summarize("{sog}\n| Am . . . |\n{eog}").hasChords)
        assertTrue(ChordProParser.parse("{sog}\n| Am . . . |\n{eog}").hasChords)
        assertFalse(ChordProParser.summarize(inTab).hasChords)
        assertFalse(ChordProParser.parse(inTab).hasChords)
    }

    @Test
    fun `carriage returns are accepted and a trailing newline adds no line`() {
        val song = ChordProParser.parse("{title: T}\r\n\r\n{start_of_verse}\r\n[Am]a\r\n\r\n{end_of_verse}\r\n")

        assertEquals("T", song.metadata.title)
        val section = song.blocks.single() as ChordProBlock.Section
        assertEquals(listOf(ChordProParser.parseLyrics("[Am]a")), section.lines)
    }

    @Test
    fun `unknown and selector suffixed directives are ignored`() {
        val song = ChordProParser.parse("{define: Am base-fret 1 frets 0 0 2 2 1 0}\n{title-guitar: Ignored}\n{pagetype: a4}\n[Am]a")

        assertNull(song.metadata.title)
        assertEquals(emptyMap(), song.metadata.custom)
        assertEquals(1, song.blocks.size)
    }

    @Test
    fun `x prefixed directives are kept as custom metadata`() {
        assertEquals(
            mapOf("x_custom" to listOf("kept")),
            ChordProParser.parse("{x_custom: kept}").metadata.custom,
        )
    }

    @Test
    fun `summarize reads the metadata of a song whose chords come before the last directive`() {
        val summary = ChordProParser.summarize("{title: T}\n\n[Am]a\n\n{tag: campfire}")

        assertTrue(summary.hasChords)
        assertEquals("T", summary.metadata.title)
        assertEquals(listOf("campfire"), summary.metadata.tags)
    }

    @Test
    fun `parseMetadata matches the metadata of a full parse`() {
        val text = """
            # a comment
            {title: The Title}
            {t: Overridden Title}
            {st: The Subtitle}
            {artist: The Artist}
            {key: Am}
            {capo: 2}
            {transpose: 1}
            {meta: tuning DADGAD}
            {x_custom: kept}
            {comment: Not metadata}

            {start_of_verse: Verse 1}
            [Am]a
            {end_of_verse}
        """.trimIndent()

        assertEquals(ChordProParser.parse(text).metadata, ChordProParser.parseMetadata(text))
        assertEquals("Overridden Title", ChordProParser.parseMetadata(text).title)
    }
}
