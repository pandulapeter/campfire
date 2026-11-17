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
import com.pandulapeter.campfire.data.model.image.PictureLinks
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koin.core.annotation.Factory

/**
 * The pictures an import's songs name by a web address (`{image: src=https://…}`, [PictureLinks]), downloaded as the
 * import is prepared, so that its review and preview show them and Import writes them as the pictures an archive brings
 * are written, each song's `src` then naming the library's file ([IncomingImages]). Only while the Features switch is on
 * (`UserPreferences.isPictureLinkDownloadEnabled`): a song file must not make the app ask a host for anything otherwise.
 *
 * Each address is asked once per import, a few at a time, within [ImportLimits.MAX_IMAGE_FILE_SIZE] (never re-encoded,
 * as an archive's pictures are not) and what is left of [ImportLimits.MAX_IMPORT_IMAGES_SIZE], in the order the songs
 * name them. One that brings nothing (no answer, nothing there) is left out, its directive kept as it arrived, so the
 * song shows it as missing, with the Download that may work later; one too large is reported with its song.
 */
@Factory
internal class IncomingPictureLinks(
    private val importDownloadRepository: ImportDownloadRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageRepository: ImageRepository,
) {

    /** What the songs of [files] name by their addresses, by address, planned as [IncomingImages.planned] plans them. */
    suspend fun planned(
        files: List<ImportedFile>,
        remainingImageSize: Long,
        onProgress: (ImportProgress) -> Unit,
    ): Map<String, ImportPlan.SongImage> {
        if (userPreferencesRepository.loadUserPreferencesIfNeeded()?.isPictureLinkDownloadEnabled == false) return emptyMap()
        val links = linksIn(files)
        if (links.isEmpty()) return emptyMap()
        var completed = 0
        onProgress(ImportProgress(ImportProgress.Phase.DOWNLOADING_PICTURES, completed, links.size))
        val slots = Semaphore(PARALLEL_DOWNLOADS)
        // The downloads finish on any thread of the worker dispatcher, so the count they report is kept under a lock.
        val counting = Mutex()
        val downloads = coroutineScope {
            links.map { link ->
                async {
                    slots.withPermit { importDownloadRepository.downloadPicture(url = link, maxSizeBytes = ImportLimits.MAX_IMAGE_FILE_SIZE) }
                        .also { counting.withLock { onProgress(ImportProgress(ImportProgress.Phase.DOWNLOADING_PICTURES, ++completed, links.size, link)) } }
                }
            }.awaitAll()
        }
        var remaining = remainingImageSize
        val images = links.zip(downloads).mapNotNull { (link, download) ->
            when (download) {
                is ImportDownload.File -> if (download.bytes.size > remaining) {
                    ImportedImage(source = link, bytes = null, problem = ImportedImage.Problem.TOO_LARGE)
                } else {
                    remaining -= download.bytes.size
                    ImportedImage(source = link, bytes = download.bytes, problem = null)
                }
                ImportDownload.TooLarge -> ImportedImage(source = link, bytes = null, problem = ImportedImage.Problem.TOO_LARGE)
                ImportDownload.Missing, ImportDownload.Unreachable -> null
            }
        }
        return IncomingImages.planned(images, imageRepository)
    }

    /** Every address the texts of [files] name a picture by, each once, in the order first named. */
    private fun linksIn(files: List<ImportedFile>) = files.asSequence()
        .filter { file -> file.bytes.isNotEmpty() }
        .map { file -> file.bytes.decodeLibraryText() }
        // Most songs name no address at all, and reading every directive of a large collection is not free.
        .filter { text -> text.contains(PictureLinks.SCHEME_PREFIX, ignoreCase = true) }
        .flatMap { text -> ChordProImages.referencedSources(text).filter(PictureLinks::isLink) }
        .distinct()
        .toList()

    private companion object {
        const val PARALLEL_DOWNLOADS = 4
    }
}
