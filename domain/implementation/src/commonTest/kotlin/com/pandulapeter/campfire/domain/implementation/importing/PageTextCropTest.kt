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

import com.pandulapeter.campfire.data.model.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageTextCropTest {

    @Test
    fun `the lines the cropper leaves out are dropped and the blank lines between kept ones stay`() = runTest {
        val cropped = PageTextCrop.cropped(PAGE, FakePageCropper { listOf(2, 3, 5, 6) }, Logger { })

        assertEquals("G   D\nDown by the river\n\nC   G\nWe sang", cropped)
    }

    @Test
    fun `a crop keeping every line is no crop`() = runTest {
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { PAGE.lines().indices.toList() }, Logger { }))
    }

    @Test
    fun `no song and a failure both keep the whole text and the failure is logged`() = runTest {
        val logged = mutableListOf<String>()

        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { null }, Logger { logged += it }))
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { emptyList() }, Logger { logged += it }))
        assertEquals(emptyList(), logged)
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { error("No weights") }, Logger { logged += it }))
        assertEquals(1, logged.size)
    }

    @Test
    fun `lines the cropper names that the text does not have are ignored`() = runTest {
        assertEquals("Down by the river", PageTextCrop.cropped(PAGE, FakePageCropper { listOf(3, 99, -1) }, Logger { }))
    }

    private companion object {
        const val PAGE = "Menu\nSign in\nG   D\nDown by the river\n\nC   G\nWe sang\n\nComments"
    }
}
