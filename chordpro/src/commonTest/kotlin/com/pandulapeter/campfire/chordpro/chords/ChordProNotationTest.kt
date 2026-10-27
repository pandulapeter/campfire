/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProNotationTest {

    @Test
    fun `a tab label inside a section is shown in the reader's notation`() {
        val song = ChordProParser.parse("{start_of_verse}\n[G]la\n{start_of_tab: Riff [B]}\ne|--3--|\n{end_of_tab}\n{end_of_verse}")

        val labels = ChordProNotation.toNotation(song, ChordNotation.GERMAN).blocks.filterIsInstance<ChordProBlock.Section>()
            .flatMap { it.lines }
            .mapNotNull { (it as? ChordProLine.Tab)?.label }

        assertEquals(listOf("Riff [H]"), labels.distinct())
    }

    @Test
    fun `a key spelled out in words is written in German notation`() {
        assertEquals("H major", ChordProNotation.toNotation(ChordProParser.parse("{key: B major}\n[B]a"), ChordNotation.GERMAN).metadata.key)
        assertEquals("B major", ChordProParser.parse("{key: H major}\n[H]a").metadata.key)
        assertEquals(ChordProParser.parse("{key: H major}\n[H]a").metadata.key, ChordProParser.summarize("{key: H major}\n[H]a").metadata.key)
    }

    @Test
    fun `a key spelled out in Hungarian is written in German notation`() {
        assertEquals("B-dúr", ChordProParser.summarize("{key: H-dúr}\n[H]a").metadata.key)
        assertEquals("B-dúr", ChordProParser.parse("{key: H-dúr}\n[H]a").metadata.key)
        assertEquals("{key: H-dúr}\n[H]a", ChordProNotation.convertText("{key: B-dúr}\n[B]a", ChordNotation.STANDARD, ChordNotation.GERMAN))
    }

    @Test
    fun `accidental signs are folded wherever they stand in a name`() {
        assertEquals("Bbm7b5/C#", ChordProNotation.withAsciiAccidentals("B♭m7♭5/C♯"))
    }

    @Test
    fun `a shown chord's accidentals are drawn as signs wherever they stand in it`() {
        assertEquals("B♭m7♭5/C♯", ChordProNotation.withAccidentalSigns("Bbm7b5/C#"))
        assertEquals("(F♯7(♭9))", ChordProNotation.withAccidentalSigns("(F#7(b9))"))
        assertEquals("Si♭7", ChordProNotation.withAccidentalSigns("Sib7"))
        assertEquals("H7/D♯", ChordProNotation.withAccidentalSigns("H7/D#"))
    }

    @Test
    fun `a shown step's accidentals are drawn as signs`() {
        assertEquals("♭7(7sus4)", ChordProNotation.withAccidentalSigns("b7(7sus4)"))
        assertEquals("♭VII", ChordProNotation.withAccidentalSigns("bVII"))
        assertEquals("5/♯4", ChordProNotation.withAccidentalSigns("5/#4"))
        assertEquals("1(7)(♭9)", ChordProNotation.withAccidentalSigns("1(7)(b9)"))
    }

    @Test
    fun `a lowercase bass note and a word that is no chord keep their letters`() {
        assertEquals("D/b", ChordProNotation.withAccidentalSigns("D/b"))
        assertEquals("E♭/b♭", ChordProNotation.withAccidentalSigns("Eb/bb"))
        assertEquals("N.C.", ChordProNotation.withAccidentalSigns("N.C."))
        assertEquals("Chorus #2", ChordProNotation.withAccidentalSigns("Chorus #2"))
    }

    @Test
    fun `a shown step with signs is still recognized as one`() {
        assertTrue(ChordProChordNames.isDisplayedChordName("♭7(7sus4)"))
        assertTrue(ChordProChordNames.isDisplayedChordName("♭VII"))
    }

    @Test
    fun `every chord a shown song draws gets its signs, and only those`() {
        val song = ChordProParser.parse(
            "{key: Bb}\n{define: Eb base-fret 1 frets x 1 1 3 4 3}\n{comment: Intro [Bb] [F#]}\n[Bb]Tab #1 [Eb]\n" +
                "{start_of_grid}\n| Bb . F# . |\n{end_of_grid}\n{start_of_tab}\nBb      F#\nEb|--1----2--|\n e|--1----2--|\n{end_of_tab}",
        )

        val shown = ChordProNotation.withAccidentalSigns(song)

        assertEquals("B♭", shown.metadata.key)
        assertEquals(listOf("E♭"), shown.metadata.definitions.map { it.name })
        val lines = shown.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        assertEquals(listOf("B♭", "E♭"), lines.filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } })
        assertEquals("Tab #1 ", lines.filterIsInstance<ChordProLine.Lyrics>().single().text)
        assertEquals("Intro [B♭] [F♯]", shown.blocks.filterIsInstance<ChordProBlock.Section>().single().label)
        assertEquals(listOf("B♭", "F♯"), lines.filterIsInstance<ChordProLine.Grid>().flatMap { it.tokens }.filterIsInstance<GridToken.Chord>().map { it.name })
        assertEquals(listOf("B♭      F♯", "E♭|--1----2--|", " e|--1----2--|"), lines.filterIsInstance<ChordProLine.Tab>().map { it.text })
    }

    @Test
    fun `a name written in German notation is read into the app's own`() {
        assertEquals("B", ChordProNotation.fromGerman("H"))
        assertEquals("Bm7", ChordProNotation.fromGerman("Hm7"))
        assertEquals("Bb", ChordProNotation.fromGerman("B"))
        assertEquals("Bb7", ChordProNotation.fromGerman("B7"))
        assertEquals("F/Bb", ChordProNotation.fromGerman("F/B"))
        assertEquals("Hello", ChordProNotation.fromGerman("Hello"))
    }

    @Test
    fun `a song says it is German notated with an H chord anywhere`() {
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[H7]la [B]la")))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("{key: B}\n[C/H]la")))
        assertFalse(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[Hello]la [B]la")))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("{sog}\n| C~H |\n{eog}")))
    }

    @Test
    fun `a lowercase h marks a song as German`() {
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[D]a [h]b")))
    }

    @Test
    fun `a lowercase bass note is read and written in German notation`() {
        assertEquals("C/h", ChordProNotation.toGerman("C/b"))
        assertEquals("C/b", ChordProNotation.toGerman("C/bb"))
        assertEquals("C/b", ChordProNotation.fromGerman("C/h"))
        assertEquals("C/bb", ChordProNotation.fromGerman("C/b"))
        assertTrue(ChordProNotation.isGermanName("C/h"))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[D]a [C/h]b")))
    }

    @Test
    fun `lowercase minors are read spelled out`() {
        assertEquals(listOf("B", "Bm", "Am", "Bbm"), ChordProParser.parse("[H]a [h]b [a]c [b]d").chordNames())
        assertEquals(listOf("F", "Dm"), ChordProParser.parse("[F]a [d]b").chordNames())
    }

    @Test
    fun `every chord of a grid cell is respelled`() {
        assertEquals(listOf("H~B"), ChordProNotation.toNotation(ChordProParser.parse("{sog}\n| B~Bb |\n{eog}"), ChordNotation.GERMAN).chordNames())
    }

    @Test
    fun `a German-notated song reads in the app's own notation`() {
        val song = ChordProParser.parse("{key: B}\n\n[B]a [H7]b [Bb]c")

        assertEquals("Bb", song.metadata.key)
        assertEquals(listOf("Bb", "B7", "Bb"), song.chordNames())
        assertEquals(listOf("B", "H7", "B"), ChordProNotation.toNotation(song, ChordNotation.GERMAN).chordNames())
    }

    @Test
    fun `B becomes H and Bb becomes B`() {
        assertEquals("H", ChordProNotation.toGerman("B"))
        assertEquals("B", ChordProNotation.toGerman("Bb"))
        assertEquals("B", ChordProNotation.toGerman("B♭"))
    }

    @Test
    fun `the quality and the extensions of the chord survive`() {
        assertEquals("Hm", ChordProNotation.toGerman("Bm"))
        assertEquals("Hmaj7", ChordProNotation.toGerman("Bmaj7"))
        assertEquals("Hm7b5", ChordProNotation.toGerman("Bm7b5"))
        assertEquals("Bsus4", ChordProNotation.toGerman("Bbsus4"))
        assertEquals("Bm7", ChordProNotation.toGerman("Bbm7"))
    }

    @Test
    fun `B sharp keeps its sign on the new letter`() {
        assertEquals("H#", ChordProNotation.toGerman("B#"))
    }

    @Test
    fun `the other six letters are left alone`() {
        listOf("C", "C#", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "A#", "Am7", "Gsus4").forEach { name ->
            assertEquals(name, ChordProNotation.toGerman(name))
        }
    }

    @Test
    fun `the bass note is rewritten by the same rule as the root`() {
        assertEquals("C/H", ChordProNotation.toGerman("C/B"))
        assertEquals("F/B", ChordProNotation.toGerman("F/Bb"))
        assertEquals("B/H", ChordProNotation.toGerman("Bb/B"))
        assertEquals("Hm7/A", ChordProNotation.toGerman("Bm7/A"))
    }

    @Test
    fun `a song already written in German notation stays as it is`() {
        assertEquals("H", ChordProNotation.toGerman("H"))
        assertEquals("Hm7", ChordProNotation.toGerman("Hm7"))
        assertEquals("B", ChordProNotation.toGerman("Hb")) // An unusual, but unambiguous, way of writing Bb.
    }

    @Test
    fun `words that are not chords are returned unchanged`() {
        assertEquals("N.C.", ChordProNotation.toGerman("N.C."))
        assertEquals("Bridge", ChordProNotation.toGerman("Bridge"))
        assertEquals("Break", ChordProNotation.toGerman("Break"))
        assertEquals("", ChordProNotation.toGerman(""))
    }

    @Test
    fun `the key the chords and the grid are rewritten but the annotations are not`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{key: Bb}\n\n[B]a [Bb]b [*hold B]c\n\n{sog}\n| B . | Bb . |\n{eog}"), ChordNotation.GERMAN)

        assertEquals("B", song.metadata.key)
        assertEquals(listOf("H", "B", "H", "B"), song.chordNames())
        assertEquals(listOf("hold B"), song.annotationNames())
    }

    @Test
    fun `the chords above a tab are rewritten and the tablature is not`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sot}\n  B     Bm\ne|--0--11-|\nB|--1--2--|\n{eot}"), ChordNotation.GERMAN)

        assertEquals(listOf("  H     Hm", "e|--0--11-|", "B|--1--2--|"), song.tabLines())
    }

    @Test
    fun `the names of a staff are converted with the chords above it, and back`() {
        val text = "{sot}\n  Bb    B\ne|--0--1--|\nB|--1--2--|\n{eot}"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals("{sot}\n  B     H\ne|--0--1--|\nH|--1--2--|\n{eot}", german)
        assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a staff renamed in Latin keeps its bar in one column, and the chords over their notes`() {
        val text = "{sot}\n  Am     E7\ne|-0-----0-|\nB|-1-----0-|\nG|-2-----1-|\nD|-2-----0-|\nA|-0-----2-|\nE|-------0-|\n{eot}"
        val latin = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.LATIN)

        assertEquals(
            "{sot}\n    Lam    Mi7\n mi|-0-----0-|\n Si|-1-----0-|\nSol|-2-----1-|\n Re|-2-----0-|\n La|-0-----2-|\n Mi|-------0-|\n{eot}",
            latin,
        )
        assertEquals(text, ChordProNotation.convertText(latin, ChordNotation.LATIN, ChordNotation.STANDARD))
    }

    @Test
    fun `every staff converted to a notation and back is returned as it was`() {
        val texts = listOf(
            // A guitar tuned a half step down, whose B flat is a character shorter in German.
            "{sot}\n   Am\nEb|--0--|\nBb|--1--|\nGb|--2--|\nDb|--2--|\nAb|--0--|\nEb|-----|\n{eot}",
            // Names set right in front of the bar, a staff without bars, and an indented one.
            "{sot}\n    C\n  e|--0--|\n  B|--1--|\nEb |--0--|\n{eot}",
            "{sot}\ne-----0-----\nB-----1-----\nG-----0-----\n{eot}",
            "{sot}\n      G\n    e|--3--|\n    B|--0--|\n    G|--0--|\n{eot}",
            // Two systems one under the other, the second named again, and strings named by numbers.
            "{sot}\nA|--0--|\nE|--2--|\nC|--2--|\nG|--2--|\n\nA|--3--|\nE|--0--|\nC|--0--|\nG|--0--|\n{eot}",
            "{sot}\n1|--0--|\n2|--1--|\n3|--0--|\n{eot}",
            // A right-aligned staff whose only long name gets shorter in German, under a chord row that starts at its
            // edge.
            "{sot}\nAm\nBb|--0--|\n F|--1--|\n C|--2--|\n{eot}",
        )
        texts.forEach { text ->
            listOf(ChordNotation.GERMAN, ChordNotation.LATIN).forEach { notation ->
                val converted = ChordProNotation.convertText(text, ChordNotation.STANDARD, notation)
                assertEquals(text, ChordProNotation.convertText(converted, notation, ChordNotation.STANDARD), "$notation: $converted")
            }
        }
    }

    @Test
    fun `a staff whose names read the same note in German is opened and saved with only its B strings renamed`() {
        val texts = listOf(
            "{sot}\ne|---0---|\nB|---1---|\nG♯|---0---|\nE|---2---|\nB|---3---|\nE|-------|\n{eot}",
            "{sot}\nE♭|--0--|\nB|--1--|\n{eot}",
            "{sot}\nmi|--0--|\nB|--1--|\nSol|--0--|\n{eot}",
            "{sot}\nb|--0--|\nG♯|--1--|\n{eot}",
            "{sot}\nEb|--0--|\nBb|--1--|\nGb|--2--|\n{eot}",
        )
        texts.forEach { text ->
            val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

            assertEquals(text.replace("\nB|", "\nH|").replace("\nb|", "\nh|"), german)
            assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD), german)
        }
    }

    @Test
    fun `a file's H string beside a B one is saved through German as two B strings, never a B flat`() {
        val text = "{sot}\nH|--0--|\nB|--1--|\n{eot}"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals("{sot}\nH|--0--|\nH|--1--|\n{eot}", german)
        assertEquals("{sot}\nB|--0--|\nB|--1--|\n{eot}", ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `strings named in the notation the field is typed in are saved in the standard one`() {
        val latin = "{sot}\n mi|--0--|\nSol|--1--|\n{eot}"
        val german = "{sot}\nB|--0--|\nH|--1--|\nh|--2--|\n{eot}"

        assertEquals("{sot}\ne|--0--|\nG|--1--|\n{eot}", ChordProNotation.convertText(latin, ChordNotation.LATIN, ChordNotation.STANDARD))
        assertEquals("{sot}\nBb|--0--|\n B|--1--|\n b|--2--|\n{eot}", ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `the names of a staff in a file are read as standard ones whatever its chords look like`() {
        val text = "{sot}\n  H\nB|--0--|\n{eot}"

        assertEquals("{sot}\n  B\nB|--0--|\n{eot}", ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.STANDARD))
        assertEquals(listOf("   B", "Bb|--0--|"), ChordProParser.parse("{sot}\n  H\nB|--0--|\n{eot}", ChordNotation.GERMAN).tabLines())
    }

    @Test
    fun `a staff indented with tab characters is not renamed`() {
        val text = "{sot}\n\te|--0--|\n\tB|--1--|\n{eot}"

        assertEquals(text, ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.LATIN))
    }

    @Test
    fun `a comment inside a grid does not hide the chords after it from the notation`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sog}\n| Am . |\n{c: x}\n| B . |\n{eog}"), ChordNotation.GERMAN)

        assertEquals(listOf("Am", "H"), song.chordNames())
    }

    @Test
    fun `a shorter chord name keeps the columns of a tab`() {
        // Bb is a character wider than the B it becomes, so the space it leaves behind is put back after it.
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sot}\n  Bb    Bb\ne|--0--3--|\n{eot}"), ChordNotation.GERMAN)

        assertEquals(listOf("  B     B", "e|--0--3--|"), song.tabLines())
    }

    @Test
    fun `a text typed in German notation is written in the standard one even without an H`() {
        // An F major chart: nothing in it says German, but the B its writer typed is a B flat all the same.
        assertEquals("{key: F}\n[F]a [Bb]b [C7]c", ChordProNotation.convertText("{key: F}\n[F]a [B]b [C7]c", ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a standard text is shown in German notation everywhere it names a chord`() {
        val text = "{key: Bb}\n[B]a [Bb7/F]b [*hold B]c\n{sog}\n| B . | Bb . |\n{eog}"

        assertEquals(
            "{key: B}\n[H]a [B7/F]b [*hold B]c\n{sog}\n| H . | B . |\n{eog}",
            ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
    }

    @Test
    fun `a standard text comes back from German notation as it was`() {
        val text = "{title: T}\n{key: Bm}\n\n[Bm]a [b]b [Bb]c [D/b]d [A#]e\n{sot}\n  Bb    B\ne|--0--3--|\n{eot}\n"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals("[Hm]a [h]b [B]c [D/h]d [A#]e", german.lines()[3])
        assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a standard text is returned as it is`() {
        val text = "{title: Hello}\n[Am]Hello [B]there [*Hm?]\n"

        assertEquals(text, ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.STANDARD))
    }

    @Test
    fun `a file with an H is brought into the standard notation`() {
        assertEquals(
            "[Bb]a [B7]b [b]c",
            ChordProNotation.convertText("[B]a [H7]b [h]c", ChordNotation.STANDARD, ChordNotation.STANDARD),
        )
        assertEquals("[Bb]a [C#]b", ChordProNotation.convertText("[B♭]a [C♯]b", ChordNotation.STANDARD, ChordNotation.STANDARD))
    }

    @Test
    fun `the chords a song defines are renamed with its chords`() {
        assertEquals(
            "{define: H frets 1 1 3 3 3 1}\n{key: H}\n[H]x [B]y",
            ChordProNotation.convertText("{define: B frets 1 1 3 3 3 1}\n{key: B}\n[B]x [Bb]y", ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
        assertEquals(
            "  {chord:  Bb7 base-fret 1}\n{define: B frets x 2 4 4 4 2}\n[B]x [Bb7]y",
            ChordProNotation.convertText("  {chord:  B7 base-fret 1}\n{define: H frets x 2 4 4 4 2}\n[H]x [B7]y", ChordNotation.GERMAN, ChordNotation.STANDARD),
        )
    }

    @Test
    fun `a definition that names no chord or is delegated is left alone`() {
        val text = "{define: Bridge frets 1 1 3 3 3 1}\n{start_of_textblock}\n{define: B frets 1 1 3 3 3 1}\n{end_of_textblock}\n[B]x"

        assertEquals(
            "{define: Bridge frets 1 1 3 3 3 1}\n{start_of_textblock}\n{define: B frets 1 1 3 3 3 1}\n{end_of_textblock}\n[H]x",
            ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
    }

    @Test
    fun `a text with definitions comes back from German notation as it was`() {
        val text = "{define: B frets x 2 4 4 4 2}\n{chord: Bb}\n[B]x [Bb]y"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a text parsed as German is German without an H`() {
        assertEquals(listOf("F", "Bb"), ChordProParser.parse("[F]a [B]b", ChordNotation.GERMAN).chordNames())
        assertEquals(listOf("F", "B"), ChordProParser.parse("[F]a [B]b").chordNames())
        assertEquals("Bb", ChordProParser.summarize("{key: B}\n[F]a", ChordNotation.GERMAN).metadata.key)
        assertEquals("Bb", ChordProSummaryCache(ChordNotation.GERMAN).summaryOf("{key: B}\n[F]a").metadata.key)
    }

    @Test
    fun `a typed chord is named with its root and bass in capitals in the standard notation`() {
        assertEquals("Cmaj7", ChordProNotation.canonicalName("Cmaj7", ChordNotation.STANDARD))
        assertEquals("Cm7", ChordProNotation.canonicalName("c7", ChordNotation.STANDARD))
        assertEquals("CmM7", ChordProNotation.canonicalName(" cM7 ", ChordNotation.STANDARD))
        assertEquals("F#m/E", ChordProNotation.canonicalName("(f♯/e)", ChordNotation.STANDARD))
        assertEquals("CM7", ChordProNotation.canonicalName("CM7", ChordNotation.STANDARD))
        assertNull(ChordProNotation.canonicalName("cmaj7", ChordNotation.STANDARD))
    }

    @Test
    fun `a typed chord is named in title case in the Latin notation`() {
        assertEquals("Lam", ChordProNotation.canonicalName("LAm", ChordNotation.LATIN))
        assertEquals("Do/Mi", ChordProNotation.canonicalName("DO/mi", ChordNotation.LATIN))
        assertEquals("Lam", ChordProNotation.canonicalName("a", ChordNotation.LATIN))
    }

    @Test
    fun `a typed chord is named in German notation as it is read there`() {
        assertEquals("Hm", ChordProNotation.canonicalName("h", ChordNotation.GERMAN))
        assertEquals("B7", ChordProNotation.canonicalName("B7", ChordNotation.GERMAN))
        assertEquals("G", ChordProNotation.canonicalName("G", ChordNotation.NASHVILLE))
    }

    @Test
    fun `a typed chord's standard name is the one a file stores, whatever notation it was typed in`() {
        assertEquals("Bb/D", ChordProNotation.standardName("B/D", ChordNotation.GERMAN))
        assertEquals("B7/F#", ChordProNotation.standardName("H7/F#", ChordNotation.GERMAN))
        assertEquals("D/F#", ChordProNotation.standardName("Re/Fa#", ChordNotation.LATIN))
        assertEquals("Am", ChordProNotation.standardName("a", ChordNotation.STANDARD))
        assertNull(ChordProNotation.standardName("xyz", ChordNotation.STANDARD))
    }

    private fun ChordProSong.lines() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }

    private fun ChordProSong.chordNames() = lines().flatMap { line ->
        when (line) {
            is ChordProLine.Lyrics -> line.chords.filter { !it.isAnnotation }.map { it.name }
            is ChordProLine.Grid -> line.tokens.filterIsInstance<GridToken.Chord>().map { it.name }
            else -> emptyList()
        }
    }

    private fun ChordProSong.tabLines() = lines().filterIsInstance<ChordProLine.Tab>().map { it.text }

    @Test
    fun `a chord written with markup is converted by its name and keeps its tags`() {
        val german = ChordProNotation.convertText("[<b>B</b>]a", ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals("[<b>H</b>]a", german)
        assertEquals("[<b>B</b>]a", ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    private fun ChordProSong.annotationNames() = lines()
        .filterIsInstance<ChordProLine.Lyrics>()
        .flatMap { line -> line.chords.filter { it.isAnnotation }.map { it.name } }
}
