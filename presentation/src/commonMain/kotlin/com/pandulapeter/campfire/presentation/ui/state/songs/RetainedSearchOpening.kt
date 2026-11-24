/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

/**
 * The [SearchOpening] of the songs search while it is open, held outside the composition for the reason the scroll
 * position is (`ScrollPosition`): the songs screen leaves the composition on a trip to another tab, the search staying
 * open, and closing it on the return has to find where the list was before it opened.
 */
internal class RetainedSearchOpening {
    var opening: SearchOpening? = null
}
