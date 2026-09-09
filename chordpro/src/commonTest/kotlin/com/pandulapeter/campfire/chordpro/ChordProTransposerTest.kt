package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChordProTransposerTest {

    @Test
    fun `the root note and the bass note are both transposed`() {
        assertEquals("Bm7/A", ChordProTransposer.transposeChord("Am7/G", 2, preferFlats = false))
    }

    @Test
    fun `accidentals follow the preferred spelling`() {
        assertEquals("B", ChordProTransposer.transposeChord("Bb", 1, preferFlats = true))
        assertEquals("C", ChordProTransposer.transposeChord("C#", -1, preferFlats = false))
        assertEquals("Db", ChordProTransposer.transposeChord("C", 1, preferFlats = true))
        assertEquals("C#", ChordProTransposer.transposeChord("C", 1, preferFlats = false))
    }

    @Test
    fun `german notation is understood`() {
        assertEquals("C", ChordProTransposer.transposeChord("H", 1, preferFlats = false))
    }

    @Test
    fun `names that are not chords are returned unchanged`() {
        assertEquals("N.C.", ChordProTransposer.transposeChord("N.C.", 2, preferFlats = false))
    }

    @Test
    fun `flats are preferred when the key transposes into a flat key`() {
        val song = ChordProParser.parse("{key: E}\n\n[E]a [A]b")

        assertTrue(ChordProTransposer.prefersFlats(song, 1)) // E -> F
        assertFalse(ChordProTransposer.prefersFlats(song, 3)) // E -> G
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("{key: Am}\n\n[Am]a"), 3)) // Am -> Cm
    }

    @Test
    fun `without a key the accidentals of the chords decide`() {
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("[Bb]a [Eb]b [C]c"), 1))
        assertFalse(ChordProTransposer.prefersFlats(ChordProParser.parse("[F#]a [C#]b [C]c"), 1))
    }

    @Test
    fun `transposing by zero returns the input untouched`() {
        val song = ChordProParser.parse("{key: E}\n\n[E]a")
        val text = "{key: E}\n\n[E]a"

        assertSame(song, ChordProTransposer.transpose(song, 0))
        assertSame(text, ChordProTransposer.transposeText(text, 0))
    }

    @Test
    fun `transposing by an octave keeps every chord name`() {
        val song = ChordProParser.parse("{key: Am}\n\n[Am]a [F]b [C]c [G]d\n\n{sog}\n| Am . | G . |\n{eog}")

        assertEquals(song.chordNames(), ChordProTransposer.transpose(song, 12).chordNames())
    }

    @Test
    fun `transposing the model moves chords grid chords and the key but not annotations`() {
        val song = ChordProTransposer.transpose(ChordProParser.parse("{key: Am}\n\n[Am]a [*hold]b\n\n{sog}\n| Am . |\n{eog}"), 2)

        assertEquals("Bm", song.metadata.key)
        assertEquals(listOf("Bm", "Bm"), song.chordNames())
        assertEquals(listOf("hold"), song.annotationNames())
    }

    @Test
    fun `transposing text leaves comments tabs and annotations alone and updates the key`() {
        val text = """
            # a note with [Am] inside
            {key: E}
            {comment: Not a chord}

            {start_of_tab}
            e|--[Am]--|
            {end_of_tab}

            [C]one [*hold]two

            {start_of_grid}
            | C . . . | (twice)
            {end_of_grid}
        """.trimIndent()

        assertEquals(
            """
            # a note with [Am] inside
            {key: F}
            {comment: Not a chord}

            {start_of_tab}
            e|--[Am]--|
            {end_of_tab}

            [Db]one [*hold]two

            {start_of_grid}
            | Db . . . | (twice)
            {end_of_grid}
            """.trimIndent(),
            ChordProTransposer.transposeText(text, 1)
        )
    }

    private fun ChordProSong.lines() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }

    private fun ChordProSong.chordNames() = lines().flatMap { line ->
        when (line) {
            is ChordProLine.Lyrics -> line.chords.filter { !it.isAnnotation }.map { it.name }
            is ChordProLine.Grid -> line.tokens.filterIsInstance<GridToken.Chord>().map { it.name }
            else -> emptyList()
        }
    }

    private fun ChordProSong.annotationNames() = lines()
        .filterIsInstance<ChordProLine.Lyrics>()
        .flatMap { line -> line.chords.filter { it.isAnnotation }.map { it.name } }
}
