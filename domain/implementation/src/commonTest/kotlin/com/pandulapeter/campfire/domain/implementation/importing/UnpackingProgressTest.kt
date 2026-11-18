/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.StagedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnpackingProgressTest {

    private fun staged(name: String, size: Long) = ImportedFile(name = name, bytes = ByteArray(0), staged = StagedFile(name = "s-1-$size.zip", size = size, sha256Hex = null))

    private val song = ImportedFile(name = "song.cho", bytes = "{title: A}".encodeToByteArray())

    @Test
    fun `an unpack's bytes fill the phase against the staged archives' sizes`() {
        val reported = mutableListOf<ImportProgress>()
        val progress = UnpackingProgress(listOf(staged("a.zip", 1000), song, staged("b.zip", 500)), reported::add)

        progress.start(0)
        progress.read(readBytes = 400, totalBytes = 1000)
        progress.finish()
        progress.start(1)
        progress.finish()
        progress.start(2)
        progress.read(readBytes = 500, totalBytes = 500)
        progress.finish()
        progress.end()

        assertEquals(listOf(0L, 400L, 1000L, 1000L, 1500L, 1500L), reported.map { it.completedBytes })
        assertTrue(reported.all { it.totalBytes == 1500L && it.phase == ImportProgress.Phase.UNPACKING })
        assertEquals(listOf(0, 0, 1, 2, 2, 3), reported.map { it.completed })
    }

    @Test
    fun `a nested archive's size joins the total as the unpack reaches it and stays in it`() {
        val reported = mutableListOf<ImportProgress>()
        val progress = UnpackingProgress(listOf(staged("a.zip", 1000), staged("b.zip", 500)), reported::add)

        progress.start(0)
        progress.read(readBytes = 1000, totalBytes = 1300)
        progress.read(readBytes = 1100, totalBytes = 1300)
        progress.finish()
        progress.start(1)
        progress.finish()
        progress.end()

        assertEquals(listOf(1500L, 1800L, 1800L, 1800L, 1800L), reported.map { it.totalBytes })
        assertEquals(listOf(0L, 1000L, 1100L, 1300L, 1800L), reported.map { it.completedBytes })
    }

    @Test
    fun `a batch without a staged archive counts only its files`() {
        val reported = mutableListOf<ImportProgress>()
        val progress = UnpackingProgress(listOf(song), reported::add)

        progress.start(0)
        progress.finish()
        progress.end()

        assertEquals(listOf(null, null), reported.map { it.completedBytes })
        assertEquals(listOf(0, 1), reported.map { it.completed })
    }
}
