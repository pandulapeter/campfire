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

/**
 * How much an import is willing to read. A song is a few kilobytes of text, and every way into the library reads a
 * file whole, so without a ceiling one wrong file in a selection is an allocation the process does not survive: an
 * Android app has a heap of about 192 MB to itself, and running out of it cannot be caught.
 *
 * The numbers are the same on every platform, so that an archive imports the same everywhere. At its worst an import
 * holds what was picked ([MAX_ARCHIVE_SIZE]), the pictures and attachments its songs name ([MAX_IMPORT_IMAGES_SIZE],
 * [MAX_IMPORT_ATTACHMENTS_SIZE]), and the songs it unpacked to with their text, which is two bytes a character as soon as
 * a song steps outside Latin-1: three times [MAX_IMPORT_SIZE]. An archive near its limit is mostly pictures and
 * attachments, which are stored rather than deflated, so its own bytes and theirs are much the same bytes twice, and the
 * songs are the small part of it. The picked archive is let go of once the review has its plan, which keeps the pictures,
 * the attachments, and the songs only.
 */
object ImportLimits {

    /**
     * One song, setlist or collection of songs, picked or inside an archive: a few thousand songs in one file. A text
     * pasted as a new song is refused over it with a message that names it as 8 MB in both languages, so the strings
     * change with it.
     */
    const val MAX_TEXT_FILE_SIZE = 8L shl 20

    /**
     * One picture a song shows, however it arrives (picked, dropped, shared, in an archive, or by sync): within what sync
     * moves, so a picture the library holds can always reach the other devices. A larger one the user adds
     * themselves is re-encoded on the device until it fits (read within [MAX_PICKED_IMAGE_FILE_SIZE]); one that cannot be
     * is refused with a message naming this as 8 MB in every language. One in an archive or arriving by sync is never
     * re-encoded, only refused.
     */
    const val MAX_IMAGE_FILE_SIZE = MAX_TEXT_FILE_SIZE

    /**
     * One picture chosen on its own (picked, dropped, shared, or opened with the app), which may be re-encoded into
     * [MAX_IMAGE_FILE_SIZE]: a phone's largest photo, or a scanned page, well within it. Only its bytes are held whole;
     * the decoding is bounded by the size it is shrunk to. A larger one is left unread, with a message naming this as
     * 40 MB in every language.
     */
    const val MAX_PICKED_IMAGE_FILE_SIZE = 40L shl 20

    /**
     * One file attached to a song, held whole in memory like every other file of the library: a recording or a short
     * video fits, and a larger one is better kept as a link.
     */
    const val MAX_ATTACHMENT_FILE_SIZE = 50L shl 20

    /**
     * What a file the user picks to attach may be: 50 MB as the messages say it and as file managers count, a little
     * less than [MAX_ATTACHMENT_FILE_SIZE], so that a picked file the app calls too large is over 50 MB everywhere it is
     * shown. Only the pickers apply it: sync, archives, and shared copies keep the larger limit, so a file a library
     * already holds is never refused.
     */
    const val MAX_PICKED_ATTACHMENT_SIZE = 50_000_000L

    /** Documents may carry embedded fonts and images much larger than their extracted text. */
    const val MAX_DOCUMENT_FILE_SIZE = 16L shl 20

    /**
     * Everything one import unpacks to apart from pictures (songs, setlists, documents): more than twice a library of
     * three thousand songs of 4 KB. A library exports to an archive of its own size plus about 180 bytes a file, so one
     * of about six thousand such songs exports to an archive this refuses, and the export says so. That message names
     * the limit as 24 MB in every language, so the strings change with it.
     */
    const val MAX_IMPORT_SIZE = 24L shl 20

    /**
     * The pictures one import reads for the songs naming them, apart from [MAX_IMPORT_SIZE] so that photos never crowd
     * songs out of an archive: about twenty phone photos, each within [MAX_IMAGE_FILE_SIZE]. A picture past it is left
     * out and reported with its song, which still imports and shows the picture as missing. The export's message names
     * it as 64 MB in every language.
     */
    const val MAX_IMPORT_IMAGES_SIZE = 64L shl 20

    /**
     * The files attached to songs that one import reads for the songs naming them, apart from the songs' and the pictures'
     * budgets: one attachment of [MAX_ATTACHMENT_FILE_SIZE], or several smaller ones. An attachment past it is left out
     * and reported with its song, which still imports and keeps its attachment line, so that sync can bring the file later.
     * Not larger, since everything an import reads is held in memory at once (see the class), and a small archive of
     * documents that deflate well could otherwise unpack to more than the heap. The export's message names it as 50 MB
     * in every language.
     */
    const val MAX_IMPORT_ATTACHMENTS_SIZE = MAX_ATTACHMENT_FILE_SIZE

    /**
     * One archive as it was picked or downloaded, and everything one selection reads: the songs' budget and the
     * pictures' together, so that an export of songs with their pictures imports whole. The import's and the export's
     * messages name it as 88 MB in every language. Versions before pictures refuse an archive over [MAX_IMPORT_SIZE] as
     * too large, and import a smaller one with its pictures reported as skipped and its image directives kept, and 5.4.0
     * its attachments that are documents, texts, or ABC tunes as songs it offers in the review.
     *
     * Not grown by [MAX_IMPORT_ATTACHMENTS_SIZE]: the picked archive is held whole beside what it unpacks to, and an archive
     * of stored recordings unpacks to as many bytes again, so an archive of 88 MB already costs an Android heap of about
     * 192 MB most of what it has. Attachments mostly fit beside the songs and pictures of a smaller archive; a library whose
     * export is larger is moved by sync, and its export says it cannot be imported back whole.
     */
    const val MAX_ARCHIVE_SIZE = MAX_IMPORT_SIZE + MAX_IMPORT_IMAGES_SIZE

    /**
     * One shared link's copy, packed and unpacked, pictures included and attachments apart
     * ([MAX_SHARED_ATTACHMENTS_SIZE]). Kept at what every version that opens links downloads ([MAX_IMPORT_SIZE] before
     * pictures), so that a link without attachments opens everywhere, and because a copy is packed in memory during a sync
     * run, its files and the archive at once, where a phone has the least to spare. The share screen names it in MB
     * through this constant.
     */
    const val MAX_SHARED_COPY_SIZE = MAX_IMPORT_SIZE

    /**
     * The attachments one shared link's copy carries beside [MAX_SHARED_COPY_SIZE], filled in the order its songs name
     * them; one that does not fit is left out of the copy, never refusing the share. What one import reads
     * ([MAX_IMPORT_ATTACHMENTS_SIZE]), so that whoever opens the link gets every attachment in it, and the copy stays
     * within [MAX_ARCHIVE_SIZE]. Not larger: the copy and its files are held in memory together while a run packs it.
     */
    const val MAX_SHARED_ATTACHMENTS_SIZE = MAX_IMPORT_ATTACHMENTS_SIZE

    /**
     * The most of an archive's central directory the streaming zip reader holds, as it declares it and as its records
     * are walked: a record is 46 bytes with its name and extra fields, so this holds [MAX_ARCHIVE_ENTRIES] records
     * averaging 120 bytes of name and extras, which other tools' timestamps and a song's folder and name leave room
     * for. Checked before the directory is read.
     */
    const val MAX_CENTRAL_DIRECTORY_SIZE = 32L shl 20

    /**
     * The most entries one archive may declare, checked against the declared count before anything is allocated for it;
     * the reader walks no more records than were declared. Far more than any library export or backup, while the
     * compact directory of that many stays a small part of a phone's heap.
     */
    const val MAX_ARCHIVE_ENTRIES = 200_000

    /**
     * How many levels of folders a dropped folder is opened to, its own being the first: a library exported as folders is
     * two deep, and a home directory dropped by accident is not read to the bottom.
     */
    const val MAX_FOLDER_DEPTH = 8

    /** How many entries, files and folders alike, are looked at below one dropped folder before the rest is left unread. */
    const val MAX_FOLDER_ENTRIES = 10_000

    /**
     * How many bytes of a file under a name the import does not know are looked at to decide whether to read the rest:
     * inside an archive and inside a dropped folder, where nobody chose the file itself and reading every photo next to
     * the songs whole would cost the whole selection's budget, one file at a time.
     */
    const val SNIFF_LENGTH = 512

    /**
     * One archive received into the staging folder (`imports/`), copied there by streaming rather than held in memory:
     * not a bound on memory but on the time one import may take, the same as [MAX_IMPORT_STAGED_SIZE].
     */
    const val MAX_STAGED_ARCHIVE_SIZE = 16L shl 30

    /**
     * What one import may unpack into the staging folder (its pictures and attachments, each within its own limit): a
     * bound on the time one import may take, since nothing staged is held in memory. Disk space is the other bound, see
     * [minFreeSpaceFor].
     */
    const val MAX_IMPORT_STAGED_SIZE = 16L shl 30

    /** The most [minFreeSpaceFor] keeps free on a device's own volume. */
    const val MAX_MIN_FREE_SPACE = 512L shl 20

    /** What [minFreeSpaceFor] keeps free of the web's storage quota, whatever the import needs. */
    const val MIN_FREE_QUOTA = 32L shl 20

    /**
     * How much space staging leaves free while an import that needs [neededBytes] writes into it. On a device's own volume
     * a quarter of what the import needs, at most [MAX_MIN_FREE_SPACE]: a large import never takes the system down to its
     * own low-storage state, and a small one is not refused on a phone with a few hundred MB free. On the web
     * ([isQuota]), whose figure is the origin's quota rather than the disk's, [MIN_FREE_QUOTA], so that the library's own
     * writes still fit beside the import.
     */
    fun minFreeSpaceFor(neededBytes: Long, isQuota: Boolean) = if (isQuota) MIN_FREE_QUOTA else minOf(neededBytes.coerceAtLeast(0) / 4, MAX_MIN_FREE_SPACE)

    /**
     * How many bytes of a file called [name] an import reads, which is none for a file it would not look inside.
     * Asked before the file is opened wherever the name is known first, which is everywhere. A name it does not know at
     * all is a question for [ImportBudget], which reads such a file to see whether it is an archive under another name,
     * and so is a text name over its limit (see [isReadAsText]).
     */
    fun maxSizeOf(name: String) = when {
        LibraryFiles.isArchiveFileName(name) -> MAX_ARCHIVE_SIZE
        LibraryFiles.isDocumentFileName(name) -> MAX_DOCUMENT_FILE_SIZE
        LibraryFiles.IMPORTABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> MAX_TEXT_FILE_SIZE
        else -> 0L
    }

    /**
     * Whether a file called [name] is read as a song, setlist or collection of them, with [MAX_TEXT_FILE_SIZE] as its
     * limit. One over that limit is still kept where its bytes are a zip archive: a backup renamed `.txt` or `.json` on
     * its way over is often larger than any text.
     */
    fun isReadAsText(name: String) = maxSizeOf(name) == MAX_TEXT_FILE_SIZE
}

/**
 * What one selection - a pick, a drop, the files of an intent - may still read. Every platform reads its files
 * through one of these, so the rule is written once: nothing the import would not look inside is read, nothing over
 * its own limit is, and the selection as a whole stops at [ImportLimits.MAX_ARCHIVE_SIZE], what one archive may be. The
 * import holds what the selection unpacks to its own budgets again.
 */
class ImportBudget {

    @PublishedApi
    internal var remaining = ImportLimits.MAX_ARCHIVE_SIZE

    /**
     * @param size What the platform says the size of the file is, null where it cannot say.
     * @param readBytes Reads the file, and no more than one byte past `limit` where [size] was null - which is how a
     *   file of unknown size is found out. Null for a file that cannot be read, which is then left out.
     */
    inline fun read(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        if (LibraryFiles.isImageFileName(name)) return readImage(name, size, readBytes)
        if (!LibraryFiles.isImportableFileName(name)) return readUnknown(name, size, readBytes)
        val ownLimit = ImportLimits.maxSizeOf(name)
        // A text name is read as far as the selection allows rather than to its own limit, since one over that limit may
        // be a backup renamed on its way over (see ImportLimits.isReadAsText). A text that is not one is then handed back
        // too large, without having cost the files after it anything.
        val limit = if (ImportLimits.isReadAsText(name)) remaining else minOf(ownLimit, remaining)
        if (size != null && size > limit) return ImportedFile.unread(name, isTooLarge = true)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit || (bytes.size > ownLimit && !LibraryFiles.isZipArchive(bytes))) {
            return ImportedFile.unread(name, isTooLarge = true)
        }
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes)
    }

    /**
     * [read] for a platform that has peeked at the file's first bytes ([head]) while archives are staged
     * ([ImportIntake.stagesArchives]): an archive is handed to [stage] instead of being read, costing the selection
     * nothing, and anything else is read whole as [read] reads it, or left unread where [ImportIntake.decide] says so.
     *
     * @param readBytes As for [read], the bytes of the whole file, [head] included.
     * @param stage Starts receiving the file into the staging folder and hands it over as such; null for a file that
     *   cannot be read.
     */
    inline fun take(
        name: String,
        size: Long?,
        head: ByteArray,
        readBytes: (limit: Long) -> ByteArray?,
        stage: () -> ImportedFile?,
    ): ImportedFile? = when (val decision = ImportIntake.decide(name = name, size = size, head = head)) {
        ImportIntake.Decision.Stage -> stage()
        is ImportIntake.Decision.LeaveUnread -> ImportedFile.unread(name, isTooLarge = decision.isTooLarge)
        is ImportIntake.Decision.ReadWhole -> read(name = name, size = size, readBytes = readBytes)
    }

    /**
     * A file whose name the import does not know is read as an archive would be, since another app's library backup is
     * often a zip archive under a name of its own, and kept where its bytes say it is one. One whose bytes read as text
     * within [ImportLimits.MAX_TEXT_FILE_SIZE] is kept too, flagged [ImportedFile.isPlainTextByContent], since a chord
     * sheet saved by another app is as often named for that app; whether it is one is the import's question. Anything
     * else - the photo or the recording picked with the songs - is handed back unread and gives back what it took, so
     * that it neither crowds the files after it out of the selection nor is reported as too large rather than as
     * unsupported: nothing says it was ever meant to be imported.
     */
    @PublishedApi
    internal inline fun readUnknown(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        val limit = remaining
        if (size != null && size > limit) return ImportedFile.unread(name)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit) return ImportedFile.unread(name)
        val isArchive = LibraryFiles.isZipArchive(bytes)
        val isText = !isArchive && isTextOfUnknownName(name, bytes)
        if (!isArchive && !isText) return ImportedFile.unread(name)
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes, isPlainTextByContent = isText)
    }

    /**
     * A picture chosen on its own, read within [ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE] for the import to tell by its
     * bytes whether it is one the library takes, or one to shrink or convert first. The platforms' folder walks never get here with a picture, which they leave
     * unread by its name, so a dropped folder of photos is not read one photo at a time.
     */
    @PublishedApi
    internal inline fun readImage(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        val limit = minOf(ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE, remaining)
        if (size != null && size > limit) return ImportedFile.unread(name, isTooLarge = true)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit) return ImportedFile.unread(name, isTooLarge = true)
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes)
    }

    /** Whether [bytes] of a file called [name], a name the import does not know, are kept as a text to look at. */
    @PublishedApi
    internal fun isTextOfUnknownName(name: String, bytes: ByteArray) = bytes.size <= ImportLimits.MAX_TEXT_FILE_SIZE &&
        !LibraryFiles.isBinaryFileName(name) && LibraryFiles.isTextLike(bytes.copyOf(minOf(bytes.size, ImportLimits.SNIFF_LENGTH)))

    companion object {

        /**
         * Whether a file under a name the import does not know is worth reading whole, judged by its first
         * [ImportLimits.SNIFF_LENGTH] bytes ([head]): an archive, or a text that may be a chord sheet. Asked by the
         * folder walks, which read nothing of a file that is not, so that a dropped folder of photos costs a peek at
         * each rather than a read of each; a file of one of the [LibraryFiles.BINARY_EXTENSIONS] is not even peeked at.
         */
        fun isWorthReadingUnknown(head: ByteArray) = LibraryFiles.isZipArchive(head) || LibraryFiles.isTextLike(head)
    }
}
