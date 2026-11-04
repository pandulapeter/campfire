/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import kotlin.math.max
import kotlin.math.min

/** How far up an item that was on screen before an anchored change has to travel to its place, by its [index]. */
internal class ItemShift(val index: Int, val shift: Int)

/**
 * How far up the item at [index], laid out at [top] with [height] after an anchored change, is drawn from as the change
 * starts, though it was not on screen before it ([shifts] are the items that were); null where it fades in where it is.
 *
 * An item after all of those, with the list moving up, comes in from below with them, as it would in a list that was
 * really scrolled: by as much as the last of them travels, and from no higher than the bottom of the viewport before
 * the change ([previousViewportEnd]), which it was not in. One before all of them, with the list moving down, comes in
 * from above the same way. An item in among them, such as a header coming back between two cards, had no place on
 * screen to come from and fades in.
 */
internal fun arrivalShift(
    index: Int,
    top: Int,
    height: Int,
    shifts: List<ItemShift>,
    previousViewportStart: Int,
    previousViewportEnd: Int,
): Int? {
    val above = shifts.filter { it.index < index }.maxByOrNull { it.index }
    val below = shifts.filter { it.index > index }.minByOrNull { it.index }
    return when {
        below == null && above != null && above.shift > 0 -> max(above.shift, previousViewportEnd - top)
        above == null && below != null && below.shift < 0 -> min(below.shift, previousViewportStart - height - top)
        else -> null
    }
}
