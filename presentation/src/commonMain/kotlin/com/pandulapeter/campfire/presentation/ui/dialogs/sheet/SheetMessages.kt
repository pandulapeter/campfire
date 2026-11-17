/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.messages.LocalMessagePlaces
import com.pandulapeter.campfire.presentation.ui.messages.MessagesPresenter

/**
 * The [content] of a [CampfireBottomSheet] with the app's messages drawn over its bottom edge while the sheet is the
 * topmost one open: the sheet is a window of its own on Android, so a message drawn at the app's root would play out
 * under it. The sheet stops drawing them as it starts closing ([isClosing]), so a message sent as it closes (a sheet
 * that writes as it goes) is drawn at the root instead. Laid over the content rather than beside it, so that it takes
 * nothing of the sheet's pinned height, a short window's included, and above the keyboard and the navigation bar, as
 * the content padding is.
 */
@Composable
internal fun SheetMessages(
    isClosing: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val places = LocalMessagePlaces.current
    val place = remember { Any() }
    val isSheetClosing by remember(isClosing) { derivedStateOf(isClosing) }
    if (places != null) {
        DisposableEffect(places, isSheetClosing) {
            if (!isSheetClosing) places.open(place)
            onDispose { places.close(place) }
        }
    }
    Box {
        content()
        // Sized by the content alone, so that a message never makes a short sheet taller.
        Box(modifier = Modifier.matchParentSize()) {
            MessagesPresenter(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                place = place,
            )
        }
    }
}
