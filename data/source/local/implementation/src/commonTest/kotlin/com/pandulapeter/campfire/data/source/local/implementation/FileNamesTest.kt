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

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FileNamesTest {

    @Test
    fun `names are normalized`() {
        assertEquals("summer_set_2026.setlist.json", setlistFileName("Summer Set 2026"))
        assertEquals("arvizturo_tukorfurogep.setlist.json", setlistFileName("Árvíztűrő tükörfúrógép"))
        assertEquals("nyari_lista.setlist.json", setlistFileName("  Nyári   lista!  "))
        // The artist and the title are normalized one at a time, so the dash between them survives as structure.
        assertEquals("tukorfurogep-arviz.cho", songFileName(title = "Árvíz", artist = "Tükörfúrógép"))
        assertEquals("arviz.cho", songFileName(title = "Árvíz", artist = ""))
        assertEquals("кино-группа_крови.cho", songFileName(title = "Группа крови", artist = "Кино"))
        // A song is stored under whichever extension of the family it arrived with, and renaming it is no reason to
        // claim its contents are written differently than they are.
        assertEquals("tukorfurogep-arviz.crd", songFileName(title = "Árvíz", artist = "Tükörfúrógép", extension = ".crd"))
    }

    @Test
    fun `the same song written down two ways arrives at one name`() {
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
    fun `a decomposed accent folds like a composed one`() {
        assertEquals("edith", LibraryFiles.normalizedName("\u00C9dith"))
        assertEquals("edith", LibraryFiles.normalizedName("E\u0301dith"))
        assertEquals("arvizturo-tukorfurogep.cho", songFileName(title = "tu\u0308ko\u0308rfu\u0301ro\u0301ge\u0301p", artist = "A\u0301rvi\u0301ztu\u030Bro\u030B"))
    }

    @Test
    fun `structure and digits survive the folding`() {
        assertEquals("ac_dc-t_n_t.cho", songFileName(title = "T.N.T.", artist = "AC/DC"))
        assertEquals("blink_182-all_the_small_things.cho", songFileName(title = "All the Small Things", artist = "blink-182"))
        assertEquals("village_people-y_m_c_a.cho", songFileName(title = "Y.M.C.A.", artist = "Village People"))
        // A leading article is part of the name rather than noise to be dropped.
        assertEquals("the_beatles-let_it_be.cho", songFileName(title = "Let It Be", artist = "The Beatles"))
    }

    @Test
    fun `a name the app gave is recognized as its own`() {
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
    fun `a name that differs only in case is already named`() {
        assertTrue("Hallelujah.cho".isNamed("hallelujah.cho"))
        assertTrue("Hallelujah_2.cho".isNamed("hallelujah.cho"))
        assertTrue("Summer.setlist.json".isNamed(setlistFileName("Summer")))
        assertFalse("Hallelujah2.cho".isNamed("hallelujah.cho"))
    }

    @Test
    fun `a name that differs in case and form is already named`() {
        // A capital Epsilon with a separate accent, as a Mac hands it out, against the composed lowercase name.
        assertTrue("\u0395\u0301\u03bd\u03b1.cho".isNamed("\u03ad\u03bd\u03b1.cho"))
    }

    @Test
    fun `a rename to the same name in another case and form is not numbered`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = true)
        storage.writeText(StorageDirectory.SONGS, DECOMPOSED_CAPITAL, "{title: \u0388\u03bd\u03b1}")

        assertEquals(COMPOSED_LOWERCASE, storage.uniqueName(StorageDirectory.SONGS, COMPOSED_LOWERCASE, currentName = DECOMPOSED_CAPITAL))
    }

    @Test
    fun `a move to the same name in another case and form keeps the file`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = true)
        storage.writeText(StorageDirectory.SONGS, DECOMPOSED_CAPITAL, "{title: \u0388\u03bd\u03b1}")

        storage.moveFile(StorageDirectory.SONGS, currentName = DECOMPOSED_CAPITAL, newName = COMPOSED_LOWERCASE) {
            storage.writeText(StorageDirectory.SONGS, it, "{title: \u0388\u03bd\u03b1}")
        }

        assertEquals("{title: \u0388\u03bd\u03b1}", storage.readText(StorageDirectory.SONGS, COMPOSED_LOWERCASE))
        assertEquals(listOf(COMPOSED_LOWERCASE), storage.listNames(StorageDirectory.SONGS))
    }

    @Test
    fun `a different file under the other form is still a collision`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = false)
        storage.writeText(StorageDirectory.SONGS, DECOMPOSED_CAPITAL, "{title: \u0388\u03bd\u03b1}")
        storage.writeText(StorageDirectory.SONGS, COMPOSED_LOWERCASE, "{title: Something else}")

        assertEquals(
            "\u03ad\u03bd\u03b1_2.cho",
            storage.uniqueName(StorageDirectory.SONGS, COMPOSED_LOWERCASE, currentName = DECOMPOSED_CAPITAL),
        )
    }

    @Test
    fun `a family numbered far is listed once rather than probed number by number`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = false)
        storage.writeText(StorageDirectory.SONGS, "a.cho", "")
        (2..50).forEach { storage.writeText(StorageDirectory.SONGS, "a_$it.cho", "") }

        assertEquals("a_51.cho", storage.uniqueName(StorageDirectory.SONGS, "a.cho"))
        assertTrue(storage.existsCalls <= 3, "${storage.existsCalls} exists calls")
        assertEquals(1, storage.listCalls)
    }

    @Test
    fun `a single collision is numbered without a listing`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = false)
        storage.writeText(StorageDirectory.SONGS, "a.cho", "")

        assertEquals("a_2.cho", storage.uniqueName(StorageDirectory.SONGS, "a.cho"))
        assertEquals(2, storage.existsCalls)
        assertEquals(0, storage.listCalls)
    }

    @Test
    fun `a number listed in another case is still taken on a folding file system`() = runTest {
        val storage = InMemoryFileStorage(foldsNames = true)
        storage.writeText(StorageDirectory.SONGS, "A.cho", "")
        storage.writeText(StorageDirectory.SONGS, "A_2.cho", "")
        storage.writeText(StorageDirectory.SONGS, "A_3.cho", "")

        assertEquals("a_4.cho", storage.uniqueName(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `a conflict copy is recognized as its own`() {
        assertTrue("x (2).cho".isNamed("x.cho"))
        assertTrue(!"_2.cho".isNamed(".cho"))
    }

    @Test
    fun `setlist name is always openable and capped`() {
        assertEquals("летний_сет.setlist.json", setlistFileName("Летний сет"))
        assertEquals("untitled.setlist.json", setlistFileName("!!!"))
        val base = setlistFileName("Lorem ipsum ".repeat(40)).removeSuffix(SETLIST_EXTENSION)
        assertTrue(base.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES)
        // Cut between words, so neither half a word nor the separator before the next one is left at the end.
        assertTrue(!base.endsWith(LibraryFiles.NAME_SEPARATOR))
        assertTrue(base.endsWith("ipsum"))
    }

    @Test
    fun `collisions are numbered without leaving the alphabet of the name they join`() {
        assertEquals("summer_set_2", "summer_set" + normalizedCollisionSuffix(2))
        assertEquals("tukorfurogep-arviz_2", "tukorfurogep-arviz" + normalizedCollisionSuffix(2))
        // A file sync brings down under the name another device gave it was never built out of underscores.
        assertEquals("Whatever They Called It (2)", "Whatever They Called It" + arrivingCollisionSuffix(2))
    }

    /**
     * Just enough of a [FileStorage] for [uniqueName] and [moveFile]. With [foldsNames] it behaves like APFS: a file
     * keeps the spelling it was first written with, and every question about a name is answered by its folded form,
     * so that two spellings of one name are one file. Without it every spelling is a file of its own, as on ext4.
     */
    private class InMemoryFileStorage(private val foldsNames: Boolean) : FileStorage {

        private val files = mutableMapOf<String, Pair<String, String>>()
        var existsCalls = 0
        var listCalls = 0

        private fun key(name: String) = if (foldsNames) name.normalizedToNfc().lowercase() else name

        override suspend fun list(directory: StorageDirectory): List<StoredFileInfo> = TODO()

        override suspend fun listNames(directory: StorageDirectory) = files.values.map { it.first }.also { listCalls++ }

        override suspend fun info(directory: StorageDirectory, name: String): StoredFileInfo? = TODO()

        override suspend fun exists(directory: StorageDirectory, name: String) = (key(name) in files).also { existsCalls++ }

        override suspend fun readText(directory: StorageDirectory, name: String) = files[key(name)]?.second

        override suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray? = TODO()

        override suspend fun writeText(directory: StorageDirectory, name: String, text: String) {
            files[key(name)] = (files[key(name)]?.first ?: name) to text
        }

        override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = TODO()

        override suspend fun delete(directory: StorageDirectory, name: String) {
            files -= key(name)
        }
    }

    private companion object {

        /** `Ένα.cho` the way macOS hands it out: a capital Epsilon followed by a separate accent. */
        const val DECOMPOSED_CAPITAL = "\u0395\u0301\u03bd\u03b1.cho"

        /** `ένα.cho`, the name the library would give the song. */
        const val COMPOSED_LOWERCASE = "\u03ad\u03bd\u03b1.cho"
    }
}
