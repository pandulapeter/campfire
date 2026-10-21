/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sharing

import com.pandulapeter.campfire.data.model.share.SharingFailure

interface LoadSharedLinksUseCase {

    /**
     * Reads the connected account's list of shares again, one request, into what [GetSharedLinksUseCase] emits, which
     * otherwise learns of other devices' shares and stops only at a sync run. Null when it worked; never throws but
     * cancellation.
     */
    suspend operator fun invoke(): SharingFailure?
}
