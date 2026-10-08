/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/** What [CampfireDestination.ImportReport] shows, see [CampfireViewModel.importReport]. */
sealed interface ImportReport {

    /**
     * Names the library has given to other files, and the question of what to do about them, see
     * [CampfireViewModel.resolveImport].
     */
    data class Review(val summary: ImportPlan.Summary) : ImportReport

    /** The answer being carried out, with [CampfireViewModel.importProgress] saying how far. */
    data object Importing : ImportReport

    /** What the import came to; null for one that failed before it could say. */
    data class Finished(val result: ImportResult?) : ImportReport
}
