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

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordSheetSiteDetailsTest {
    private fun convert(text: String) = ChordSheetConverter.convert(ChordSheet.ofPlainText(text)).single()

    @Test
    fun `a details block glued into one line becomes the header`() {
        val result = convert("Riverlight Chords by Someone\nTuning: E A D G B EKey: GCapo: No capo\n\n[Verse 1]\nG      C\nHello world")
        val metadata = ChordProParser.parse(result).metadata
        assertEquals("G", metadata.key)
        assertEquals(null, metadata.capo)
        assertFalse("Tuning" in result || "capo" in result.lowercase() || "{meta" in result, result)
        assertTrue("[G]Hello [C]world" in result, result)
    }

    @Test
    fun `a glued block keeps a capo a tempo and a non-standard tuning`() {
        val result = convert("Song\nSomeone\nDifficulty: novice\nTuning: Eb Ab Db Gb Bb EbKey: EbCapo: 2nd fretTempo: 96\n\nG      C\nHello world")
        assertTrue(result.contains("{meta: tuning Eb Ab Db Gb Bb Eb}"), result)
        assertTrue(result.contains("{key: Eb}") && result.contains("{capo: 2}") && result.contains("{tempo: 96}"), result)
        assertFalse("novice" in result || "Difficulty" in result, result)
    }

    @Test
    fun `details on lines of their own become the header in every language the app ships`() {
        val cases = listOf(
            "Tuning: E A D G B E\nKey: Em\nCapo: 2nd fret" to "{key: Em}\n{capo: 2}\n",
            "Afinação: Padrão\nTom: G\nCapotraste na 2ª casa" to "{key: G}\n{capo: 2}\n",
            "Afinación: Estándar\nTono: Sol\nCejilla: 3" to "{key: Sol}\n{capo: 3}\n",
            "Stimmung: Standard\nTonart: G\nKapodaster: 0" to "{key: G}\n",
            "Accordage: Standard\nTonalité: Sol" to "{key: Sol}\n",
            "Accordatura: Standard\nTonalità: Sol\nCapotasto: nessuno" to "{key: Sol}\n",
            "Hangolás: Normál\nHangnem: G\nCapo: nincs" to "{key: G}\n",
            "Acordaj: Standard\nTonalitate: G\nCapodastru: fără" to "{key: G}\n",
            "Strój: Standardowy\nTonacja: G\nKapodaster: brak" to "{key: G}\n",
            "Ladění: Standardní\nTónina: G\nKapodastr: 2" to "{key: G}\n{capo: 2}\n",
        )
        for ((lines, header) in cases) {
            val result = convert("$lines\n\nG      C\nHello world")
            assertTrue(result.startsWith(header), "$lines\n---\n$result")
            assertTrue(result.endsWith("[G]Hello [C]world\n") && !result.contains("{meta"), result)
        }
        // A capo whose value names no fret and no "none" stays the line of text an unreadable one is.
        assertTrue(convert("Capo: no way\n\nG      C\nHello world").contains("Capo: no way"))
    }

    @Test
    fun `a capo of none is written as nothing`() {
        for (line in listOf("Capo: No capo", "Capo: none", "Capo: 0", "Capo: -", "Capotraste: Sem capotraste", "Kapodaster: ohne", "Sem capotraste")) {
            assertEquals("[G]Hello [C]world\n", convert("$line\nG      C\nHello world"), line)
        }
    }

    @Test
    fun `a time signature and the credits of the app's languages become directives`() {
        val result = convert("Time signature: 6/8\nLetra y música: Someone\nComposição: Another\n\nG      C\nHello world")
        assertTrue(result.startsWith("{time: 6/8}\n{artist: Someone}\n{composer: Another}\n"), result)
        assertTrue(convert("Words and Music: Someone\n\nG      C\nHello world").startsWith("{artist: Someone}\n"))
    }

    @Test
    fun `a CCLI number is kept and the licence and terms are left out`() {
        val result = convert(
            "Verse 1\nG      C\nHello world\n\nCCLI Song # 1234567\n© 2010 Someone Music\n" +
                "For use solely with the SongSelect® Terms of Use. All rights reserved. www.ccli.com\nCCLI License # 7654321",
        )
        assertTrue(result.contains("{meta: ccli 1234567}") && result.contains("{copyright: 2010 Someone Music}"), result)
        assertFalse("SongSelect" in result || "7654321" in result, result)
    }

    @Test
    fun `a chord site's chrome around the song is left out`() {
        val text = "Riverlight Chords by Someone\n1,234,567 views, added to favorites 3,210 times\nAuthor: someone [a] 1,234. 2 contributors total, " +
            "last edit on Mar 19, 2024\nCheck out the tab »\nAutoscroll\n\n[Verse 1]\nG      C\nHello world\n\nLast update: Mar 19, 2024\nTranspose\n" +
            "Simplify\nReport bad tab"
        val result = convert(text)
        assertEquals("Riverlight", ChordProParser.parse(result).metadata.title, result)
        for (word in listOf("views", "contributors", "Check out", "Autoscroll", "Last update", "Transpose", "Simplify", "Report")) {
            assertFalse(word in result, "$word\n$result")
        }
    }

    @Test
    fun `glued counters and the save and interactive tab buttons over the song are left out`() {
        val result = convert("6,799 views201 saves\nSave\nInteractive tab\n\nG      C\nHello world\nSave\nG      C\nHello again")
        assertEquals("[G]Hello [C]world\nSave\n[G]Hello [C]again\n", result)
    }

    @Test
    fun `a list of the chords the song plays one a line is left out of the song`() {
        val song = "\n\n| Am Em | F C/H |\n\nAm      C\nHello world\nF       G\nHello again"
        val result = convert("Am\nC\n2fr\nF\nC/B\nG$song")
        assertTrue(result.startsWith("{start_of_grid}"), result)
        // Its label goes with it, in any language of the app.
        for (label in listOf("Chords in the song", "Akkordok a dalban", "Acordes:")) {
            assertTrue(convert("$label\nAm\nC\nF\nG$song").startsWith("{start_of_grid}"), label)
        }
        // A run naming a chord twice, one the song never plays, or one under a heading is the song's own.
        for (run in listOf("Am\nC\nAm", "Am\nC\nD", "Outro:\nAm\nC\nF")) {
            assertTrue(convert("$song\n\n$run").contains("[C]"), run)
            assertTrue(convert("$song\n\n$run").substringAfter("[G]").contains(run.lines().last()), run)
        }
    }

    @Test
    fun `an author's credit glued to the site's difficulty is the transcriber's and goes with it`() {
        assertEquals("[G]Hello [C]world\n", convert("Author: someone [pro] 52Difficulty: Intermediate\n\nG      C\nHello world"))
        // Beside a detail of the song's own, the credit is as likely the song's author, so the line stays.
        assertTrue(convert("Author: John Newton Key: G\n\nG      C\nHello world").contains("John Newton"))
    }

    @Test
    fun `lyrics that only contain a details word are never eaten`() {
        for (line in listOf("Key to my heart", "Speed of sound", "Tuning in to you", "Simplify my life", "Author of my story", "Difficulty is my name")) {
            val result = convert("Verse 1\nG      C\nHello world\n$line\nG      C\nHello again")
            assertTrue(result.contains(line), result)
        }
        // A word of the chrome sung between two lyric lines is a lyric, and so is a song's author named alone.
        assertTrue(convert("Verse 1\nG      C\nHello world\nTranspose\nG      C\nHello again").contains("Transpose"))
        assertTrue(convert("Author: Isaac Watts\n\nG      C\nHello world").contains("Author: Isaac Watts"))
    }

    @Test
    fun `glued labels are cut apart only where a line opens with a details label`() {
        assertEquals(listOf("Tuning: E A D G B E", "Key: G", "Capo: No capo"), ChordSheetSiteDetails.parts("Tuning: E A D G B EKey: GCapo: No capo"))
        assertEquals(listOf("Key: G", "Capo: 2"), ChordSheetSiteDetails.parts("Key: G Capo: 2"))
        assertEquals(listOf("Artist: Someone Key: G"), ChordSheetSiteDetails.parts("Artist: Someone Key: G"))
        assertEquals(listOf("The Key: unlocked"), ChordSheetSiteDetails.parts("The Key: unlocked"))
    }

    @Test
    fun `the standard tuning is told from another`() {
        for (value in listOf("E A D G B E", "EADGBE", "Standard", "Standard (E A D G B E)", "Padrão", "E A D G B e")) {
            assertTrue(ChordSheetSiteDetails.isStandardTuning(value), value)
        }
        for (value in listOf("Eb Ab Db Gb Bb Eb", "D A D G B E", "Drop D", "Half step down", "Open G")) {
            assertFalse(ChordSheetSiteDetails.isStandardTuning(value), value)
        }
    }
}
