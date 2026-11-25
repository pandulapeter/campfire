/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineMarks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Weights no test here can read, so each read is seen and every model is missing. */
class PageModelsTest {

    private val read = mutableListOf<String>()
    private val models = PageModels { path -> read += path; ByteArray(0) }
    private val marks = PageLineMarks(size = 1f, isBold = true, isItalic = false, isMonospace = false, isHeading = true, isLink = false, isNewBlock = true, isLandmark = false)

    @Test
    fun `a page without marks reads only the plain weights and only once`() = runTest {
        assertNull(models.forPage(listOf(PageLine("G   D"), PageLine("Down by the river"))))
        assertNull(models.forPage(listOf(PageLine("Menu"))))
        assertNull(models.plain())

        assertEquals(listOf(PageModels.PLAIN_WEIGHTS), read)
    }

    @Test
    fun `a page with marks reads the marked weights and falls back to the plain ones`() = runTest {
        assertNull(models.forPage(listOf(PageLine("Title", marks = marks), PageLine("G   D"))))
        assertNull(models.forPage(listOf(PageLine("Title", marks = marks))))

        assertEquals(listOf(PageModels.MARKED_WEIGHTS, PageModels.PLAIN_WEIGHTS), read)
    }
}
