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

/**
 * What the gate is told while a required update may be waited out offline. Only [AppUpdateState.Required] is hidden
 * by the wait, as nothing: an optional update or a finished download found in the meantime is still offered, and the
 * controller itself keeps deciding on the real state.
 */
internal fun visibleAppUpdateState(
    state: AppUpdateState,
    isWaitingForConnection: Boolean,
) = if (isWaitingForConnection && state == AppUpdateState.Required) AppUpdateState.NotAvailable else state

/**
 * Whether a "continue offline" still stands once the store has answered a check. An answer given while offline (the
 * store can answer from what it knows on the device) leaves it standing, so it cannot put the blocking screen back
 * before a connection could have changed anything; an answer given online decides again.
 */
internal fun isStillWaitingForConnection(
    isWaitingForConnection: Boolean,
    isOnline: Boolean,
) = isWaitingForConnection && !isOnline
