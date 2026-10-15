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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The copy sync brings down of a file changed on both sides lands next to what is there, never over it. */
class LibraryFileLocalSourceImplTest {

    @Test
    fun `a conflict copy goes to the first free numbered name and leaves the existing files alone`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage())
        localSource.writeLibraryFile(LibraryFileKind.SONG, "Song.cho", LOCAL)
        localSource.writeLibraryFile(LibraryFileKind.SONG, "Song (2).cho", OTHER)

        val name = localSource.writeLibraryFileToFreeName(LibraryFileKind.SONG, "Song.cho", REMOTE)

        assertEquals("Song (3).cho", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryFileKind.SONG, name))
        assertContentEquals(LOCAL, localSource.readLibraryFile(LibraryFileKind.SONG, "Song.cho"))
        assertContentEquals(OTHER, localSource.readLibraryFile(LibraryFileKind.SONG, "Song (2).cho"))
    }

    @Test
    fun `a conflict copy skips a name the caller says is taken`() = runTest {
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage())
        localSource.writeLibraryFile(LibraryFileKind.SETLIST, "gig.setlist.json", LOCAL)

        val name = localSource.writeLibraryFileToFreeName(LibraryFileKind.SETLIST, "gig.setlist.json", REMOTE) { it == "gig (2).setlist.json" }

        assertEquals("gig (3).setlist.json", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryFileKind.SETLIST, name))
    }

    @Test
    fun `a conflict copy under another case or Unicode form of an existing name does not overwrite it`() = runTest {
        // Where the file system folds names (macOS, iOS, Windows), these spellings are the file that is already there.
        val localSource = LibraryFileLocalSourceImpl(InMemoryFileStorage(foldsNames = true))
        localSource.writeLibraryFile(LibraryFileKind.SONG, COMPOSED_LOWERCASE, LOCAL)

        val name = localSource.writeLibraryFileToFreeName(LibraryFileKind.SONG, DECOMPOSED_CAPITAL, REMOTE)

        assertEquals(DECOMPOSED_CAPITAL.removeSuffix(".cho") + " (2).cho", name)
        assertContentEquals(REMOTE, localSource.readLibraryFile(LibraryFileKind.SONG, name))
        assertContentEquals(LOCAL, localSource.readLibraryFile(LibraryFileKind.SONG, COMPOSED_LOWERCASE))
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
