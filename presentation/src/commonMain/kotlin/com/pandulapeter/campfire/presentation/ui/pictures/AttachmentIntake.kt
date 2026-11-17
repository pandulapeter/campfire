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

import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.platform.isAttachmentFileName
import kotlinx.coroutines.CancellationException

/**
 * How a file picked to attach to a song (`FilePicker.pickAttachment`) is kept on the draft shelf, for the editor's Attach
 * button and the Attachments sheet alike: refused with a snackbar where it is too large or of a type no song takes, else
 * handed to [addDraftAttachment] under its own extension, which reads and hashes it as part of [work], so that a large
 * recording brings up the window's work bar. Never re-encoded, whatever it is.
 */
internal class AttachmentIntake(
    private val addDraftAttachment: suspend (bytes: ByteArray, extension: String) -> ImageIntake,
    private val messageSink: MessageSink,
    private val work: PictureWork,
) {

    /** The attachment [file] is kept as, named as it was picked, or null where it was not kept, said in a snackbar. */
    suspend fun keep(file: ImportedFile): ChordProAttachment? {
        val refusal = when {
            file.isTooLarge -> ImageRefusal.TOO_LARGE
            !isAttachmentFileName(file.name) -> ImageRefusal.UNSUPPORTED_FORMAT
            else -> null
        }
        if (refusal != null) return null.also { messageSink.sendMessage(Message.AttachmentNotAdded(refusal)) }
        // A file handed back empty is one the platform could not read.
        if (file.bytes.isEmpty()) return null.also { messageSink.sendMessage(Message.OperationFailed) }
        val intake = try {
            work.track { addDraftAttachment(file.bytes, file.name.substringAfterLast('.').lowercase()) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not keep an attachment: ${exception::class.simpleName}")
            return null.also { messageSink.sendMessage(Message.OperationFailed) }
        }
        return when (intake) {
            is ImageIntake.Accepted -> ChordProAttachment(source = intake.name, name = file.name)
            is ImageIntake.Refused -> null.also { messageSink.sendMessage(Message.AttachmentNotAdded(intake.reason)) }
        }
    }
}
