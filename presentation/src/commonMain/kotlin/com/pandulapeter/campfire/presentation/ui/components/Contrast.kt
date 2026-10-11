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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Whether [dark] reads more clearly on [background] than [light] does, by contrast as WCAG measures it: the geometric
 * midpoint of `luminance + 0.05` of the two is where they contrast with it equally, which for black and white is a
 * luminance of about 0.18 rather than the 0.5 the eye would guess.
 */
internal fun isDarkerMoreLegible(background: Color, light: Color = Color.White, dark: Color = Color.Black): Boolean =
    (background.luminance() + 0.05f).let { it * it } > (light.luminance() + 0.05f) * (dark.luminance() + 0.05f)
