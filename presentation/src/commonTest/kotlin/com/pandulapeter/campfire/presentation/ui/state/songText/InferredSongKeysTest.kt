/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InferredSongKeysTest {

    private var walks = 0
    private val keys = InferredSongKeys(inferKey = { text -> walks++; text.substringAfter("[").substringBefore("]") }, dispatcher = Dispatchers.Unconfined)

    @Test
    fun `a forgotten name loses its key and the others keep theirs`() = runTest {
        keys.inferFor(listOf("preview" to "[G]Hello", "a.cho" to "[D]World"))
        keys.forget { it == "preview" }
        assertEquals(mapOf("a.cho" to "D"), keys.keys.value)
    }

    @Test
    fun `a forgotten name is walked again, and one kept is not`() = runTest {
        keys.inferFor(listOf("preview" to "[G]Hello", "a.cho" to "[D]World"))
        keys.forget { it == "preview" }
        walks = 0
        keys.inferFor(listOf("preview" to "[G]Hello", "a.cho" to "[D]World"))
        assertEquals(1, walks)
        assertEquals(mapOf("preview" to "G", "a.cho" to "D"), keys.keys.value)
    }
}
