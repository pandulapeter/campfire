/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttachmentIntakeTest {

    @Test
    fun `an attachment the device has no room for says so`() = runTest {
        val messageSink = MessageSink(backgroundScope)
        val intake = AttachmentIntake(
            addDraftAttachment = { _, _ -> throw IllegalStateException("Could not write.", OutOfSpaceException()) },
            messageSink = messageSink,
            work = PictureWork(),
        )

        assertNull(intake.keep(ImportedFile(name = "Recording.mp3", bytes = ByteArray(3))))
        assertEquals(listOf<Message>(Message.StorageFull), messageSink.messageQueue.value.map { it.value })
    }

    @Test
    fun `an attachment that could not be kept for any other reason says only that`() = runTest {
        val messageSink = MessageSink(backgroundScope)
        val intake = AttachmentIntake(
            addDraftAttachment = { _, _ -> throw IllegalStateException("Could not write.") },
            messageSink = messageSink,
            work = PictureWork(),
        )

        assertNull(intake.keep(ImportedFile(name = "Recording.mp3", bytes = ByteArray(3))))
        assertEquals(listOf<Message>(Message.OperationFailed), messageSink.messageQueue.value.map { it.value })
    }
}
