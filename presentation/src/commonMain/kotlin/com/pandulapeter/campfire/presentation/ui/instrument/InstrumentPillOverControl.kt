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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * The halves of an [InstrumentPill] that ends or, where [isAtStart], starts in [control], laid out as one: the halves fill
 * the whole pill, so that each one's press and ripple reach the pill's edges, the one at that end running under
 * [control], which stands over the halves there and takes its own presses (drawn and hit tested after them). The halves
 * are told how wide it is ([halves]' start or end inset), so that their names keep clear of it. Measured in that order,
 * [control] first, which only a subcomposition can do within one pass; it answers no intrinsic queries, so a pill with
 * [control] is never measured by them. A screen reader reads the halves before [control], whatever their bounds say.
 */
@Composable
internal fun InstrumentPillOverControl(
    halves: @Composable (startInset: Dp, endInset: Dp) -> Unit,
    control: @Composable () -> Unit,
    isAtStart: Boolean,
) = SubcomposeLayout { constraints ->
    val controlPlaceables = subcompose(PillSlot.CONTROL) {
        Box(modifier = Modifier.semantics { traversalIndex = 1f }) { control() }
    }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    val controlWidth = controlPlaceables.maxOfOrNull { it.width } ?: 0
    val inset = controlWidth.toDp()
    val halvesPlaceables = subcompose(PillSlot.HALVES) {
        if (isAtStart) halves(inset, 0.dp) else halves(0.dp, inset)
    }.map { it.measure(constraints) }
    val width = constraints.constrainWidth(maxOf(halvesPlaceables.maxOfOrNull { it.width } ?: 0, controlWidth))
    val height = constraints.constrainHeight(maxOf(halvesPlaceables.maxOfOrNull { it.height } ?: 0, controlPlaceables.maxOfOrNull { it.height } ?: 0))
    layout(width, height) {
        halvesPlaceables.forEach { it.placeRelative(0, (height - it.height) / 2) }
        controlPlaceables.forEach { it.placeRelative(if (isAtStart) 0 else width - it.width, (height - it.height) / 2) }
    }
}

/** The parts [InstrumentPillOverControl] subcomposes. */
private enum class PillSlot {
    CONTROL,
    HALVES,
}
