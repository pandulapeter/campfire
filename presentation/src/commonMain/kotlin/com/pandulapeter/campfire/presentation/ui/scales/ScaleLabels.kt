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

/** What the scale sheet writes in the dots of its neck, picked under it and kept for the session. */
internal enum class ScaleLabels {
    DEGREES,
    NOTES,
}

/** A degree label of the catalogue (`b3`, `#4`, `bb7`) with the accidental signs a reader sees (`♭3`, `♯4`, `♭♭7`). */
internal fun displayedDegree(degree: String) = degree.replace('b', '♭').replace('#', '♯')
