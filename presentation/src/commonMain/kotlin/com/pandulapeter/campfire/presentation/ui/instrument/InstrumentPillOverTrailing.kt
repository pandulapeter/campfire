/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * The halves of an [InstrumentPill] that ends in [trailing], laid out as one: the halves fill the whole pill, so that
 * each one's press and ripple reach the pill's edges, the end one's running under [trailing], which stands over the
 * halves' end and takes its own presses (drawn and hit tested after them). The halves are told how wide it is
 * ([halves]' end inset), so that their names keep clear of it. Measured in that order, [trailing] first, which only a
 * subcomposition can do within one pass; it answers no intrinsic queries, so a pill with [trailing] is never measured
 * by them. A screen reader reads the halves before [trailing], whatever their bounds say.
 */
@Composable
internal fun InstrumentPillOverTrailing(
    halves: @Composable (endInset: Dp) -> Unit,
    trailing: @Composable () -> Unit,
) = SubcomposeLayout { constraints ->
    val trailingPlaceables = subcompose(PillSlot.TRAILING) {
        Box(modifier = Modifier.semantics { traversalIndex = 1f }) { trailing() }
    }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
    val halvesPlaceables = subcompose(PillSlot.HALVES) { halves(trailingWidth.toDp()) }.map { it.measure(constraints) }
    val width = constraints.constrainWidth(maxOf(halvesPlaceables.maxOfOrNull { it.width } ?: 0, trailingWidth))
    val height = constraints.constrainHeight(maxOf(halvesPlaceables.maxOfOrNull { it.height } ?: 0, trailingPlaceables.maxOfOrNull { it.height } ?: 0))
    layout(width, height) {
        halvesPlaceables.forEach { it.placeRelative(0, (height - it.height) / 2) }
        trailingPlaceables.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
    }
}

/** The parts [InstrumentPillOverTrailing] subcomposes. */
private enum class PillSlot {
    TRAILING,
    HALVES,
}
