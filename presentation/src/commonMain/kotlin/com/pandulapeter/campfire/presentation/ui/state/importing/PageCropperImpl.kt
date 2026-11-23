/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.domain.api.importing.PageCropper
import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineSource
import com.pandulapeter.campfire.pagesong.PageSong
import com.pandulapeter.campfire.presentation.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * The page model (`:libs:pagesong`) over the weights shipped in `composeResources/files`. They are a few megabytes, so
 * they are read and parsed on the first page cropped rather than at launch, once per process: a read that fails is not
 * tried again, and every page is then converted whole, as it was before the model.
 *
 * The title and artist lines are kept with the song even where the model does not think them part of it, so the
 * converter's own reading of a song's header still finds them.
 */
@Single
internal class PageCropperImpl : PageCropper {

    private val mutex = Mutex()
    private var pageSong: PageSong? = null
    private var isRead = false

    override suspend fun songLinesOf(lines: List<String>): List<Int>? {
        val pageSong = pageSong() ?: return null
        val crop = withContext(Dispatchers.Default) { pageSong.crop(lines.map(::PageLine), PageLineSource.PAGE) }
        return if (crop.holdsSong) (crop.keptLines + listOfNotNull(crop.titleLine, crop.artistLine)).distinct().sorted() else null
    }

    private suspend fun pageSong() = mutex.withLock {
        if (!isRead) {
            pageSong = try {
                withContext(Dispatchers.Default) { PageSong(Res.readBytes(WEIGHTS)) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not read the page model: ${exception.message}")
                null
            }
            isRead = true
        }
        pageSong
    }

    private companion object {
        const val WEIGHTS = "files/page-model.bin"
    }
}
