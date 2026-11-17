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

import com.pandulapeter.campfire.data.model.importing.ReceivedArchive

/**
 * Opens the staging folder's file for an archive that arrived from outside (picked, dropped, or handed over by the
 * system), in a staging request of its own, with room reserved for [announcedSize] where the platform said it, so that
 * a disk too full for it is found out before anything is copied. Throws `StagingRefusedException` where it cannot fit,
 * having made nothing. The caller fills it and finishes or abandons it; what is finished stays until the request is
 * deleted (`DeleteImportStagingUseCase`) or the next launch.
 */
interface ReceiveImportArchiveUseCase {

    suspend operator fun invoke(announcedSize: Long?): ReceivedArchive
}
