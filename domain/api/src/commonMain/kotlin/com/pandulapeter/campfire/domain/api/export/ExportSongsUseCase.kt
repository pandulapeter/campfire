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

import com.pandulapeter.campfire.data.model.library.ExportedFile

interface ExportSongsUseCase {

    /**
     * A single song leaves as the `.cho` file it already is and several as a zip, unless [isArchive] asks for a zip
     * whatever the count: the songs ticked in the selection mode, whose export screen promised one. Null when none of
     * them can be read. [isDated] is as for `ExportSetlistUseCase`, and only matters for a zip. [rewrite] is given each
     * song's file name and text as read and answers the text that goes out, the same text for the file as it is stored.
     */
    suspend operator fun invoke(
        fileNames: List<String>,
        isArchive: Boolean,
        isDated: Boolean,
        rewrite: (fileName: String, text: String) -> String,
    ): ExportedFile?
}
