/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tells when a screen has settled after something was asked of it, from frames drawn [SAMPLE_STEP_MILLIS] of the scene's
 * clock apart: first it waits up to the expected change's window for the screen to change at all, since what an action
 * starts may land from another thread a while later and draw nothing until it does, then until no change has shown for
 * [QUIET_MILLIS]. A change is measured on a grayscale thumbnail of one sample a [CELL_DP] square, as the number of
 * samples that moved, so that the first wait notices a switch being toggled while the second is not kept busy by a
 * caret blinking or a progress indicator spinning. The wait never fails a shot: after [SETTLE_TIMEOUT_MILLIS] the screen
 * is taken as it is.
 *
 * [step] moves the scene's clock by a step, lets as much real time pass for the app's own coroutines, and draws the
 * frame, which the caller closes.
 */
internal class SettleDetector(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    private val step: suspend (milliseconds: Long) -> Image,
) : AutoCloseable {
    private val thumbnail = Bitmap().apply {
        val cellPx = CELL_DP * density
        allocPixels(
            ImageInfo(
                (widthPx / cellPx).roundToInt().coerceAtLeast(1),
                (heightPx / cellPx).roundToInt().coerceAtLeast(1),
                ColorType.GRAY_8,
                ColorAlphaType.OPAQUE,
            ),
        )
    }
    private var last: ByteArray? = null

    /**
     * Waits until the screen has settled, the first change expected within [expectChangeMillis]. The frame this compares
     * against at first is the last one it drew, from before the action, so a change that is already on the first frame
     * drawn after it counts.
     */
    suspend fun awaitSettled(expectChangeMillis: Long = EXPECT_CHANGE_MILLIS) {
        var elapsed = 0L
        suspend fun next(): ByteArray = sample().also { elapsed += SAMPLE_STEP_MILLIS }
        var reference = last ?: next()
        while (elapsed < expectChangeMillis) {
            val frame = next()
            if (frame.changedCells(reference) >= FIRST_CHANGE_CELLS) {
                reference = frame
                break
            }
        }
        // Compared with the frame the last change ended on rather than with the one before, so that an animation easing
        // out by less than a change a step still counts once it has moved far enough.
        var quiet = 0L
        while (quiet < QUIET_MILLIS && elapsed < SETTLE_TIMEOUT_MILLIS) {
            val frame = next()
            if (frame.changedCells(reference) >= QUIET_CELLS) {
                reference = frame
                quiet = 0L
            } else {
                quiet += SAMPLE_STEP_MILLIS
            }
        }
    }

    private suspend fun sample(): ByteArray = step(SAMPLE_STEP_MILLIS).use { image ->
        // Mipmapped, so that each sample is the average of its cell rather than the one pixel nearest its middle.
        image.scalePixels(checkNotNull(thumbnail.peekPixels()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), false)
        checkNotNull(thumbnail.readPixels()).also { last = it }
    }

    private fun ByteArray.changedCells(other: ByteArray) = indices.count { abs((this[it].toInt() and 0xFF) - (other[it].toInt() and 0xFF)) > CELL_DELTA }

    override fun close() = thumbnail.close()
}

/** How long an action is expected to take to show anything, after which the screen is taken to have nothing to show. */
internal const val EXPECT_CHANGE_MILLIS = 500L

/** The same for the app's start and the library's load, which read files and lay out the whole first screen. */
internal const val EXPECT_START_MILLIS = 1_000L

internal const val SAMPLE_STEP_MILLIS = 50L
private const val QUIET_MILLIS = 250L
private const val SETTLE_TIMEOUT_MILLIS = 10_000L
private const val CELL_DP = 4
private const val CELL_DELTA = 8
private const val FIRST_CHANGE_CELLS = 4
private const val QUIET_CELLS = 64
