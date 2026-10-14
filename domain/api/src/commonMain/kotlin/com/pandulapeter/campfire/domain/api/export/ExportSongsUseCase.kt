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

import com.pandulapeter.campfire.data.model.domain.ExportedFile

interface ExportSongsUseCase {

    /**
     * A single song leaves as the `.cho` file it already is and several as a zip, unless [isArchive] asks for a zip
     * whatever the count: the songs ticked in the selection mode, whose export screen promised one. Null when none of
     * them can be read.
     */
    suspend operator fun invoke(fileNames: List<String>, isArchive: Boolean): ExportedFile?
}
