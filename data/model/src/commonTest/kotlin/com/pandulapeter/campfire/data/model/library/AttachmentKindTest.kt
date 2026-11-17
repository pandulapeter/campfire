/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AttachmentKindTest {

    @Test
    fun `every extension an attachment may have has a kind other than other`() {
        LibraryFiles.ATTACHMENT_EXTENSIONS.forEach { extension ->
            assertNotEquals(AttachmentKind.OTHER, AttachmentKind.of("3fa9c2e81b7d4f06$extension"), extension)
        }
    }

    @Test
    fun `the kind is read from the extension in any case`() {
        assertEquals(AttachmentKind.DOCUMENT, AttachmentKind.of("Rehearsal notes.PDF"))
        assertEquals(AttachmentKind.AUDIO, AttachmentKind.of("take 2.m4a"))
        assertEquals(AttachmentKind.VIDEO, AttachmentKind.of("fingering.MOV"))
        assertEquals(AttachmentKind.PICTURE, AttachmentKind.of("chart.jpeg"))
        assertEquals(AttachmentKind.MUSIC_NOTATION, AttachmentKind.of("solo.gp5"))
        assertEquals(AttachmentKind.OTHER, AttachmentKind.of("archive.zip"))
        assertEquals(AttachmentKind.OTHER, AttachmentKind.of("no extension"))
    }

    @Test
    fun `a file is handed over under its registered type - else as plain bytes`() {
        assertEquals("application/pdf", AttachmentKind.mimeTypeOf("sheet.pdf"))
        assertEquals("audio/mpeg", AttachmentKind.mimeTypeOf("Take.MP3"))
        assertEquals("application/octet-stream", AttachmentKind.mimeTypeOf("solo.gp5"))
        assertEquals("application/octet-stream", AttachmentKind.mimeTypeOf("unnamed"))
    }
}
