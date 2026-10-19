/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

/**
 * The font size of a scale diagram's dot labels, in pixels: [LABEL_TEXT] of the dot's [radius], made just small
 * enough that the widest of the diagram's labels, [widestAtDefault] pixels wide at that size, fits [LABEL_ROOM]
 * radii, since a label spilling past its dot is drawn in the surface colour over the sheet's and is lost.
 */
internal fun scaleLabelFontSize(radius: Float, widestAtDefault: Float): Float {
    val default = radius * LABEL_TEXT
    val room = radius * LABEL_ROOM
    return if (widestAtDefault <= room) default else default * room / widestAtDefault
}

internal const val LABEL_TEXT = 1.05f

/** How much of a dot's width, in radii, a label may take: its diameter less a little air at each side. */
internal const val LABEL_ROOM = 1.8f
