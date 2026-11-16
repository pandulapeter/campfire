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

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter

/**
 * The pictures an incoming song brings, planned and then stored. Rewriting each `src` to the library's name for its
 * picture is the one change an import makes to a song's text, made when it is planned, so the text the review holds,
 * compares with the library and its own batch, and writes is the text the song will have: re-importing Campfire's own
 * export of a song whose pictures the library holds finds it unchanged, whatever the archive called them.
 */
internal object IncomingImages {

    /** Each of [images] by the file its directive names, as the library would take it: under which name, or why not. */
    suspend fun planned(images: List<ImportedImage>, imageRepository: ImageRepository): Map<String, ImportPlan.SongImage> =
        images.associate { image ->
            val bytes = image.bytes
            image.source to when (val intake = bytes?.let { imageRepository.libraryNameOf(it) }) {
                null -> ImportPlan.SongImage(image.source, name = null, bytes = null, problem = image.problem ?: ImportedImage.Problem.MISSING)
                is ImageIntake.Accepted -> ImportPlan.SongImage(image.source, name = intake.name, bytes = bytes, problem = null)
                is ImageIntake.Refused -> ImportPlan.SongImage(image.source, name = null, bytes = null, problem = intake.reason.problem)
            }
        }

    /**
     * [text] naming every picture it brings out of [planned] by its library name, and those it names among them, in the
     * order it first names them. A directive naming a picture not brought keeps its file as it arrived.
     */
    fun rewritten(text: String, planned: Map<String, ImportPlan.SongImage>): Pair<String, List<ImportPlan.SongImage>> {
        if (planned.isEmpty()) return text to emptyList()
        val images = ChordProImages.referencedSources(text).mapNotNull(planned::get)
        return ChordProImages.withSourcesRewritten(text) { source -> planned[source]?.name } to images
    }

    /**
     * Stores the pictures [entry] brings through [writer], each one once per [stored] (an import's pictures are often
     * shared by its songs, and comparing a picture with the library's copy reads that copy), and answers [entry]'s text
     * as it is to be written: as planned, unless a picture had to take another name since (a different picture took the
     * planned one meanwhile), which the text then names instead.
     */
    suspend fun store(entry: ImportPlan.SongEntry, writer: LibraryImageWriter, stored: MutableMap<String, ImageIntake>): String {
        val renamed = mutableMapOf<String, String>()
        entry.images.forEach { image ->
            val name = image.name ?: return@forEach
            val bytes = image.bytes ?: return@forEach
            when (val intake = stored.getOrPut(name) { writer.addImage(bytes) }) {
                is ImageIntake.Accepted -> if (intake.name != name) renamed[name] = intake.name
                // Examined when it was planned, so never refused here; were it ever, the song names it as it arrived.
                is ImageIntake.Refused -> renamed[name] = image.source
            }
        }
        return if (renamed.isEmpty()) entry.text else ChordProImages.withSourcesRewritten(entry.text) { renamed[it] }
    }

    private val ImageRefusal.problem
        get() = when (this) {
            ImageRefusal.TOO_LARGE -> ImportedImage.Problem.TOO_LARGE
            ImageRefusal.UNSUPPORTED_FORMAT -> ImportedImage.Problem.UNSUPPORTED_FORMAT
            ImageRefusal.NOT_AN_IMAGE -> ImportedImage.Problem.NOT_AN_IMAGE
        }
}
