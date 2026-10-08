/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.fontScale

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The exponent applied to the spread ratio of the fingers, on a touchscreen and on a touchpad alike
 * ([CampfireViewModel.magnifyByTouchpad]), so that the same movement of the same fingers resizes the text as much on both.
 */
internal const val PINCH_SENSITIVITY = 0.4f
