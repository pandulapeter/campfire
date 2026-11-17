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

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportIntake
import com.pandulapeter.campfire.data.model.importing.ImportLimits

/**
 * Downloads a file to import from an address the user opened, or a picture a song names by its address. Nothing is
 * kept but an archive received into the staging folder, whose request the import owns: not the address, not the
 * answer, so asking again is asking again.
 */
interface ImportDownloadRepository {

    /**
     * The file at [url], or why there is none. Read into memory within [ImportLimits.MAX_ARCHIVE_SIZE]; or, where
     * [ImportIntake.stagesArchives] is on, a zip by its first bytes received into the staging folder within
     * [ImportLimits.MAX_STAGED_ARCHIVE_SIZE] (`ImportDownload.File.staged`, in a request of its own), reporting the
     * bytes received and the length the host declared to [onProgress] as they arrive, and anything else read into memory
     * as before. Only a cancellation is thrown, and, staging, `StagingRefusedException` or the storage's failure where the
     * archive does not fit or cannot be written; whatever does not end in a staged file leaves none.
     */
    suspend fun download(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload

    /**
     * The picture a song names at [url], no larger than [maxSizeBytes], or why there is none; only a cancellation is
     * thrown. Asked only of public `https` hosts, see `ImportDownloadRemoteSource.downloadPicture`.
     */
    suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload
}
