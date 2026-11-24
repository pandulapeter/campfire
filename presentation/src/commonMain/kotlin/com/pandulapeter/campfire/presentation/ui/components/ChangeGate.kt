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

/**
 * Whether a value has changed since it was first composed, for an animation that is only worth composing once there is
 * something to animate ([AnimatedContentOnChange], [AnimatedVisibilityOnChange]). Once changed it stays changed, even
 * when the value comes back: the animation armed by the change keeps running, and going back to the plain content
 * would cut it off.
 *
 * Written during composition rather than held as state, since the branch it decides is taken in that same composition:
 * the frame of the first change already composes the animation, which then animates that change.
 *
 * @param resting The value the content was first composed with, which the animation starts from.
 */
internal class ChangeGate<T>(val resting: T) {

    var hasChanged = false
        private set

    /** Records [value] and answers whether it, or any value before it, differed from [resting]. */
    fun hasChangedTo(value: T): Boolean {
        if (value != resting) hasChanged = true
        return hasChanged
    }
}
