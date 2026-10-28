/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/**
 * The tune of [previous] each tune of [current] is drawn as until its own rendering arrives: the tunes that left
 * [previous], paired in order with those that came into [current], where as many left as came. An edit of one tune is
 * one leaving and one coming; a tune added or removed beside the others pairs with nothing, so no tune is ever drawn as
 * its neighbour's staff.
 */
internal fun <T> notationStandInsOf(previous: List<T>, current: List<T>): Map<T, T> {
    val currentSet = current.toSet()
    val previousSet = previous.toSet()
    val left = previous.filter { it !in currentSet }
    val came = current.filter { it !in previousSet }
    return if (left.size == came.size) came.zip(left).toMap() else emptyMap()
}
