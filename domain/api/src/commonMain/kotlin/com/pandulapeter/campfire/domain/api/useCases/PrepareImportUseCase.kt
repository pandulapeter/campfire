/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportedFile

/**
 * Works out what importing [files] would do, without writing anything: archives are unpacked, collections are split
 * into songs, and every resulting file is held against the name it wants in the library.
 *
 * The half of an import that decides is separate from the half that acts for the same reason sync's is: only a
 * decision made before anything moves can be put to the user as one question about a whole archive.
 */
interface PrepareImportUseCase {

    suspend operator fun invoke(files: List<ImportedFile>): ImportPlan
}
