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
import com.pandulapeter.campfire.presentation.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * The page models (`:libs:pagesong`, [PageModels]) over the weights shipped in `composeResources/files`: a page whose
 * lines came with their structure marks is cropped by the model trained with them, everything else by the plain one.
 * Where no model can run, every page is converted whole, as it was before the model ([songLinesOf] throws, since its null
 * means a page with no song).
 *
 * The title and artist lines are answered apart from the song, for the import to read as its header (`PageTextCrop`).
 * A document's lines are only scored here, as a whole, always by the plain model; the converter crops each of its songs
 * by the scores (`ChordSheetSongCrop`).
 */
@Single
internal class PageCropperImpl : PageCropper {

    private val models = PageModels(readWeights = Res::readBytes)

    private val memo = PageSongLinesMemo { lines ->
        // No model is no reason to say a page holds no song, which would refuse a page read from its address.
        val (pageSong, read) = models.forPage(lines) ?: throw IllegalStateException("The page model is not there")
        PageSongLines.of(withContext(Dispatchers.Default) { pageSong.crop(read, PageLineSource.PAGE) })
    }

    override suspend fun songLinesOf(lines: List<PageLine>): PageSongLines? = memo.songLinesOf(lines)

    override suspend fun documentScoresOf(lines: List<PageLine>): List<PageLineScores>? {
        val pageSong = models.plain() ?: return null
        return withContext(Dispatchers.Default) { pageSong.crop(lines, PageLineSource.DOCUMENT).scores }
    }
}
