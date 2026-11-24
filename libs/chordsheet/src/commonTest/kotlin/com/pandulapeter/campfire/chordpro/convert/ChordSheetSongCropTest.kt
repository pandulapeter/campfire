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

import com.pandulapeter.campfire.pagesong.PageLineScores
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordSheetSongCropTest {

    @Test
    fun `the lines the model reads as clutter are left out of the song and its title stays`() {
        val lines = listOf(
            "Page 1 of 2" to junk,
            "Amazing Grace" to title,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "www.example.com/songs" to junk,
        )

        assertEquals(converted(lines.filter { it.second != junk }), converted(lines, cropped = true))
    }

    @Test
    fun `a line the model is a little unsure is clutter is kept and so is a title it is a little unsure of`() {
        val lines = listOf(
            "Amazing Grace" to scores(junk = 0.9f, title = 0.1f),
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "I once was lost" to scores(junk = 0.97f, song = 0.03f),
            "Footer" to junk,
        )

        assertEquals(converted(lines.dropLast(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a chord row in the song's body is kept even where the model reads it as clutter`() {
        val lines = listOf(
            "Amazing Grace" to title,
            "Verse" to song,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "That saved a wretch like me" to song,
            "Outro" to song,
            "G" to junk,
            "3" to junk,
        )

        assertEquals(converted(lines.dropLast(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a row of numerals in a song written in them is kept as a chord row is - a page number is not`() {
        val lines = listOf(
            "Gimme" to title,
            "Key: Dm" to song,
            "INTRO" to song,
            "i i#5 i4 i" to junk,
            "VERSE" to song,
            "i         VII    V" to junk,
            "Half-past twelve" to song,
            "III             IV" to junk,
            "And I'm watching the late show" to song,
            "2" to junk,
        )

        assertEquals(converted(lines.dropLast(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a chord diagram block under the song is left out with its chord names`() {
        val song = listOf(
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
        )
        val diagrams = listOf("Chords used", "G", "×", "3", "2", "o", "Am", "×", "o", "2", "D").map { it to junk }

        assertEquals(converted(song), converted(song + diagrams, cropped = true))
    }

    @Test
    fun `a chord row the model reads as clutter between two song lines is kept`() {
        val lines = listOf(
            "Verse" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to junk,
            "That saved a wretch like me" to song,
            "I once was lost but now am found" to song,
            "Footer" to junk,
        )

        assertEquals(converted(lines.dropLast(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a running header inside the song the model gives a small chance of being the title is left out`() {
        val lines = listOf(
            "Amazing Grace" to title,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "Amazing Grace - John Newton | Songs.example" to runningHeader,
            "C           G" to song,
            "I once was lost but now am found" to song,
        )

        assertEquals(converted(lines.filter { it.second != runningHeader }), converted(lines, cropped = true))
    }

    @Test
    fun `a line above the song the model gives a small chance of being the title is kept for the header`() {
        val lines = listOf(
            "Page 1" to junk,
            "Amazing Grace - John Newton" to runningHeader,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
        )

        assertEquals(converted(lines.drop(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a line labelled the title inside the song is kept though a surer one heads it`() {
        val lines = listOf(
            "Amazing Grace" to title,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "Amazing grace" to scores(junk = 0.1f, title = 0.9f),
            "C           G" to song,
            "I once was lost but now am found" to song,
            "Footer" to junk,
        )

        assertEquals(converted(lines.dropLast(1)), converted(lines, cropped = true))
    }

    @Test
    fun `a song the model sees no song in is converted whole`() {
        val lines = listOf(
            "Page 1" to junk,
            "Amazing Grace" to title,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "Footer" to junk,
        )

        assertEquals(converted(lines), converted(lines, cropped = true))
    }

    @Test
    fun `a song with a line past the lines the model read is converted whole`() {
        val lines = listOf(
            "Page 1" to junk,
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "Footer" to junk,
        )
        val sheet = ChordSheet(ChordSheet.ofPlainText(lines.joinToString("\n") { it.first }).pages)

        assertEquals(converted(lines), ChordSheetConverter.convert(sheet, pageScores = lines.dropLast(1).map { it.second }))
    }

    @Test
    fun `blank lines the model calls the song do not make a song of too few printed ones`() {
        val lines = listOf(
            "Page 1" to junk,
            "G           C        G" to song,
            "" to song,
            "Amazing grace, how sweet the sound" to song,
            "" to song,
            "That saved a wretch like me" to song,
            "Footer" to junk,
        )

        assertEquals(converted(lines), converted(lines, cropped = true))
    }

    @Test
    fun `no blank line after the song is kept for a small chance of being the title`() {
        val lines = listOf(
            "G           C        G" to song,
            "Amazing grace, how sweet the sound" to song,
            "G                D" to song,
            "That saved a wretch like me" to song,
            "" to song,
            "Footer" to junk,
            "" to junk,
            "" to scores(junk = 0.95f, title = 0.05f),
        )
        val song = lines.mapIndexed { index, (text, _) -> Rendered(text, DoubleArray(0), ChordSheet.Line(emptyList()), origin = index) }

        assertEquals(song.take(4), ChordSheetSongCrop.cropped(song, lines.map { it.second }))
    }

    private fun converted(lines: List<Pair<String, PageLineScores>>, cropped: Boolean = false): List<String> {
        val sheet = ChordSheet(ChordSheet.ofPlainText(lines.joinToString("\n") { it.first }).pages)
        return ChordSheetConverter.convert(sheet, pageScores = if (cropped) lines.map { it.second } else null)
    }

    private val junk = scores(junk = 1f)
    private val song = scores(song = 1f)
    private val title = scores(title = 1f)
    private val runningHeader = scores(junk = 0.95f, title = 0.03f, artist = 0.02f)

    private fun scores(junk: Float = 0f, song: Float = 0f, title: Float = 0f, artist: Float = 0f, metadata: Float = 0f) =
        PageLineScores(floatArrayOf(junk, song, title, artist, metadata))
}
