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

/**
 * Whether every icon in the app is in memory, so that the first `painterResource` asking for one is answered with
 * the icon itself rather than with a placeholder.
 *
 * Three of the four platforms read a drawable out of storage the app already has open, and Compose resources does
 * that without suspending: nothing is ever drawn before it is there, and the answer is true from the first frame.
 * The web build fetches every drawable over the network, one request per file, and `painterResource` returns an
 * empty one-by-one image until the answer arrives - so an icon composed before its file is in takes no space, and
 * the row, the button or the chip around it is laid out at the wrong size and jumps once it lands. Since the whole
 * set is forty-odd XML files of a few hundred bytes each, fetched in parallel, fetching all of them up front is
 * cheaper than watching the screens rearrange themselves.
 *
 * Called while the launch screen is up, which is what the waiting is spent on; see [com.pandulapeter.campfire.presentation.ui.CampfireApp].
 */
@Composable
internal expect fun areDrawablesLoaded(): Boolean
