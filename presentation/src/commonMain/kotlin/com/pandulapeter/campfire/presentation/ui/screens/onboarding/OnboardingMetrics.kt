/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.pandulapeter.campfire.presentation.ui.components.THEME_COLOR_CHOICE_WIDTH

/**
 * As wide as a page of the onboarding and its bar get: a comfortable line of text, or every theme color in one row where
 * that is wider, so that a tablet or a desktop window shows a column in the middle rather than cards stretched across it.
 */
internal val ONBOARDING_CONTENT_WIDTH = max(560.dp, THEME_COLOR_CHOICE_WIDTH)

/**
 * The least height a page keeps its mark at. Higher than the app's short window, since the mark is decoration, and the
 * pages are better scrolled for their answers than for it.
 */
internal val ONBOARDING_MARK_MIN_HEIGHT = 640.dp

/**
 * How long the mark and its place take to leave or come back as a window crosses [ONBOARDING_MARK_MIN_HEIGHT]. Slower
 * than the app's springs, since the page reflows around it and a change that size is lost if it is over in a blink.
 */
internal const val ONBOARDING_MARK_RESIZE_MILLIS = 600
