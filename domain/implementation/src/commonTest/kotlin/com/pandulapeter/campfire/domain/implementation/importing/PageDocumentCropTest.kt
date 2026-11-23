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

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.pagesong.PageLineScores
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageDocumentCropTest {

    @Test
    fun `a document's lines are scored with their layout`() = runTest {
        val scores = List(2) { PageLineScores(floatArrayOf(0f, 1f, 0f, 0f, 0f)) }
        val cropper = FakePageCropper(documentScoresOf = { scores })

        assertEquals(scores, PageDocumentCrop.scoresOf(SHEET, cropper, Logger { }))
        assertEquals(listOf("Amazing grace", "How sweet the sound"), cropper.askedDocuments.single().map { it.text })
        assertTrue(cropper.askedDocuments.single().all { it.layout != null })
    }

    @Test
    fun `no model and a failure both convert the document whole and the failure is logged`() = runTest {
        val logged = mutableListOf<String>()

        assertNull(PageDocumentCrop.scoresOf(SHEET, FakePageCropper(), Logger { logged += it }))
        assertEquals(emptyList(), logged)
        assertNull(PageDocumentCrop.scoresOf(SHEET, FakePageCropper(documentScoresOf = { error("No weights") }), Logger { logged += it }))
        assertEquals(1, logged.size)
    }

    @Test
    fun `a document of no line is not asked about`() = runTest {
        val cropper = FakePageCropper()

        assertNull(PageDocumentCrop.scoresOf(ChordSheet(emptyList()), cropper, Logger { }))
        assertEquals(emptyList(), cropper.askedDocuments)
    }

    private companion object {
        val SHEET = ChordSheet(ChordSheet.ofPlainText("Amazing grace\nHow sweet the sound").pages)
    }
}
