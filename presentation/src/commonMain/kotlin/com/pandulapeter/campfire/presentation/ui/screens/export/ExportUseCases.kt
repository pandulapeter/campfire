/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.domain.api.export.ClearExportFilesUseCase
import com.pandulapeter.campfire.domain.api.export.CreateExportFileUseCase
import com.pandulapeter.campfire.domain.api.export.DiscardExportedFileUseCase
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import org.koin.core.annotation.Factory

/** The use cases [ExportController] makes and hands out files with, which the view model passes on as one. */
@Factory
class ExportUseCases(
    internal val exportSongs: ExportSongsUseCase,
    internal val exportSetlist: ExportSetlistUseCase,
    internal val exportLibrary: ExportLibraryUseCase,
    internal val createExportFile: CreateExportFileUseCase,
    internal val discardExportedFile: DiscardExportedFileUseCase,
    internal val clearExportFiles: ClearExportFilesUseCase,
)
