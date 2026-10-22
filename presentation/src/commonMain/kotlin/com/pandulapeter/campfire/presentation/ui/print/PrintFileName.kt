/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * The name a PDF is offered under. A song's is the one its own header gives it, artist and title, by the rule the
 * library and every other export name a song by ([LibraryFiles.songBaseName]) - whatever its library file happens to
 * be called, a numbered sibling or a name from before its title changed. Several songs that are not a setlist have no
 * title of their own, and are named the way their zip is.
 */
internal fun pdfFileName(source: PrintSource) = exportBaseName(source) + PDF_EXTENSION

/**
 * The name the library's own files are offered under, as the export use cases name them: a song by the rule [pdfFileName]
 * names it, keeping the extension its file is stored with, and a setlist or several songs as a zip named the same way.
 */
internal fun filesFileName(source: PrintSource): String {
    val song = source.songs.singleOrNull()?.takeIf { source.kind == PrintSource.Kind.SONG }
    val extension = if (song == null) LibraryFiles.ARCHIVE_EXTENSION else "." + song.fileName.substringAfterLast('.', LibraryFiles.SONG_EXTENSION.drop(1))
    return exportBaseName(source) + extension
}

private fun exportBaseName(source: PrintSource): String {
    val song = source.songs.singleOrNull()
    return when {
        source.kind == PrintSource.Kind.SETLIST -> LibraryFiles.normalizedName(source.title)
        source.kind == PrintSource.Kind.SONGS -> SONGS_FILE_NAME
        song == null -> LibraryFiles.normalizedName(source.title)
        else -> LibraryFiles.songBaseName(title = song.title, artist = song.artist.orEmpty())
    }
}

/**
 * The name a link's QR code is offered under: the song's or the setlist's own, as [pdfFileName] gives it, with a suffix
 * telling the picture from the PDF beside it.
 */
internal fun qrCodeFileName(source: PrintSource) = pdfFileName(source).removeSuffix(PDF_EXTENSION) + QR_CODE_SUFFIX

private const val QR_CODE_SUFFIX = "-qr_code.png"

/** What several songs exported together are called, as `ExportSongsUseCase` calls their zip. */
private const val SONGS_FILE_NAME = "campfire_songs"
private const val PDF_EXTENSION = ".pdf"
