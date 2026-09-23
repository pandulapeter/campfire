/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SongMappersTest {

    @Test
    fun theTagsOfASongAreComposedAndMerged() {
        val song = StoredFileInfo(name = "a.cho", size = 0, lastModified = 0)
            .toSong(ChordProParser.summarize("{title: A}\n{tag: Café}\n{tag: café}"))

        assertEquals(listOf("Café"), song.tags)
    }
}
