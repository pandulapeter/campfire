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

import com.pandulapeter.campfire.data.model.importing.ImportPlan

/**
 * This plan with the pictures that arrived on their own ([ImportPlan.imageFiles]) reported the way a file the import does
 * not take is, by name among the skipped ones, and nothing else of them kept. A picture alone is to open the editor on a
 * new song showing it, its bytes waiting as a draft picture until Save; until the editor takes one, this is where the
 * import hands them over, so they are said to be left out rather than lost silently.
 */
internal fun ImportPlan.withImageFilesSkipped() =
    if (imageFiles.isEmpty()) this else copy(skippedFileNames = skippedFileNames + imageFiles.map { it.name }, imageFiles = emptyList())
