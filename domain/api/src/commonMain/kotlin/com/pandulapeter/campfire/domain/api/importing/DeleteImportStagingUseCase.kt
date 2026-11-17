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

import com.pandulapeter.campfire.data.model.importing.StagedFile

/**
 * Deletes everything the staging requests of [files] hold (a received or downloaded archive and all that was unpacked
 * from it), once the import they belong to has settled for good: imported, cancelled, or failed and dismissed. Never
 * throws but cancellation, and finishes even then.
 */
interface DeleteImportStagingUseCase {

    suspend operator fun invoke(files: Collection<StagedFile>)
}
