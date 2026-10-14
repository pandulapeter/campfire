/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.importing.ImportDownload

/**
 * Downloads a file the user asked to import from an address. The address may point at any host, and its answer is
 * trusted no further than its size: an implementation follows redirects, never reads more than [download]'s ceiling
 * allows, does not judge the file by the content type the host claims (a share link's is whatever the host says), and
 * keeps nothing once it has answered.
 */
interface ImportDownloadRemoteSource {

    /** The file at [url], or why there is none; only a cancellation is thrown. Never reads past [maxSizeBytes]. */
    suspend fun download(url: String, maxSizeBytes: Long): ImportDownload
}
