/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.ui.unit.dp

/** The room around a page at a zoom of 1, and between two pages of the pager. */
internal val PAGE_MARGIN = 16.dp

/**
 * The band a floating control takes at an end of the preview: [PAGE_MARGIN] on either side of the save button's 56dp, and
 * the same for the page buttons' pill at the other end, though it is 8dp shorter, so that a page at rest is centered in
 * its pane with the same room over it as under it. The options' list ends above the same band, so the button never rests
 * on an option.
 */
internal val FLOATING_CONTROLS_CLEARANCE = 88.dp

/**
 * The preview as the first item of the options' list on a phone: 360dp for the page, large enough to judge it by, plus
 * the band the page buttons' pill takes over it, so the pill never sits on the page.
 */
internal val STACKED_PREVIEW_HEIGHT = 360.dp + FLOATING_CONTROLS_CLEARANCE - PAGE_MARGIN
