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
 * A line's characters as the bucket numbers `model.py`'s `hashed` gives them: the first [maxCharacters] code points of
 * its visible text, each Knuth's multiplicative hash modulo [buckets], from 1 (0 is the padding the model's input fills
 * the rest of the line with, which this leaves out).
 */
internal fun hashedCodesOf(text: String, buckets: Int, maxCharacters: Int): IntArray {
    val codePoints = visibleTextOf(text).codePoints
    return IntArray(minOf(codePoints.size, maxCharacters)) { index ->
        ((codePoints[index] * 2654435761L % 4294967296L) % buckets + 1).toInt()
    }
}

/**
 * The line encoder's convolutions and max-pool for one call's lines. Buffers are reused from line to line, and each
 * bucket's first convolution, the same wherever the character stands, is computed once a call, so one instance serves
 * one call and is never shared between threads.
 *
 * The model runs its convolutions over the whole padded line, where every position past the line's characters holds the
 * padding row's embedding and the convolutions' own padding is zero. So the first convolution past the line's end is its
 * bias and the real neighbours' share, not zero, and the second reads it two positions on: both are computed that way
 * here, over only the positions the pool can reach.
 */
internal class LineEncoder(
    private val weights: PageModelWeights,
) {
    private val channels = weights.channels
    private val maxCharacters = weights.maxCharacters
    private val tapsByBucket = HashMap<Int, FloatArray>()
    private val first = FloatArray(maxCharacters * channels)
    private val second = FloatArray(channels)

    /** Each line's pooled channels, for [channels] floats from [into] in [result]; zeros for a line with no characters. */
    fun encode(codes: IntArray, result: FloatArray, into: Int) {
        val length = codes.size
        result.fill(0f, into, into + channels)
        if (length == 0) return
        val firstEnd = minOf(length + 2, maxCharacters)
        for (position in 0 until firstEnd) {
            val offset = position * channels
            weights.convolution1Bias.copyInto(first, offset)
            for (tap in 0 until 3) {
                val neighbour = position + tap - 1
                if (neighbour < 0 || neighbour >= maxCharacters) continue
                val taps = tapsOf(if (neighbour < length) codes[neighbour] else 0)
                val tapOffset = tap * channels
                for (channel in 0 until channels) first[offset + channel] += taps[tapOffset + channel]
            }
            for (channel in 0 until channels) if (first[offset + channel] < 0f) first[offset + channel] = 0f
        }
        val kernel = weights.convolution2
        for (position in 0 until length) {
            weights.convolution2Bias.copyInto(second)
            for (tap in 0 until 3) {
                val neighbour = position + 2 * (tap - 1)
                if (neighbour < 0 || neighbour >= firstEnd) continue
                val inputOffset = neighbour * channels
                for (input in 0 until channels) {
                    val value = first[inputOffset + input]
                    if (value == 0f) continue
                    val kernelOffset = (tap * channels + input) * channels
                    for (channel in 0 until channels) second[channel] += kernel[kernelOffset + channel] * value
                }
            }
            // After the ReLU every value is at least 0, so a pool starting from 0 is the maximum of the ReLU's outputs.
            for (channel in 0 until channels) if (second[channel] > result[into + channel]) result[into + channel] = second[channel]
        }
    }

    /** The first convolution's three taps (tap × output channel) applied to one bucket's embedding. */
    private fun tapsOf(bucket: Int) = tapsByBucket.getOrPut(bucket) {
        val embeddingSize = weights.embeddingSize
        val kernel = weights.convolution1.values
        val embedding = weights.embedding.values
        val embeddingOffset = bucket * embeddingSize
        FloatArray(3 * channels) { index ->
            val tap = index / channels
            val channel = index % channels
            val kernelOffset = channel * 3 * embeddingSize + tap * embeddingSize
            var sum = 0f
            for (input in 0 until embeddingSize) sum += kernel[kernelOffset + input] * embedding[embeddingOffset + input]
            sum
        }
    }
}
