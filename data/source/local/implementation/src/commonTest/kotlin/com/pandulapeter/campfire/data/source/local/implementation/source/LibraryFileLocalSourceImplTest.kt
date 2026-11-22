/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The copy sync brings down of a file changed on both sides lands next to what is there, never over it, and a file is
 * found under any spelling of its name.
 */
class LibraryFileLocalSourceImplTest {

    @Test
    fun `a conflict copy goes to the first free numbered name and leaves the existing files alone`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage())
        localSource.writeLibraryFile(LibraryCollections.SONGS, "Song.cho", LOCAL)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "Song (2).cho", OTHER)

        val name = localSource.writeLibraryFileToFreeName(LibraryCollections.SONGS, "Song.cho", REMOTE)

        assertEquals("Song (3).cho", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryCollections.SONGS, name))
        assertContentEquals(LOCAL, localSource.readLibraryFile(LibraryCollections.SONGS, "Song.cho"))
        assertContentEquals(OTHER, localSource.readLibraryFile(LibraryCollections.SONGS, "Song (2).cho"))
    }

    @Test
    fun `a conflict copy skips a name the caller says is taken`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage())
        localSource.writeLibraryFile(LibraryCollections.SETLISTS, "gig.setlist.json", LOCAL)

        val name = localSource.writeLibraryFileToFreeName(LibraryCollections.SETLISTS, "gig.setlist.json", REMOTE) { it == "gig (2).setlist.json" }

        assertEquals("gig (3).setlist.json", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryCollections.SETLISTS, name))
    }

    @Test
    fun `a conflict copy under another case or Unicode form of an existing name does not overwrite it`() = runTest {
        // Where the file system folds names (macOS, iOS, Windows), these spellings are the file that is already there.
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = true))
        localSource.writeLibraryFile(LibraryCollections.SONGS, COMPOSED_LOWERCASE, LOCAL)

        val name = localSource.writeLibraryFileToFreeName(LibraryCollections.SONGS, DECOMPOSED_CAPITAL, REMOTE)

        assertEquals(DECOMPOSED_CAPITAL.removeSuffix(".cho") + " (2).cho", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryCollections.SONGS, name))
        assertContentEquals(LOCAL, localSource.readLibraryFile(LibraryCollections.SONGS, COMPOSED_LOWERCASE))
    }

    @Test
    fun `a conflict copy is not given a name another file holds in another case where the file system tells case apart`() = runTest {
        // The sync service takes the two spellings for one file, so the copy would collide with the other file there.
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = false))
        localSource.writeLibraryFile(LibraryCollections.SONGS, "Song.cho", LOCAL)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song (2).cho", OTHER)

        val name = localSource.writeLibraryFileToFreeName(LibraryCollections.SONGS, "Song.cho", REMOTE)

        assertEquals("Song (3).cho", name)
        assertContentEquals(OTHER, localSource.readLibraryFile(LibraryCollections.SONGS, "song (2).cho"))
    }

    @Test
    fun `a numbered variant holding the incoming bytes is found past one holding others`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = false))
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1.cho", LOCAL)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1 (2).cho", OTHER)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1 (3).cho", REMOTE)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1 (4).cho", REMOTE)

        assertEquals("song_1 (3).cho", localSource.numberedVariantHolding(LibraryCollections.SONGS, "song_1.cho", REMOTE))
    }

    @Test
    fun `a numbered variant is matched under another case`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = false))
        localSource.writeLibraryFile(LibraryCollections.SONGS, "SONG_1 (2).CHO", REMOTE)

        assertEquals("SONG_1 (2).CHO", localSource.numberedVariantHolding(LibraryCollections.SONGS, "song_1.cho", REMOTE))
    }

    @Test
    fun `names that only look like numbered variants are not`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = false))
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1.cho", REMOTE)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_12.cho", REMOTE)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song_1 (2) copy.cho", REMOTE)

        assertNull(localSource.numberedVariantHolding(LibraryCollections.SONGS, "song_1.cho", REMOTE))
    }

    @Test
    fun `a file is found under another case or Unicode form where the file system tells them apart`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = false))
        localSource.writeLibraryFile(LibraryCollections.SONGS, COMPOSED_LOWERCASE, LOCAL)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "Song.cho", LOCAL)
        localSource.writeLibraryFile(LibraryCollections.SONGS, "song.cho", OTHER)

        assertEquals(COMPOSED_LOWERCASE, localSource.existingSpellingOf(LibraryCollections.SONGS, DECOMPOSED_CAPITAL))
        assertEquals("song.cho", localSource.existingSpellingOf(LibraryCollections.SONGS, "song.cho"))
        assertNull(localSource.existingSpellingOf(LibraryCollections.SONGS, "other.cho"))
        assertNull(localSource.existingSpellingOf(LibraryCollections.SETLISTS, "song.cho"))
    }

    private companion object {
        val LOCAL = "local".encodeToByteArray()
        val REMOTE = "remote".encodeToByteArray()
        val OTHER = "other".encodeToByteArray()

        /** `Ένα.cho` the way macOS hands it out: a capital Epsilon followed by a separate accent. */
        const val DECOMPOSED_CAPITAL = "\u0395\u0301\u03bd\u03b1.cho"

        /** `ένα.cho`, the name the library would give the song. */
        const val COMPOSED_LOWERCASE = "\u03ad\u03bd\u03b1.cho"
    }
}
