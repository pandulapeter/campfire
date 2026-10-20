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

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.geometry.Rect

/** The motion scheme's spatial spring for the bounds `animateBounds` carries a pane through, settling within a pixel. */
internal fun MotionScheme.spatialBoundsSpec() = when (val spec = defaultSpatialSpec<Rect>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = Rect.VisibilityThreshold)
    else -> spec
}
