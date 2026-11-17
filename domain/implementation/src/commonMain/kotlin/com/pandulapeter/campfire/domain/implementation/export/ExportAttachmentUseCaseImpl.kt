/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.data.model.library.AttachmentKind
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.domain.api.export.ExportAttachmentUseCase
import org.koin.core.annotation.Factory

@Factory
internal class ExportAttachmentUseCaseImpl(
    private val imageRepository: ImageRepository,
    private val exportFileRepository: ExportFileRepository,
) : ExportAttachmentUseCase {

    override suspend operator fun invoke(source: String, name: String): ExportedFile? {
        val bytes = imageRepository.loadImage(source) ?: return null
        // Unique, since the app the last copy of this name was handed to may still have it open.
        val writer = exportFileRepository.createUnique(attachmentFileName(source = source, name = name))
        val content = try {
            writer.write(bytes, 0, bytes.size)
            writer.finish()
        } catch (throwable: Throwable) {
            // A cancellation as much as a failed write: a half-written copy must not be what the next open finds.
            writer.abandon()
            throw throwable
        }
        return ExportedFile(name = writer.name, mimeType = AttachmentKind.mimeTypeOf(writer.name), content = content)
    }
}

/**
 * The name the copy of the attachment [source] is written under for [name]: the characters some file system refuses
 * (Windows' reserved ones, separators, control characters) replaced, no leading dot (a hidden file) or trailing dot or
 * space (which Windows drops), at most [MAX_NAME_LENGTH] characters before the extension, and [source]'s extension
 * added where the name does not end in it, since that is what the platform picks the app to open the copy with.
 */
internal fun attachmentFileName(source: String, name: String): String {
    val extension = source.substringAfterLast('.', "").lowercase().let { if (it.isEmpty()) "" else ".$it" }
    val cleaned = name.map { if (it in RESERVED_CHARACTERS || it.isISOControl()) '_' else it }.joinToString("").trim()
    val withoutExtension = if (extension.isNotEmpty() && cleaned.endsWith(extension, ignoreCase = true)) cleaned.dropLast(extension.length) else cleaned
    val base = withoutExtension.trimStart('.', ' ').take(MAX_NAME_LENGTH).trimEnd('.', ' ').ifEmpty { FALLBACK_NAME }
    return base + if (withoutExtension == cleaned) extension else cleaned.takeLast(extension.length)
}

private const val RESERVED_CHARACTERS = "/\\:*?\"<>|"
private const val MAX_NAME_LENGTH = 120
private const val FALLBACK_NAME = "attachment"
