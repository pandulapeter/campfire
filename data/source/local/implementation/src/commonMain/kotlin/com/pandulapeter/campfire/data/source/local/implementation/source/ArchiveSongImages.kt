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
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipContent
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText

/**
 * The pictures and attachments the ChordPro songs of an archive name, read from the same archive in further walks of it,
 * after the first has read the songs: a file is read only for a song that names it, so the photos next to the songs of a
 * folder somebody zipped up cost nothing, and stay reported as skipped. Another app's song (OpenSong, OnSong, a JSON
 * export, ABC) brings none: those apps keep their pictures in ways of their own.
 *
 * Every picture is held to [ImportLimits.MAX_IMAGE_FILE_SIZE] and all of them together to what is left of the import's
 * picture budget, every attachment to [ImportLimits.MAX_ATTACHMENT_FILE_SIZE] and all of them to the attachment budget,
 * each charged as the entries declare themselves before they are read, so a file past either is reported with its song
 * rather than read. A file named by several songs is read once, and one a song shows as a picture is read as a picture
 * however many attach it, so that the one read serves both.
 */
internal object ArchiveSongImages {

    /**
     * What [read] found: the pictures and attachments of each entry of the first walk, in its order (empty for one that
     * is no song or names none), which entries of the archive were taken for them (and so are not reported as skipped),
     * and what they cost the picture and attachment budgets.
     */
    class Found(
        val imagesByEntry: List<List<ImportedImage>>,
        val takenEntryNames: Set<String>,
        val chargedSize: Long,
        val chargedAttachmentSize: Long,
    )

    /**
     * The pictures and attachments the songs among [content]'s entries name, out of [archive], within [maxSize] and
     * [maxAttachmentSize] bytes. [isHidden] tells the entries no song can mean, which the first walk left out unreported.
     */
    fun read(archive: ByteArray, content: ZipContent, maxSize: Long, maxAttachmentSize: Long, isHidden: (String) -> Boolean): Found {
        val wanted = wanted(content.entries.map { it.name to it.bytes }, content.unread.map { it.name }, isHidden)
            ?: return Found(content.entries.map { emptyList() }, emptySet(), 0, 0)
        val walks = listOfNotNull(
            walk(archive, wanted.pictures, maxSize, isAttachment = false),
            walk(archive, wanted.attachments, maxAttachmentSize, isAttachment = true),
        )
        return found(
            wanted = wanted,
            bytesByName = wanted.readFirst + walks.flatMap { it.content.entries }.associate { it.name to it.bytes },
            unreadByName = walks.flatMap { walk -> walk.content.unread.filter { it.name in walk.wanted } }.associate { it.name to it.reason },
            chargedSize = walks.firstOrNull { !it.isAttachment }?.content?.chargedSize ?: 0,
            chargedAttachmentSize = walks.firstOrNull { it.isAttachment }?.content?.chargedSize ?: 0,
        )
    }

    /**
     * What the songs of an archive's first walk name, and where in the archive each of those files is: by entry of
     * [entries] (each entry's name, and its bytes where it was read into memory), the entry each picture's and each
     * attachment's source resolves to, and the entries a further walk is to read for them. [unreadNames] are the entries
     * the first walk did not read, which a song may name all the same. Null where no song names anything.
     */
    class Wanted(
        val resolvedPictures: List<Map<String, String?>>,
        val resolvedAttachments: List<Map<String, String?>>,
        val readFirst: Map<String, ByteArray>,
        val pictures: Set<String>,
        val attachments: Set<String>,
    )

    /** See [Wanted]. */
    fun wanted(entries: List<Pair<String, ByteArray?>>, unreadNames: List<String>, isHidden: (String) -> Boolean): Wanted? {
        val texts = entries.map { (name, bytes) ->
            if (isHidden(name) || LibraryFiles.SONG_EXTENSIONS.none { name.endsWith(it, ignoreCase = true) }) null else bytes?.decodeLibraryText()
        }
        val pictureSources = texts.map { text -> text?.let(ChordProImages::referencedSources).orEmpty().toList() }
        val attachmentSources = texts.map { text -> text?.let(ChordProAttachments::referencedSources).orEmpty().toList() }
        if (pictureSources.all { it.isEmpty() } && attachmentSources.all { it.isEmpty() }) return null
        val paths = ArchiveImagePaths((entries.map { it.first } + unreadNames).filterNot(isHidden))
        val resolvedPictures = entries.mapIndexed { index, (name, _) -> pictureSources[index].associateWith { paths.resolve(name, it) } }
        val resolvedAttachments = entries.mapIndexed { index, (name, _) -> attachmentSources[index].associateWith { paths.resolve(name, it) } }
        val readFirst = entries.mapNotNull { (name, bytes) -> bytes?.let { name to it } }.toMap()
        val wantedPictures = resolvedPictures.flatMap { it.values }.filterNotNull().filterNot(readFirst::containsKey).toSet()
        val wantedAttachments = resolvedAttachments.flatMap { it.values }.filterNotNull().filterNot { it in readFirst || it in wantedPictures }.toSet()
        return Wanted(resolvedPictures, resolvedAttachments, readFirst, wantedPictures, wantedAttachments)
    }

    /** What a walk reading the files of [wanted] asks of each entry: only those, each within its kind's own limit. */
    fun policyOf(wanted: Set<String>, isAttachment: Boolean): (String, Long) -> EntryPolicy = { name, _ ->
        if (name in wanted) EntryPolicy.Read(if (isAttachment) ImportLimits.MAX_ATTACHMENT_FILE_SIZE else ImportLimits.MAX_IMAGE_FILE_SIZE) else EntryPolicy.Skip
    }

    /**
     * Each entry's pictures and attachments, out of the files [wanted] names that were read ([bytesByName], the first
     * walk's among them) or why not ([unreadByName]), with what reading them cost.
     */
    fun found(
        wanted: Wanted,
        bytesByName: Map<String, ByteArray>,
        unreadByName: Map<String, UnreadZipEntry.Reason>,
        chargedSize: Long,
        chargedAttachmentSize: Long,
    ): Found {
        val images = wanted.resolvedPictures.indices.map { index ->
            wanted.resolvedPictures[index].map { (source, name) -> found(source, name, bytesByName, unreadByName, isAttachment = false) } +
                wanted.resolvedAttachments[index].map { (source, name) -> found(source, name, bytesByName, unreadByName, isAttachment = true) }
        }
        return Found(
            imagesByEntry = images,
            takenEntryNames = (wanted.resolvedPictures + wanted.resolvedAttachments).flatMap { it.values }.filterNotNull().toSet(),
            chargedSize = chargedSize,
            chargedAttachmentSize = chargedAttachmentSize,
        )
    }

    private fun found(
        source: String,
        name: String?,
        bytesByName: Map<String, ByteArray>,
        unreadByName: Map<String, UnreadZipEntry.Reason>,
        isAttachment: Boolean,
    ): ImportedImage {
        val bytes = name?.let(bytesByName::get)
        return ImportedImage(
            source = source,
            bytes = bytes,
            problem = when {
                bytes != null -> null
                name == null -> ImportedImage.Problem.MISSING
                unreadByName[name] == UnreadZipEntry.Reason.TOO_LARGE -> ImportedImage.Problem.TOO_LARGE
                else -> ImportedImage.Problem.UNREADABLE
            },
            isAttachment = isAttachment,
        )
    }

    /** One walk of an archive for the entries it [wanted], and whether those were attachments. */
    private class Walk(val content: ZipContent, val wanted: Set<String>, val isAttachment: Boolean)

    /**
     * The entries of [archive] among [wanted], each within the limit of a picture or of an attachment and all within
     * [maxSize]; null where none is wanted.
     */
    private fun walk(archive: ByteArray, wanted: Set<String>, maxSize: Long, isAttachment: Boolean) = if (wanted.isEmpty()) {
        null
    } else {
        Walk(
            content = ZipReader.read(archive = archive, maxTotalSize = maxSize, limitOf = policyOf(wanted, isAttachment)),
            wanted = wanted,
            isAttachment = isAttachment,
        )
    }
}
