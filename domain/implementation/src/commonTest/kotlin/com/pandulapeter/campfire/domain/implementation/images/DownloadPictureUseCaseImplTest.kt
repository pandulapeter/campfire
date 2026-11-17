/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.images

import com.pandulapeter.campfire.domain.api.images.PictureDownload
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.FakeImportDownloadRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DownloadPictureUseCaseImplTest {

    @Test
    fun `a picture is told by its bytes`() = runTest {
        val picture = FakeImageRepository.picture(1)
        val download = useCase(PICTURE to picture, PAGE to "<html></html>".encodeToByteArray())

        assertContentEquals(picture, assertIs<PictureDownload.Picture>(download(PICTURE)).bytes)
        assertEquals(PictureDownload.NotAPicture, download(PAGE))
    }

    @Test
    fun `a picture of a format the library converts is still a picture`() = runTest {
        assertIs<PictureDownload.Picture>(useCase(PICTURE to FakeImageRepository.HEIC)(PICTURE))
    }

    @Test
    fun `an address that does not answer is unreachable`() = runTest {
        assertEquals(PictureDownload.Unreachable, useCase()(PICTURE))
    }

    private fun useCase(vararg pictures: Pair<String, ByteArray>) = DownloadPictureUseCaseImpl(
        importDownloadRepository = FakeImportDownloadRepository(pictures.toMap()),
        imageRepository = FakeImageRepository(),
    )

    private companion object {
        const val PICTURE = "https://example.com/chart.png"
        const val PAGE = "https://example.com/chart"
    }
}
