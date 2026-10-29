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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection

/**
 * Which way along a horizontal pager the arrow key points: 1 for the next item, -1 for the previous one, null for a key
 * that is not Left or Right. The arrows are physical, so Left goes to whatever lies to the left: the previous item in a
 * left-to-right layout and the next one in a right-to-left layout, where the pager lays its items out from the right.
 * A page turner pedal sends the same arrows, and a reader looks where the pages are, not at which way the words run.
 */
internal fun Key.horizontalArrowStep(layoutDirection: LayoutDirection): Int? {
    val towardsEnd = when (layoutDirection) {
        LayoutDirection.Ltr -> 1
        LayoutDirection.Rtl -> -1
    }
    return when (this) {
        Key.DirectionRight -> towardsEnd
        Key.DirectionLeft -> -towardsEnd
        else -> null
    }
}
