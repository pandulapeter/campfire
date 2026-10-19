/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.coverArt

/**
 * What Coil is given for a cover with nothing to show, which leaves its place empty. [isUnreachable] tells a cover that
 * got no answer, and may well load a moment later, from one whose address has nothing to give.
 */
internal class CoverArtUnavailableException(
    url: String,
    val isUnreachable: Boolean,
) : Exception("No cover art at $url.")
