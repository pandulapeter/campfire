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

/** Modules on each side of a symbol of [version]. */
internal fun qrSize(version: Int) = version * 4 + 17

/** The bytes a byte mode segment can carry in a symbol of [version] at [level]. */
internal fun qrByteCapacity(version: Int, level: Level) =
    (qrDataCodewordCount(version, level) * 8 - MODE_INDICATOR_BITS - qrCharacterCountBits(version)) / 8

/** The width of a byte mode segment's length field, which grows once the versions can hold more than 255 bytes. */
internal fun qrCharacterCountBits(version: Int) = if (version <= 9) 8 else 16

/** Every codeword of [version]: the modules no function pattern takes, in whole bytes, the remainder bits left over. */
internal fun qrTotalCodewords(version: Int): Int {
    var modules = (16 * version + 128) * version + 64
    if (version >= 2) {
        val alignments = version / 7 + 2
        modules -= (25 * alignments - 10) * alignments - 55
        if (version >= 7) modules -= 36
    }
    return modules / 8
}

internal fun qrDataCodewordCount(version: Int, level: Level) =
    qrTotalCodewords(version) - qrErrorCorrectionCodewordsPerBlock(version, level) * qrBlockCount(version, level)

internal fun qrErrorCorrectionCodewordsPerBlock(version: Int, level: Level) = ERROR_CORRECTION_CODEWORDS_PER_BLOCK[level.ordinal][version]

internal fun qrBlockCount(version: Int, level: Level) = BLOCK_COUNTS[level.ordinal][version]

internal const val MODE_INDICATOR_BITS = 4

/** Table 9 of the standard, by level (L, M, Q, H) and then by version, the first column standing for no version. */
private val ERROR_CORRECTION_CODEWORDS_PER_BLOCK = arrayOf(
    intArrayOf(
        -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
    ),
    intArrayOf(
        -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    ),
    intArrayOf(
        -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
    ),
    intArrayOf(
        -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
    ),
)

/** Table 9 of the standard again: the error correction blocks each symbol is cut into. */
private val BLOCK_COUNTS = arrayOf(
    intArrayOf(
        -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20,
        21, 22, 24, 25,
    ),
    intArrayOf(
        -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37,
        38, 40, 43, 45, 47, 49,
    ),
    intArrayOf(
        -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48,
        51, 53, 56, 59, 62, 65, 68,
    ),
    intArrayOf(
        -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57,
        60, 63, 66, 70, 74, 77, 81,
    ),
)
