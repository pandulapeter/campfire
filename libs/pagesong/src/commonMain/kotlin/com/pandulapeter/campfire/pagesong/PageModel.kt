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

import kotlin.math.exp
import kotlin.math.tanh

/**
 * `model.py`'s `PageModel` forward pass in plain arrays: each line encoded from its characters and features, then a
 * two-layer bidirectional GRU across the lines, then one logit a label. Holds no state between calls.
 */
internal class PageModel(
    private val weights: PageModelWeights,
) {
    /** The logits of each line, a row of [PageLineLabel]s' count each. */
    fun logitsOf(lines: List<PageLine>, source: PageLineSource): List<FloatArray> {
        if (lines.isEmpty()) return emptyList()
        var sequence = linesOf(lines, source)
        var width = weights.lineSize
        for (layer in 0 until 2) {
            val next = FloatArray(lines.size * weights.hidden * 2)
            run(weights.sequence[layer * 2], sequence, width, lines.size, next, isReversed = false)
            run(weights.sequence[layer * 2 + 1], sequence, width, lines.size, next, isReversed = true)
            sequence = next
            width = weights.hidden * 2
        }
        return List(lines.size) { index ->
            weights.outputBias.copyOf().also { weights.output.multiplyAdd(sequence, index * width, it, 0) }
        }
    }

    /** Each line's vector for the GRU: its pooled characters beside its features, through the line layer and a ReLU. */
    private fun linesOf(lines: List<PageLine>, source: PageLineSource): FloatArray {
        val channels = weights.channels
        val encoder = LineEncoder(weights)
        val pooledByText = HashMap<String, FloatArray>()
        val input = FloatArray(channels + FEATURE_COUNT)
        val result = FloatArray(lines.size * weights.lineSize)
        lines.forEachIndexed { index, line ->
            // Pages repeat lines (a chorus, a menu, blank lines), and the characters' share is the costly one.
            val pooled = pooledByText.getOrPut(line.text) {
                FloatArray(channels).also { encoder.encode(hashedCodesOf(line.text, weights.buckets, weights.maxCharacters), it, 0) }
            }
            pooled.copyInto(input)
            lineFeaturesOf(line, source).copyInto(input, channels)
            val offset = index * weights.lineSize
            weights.lineBias.copyInto(result, offset)
            weights.line.multiplyAdd(input, 0, result, offset)
            for (position in offset until offset + weights.lineSize) if (result[position] < 0f) result[position] = 0f
        }
        return result
    }

    /**
     * One GRU pass over [count] rows of [width] in [input], in PyTorch's equations (reset, update, and new gates; the new
     * gate's state share reset after its bias), writing its states to its half of each row of [output].
     */
    private fun run(gru: PageModelWeights.GruWeights, input: FloatArray, width: Int, count: Int, output: FloatArray, isReversed: Boolean) {
        val hidden = weights.hidden
        val gates = hidden * 3
        val fromInput = FloatArray(count * gates)
        for (index in 0 until count) {
            gru.inputBias.copyInto(fromInput, index * gates)
            gru.input.multiplyAdd(input, index * width, fromInput, index * gates)
        }
        val state = FloatArray(hidden)
        val fromState = FloatArray(gates)
        for (step in 0 until count) {
            val index = if (isReversed) count - 1 - step else step
            gru.stateBias.copyInto(fromState)
            gru.state.multiplyAdd(state, 0, fromState, 0)
            val offset = index * gates
            for (unit in 0 until hidden) {
                val reset = sigmoid(fromInput[offset + unit] + fromState[unit])
                val update = sigmoid(fromInput[offset + hidden + unit] + fromState[hidden + unit])
                val candidate = tanh(fromInput[offset + 2 * hidden + unit] + reset * fromState[2 * hidden + unit])
                state[unit] = (1f - update) * candidate + update * state[unit]
            }
            state.copyInto(output, index * hidden * 2 + if (isReversed) hidden else 0)
        }
    }

    private fun sigmoid(value: Float) = 1f / (1f + exp(-value))
}
