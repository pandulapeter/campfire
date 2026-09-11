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
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun setlistNameIsAlwaysOpenableAndCapped() {
        // A title written in an alphabet the accent table knows nothing about leaves no name behind at all.
        assertEquals("untitled.setlist.json", setlistFileName("Летний сет"))
        assertEquals("untitled.setlist.json", setlistFileName("!!!"))
        val base = setlistFileName("Lorem ipsum ".repeat(40)).removeSuffix(SETLIST_EXTENSION)
        assertTrue(base.length <= LibraryFiles.MAX_NAME_LENGTH)
        // Cut mid-word rather than left ending on the separator a cut word would otherwise leave behind.
        assertTrue(!base.endsWith(LibraryFiles.NAME_SEPARATOR))
    }

    @Test
    fun setlistCollisionsAreNumberedWithoutLeavingTheNormalizedAlphabet() {
        assertEquals("summer_set_2", "summer_set" + setlistCollisionSuffix(2))
    }
}
