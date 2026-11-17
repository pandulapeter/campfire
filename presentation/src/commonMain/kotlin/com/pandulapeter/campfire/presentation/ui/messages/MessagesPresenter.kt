/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.messages

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Draws the messages [Messages] shows when [place] is where they are drawn now ([MessagePlaces.isPresentedIn]). Only one
 * place composes its host at a time: two hosts of the one state would both draw the same message.
 */
@Composable
internal fun MessagesPresenter(
    modifier: Modifier = Modifier,
    place: Any,
) {
    val places = LocalMessagePlaces.current ?: return
    if (places.isPresentedIn(place)) {
        SnackbarHost(
            modifier = modifier,
            hostState = places.hostState,
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}
