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

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedLinkDownloadTest {

    @Test
    fun `a zip is read under the name it came with`() {
        val bytes = byteArrayOf(0x50, 0x4b, 3, 4, 20, 0)
        val zip = assertIs<SharedLinkDownload.Zip>(ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = bytes).toSharedLinkDownload())
        assertEquals("abcdefghijklmnop.zip", zip.file.name)
        assertEquals(bytes.toList(), zip.file.bytes.toList())
    }

    @Test
    fun `a page or an empty file in the zip's place is a link that no longer works`() {
        assertEquals(ImportFailure.LINK_GONE, ImportDownload.File(name = "x.zip", bytes = "<html>Error</html>".encodeToByteArray()).reason())
        assertEquals(ImportFailure.LINK_GONE, ImportDownload.File(name = "x.zip", bytes = ByteArray(0)).reason())
    }

    @Test
    fun `a refusal is a link that no longer works, and only no answer is worth trying again`() {
        assertEquals(ImportFailure.LINK_GONE, ImportDownload.Missing.reason())
        assertEquals(ImportFailure.LINK_UNREACHABLE, ImportDownload.Unreachable.reason())
        assertEquals(ImportFailure.TOO_LARGE, ImportDownload.TooLarge.reason())
    }

    private fun ImportDownload.reason() = assertIs<SharedLinkDownload.Failed>(toSharedLinkDownload()).reason
}
