/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Asks for the microphone as the tuner opens, where a tap opened it ([consumeOpenedByTap]) and the question has never
 * been asked ([TunerNotice.NOT_ASKED]): the tap was the player asking to tune, and the notice's button would only be a
 * second tap for the same thing. A refusal, a missing microphone and a tuner shown without a tap keep their notice.
 *
 * @param ask What the notice's own button does: the platform's request, or opening the input where that is what asks.
 */
@Composable
internal fun TunerAskOnOpenEffect(
    notice: TunerNotice?,
    consumeOpenedByTap: () -> Boolean,
    ask: () -> Unit,
) {
    val currentNotice by rememberUpdatedState(notice)
    val currentAsk by rememberUpdatedState(ask)
    LaunchedEffect(Unit) {
        // Consumed whatever the notice, so that a mark left by this opening never answers for a later one.
        if (consumeOpenedByTap() && currentNotice == TunerNotice.NOT_ASKED) currentAsk()
    }
}
