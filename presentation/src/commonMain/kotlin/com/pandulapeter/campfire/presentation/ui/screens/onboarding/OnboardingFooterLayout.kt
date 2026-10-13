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

/**
 * Where the dots start in a footer [width] wide: centered while the widest the start and end slots can be
 * ([startSlotWidth], [endSlotWidth]) both fit beside them with [gap], else moved away from the wider slot as far as
 * the narrower one allows, so that neither button is squeezed. Where the two slots and the dots cannot all fit even
 * then (a larger font or interface scale, a longer translation), the dots stay centered and each button gets the half
 * its side has: moving the dots cannot make room that is not there, and centered is what keeps them still between pages.
 */
internal fun onboardingDotsStart(
    width: Int,
    dotsWidth: Int,
    startSlotWidth: Int,
    endSlotWidth: Int,
    gap: Int,
): Int {
    val centered = (width - dotsWidth) / 2
    if (startSlotWidth + gap + dotsWidth + gap + endSlotWidth > width) return centered
    return centered.coerceIn(startSlotWidth + gap, width - endSlotWidth - gap - dotsWidth)
}
