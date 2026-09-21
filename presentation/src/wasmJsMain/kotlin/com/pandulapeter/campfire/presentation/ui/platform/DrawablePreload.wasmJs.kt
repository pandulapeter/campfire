/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.allDrawableResources
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadImageVector

/**
 * Asks for every drawable the app has at once and reports when the last of them is in.
 *
 * `preloadImageVector` puts the decoded vector in the same process-wide cache `painterResource` reads, so from here
 * on every icon in the app is answered out of memory, in the composition that asks for it rather than a frame or
 * two later. The whole set is requested in one pass instead of screen by screen because the requests are what costs
 * the time - they are one round trip each, and the browser runs them in parallel - while the files themselves are a
 * few hundred bytes of XML.
 *
 * The wait is bounded, see [PRELOAD_DEADLINE_MILLIS]: a drawable that cannot be fetched must not be what keeps the
 * app covered.
 *
 * Every drawable in `composeResources/drawable` is an XML vector, so the vector preload covers all of them; a
 * bitmap or an SVG added later would need `preloadImageBitmap` alongside it to be counted here.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun areDrawablesLoaded(): Boolean {
    var pending = 0
    Res.allDrawableResources.forEach { (name, drawable) ->
        // Keyed by name rather than left to the position in the map, so that a drawable added or removed later
        // cannot shift the preload of every drawable after it onto somebody else's remembered state.
        if (key(name) { preloadImageVector(drawable).value } == null) pending++
    }
    // A request that failed looks exactly like one that has not been answered: Compose resources swallows the
    // failure and leaves the state where it was, for good. So the wait ends by itself, and an app with an icon
    // missing - which the screen that draws it asks for again, in a request of its own - is a far better place to
    // be than a loading page that never goes away over an app that has started.
    val hasWaitedLongEnough by produceState(initialValue = false) {
        delay(PRELOAD_DEADLINE_MILLIS)
        value = true
    }
    return pending == 0 || hasWaitedLongEnough
}

/**
 * Counted from the first composition, by which time the binaries are in and the requests are all on their way
 * together: fifty files of a few hundred bytes each are a second's work on a slow connection, so this is only
 * ever reached by a request that is not coming back.
 */
private const val PRELOAD_DEADLINE_MILLIS = 5_000L
