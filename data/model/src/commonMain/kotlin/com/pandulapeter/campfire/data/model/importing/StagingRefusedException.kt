/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import com.pandulapeter.campfire.data.model.OutOfSpaceException

/**
 * A write into the staging folder refused before it was made, its partial file already deleted. Either the device has
 * no room for it ([NoSpace], whose cause is an [OutOfSpaceException] so that `isOutOfSpace` reads it as the device being
 * full, as it reads a write the storage itself refused), or the import passes what one import may stage ([TooLarge]).
 */
sealed class StagingRefusedException(message: String, cause: Throwable?) : Exception(message, cause) {

    /**
     * The free space (less what [ImportLimits.minFreeSpaceFor] keeps free, and what other imports have reserved and not
     * yet written) is less than what this one needs.
     *
     * @param neededBytes How much space the whole import needs (what it has staged so far, what it asked for, and the
     *   margin kept free), for the user to make room for.
     */
    class NoSpace(val neededBytes: Long) : StagingRefusedException("There is no room to stage $neededBytes bytes.", OutOfSpaceException())

    /** One staged file or one import's staged files together would pass [limit] bytes. */
    class TooLarge(val limit: Long) : StagingRefusedException("Staging is limited to $limit bytes.", null)
}
