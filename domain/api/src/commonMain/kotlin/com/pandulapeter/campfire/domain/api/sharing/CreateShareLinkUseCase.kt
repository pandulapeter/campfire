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

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind

interface CreateShareLinkUseCase {

    /**
     * Shares the song or setlist [fileName] as a link anyone can open, from the connected sync account, or answers the
     * link it already has there: one item, shared from any device, has one link. A setlist is shared whole, with every
     * song it names. Does not wait for a sync run. Never throws but cancellation.
     *
     * @param title The item's title, shown with the link and carried in it; for display only.
     */
    suspend operator fun invoke(
        kind: SharedLinkKind,
        fileName: String,
        title: String,
    ): CreateShareResult
}
