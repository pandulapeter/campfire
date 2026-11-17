/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/**
 * An exit that is waiting for an answer, and what to tell the caller if the answer is staying.
 *
 * @param question The dialog asking, which the exit is kept for for as long as it is the one on screen.
 */
internal class PendingExit(
    val exit: () -> Unit,
    val onCancelled: () -> Unit,
    val question: DialogType,
)
