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

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The committed images the golden renders are compared with, in [directory], and [output], where a run leaves what it
 * rendered (`actual/`) and, for each image that changed, where it changed (`diff/`).
 *
 * The goldens are recorded and compared on Linux, which is what CI runs: text is rasterized by each system's own font
 * engine, so a Mac draws the same layout with pixels a little different everywhere there is text. Elsewhere a run
 * renders every case, which still fails on a case that cannot be driven, and only leaves the images in [output].
 */
internal class Goldens(private val directory: File, private val output: File, private val isUpdating: Boolean) {
    private val isComparing = System.getProperty("os.name").startsWith("Linux")

    init {
        check(!isUpdating || isComparing) { "The goldens are recorded on Linux; on another system, take them from a CI run's goldens/actual." }
        output.deleteRecursively()
    }

    /** Why [image] is not what the golden called [name] shows, or null where it is (or where nothing is compared). */
    fun check(name: String, actual: BufferedImage): String? {
        write(actual, File(output, "actual/$name.png"))
        val golden = File(directory, "$name.png")
        return when {
            !isComparing -> null
            isUpdating -> null.also { write(actual, golden) }
            !golden.isFile -> "$name has no golden."
            else -> difference(name, ImageIO.read(golden), actual)
        }
    }

    /** Whether the goldens were compared at all, for the run to say when they were not. */
    val wereCompared get() = isComparing

    private fun difference(name: String, golden: BufferedImage, actual: BufferedImage): String? {
        if (golden.width != actual.width || golden.height != actual.height) {
            return "$name is ${actual.width}×${actual.height}, its golden ${golden.width}×${golden.height}."
        }
        val diff = BufferedImage(golden.width, golden.height, BufferedImage.TYPE_INT_RGB)
        var differing = 0
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                val expected = golden.getRGB(x, y)
                if (expected.differsFrom(actual.getRGB(x, y))) {
                    differing++
                    diff.setRGB(x, y, DIFF_COLOR)
                } else {
                    diff.setRGB(x, y, expected.faded())
                }
            }
        }
        if (differing <= MAX_DIFFERING_PIXELS) return null
        write(diff, File(output, "diff/$name.png"))
        return "$name differs from its golden in $differing pixels."
    }

    private fun write(image: BufferedImage, file: File) {
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }
}

/**
 * Whether two pixels differ by more than anti-aliasing does between two runs: any channel, alpha included, by more
 * than [CHANNEL_TOLERANCE].
 */
private fun Int.differsFrom(other: Int) = (0 until 32 step 8).any { shift -> abs((this shr shift and 0xFF) - (other shr shift and 0xFF)) > CHANNEL_TOLERANCE }

/** The pixel a third as strong, over white, so that the changed pixels of a diff stand out from the screen around them. */
private fun Int.faded() = (0 until 24 step 8).sumOf { shift -> (255 - (255 - (this shr shift and 0xFF)) / 3) shl shift }

private const val CHANNEL_TOLERANCE = 8
private const val MAX_DIFFERING_PIXELS = 16
private const val DIFF_COLOR = 0xFF0000
