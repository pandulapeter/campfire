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

/** The model's probability of each [PageLineLabel] for one line, summing to 1. */
public class PageLineScores(
    private val probabilities: FloatArray,
) {
    init {
        require(probabilities.size == PageLineLabel.entries.size) { "One probability a label is needed." }
    }

    /** The most probable label, the first of them on a tie. */
    public val label: PageLineLabel
        get() = PageLineLabel.entries.maxBy { this[it] }

    public operator fun get(label: PageLineLabel): Float = probabilities[label.ordinal]

    override fun equals(other: Any?): Boolean = other is PageLineScores && probabilities.contentEquals(other.probabilities)

    override fun hashCode(): Int = probabilities.contentHashCode()

    override fun toString(): String = PageLineLabel.entries.joinToString(prefix = "PageLineScores(", postfix = ")") { "$it=${this[it]}" }
}
