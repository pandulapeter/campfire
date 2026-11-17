/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import com.pandulapeter.campfire.data.model.library.LibraryFiles

/** How an import takes in the files that arrive, before anything in them is read. */
object ImportIntake {

    /**
     * Whether an archive that arrives is received into the staging folder (`imports/`) and unpacked from there rather
     * than read whole into memory. The one switch between the two paths: off while the streaming one is proven equal to
     * the in-memory one, which stays what runs until then.
     */
    const val STAGES_ARCHIVES = false

    /**
     * What the platforms' intake and the download read [STAGES_ARCHIVES] through: the same value in every build, set
     * otherwise only by tests, which run the import both ways.
     */
    var stagesArchives = STAGES_ARCHIVES

    /** What becomes of one file that arrives, see [decide]. */
    sealed interface Decision {

        /** Read whole into memory, as every file was before staging: within [maxSize], the file's own limit. */
        data class ReadWhole(val maxSize: Long) : Decision

        /** Copied into the staging folder as it is read, and unpacked from there as an archive. */
        data object Stage : Decision

        /** Not read at all: reported as too large ([isTooLarge]), or as a file the import does not take. */
        data class LeaveUnread(val isTooLarge: Boolean) : Decision
    }

    /**
     * What becomes of a file called [name], [size] bytes long where the platform says (null where it cannot), whose
     * first bytes are [head] (at least [ImportLimits.SNIFF_LENGTH] of them where the file is that long), while archives
     * are staged.
     *
     * An archive is what the import unpacks as one: one by its name, or one of any other name whose bytes are a zip's
     * (another app's backup under its own extension, or renamed to a text's on its way over), as the import's own triage
     * says. A picture and a document are never one, whatever their bytes, since a Word file is a zip of its parts and a
     * picture chosen on its own is only ever read as a picture; both keep their limits. An archive is left unread as too
     * large only past [ImportLimits.MAX_STAGED_ARCHIVE_SIZE], every other file within the limit it is read with today,
     * which the head now tells before anything else is read: a text name that is no zip by its bytes is held to
     * [ImportLimits.MAX_TEXT_FILE_SIZE], and a name the import does not know is read only as a text within it.
     */
    fun decide(name: String, size: Long?, head: ByteArray): Decision = when {
        LibraryFiles.isImageFileName(name) -> Decision.ReadWhole(ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE)
        LibraryFiles.isDocumentFileName(name) -> Decision.ReadWhole(ImportLimits.MAX_DOCUMENT_FILE_SIZE)
        LibraryFiles.isArchiveFileName(name) || LibraryFiles.isZipArchive(head) -> if (size != null && size > ImportLimits.MAX_STAGED_ARCHIVE_SIZE) {
            Decision.LeaveUnread(isTooLarge = true)
        } else {
            Decision.Stage
        }
        LibraryFiles.isImportableFileName(name) -> if (size != null && size > ImportLimits.maxSizeOf(name)) {
            Decision.LeaveUnread(isTooLarge = true)
        } else {
            Decision.ReadWhole(ImportLimits.maxSizeOf(name))
        }
        LibraryFiles.isBinaryFileName(name) || !LibraryFiles.isTextLike(head.copyOf(minOf(head.size, ImportLimits.SNIFF_LENGTH))) ->
            Decision.LeaveUnread(isTooLarge = false)
        size != null && size > ImportLimits.MAX_TEXT_FILE_SIZE -> Decision.LeaveUnread(isTooLarge = false)
        else -> Decision.ReadWhole(ImportLimits.MAX_TEXT_FILE_SIZE)
    }
}
