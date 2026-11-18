/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.formats.zip.ByteArrayZipSource
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A difference allowed between the paths for an archive the in-memory
 * reader reads: pictures and attachments the in-memory path reported too large for the import's budget
 * ([ImportLimits.MAX_IMPORT_IMAGES_SIZE], [ImportLimits.MAX_IMPORT_ATTACHMENTS_SIZE], the first shared with picture
 * links) may come in on the streaming path, which stages them within the disk instead. [check] fails unless every other
 * field of the plan, the result, and the library is the same once those files are accounted for.
 */
internal object BudgetDifference {

    /** A file the streaming path brought where the in-memory one reported it too large, and the token both texts name it by. */
    class Lifted(val source: String, val name: String, val isAttachment: Boolean, val size: Long, val token: String)

    /**
     * Checks that [old] and [new] differ only by files the in-memory path left out for its budget, and answers them.
     * A file reported too large is left out for the budget where it is within its own limit, since that limit and the
     * budget are the only two reasons the in-memory path gives for it; the files of its kind the streaming path brought
     * must then add up to more than the budget, or the in-memory path ran out of a budget it had. Such a file may also
     * be unreadable on the streaming path, which has no budget to run out of and finds it damaged (its data cannot be
     * what it declares): then the files of its kind in [archive] must declare more than the budget together, which is
     * what the in-memory path charged them as before it read any.
     */
    suspend fun check(
        label: String,
        archive: ByteArray,
        old: ImportOutcome,
        new: ImportOutcome,
        libraryText: (isNew: Boolean, path: String) -> String,
    ): List<Lifted> {
        val oldPlan = assertNotNull(old.plan, "$label: the in-memory path failed: ${old.failure}")
        val newPlan = assertNotNull(new.plan, "$label: the streaming path failed: ${new.failure}")
        assertEquals(oldPlan.songs.size, newPlan.songs.size, "$label: songs\nin memory: $old\nstreamed: $new")
        val lifted = mutableListOf<Lifted>()
        val relabelled = mutableListOf<ImportPlan.SongImage>()
        val liftedBySong = oldPlan.songs.zip(newPlan.songs).map { (oldSong, newSong) ->
            assertEquals(oldSong.images.map { it.source to it.isAttachment }, newSong.images.map { it.source to it.isAttachment }, "$label: ${oldSong.fileName}")
            oldSong.images.zip(newSong.images).filter { (oldImage, newImage) -> oldImage != newImage }.mapNotNull { (oldImage, newImage) ->
                assertEquals(ImportedImage.Problem.TOO_LARGE, oldImage.problem, "$label: ${oldSong.fileName} ${oldImage.source} differs other than by a budget")
                if (newImage.problem == ImportedImage.Problem.UNREADABLE) {
                    relabelled += newImage
                    return@mapNotNull null
                }
                assertNull(newImage.problem, "$label: ${oldSong.fileName} ${oldImage.source}")
                val content = assertNotNull(newImage.content, "$label: ${oldSong.fileName} ${oldImage.source}")
                val name = assertNotNull(newImage.name)
                val limit = if (oldImage.isAttachment) ImportLimits.MAX_ATTACHMENT_FILE_SIZE else ImportLimits.MAX_IMAGE_FILE_SIZE
                assertTrue(content.size <= limit, "$label: ${oldImage.source} is past its own limit, which no budget lifts")
                lifted.firstOrNull { it.name == name && it.isAttachment == oldImage.isAttachment } ?: Lifted(
                    source = oldImage.source,
                    name = name,
                    isAttachment = oldImage.isAttachment,
                    size = content.size,
                    token = "lifted${lifted.size}.${name.substringAfterLast('.')}",
                ).also { lifted += it }
            }
        }
        if (lifted.isEmpty() && relabelled.isEmpty()) fail("$label: the paths differ, and not by a file the in-memory path found too large\nin memory: $old\nstreamed: $new")
        for (isAttachment in listOf(false, true)) {
            if (lifted.none { it.isAttachment == isAttachment }) continue
            val budget = if (isAttachment) ImportLimits.MAX_IMPORT_ATTACHMENTS_SIZE else ImportLimits.MAX_IMPORT_IMAGES_SIZE
            val brought = newPlan.songs.flatMap { it.images }.mapNotNull { image -> image.content?.takeIf { image.isAttachment == isAttachment } }
                .associateBy { it.sha256Hex() }.values.sumOf { it.size }
            assertTrue(brought > budget, "$label: the streaming path brought $brought bytes, within the budget of $budget the in-memory one ran out of")
        }
        for (isAttachment in listOf(false, true)) {
            if (relabelled.none { it.isAttachment == isAttachment }) continue
            val budget = if (isAttachment) ImportLimits.MAX_IMPORT_ATTACHMENTS_SIZE else ImportLimits.MAX_IMPORT_IMAGES_SIZE
            val limit = if (isAttachment) ImportLimits.MAX_ATTACHMENT_FILE_SIZE else ImportLimits.MAX_IMAGE_FILE_SIZE
            val sources = newPlan.songs.flatMap { it.images }.filter { it.isAttachment == isAttachment }.map { it.source.substringAfterLast('/') }.toSet()
            val declared = declaredSizesOf(archive).filter { (name, size) -> name.substringAfterLast('/') in sources && size <= limit }.sumOf { it.second }
            assertTrue(declared > budget, "$label: its files declare $declared bytes, within the budget of $budget the in-memory one ran out of")
        }
        val relabelledOld = oldPlan.songs.map { song ->
            song.copy(images = song.images.map { image -> relabelled.firstOrNull { it.source == image.source && it.isAttachment == image.isAttachment } ?: image })
        }
        assertEquals(relabelledOld.map { it.canonical(lifted, isNew = false) }, newPlan.songs.map { it.canonical(lifted, isNew = true) }, "$label: songs")
        assertEquals(oldPlan.copy(songs = emptyList()), newPlan.copy(songs = emptyList()), "$label: everything but the songs")
        val oldResult = assertNotNull(old.result)
        val leftOutAfterwards = oldResult.songsWithFilesLeftOut.mapNotNull { song ->
            val gone = liftedBySong.getOrElse(oldPlan.songs.indexOfFirst { it.fileName == song.fileName }) { emptyList() }
            song.copy(
                pictureCount = song.pictureCount - gone.count { !it.isAttachment },
                attachmentCount = song.attachmentCount - gone.count { it.isAttachment },
            ).takeIf { it.pictureCount > 0 || it.attachmentCount > 0 }
        }
        assertEquals(oldResult.copy(songsWithFilesLeftOut = leftOutAfterwards), new.result, "$label: result")
        assertEquals(old.library.keys + lifted.map { "files/${it.name}" }, new.library.keys, "$label: library files")
        for ((path, hash) in old.library) {
            if (new.library[path] == hash) continue
            assertTrue(path.startsWith("songs/"), "$label: $path differs")
            assertEquals(canonical(libraryText(false, path), lifted, isNew = false), canonical(libraryText(true, path), lifted, isNew = true), "$label: $path")
        }
        return lifted
    }

    /** Every entry of [archive] (not of the archives in it) by name, with the size it declares. */
    private suspend fun declaredSizesOf(archive: ByteArray): List<Pair<String, Long>> {
        val declared = mutableListOf<Pair<String, Long>>()
        ZipReader.open(ByteArrayZipSource(archive)).walk(limitOf = { name, size -> EntryPolicy.Skip.also { declared += name to size } }) { _, _ -> error("Nothing is read") }
        return declared
    }

    private fun ImportPlan.SongEntry.canonical(lifted: List<Lifted>, isNew: Boolean) = copy(
        text = canonical(text, lifted, isNew),
        images = images.map { image ->
            val match = lifted.firstOrNull { it.source == image.source && it.isAttachment == image.isAttachment }
            if (match == null) image else ImportPlan.SongImage(source = image.source, name = match.token, content = null, problem = null, isAttachment = image.isAttachment)
        },
    )

    /** [text] with every lifted file named by its token: by its arrival name on the old path, its library name on the new. */
    private fun canonical(text: String, lifted: List<Lifted>, isNew: Boolean): String {
        fun tokenOf(value: String, isAttachment: Boolean) = lifted.firstOrNull { it.isAttachment == isAttachment && (if (isNew) it.name else it.source) == value }?.token
        val pictures = ChordProImages.withSourcesRewritten(text) { tokenOf(it, isAttachment = false) }
        return ChordProAttachments.withAttachmentSourcesRewritten(pictures) { tokenOf(it, isAttachment = true) }
    }
}
