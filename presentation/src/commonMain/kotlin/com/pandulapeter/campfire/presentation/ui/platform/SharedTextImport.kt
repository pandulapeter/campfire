/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Text that was shared to Campfire rather than a file - a chord sheet selected on a web page, a note - which is
 * a song that has no file yet. It is given a name and sent down the same way a file is, so everything the import
 * does for a file it does for this: the song is named by its own header, a text the library already holds is
 * disregarded, and a name that is taken is asked about.
 *
 * @param subject What the sender called it, which stands in as the file name and so titles the song where the
 *   text declares no `{title}`.
 */
fun sharedTextsToImportedFiles(texts: List<String>, subject: String?): List<ImportedFile> =
    // The user picked Campfire in a share sheet, so a share with nothing in it is still answered: an empty
    // file is one the import reports as skipped.
    texts.ifEmpty { listOf("") }.map(::withoutSourceLink).mapIndexed { index, text ->
        val name = sharedTextName(subject)?.let { if (texts.size > 1) "${it}_${index + 1}" else it } ?: text.firstPlainLine()
        ImportedFile(
            name = (name ?: UNTITLED) + LibraryFiles.TEXT_EXTENSION,
            // A link by itself is not a song. Importing it as one would leave a file to find and delete, and
            // fetching what it points at is not something this app does.
            bytes = if (text.isOnlyLinks()) ByteArray(0) else text.encodeToByteArray(),
        )
    }

/**
 * The text without the address of the page it was selected on. A browser that shares a highlight (Chrome's Share of a
 * selection, a copy with its link) puts the page's address on a line of its own after it, and often wraps the selection
 * in quotation marks: where the last line that is not blank is only a link and the one before it is not, that line is
 * the source rather than part of the song, and goes, with the quotation marks around what is left. A text that is
 * nothing but links, or ends in several of them, is left as it is: there is no telling which of those is the page.
 */
internal fun withoutSourceLink(text: String): String {
    val lines = text.lines().dropLastWhile { it.isBlank() }
    val lastLine = lines.lastOrNull()?.trim() ?: return text
    val previousLine = lines.dropLast(1).lastOrNull { it.isNotBlank() }?.trim() ?: return text
    if (!LINK.matches(lastLine) || LINK.matches(previousLine)) return text
    val remaining = lines.dropLast(1).joinToString("\n").trim()
    val opening = remaining.first()
    val closing = remaining.last()
    return if (remaining.length >= 2 && QUOTATION_MARKS[opening]?.contains(closing) == true) remaining.substring(1, remaining.length - 1).trim() else remaining
}

/** The line is an address and nothing else, which is not part of a song. */
internal fun String.isLink() = LINK.matches(trim())

/** Every line that is not blank is an address, which is not a song and is never fetched. */
internal fun String.isOnlyLinks() = lineSequence().filter { it.isNotBlank() }.let { lines -> lines.any() && lines.all { LINK.matches(it.trim()) } }

/**
 * Whether the text is within what an import reads of one text file, counted in UTF-8 bytes as the file would be: a
 * text over it is refused before anything is prepared rather than reported as an oversized file.
 */
internal fun String.fitsImport() = encodeToByteArray().size <= ImportLimits.MAX_TEXT_FILE_SIZE

/** Each opening quotation mark with the closing ones that end it, in the conventions the app's languages use. */
private val QUOTATION_MARKS = mapOf(
    '"' to "\"",
    '\'' to "'",
    '“' to "”",
    '„' to "”“",
    '«' to "»",
    '»' to "«",
    '‘' to "’",
)
private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private const val UNTITLED = "untitled"
