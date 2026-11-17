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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.FakeImportDownloadRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncomingPictureLinksTest {

    @Test
    fun `every https address the songs name is asked once`() = runTest {
        val downloads = FakeImportDownloadRepository()
        val files = listOf(
            song("{title: A}\n{image: src=$FIRST}\n{image: src=chart.png}\n{image: src=http://example.com/old.png}\n"),
            song("{title: B}\n{image: src=$SECOND}\n{image: src=$FIRST}\n"),
        )

        incomingPictureLinks(downloads = downloads).planned(files, ImportLimits.MAX_IMPORT_IMAGES_SIZE) {}

        assertEquals(listOf(FIRST, SECOND).sorted(), downloads.asked.sorted())
    }

    @Test
    fun `pictures past what is left of the import's picture budget are reported too large`() = runTest {
        val first = FakeImageRepository.picture(1)
        val second = FakeImageRepository.picture(2)
        val downloads = FakeImportDownloadRepository(mapOf(FIRST to first, SECOND to second))
        val files = listOf(song("{title: A}\n{image: src=$FIRST}\n{image: src=$SECOND}\n"))

        val planned = incomingPictureLinks(downloads = downloads).planned(files, remainingImageSize = first.size.toLong()) {}

        assertTrue(planned.getValue(FIRST).name != null)
        assertEquals(ImportedImage.Problem.TOO_LARGE, planned.getValue(SECOND).problem)
    }

    @Test
    fun `an address that brings nothing is left out and its directive with it`() = runTest {
        val planned = incomingPictureLinks().planned(listOf(song("{title: A}\n{image: src=$FIRST}\n")), ImportLimits.MAX_IMPORT_IMAGES_SIZE) {}

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `the downloads are counted as their own phase`() = runTest {
        val progress = mutableListOf<ImportProgress>()
        val files = listOf(song("{title: A}\n{image: src=$FIRST}\n{image: src=$SECOND}\n"))

        incomingPictureLinks().planned(files, ImportLimits.MAX_IMPORT_IMAGES_SIZE) { progress += it }

        assertEquals(listOf(0, 1, 2), progress.map { it.completed })
        assertTrue(progress.all { it.phase == ImportProgress.Phase.DOWNLOADING_PICTURES && it.total == 2 })
    }

    @Test
    fun `with the Features switch off nothing is asked`() = runTest {
        val downloads = FakeImportDownloadRepository(mapOf(FIRST to FakeImageRepository.picture(1)))

        val planned = incomingPictureLinks(downloads = downloads, isEnabled = false)
            .planned(listOf(song("{title: A}\n{image: src=$FIRST}\n")), ImportLimits.MAX_IMPORT_IMAGES_SIZE) {}

        assertTrue(planned.isEmpty())
        assertTrue(downloads.asked.isEmpty())
    }

    private fun song(text: String) = ImportedFile("song.cho", text.encodeToByteArray())

    private companion object {
        const val FIRST = "https://example.com/first.png"
        const val SECOND = "https://example.com/second.png"
    }
}
