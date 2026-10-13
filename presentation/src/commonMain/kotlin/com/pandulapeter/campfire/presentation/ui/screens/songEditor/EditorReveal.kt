/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import kotlin.math.roundToInt

/**
 * Where to scroll the editor's field so that the line from [lineTop] to [lineBottom] is inside the top [uncoveredHeight]
 * of the pane, the part a sheet over the field leaves uncovered, or null where the line is there already (a match on the
 * screen does not move the text under the eye that has found it) or where there is no room at all. The line is put
 * [margin] below the top of that part, but never more than a third of it down, since a narrow strip has no room to spare.
 */
internal fun revealTarget(lineTop: Float, lineBottom: Float, scroll: Int, uncoveredHeight: Float, margin: Float): Int? {
    if (uncoveredHeight <= 0f) return null
    if (lineTop >= scroll && lineBottom <= scroll + uncoveredHeight) return null
    val usedMargin = minOf(margin, uncoveredHeight / 3)
    return (lineTop - usedMargin).roundToInt().coerceAtLeast(0)
}
