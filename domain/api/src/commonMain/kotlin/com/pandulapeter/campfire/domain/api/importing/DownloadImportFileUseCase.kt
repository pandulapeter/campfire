/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.data.model.importing.ImportDownload

interface DownloadImportFileUseCase {

    /**
     * Downloads the file at [url] for an import the user started, as large as an import of picked files may be, and
     * keeps nothing of it but an archive received into the staging folder (`ImportDownload.File.staged`, see
     * `ImportDownloadRepository.download`), telling [onProgress] the bytes received and the declared length as they
     * arrive; the caller hands a [ImportDownload.File] to [PrepareImportUseCase] as one picked file. Only a cancellation
     * is thrown, and, staging, a failure to find room for the archive or write it.
     */
    suspend operator fun invoke(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload
}
