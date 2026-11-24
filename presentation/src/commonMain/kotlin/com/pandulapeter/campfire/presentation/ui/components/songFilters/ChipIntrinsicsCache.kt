/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The chips' widths and line height of each [CollapsibleChipFlow], worked out once while [FilterGroupsLayout] asks its
 * groups both of their intrinsic heights for one measure, rather than by every intrinsic query again: each walks every
 * chip's text, and the four walks were half of the lookahead measure of a sheet opening.
 *
 * Kept only for that one measure ([during]): a count that moves ("Rock 12" to "Rock 13") changes a chip's width without
 * changing anything a longer-lived key could see, and a parent asking again while Compose skips a group's own measure
 * would be answered from widths gone stale. Outside it every query walks the chips afresh.
 */
internal class ChipIntrinsicsCache {

    private var walks: MutableMap<Pair<Any, Int>, ChipWalk>? = null

    /** Runs [block] with the cache open, every walk of it forgotten once it returns. */
    fun <T> during(block: () -> T): T {
        walks = mutableMapOf()
        try {
            return block()
        } finally {
            walks = null
        }
    }

    /** The walk of [group]'s chips at [lineWidth]: [walk]'s, shared by every query while the cache is open. */
    fun walkOf(group: Any, lineWidth: Int, walk: () -> ChipWalk): ChipWalk = walks?.getOrPut(group to lineWidth, walk) ?: walk()
}

/** What a group's intrinsic heights are worked out from: each chip's width on one line, and the tallest chip's height. */
internal data class ChipWalk(val widths: List<Int>, val lineHeight: Int)

/** The cache of the [FilterGroupsLayout] a group is in; null outside one, where nothing is cached. */
internal val LocalChipIntrinsicsCache = staticCompositionLocalOf<ChipIntrinsicsCache?> { null }
