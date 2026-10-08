/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout

/**
 * A list screen's list with its [SearchableTopAppBar] over it, both filling the screen: drawn in that order, but with
 * the bar measured first. The bar reports how far its buttons reach ([AppBarOverlap.reach]) as it is measured, and the
 * list's section headers count the room their actions may take out of that reach as they are measured, so the other
 * way around the first frame of the screen is laid out with no reach at all - every setlist header letting its actions
 * out of their menu and then shrinking them away again a frame later, which is what coming back to the screen showed.
 */
@Composable
internal fun ListUnderAppBar(
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit,
    appBar: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    contents = listOf(list, appBar),
) { (listMeasurables, appBarMeasurables), constraints ->
    val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val appBarPlaceables = appBarMeasurables.map { it.measure(childConstraints) }
    val listPlaceables = listMeasurables.map { it.measure(childConstraints) }
    layout(constraints.maxWidth, constraints.maxHeight) {
        listPlaceables.forEach { it.placeRelative(x = 0, y = 0) }
        appBarPlaceables.forEach { it.placeRelative(x = 0, y = 0) }
    }
}
