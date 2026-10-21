/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongScaleSuggestionsTest {

    /** The suggestions for a song of one section per entry of [sections], labelled by their keys, under [key]. */
    private fun suggestions(vararg sections: Pair<String, String>, key: String? = null) = SongScaleSuggestions.of(
        ChordProParser.parse(
            (
                listOfNotNull(key?.let { "{key: $it}" }) + sections.map { (label, chords) ->
                    "{start_of_verse: $label}\n" + chords.split(' ').joinToString(" ") { "[$it]x" } + "\n{end_of_verse}"
                }
                ).joinToString("\n"),
        ),
    )

    private val ScaleGroup.labels get() = blocks.map { (it as ChordProBlock.Section).label }

    private val ScaleGroup.moments get() = exceptions.map { Triple(it.chordName, it.rootName, it.scales.first().id) }

    @Test
    fun `a dominant blues changes scale over the IV and the V, whose thirds the minor pentatonic leaves out`() {
        val blues = suggestions("Verse" to "E7 E7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7", key = "E").single()
        assertEquals("E" to "minor_pentatonic", blues.rootName to blues.scales.first().id)
        assertEquals(listOf(Triple("A7", "A", "mixolydian"), Triple("B7", "B", "mixolydian")), blues.moments)
    }

    @Test
    fun `a dominant the home scale holds the third of, and a rock riff, keep the home scale throughout`() {
        assertTrue(suggestions("Verse" to "C Am F G7 C Am F G7").single().exceptions.isEmpty())
        assertTrue(suggestions("Verse" to "E G A E G A E D").single().exceptions.isEmpty())
    }

    @Test
    fun `a chord played once in a long song is no moment`() {
        val verse = "C Am F G C Am F G C Am F G C Am F G"
        val song = suggestions("Verse" to verse, "Chorus" to "C F G Am F G E7 Am C", "Verse" to verse)
        assertTrue(song.single().exceptions.isEmpty())
    }

    @Test
    fun `a modulated solo is shown though its own exceptions, held at home, outrank it`() {
        val verse = "G F C D G F C D G F C D G F C D"
        val chorus = "G F C D G F C D G F C D Em A F G"
        val song = suggestions(
            "Verse" to verse,
            "Chorus" to chorus,
            "Verse" to verse,
            "Chorus" to chorus,
            "Solo" to "F C C D# G# D# D# C A# F F D D D D",
            "Verse" to verse,
            "Chorus" to chorus,
            key = "G",
        )
        assertEquals(listOf("G" to "major", "F" to "natural_minor"), song.map { it.rootName to it.scales.first().id })
        assertEquals(listOf("Solo"), song.last().labels)
    }

    @Test
    fun `a song gets at most three moments, however many keys it visits`() {
        val song = suggestions(
            "Verse" to "C F G C C F G C C F G C C F G C",
            "Bridge" to "D G A D",
            "Solo" to "E A B E",
            "Middle" to "Bb Eb F Bb",
            "Outro" to "Ab Db Eb Ab",
        )
        assertEquals(listOf("Verse"), song.first().labels)
        assertEquals(4, song.size)
    }

    @Test
    fun `a part in the relative minor needs no scale of its own, and its chords outside the key go with the home`() {
        val song = suggestions("Verse" to "C F G C", "Chorus" to "Am Dm E7 Am")
        assertEquals(listOf(listOf("Verse")), song.map { it.labels })
        assertEquals(listOf(Triple("E7", "A", "harmonic_minor")), song.single().moments)
    }

    @Test
    fun `a jazz standard is one home with its minor key's V first`() {
        val song = suggestions(
            "A" to "Am7 D7 Gmaj7 Cmaj7 F#m7b5 B7 Em Em",
            "B" to "F#m7b5 B7 Em Em Am7 D7 Gmaj7 Gmaj7 F#m7b5 B7 Em7 A7 Dm7 G7 F#m7b5 B7 Em",
            key = "Em",
        ).single()
        assertEquals(listOf("A", "B"), song.labels)
        assertEquals(Triple("B7", "E", "harmonic_minor"), song.moments.first())
        assertTrue(song.moments.size <= 3)
    }

    @Test
    fun `a borrowed flat seventh closing every chorus is a moment of the major key, not a rock riff`() {
        val song = suggestions(
            "Verse" to "A Asus2 G A C#m D Bm A D A Bm E",
            "Chorus" to "A D E E7 A A D E E7 A A D E E7 G",
            "Verse" to "A Asus2 G A C#m D Bm A D A Bm E",
            "Chorus" to "A D E E7 A A D E E7 A A D E E7 G",
            "Bridge" to "D D F#m Bm A G F#m D E F#m E",
            "Chorus" to "A D E E7 A A D E E7 A A D E E7 G",
            key = "A",
        )
        assertEquals("A" to "major", song.first().rootName to song.first().scales.first().id)
        assertEquals(listOf(Triple("G", "A", "mixolydian")), song.first().moments)
    }

    @Test
    fun `a bridge that hardly plays the home's root is a moment on its own centre, in the scale holding most of it`() {
        val song = suggestions(
            "Verse" to "A D E A A D E A A D E A",
            "Chorus" to "A E F#m D A E F#m D A E D A",
            "Bridge" to "D D F#m Bm A G F#m D E F#m E",
            "Chorus" to "A E F#m D A E F#m D A E D A",
            key = "A",
        )
        assertEquals(listOf(listOf("Verse", "Chorus", "Chorus"), listOf("Bridge")), song.map { it.labels })
        assertEquals("D" to listOf("major_pentatonic", "major"), song[1].rootName to song[1].scales.map { it.id })
    }

    @Test
    fun `a part that cadences into the home's root is no centre of its own`() {
        val hotelCalifornia = suggestions(
            "Intro" to "Bm F#7 A E G D Em F#",
            "Verse" to "Bm F#7 A E G D Em F#",
            "Chorus" to "G D F# Bm G D Em F#",
            "Verse" to "Bm F#7 A E G D Em F#",
            key = "Bm",
        )
        assertEquals(listOf(listOf("Intro", "Verse", "Chorus", "Verse")), hotelCalifornia.map { it.labels })
        assertTrue(hotelCalifornia.single().isHome)
        val song = suggestions(
            "Verse" to "A D E A A D E A A D E A",
            "Chorus" to "A E F#m D A E F#m D A E D A",
            "Bridge" to "D E F#m D E F#m D E A",
            "Chorus" to "A E F#m D A E F#m D A E D A",
            key = "A",
        ).single()
        assertEquals(listOf("Verse", "Chorus", "Bridge", "Chorus"), song.labels)
        assertEquals("A" to "major", song.rootName to song.scales.first().id)
    }

    @Test
    fun `a vamp on the IV that never resolves home stays a centre of its own`() {
        val inE = suggestions(
            "Verse" to "E B C#m A E B C#m A E B A B",
            "Chorus" to "A B E E A B E E",
            "Bridge" to "A A C#m C#m A A B B A A C#m C#m A A B B",
            "Chorus" to "A B E E A B E E",
            key = "E",
        )
        assertEquals(listOf(listOf("Verse", "Chorus", "Chorus"), listOf("Bridge")), inE.map { it.labels })
        assertEquals("A" to listOf("major_pentatonic", "major"), inE[1].rootName to inE[1].scales.map { it.id })
        val inG = suggestions("Verse" to "G D Em C G D Em C", "Chorus" to "C D G Em C D G G", "Bridge" to "C Am C Am C Am D D", key = "G")
        assertEquals(listOf(listOf("Verse", "Chorus"), listOf("Bridge")), inG.map { it.labels })
        assertEquals("C" to listOf("major_pentatonic", "major"), inG[1].rootName to inG[1].scales.map { it.id })
        val throughItsOwnDominant = suggestions(
            "Verse" to "C Am F G C Am F G",
            "Chorus" to "C F G C C F G C",
            "Bridge" to "F F Dm Dm F F G C7 F F Dm Dm F F G C7",
            "Chorus" to "C F G C C F G C",
            key = "C",
        )
        assertEquals(listOf(listOf("Verse", "Chorus", "Chorus"), listOf("Bridge")), throughItsOwnDominant.map { it.labels })
        assertEquals(
            "F" to listOf("major_pentatonic", "major"),
            throughItsOwnDominant[1].rootName to throughItsOwnDominant[1].scales.map { it.id },
        )
    }

    @Test
    fun `the key the song names is the home's root where its scale holds the part`() {
        val song = suggestions("Verse" to "C E7 Am F G C E7 Am", "Chorus" to "F G C Am F G C", key = "C").single()
        assertEquals("C" to listOf(Triple("E7", "A", "harmonic_minor")), song.rootName to song.moments)
    }

    @Test
    fun `a song in several keys with no home shows every group`() {
        val song = suggestions("Verse" to "C F G C", "Bridge" to "D G A D", "Solo" to "E A B E", "Outro" to "Bb Eb F Bb", "Coda" to "Ab Db Eb Ab")
        assertEquals(5, song.size)
        assertTrue(song.none { it.isHome })
    }

    @Test
    fun `a part both in another key and on a centre of its own is one moment, on its centre`() {
        val verse = "E E E E E C#m B E C#m B E C#m A B E C#m B E C#m A B"
        val chorus = "E G# F#m C#m B E G# F#m C#m B"
        val song = suggestions(
            "Verse" to verse,
            "Chorus" to chorus,
            "Bridge" to "A7 A6 A A7 A6 A E A E A A7 A6 A A7 A6 A",
            "Verse" to verse,
            "Chorus" to chorus,
            key = "E",
        )
        assertEquals(listOf("E" to true, "A" to false), song.map { it.rootName to it.isHome })
        assertEquals(listOf("major_pentatonic", "mixolydian"), song[1].scales.map { it.id })
        assertEquals(listOf(Triple("G#", "C#", "harmonic_minor")), song.first().moments)
    }

    @Test
    fun `chords one scale on one root serves are one moment, named by each`() {
        val verse = "Bm F#7 A E G D Em F#"
        val song = suggestions("Intro" to verse, "Verse" to verse, "Chorus" to "G D F#7 Bm G D Em F#", "Verse" to verse, key = "Bm").first()
        val overFifth = song.exceptions.single { it.rootName == "B" && it.scales.first().id == "harmonic_minor" }
        assertEquals(listOf("F#7", "F#"), listOf(overFifth.chordName) + overFifth.otherChordNames)
        assertEquals(1, song.exceptions.count { it.scales.first().id == "harmonic_minor" })
    }

    @Test
    fun `chords whose scales are one set of notes are one moment, named by the parent key`() {
        val inC = suggestions("Verse" to "C F#m7b5 D7 G7 C F#m7b5 D7 G7", key = "C").single()
        assertEquals(listOf(Triple("F#m7b5", "G", "major")), inC.moments)
        assertEquals(listOf("D7"), inC.exceptions.single().otherChordNames)
        val inF = suggestions("Verse" to "F Bm7b5 G7 C7 F Bm7b5 G7 C7", key = "F").single()
        assertEquals(listOf(Triple("Bm7b5", "C", "major")), inF.moments)
        assertEquals(listOf("G7"), inF.exceptions.single().otherChordNames)
    }

    @Test
    fun `chords the key's own scale answers keep its name when joined`() {
        val autumnLeaves = suggestions(
            "A" to "Cm7 F7 Bbmaj7 Ebmaj7 Am7b5 D7 Gm Gm",
            "B" to "Am7b5 D7 Gm Gm Cm7 F7 Bbmaj7 Bbmaj7 Am7b5 D7 Gm7 C7 Fm7 Bb7 Am7b5 D7 Gm",
            key = "Gm",
        ).single()
        val overFm7 = autumnLeaves.exceptions.single { it.chordName == "Fm7" }
        assertEquals(Triple("G", "phrygian", listOf("Bb7")), Triple(overFm7.rootName, overFm7.scales.first().id, overFm7.otherChordNames))
        val backdoor = suggestions("Verse" to "Cmaj7 Am7 Fm7 Bb7 Cmaj7 Am7 Fm7 Bb7", key = "C").single()
        assertEquals(listOf(Triple("Fm7", "C", "natural_minor")), backdoor.moments)
        assertEquals(listOf("Bb7"), backdoor.exceptions.single().otherChordNames)
    }

    @Test
    fun `a riff of power chords with a chorus of triads is one home, groups with one answer joined`() {
        val riff = "A5 C5 A5 D5 A5 C5 A5 A5 C5 A5 D5 A5 C5 A5"
        val song = suggestions(
            "Intro" to riff,
            "Verse" to "A5 A5 C5 A5 D5 A5 C5 A5 A5 C4 A5 D5 A5 C5",
            "Chorus" to "C5 A5 D5 A5 C5 F D A G A G A G G D G D G D",
            "Solo" to "$riff F D $riff",
            "Chorus" to "A5 C5 A5 D5 A5 C5 F D A G A G A G G D G D G D",
            "Outro" to "$riff A5 G5 A5 G5 A5",
            key = "A",
        )
        assertEquals(listOf("A" to "minor_pentatonic"), song.map { it.rootName to it.scales.first().id })
        assertTrue(song.single().isHome)
    }

    @Test
    fun `a riff of power chords is one home, though its parts would each fit a different mode`() {
        val song = suggestions(
            "Intro" to "E5 G5 E5 A5 E5 D5 E5 E5 G5 E5 A5 E5 D5 E5",
            "Verse" to "D5 E5 D5 E5 D5 E5 D5 E5 D5 E5 D5 E5 D5 E5",
            "Chorus" to "A5 A5 G5 A5 E5 E5 D5 E5 A5 A5 G5 A5 E5",
            "Verse" to "E5 D5 E5 D5 E5 D5 E5 D5 E5 D5 E5",
            "Chorus" to "A5 A5 G5 A5 E5 E5 D5 E5 A5 A5 G5 A5 E5",
            "Solo" to "B5 A5 B5 A5 B5 A5 B5 D5 E5 G5 E5 A5 E5 D5 E5",
            key = "E",
        )
        assertEquals(listOf("E" to "minor_pentatonic"), song.map { it.rootName to it.scales.first().id })
        assertTrue(song.single().exceptions.isEmpty())
    }

    @Test
    fun `a Mixolydian verse ending on the major V is one with the others, the V its moment`() {
        val verse = "A D G D G D G D G D A A A D G D G D G D G D A A"
        val ending = "A D G D G D G D G D A A A D G D G D G D G D E E"
        val song = suggestions(
            "Verse" to verse,
            "Verse" to ending,
            "Chorus" to "A D G D A D G D A D G D A D",
            "Verse" to verse,
            "Verse" to ending,
            "Chorus" to "A D G D A D G D A D G D A D",
            key = "A",
        )
        assertEquals(listOf("A" to "mixolydian"), song.map { it.rootName to it.scales.first().id })
        assertEquals(listOf("E"), song.single().exceptions.map { it.chordName })
    }

    @Test
    fun `the home is the group playing the most chords, shown first`() {
        val song = suggestions("Intro" to "D G A D", "Verse" to "C F G C C F G C")
        assertEquals(listOf(listOf("Verse"), listOf("Intro")), song.map { it.labels })
    }
}
