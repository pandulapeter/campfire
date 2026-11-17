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

import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter

/**
 * The pictures and attachments an incoming song brings, planned and then stored. Rewriting each `src` and each
 * attachment line's file to the library's name for it is the one change an import makes to a song's text, made when it
 * is planned, so the text the review holds, compares with the library and its own batch, and writes is the text the song
 * will have: re-importing Campfire's own export of a song whose files the library holds finds it unchanged, whatever the
 * archive called them.
 */
internal object IncomingImages {

    /**
     * Each of [images] by the file its directive names, as the library would take it: under which name, or why not. An
     * attachment is judged by its size and the extension of the file its line names, which is the one it arrived with.
     */
    suspend fun planned(images: List<ImportedImage>, imageRepository: ImageRepository): Map<String, ImportPlan.SongImage> =
        images.associate { image ->
            val bytes = image.bytes
            val intake = bytes?.let {
                if (image.isAttachment) imageRepository.libraryNameOfAttachment(it, image.source.substringAfterLast('.', "")) else imageRepository.libraryNameOf(it)
            }
            image.source to when (intake) {
                null -> ImportPlan.SongImage(image.source, name = null, bytes = null, problem = image.problem ?: ImportedImage.Problem.MISSING, isAttachment = image.isAttachment)
                is ImageIntake.Accepted -> ImportPlan.SongImage(image.source, name = intake.name, bytes = bytes, problem = null, isAttachment = image.isAttachment)
                is ImageIntake.Refused -> ImportPlan.SongImage(image.source, name = null, bytes = null, problem = intake.reason.problem, isAttachment = image.isAttachment)
            }
        }

    /**
     * [text] naming every picture it brings out of [planned], and every attachment out of [plannedAttachments], by its
     * library name, and those it names among them, pictures first, each in the order it first names them. A directive
     * naming a file not brought keeps it as it arrived.
     */
    fun rewritten(
        text: String,
        planned: Map<String, ImportPlan.SongImage>,
        plannedAttachments: Map<String, ImportPlan.SongImage>,
    ): Pair<String, List<ImportPlan.SongImage>> {
        if (planned.isEmpty() && plannedAttachments.isEmpty()) return text to emptyList()
        val images = ChordProImages.referencedSources(text).mapNotNull(planned::get) +
            ChordProAttachments.referencedSources(text).mapNotNull(plannedAttachments::get)
        val withPictures = ChordProImages.withSourcesRewritten(text) { source -> planned[source]?.name }
        return ChordProAttachments.withAttachmentSourcesRewritten(withPictures) { source -> plannedAttachments[source]?.name } to images
    }

    /**
     * Stores the pictures and attachments [entry] brings through [writer], each one once per [stored] (an import's
     * pictures are often shared by its songs, and comparing a picture with the library's copy reads that copy), and
     * answers [entry]'s text as it is to be written: as planned, unless a file had to take another name since (a different
     * file took the planned one meanwhile), which the text then names instead.
     */
    suspend fun store(entry: ImportPlan.SongEntry, writer: LibraryImageWriter, stored: MutableMap<String, ImageIntake>): String {
        val renamed = mutableMapOf<String, String>()
        val renamedAttachments = mutableMapOf<String, String>()
        entry.images.forEach { image ->
            val name = image.name ?: return@forEach
            val bytes = image.bytes ?: return@forEach
            val renames = if (image.isAttachment) renamedAttachments else renamed
            val intake = stored.getOrPut(if (image.isAttachment) ATTACHMENT_KEY + name else name) {
                if (image.isAttachment) writer.addAttachment(bytes, name.substringAfterLast('.')) else writer.addImage(bytes)
            }
            when (intake) {
                is ImageIntake.Accepted -> if (intake.name != name) renames[name] = intake.name
                // Examined when it was planned, so never refused here; were it ever, the song names it as it arrived.
                is ImageIntake.Refused -> renames[name] = image.source
            }
        }
        if (renamed.isEmpty() && renamedAttachments.isEmpty()) return entry.text
        val withPictures = ChordProImages.withSourcesRewritten(entry.text) { renamed[it] }
        return ChordProAttachments.withAttachmentSourcesRewritten(withPictures) { renamedAttachments[it] }
    }

    /** Before the name of an attachment among what [store] stored, which a picture under the same name is stored apart from. */
    private const val ATTACHMENT_KEY = "attachment:"

    private val ImageRefusal.problem
        get() = when (this) {
            ImageRefusal.TOO_LARGE, ImageRefusal.TOO_MANY_PIXELS -> ImportedImage.Problem.TOO_LARGE
            ImageRefusal.UNSUPPORTED_FORMAT -> ImportedImage.Problem.UNSUPPORTED_FORMAT
            ImageRefusal.NOT_AN_IMAGE -> ImportedImage.Problem.NOT_AN_IMAGE
        }
}
