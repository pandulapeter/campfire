/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.update

import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction

/**
 * The buttons of the blocking screen a required update puts up: Update, always, and Continue offline while there is
 * an [onContinueOffline] to call. The second is in the list either way, hidden rather than left out, so that it
 * animates in and out as the connection comes and goes instead of the screen being laid out again.
 */
internal fun requiredUpdateActions(
    updateLabel: String,
    continueOfflineLabel: String,
    onUpdate: () -> Unit,
    onContinueOffline: (() -> Unit)?,
) = listOf(
    EmptyStateAction(text = updateLabel, onClick = onUpdate),
    EmptyStateAction(
        text = continueOfflineLabel,
        onClick = onContinueOffline ?: {},
        isVisible = onContinueOffline != null,
    ),
)
