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
    fun `the accidental signs of a file are read into the ones the app writes`() {
        val song = ChordProParser.parse("{key: F♯m}\n[B♭]a [F♯m7♭5/C♯]b [*B♭ only]c ♭\n{sog}\n| E♭ . |\n{eog}")

        assertEquals("F#m", song.metadata.key)
        val lines = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        assertEquals(listOf("Bb", "F#m7b5/C#", "B♭ only"), (lines[0] as ChordProLine.Lyrics).chords.map { it.name })
        assertEquals(listOf("Eb"), (lines[1] as ChordProLine.Grid).tokens.filterIsInstance<GridToken.Chord>().map { it.name })
    }

    @Test
    fun `a song written in German notation is parsed into the app's own`() {
        val song = ChordProParser.parse("{key: H}\n[H7]a [B]b [F/B]c\n{sog}\n| H . | B . |\n{eog}")

        assertEquals("B", song.metadata.key)
        val lines = (song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines })
        assertEquals(listOf("B7", "Bb", "F/Bb"), (lines[0] as ChordProLine.Lyrics).chords.map { it.name })
        assertEquals(listOf("B", "Bb"), (lines[1] as ChordProLine.Grid).tokens.filterIsInstance<GridToken.Chord>().map { it.name })
    }

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
        assertEquals(ChordProBlock.ChorusRecall(null, blocks = listOf(blocks[0])), blocks[1])
        assertEquals(ChordProBlock.ChorusRecall("Chorus 2", blocks = listOf(blocks[0])), blocks[2])
    }

    @Test
    fun `the half of a section after a comment is its continuation`() {
        listOf(
            "{soc}\n[C]one\n{comment: softly}\n[G]two\n{eoc}",
            "{soc}\n[C]one\n{colb}\n[G]two\n{eoc}",
            "{soc}\n[C]one\n{ci: x}\n[G]two\n{eoc}",
            "{c: Verse 1}\n[G]a\n{ci: x}\n[C]b",
        ).forEach { text ->
            val blocks = ChordProParser.parse(text).blocks

            assertEquals(3, blocks.size, text)
            val first = blocks[0] as ChordProBlock.Section
            val second = blocks[2] as ChordProBlock.Section
            assertFalse(first.isContinuation, text)
            assertTrue(second.isContinuation, text)
            assertEquals(first.type, second.type, text)
            assertEquals(first.label, second.label, text)
        }
    }

    @Test
    fun `a recall carries every piece of the last chorus`() {
        val blocks = ChordProParser.parse("{soc}\n[C]one\n{comment: softly}\n[G]two\n{eoc}\n\n{chorus}").blocks

        assertEquals(
            listOf(
                ChordProBlock.Section(SectionType.Chorus, null, listOf(ChordProParser.parseLyrics("[C]one"))),
                ChordProBlock.Comment("softly", CommentStyle.PLAIN),
                ChordProBlock.Section(SectionType.Chorus, null, listOf(ChordProParser.parseLyrics("[G]two")), isContinuation = true),
            ),
            (blocks.last() as ChordProBlock.ChorusRecall).blocks,
        )
    }

    @Test
    fun `a recall with no chorus before it carries nothing`() {
        val blocks = ChordProParser.parse("{sov}\n[C]one\n{eov}\n{chorus}").blocks

        assertEquals(ChordProBlock.ChorusRecall(null), blocks.last())
    }

    @Test
    fun `a recall inside a chorus repeats the chorus before it`() {
        val blocks = ChordProParser.parse("{soc}\n[A]first\n{eoc}\n{soc}\n{sot}\ne|-0-|\n{chorus}\ne|-2-|\n{eot}\n{eoc}").blocks

        val recall = blocks.filterIsInstance<ChordProBlock.ChorusRecall>().single()
        assertEquals(listOf(blocks[0]), recall.blocks)
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
    fun `a legacy heading with no lines under it is kept as the comment it was`() {
        val blocks = ChordProParser.parse("{sov}\n[Am]la\n{eov}\n{c: Chorus x2}\n{soc}\n[C]lo\n{eoc}").blocks

        assertEquals(3, blocks.size)
        assertEquals(SectionType.Verse, (blocks[0] as ChordProBlock.Section).type)
        assertEquals(ChordProBlock.Comment("Chorus x2", CommentStyle.PLAIN), blocks[1])
        assertEquals(SectionType.Chorus, (blocks[2] as ChordProBlock.Section).type)
    }

    @Test
    fun `a legacy heading followed by a blank line is kept as a comment`() {
        val blocks = ChordProParser.parse("{c: Chorus x2}\n\n[C]lo").blocks

        assertEquals(2, blocks.size)
        assertEquals(ChordProBlock.Comment("Chorus x2", CommentStyle.PLAIN), blocks[0])
        assertEquals(SectionType.Paragraph, (blocks[1] as ChordProBlock.Section).type)
    }

    @Test
    fun `a legacy heading at the end of the file is kept as a comment`() {
        val blocks = ChordProParser.parse("[C]la\n\n{c: Outro}").blocks

        assertEquals(ChordProBlock.Comment("Outro", CommentStyle.PLAIN), blocks.last())
    }

    @Test
    fun `a break right after a legacy heading keeps the heading`() {
        val blocks = ChordProParser.parse("{c: Chorus}\n{colb}\n[C]la").blocks

        assertEquals(2, blocks.size)
        assertEquals(ChordProBlock.Break, blocks[0])
        assertEquals("Chorus", (blocks[1] as ChordProBlock.Section).label)
    }

    @Test
    fun `a break at the end of a legacy heading section does not repeat the heading`() {
        val blocks = ChordProParser.parse("{c: Chorus}\n[C]la\n{colb}").blocks

        assertEquals(2, blocks.size)
        assertEquals("Chorus", (blocks[0] as ChordProBlock.Section).label)
        assertEquals(ChordProBlock.Break, blocks[1])
    }

    @Test
    fun `tab lines keep their indentation`() {
        val section = ChordProParser.parse("{start_of_tab: Riff}\n  e|---0---|\n{end_of_tab}").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Paragraph, section.type)
        assertEquals("Riff", section.label)
        assertEquals(ChordProLine.Tab("  e|---0---|"), section.lines.single())
    }

    @Test
    fun `a tab environment inside a section is a run of lines rather than a section of its own`() {
        val blocks = ChordProParser.parse(
            """
            {start_of_verse: Solo}
            [Am]over the solo
            {start_of_tab}
            e|---0---|
            {end_of_tab}
            back to [C]lyrics
            {end_of_verse}
            """.trimIndent()
        ).blocks

        val section = blocks.single() as ChordProBlock.Section
        assertEquals(SectionType.Verse, section.type)
        assertEquals("Solo", section.label)
        assertEquals(3, section.lines.size)
        assertTrue(section.lines[0] is ChordProLine.Lyrics)
        assertEquals(ChordProLine.Tab("e|---0---|"), section.lines[1])
        assertTrue(section.lines[2] is ChordProLine.Lyrics)
    }

    @Test
    fun `a grid environment inside a section does not break it up either`() {
        val section = ChordProParser.parse(
            """
            {start_of_chorus}
            {start_of_grid}
            | Am | C |
            {end_of_grid}
            [G]and on
            {end_of_chorus}
            """.trimIndent()
        ).blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Chorus, section.type)
        assertEquals(2, section.lines.size)
        assertTrue(section.lines[0] is ChordProLine.Grid)
        assertTrue(section.lines[1] is ChordProLine.Lyrics)
    }

    @Test
    fun `a comment inside a tab environment does not end the tablature`() {
        val blocks = ChordProParser.parse("{start_of_tab: Riff}\ne|---0---2---|\n{comment: Repeat x2}\ne|---3---5---|\n{end_of_tab}\nla [C]la").blocks

        assertEquals(3, blocks.size)
        assertEquals(ChordProLine.Tab("e|---0---2---|"), (blocks[0] as ChordProBlock.Section).lines.single())
        assertEquals(ChordProBlock.Comment("Repeat x2", CommentStyle.PLAIN), blocks[1])
        val secondSection = blocks[2] as ChordProBlock.Section
        assertEquals("Riff", secondSection.label)
        assertEquals(ChordProLine.Tab("e|---3---5---|"), secondSection.lines[0])
        assertEquals(ChordProParser.parseLyrics("la [C]la"), secondSection.lines[1])
    }

    @Test
    fun `a break inside a grid environment does not end the grid`() {
        listOf("{column_break}", "{new_page}", "{np}", "{colb}", "{ci: x}", "{cb: x}").forEach { directive ->
            val blocks = ChordProParser.parse("{start_of_grid}\n| Am . . . |\n$directive\n| C . . . |\n{end_of_grid}").blocks

            assertEquals(3, blocks.size)
            assertEquals(
                ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("C"), GridToken.Beat, GridToken.Beat, GridToken.Beat, GridToken.Bar("|"))),
                (blocks[2] as ChordProBlock.Section).lines.single(),
            )
        }
    }

    @Test
    fun `a legacy heading name inside a tab environment stays a comment`() {
        val blocks = ChordProParser.parse("{sot}\ne|---0---|\n{c: Solo}\ne|---3---|\n{eot}").blocks

        assertEquals(ChordProBlock.Comment("Solo", CommentStyle.PLAIN), blocks[1])
        assertEquals(ChordProLine.Tab("e|---3---|"), (blocks[2] as ChordProBlock.Section).lines.single())
    }

    @Test
    fun `a legacy heading still opens the section a tab is written in`() {
        val section = ChordProParser.parse("{c: Solo}\n{sot}\ne|---0---|\n{eot}").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Custom("solo"), section.type)
        assertEquals("Solo", section.label)
        assertEquals(listOf(ChordProLine.Tab("e|---0---|")), section.lines)
    }

    @Test
    fun `a chorus recall inside a tab environment does not end the tablature`() {
        val blocks = ChordProParser.parse("{sot}\ne|---0---|\n{chorus}\ne|---3---|\n{eot}").blocks

        assertEquals(ChordProBlock.ChorusRecall(null), blocks[1])
        assertEquals(ChordProLine.Tab("e|---3---|"), (blocks[2] as ChordProBlock.Section).lines.single())
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
    fun `the words before the first bar of a grid line are its margin label`() {
        assertEquals(
            listOf(GridToken.Text("A"), GridToken.Bar("||"), GridToken.Chord("G7"), GridToken.Beat, GridToken.Bar("|")),
            ChordProSyntax.parseGridTokens("A    || G7 . |"),
        )
        assertEquals(
            listOf(GridToken.Text("Coda"), GridToken.Bar("|"), GridToken.Chord("D7"), GridToken.Bar("|.")),
            ChordProSyntax.parseGridTokens("Coda | D7 |."),
        )
        assertEquals(listOf(GridToken.Chord("Am"), GridToken.Chord("C")), ChordProSyntax.parseGridTokens("Am C"))
    }

    @Test
    fun `repeats voltas and chord positions are not chords`() {
        assertEquals(
            listOf(
                GridToken.Bar("|:"),
                GridToken.Chord("C7"),
                GridToken.Text("/"),
                GridToken.Bar(":|:"),
                GridToken.Chord("G7"),
                GridToken.Beat,
                GridToken.Bar(":|2>"),
                GridToken.Chord("D"),
                GridToken.Bar("|"),
            ),
            ChordProSyntax.parseGridTokens("|: C7 / :|: G7 . :|2> D |"),
        )
        val voltas = ChordProSyntax.parseGridTokens("|1 C :|2 D |")
        assertEquals(GridToken.Bar("|1"), voltas.first())
        assertTrue(GridToken.Bar(":|2") in voltas)
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
    fun `tags that differ only in case are one tag, the first spelling winning`() {
        assertEquals(listOf("Demo"), ChordProParser.parseMetadata("{tag: Demo}\n{tag: DEMO}\n{tag: demo}").tags)
    }

    @Test
    fun `a dotted capital I and a plain i are the same tag, as equals ignoring case says`() {
        assertEquals(listOf("İstanbul"), ChordProParser.parseMetadata("{tag: İstanbul}\n{tag: istanbul}").tags)
    }

    @Test
    fun `the order of tags and languages is the order of the file`() {
        val metadata = ChordProParser.parseMetadata(
            "{tag: b}\n{meta: language hu}\n{tag: c}\n{lang: en}\n{tag: a}\n{meta: language de}\n{tag: B}\n{lang: hu}",
        )

        assertEquals(listOf("b", "c", "a"), metadata.tags)
        assertEquals(listOf("hu", "en", "de"), metadata.languages)
    }

    @Test
    fun `a song with a hundred thousand distinct tags or languages is parsed in linear time`() {
        val tags = (0 until 100_000).joinToString("\n") { "{tag: t$it}" }
        val languages = (0 until 100_000).joinToString("\n") { "{lang: q$it}" }

        assertEquals(100_000, ChordProParser.summarize(tags).metadata.tags.size)
        assertEquals(100_000, ChordProParser.summarize(languages).metadata.languages.size)
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

    @Test
    fun `directives written with whitespace instead of a colon are read`() {
        val song = ChordProParser.parse(
            "{title Wonderwall}\n{artist Oasis}\n{tag Needs study}\n{meta language en}\n\n{start_of_verse Verse 1}\n[Am]a\n{end_of_verse}\n\n" +
                    "{start_of_grid shape=\"1+4x2+4\"}\n| C . |\n{end_of_grid}",
        )

        assertEquals("Wonderwall", song.metadata.title)
        assertEquals("Oasis", song.metadata.artist)
        assertEquals(listOf("Needs study"), song.metadata.tags)
        assertEquals(listOf("en"), song.metadata.languages)
        assertEquals(ChordProBlock.Section(SectionType.Verse, "Verse 1", listOf(ChordProParser.parseLyrics("[Am]a"))), song.blocks[0])
        val grid = song.blocks[1] as ChordProBlock.Section
        assertEquals(SectionType.Paragraph, grid.type)
        assertNull(grid.label)
        assertTrue(grid.lines.single() is ChordProLine.Grid)
        assertEquals(ChordProParser.parse("{sov: Verse 1}\n[Am]a\n{eov}"), ChordProParser.parse("{sov label='Verse 1'}\n[Am]a\n{eov}"))
        assertEquals(
            listOf<ChordProBlock>(ChordProBlock.Section(SectionType.Paragraph, null, listOf(ChordProLine.Lyrics("{Verse 2}", emptyList())))),
            ChordProParser.parse("{Verse 2}").blocks,
        )
    }

    @Test
    fun `the standard meta names are read as their own directives`() {
        val text = "{meta: title Amazing Grace}\n{meta: Artist John Newton}\n{meta: key G}\n{meta: capo 2}\n{meta: tuning DADGAD}"
        val metadata = ChordProParser.parse(text).metadata

        assertEquals("Amazing Grace", metadata.title)
        assertEquals("John Newton", metadata.artist)
        assertEquals("G", metadata.key)
        assertEquals(2, metadata.capo)
        assertEquals(mapOf("tuning" to listOf("DADGAD")), metadata.custom)
        assertEquals(metadata, ChordProParser.summarize(text).metadata)
        assertEquals(metadata, ChordProParser.parseMetadata(text))
        assertEquals("B", ChordProParser.summarize("{meta: key H}\n[H]a").metadata.key)
    }

    @Test
    fun `a song is in the key it starts in`() {
        val text = "{key: F}\n[C]a\n{key: A}\n[E]b"

        assertEquals("F", ChordProParser.parse(text).metadata.key)
        assertEquals("F", ChordProParser.summarize(text).metadata.key)
        assertEquals("G", ChordProParser.parse("{key: }\n{key: G}").metadata.key)
    }

    @Test
    fun `the lines of an abc block are kept verbatim with no chords`() {
        val section = ChordProParser.parse("{start_of_abc}\nX:1\n[CEG]2 [A2B] |\n{end_of_abc}").blocks.single() as ChordProBlock.Section

        assertEquals(SectionType.Custom("abc"), section.type)
        assertEquals(listOf(ChordProLine.Lyrics("X:1", emptyList()), ChordProLine.Lyrics("[CEG]2 [A2B] |", emptyList())), section.lines)
        assertFalse(ChordProParser.summarize("{start_of_ly}\n[c e g]\n{end_of_ly}").hasChords)
        val textBlock = ChordProParser.parse("{start_of_textblock}\nfirst\n{comment: Chorus}\nsecond\n{end_of_textblock}").blocks
        assertEquals(ChordProBlock.Comment("Chorus", CommentStyle.PLAIN), textBlock[1])
    }

    @Test
    fun `an environment with a selector is the environment it selects`() {
        val blocks = ChordProParser.parse("{start_of_chorus-guitar}\n[C]la\n{end_of_chorus}\n{chorus}").blocks

        assertEquals(SectionType.Chorus, (blocks[0] as ChordProBlock.Section).type)
        assertEquals(ChordProBlock.ChorusRecall(null, blocks = listOf(blocks[0])), blocks[1])
        assertEquals(
            SectionType.Custom("pre-chorus"),
            (ChordProParser.parse("{start_of_pre-chorus}\n[C]la\n{end_of_pre-chorus}").blocks.single() as ChordProBlock.Section).type,
        )
        val tab = ChordProParser.parse("{start_of_tab-guitar}\ne|--0--|\n{end_of_tab}").blocks.single() as ChordProBlock.Section
        assertEquals(listOf(ChordProLine.Tab("e|--0--|")), tab.lines)
    }

    @Test
    fun `a negated selector is read as the directive it is written on`() {
        assertEquals("X", ChordProParser.parse("{title-guitar!: X}").metadata.title)
        assertNull(ChordProParser.parse("{title-guitar: Y}").metadata.title)
        assertNull(ChordProSyntax.matchDirective("{tit!le: X}"))
        assertNull(ChordProSyntax.matchDirective("{title!: X}"))
    }

    @Test
    fun `a highlight is shown as a comment and never taken for a heading`() {
        val blocks = ChordProParser.parse("{highlight: Chorus}\n[C]la").blocks

        assertEquals(ChordProBlock.Comment("Chorus", CommentStyle.PLAIN), blocks.first())
        assertEquals(SectionType.Paragraph, (blocks[1] as ChordProBlock.Section).type)
        val tab = ChordProParser.parse("{sot}\ne|-3-|\n{highlight: x}\ne|-5-|\n{eot}").blocks
        assertEquals(3, tab.size)
        assertEquals(ChordProLine.Tab("e|-3-|"), (tab[0] as ChordProBlock.Section).lines.single())
        assertEquals(ChordProBlock.Comment("x", CommentStyle.PLAIN), tab[1])
        assertEquals(ChordProLine.Tab("e|-5-|"), (tab[2] as ChordProBlock.Section).lines.single())
    }
}
