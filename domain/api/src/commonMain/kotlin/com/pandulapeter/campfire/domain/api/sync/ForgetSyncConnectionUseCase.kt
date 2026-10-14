/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sync

interface ForgetSyncConnectionUseCase {

    /**
     * Drops whatever this device has stored about a sync account, without telling the service anything: for an
     * installation that has just been made, credentials left behind by the previous one belong to nobody.
     * Different from [DisconnectSyncProviderUseCase], which is the user disconnecting and does revoke the token.
     */
    suspend operator fun invoke()
}
