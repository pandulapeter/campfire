/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FileNamesTest {

    @Test
    fun namesAreNormalized() {
        assertEquals("summer_set_2026.setlist.json", setlistFileName("Summer Set 2026"))
        assertEquals("arvizturo_tukorfurogep.setlist.json", setlistFileName("Árvíztűrő tükörfúrógép"))
        assertEquals("nyari_lista.setlist.json", setlistFileName("  Nyári   lista!  "))
        // The artist and the title are normalized one at a time, so the dash between them survives as structure.
        assertEquals("tukorfurogep-arviz.cho", songFileName(title = "Árvíz", artist = "Tükörfúrógép"))
        assertEquals("arviz.cho", songFileName(title = "Árvíz", artist = ""))
        // A song is stored under whichever extension of the family it arrived with, and renaming it is no reason to
        // claim its contents are written differently than they are.
        assertEquals("tukorfurogep-arviz.crd", songFileName(title = "Árvíz", artist = "Tükörfúrógép", extension = ".crd"))
    }

    @Test
    fun theSameSongWrittenDownTwoWaysArrivesAtOneName() {
        // An apostrophe binds rather than separates, in either of the spellings a keyboard produces.
        assertEquals("guns_n_roses-dont_cry.cho", songFileName(title = "Don't Cry", artist = "Guns N' Roses"))
        assertEquals("guns_n_roses-dont_cry.cho", songFileName(title = "Don’t Cry", artist = "Guns N’ Roses"))
        // Both signs a title writes "and" with are spelled out, so neither files a second copy of the same song.
        assertEquals("rock_and_roll.cho", songFileName(title = "Rock & Roll", artist = ""))
        assertEquals("rock_and_roll.cho", songFileName(title = "Rock+Roll", artist = ""))
        assertEquals("rock_and_roll.cho", songFileName(title = "Rock and Roll", artist = ""))
        // A credit is filed one way however it was abbreviated.
        assertEquals("jay_z_ft_alicia_keys-empire_state_of_mind.cho", songFileName(title = "Empire State of Mind", artist = "Jay-Z feat. Alicia Keys"))
        assertEquals("jay_z_ft_alicia_keys-empire_state_of_mind.cho", songFileName(title = "Empire State of Mind", artist = "Jay-Z featuring Alicia Keys"))
    }

    @Test
    fun aNormalizedNameSurvivesBeingNormalizedAgain() {
        // An exported name is normalized again on its way back in, so every rule here has to leave its own output
        // alone - the spelled out "and" and the abbreviated "ft" included.
        listOf("rock_and_roll", "jay_z_ft_alicia_keys", "dont_cry", "blink_182", "ac_dc", "y_m_c_a", "the_beatles").forEach { name ->
            assertEquals(name, LibraryFiles.normalizedName(name))
        }
    }

    @Test
    fun aCappedNameSurvivesBeingNormalizedAgain() {
        // Cut by characters, the first pass would end on "feat", which the second pass files as "ft".
        val feather = "a".repeat(115) + " feather"
        val long = "Árvíztűrő tükörfúrógép és a hosszú cím ".repeat(8)
        val singleWord = "b".repeat(300)
        listOf(feather, long, singleWord, *DECOMPOSED_NAMES.map { it.first }.toTypedArray()).forEach { base ->
            val once = LibraryFiles.normalizedName(base)
            assertTrue(once.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES, once)
            assertEquals(once, LibraryFiles.normalizedName(once))
        }
        assertEquals("a".repeat(115), LibraryFiles.normalizedName(feather))
        assertEquals("b".repeat(LibraryFiles.MAX_NAME_BYTES), LibraryFiles.normalizedName(singleWord))
    }

    @Test
    fun aDecomposedAccentFoldsLikeAComposedOne() {
        assertEquals("edith", LibraryFiles.normalizedName("\u00C9dith"))
        assertEquals("edith", LibraryFiles.normalizedName("E\u0301dith"))
        assertEquals("arvizturo-tukorfurogep.cho", songFileName(title = "tu\u0308ko\u0308rfu\u0301ro\u0301ge\u0301p", artist = "A\u0301rvi\u0301ztu\u030Bro\u030B"))
    }

    @Test
    fun aDecomposedNameInAnyScriptFoldsLikeItsComposedTwin() {
        DECOMPOSED_NAMES.forEach { (decomposed, composed) ->
            assertEquals(LibraryFiles.normalizedName(composed), LibraryFiles.normalizedName(decomposed), decomposed)
        }
        assertEquals("\u043C\u0430\u0439", LibraryFiles.normalizedName("\u041C\u0430\u0438\u0306"))
        assertEquals("\u03B5\u03BB\u03BB\u03AC\u03B4\u03B1_\u03BC\u03BF\u03C5", LibraryFiles.normalizedName("\u0395\u03BB\u03BB\u03B1\u0301\u03B4\u03B1 \u03BC\u03BF\u03C5"))
        assertEquals("viet", LibraryFiles.normalizedName("Vie\u0323\u0302t"))
    }

    @Test
    fun aNameWithNoComposedFormIsLeftAlone() {
        assertEquals("\u0939\u093F\u0928\u094D\u0926\u0940", LibraryFiles.normalizedName("\u0939\u093F\u0928\u094D\u0926\u0940"))
    }

    @Test
    fun aDecomposedNameIsCappedWhereItsComposedTwinIs() {
        assertEquals("\u0439".repeat(60), LibraryFiles.normalizedName("\u0438\u0306".repeat(100)))
        assertEquals(LibraryFiles.normalizedName("\u0439".repeat(100)), LibraryFiles.normalizedName("\u0438\u0306".repeat(100)))
    }

    @Test
    fun structureAndDigitsSurviveTheFolding() {
        assertEquals("ac_dc-t_n_t.cho", songFileName(title = "T.N.T.", artist = "AC/DC"))
        assertEquals("blink_182-all_the_small_things.cho", songFileName(title = "All the Small Things", artist = "blink-182"))
        assertEquals("village_people-y_m_c_a.cho", songFileName(title = "Y.M.C.A.", artist = "Village People"))
        // A leading article is part of the name rather than noise to be dropped.
        assertEquals("the_beatles-let_it_be.cho", songFileName(title = "Let It Be", artist = "The Beatles"))
    }

    @Test
    fun aNameTheAppGaveIsRecognizedAsItsOwn() {
        val desired = songFileName(title = "Árvíz", artist = "Tükörfúrógép")
        assertTrue("tukorfurogep-arviz.cho".isNamed(desired))
        // Already as close to the derived name as a file that had to make way for another one can get, so offering
        // to rename it again would be an offer that never goes away.
        assertTrue("tukorfurogep-arviz_2.cho".isNamed(desired))
        assertTrue(!"tukorfurogep-arvizek.cho".isNamed(desired))
        assertTrue(!"valaki_mas-arviz.cho".isNamed(desired))
        // A different extension is a different file, whatever the name in front of it says.
        assertTrue(!"tukorfurogep-arviz.crd".isNamed(desired))
        assertTrue("summer_set_2.setlist.json".isNamed(setlistFileName("Summer Set")))
    }

    @Test
    fun aNameThatDiffersOnlyInCaseIsAlreadyNamed() {
        assertTrue("Hallelujah.cho".isNamed("hallelujah.cho"))
        assertTrue("Hallelujah_2.cho".isNamed("hallelujah.cho"))
        assertTrue("Summer.setlist.json".isNamed(setlistFileName("Summer")))
        assertFalse("Hallelujah2.cho".isNamed("hallelujah.cho"))
    }

    @Test
    fun aNameThatDiffersInCaseAndFormIsAlreadyNamed() {
        // A capital Epsilon with a separate accent, as a Mac hands it out, against the composed lowercase name.
        assertTrue("\u0395\u0301\u03bd\u03b1.cho".isNamed("\u03ad\u03bd\u03b1.cho"))
    }

    @Test
    fun aConflictCopyIsRecognizedAsItsOwn() {
        assertTrue("x (2).cho".isNamed("x.cho"))
        assertTrue(!"_2.cho".isNamed(".cho"))
    }

    @Test
    fun setlistNameIsAlwaysOpenableAndCapped() {
        assertEquals("летний_сет.setlist.json", setlistFileName("Летний сет"))
        assertEquals("untitled.setlist.json", setlistFileName("!!!"))
        val base = setlistFileName("Lorem ipsum ".repeat(40)).removeSuffix(SETLIST_EXTENSION)
        assertTrue(base.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES)
        // Cut between words, so neither half a word nor the separator before the next one is left at the end.
        assertTrue(!base.endsWith(LibraryFiles.NAME_SEPARATOR))
        assertTrue(base.endsWith("ipsum"))
    }

    @Test
    fun collisionsAreNumberedWithoutLeavingTheAlphabetOfTheNameTheyJoin() {
        assertEquals("summer_set_2", "summer_set" + normalizedCollisionSuffix(2))
        assertEquals("tukorfurogep-arviz_2", "tukorfurogep-arviz" + normalizedCollisionSuffix(2))
        // A file sync brings down under the name another device gave it was never built out of underscores.
        assertEquals("Whatever They Called It (2)", "Whatever They Called It" + arrivingCollisionSuffix(2))
    }

    @Test
    fun lettersOfOtherScriptsAreKept() {
        assertEquals("катюша", LibraryFiles.normalizedName("Катюша"))
        assertEquals("ελλάδα_μου", LibraryFiles.normalizedName("Ελλάδα μου"))
        assertEquals("שלום_עולם", LibraryFiles.normalizedName("שלום עולם"))
        assertEquals("مرحبا_بالعالم", LibraryFiles.normalizedName("مرحبا بالعالم"))
        assertEquals("千と千尋の神隠し", LibraryFiles.normalizedName("千と千尋の神隠し"))
        assertEquals("हिन्दी_गीत", LibraryFiles.normalizedName("हिन्दी गीत"))
        assertEquals("кино_ft_цой", LibraryFiles.normalizedName("Кино feat. Цой"))
        assertEquals("кино-группа_крови.cho", songFileName(title = "Группа крови", artist = "Кино"))
    }

    @Test
    fun theCapCountsUtf8Bytes() {
        assertEquals("я".repeat(60), LibraryFiles.normalizedName("я".repeat(300)))
        assertEquals("千".repeat(40), LibraryFiles.normalizedName("千".repeat(100)))
    }

    @Test
    fun hiddenFilesAreNotLibraryFiles() {
        assertTrue(LibraryFiles.isSongFileName("a.cho"))
        assertTrue(LibraryFiles.isSongFileName("A.CHO"))
        assertTrue(LibraryFiles.isSongFileName("a.crd"))
        assertFalse(LibraryFiles.isSongFileName("._a.cho"))
        assertFalse(LibraryFiles.isSongFileName(".cho"))
        assertFalse(LibraryFiles.isSongFileName(".DS_Store"))
        assertFalse(LibraryFiles.isSongFileName("a.txt"))
        assertTrue(LibraryFiles.isSetlistFileName("s.setlist.json"))
        assertFalse(LibraryFiles.isSetlistFileName("._s.setlist.json"))
        assertFalse(LibraryFiles.isSetlistFileName("s.json"))
        assertFalse(LibraryFileKind.SONG.matches("._a.cho"))
        assertTrue(LibraryFileKind.SETLIST.matches("s.setlist.json"))
    }

    @Test
    fun everyLatinLetterFoldsToItsBaseLetter() {
        assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Gęsi za wodą"))
        // The same title decomposed, which is how macOS and some tools store it, arrives at the same name.
        assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Ge\u0328si za woda\u0328"))
        assertEquals("isik", LibraryFiles.normalizedName("Işık"))
        assertEquals("istanbul", LibraryFiles.normalizedName("İstanbul"))
        // The cedilla spelling of the Romanian letters is at least as common as the comma below.
        assertEquals("sarki", LibraryFiles.normalizedName("Şarkı"))
        assertEquals("tara", LibraryFiles.normalizedName("Ţara"))
        assertEquals("viet_nam", LibraryFiles.normalizedName("Việt Nam"))
        assertEquals("dorde", LibraryFiles.normalizedName("Đorđe"))
        assertEquals("thu", LibraryFiles.normalizedName("Þú"))
        assertEquals("ijsselmeer", LibraryFiles.normalizedName("Ĳsselmeer"))
        listOf("gesi_za_woda", "isik", "viet_nam", "thu").forEach { assertEquals(it, LibraryFiles.normalizedName(it)) }
    }

    private companion object {

        /** One name per script in both of its forms, written as escapes so that the source file's own encoding decides nothing. */
        val DECOMPOSED_NAMES = listOf(
            "\u041C\u0430\u0438\u0306" to "\u041C\u0430\u0439",
            "\u0395\u03BB\u03BB\u03B1\u0301\u03B4\u03B1" to "\u0395\u03BB\u03BB\u03AC\u03B4\u03B1",
            "\u05E9\u05C1\u05B8\u05DC\u05D5\u05B9\u05DD" to "\u05E9\u05B8\u05C1\u05DC\u05D5\u05B9\u05DD",
            "Vie\u0323\u0302t Nam" to "Vi\u1EC7t Nam",
        )
    }
}
