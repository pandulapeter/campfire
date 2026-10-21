/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/** How sharing a song or setlist as a link went. */
sealed interface CreateShareResult {

    /** The item is shared: just now, or already, by this device or another one, in which case this is that share. */
    data class Shared(val share: Share) : CreateShareResult

    /** The item's copy would be over the import limit, which is all the person opening the link could take in. */
    data object TooLarge : CreateShareResult

    /** The item is not in the library. */
    data object Missing : CreateShareResult

    data class Failed(val reason: SharingFailure) : CreateShareResult
}
