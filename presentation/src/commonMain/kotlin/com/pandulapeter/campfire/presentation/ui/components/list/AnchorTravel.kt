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

/**
 * How far up an item that is on screen both before and after an anchored change travels to its place, by its [index]:
 * in the layout after the change for [arrivalShift], in the one before it for [departureShift].
 */
internal class ItemShift(val index: Int, val shift: Int)

/**
 * How far up the item at [index], laid out at [top] with [height] after an anchored change, is drawn from as the change
 * starts, though it was not on screen before it ([shifts] are the items that were); null where it fades in where it is.
 *
 * An item after all of those, with the list moving up, comes in from below with them, as it would in a list that was
 * really scrolled: from no higher than the bottom of the viewport before the change ([previousViewportEnd]), which it
 * was not in, and by at least as much as any of them travels, so that none it follows is caught up with where a reflow
 * moves some cards more than others. One before all of them, with the list moving down, comes in from above the same
 * way. An item in among them, such as a header coming back between two cards, had no place on screen to come from and
 * fades in.
 */
internal fun arrivalShift(
    index: Int,
    top: Int,
    height: Int,
    shifts: List<ItemShift>,
    previousViewportStart: Int,
    previousViewportEnd: Int,
): Int? {
    val before = shifts.filter { it.index < index }
    val after = shifts.filter { it.index > index }
    return when {
        after.isEmpty() && before.isNotEmpty() && before.maxOf { it.shift } > 0 -> max(before.maxOf { it.shift }, previousViewportEnd - top)
        before.isEmpty() && after.isNotEmpty() && after.minOf { it.shift } < 0 -> min(after.minOf { it.shift }, previousViewportStart - height - top)
        else -> null
    }
}

/**
 * How far up the item at [index], laid out at [top] with [height] before an anchored change, travels as the change runs,
 * though it is not on screen after it ([shifts] are the items that still are, by their index before it); null where it
 * is gone at once.
 *
 * The reverse of [arrivalShift]: an item after all of those, with the list moving down, is pushed off the bottom with
 * them, to no higher than the bottom of the viewport after the change ([viewportEnd]), and by at least as much as any
 * of them travels, so that none of them runs into it. One before all of them, with the list moving up, is pushed off
 * the top the same way. One in among them did not leave by an edge.
 */
internal fun departureShift(
    index: Int,
    top: Int,
    height: Int,
    shifts: List<ItemShift>,
    viewportStart: Int,
    viewportEnd: Int,
): Int? {
    val before = shifts.filter { it.index < index }
    val after = shifts.filter { it.index > index }
    return when {
        after.isEmpty() && before.isNotEmpty() && before.minOf { it.shift } < 0 -> min(before.minOf { it.shift }, top - viewportEnd)
        before.isEmpty() && after.isNotEmpty() && after.maxOf { it.shift } > 0 -> max(after.maxOf { it.shift }, top + height - viewportStart)
        else -> null
    }
}
