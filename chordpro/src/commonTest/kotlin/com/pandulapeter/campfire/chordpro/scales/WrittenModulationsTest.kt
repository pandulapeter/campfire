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
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class WrittenModulationsTest {

    private fun section(type: String, chords: String) = "{start_of_$type}\n" + chords.split(' ').joinToString(" ") { "[$it]x" } + "\n{end_of_$type}"

    private fun parse(vararg sections: String, key: String? = null) =
        ChordProTransposer.transpose(ChordProParser.parse((listOfNotNull(key?.let { "{key: $it}" }) + sections).joinToString("\n")), 0)

    /** What a song's Scales section shows, as a viewer reads it: each row's root, first scale, exceptions, and home. */
    private fun shown(vararg sections: String, key: String? = null) = SongScaleSuggestions.of(parse(*sections, key = key)).map { group ->
        listOf(group.rootName, group.scales.first().id, group.exceptions.joinToString { it.chordName }, group.isHome.toString())
    }

    private val chorus = section("chorus", "D Bm G A C G D Bm G A C G D")

    @Test
    fun `a last chorus written out a whole step up is read as the transpose it stands for, the outro with it`() {
        val written = shown(chorus, chorus, section("chorus", "E C#m A B D A E C#m A B D A E"), section("outro", "C#m A B D A E"), key = "D")
        val directed = shown(chorus, chorus, "{transpose: 2}", chorus, section("outro", "Bm G A C G D"), key = "D")
        assertEquals(listOf(listOf("D", "major", "C", "true"), listOf("E", "major", "D", "false")), written)
        assertEquals(directed, written)
    }

    @Test
    fun `a chorus written out a half step up with no key starts a part the outro stays in`() {
        val verse = section("verse", "G Em C D")
        val song = parse(verse, section("chorus", "C G D Em C D G"), verse, section("chorus", "Db Ab Eb Fm Db Eb Ab"), section("outro", "Ab Db Eb Ab"))
        val groups = SongScaleGroups.of(song)
        assertEquals(listOf("G", "Ab"), groups.map { it.rootName })
        assertEquals(song.blocks.takeLast(2), groups[1].blocks)
        assertEquals(
            listOf(null),
            WrittenModulations.of(parse(verse, section("chorus", "C G D Em"), section("chorus", "Db Ab Eb Fm")).blocks, null)
                .filterIsInstance<ChordProBlock.Transpose>().map { it.key },
        )
    }

    @Test
    fun `a chorus on the IV repeating the verse's progression is no key change`() {
        val verse = section("verse", "G Em C D")
        val song = parse(verse, section("chorus", "C Am F G"), verse, section("chorus", "C Am F G"), key = "G")
        assertEquals(song.blocks, WrittenModulations.of(song.blocks, SongKey(SpelledNote(7, "G"), isMinor = false)))
        val choruses = parse(verse, section("chorus", "G D Em C"), verse, section("chorus", "C G Am F"), key = "G")
        assertEquals(choruses.blocks, WrittenModulations.of(choruses.blocks, SongKey(SpelledNote(7, "G"), isMinor = false)))
    }

    @Test
    fun `a chorus copied in the home key again returns there, and what follows stays home`() {
        val home = section("chorus", "F G Em Am F G C")
        val song = parse(
            section("verse", "C Am Dm G"),
            home,
            section("chorus", "G A F#m Bm G A D"),
            section("bridge", "Bm G D A"),
            home,
            section("outro", "F G C"),
            key = "C",
        )
        val keys = WrittenModulations.of(song.blocks, SongKey(SpelledNote(0, "C"), isMinor = false)).filterIsInstance<ChordProBlock.Transpose>()
        assertEquals(listOf("D", "C"), keys.map { it.key })
        val groups = SongScaleSuggestions.of(song)
        assertEquals(listOf("C" to true, "D" to false), groups.map { it.rootName to it.isHome })
        assertEquals(song.blocks.subList(2, 4), groups[1].blocks)
    }
}
