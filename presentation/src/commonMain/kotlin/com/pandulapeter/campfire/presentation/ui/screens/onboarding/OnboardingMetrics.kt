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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.pandulapeter.campfire.presentation.ui.components.preferences.THEME_COLOR_CHOICE_WIDTH

/**
 * As wide as a page of the onboarding and its bar get: a comfortable line of text, or every theme color in one row where
 * that is wider, so that a tablet or a desktop window shows a column in the middle rather than cards stretched across it.
 */
internal val ONBOARDING_CONTENT_WIDTH = max(560.dp, THEME_COLOR_CHOICE_WIDTH)

/**
 * Room kept beside a page and its bar on top of the margin each answer keeps for itself, since the pages hold little and
 * read better with air around them than with cards reaching for the window's edges. It grows with the room [width]
 * leaves beyond the smallest supported phone, which gives none of it up: there the chord preview's first line would
 * wrap. Kept outside [ONBOARDING_CONTENT_WIDTH], so the widest the content gets is unchanged.
 */
internal fun onboardingSideMargin(width: Dp) = ((width - SMALLEST_WIDTH) / 5).coerceIn(0.dp, MAX_SIDE_MARGIN)

private val SMALLEST_WIDTH = 360.dp
private val MAX_SIDE_MARGIN = 8.dp

/**
 * The least height a page keeps its mark at. Higher than the app's short window, since the mark is decoration, and the
 * pages are better scrolled for their answers than for it.
 */
internal val ONBOARDING_MARK_MIN_HEIGHT = 640.dp

/**
 * The least room a page gets its wider gaps in: a tall phone held upright, a tablet, or a tall desktop window, where the
 * standard gaps leave the page looking packed in the middle of empty space. Changing between the two is animated with
 * the mark's own change ([ONBOARDING_MARK_RESIZE_MILLIS]), since only a resized window crosses it.
 */
internal val ONBOARDING_ROOMY_MIN_HEIGHT = 720.dp

/**
 * Room kept under the bar on top of its own padding, so that a tall window (a desktop or a browser in full screen) does
 * not leave Next pressed against the bottom edge while the page above has space to spare. It grows with the height
 * [height] has beyond [ONBOARDING_ROOMY_MIN_HEIGHT], so phones, which need every dp for the page, keep none of it, and
 * follows a resize continuously rather than switching at a threshold.
 */
internal fun onboardingBottomMargin(height: Dp) = ((height - ONBOARDING_ROOMY_MIN_HEIGHT) / 6).coerceIn(0.dp, MAX_BOTTOM_MARGIN)

private val MAX_BOTTOM_MARGIN = 48.dp

/**
 * How long the mark and its place take to leave or come back as a window crosses [ONBOARDING_MARK_MIN_HEIGHT]. Slower
 * than the app's springs, since the page reflows around it and a change that size is lost if it is over in a blink.
 */
internal const val ONBOARDING_MARK_RESIZE_MILLIS = 600
