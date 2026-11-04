/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

/**
 * A request to bring the setlist [fileName] into sight, see [SetlistsController.revealSetlist]: scrolled to while the
 * screen is already on top ([isAnimated]), or put in place at once on a screen that is only now arriving, which moves in
 * whole and has nothing on it yet for a scroll to start from.
 */
internal data class SetlistReveal(
    val fileName: String,
    val isAnimated: Boolean,
)
