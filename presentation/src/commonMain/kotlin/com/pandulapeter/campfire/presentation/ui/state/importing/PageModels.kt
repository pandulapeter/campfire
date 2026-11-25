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

import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The two page models the app ships, each built from its weights ([readWeights] of [PLAIN_WEIGHTS] or [MARKED_WEIGHTS])
 * on its first need and kept for the process. One model trained with structure marks crops a page read with them better
 * but a paste and a document worse, so the plain one, trained without, reads everything that has none (a paste, a drop,
 * a share, a document's lines, every page of the web build), and the marked one only a page whose lines came with their
 * marks, read the first time such a page arrives. The weights are a few megabytes, so they are read and parsed off the
 * main thread; a read that fails is not tried again.
 */
internal class PageModels(
    private val readWeights: suspend (path: String) -> ByteArray,
) {
    private val plain = LazyPageSong(PLAIN_WEIGHTS)
    private val marked = LazyPageSong(MARKED_WEIGHTS)

    /** The plain model, or null where its weights could not be read. */
    suspend fun plain(): PageSong? = plain.get()

    /**
     * The model to crop [lines] with and the lines as it reads them: the marked model where any of them came with its
     * marks (`pageTextLinesOf` hands them over only where they are those of the text's own lines), else the plain one. A
     * page with marks whose marked model cannot be read is cropped by the plain one as a paste, its marks left out, since
     * that model never saw a mark in training and its inputs for them are noise. Null where no model can run.
     */
    suspend fun forPage(lines: List<PageLine>): Pair<PageSong, List<PageLine>>? {
        val markedLines = lines.count { it.marks != null }
        if (markedLines > 0) {
            marked.get()?.let { pageSong ->
                println("Page crop: the marked model, $markedLines of ${lines.size} lines marked")
                return pageSong to lines
            }
            val pageSong = plain.get() ?: return null
            println("Page crop: the plain model, the marked one could not be read")
            return pageSong to lines.map { it.copy(marks = null) }
        }
        val pageSong = plain.get() ?: return null
        println("Page crop: the plain model, no structure marks")
        return pageSong to lines
    }

    /** One model's weights, read on the first [get] only, whether that read succeeds or not. */
    private inner class LazyPageSong(private val path: String) {
        private val mutex = Mutex()
        private var pageSong: PageSong? = null
        private var isRead = false

        suspend fun get() = mutex.withLock {
            if (!isRead) {
                pageSong = try {
                    withContext(Dispatchers.Default) { PageSong(readWeights(path)) }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not read the page model $path: ${exception.message}")
                    null
                }
                isRead = true
            }
            pageSong
        }
    }

    companion object {
        const val PLAIN_WEIGHTS = "files/page-model.bin"
        const val MARKED_WEIGHTS = "files/page-model-marked.bin"
    }
}
