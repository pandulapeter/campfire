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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The library's pictures, read through [getImageSize] and [getImage]. A size read is kept by name for as long as the view
 * model lives: a picture's name is its content, so it never changes under that name, and a song opened again is laid out
 * from it on its first frame. Every name is asked again whenever the library's pictures change ([imageChanges]): one
 * missing may have been added (by sync, or by the placeholder's Add), one shown may have been deleted.
 */
internal class LibrarySongImages(
    scope: CoroutineScope,
    private val getImageSize: suspend (name: String) -> ImageSize?,
    private val getImage: suspend (name: String) -> ByteArray?,
    imageChanges: StateFlow<Long>,
) : SongImageLoader {

    private val _sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
    override val sizes = _sizes.asStateFlow()
    override val cacheKey = "library"

    override suspend fun loadSizes(names: Set<String>) {
        val unread = names - _sizes.value.keys
        if (unread.isEmpty()) return
        val read = unread.associateWith { name -> sizeOf(name) }
        _sizes.update { sizes -> sizes + read }
    }

    override suspend fun loadImage(name: String) = getImage(name)

    private suspend fun sizeOf(name: String) = getImageSize(name)?.let { SongImageSize.Known(it.width, it.height) } ?: SongImageSize.Missing

    init {
        scope.launch {
            imageChanges.drop(1).collect {
                val known = _sizes.value.keys
                val read = known.associateWith { name -> sizeOf(name) }
                _sizes.update { sizes -> sizes + read }
            }
        }
    }
}
