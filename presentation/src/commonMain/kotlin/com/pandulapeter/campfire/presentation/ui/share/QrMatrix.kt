/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.presentation.ui.share.QrCode.Level
import kotlin.math.abs
import kotlin.math.max

/**
 * The modules of one symbol being built, by row: the function patterns drawn when it is made, the codewords placed
 * once, and then any number of masked copies to choose from, each finished with its own format information.
 */
internal class QrMatrix(private val version: Int) {
    val size = qrSize(version)
    private val modules = BooleanArray(size * size)
    private val isFunction = BooleanArray(size * size)

    init {
        for (index in 0 until size) {
            setFunction(6, index, index % 2 == 0)
            setFunction(index, 6, index % 2 == 0)
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)
        val positions = qrAlignmentPositions(version)
        val last = positions.size - 1
        for (row in positions.indices) {
            for (column in positions.indices) {
                // The three corners already hold finder patterns.
                if (row == 0 && column == 0 || row == 0 && column == last || row == last && column == 0) continue
                drawAlignment(positions[column], positions[row])
            }
        }
        // The format information is reserved now, so that no codeword lands on it, and written per mask.
        drawFormat(modules, 0)
        drawVersion()
    }

    /**
     * Places the codewords' bits in the two-module-wide columns from the bottom right corner, up and down in turn,
     * stepping over the vertical timing pattern and every function module. The remainder bits stay light.
     */
    fun placeCodewords(codewords: ByteArray) {
        var bit = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            val isUpward = (right + 1) and 2 == 0
            for (step in 0 until size) {
                val y = if (isUpward) size - 1 - step else step
                for (x in right downTo right - 1) {
                    val index = y * size + x
                    if (isFunction[index] || bit >= codewords.size * 8) continue
                    modules[index] = codewords[bit ushr 3].toInt() ushr (7 - (bit and 7)) and 1 != 0
                    bit++
                }
            }
            right -= 2
        }
    }

    /** A copy of the modules with [mask] applied to the codewords and the format information of [level] and [mask]. */
    fun masked(level: Level, mask: Int): BooleanArray {
        val result = modules.copyOf()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                if (!isFunction[index] && isMasked(mask, x, y)) result[index] = !result[index]
            }
        }
        drawFormat(result, level.formatBits shl 3 or mask)
        return result
    }

    private fun setFunction(x: Int, y: Int, isDark: Boolean) {
        modules[y * size + x] = isDark
        isFunction[y * size + x] = true
    }

    /** A finder pattern centred on ([centerX], [centerY]), with its light separator where it is inside the symbol. */
    private fun drawFinder(centerX: Int, centerY: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = centerX + dx
                val y = centerY + dy
                val distance = max(abs(dx), abs(dy))
                if (x in 0 until size && y in 0 until size) setFunction(x, y, distance != 2 && distance != 4)
            }
        }
    }

    private fun drawAlignment(centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) setFunction(centerX + dx, centerY + dy, max(abs(dx), abs(dy)) != 1)
        }
    }

    /**
     * The 15 bits of format information (five data bits, a BCH(15, 5) code, and the standard's XOR pattern) in both of
     * their copies, around the top left finder and split between the other two, with the dark module beside them.
     */
    private fun drawFormat(target: BooleanArray, data: Int) {
        var remainder = data
        repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
        val bits = (data shl 10 or remainder) xor 0x5412
        fun set(x: Int, y: Int, bit: Int) {
            target[y * size + x] = bits ushr bit and 1 != 0
            isFunction[y * size + x] = true
        }
        for (bit in 0..5) set(8, bit, bit)
        set(8, 7, 6)
        set(8, 8, 7)
        set(7, 8, 8)
        for (bit in 9..14) set(14 - bit, 8, bit)
        for (bit in 0..7) set(size - 1 - bit, 8, bit)
        for (bit in 8..14) set(8, size - 15 + bit, bit)
        target[(size - 8) * size + 8] = true
        isFunction[(size - 8) * size + 8] = true
    }

    /** From version 7, the version number and its BCH(18, 6) code, beside the top right and bottom left finders. */
    private fun drawVersion() {
        if (version < 7) return
        var remainder = version
        repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
        val bits = version shl 12 or remainder
        for (bit in 0 until 18) {
            val isDark = bits ushr bit and 1 != 0
            val a = size - 11 + bit % 3
            val b = bit / 3
            setFunction(a, b, isDark)
            setFunction(b, a, isDark)
        }
    }
}

/** The rows (and columns) the alignment patterns are centred on, as table E.1 of the standard lists them. */
internal fun qrAlignmentPositions(version: Int): IntArray {
    if (version == 1) return IntArray(0)
    val count = version / 7 + 2
    val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
    val last = qrSize(version) - 7
    return IntArray(count) { if (it == 0) 6 else last - (count - 1 - it) * step }
}

/** The eight data mask patterns, with [x] the column and [y] the row. */
private fun isMasked(mask: Int, x: Int, y: Int) = when (mask) {
    0 -> (x + y) % 2 == 0
    1 -> y % 2 == 0
    2 -> x % 3 == 0
    3 -> (x + y) % 3 == 0
    4 -> (x / 3 + y / 2) % 2 == 0
    5 -> x * y % 2 + x * y % 3 == 0
    6 -> (x * y % 2 + x * y % 3) % 2 == 0
    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
}
