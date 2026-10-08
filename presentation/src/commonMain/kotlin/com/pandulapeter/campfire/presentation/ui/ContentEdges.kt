/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The edges the screens keep their content clear of: the system bars and, where the window is laid out into it, the
 * display cutout. Android's edge to edge window reaches into the cutout on every side, and Material's app bars, rail
 * and navigation bar already keep clear of it there (their default insets are these); a camera in the middle of a
 * landscape phone's long edge would otherwise be drawn over the ends of the list rows and the lyrics. Elsewhere the
 * cutout is either nothing or inside the system bars already, which a union leaves as it is.
 */
internal val WindowInsets.Companion.contentEdges: WindowInsets
    @Composable get() = systemBars.union(displayCutout)

/**
 * The least room the navigation rail and the top level screens keep above what they hold. A status bar or the desktop
 * title bar's strip already leaves more than this, but a window with neither - the web, Linux, a desktop window in full
 * screen - would have the rail's first indicator and the list app bar's pill start 4dp from its top edge, closer than
 * either is to the window's sides. The rail and the screens take the same amount, so the two stay level.
 */
private val MIN_TOP_EDGE = 8.dp

internal val WindowInsets.withMinTopEdge: WindowInsets
    get() = union(WindowInsets(top = MIN_TOP_EDGE))
