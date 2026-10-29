/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The shape of the segment at [index] of [count], Material's own, which rounds the first segment's start and the last
 * one's end, made unequal to itself in the other layout direction.
 *
 * A border caches its outline and drops it only when its size or its shape changes: the draw cache it keeps is a node
 * delegated to, which Compose does not tell of a new layout direction. Switching the app's language between one written
 * from the left and one written from the right would leave each end segment's border rounded at the wrong side while
 * its fill turns over, so the shape itself carries the direction, and a new one is a new shape to the border.
 */
@Composable
internal fun segmentShape(index: Int, count: Int): Shape = DirectionalShape(
    shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
    layoutDirection = LocalLayoutDirection.current,
)

/** [shape] drawn as it is; [layoutDirection] only tells it apart from the same shape in the other direction. */
private data class DirectionalShape(
    val shape: Shape,
    val layoutDirection: LayoutDirection,
) : Shape by shape
