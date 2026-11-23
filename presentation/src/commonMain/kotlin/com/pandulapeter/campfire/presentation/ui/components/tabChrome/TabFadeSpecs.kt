/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.tabChrome

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * The fade through of two tabs: the screen being left fades out first, and only then does the one being opened fade in.
 * Kept here rather than with the navigation transitions, so that what is drawn over both screens while they swap (see
 * [TabChrome]) fades its content on the very curves the screens fade on.
 */
internal fun <T> tabFadeOutSpec() = tween<T>(TAB_FADE_OUT_DURATION, easing = FastOutLinearInEasing)

/** See [tabFadeOutSpec]. */
internal fun <T> tabFadeInSpec() = tween<T>(TAB_FADE_IN_DURATION, delayMillis = TAB_FADE_OUT_DURATION, easing = LinearOutSlowInEasing)

/** A transition driven by a back gesture, which follows the finger linearly: a card's, and the cross fade of two tabs. */
internal fun <T> predictiveBackSpec() = tween<T>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)

private const val TAB_FADE_OUT_DURATION = 90
private const val TAB_FADE_IN_DURATION = 210
private const val PREDICTIVE_BACK_DURATION = 350
