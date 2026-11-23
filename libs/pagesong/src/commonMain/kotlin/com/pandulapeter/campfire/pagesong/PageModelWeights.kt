/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/**
 * The page model's weights as `tools/page-model/export.py` writes them (its docstring is the format), dequantized once
 * into floats. The convolutions' kernels are rearranged for the loops that read them: the first's as output channel, tap,
 * input channel (each bucket's taps are computed once a call), the second's as tap, input channel, output channel, so a
 * character position adds each input times a contiguous column, which the platforms' compilers vectorize, and skips the
 * inputs the ReLU left at zero.
 */
internal class PageModelWeights(
    bytes: ByteArray,
) {
    private val reader = Reader(bytes)
    val buckets: Int
    val maxCharacters: Int
    val embeddingSize: Int
    val channels: Int
    val lineSize: Int
    val hidden: Int
    val embedding: Matrix
    val convolution1: Matrix
    val convolution1Bias: FloatArray
    val convolution2: FloatArray
    val convolution2Bias: FloatArray
    val line: Matrix
    val lineBias: FloatArray

    /** The GRU's four passes: layer 1 forward and backward, then layer 2's. */
    val sequence: List<GruWeights>
    val output: Matrix
    val outputBias: FloatArray

    init {
        require(bytes.size >= HEADER_SIZE && bytes.decodeToString(0, 4) == MAGIC) { "Not a page model's weights." }
        reader.position = 4
        val version = reader.int()
        require(version == VERSION) { "Page model weights of version $version, not $VERSION." }
        buckets = reader.int()
        maxCharacters = reader.int()
        embeddingSize = reader.int()
        channels = reader.int()
        lineSize = reader.int()
        val features = reader.int()
        hidden = reader.int()
        val labels = reader.int()
        require(features == FEATURE_COUNT && labels == PageLineLabel.entries.size) {
            "Page model weights for $features features and $labels labels, not $FEATURE_COUNT and ${PageLineLabel.entries.size}."
        }
        require(listOf(buckets, maxCharacters, embeddingSize, channels, lineSize, hidden).all { it in 1..MAX_DIMENSION }) {
            "Page model weights of an impossible shape."
        }
        embedding = reader.matrix(buckets + 1, embeddingSize)
        convolution1 = reader.matrix(channels, embeddingSize * 3).byTap(embeddingSize)
        convolution1Bias = reader.bias(channels)
        convolution2 = reader.matrix(channels, channels * 3).let { kernel ->
            FloatArray(kernel.values.size) { index ->
                val tap = index / (channels * channels)
                val input = index / channels % channels
                kernel.values[(index % channels) * kernel.columns + input * 3 + tap]
            }
        }
        convolution2Bias = reader.bias(channels)
        line = reader.matrix(lineSize, channels + FEATURE_COUNT)
        lineBias = reader.bias(lineSize)
        sequence = listOf(lineSize, lineSize, hidden * 2, hidden * 2).map { input ->
            GruWeights(
                input = reader.matrix(hidden * 3, input),
                state = reader.matrix(hidden * 3, hidden),
                inputBias = reader.bias(hidden * 3),
                stateBias = reader.bias(hidden * 3),
            )
        }
        output = reader.matrix(labels, hidden * 2)
        outputBias = reader.bias(labels)
        require(reader.position == bytes.size) { "Page model weights of ${bytes.size} bytes, not the ${reader.position} their shape needs." }
    }

    /** One GRU pass's matrices, its rows the reset, update, and new gates in PyTorch's order. */
    class GruWeights(
        val input: Matrix,
        val state: Matrix,
        val inputBias: FloatArray,
        val stateBias: FloatArray,
    )

    /** A row-major float matrix. */
    class Matrix(
        val rows: Int,
        val columns: Int,
        val values: FloatArray,
    ) {
        /** Adds this matrix times [vector] (read from [from]) to [result] (written from [into]). */
        fun multiplyAdd(vector: FloatArray, from: Int, result: FloatArray, into: Int) {
            var offset = 0
            for (row in 0 until rows) {
                var sum = 0f
                for (column in 0 until columns) sum += values[offset + column] * vector[from + column]
                result[into + row] += sum
                offset += columns
            }
        }

        /** PyTorch's kernel row (input channel × 3 + tap) rearranged as tap × inputs + input channel. */
        fun byTap(inputs: Int) = Matrix(
            rows = rows,
            columns = columns,
            values = FloatArray(values.size) { index ->
                val row = index / columns
                val tap = index % columns / inputs
                val input = index % inputs
                values[row * columns + input * 3 + tap]
            },
        )
    }

    private class Reader(
        val bytes: ByteArray,
    ) {
        var position = 0

        fun int(): Int {
            require(position + 4 <= bytes.size) { "Page model weights cut short." }
            val value = (bytes[position].toInt() and 0xFF) or
                ((bytes[position + 1].toInt() and 0xFF) shl 8) or
                ((bytes[position + 2].toInt() and 0xFF) shl 16) or
                (bytes[position + 3].toInt() shl 24)
            position += 4
            return value
        }

        fun float() = Float.fromBits(int())

        fun bias(size: Int) = FloatArray(size) { float() }

        /** One scale a row, then the rows' int8 values, each value its row's scale times it. */
        fun matrix(rows: Int, columns: Int): Matrix {
            val scales = bias(rows)
            require(position.toLong() + rows.toLong() * columns <= bytes.size) { "Page model weights cut short." }
            val values = FloatArray(rows * columns) { index -> scales[index / columns] * bytes[position + index] }
            position += rows * columns
            return Matrix(rows, columns, values)
        }
    }

    private companion object {
        const val MAGIC = "CFPM"
        const val VERSION = 1
        const val HEADER_SIZE = 4 + 4 + 8 * 4
        const val MAX_DIMENSION = 1 shl 16
    }
}
