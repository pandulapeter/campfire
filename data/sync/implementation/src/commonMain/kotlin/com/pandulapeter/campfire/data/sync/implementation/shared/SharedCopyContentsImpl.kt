/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import org.koin.core.annotation.Single

@Single
internal class SharedCopyContentsImpl(
    private val setlistLocalSource: SetlistLocalSource,
    private val songLocalSource: SongLocalSource,
    private val archiveLocalSource: ArchiveLocalSource,
) : SharedCopyContents {

    override suspend fun setlistSongFileNames(bytes: ByteArray) =
        setlistLocalSource.parseSetlist(bytes.decodeToString())?.setlist?.entries?.map { it.songFileName }

    override suspend fun titleOf(kind: SharedLinkKind, fileName: String, bytes: ByteArray) = when (kind) {
        SharedLinkKind.SETLIST -> setlistLocalSource.parseSetlist(bytes.decodeToString())?.setlist?.title
        // Read from the file again rather than parsed from the bytes, since parsing a song is the song source's
        // business; the file is the one just packed, and a title read a moment later only names it.
        SharedLinkKind.SONG -> songLocalSource.loadSong(fileName)?.title
    }

    override fun libraryFileNamesOf(bytes: ByteArray) = libraryFilesNamedBy(bytes.decodeLibraryText())

    override fun pictureNamesOf(bytes: ByteArray) = ChordProImages.referencedSources(bytes.decodeLibraryText())

    override fun attachmentNamesOf(bytes: ByteArray) = ChordProAttachments.referencedSources(bytes.decodeLibraryText()).toList()

    override fun withoutAttachments(bytes: ByteArray, sources: Set<String>): ByteArray {
        val text = bytes.decodeLibraryText()
        val stripped = ChordProAttachments.withoutAttachments(text, sources)
        return if (stripped === text) bytes else stripped.encodeToByteArray()
    }

    override suspend fun pack(files: Map<String, ByteArray>) = archiveLocalSource.pack(files = files, isDated = false)
}
