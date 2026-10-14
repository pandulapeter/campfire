/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

interface CancelSyncConnectionUseCase {

    /**
     * Gives up on an authorization that is waiting, whether or not the [ConnectSyncProviderUseCase] call that
     * started it is still running - on the web it never is. The caller cancels its own call first.
     */
    suspend operator fun invoke()
}
