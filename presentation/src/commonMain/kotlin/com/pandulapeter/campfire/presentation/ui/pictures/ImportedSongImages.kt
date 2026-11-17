/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The pictures an import under review brings, which no shelf keeps until Import, by the name each song's text names
 * them by (the library's name for their content), and the [library]'s for every other name: what its preview shows a
 * song's pictures from. Sizes are read from the bytes ([readSize]): in memory, or read whole from the staging folder
 * ([readStaged]) for a staged one, which is at most a library picture's size, so it is never held longer than a library
 * picture being drawn is; a staged one gone or unreadable reads as missing. A name is a picture's content, so a copy
 * decoded for the library under the same name is the same picture, but the [cacheKey] keeps the two apart all the same.
 */
internal class ImportedSongImages(
    private val pictures: Map<String, IncomingContent>,
    private val library: SongImageLoader,
    private val readSize: suspend (bytes: ByteArray) -> ImageSize?,
    private val readStaged: suspend (file: StagedFile) -> ByteArray?,
) : SongImageLoader {

    private val _sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
    override val sizes = _sizes.asStateFlow()
    override val cacheKey = "import"

    override suspend fun loadSizes(names: Set<String>) {
        val unread = names - _sizes.value.keys
        if (unread.isEmpty()) return
        val (brought, others) = unread.partition { it in pictures }
        if (others.isNotEmpty()) library.loadSizes(others.toSet())
        val read = brought.associateWith { name ->
            bytesOf(pictures.getValue(name))?.let { readSize(it) }?.let { SongImageSize.Known(it.width, it.height) } ?: SongImageSize.Missing
        } + others.associateWith { name -> library.sizes.value[name] ?: SongImageSize.Missing }
        _sizes.update { sizes -> sizes + read }
    }

    override suspend fun loadImage(name: String) = pictures[name]?.let { bytesOf(it) } ?: library.loadImage(name)

    private suspend fun bytesOf(content: IncomingContent) = when (content) {
        is IncomingContent.Bytes -> content.bytes
        is IncomingContent.Staged -> readStaged(content.file)
    }
}
