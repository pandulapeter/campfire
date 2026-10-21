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

/**
 * The data codewords of [data] as one byte mode segment, ended by the terminator (as much of it as fits), filled to a
 * whole byte with zeros and then to the symbol's capacity with the two pad bytes the standard alternates.
 */
internal fun qrDataCodewords(data: ByteArray, version: Int, level: Level): ByteArray {
    val result = ByteArray(qrDataCodewordCount(version, level))
    val capacityBits = result.size * 8
    var length = 0
    fun append(value: Int, count: Int) {
        for (bit in count - 1 downTo 0) {
            if (value ushr bit and 1 != 0) result[length ushr 3] = (result[length ushr 3].toInt() or (0x80 ushr (length and 7))).toByte()
            length++
        }
    }
    append(BYTE_MODE, MODE_INDICATOR_BITS)
    append(data.size, qrCharacterCountBits(version))
    for (byte in data) append(byte.toInt() and 255, 8)
    length += minOf(4, capacityBits - length)
    var pad = FIRST_PAD
    for (index in (length + 7) / 8 until result.size) {
        result[index] = pad.toByte()
        pad = pad xor (FIRST_PAD xor SECOND_PAD)
    }
    return result
}

/**
 * The codewords in the order they are drawn: the data cut into blocks (the later ones a byte longer where the count
 * does not divide evenly), each given its Reed–Solomon codewords, then everything interleaved a byte from each block at
 * a time, the data first.
 */
internal fun qrInterleavedCodewords(data: ByteArray, version: Int, level: Level): ByteArray {
    val blockCount = qrBlockCount(version, level)
    val errorCorrectionLength = qrErrorCorrectionCodewordsPerBlock(version, level)
    val total = qrTotalCodewords(version)
    val shortBlockCount = blockCount - total % blockCount
    val shortDataLength = total / blockCount - errorCorrectionLength
    val divisor = reedSolomonDivisor(errorCorrectionLength)
    val dataBlocks = ArrayList<ByteArray>(blockCount)
    var offset = 0
    repeat(blockCount) { block ->
        val length = shortDataLength + if (block < shortBlockCount) 0 else 1
        dataBlocks += data.copyOfRange(offset, offset + length)
        offset += length
    }
    val errorCorrectionBlocks = dataBlocks.map { reedSolomonRemainder(it, divisor) }
    val result = ByteArray(total)
    var index = 0
    for (column in 0..shortDataLength) {
        for (block in dataBlocks) if (column < block.size) result[index++] = block[column]
    }
    for (column in 0 until errorCorrectionLength) {
        for (block in errorCorrectionBlocks) result[index++] = block[column].toByte()
    }
    return result
}

/** The generator polynomial of [degree], highest coefficient first and its leading 1 left out. */
private fun reedSolomonDivisor(degree: Int): IntArray {
    val result = IntArray(degree)
    result[degree - 1] = 1
    var root = 1
    repeat(degree) {
        for (index in result.indices) {
            result[index] = galoisMultiply(result[index], root)
            if (index + 1 < result.size) result[index] = result[index] xor result[index + 1]
        }
        root = galoisMultiply(root, 2)
    }
    return result
}

private fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): IntArray {
    val result = IntArray(divisor.size)
    for (byte in data) {
        val factor = (byte.toInt() and 255) xor result[0]
        result.copyInto(result, 0, 1)
        result[result.size - 1] = 0
        for (index in result.indices) result[index] = result[index] xor galoisMultiply(divisor[index], factor)
    }
    return result
}

/** Multiplication in GF(2⁸) modulo the standard's polynomial x⁸ + x⁴ + x³ + x² + 1. */
private fun galoisMultiply(x: Int, y: Int): Int {
    var result = 0
    for (bit in 7 downTo 0) {
        result = (result shl 1) xor ((result ushr 7) * 0x11D)
        result = result xor ((y ushr bit and 1) * x)
    }
    return result
}

private const val BYTE_MODE = 0b0100
private const val FIRST_PAD = 0xEC
private const val SECOND_PAD = 0x11
