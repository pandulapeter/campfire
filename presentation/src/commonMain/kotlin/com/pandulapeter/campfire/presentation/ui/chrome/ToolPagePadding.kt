/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.unit.dp

/**
 * The padding at the sides of a tool's page whose content is a grid of cards (the Chords and the Scales tabs), which the
 * shared rows their controls stand in bring with them by reaching past it (`bleedHorizontally`).
 */
internal val TOOL_PAGE_PADDING = 16.dp

/**
 * The room between the groups a tool's page stacks - the instrument, the key and the search, the families or the heading,
 * and the cards - the same on the Chords, the Scales, and the Tuner tabs, so that the three read as one page. The grids'
 * own spacing and the search field's padding count towards it.
 */
internal val TOOL_GROUP_GAP = 16.dp
