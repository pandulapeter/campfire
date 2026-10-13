/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.ImportDownload
import com.pandulapeter.campfire.data.model.domain.ImportLimits

/**
 * Downloads a file to import from an address the user opened. Nothing is kept: not the file, not the address, not
 * the answer, so asking again is asking again.
 */
interface ImportDownloadRepository {

    /** The file at [url], no larger than [ImportLimits.MAX_IMPORT_SIZE], or why there is none; only a cancellation is thrown. */
    suspend fun download(url: String): ImportDownload
}
