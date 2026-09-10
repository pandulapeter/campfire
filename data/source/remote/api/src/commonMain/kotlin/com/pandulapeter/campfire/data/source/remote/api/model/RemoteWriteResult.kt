/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

/** What an upload ended in. A [Conflict] is not an error: it means the remote file moved under the write. */
sealed interface RemoteWriteResult {

    data class Written(val revision: String) : RemoteWriteResult

    /** The remote file did not have the revision the caller expected any more, so nothing was overwritten. */
    data object Conflict : RemoteWriteResult
}
