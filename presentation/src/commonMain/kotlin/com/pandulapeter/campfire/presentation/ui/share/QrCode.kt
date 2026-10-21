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

/**
 * A QR code symbol (ISO/IEC 18004) holding one byte mode segment, written from the standard rather than taken from a
 * library: none publishes a variant for every platform the app builds, and a link needs a small part of what one does.
 *
 * The text goes in as UTF-8 without an ECI header. The standard's default reading is ISO 8859-1, but every phone camera
 * guesses UTF-8 from the bytes, while an ECI header is something several readers fail on; the links the app encodes are
 * ASCII anyway, their titles percent-encoded.
 */
internal class QrCode private constructor(
    val version: Int,
    val level: Level,
    val mask: Int,
    private val modules: BooleanArray,
) {
    /** Modules on each side, without the quiet zone a reader needs around them. */
    val size = qrSize(version)

    /** Whether the module in column [x] and row [y] is dark. */
    fun isDark(x: Int, y: Int) = modules[y * size + x]

    /** The error correction levels from the weakest to the strongest, with the two bits the format information gives each. */
    enum class Level(val formatBits: Int) {
        L(1),
        M(0),
        Q(3),
        H(2),
    }

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 40
        const val MASK_COUNT = 8

        /**
         * The smallest symbol that holds [text] at [minimumLevel] at least, raised to the strongest level that still fits
         * in that size, or null when not even version 40 holds it.
         */
        fun encode(text: String, minimumLevel: Level = Level.M): QrCode? {
            val data = text.encodeToByteArray()
            val version = (MIN_VERSION..MAX_VERSION).firstOrNull { qrByteCapacity(it, minimumLevel) >= data.size } ?: return null
            val level = Level.entries.last { it >= minimumLevel && qrByteCapacity(version, it) >= data.size }
            return encode(data = data, version = version, level = level, mask = null)
        }

        /**
         * The symbol of [data] at exactly [version] and [level], which must hold it. A null [mask] picks the one the
         * standard's penalty rules score lowest; any of the eight reads the same, so only tests force one.
         */
        fun encode(data: ByteArray, version: Int, level: Level, mask: Int?): QrCode {
            require(version in MIN_VERSION..MAX_VERSION) { "No QR code version $version" }
            require(data.size <= qrByteCapacity(version, level)) { "${data.size} bytes don't fit version $version-$level" }
            require(mask == null || mask in 0 until MASK_COUNT) { "No QR code mask $mask" }
            val matrix = QrMatrix(version)
            matrix.placeCodewords(qrInterleavedCodewords(qrDataCodewords(data, version, level), version, level))
            val chosen = mask ?: (0 until MASK_COUNT).minBy { qrPenalty(matrix.masked(level, it), matrix.size) }
            return QrCode(version, level, chosen, matrix.masked(level, chosen))
        }
    }
}
