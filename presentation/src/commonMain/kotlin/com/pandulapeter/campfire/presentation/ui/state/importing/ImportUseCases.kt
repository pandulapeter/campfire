/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase
import com.pandulapeter.campfire.domain.api.importing.ClearImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.GetSharedLinkImportUseCase
import org.koin.core.annotation.Factory

/** The use cases [ImportController] receives, reads, and writes imports with, which the view model passes on as one. */
@Factory
class ImportUseCases(
    internal val downloadImportFile: DownloadImportFileUseCase,
    internal val prepareImport: PrepareImportUseCase,
    internal val applyImport: ApplyImportUseCase,
    internal val getSharedLinkImport: GetSharedLinkImportUseCase,
    internal val clearImportStaging: ClearImportStagingUseCase,
)
