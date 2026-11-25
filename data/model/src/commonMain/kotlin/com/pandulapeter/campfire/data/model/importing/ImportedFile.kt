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
 * A file on its way into the library: what a file picker, a drop or an archive hands over, before anything has been
 * decided about it. [name] is a plain file name; entries coming out of an archive have their path stripped.
 *
 * A file that was not read is handed over all the same, with no bytes, so that it is reported rather than lost: one
 * the import would not look inside reads as skipped by its extension, one that could not be read holds nothing to
 * import, and one that was left unread for its size says so through [isTooLarge], see [ImportLimits].
 *
 * [isPlainTextByContent] marks a file kept for its bytes alone, under a name the import does not know: read as plain
 * text, and only where it has the structure of a chord sheet. The flag is carried rather than the name changed, since
 * the import reports a file under the name it came under.
 *
 * [images] are the pictures a ChordPro song out of an archive names, found in the same archive (see
 * `ArchiveLocalSource.unpack`), one per file its directives name; empty for every other file.
 *
 * [staged] is a file whose content is in the staging folder rather than in [bytes], which are then empty: an archive
 * received there, or one an archive being unpacked from there holds.
 *
 * [receiving] is an archive that arrived and is still being copied into the staging folder, its [bytes] empty too: the
 * import's preparation awaits it first, and it becomes a [staged] one then.
 *
 * [setlistSongTitles] belong to a setlist translated from another app's set, which names its songs by title rather than
 * by file: the title each entry's song had there, by the file name the entry points at, for an entry whose song the
 * archive it came in did not hold. The import looks for it among the batch's other songs and then in the library by
 * that title, and leaves the entry as a missing song where neither holds one.
 *
 * [isPageText] marks a text that came from a web page or another app as one new song (a paste, a drop, a share), whose
 * page clutter the import crops before it converts the text (`PageCropper`); a text file picked or opened is read whole.
 * [pageMarks] are the structure marks of such a text's lines where the app read the page itself (see
 * [LoadedPage.Read.marks]), null for a paste or a drop.
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray,
    val isTooLarge: Boolean = false,
    val isPlainTextByContent: Boolean = false,
    val images: List<ImportedImage> = emptyList(),
    val staged: StagedFile? = null,
    val receiving: ReceivingArchive? = null,
    val setlistSongTitles: Map<String, String> = emptyMap(),
    val isPageText: Boolean = false,
    val pageMarks: List<PageLineMarks?>? = null,
) {

    /**
     * [name] without its extension, which titles a song that names none itself. A file kept for its text loses only an
     * extension with no space in it: `riverlight.song` is "riverlight", but `Mr. Brightside` is a title whose dot
     * ends an abbreviation.
     */
    val baseName: String
        get() {
            val extension = name.substringAfterLast('.', "")
            val hasExtension = !isPlainTextByContent || (extension.isNotEmpty() && extension.none(Char::isWhitespace))
            return if (hasExtension) name.substringBeforeLast('.') else name
        }

    /**
     * Whether the import unpacks this file as an archive: one [staged] or being [received][receiving], one by its name,
     * or one of any other name but a document's whose bytes are a zip's, since other apps' library backups are zip
     * archives of their own extension, or renamed to a text file's on their way over. Only a document is taken at its
     * name's word, since a Word file is a zip archive of its parts.
     */
    val isArchive: Boolean
        get() = staged != null || receiving != null || LibraryFiles.isArchiveFileName(name) ||
            (!LibraryFiles.isDocumentFileName(name) && LibraryFiles.isZipArchive(bytes))

    override fun equals(other: Any?) = this === other || (
        other is ImportedFile && name == other.name && isTooLarge == other.isTooLarge &&
            isPlainTextByContent == other.isPlainTextByContent && bytes.contentEquals(other.bytes) && images == other.images &&
            staged == other.staged && receiving == other.receiving && setlistSongTitles == other.setlistSongTitles &&
            isPageText == other.isPageText && pageMarks == other.pageMarks
        )

    override fun hashCode() =
        31 * (31 * (31 * (31 * (31 * name.hashCode() + isTooLarge.hashCode()) + isPlainTextByContent.hashCode()) + bytes.contentHashCode()) + images.hashCode()) +
            31 * (31 * staged.hashCode() + receiving.hashCode()) + 31 * setlistSongTitles.hashCode() + 31 * isPageText.hashCode() + pageMarks.hashCode()

    companion object {

        /** [name] as a file nobody read, see the class. */
        fun unread(name: String, isTooLarge: Boolean = false) = ImportedFile(name = name, bytes = ByteArray(0), isTooLarge = isTooLarge)
    }
}
