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
import com.pandulapeter.campfire.domain.api.importing.PageSongLines
import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineScores
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
 * tried again, and every page is then converted whole, as it was before the model ([songLinesOf] throws, since its null
 * means a page with no song).
 *
 * The title and artist lines are answered apart from the song, for the import to read as its header (`PageTextCrop`).
 * A document's lines are only scored here, as a whole; the converter crops each of its songs by the scores
 * (`ChordSheetSongCrop`).
 */
@Single
internal class PageCropperImpl : PageCropper {

    private val mutex = Mutex()
    private var pageSong: PageSong? = null
    private var isRead = false

    override suspend fun songLinesOf(lines: List<String>): PageSongLines? {
        // No model is no reason to say a page holds no song, which would refuse a page read from its address.
        val pageSong = pageSong() ?: throw IllegalStateException("The page model is not there")
        val crop = withContext(Dispatchers.Default) { pageSong.crop(lines.map(::PageLine), PageLineSource.PAGE) }
        if (!crop.holdsSong) return null
        return PageSongLines(
            keptLines = crop.keptLines,
            titleLine = crop.titleLine,
            artistLine = crop.artistLine,
            headerLines = crop.headerLines,
        )
    }

    override suspend fun documentScoresOf(lines: List<PageLine>): List<PageLineScores>? {
        val pageSong = pageSong() ?: return null
        return withContext(Dispatchers.Default) { pageSong.crop(lines, PageLineSource.DOCUMENT).scores }
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
