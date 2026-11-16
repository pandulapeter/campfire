/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.export

import com.pandulapeter.campfire.data.model.library.ExportFileWriter

/**
 * A temporary file called [name] for an export the caller writes itself (a PDF, page by page), replacing one of that
 * name. The caller ends it with `finish` or `abandon`, and owns the file `finish` answers until it hands it to the
 * platform or to [DiscardExportedFileUseCase]. Throws where the file cannot be made.
 */
interface CreateExportFileUseCase {

    suspend operator fun invoke(name: String): ExportFileWriter
}
