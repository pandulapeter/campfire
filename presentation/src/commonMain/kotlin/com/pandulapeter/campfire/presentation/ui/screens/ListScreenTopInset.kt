/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.components.only

/**
 * Places a list screen (Songs, Setlists) below the top inset its [contentPadding] carries, where every other top level
 * screen lets what it scrolls go on up under the status bar. A list's cards are faded out entirely under the pinned
 * header's row and the app bar's buttons (`ListTopFade`), so above that row they could only show again in a strip of
 * the status bar, parted from the list by the empty row and crowding the clock: the room is the bar's to stand below.
 * What the screen hands on inside is [withoutTopInset].
 */
internal fun Modifier.belowTopInset(contentPadding: PaddingValues) = padding(contentPadding.only(top = true))

/** These paddings without the top inset, which [belowTopInset] has already placed the screen below. */
internal fun PaddingValues.withoutTopInset() = only(start = true, end = true, bottom = true)
