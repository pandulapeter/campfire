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

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PictureWorkTest {

    private val work = PictureWork()

    @Test
    fun `work that takes a while is shown once it has run a moment, and no longer once it ends`() = runTest {
        val shown = collectShown()
        val done = CompletableDeferred<Unit>()
        launch { work.track { done.await() } }
        advanceTimeBy(PictureWork.SHOW_DELAY - 1.milliseconds)
        assertEquals(listOf(false), shown)
        advanceTimeBy(2.milliseconds)
        assertEquals(listOf(false, true), shown)
        done.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true, false), shown)
    }

    @Test
    fun `work of a moment is never shown`() = runTest {
        val shown = collectShown()
        work.track { delay(PictureWork.SHOW_DELAY / 2) }
        advanceTimeBy(5.seconds)
        assertEquals(listOf(false), shown)
    }

    @Test
    fun `overlapping work is shown until the last piece of it ends`() = runTest {
        val shown = collectShown()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        launch { work.track { first.await() } }
        launch { work.track { second.await() } }
        advanceTimeBy(PictureWork.SHOW_DELAY + 1.milliseconds)
        first.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true), shown)
        second.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true, false), shown)
    }

    @Test
    fun `work that fails still ends`() = runTest {
        val shown = collectShown()
        launch { runCatching { work.track { delay(1.seconds); error("The decoder gave up.") } } }
        advanceTimeBy(5.seconds)
        assertEquals(listOf(false, true, false), shown)
    }

    @Test
    fun `a picture being re-encoded is work of the fitter`() = runTest {
        val shown = collectShown()
        val reencoded = CompletableDeferred<ByteArray?>()
        val fitter = PictureFitter(
            examine = { ImageIntake.Refused(ImageRefusal.TOO_MANY_PIXELS) },
            reencode = { _, _, _ -> reencoded.await() },
            work = work,
        )
        launch { fitter.fit(byteArrayOf(1)) }
        advanceTimeBy(1.seconds)
        assertEquals(listOf(false, true), shown)
        reencoded.complete(null)
        runCurrent()
        assertEquals(listOf(false, true, false), shown)
    }

    private fun TestScope.collectShown(): List<Boolean> {
        val shown = mutableListOf<Boolean>()
        backgroundScope.launch { work.isShown.collect { shown += it } }
        runCurrent()
        return shown
    }
}
